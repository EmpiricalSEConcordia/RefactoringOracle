/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg
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

package org.eclipse.jgit.merge;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import java.io.IOException;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.SampleDataRepositoryTestCase;
import org.eclipse.jgit.treewalk.TreeWalk;

public class SimpleMergeTest extends SampleDataRepositoryTestCase {

	public void testOurs() throws IOException {
		Merger ourMerger = MergeStrategy.OURS.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a"), db.resolve("c") });
		assertTrue(merge);
		assertEquals(db.mapTree("a").getId(), ourMerger.getResultTreeId());
	}

	public void testTheirs() throws IOException {
		Merger ourMerger = MergeStrategy.THEIRS.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a"), db.resolve("c") });
		assertTrue(merge);
		assertEquals(db.mapTree("c").getId(), ourMerger.getResultTreeId());
	}

	public void testTrivialTwoWay() throws IOException {
		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a"), db.resolve("c") });
		assertTrue(merge);
		assertEquals("02ba32d3649e510002c21651936b7077aa75ffa9",ourMerger.getResultTreeId().name());
	}

	public void testTrivialTwoWay_disjointhistories() throws IOException {
		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a"), db.resolve("c~4") });
		assertTrue(merge);
		assertEquals("86265c33b19b2be71bdd7b8cb95823f2743d03a8",ourMerger.getResultTreeId().name());
	}

	public void testTrivialTwoWay_ok() throws IOException {
		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a^0^0^0"), db.resolve("a^0^0^1") });
		assertTrue(merge);
		assertEquals(db.mapTree("a^0^0").getId(), ourMerger.getResultTreeId());
	}

	public void testTrivialTwoWay_conflict() throws IOException {
		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("f"), db.resolve("g") });
		assertFalse(merge);
	}

	public void testTrivialTwoWay_validSubtreeSort() throws Exception {
		final DirCache treeB = db.readDirCache();
		final DirCache treeO = db.readDirCache();
		final DirCache treeT = db.readDirCache();
		{
			final DirCacheBuilder b = treeB.builder();
			final DirCacheBuilder o = treeO.builder();
			final DirCacheBuilder t = treeT.builder();

			b.add(makeEntry("libelf-po/a", FileMode.REGULAR_FILE));
			b.add(makeEntry("libelf/c", FileMode.REGULAR_FILE));

			o.add(makeEntry("Makefile", FileMode.REGULAR_FILE));
			o.add(makeEntry("libelf-po/a", FileMode.REGULAR_FILE));
			o.add(makeEntry("libelf/c", FileMode.REGULAR_FILE));

			t.add(makeEntry("libelf-po/a", FileMode.REGULAR_FILE));
			t.add(makeEntry("libelf/c", FileMode.REGULAR_FILE, "blah"));

			b.finish();
			o.finish();
			t.finish();
		}

		final ObjectInserter ow = db.newObjectInserter();
		final ObjectId b = commit(ow, treeB, new ObjectId[] {});
		final ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		final ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertTrue(merge);

		final TreeWalk tw = new TreeWalk(db);
		tw.setRecursive(true);
		tw.reset(ourMerger.getResultTreeId());

		assertTrue(tw.next());
		assertEquals("Makefile", tw.getPathString());
		assertCorrectId(treeO, tw);

		assertTrue(tw.next());
		assertEquals("libelf-po/a", tw.getPathString());
		assertCorrectId(treeO, tw);

		assertTrue(tw.next());
		assertEquals("libelf/c", tw.getPathString());
		assertCorrectId(treeT, tw);

		assertFalse(tw.next());
	}

	public void testTrivialTwoWay_concurrentSubtreeChange() throws Exception {
		final DirCache treeB = db.readDirCache();
		final DirCache treeO = db.readDirCache();
		final DirCache treeT = db.readDirCache();
		{
			final DirCacheBuilder b = treeB.builder();
			final DirCacheBuilder o = treeO.builder();
			final DirCacheBuilder t = treeT.builder();

			b.add(makeEntry("d/o", FileMode.REGULAR_FILE));
			b.add(makeEntry("d/t", FileMode.REGULAR_FILE));

			o.add(makeEntry("d/o", FileMode.REGULAR_FILE, "o !"));
			o.add(makeEntry("d/t", FileMode.REGULAR_FILE));

			t.add(makeEntry("d/o", FileMode.REGULAR_FILE));
			t.add(makeEntry("d/t", FileMode.REGULAR_FILE, "t !"));

			b.finish();
			o.finish();
			t.finish();
		}

		final ObjectInserter ow = db.newObjectInserter();
		final ObjectId b = commit(ow, treeB, new ObjectId[] {});
		final ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		final ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertTrue(merge);

		final TreeWalk tw = new TreeWalk(db);
		tw.setRecursive(true);
		tw.reset(ourMerger.getResultTreeId());

		assertTrue(tw.next());
		assertEquals("d/o", tw.getPathString());
		assertCorrectId(treeO, tw);

		assertTrue(tw.next());
		assertEquals("d/t", tw.getPathString());
		assertCorrectId(treeT, tw);

		assertFalse(tw.next());
	}

	public void testTrivialTwoWay_conflictSubtreeChange() throws Exception {
		final DirCache treeB = db.readDirCache();
		final DirCache treeO = db.readDirCache();
		final DirCache treeT = db.readDirCache();
		{
			final DirCacheBuilder b = treeB.builder();
			final DirCacheBuilder o = treeO.builder();
			final DirCacheBuilder t = treeT.builder();

			b.add(makeEntry("d/o", FileMode.REGULAR_FILE));
			b.add(makeEntry("d/t", FileMode.REGULAR_FILE));

			o.add(makeEntry("d/o", FileMode.REGULAR_FILE));
			o.add(makeEntry("d/t", FileMode.REGULAR_FILE, "o !"));

			t.add(makeEntry("d/o", FileMode.REGULAR_FILE, "t !"));
			t.add(makeEntry("d/t", FileMode.REGULAR_FILE, "t !"));

			b.finish();
			o.finish();
			t.finish();
		}

		final ObjectInserter ow = db.newObjectInserter();
		final ObjectId b = commit(ow, treeB, new ObjectId[] {});
		final ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		final ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	public void testTrivialTwoWay_leftDFconflict1() throws Exception {
		final DirCache treeB = db.readDirCache();
		final DirCache treeO = db.readDirCache();
		final DirCache treeT = db.readDirCache();
		{
			final DirCacheBuilder b = treeB.builder();
			final DirCacheBuilder o = treeO.builder();
			final DirCacheBuilder t = treeT.builder();

			b.add(makeEntry("d/o", FileMode.REGULAR_FILE));
			b.add(makeEntry("d/t", FileMode.REGULAR_FILE));

			o.add(makeEntry("d", FileMode.REGULAR_FILE));

			t.add(makeEntry("d/o", FileMode.REGULAR_FILE));
			t.add(makeEntry("d/t", FileMode.REGULAR_FILE, "t !"));

			b.finish();
			o.finish();
			t.finish();
		}

		final ObjectInserter ow = db.newObjectInserter();
		final ObjectId b = commit(ow, treeB, new ObjectId[] {});
		final ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		final ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	public void testTrivialTwoWay_rightDFconflict1() throws Exception {
		final DirCache treeB = db.readDirCache();
		final DirCache treeO = db.readDirCache();
		final DirCache treeT = db.readDirCache();
		{
			final DirCacheBuilder b = treeB.builder();
			final DirCacheBuilder o = treeO.builder();
			final DirCacheBuilder t = treeT.builder();

			b.add(makeEntry("d/o", FileMode.REGULAR_FILE));
			b.add(makeEntry("d/t", FileMode.REGULAR_FILE));

			o.add(makeEntry("d/o", FileMode.REGULAR_FILE));
			o.add(makeEntry("d/t", FileMode.REGULAR_FILE, "o !"));

			t.add(makeEntry("d", FileMode.REGULAR_FILE));

			b.finish();
			o.finish();
			t.finish();
		}

		final ObjectInserter ow = db.newObjectInserter();
		final ObjectId b = commit(ow, treeB, new ObjectId[] {});
		final ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		final ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	public void testTrivialTwoWay_leftDFconflict2() throws Exception {
		final DirCache treeB = db.readDirCache();
		final DirCache treeO = db.readDirCache();
		final DirCache treeT = db.readDirCache();
		{
			final DirCacheBuilder b = treeB.builder();
			final DirCacheBuilder o = treeO.builder();
			final DirCacheBuilder t = treeT.builder();

			b.add(makeEntry("d", FileMode.REGULAR_FILE));

			o.add(makeEntry("d", FileMode.REGULAR_FILE, "o !"));

			t.add(makeEntry("d/o", FileMode.REGULAR_FILE));

			b.finish();
			o.finish();
			t.finish();
		}

		final ObjectInserter ow = db.newObjectInserter();
		final ObjectId b = commit(ow, treeB, new ObjectId[] {});
		final ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		final ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	public void testTrivialTwoWay_rightDFconflict2() throws Exception {
		final DirCache treeB = db.readDirCache();
		final DirCache treeO = db.readDirCache();
		final DirCache treeT = db.readDirCache();
		{
			final DirCacheBuilder b = treeB.builder();
			final DirCacheBuilder o = treeO.builder();
			final DirCacheBuilder t = treeT.builder();

			b.add(makeEntry("d", FileMode.REGULAR_FILE));

			o.add(makeEntry("d/o", FileMode.REGULAR_FILE));

			t.add(makeEntry("d", FileMode.REGULAR_FILE, "t !"));

			b.finish();
			o.finish();
			t.finish();
		}

		final ObjectInserter ow = db.newObjectInserter();
		final ObjectId b = commit(ow, treeB, new ObjectId[] {});
		final ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		final ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	private void assertCorrectId(final DirCache treeT, final TreeWalk tw) {
		assertEquals(treeT.getEntry(tw.getPathString()).getObjectId(), tw
				.getObjectId(0));
	}

	private ObjectId commit(final ObjectInserter odi, final DirCache treeB,
			final ObjectId[] parentIds) throws Exception {
		final CommitBuilder c = new CommitBuilder();
		c.setTreeId(treeB.writeTree(odi));
		c.setAuthor(new PersonIdent("A U Thor", "a.u.thor", 1L, 0));
		c.setCommitter(c.getAuthor());
		c.setParentIds(parentIds);
		c.setMessage("Tree " + c.getTreeId().name());
		ObjectId id = odi.insert(OBJ_COMMIT, odi.format(c));
		odi.flush();
		return id;
	}

	private DirCacheEntry makeEntry(final String path, final FileMode mode)
			throws Exception {
		return makeEntry(path, mode, path);
	}

	private DirCacheEntry makeEntry(final String path, final FileMode mode,
			final String content) throws Exception {
		final DirCacheEntry ent = new DirCacheEntry(path);
		ent.setFileMode(mode);
		ent.setObjectId(new ObjectInserter.Formatter().idFor(OBJ_BLOB,
				Constants.encode(content)));
		return ent;
	}
}
