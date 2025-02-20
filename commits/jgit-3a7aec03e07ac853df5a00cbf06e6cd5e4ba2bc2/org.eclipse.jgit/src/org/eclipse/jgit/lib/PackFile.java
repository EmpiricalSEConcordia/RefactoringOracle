/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.lib;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.PackInvalidException;
import org.eclipse.jgit.errors.PackMismatchException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.util.LongList;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A Git version 2 pack file representation. A pack file contains Git objects in
 * delta packed format yielding high compression of lots of object where some
 * objects are similar.
 */
public class PackFile implements Iterable<PackIndex.MutableEntry> {
	/** Sorts PackFiles to be most recently created to least recently created. */
	public static Comparator<PackFile> SORT = new Comparator<PackFile>() {
		public int compare(final PackFile a, final PackFile b) {
			return b.packLastModified - a.packLastModified;
		}
	};

	private final File idxFile;

	private final File packFile;

	final int hash;

	private RandomAccessFile fd;

	/** Serializes reads performed against {@link #fd}. */
	private final Object readLock = new Object();

	long length;

	private int activeWindows;

	private int activeCopyRawData;

	private int packLastModified;

	private volatile boolean invalid;

	private byte[] packChecksum;

	private PackIndex loadedIdx;

	private PackReverseIndex reverseIdx;

	/**
	 * Objects we have tried to read, and discovered to be corrupt.
	 * <p>
	 * The list is allocated after the first corruption is found, and filled in
	 * as more entries are discovered. Typically this list is never used, as
	 * pack files do not usually contain corrupt objects.
	 */
	private volatile LongList corruptObjects;

	/**
	 * Construct a reader for an existing, pre-indexed packfile.
	 *
	 * @param idxFile
	 *            path of the <code>.idx</code> file listing the contents.
	 * @param packFile
	 *            path of the <code>.pack</code> file holding the data.
	 */
	public PackFile(final File idxFile, final File packFile) {
		this.idxFile = idxFile;
		this.packFile = packFile;
		this.packLastModified = (int) (packFile.lastModified() >> 10);

		// Multiply by 31 here so we can more directly combine with another
		// value in WindowCache.hash(), without doing the multiply there.
		//
		hash = System.identityHashCode(this) * 31;
		length = Long.MAX_VALUE;
	}

	private synchronized PackIndex idx() throws IOException {
		if (loadedIdx == null) {
			if (invalid)
				throw new PackInvalidException(packFile);

			try {
				final PackIndex idx = PackIndex.open(idxFile);

				if (packChecksum == null)
					packChecksum = idx.packChecksum;
				else if (!Arrays.equals(packChecksum, idx.packChecksum))
					throw new PackMismatchException(JGitText.get().packChecksumMismatch);

				loadedIdx = idx;
			} catch (IOException e) {
				invalid = true;
				throw e;
			}
		}
		return loadedIdx;
	}

	final PackedObjectLoader resolveBase(final WindowCursor curs, final long ofs)
			throws IOException {
		if (isCorrupt(ofs)) {
			throw new CorruptObjectException(MessageFormat.format(JGitText
					.get().objectAtHasBadZlibStream, ofs, getPackFile()));
		}
		return reader(curs, ofs);
	}

	/** @return the File object which locates this pack on disk. */
	public File getPackFile() {
		return packFile;
	}

	/**
	 * Determine if an object is contained within the pack file.
	 * <p>
	 * For performance reasons only the index file is searched; the main pack
	 * content is ignored entirely.
	 * </p>
	 *
	 * @param id
	 *            the object to look for. Must not be null.
	 * @return true if the object is in this pack; false otherwise.
	 * @throws IOException
	 *             the index file cannot be loaded into memory.
	 */
	public boolean hasObject(final AnyObjectId id) throws IOException {
		final long offset = idx().findOffset(id);
		return 0 < offset && !isCorrupt(offset);
	}

	/**
	 * Get an object from this pack.
	 *
	 * @param curs
	 *            temporary working space associated with the calling thread.
	 * @param id
	 *            the object to obtain from the pack. Must not be null.
	 * @return the object loader for the requested object if it is contained in
	 *         this pack; null if the object was not found.
	 * @throws IOException
	 *             the pack file or the index could not be read.
	 */
	public PackedObjectLoader get(final WindowCursor curs, final AnyObjectId id)
			throws IOException {
		final long offset = idx().findOffset(id);
		return 0 < offset && !isCorrupt(offset) ? reader(curs, offset) : null;
	}

	/**
	 * Close the resources utilized by this repository
	 */
	public void close() {
		UnpackedObjectCache.purge(this);
		WindowCache.purge(this);
		synchronized (this) {
			loadedIdx = null;
			reverseIdx = null;
		}
	}

	/**
	 * Provide iterator over entries in associated pack index, that should also
	 * exist in this pack file. Objects returned by such iterator are mutable
	 * during iteration.
	 * <p>
	 * Iterator returns objects in SHA-1 lexicographical order.
	 * </p>
	 *
	 * @return iterator over entries of associated pack index
	 *
	 * @see PackIndex#iterator()
	 */
	public Iterator<PackIndex.MutableEntry> iterator() {
		try {
			return idx().iterator();
		} catch (IOException e) {
			return Collections.<PackIndex.MutableEntry> emptyList().iterator();
		}
	}

	/**
	 * Obtain the total number of objects available in this pack. This method
	 * relies on pack index, giving number of effectively available objects.
	 *
	 * @return number of objects in index of this pack, likewise in this pack
	 * @throws IOException
	 *             the index file cannot be loaded into memory.
	 */
	long getObjectCount() throws IOException {
		return idx().getObjectCount();
	}

	/**
	 * Search for object id with the specified start offset in associated pack
	 * (reverse) index.
	 *
	 * @param offset
	 *            start offset of object to find
	 * @return object id for this offset, or null if no object was found
	 * @throws IOException
	 *             the index file cannot be loaded into memory.
	 */
	ObjectId findObjectForOffset(final long offset) throws IOException {
		return getReverseIdx().findObject(offset);
	}

	final UnpackedObjectCache.Entry readCache(final long position) {
		return UnpackedObjectCache.get(this, position);
	}

	final void saveCache(final long position, final byte[] data, final int type) {
		UnpackedObjectCache.store(this, position, data, type);
	}

	final byte[] decompress(final long position, final int totalSize,
			final WindowCursor curs) throws DataFormatException, IOException {
		final byte[] dstbuf = new byte[totalSize];
		if (curs.inflate(this, position, dstbuf, 0) != totalSize)
			throw new EOFException(MessageFormat.format(JGitText.get().shortCompressedStreamAt, position));
		return dstbuf;
	}

	final void copyAsIs(PackOutputStream out, LocalObjectToPack src,
			WindowCursor curs) throws IOException,
			StoredObjectRepresentationNotAvailableException {
		beginCopyAsIs(src);
		try {
			copyAsIs2(out, src, curs);
		} finally {
			endCopyAsIs();
		}
	}

	private void copyAsIs2(PackOutputStream out, LocalObjectToPack src,
			WindowCursor curs) throws IOException,
			StoredObjectRepresentationNotAvailableException {
		final CRC32 crc1 = new CRC32();
		final CRC32 crc2 = new CRC32();
		final byte[] buf = out.getCopyBuffer();

		// Rip apart the header so we can discover the size.
		//
		readFully(src.copyOffset, buf, 0, 20, curs);
		int c = buf[0] & 0xff;
		final int typeCode = (c >> 4) & 7;
		long inflatedLength = c & 15;
		int shift = 4;
		int headerCnt = 1;
		while ((c & 0x80) != 0) {
			c = buf[headerCnt++] & 0xff;
			inflatedLength += (c & 0x7f) << shift;
			shift += 7;
		}

		if (typeCode == Constants.OBJ_OFS_DELTA) {
			do {
				c = buf[headerCnt++] & 0xff;
			} while ((c & 128) != 0);
			crc1.update(buf, 0, headerCnt);
			crc2.update(buf, 0, headerCnt);
		} else if (typeCode == Constants.OBJ_REF_DELTA) {
			crc1.update(buf, 0, headerCnt);
			crc2.update(buf, 0, headerCnt);

			readFully(src.copyOffset + headerCnt, buf, 0, 20, curs);
			crc1.update(buf, 0, 20);
			crc2.update(buf, 0, headerCnt);
			headerCnt += 20;
		} else {
			crc1.update(buf, 0, headerCnt);
			crc2.update(buf, 0, headerCnt);
		}

		final long dataOffset = src.copyOffset + headerCnt;
		final long dataLength;
		final long expectedCRC;
		final ByteArrayWindow quickCopy;

		// Verify the object isn't corrupt before sending. If it is,
		// we report it missing instead.
		//
		try {
			dataLength = findEndOffset(src.copyOffset) - dataOffset;
			quickCopy = curs.quickCopy(this, dataOffset, dataLength);

			if (idx().hasCRC32Support()) {
				// Index has the CRC32 code cached, validate the object.
				//
				expectedCRC = idx().findCRC32(src);
				if (quickCopy != null) {
					quickCopy.crc32(crc1, dataOffset, (int) dataLength);
				} else {
					long pos = dataOffset;
					long cnt = dataLength;
					while (cnt > 0) {
						final int n = (int) Math.min(cnt, buf.length);
						readFully(pos, buf, 0, n, curs);
						crc1.update(buf, 0, n);
						pos += n;
						cnt -= n;
					}
				}
				if (crc1.getValue() != expectedCRC) {
					setCorrupt(src.copyOffset);
					throw new CorruptObjectException(MessageFormat.format(
							JGitText.get().objectAtHasBadZlibStream,
							src.copyOffset, getPackFile()));
				}
			} else {
				// We don't have a CRC32 code in the index, so compute it
				// now while inflating the raw data to get zlib to tell us
				// whether or not the data is safe.
				//
				Inflater inf = curs.inflater();
				byte[] tmp = new byte[1024];
				if (quickCopy != null) {
					quickCopy.check(inf, tmp, dataOffset, (int) dataLength);
				} else {
					long pos = dataOffset;
					long cnt = dataLength;
					while (cnt > 0) {
						final int n = (int) Math.min(cnt, buf.length);
						readFully(pos, buf, 0, n, curs);
						crc1.update(buf, 0, n);
						inf.setInput(buf, 0, n);
						while (inf.inflate(tmp, 0, tmp.length) > 0)
							continue;
						pos += n;
						cnt -= n;
					}
				}
				if (!inf.finished() || inf.getBytesRead() != dataLength) {
					setCorrupt(src.copyOffset);
					throw new EOFException(MessageFormat.format(
							JGitText.get().shortCompressedStreamAt,
							src.copyOffset));
				}
				expectedCRC = crc1.getValue();
			}
		} catch (DataFormatException dataFormat) {
			setCorrupt(src.copyOffset);

			CorruptObjectException corruptObject = new CorruptObjectException(
					MessageFormat.format(
							JGitText.get().objectAtHasBadZlibStream,
							src.copyOffset, getPackFile()));
			corruptObject.initCause(dataFormat);

			StoredObjectRepresentationNotAvailableException gone;
			gone = new StoredObjectRepresentationNotAvailableException(src);
			gone.initCause(corruptObject);
			throw gone;

		} catch (IOException ioError) {
			StoredObjectRepresentationNotAvailableException gone;
			gone = new StoredObjectRepresentationNotAvailableException(src);
			gone.initCause(ioError);
			throw gone;
		}

		if (quickCopy != null) {
			// The entire object fits into a single byte array window slice,
			// and we have it pinned.  Write this out without copying.
			//
			out.writeHeader(src, inflatedLength);
			quickCopy.write(out, dataOffset, (int) dataLength);

		} else if (dataLength <= buf.length) {
			// Tiny optimization: Lots of objects are very small deltas or
			// deflated commits that are likely to fit in the copy buffer.
			//
			out.writeHeader(src, inflatedLength);
			out.write(buf, 0, (int) dataLength);
		} else {
			// Now we are committed to sending the object. As we spool it out,
			// check its CRC32 code to make sure there wasn't corruption between
			// the verification we did above, and us actually outputting it.
			//
			out.writeHeader(src, inflatedLength);
			long pos = dataOffset;
			long cnt = dataLength;
			while (cnt > 0) {
				final int n = (int) Math.min(cnt, buf.length);
				readFully(pos, buf, 0, n, curs);
				crc2.update(buf, 0, n);
				out.write(buf, 0, n);
				pos += n;
				cnt -= n;
			}
			if (crc2.getValue() != expectedCRC) {
				throw new CorruptObjectException(MessageFormat.format(JGitText
						.get().objectAtHasBadZlibStream, src.copyOffset,
						getPackFile()));
			}
		}
	}

	boolean invalid() {
		return invalid;
	}

	private void readFully(final long position, final byte[] dstbuf,
			int dstoff, final int cnt, final WindowCursor curs)
			throws IOException {
		if (curs.copy(this, position, dstbuf, dstoff, cnt) != cnt)
			throw new EOFException();
	}

	private synchronized void beginCopyAsIs(ObjectToPack otp)
			throws StoredObjectRepresentationNotAvailableException {
		if (++activeCopyRawData == 1 && activeWindows == 0) {
			try {
				doOpen();
			} catch (IOException thisPackNotValid) {
				StoredObjectRepresentationNotAvailableException gone;

				gone = new StoredObjectRepresentationNotAvailableException(otp);
				gone.initCause(thisPackNotValid);
				throw gone;
			}
		}
	}

	private synchronized void endCopyAsIs() {
		if (--activeCopyRawData == 0 && activeWindows == 0)
			doClose();
	}

	synchronized boolean beginWindowCache() throws IOException {
		if (++activeWindows == 1) {
			if (activeCopyRawData == 0)
				doOpen();
			return true;
		}
		return false;
	}

	synchronized boolean endWindowCache() {
		final boolean r = --activeWindows == 0;
		if (r && activeCopyRawData == 0)
			doClose();
		return r;
	}

	private void doOpen() throws IOException {
		try {
			if (invalid)
				throw new PackInvalidException(packFile);
			synchronized (readLock) {
				fd = new RandomAccessFile(packFile, "r");
				length = fd.length();
				onOpenPack();
			}
		} catch (IOException ioe) {
			openFail();
			throw ioe;
		} catch (RuntimeException re) {
			openFail();
			throw re;
		} catch (Error re) {
			openFail();
			throw re;
		}
	}

	private void openFail() {
		activeWindows = 0;
		activeCopyRawData = 0;
		invalid = true;
		doClose();
	}

	private void doClose() {
		synchronized (readLock) {
			if (fd != null) {
				try {
					fd.close();
				} catch (IOException err) {
					// Ignore a close event. We had it open only for reading.
					// There should not be errors related to network buffers
					// not flushed, etc.
				}
				fd = null;
			}
		}
	}

	ByteArrayWindow read(final long pos, int size) throws IOException {
		synchronized (readLock) {
			if (length < pos + size)
				size = (int) (length - pos);
			final byte[] buf = new byte[size];
			fd.seek(pos);
			fd.readFully(buf, 0, size);
			return new ByteArrayWindow(this, pos, buf);
		}
	}

	ByteWindow mmap(final long pos, int size) throws IOException {
		synchronized (readLock) {
			if (length < pos + size)
				size = (int) (length - pos);

			MappedByteBuffer map;
			try {
				map = fd.getChannel().map(MapMode.READ_ONLY, pos, size);
			} catch (IOException ioe1) {
				// The most likely reason this failed is the JVM has run out
				// of virtual memory. We need to discard quickly, and try to
				// force the GC to finalize and release any existing mappings.
				//
				System.gc();
				System.runFinalization();
				map = fd.getChannel().map(MapMode.READ_ONLY, pos, size);
			}

			if (map.hasArray())
				return new ByteArrayWindow(this, pos, map.array());
			return new ByteBufferWindow(this, pos, map);
		}
	}

	private void onOpenPack() throws IOException {
		final PackIndex idx = idx();
		final byte[] buf = new byte[20];

		fd.seek(0);
		fd.readFully(buf, 0, 12);
		if (RawParseUtils.match(buf, 0, Constants.PACK_SIGNATURE) != 4)
			throw new IOException(JGitText.get().notAPACKFile);
		final long vers = NB.decodeUInt32(buf, 4);
		final long packCnt = NB.decodeUInt32(buf, 8);
		if (vers != 2 && vers != 3)
			throw new IOException(MessageFormat.format(JGitText.get().unsupportedPackVersion, vers));

		if (packCnt != idx.getObjectCount())
			throw new PackMismatchException(MessageFormat.format(
					JGitText.get().packObjectCountMismatch, packCnt, idx.getObjectCount(), getPackFile()));

		fd.seek(length - 20);
		fd.read(buf, 0, 20);
		if (!Arrays.equals(buf, packChecksum))
			throw new PackMismatchException(MessageFormat.format(
					JGitText.get().packObjectCountMismatch
					, ObjectId.fromRaw(buf).name()
					, ObjectId.fromRaw(idx.packChecksum).name()
					, getPackFile()));
	}

	private PackedObjectLoader reader(final WindowCursor curs,
			final long objOffset) throws IOException {
		int p = 0;
		final byte[] ib = curs.tempId;
		readFully(objOffset, ib, 0, 20, curs);
		int c = ib[p++] & 0xff;
		final int typeCode = (c >> 4) & 7;
		long dataSize = c & 15;
		int shift = 4;
		while ((c & 0x80) != 0) {
			c = ib[p++] & 0xff;
			dataSize += (c & 0x7f) << shift;
			shift += 7;
		}

		switch (typeCode) {
		case Constants.OBJ_COMMIT:
		case Constants.OBJ_TREE:
		case Constants.OBJ_BLOB:
		case Constants.OBJ_TAG:
			return new WholePackedObjectLoader(this, objOffset, p, typeCode,
					(int) dataSize);

		case Constants.OBJ_OFS_DELTA: {
			c = ib[p++] & 0xff;
			long ofs = c & 127;
			while ((c & 128) != 0) {
				ofs += 1;
				c = ib[p++] & 0xff;
				ofs <<= 7;
				ofs += (c & 127);
			}
			return new DeltaOfsPackedObjectLoader(this, objOffset, p,
					(int) dataSize, objOffset - ofs);
		}
		case Constants.OBJ_REF_DELTA: {
			readFully(objOffset + p, ib, 0, 20, curs);
			return new DeltaRefPackedObjectLoader(this, objOffset, p + 20,
					(int) dataSize, ObjectId.fromRaw(ib));
		}
		default:
			throw new IOException(MessageFormat.format(JGitText.get().unknownObjectType, typeCode));
		}
	}

	private long findEndOffset(final long startOffset)
			throws IOException, CorruptObjectException {
		final long maxOffset = length - 20;
		return getReverseIdx().findNextOffset(startOffset, maxOffset);
	}

	private synchronized PackReverseIndex getReverseIdx() throws IOException {
		if (reverseIdx == null)
			reverseIdx = new PackReverseIndex(idx());
		return reverseIdx;
	}

	private boolean isCorrupt(long offset) {
		LongList list = corruptObjects;
		if (list == null)
			return false;
		synchronized (list) {
			return list.contains(offset);
		}
	}

	private void setCorrupt(long offset) {
		LongList list = corruptObjects;
		if (list == null) {
			synchronized (readLock) {
				list = corruptObjects;
				if (list == null) {
					list = new LongList();
					corruptObjects = list;
				}
			}
		}
		synchronized (list) {
			list.add(offset);
		}
	}
}
