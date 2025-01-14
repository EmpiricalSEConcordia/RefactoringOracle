/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2011, Shawn O. Pearce <spearce@spearce.org>
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
package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.internal.storage.pack.PackExt.BITMAP_INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.INDEX;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.CancelledException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.internal.storage.reftree.RefTreeNames;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSet;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.GitDateParser;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A garbage collector for git {@link FileRepository}. Instances of this class
 * are not thread-safe. Don't use the same instance from multiple threads.
 *
 * This class started as a copy of DfsGarbageCollector from Shawn O. Pearce
 * adapted to FileRepositories.
 */
public class GC {
	private final static Logger LOG = LoggerFactory
			.getLogger(GC.class);

	private static final String PRUNE_EXPIRE_DEFAULT = "2.weeks.ago"; //$NON-NLS-1$

	private static final String PRUNE_PACK_EXPIRE_DEFAULT = "1.hour.ago"; //$NON-NLS-1$

	private static final Pattern PATTERN_LOOSE_OBJECT = Pattern
			.compile("[0-9a-fA-F]{38}"); //$NON-NLS-1$

	private static final String PACK_EXT = "." + PackExt.PACK.getExtension();//$NON-NLS-1$

	private static final String BITMAP_EXT = "." //$NON-NLS-1$
			+ PackExt.BITMAP_INDEX.getExtension();

	private static final String INDEX_EXT = "." + PackExt.INDEX.getExtension(); //$NON-NLS-1$

	private static final int DEFAULT_AUTOPACKLIMIT = 50;

	private static final int DEFAULT_AUTOLIMIT = 6700;

	private final FileRepository repo;

	private ProgressMonitor pm;

	private long expireAgeMillis = -1;

	private Date expire;

	private long packExpireAgeMillis = -1;

	private Date packExpire;

	private PackConfig pconfig = null;

	/**
	 * the refs which existed during the last call to {@link #repack()}. This is
	 * needed during {@link #prune(Set)} where we can optimize by looking at the
	 * difference between the current refs and the refs which existed during
	 * last {@link #repack()}.
	 */
	private Collection<Ref> lastPackedRefs;

	/**
	 * Holds the starting time of the last repack() execution. This is needed in
	 * prune() to inspect only those reflog entries which have been added since
	 * last repack().
	 */
	private long lastRepackTime;

	/**
	 * Whether gc should do automatic housekeeping
	 */
	private boolean automatic;

	/**
	 * Creates a new garbage collector with default values. An expirationTime of
	 * two weeks and <code>null</code> as progress monitor will be used.
	 *
	 * @param repo
	 *            the repo to work on
	 */
	public GC(FileRepository repo) {
		this.repo = repo;
		this.pm = NullProgressMonitor.INSTANCE;
	}

	/**
	 * Runs a garbage collector on a {@link FileRepository}. It will
	 * <ul>
	 * <li>pack loose references into packed-refs</li>
	 * <li>repack all reachable objects into new pack files and delete the old
	 * pack files</li>
	 * <li>prune all loose objects which are now reachable by packs</li>
	 * </ul>
	 *
	 * If {@link #setAuto(boolean)} was set to {@code true} {@code gc} will
	 * first check whether any housekeeping is required; if not, it exits
	 * without performing any work.
	 *
	 * @return the collection of {@link PackFile}'s which are newly created
	 * @throws IOException
	 * @throws ParseException
	 *             If the configuration parameter "gc.pruneexpire" couldn't be
	 *             parsed
	 */
	public Collection<PackFile> gc() throws IOException, ParseException {
		if (automatic && !needGc()) {
			return Collections.emptyList();
		}
		pm.start(6 /* tasks */);
		packRefs();
		// TODO: implement reflog_expire(pm, repo);
		Collection<PackFile> newPacks = repack();
		prune(Collections.<ObjectId> emptySet());
		// TODO: implement rerere_gc(pm);
		return newPacks;
	}

	/**
	 * Loosen objects in a pack file which are not also in the newly-created
	 * pack files.
	 *
	 * @param inserter
	 * @param reader
	 * @param pack
	 * @param existing
	 * @throws IOException
	 */
	private void loosen(ObjectDirectoryInserter inserter, ObjectReader reader, PackFile pack, HashSet<ObjectId> existing)
			throws IOException {
		for (PackIndex.MutableEntry entry : pack) {
			ObjectId oid = entry.toObjectId();
			if (existing.contains(oid)) {
				continue;
			}
			existing.add(oid);
			ObjectLoader loader = reader.open(oid);
			inserter.insert(loader.getType(),
					loader.getSize(),
					loader.openStream(),
					true /* create this object even though it's a duplicate */);
		}
	}

	/**
	 * Delete old pack files. What is 'old' is defined by specifying a set of
	 * old pack files and a set of new pack files. Each pack file contained in
	 * old pack files but not contained in new pack files will be deleted. If
	 * preserveOldPacks is set, keep a copy of the pack file in the preserve
	 * directory. If an expirationDate is set then pack files which are younger
	 * than the expirationDate will not be deleted nor preserved.
	 * <p>
	 * If we're not immediately expiring loose objects, loosen any objects
	 * in the old pack files which aren't in the new pack files.
	 *
	 * @param oldPacks
	 * @param newPacks
	 * @throws ParseException
	 * @throws IOException
	 */
	private void deleteOldPacks(Collection<PackFile> oldPacks,
			Collection<PackFile> newPacks) throws ParseException, IOException {
		HashSet<ObjectId> ids = new HashSet<>();
		for (PackFile pack : newPacks) {
			for (PackIndex.MutableEntry entry : pack) {
				ids.add(entry.toObjectId());
			}
		}
		ObjectReader reader = repo.newObjectReader();
		ObjectDirectory dir = repo.getObjectDatabase();
		ObjectDirectoryInserter inserter = dir.newInserter();
		boolean shouldLoosen = !"now".equals(getPruneExpireStr()) && //$NON-NLS-1$
			getExpireDate() < Long.MAX_VALUE;

		prunePreserved();
		long packExpireDate = getPackExpireDate();
		oldPackLoop: for (PackFile oldPack : oldPacks) {
			checkCancelled();
			String oldName = oldPack.getPackName();
			// check whether an old pack file is also among the list of new
			// pack files. Then we must not delete it.
			for (PackFile newPack : newPacks)
				if (oldName.equals(newPack.getPackName()))
					continue oldPackLoop;

			if (!oldPack.shouldBeKept()
					&& repo.getFS().lastModified(
							oldPack.getPackFile()) < packExpireDate) {
				oldPack.close();
				if (shouldLoosen) {
					loosen(inserter, reader, oldPack, ids);
				}
				prunePack(oldName);
			}
		}

		// close the complete object database. That's my only chance to force
		// rescanning and to detect that certain pack files are now deleted.
		repo.getObjectDatabase().close();
	}

	/**
	 * Deletes old pack file, unless 'preserve-oldpacks' is set, in which case it
	 * moves the pack file to the preserved directory
	 *
	 * @param packFile
	 * @param packName
	 * @param ext
	 * @param deleteOptions
	 * @throws IOException
	 */
	private void removeOldPack(File packFile, String packName, PackExt ext,
			int deleteOptions) throws IOException {
		if (pconfig != null && pconfig.isPreserveOldPacks()) {
			File oldPackDir = repo.getObjectDatabase().getPreservedDirectory();
			FileUtils.mkdir(oldPackDir, true);

			String oldPackName = "pack-" + packName + ".old-" + ext.getExtension();  //$NON-NLS-1$ //$NON-NLS-2$
			File oldPackFile = new File(oldPackDir, oldPackName);
			FileUtils.rename(packFile, oldPackFile);
		} else {
			FileUtils.delete(packFile, deleteOptions);
		}
	}

	/**
	 * Delete the preserved directory including all pack files within
	 */
	private void prunePreserved() {
		if (pconfig != null && pconfig.isPrunePreserved()) {
			try {
				FileUtils.delete(repo.getObjectDatabase().getPreservedDirectory(),
						FileUtils.RECURSIVE | FileUtils.RETRY | FileUtils.SKIP_MISSING);
			} catch (IOException e) {
				// Deletion of the preserved pack files failed. Silently return.
			}
		}
	}

	/**
	 * Delete files associated with a single pack file. First try to delete the
	 * ".pack" file because on some platforms the ".pack" file may be locked and
	 * can't be deleted. In such a case it is better to detect this early and
	 * give up on deleting files for this packfile. Otherwise we may delete the
	 * ".index" file and when failing to delete the ".pack" file we are left
	 * with a ".pack" file without a ".index" file.
	 *
	 * @param packName
	 */
	private void prunePack(String packName) {
		PackExt[] extensions = PackExt.values();
		try {
			// Delete the .pack file first and if this fails give up on deleting
			// the other files
			int deleteOptions = FileUtils.RETRY | FileUtils.SKIP_MISSING;
			for (PackExt ext : extensions)
				if (PackExt.PACK.equals(ext)) {
					File f = nameFor(packName, "." + ext.getExtension()); //$NON-NLS-1$
					removeOldPack(f, packName, ext, deleteOptions);
					break;
				}
			// The .pack file has been deleted. Delete as many as the other
			// files as you can.
			deleteOptions |= FileUtils.IGNORE_ERRORS;
			for (PackExt ext : extensions) {
				if (!PackExt.PACK.equals(ext)) {
					File f = nameFor(packName, "." + ext.getExtension()); //$NON-NLS-1$
					removeOldPack(f, packName, ext, deleteOptions);
				}
			}
		} catch (IOException e) {
			// Deletion of the .pack file failed. Silently return.
		}
	}

	/**
	 * Like "git prune-packed" this method tries to prune all loose objects
	 * which can be found in packs. If certain objects can't be pruned (e.g.
	 * because the filesystem delete operation fails) this is silently ignored.
	 *
	 * @throws IOException
	 */
	public void prunePacked() throws IOException {
		ObjectDirectory objdb = repo.getObjectDatabase();
		Collection<PackFile> packs = objdb.getPacks();
		File objects = repo.getObjectsDirectory();
		String[] fanout = objects.list();

		if (fanout != null && fanout.length > 0) {
			pm.beginTask(JGitText.get().pruneLoosePackedObjects, fanout.length);
			try {
				for (String d : fanout) {
					checkCancelled();
					pm.update(1);
					if (d.length() != 2)
						continue;
					String[] entries = new File(objects, d).list();
					if (entries == null)
						continue;
					for (String e : entries) {
						checkCancelled();
						if (e.length() != Constants.OBJECT_ID_STRING_LENGTH - 2)
							continue;
						ObjectId id;
						try {
							id = ObjectId.fromString(d + e);
						} catch (IllegalArgumentException notAnObject) {
							// ignoring the file that does not represent loose
							// object
							continue;
						}
						boolean found = false;
						for (PackFile p : packs) {
							checkCancelled();
							if (p.hasObject(id)) {
								found = true;
								break;
							}
						}
						if (found)
							FileUtils.delete(objdb.fileFor(id), FileUtils.RETRY
									| FileUtils.SKIP_MISSING
									| FileUtils.IGNORE_ERRORS);
					}
				}
			} finally {
				pm.endTask();
			}
		}
	}

	/**
	 * Like "git prune" this method tries to prune all loose objects which are
	 * unreferenced. If certain objects can't be pruned (e.g. because the
	 * filesystem delete operation fails) this is silently ignored.
	 *
	 * @param objectsToKeep
	 *            a set of objects which should explicitly not be pruned
	 *
	 * @throws IOException
	 * @throws ParseException
	 *             If the configuration parameter "gc.pruneexpire" couldn't be
	 *             parsed
	 */
	public void prune(Set<ObjectId> objectsToKeep) throws IOException,
			ParseException {
		long expireDate = getExpireDate();

		// Collect all loose objects which are old enough, not referenced from
		// the index and not in objectsToKeep
		Map<ObjectId, File> deletionCandidates = new HashMap<ObjectId, File>();
		Set<ObjectId> indexObjects = null;
		File objects = repo.getObjectsDirectory();
		String[] fanout = objects.list();
		if (fanout == null || fanout.length == 0) {
			return;
		}
		pm.beginTask(JGitText.get().pruneLooseUnreferencedObjects,
				fanout.length);
		try {
			for (String d : fanout) {
				checkCancelled();
				pm.update(1);
				if (d.length() != 2)
					continue;
				File[] entries = new File(objects, d).listFiles();
				if (entries == null)
					continue;
				for (File f : entries) {
					checkCancelled();
					String fName = f.getName();
					if (fName.length() != Constants.OBJECT_ID_STRING_LENGTH - 2)
						continue;
					if (repo.getFS().lastModified(f) >= expireDate)
						continue;
					try {
						ObjectId id = ObjectId.fromString(d + fName);
						if (objectsToKeep.contains(id))
							continue;
						if (indexObjects == null)
							indexObjects = listNonHEADIndexObjects();
						if (indexObjects.contains(id))
							continue;
						deletionCandidates.put(id, f);
					} catch (IllegalArgumentException notAnObject) {
						// ignoring the file that does not represent loose
						// object
						continue;
					}
				}
			}
		} finally {
			pm.endTask();
		}

		if (deletionCandidates.isEmpty()) {
			return;
		}

		checkCancelled();

		// From the set of current refs remove all those which have been handled
		// during last repack(). Only those refs will survive which have been
		// added or modified since the last repack. Only these can save existing
		// loose refs from being pruned.
		Collection<Ref> newRefs;
		if (lastPackedRefs == null || lastPackedRefs.isEmpty())
			newRefs = getAllRefs();
		else {
			Map<String, Ref> last = new HashMap<>();
			for (Ref r : lastPackedRefs) {
				last.put(r.getName(), r);
			}
			newRefs = new ArrayList<>();
			for (Ref r : getAllRefs()) {
				Ref old = last.get(r.getName());
				if (!equals(r, old)) {
					newRefs.add(r);
				}
			}
		}

		if (!newRefs.isEmpty()) {
			// There are new/modified refs! Check which loose objects are now
			// referenced by these modified refs (or their reflogentries).
			// Remove these loose objects
			// from the deletionCandidates. When the last candidate is removed
			// leave this method.
			ObjectWalk w = new ObjectWalk(repo);
			try {
				for (Ref cr : newRefs) {
					checkCancelled();
					w.markStart(w.parseAny(cr.getObjectId()));
				}
				if (lastPackedRefs != null)
					for (Ref lpr : lastPackedRefs) {
						w.markUninteresting(w.parseAny(lpr.getObjectId()));
					}
				removeReferenced(deletionCandidates, w);
			} finally {
				w.dispose();
			}
		}

		if (deletionCandidates.isEmpty())
			return;

		// Since we have not left the method yet there are still
		// deletionCandidates. Last chance for these objects not to be pruned is
		// that they are referenced by reflog entries. Even refs which currently
		// point to the same object as during last repack() may have
		// additional reflog entries not handled during last repack()
		ObjectWalk w = new ObjectWalk(repo);
		try {
			for (Ref ar : getAllRefs())
				for (ObjectId id : listRefLogObjects(ar, lastRepackTime)) {
					checkCancelled();
					w.markStart(w.parseAny(id));
				}
			if (lastPackedRefs != null)
				for (Ref lpr : lastPackedRefs) {
					checkCancelled();
					w.markUninteresting(w.parseAny(lpr.getObjectId()));
				}
			removeReferenced(deletionCandidates, w);
		} finally {
			w.dispose();
		}

		if (deletionCandidates.isEmpty())
			return;

		checkCancelled();

		// delete all candidates which have survived: these are unreferenced
		// loose objects. Make a last check, though, to avoid deleting objects
		// that could have been referenced while the candidates list was being
		// built (by an incoming push, for example).
		Set<File> touchedFanout = new HashSet<>();
		for (File f : deletionCandidates.values()) {
			if (f.lastModified() < expireDate) {
				f.delete();
				touchedFanout.add(f.getParentFile());
			}
		}

		for (File f : touchedFanout) {
			FileUtils.delete(f,
					FileUtils.EMPTY_DIRECTORIES_ONLY | FileUtils.IGNORE_ERRORS);
		}

		repo.getObjectDatabase().close();
	}

	private long getExpireDate() throws ParseException {
		long expireDate = Long.MAX_VALUE;

		if (expire == null && expireAgeMillis == -1) {
			String pruneExpireStr = getPruneExpireStr();
			if (pruneExpireStr == null)
				pruneExpireStr = PRUNE_EXPIRE_DEFAULT;
			expire = GitDateParser.parse(pruneExpireStr, null, SystemReader
					.getInstance().getLocale());
			expireAgeMillis = -1;
		}
		if (expire != null)
			expireDate = expire.getTime();
		if (expireAgeMillis != -1)
			expireDate = System.currentTimeMillis() - expireAgeMillis;
		return expireDate;
	}

	private String getPruneExpireStr() {
		return repo.getConfig().getString(
                        ConfigConstants.CONFIG_GC_SECTION, null,
                        ConfigConstants.CONFIG_KEY_PRUNEEXPIRE);
	}

	private long getPackExpireDate() throws ParseException {
		long packExpireDate = Long.MAX_VALUE;

		if (packExpire == null && packExpireAgeMillis == -1) {
			String prunePackExpireStr = repo.getConfig().getString(
					ConfigConstants.CONFIG_GC_SECTION, null,
					ConfigConstants.CONFIG_KEY_PRUNEPACKEXPIRE);
			if (prunePackExpireStr == null)
				prunePackExpireStr = PRUNE_PACK_EXPIRE_DEFAULT;
			packExpire = GitDateParser.parse(prunePackExpireStr, null,
					SystemReader.getInstance().getLocale());
			packExpireAgeMillis = -1;
		}
		if (packExpire != null)
			packExpireDate = packExpire.getTime();
		if (packExpireAgeMillis != -1)
			packExpireDate = System.currentTimeMillis() - packExpireAgeMillis;
		return packExpireDate;
	}

	/**
	 * Remove all entries from a map which key is the id of an object referenced
	 * by the given ObjectWalk
	 *
	 * @param id2File
	 * @param w
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	private void removeReferenced(Map<ObjectId, File> id2File,
			ObjectWalk w) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		RevObject ro = w.next();
		while (ro != null) {
			checkCancelled();
			if (id2File.remove(ro.getId()) != null)
				if (id2File.isEmpty())
					return;
			ro = w.next();
		}
		ro = w.nextObject();
		while (ro != null) {
			checkCancelled();
			if (id2File.remove(ro.getId()) != null)
				if (id2File.isEmpty())
					return;
			ro = w.nextObject();
		}
	}

	private static boolean equals(Ref r1, Ref r2) {
		if (r1 == null || r2 == null)
			return false;
		if (r1.isSymbolic()) {
			if (!r2.isSymbolic())
				return false;
			return r1.getTarget().getName().equals(r2.getTarget().getName());
		} else {
			if (r2.isSymbolic()) {
				return false;
			}
			return Objects.equals(r1.getObjectId(), r2.getObjectId());
		}
	}

	/**
	 * Packs all non-symbolic, loose refs into packed-refs.
	 *
	 * @throws IOException
	 */
	public void packRefs() throws IOException {
		Collection<Ref> refs = repo.getRefDatabase().getRefs(Constants.R_REFS).values();
		List<String> refsToBePacked = new ArrayList<String>(refs.size());
		pm.beginTask(JGitText.get().packRefs, refs.size());
		try {
			for (Ref ref : refs) {
				checkCancelled();
				if (!ref.isSymbolic() && ref.getStorage().isLoose())
					refsToBePacked.add(ref.getName());
				pm.update(1);
			}
			((RefDirectory) repo.getRefDatabase()).pack(refsToBePacked);
		} finally {
			pm.endTask();
		}
	}

	/**
	 * Packs all objects which reachable from any of the heads into one pack
	 * file. Additionally all objects which are not reachable from any head but
	 * which are reachable from any of the other refs (e.g. tags), special refs
	 * (e.g. FETCH_HEAD) or index are packed into a separate pack file. Objects
	 * included in pack files which have a .keep file associated are never
	 * repacked. All old pack files which existed before are deleted.
	 *
	 * @return a collection of the newly created pack files
	 * @throws IOException
	 *             when during reading of refs, index, packfiles, objects,
	 *             reflog-entries or during writing to the packfiles
	 *             {@link IOException} occurs
	 */
	public Collection<PackFile> repack() throws IOException {
		Collection<PackFile> toBeDeleted = repo.getObjectDatabase().getPacks();

		long time = System.currentTimeMillis();
		Collection<Ref> refsBefore = getAllRefs();

		Set<ObjectId> allHeads = new HashSet<ObjectId>();
		Set<ObjectId> nonHeads = new HashSet<ObjectId>();
		Set<ObjectId> txnHeads = new HashSet<ObjectId>();
		Set<ObjectId> tagTargets = new HashSet<ObjectId>();
		Set<ObjectId> indexObjects = listNonHEADIndexObjects();
		RefDatabase refdb = repo.getRefDatabase();

		for (Ref ref : refsBefore) {
			checkCancelled();
			nonHeads.addAll(listRefLogObjects(ref, 0));
			if (ref.isSymbolic() || ref.getObjectId() == null)
				continue;
			if (isHead(ref) || isTag(ref))
				allHeads.add(ref.getObjectId());
			else if (RefTreeNames.isRefTree(refdb, ref.getName()))
				txnHeads.add(ref.getObjectId());
			else
				nonHeads.add(ref.getObjectId());
			if (ref.getPeeledObjectId() != null)
				tagTargets.add(ref.getPeeledObjectId());
		}

		List<ObjectIdSet> excluded = new LinkedList<ObjectIdSet>();
		for (final PackFile f : repo.getObjectDatabase().getPacks()) {
			checkCancelled();
			if (f.shouldBeKept())
				excluded.add(f.getIndex());
		}

		tagTargets.addAll(allHeads);
		nonHeads.addAll(indexObjects);

		List<PackFile> ret = new ArrayList<PackFile>(2);
		PackFile heads = null;
		if (!allHeads.isEmpty()) {
			heads = writePack(allHeads, Collections.<ObjectId> emptySet(),
					tagTargets, excluded);
			if (heads != null) {
				ret.add(heads);
				excluded.add(0, heads.getIndex());
			}
		}
		if (!nonHeads.isEmpty()) {
			PackFile rest = writePack(nonHeads, allHeads, tagTargets, excluded);
			if (rest != null)
				ret.add(rest);
		}
		if (!txnHeads.isEmpty()) {
			PackFile txn = writePack(txnHeads, PackWriter.NONE, null, excluded);
			if (txn != null)
				ret.add(txn);
		}
		try {
			deleteOldPacks(toBeDeleted, ret);
		} catch (ParseException e) {
			// TODO: the exception has to be wrapped into an IOException because
			// throwing the ParseException directly would break the API, instead
			// we should throw a ConfigInvalidException
			throw new IOException(e);
		}
		prunePacked();
		deleteOrphans();

		lastPackedRefs = refsBefore;
		lastRepackTime = time;
		return ret;
	}

	private static boolean isHead(Ref ref) {
		return ref.getName().startsWith(Constants.R_HEADS);
	}

	private static boolean isTag(Ref ref) {
		return ref.getName().startsWith(Constants.R_TAGS);
	}

	/**
	 * Deletes orphans
	 * <p>
	 * A file is considered an orphan if it is either a "bitmap" or an index
	 * file, and its corresponding pack file is missing in the list.
	 * </p>
	 */
	private void deleteOrphans() {
		Path packDir = Paths.get(repo.getObjectsDirectory().getAbsolutePath(),
				"pack"); //$NON-NLS-1$
		List<String> fileNames = null;
		try (Stream<Path> files = Files.list(packDir)) {
			fileNames = files.map(path -> path.getFileName().toString())
					.filter(name -> {
						return (name.endsWith(PACK_EXT)
								|| name.endsWith(BITMAP_EXT)
								|| name.endsWith(INDEX_EXT));
					}).sorted(Collections.reverseOrder())
					.collect(Collectors.toList());
		} catch (IOException e1) {
			// ignore
		}
		if (fileNames == null) {
			return;
		}

		String base = null;
		for (String n : fileNames) {
			if (n.endsWith(PACK_EXT)) {
				base = n.substring(0, n.lastIndexOf('.'));
			} else {
				if (base == null || !n.startsWith(base)) {
					try {
						Files.delete(new File(packDir.toFile(), n).toPath());
					} catch (IOException e) {
						LOG.error(e.getMessage(), e);
					}
				}
			}
		}
	}

	/**
	 * @param ref
	 *            the ref which log should be inspected
	 * @param minTime only reflog entries not older then this time are processed
	 * @return the {@link ObjectId}s contained in the reflog
	 * @throws IOException
	 */
	private Set<ObjectId> listRefLogObjects(Ref ref, long minTime) throws IOException {
		ReflogReader reflogReader = repo.getReflogReader(ref.getName());
		if (reflogReader == null) {
			return Collections.emptySet();
		}
		List<ReflogEntry> rlEntries = reflogReader
				.getReverseEntries();
		if (rlEntries == null || rlEntries.isEmpty())
			return Collections.<ObjectId> emptySet();
		Set<ObjectId> ret = new HashSet<ObjectId>();
		for (ReflogEntry e : rlEntries) {
			if (e.getWho().getWhen().getTime() < minTime)
				break;
			ObjectId newId = e.getNewId();
			if (newId != null && !ObjectId.zeroId().equals(newId))
				ret.add(newId);
			ObjectId oldId = e.getOldId();
			if (oldId != null && !ObjectId.zeroId().equals(oldId))
				ret.add(oldId);
		}
		return ret;
	}

	/**
	 * Returns a collection of all refs and additional refs.
	 *
	 * Additional refs which don't start with "refs/" are not returned because
	 * they should not save objects from being garbage collected. Examples for
	 * such references are ORIG_HEAD, MERGE_HEAD, FETCH_HEAD and
	 * CHERRY_PICK_HEAD.
	 *
	 * @return a collection of refs pointing to live objects.
	 * @throws IOException
	 */
	private Collection<Ref> getAllRefs() throws IOException {
		RefDatabase refdb = repo.getRefDatabase();
		Collection<Ref> refs = refdb.getRefs(RefDatabase.ALL).values();
		List<Ref> addl = refdb.getAdditionalRefs();
		if (!addl.isEmpty()) {
			List<Ref> all = new ArrayList<>(refs.size() + addl.size());
			all.addAll(refs);
			// add additional refs which start with refs/
			for (Ref r : addl) {
				checkCancelled();
				if (r.getName().startsWith(Constants.R_REFS)) {
					all.add(r);
				}
			}
			return all;
		}
		return refs;
	}

	/**
	 * Return a list of those objects in the index which differ from whats in
	 * HEAD
	 *
	 * @return a set of ObjectIds of changed objects in the index
	 * @throws IOException
	 * @throws CorruptObjectException
	 * @throws NoWorkTreeException
	 */
	private Set<ObjectId> listNonHEADIndexObjects()
			throws CorruptObjectException, IOException {
		if (repo.isBare()) {
			return Collections.emptySet();
		}
		try (TreeWalk treeWalk = new TreeWalk(repo)) {
			treeWalk.addTree(new DirCacheIterator(repo.readDirCache()));
			ObjectId headID = repo.resolve(Constants.HEAD);
			if (headID != null) {
				try (RevWalk revWalk = new RevWalk(repo)) {
					treeWalk.addTree(revWalk.parseTree(headID));
				}
			}

			treeWalk.setFilter(TreeFilter.ANY_DIFF);
			treeWalk.setRecursive(true);
			Set<ObjectId> ret = new HashSet<ObjectId>();

			while (treeWalk.next()) {
				checkCancelled();
				ObjectId objectId = treeWalk.getObjectId(0);
				switch (treeWalk.getRawMode(0) & FileMode.TYPE_MASK) {
				case FileMode.TYPE_MISSING:
				case FileMode.TYPE_GITLINK:
					continue;
				case FileMode.TYPE_TREE:
				case FileMode.TYPE_FILE:
				case FileMode.TYPE_SYMLINK:
					ret.add(objectId);
					continue;
				default:
					throw new IOException(MessageFormat.format(
							JGitText.get().corruptObjectInvalidMode3,
							String.format("%o", //$NON-NLS-1$
									Integer.valueOf(treeWalk.getRawMode(0))),
							(objectId == null) ? "null" : objectId.name(), //$NON-NLS-1$
							treeWalk.getPathString(), //
							repo.getIndexFile()));
				}
			}
			return ret;
		}
	}

	private PackFile writePack(@NonNull Set<? extends ObjectId> want,
			@NonNull Set<? extends ObjectId> have, Set<ObjectId> tagTargets,
			List<ObjectIdSet> excludeObjects) throws IOException {
		checkCancelled();
		File tmpPack = null;
		Map<PackExt, File> tmpExts = new TreeMap<PackExt, File>(
				new Comparator<PackExt>() {
					public int compare(PackExt o1, PackExt o2) {
						// INDEX entries must be returned last, so the pack
						// scanner does pick up the new pack until all the
						// PackExt entries have been written.
						if (o1 == o2)
							return 0;
						if (o1 == PackExt.INDEX)
							return 1;
						if (o2 == PackExt.INDEX)
							return -1;
						return Integer.signum(o1.hashCode() - o2.hashCode());
					}

				});
		try (PackWriter pw = new PackWriter(
				(pconfig == null) ? new PackConfig(repo) : pconfig,
				repo.newObjectReader())) {
			// prepare the PackWriter
			pw.setDeltaBaseAsOffset(true);
			pw.setReuseDeltaCommits(false);
			if (tagTargets != null)
				pw.setTagTargets(tagTargets);
			if (excludeObjects != null)
				for (ObjectIdSet idx : excludeObjects)
					pw.excludeObjects(idx);
			pw.preparePack(pm, want, have);
			if (pw.getObjectCount() == 0)
				return null;
			checkCancelled();

			// create temporary files
			String id = pw.computeName().getName();
			File packdir = new File(repo.getObjectsDirectory(), "pack"); //$NON-NLS-1$
			tmpPack = File.createTempFile("gc_", ".pack_tmp", packdir); //$NON-NLS-1$ //$NON-NLS-2$
			final String tmpBase = tmpPack.getName()
					.substring(0, tmpPack.getName().lastIndexOf('.'));
			File tmpIdx = new File(packdir, tmpBase + ".idx_tmp"); //$NON-NLS-1$
			tmpExts.put(INDEX, tmpIdx);

			if (!tmpIdx.createNewFile())
				throw new IOException(MessageFormat.format(
						JGitText.get().cannotCreateIndexfile, tmpIdx.getPath()));

			// write the packfile
			FileOutputStream fos = new FileOutputStream(tmpPack);
			FileChannel channel = fos.getChannel();
			OutputStream channelStream = Channels.newOutputStream(channel);
			try {
				pw.writePack(pm, pm, channelStream);
			} finally {
				channel.force(true);
				channelStream.close();
				fos.close();
			}

			// write the packindex
			fos = new FileOutputStream(tmpIdx);
			FileChannel idxChannel = fos.getChannel();
			OutputStream idxStream = Channels.newOutputStream(idxChannel);
			try {
				pw.writeIndex(idxStream);
			} finally {
				idxChannel.force(true);
				idxStream.close();
				fos.close();
			}

			if (pw.prepareBitmapIndex(pm)) {
				File tmpBitmapIdx = new File(packdir, tmpBase + ".bitmap_tmp"); //$NON-NLS-1$
				tmpExts.put(BITMAP_INDEX, tmpBitmapIdx);

				if (!tmpBitmapIdx.createNewFile())
					throw new IOException(MessageFormat.format(
							JGitText.get().cannotCreateIndexfile,
							tmpBitmapIdx.getPath()));

				fos = new FileOutputStream(tmpBitmapIdx);
				idxChannel = fos.getChannel();
				idxStream = Channels.newOutputStream(idxChannel);
				try {
					pw.writeBitmapIndex(idxStream);
				} finally {
					idxChannel.force(true);
					idxStream.close();
					fos.close();
				}
			}

			// rename the temporary files to real files
			File realPack = nameFor(id, ".pack"); //$NON-NLS-1$

			// if the packfile already exists (because we are rewriting a
			// packfile for the same set of objects maybe with different
			// PackConfig) then make sure we get rid of all handles on the file.
			// Windows will not allow for rename otherwise.
			if (realPack.exists())
				for (PackFile p : repo.getObjectDatabase().getPacks())
					if (realPack.getPath().equals(p.getPackFile().getPath())) {
						p.close();
						break;
					}
			tmpPack.setReadOnly();

			FileUtils.rename(tmpPack, realPack, StandardCopyOption.ATOMIC_MOVE);
			for (Map.Entry<PackExt, File> tmpEntry : tmpExts.entrySet()) {
				File tmpExt = tmpEntry.getValue();
				tmpExt.setReadOnly();

				File realExt = nameFor(id,
						"." + tmpEntry.getKey().getExtension()); //$NON-NLS-1$
				try {
					FileUtils.rename(tmpExt, realExt,
							StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException e) {
					File newExt = new File(realExt.getParentFile(),
							realExt.getName() + ".new"); //$NON-NLS-1$
					try {
						FileUtils.rename(tmpExt, newExt,
								StandardCopyOption.ATOMIC_MOVE);
					} catch (IOException e2) {
						newExt = tmpExt;
						e = e2;
					}
					throw new IOException(MessageFormat.format(
							JGitText.get().panicCantRenameIndexFile, newExt,
							realExt), e);
				}
			}

			return repo.getObjectDatabase().openPack(realPack);
		} finally {
			if (tmpPack != null && tmpPack.exists())
				tmpPack.delete();
			for (File tmpExt : tmpExts.values()) {
				if (tmpExt.exists())
					tmpExt.delete();
			}
		}
	}

	private File nameFor(String name, String ext) {
		File packdir = new File(repo.getObjectsDirectory(), "pack"); //$NON-NLS-1$
		return new File(packdir, "pack-" + name + ext); //$NON-NLS-1$
	}

	private void checkCancelled() throws CancelledException {
		if (pm.isCancelled()) {
			throw new CancelledException(JGitText.get().operationCanceled);
		}
	}

	/**
	 * A class holding statistical data for a FileRepository regarding how many
	 * objects are stored as loose or packed objects
	 */
	public static class RepoStatistics {
		/**
		 * The number of objects stored in pack files. If the same object is
		 * stored in multiple pack files then it is counted as often as it
		 * occurs in pack files.
		 */
		public long numberOfPackedObjects;

		/**
		 * The number of pack files
		 */
		public long numberOfPackFiles;

		/**
		 * The number of objects stored as loose objects.
		 */
		public long numberOfLooseObjects;

		/**
		 * The sum of the sizes of all files used to persist loose objects.
		 */
		public long sizeOfLooseObjects;

		/**
		 * The sum of the sizes of all pack files.
		 */
		public long sizeOfPackedObjects;

		/**
		 * The number of loose refs.
		 */
		public long numberOfLooseRefs;

		/**
		 * The number of refs stored in pack files.
		 */
		public long numberOfPackedRefs;

		/**
		 * The number of bitmaps in the bitmap indices.
		 */
		public long numberOfBitmaps;

		public String toString() {
			final StringBuilder b = new StringBuilder();
			b.append("numberOfPackedObjects=").append(numberOfPackedObjects); //$NON-NLS-1$
			b.append(", numberOfPackFiles=").append(numberOfPackFiles); //$NON-NLS-1$
			b.append(", numberOfLooseObjects=").append(numberOfLooseObjects); //$NON-NLS-1$
			b.append(", numberOfLooseRefs=").append(numberOfLooseRefs); //$NON-NLS-1$
			b.append(", numberOfPackedRefs=").append(numberOfPackedRefs); //$NON-NLS-1$
			b.append(", sizeOfLooseObjects=").append(sizeOfLooseObjects); //$NON-NLS-1$
			b.append(", sizeOfPackedObjects=").append(sizeOfPackedObjects); //$NON-NLS-1$
			b.append(", numberOfBitmaps=").append(numberOfBitmaps); //$NON-NLS-1$
			return b.toString();
		}
	}

	/**
	 * Returns information about objects and pack files for a FileRepository.
	 *
	 * @return information about objects and pack files for a FileRepository
	 * @throws IOException
	 */
	public RepoStatistics getStatistics() throws IOException {
		RepoStatistics ret = new RepoStatistics();
		Collection<PackFile> packs = repo.getObjectDatabase().getPacks();
		for (PackFile f : packs) {
			ret.numberOfPackedObjects += f.getIndex().getObjectCount();
			ret.numberOfPackFiles++;
			ret.sizeOfPackedObjects += f.getPackFile().length();
			if (f.getBitmapIndex() != null)
				ret.numberOfBitmaps += f.getBitmapIndex().getBitmapCount();
		}
		File objDir = repo.getObjectsDirectory();
		String[] fanout = objDir.list();
		if (fanout != null && fanout.length > 0) {
			for (String d : fanout) {
				if (d.length() != 2)
					continue;
				File[] entries = new File(objDir, d).listFiles();
				if (entries == null)
					continue;
				for (File f : entries) {
					if (f.getName().length() != Constants.OBJECT_ID_STRING_LENGTH - 2)
						continue;
					ret.numberOfLooseObjects++;
					ret.sizeOfLooseObjects += f.length();
				}
			}
		}

		RefDatabase refDb = repo.getRefDatabase();
		for (Ref r : refDb.getRefs(RefDatabase.ALL).values()) {
			Storage storage = r.getStorage();
			if (storage == Storage.LOOSE || storage == Storage.LOOSE_PACKED)
				ret.numberOfLooseRefs++;
			if (storage == Storage.PACKED || storage == Storage.LOOSE_PACKED)
				ret.numberOfPackedRefs++;
		}

		return ret;
	}

	/**
	 * Set the progress monitor used for garbage collection methods.
	 *
	 * @param pm
	 * @return this
	 */
	public GC setProgressMonitor(ProgressMonitor pm) {
		this.pm = (pm == null) ? NullProgressMonitor.INSTANCE : pm;
		return this;
	}

	/**
	 * During gc() or prune() each unreferenced, loose object which has been
	 * created or modified in the last <code>expireAgeMillis</code> milliseconds
	 * will not be pruned. Only older objects may be pruned. If set to 0 then
	 * every object is a candidate for pruning.
	 *
	 * @param expireAgeMillis
	 *            minimal age of objects to be pruned in milliseconds.
	 */
	public void setExpireAgeMillis(long expireAgeMillis) {
		this.expireAgeMillis = expireAgeMillis;
		expire = null;
	}

	/**
	 * During gc() or prune() packfiles which are created or modified in the
	 * last <code>packExpireAgeMillis</code> milliseconds will not be deleted.
	 * Only older packfiles may be deleted. If set to 0 then every packfile is a
	 * candidate for deletion.
	 *
	 * @param packExpireAgeMillis
	 *            minimal age of packfiles to be deleted in milliseconds.
	 */
	public void setPackExpireAgeMillis(long packExpireAgeMillis) {
		this.packExpireAgeMillis = packExpireAgeMillis;
		expire = null;
	}

	/**
	 * Set the PackConfig used when (re-)writing packfiles. This allows to
	 * influence how packs are written and to implement something similar to
	 * "git gc --aggressive"
	 *
	 * @param pconfig
	 *            the {@link PackConfig} used when writing packs
	 */
	public void setPackConfig(PackConfig pconfig) {
		this.pconfig = pconfig;
	}

	/**
	 * During gc() or prune() each unreferenced, loose object which has been
	 * created or modified after or at <code>expire</code> will not be pruned.
	 * Only older objects may be pruned. If set to null then every object is a
	 * candidate for pruning.
	 *
	 * @param expire
	 *            instant in time which defines object expiration
	 *            objects with modification time before this instant are expired
	 *            objects with modification time newer or equal to this instant
	 *            are not expired
	 */
	public void setExpire(Date expire) {
		this.expire = expire;
		expireAgeMillis = -1;
	}

	/**
	 * During gc() or prune() packfiles which are created or modified after or
	 * at <code>packExpire</code> will not be deleted. Only older packfiles may
	 * be deleted. If set to null then every packfile is a candidate for
	 * deletion.
	 *
	 * @param packExpire
	 *            instant in time which defines packfile expiration
	 */
	public void setPackExpire(Date packExpire) {
		this.packExpire = packExpire;
		packExpireAgeMillis = -1;
	}

	/**
	 * Set the {@code gc --auto} option.
	 *
	 * With this option, gc checks whether any housekeeping is required; if not,
	 * it exits without performing any work. Some JGit commands run
	 * {@code gc --auto} after performing operations that could create many
	 * loose objects.
	 * <p/>
	 * Housekeeping is required if there are too many loose objects or too many
	 * packs in the repository. If the number of loose objects exceeds the value
	 * of the gc.auto option JGit GC consolidates all existing packs into a
	 * single pack (equivalent to {@code -A} option), whereas git-core would
	 * combine all loose objects into a single pack using {@code repack -d -l}.
	 * Setting the value of {@code gc.auto} to 0 disables automatic packing of
	 * loose objects.
	 * <p/>
	 * If the number of packs exceeds the value of {@code gc.autoPackLimit},
	 * then existing packs (except those marked with a .keep file) are
	 * consolidated into a single pack by using the {@code -A} option of repack.
	 * Setting {@code gc.autoPackLimit} to 0 disables automatic consolidation of
	 * packs.
	 * <p/>
	 * Like git the following jgit commands run auto gc:
	 * <ul>
	 * <li>fetch</li>
	 * <li>merge</li>
	 * <li>rebase</li>
	 * <li>receive-pack</li>
	 * </ul>
	 * The auto gc for receive-pack can be suppressed by setting the config
	 * option {@code receive.autogc = false}
	 *
	 * @param auto
	 *            defines whether gc should do automatic housekeeping
	 */
	public void setAuto(boolean auto) {
		this.automatic = auto;
	}

	private boolean needGc() {
		if (tooManyPacks()) {
			addRepackAllOption();
		} else if (!tooManyLooseObjects()) {
			return false;
		}
		// TODO run pre-auto-gc hook, if it fails return false
		return true;
	}

	private void addRepackAllOption() {
		// TODO: if JGit GC is enhanced to support repack's option -l this
		// method needs to be implemented
	}

	/**
	 * @return {@code true} if number of packs > gc.autopacklimit (default 50)
	 */
	boolean tooManyPacks() {
		int autopacklimit = repo.getConfig().getInt(
				ConfigConstants.CONFIG_GC_SECTION,
				ConfigConstants.CONFIG_KEY_AUTOPACKLIMIT,
				DEFAULT_AUTOPACKLIMIT);
		if (autopacklimit <= 0) {
			return false;
		}
		// JGit always creates two packfiles, one for the objects reachable from
		// branches, and another one for the rest
		return repo.getObjectDatabase().getPacks().size() > (autopacklimit + 1);
	}

	/**
	 * Quickly estimate number of loose objects, SHA1 is distributed evenly so
	 * counting objects in one directory (bucket 17) is sufficient
	 *
	 * @return {@code true} if number of loose objects > gc.auto (default 6700)
	 */
	boolean tooManyLooseObjects() {
		int auto = repo.getConfig().getInt(ConfigConstants.CONFIG_GC_SECTION,
				ConfigConstants.CONFIG_KEY_AUTO, DEFAULT_AUTOLIMIT);
		if (auto <= 0) {
			return false;
		}
		int n = 0;
		int threshold = (auto + 255) / 256;
		Path dir = repo.getObjectsDirectory().toPath().resolve("17"); //$NON-NLS-1$
		if (!Files.exists(dir)) {
			return false;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir,
				new DirectoryStream.Filter<Path>() {

					public boolean accept(Path file) throws IOException {
						Path fileName = file.getFileName();
						return Files.isRegularFile(file) && fileName != null
								&& PATTERN_LOOSE_OBJECT
										.matcher(fileName.toString()).matches();
					}
				})) {
			for (Iterator<Path> iter = stream.iterator(); iter.hasNext();
					iter.next()) {
				if (++n > threshold) {
					return true;
				}
			}
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
		return false;
	}
}
