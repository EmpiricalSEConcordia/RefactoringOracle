/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
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
package org.eclipse.jgit.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.RebaseResult.Status;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class RebaseCommandTest extends RepositoryTestCase {
	private void createBranch(ObjectId objectId, String branchName)
			throws IOException {
		RefUpdate updateRef = db.updateRef(branchName);
		updateRef.setNewObjectId(objectId);
		updateRef.update();
	}

	private void checkoutBranch(String branchName)
			throws IllegalStateException, IOException {
		RevWalk walk = new RevWalk(db);
		RevCommit head = walk.parseCommit(db.resolve(Constants.HEAD));
		RevCommit branch = walk.parseCommit(db.resolve(branchName));
		DirCacheCheckout dco = new DirCacheCheckout(db, head.getTree().getId(),
				db.lockDirCache(), branch.getTree().getId());
		dco.setFailOnConflict(true);
		dco.checkout();
		walk.release();
		// update the HEAD
		RefUpdate refUpdate = db.updateRef(Constants.HEAD);
		refUpdate.link(branchName);
	}

	private void checkoutCommit(RevCommit commit) throws IllegalStateException,
			IOException {
		RevWalk walk = new RevWalk(db);
		RevCommit head = walk.parseCommit(db.resolve(Constants.HEAD));
		DirCacheCheckout dco = new DirCacheCheckout(db, head.getTree(),
				db.lockDirCache(), commit.getTree());
		dco.setFailOnConflict(true);
		dco.checkout();
		walk.release();
		// update the HEAD
		RefUpdate refUpdate = db.updateRef(Constants.HEAD, true);
		refUpdate.setNewObjectId(commit);
		refUpdate.forceUpdate();
	}

	public void testFastForwardWithNewFile() throws Exception {
		Git git = new Git(db);

		// create file1 on master
		writeTrashFile("file1", "file1");
		git.add().addFilepattern("file1").call();
		RevCommit first = git.commit().setMessage("Add file1").call();

		assertTrue(new File(db.getWorkTree(), "file1").exists());
		// create a topic branch
		createBranch(first, "refs/heads/topic");
		// create file2 on master
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		checkoutBranch("refs/heads/topic");
		assertFalse(new File(db.getWorkTree(), "file2").exists());

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.UP_TO_DATE, res.getStatus());
	}

	public void testUpToDate() throws Exception {
		Git git = new Git(db);

		// create file1 on master
		writeTrashFile("file1", "file1");
		git.add().addFilepattern("file1").call();
		RevCommit first = git.commit().setMessage("Add file1").call();

		assertTrue(new File(db.getWorkTree(), "file1").exists());

		RebaseResult res = git.rebase().setUpstream(first).call();
		assertEquals(Status.UP_TO_DATE, res.getStatus());
	}

	public void testUnknownUpstream() throws Exception {
		Git git = new Git(db);

		// create file1 on master
		writeTrashFile("file1", "file1");
		git.add().addFilepattern("file1").call();
		git.commit().setMessage("Add file1").call();

		assertTrue(new File(db.getWorkTree(), "file1").exists());

		try {
			git.rebase().setUpstream("refs/heads/xyz")
					.call();
			fail("expected exception was not thrown");
		} catch (RefNotFoundException e) {
			// expected exception
		}
	}

	public void testConflictFreeWithSingleFile() throws Exception {
		Git git = new Git(db);

		// create file1 on master
		File theFile = writeTrashFile("file1", "1\n2\n3\n");
		git.add().addFilepattern("file1").call();
		RevCommit second = git.commit().setMessage("Add file1").call();
		assertTrue(new File(db.getWorkTree(), "file1").exists());
		// change first line in master and commit
		writeTrashFile("file1", "1master\n2\n3\n");
		checkFile(theFile, "1master\n2\n3\n");
		git.add().addFilepattern("file1").call();
		RevCommit lastMasterChange = git.commit().setMessage(
				"change file1 in master").call();

		// create a topic branch based on second commit
		createBranch(second, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(theFile, "1\n2\n3\n");

		assertTrue(new File(db.getWorkTree(), "file1").exists());
		// change third line in topic branch
		writeTrashFile("file1", "1\n2\n3\ntopic\n");
		git.add().addFilepattern("file1").call();
		git.commit().setMessage("change file1 in topic").call();

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.OK, res.getStatus());
		checkFile(theFile, "1master\n2\n3\ntopic\n");
		// our old branch should be checked out again
		assertEquals("refs/heads/topic", db.getFullBranch());
		assertEquals(lastMasterChange, new RevWalk(db).parseCommit(
				db.resolve(Constants.HEAD)).getParent(0));
	}

	public void testDetachedHead() throws Exception {
		Git git = new Git(db);

		// create file1 on master
		File theFile = writeTrashFile("file1", "1\n2\n3\n");
		git.add().addFilepattern("file1").call();
		RevCommit second = git.commit().setMessage("Add file1").call();
		assertTrue(new File(db.getWorkTree(), "file1").exists());
		// change first line in master and commit
		writeTrashFile("file1", "1master\n2\n3\n");
		checkFile(theFile, "1master\n2\n3\n");
		git.add().addFilepattern("file1").call();
		RevCommit lastMasterChange = git.commit()
				.setMessage("change file1 in master").call();

		// create a topic branch based on second commit
		createBranch(second, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(theFile, "1\n2\n3\n");

		assertTrue(new File(db.getWorkTree(), "file1").exists());
		// change third line in topic branch
		writeTrashFile("file1", "1\n2\n3\ntopic\n");
		git.add().addFilepattern("file1").call();
		RevCommit topicCommit = git.commit()
				.setMessage("change file1 in topic").call();
		checkoutBranch("refs/heads/master");
		checkoutCommit(topicCommit);
		assertEquals(topicCommit.getId().getName(), db.getFullBranch());

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.OK, res.getStatus());
		checkFile(theFile, "1master\n2\n3\ntopic\n");
		assertEquals(lastMasterChange,
				new RevWalk(db).parseCommit(db.resolve(Constants.HEAD))
						.getParent(0));

	}

	public void testFilesAddedFromTwoBranches() throws Exception {
		Git git = new Git(db);

		// create file1 on master
		writeTrashFile("file1", "file1");
		git.add().addFilepattern("file1").call();
		RevCommit masterCommit = git.commit().setMessage("Add file1 to master")
				.call();

		// create a branch named file2 and add file2
		createBranch(masterCommit, "refs/heads/file2");
		checkoutBranch("refs/heads/file2");
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		RevCommit addFile2 = git.commit().setMessage(
				"Add file2 to branch file2").call();

		// create a branch named file3 and add file3
		createBranch(masterCommit, "refs/heads/file3");
		checkoutBranch("refs/heads/file3");
		writeTrashFile("file3", "file3");
		git.add().addFilepattern("file3").call();
		git.commit().setMessage("Add file3 to branch file3").call();

		assertTrue(new File(db.getWorkTree(), "file1").exists());
		assertFalse(new File(db.getWorkTree(), "file2").exists());
		assertTrue(new File(db.getWorkTree(), "file3").exists());

		RebaseResult res = git.rebase().setUpstream("refs/heads/file2").call();
		assertEquals(Status.OK, res.getStatus());

		assertTrue(new File(db.getWorkTree(), "file1").exists());
		assertTrue(new File(db.getWorkTree(), "file2").exists());
		assertTrue(new File(db.getWorkTree(), "file3").exists());

		// our old branch should be checked out again
		assertEquals("refs/heads/file3", db.getFullBranch());
		assertEquals(addFile2, new RevWalk(db).parseCommit(
				db.resolve(Constants.HEAD)).getParent(0));

		checkoutBranch("refs/heads/file2");
		assertTrue(new File(db.getWorkTree(), "file1").exists());
		assertTrue(new File(db.getWorkTree(), "file2").exists());
		assertFalse(new File(db.getWorkTree(), "file3").exists());
	}

	public void testAbortOnConflict() throws Exception {
		Git git = new Git(db);

		// create file1 on master
		File theFile = writeTrashFile("file1", "1\n2\n3\n");
		git.add().addFilepattern("file1").call();
		RevCommit firstInMaster = git.commit().setMessage("Add file1").call();
		assertTrue(new File(db.getWorkTree(), "file1").exists());
		// change first line in master and commit
		writeTrashFile("file1", "1master\n2\n3\n");
		checkFile(theFile, "1master\n2\n3\n");
		git.add().addFilepattern("file1").call();
		git.commit().setMessage("change file1 in master").call();

		// create a topic branch based on second commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(theFile, "1\n2\n3\n");

		assertTrue(new File(db.getWorkTree(), "file1").exists());
		// add a line (non-conflicting)
		writeTrashFile("file1", "1\n2\n3\ntopic4\n");
		git.add().addFilepattern("file1").call();
		git.commit().setMessage("add a line to file1 in topic").call();

		// change first line (conflicting)
		writeTrashFile("file1", "1topic\n2\n3\ntopic4\n");
		git.add().addFilepattern("file1").call();
		RevCommit conflicting = git.commit()
				.setMessage("change file1 in topic").call();

		// change second line (not conflicting)
		writeTrashFile("file1", "1topic\n2topic\n3\ntopic4\n");
		git.add().addFilepattern("file1").call();
		RevCommit lastTopicCommit = git.commit().setMessage(
				"change file1 in topic again").call();

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());
		assertEquals(conflicting, res.getCurrentCommit());
		checkFile(theFile,
				"<<<<<<< OURS\n1master\n=======\n1topic\n>>>>>>> THEIRS\n2\n3\ntopic4\n");

		assertEquals(RepositoryState.REBASING_MERGE, db.getRepositoryState());
		assertTrue(new File(db.getDirectory(), "rebase-merge").exists());
		// the first one should be included, so we should have left two picks in
		// the file
		assertEquals(2, countPicks());

		// rebase should not succeed in this state
		try {
			git.rebase().setUpstream("refs/heads/master").call();
			fail("Expected exception was not thrown");
		} catch (WrongRepositoryStateException e) {
			// expected
		}

		// abort should reset to topic branch
		res = git.rebase().setOperation(Operation.ABORT).call();
		assertEquals(res.getStatus(), Status.ABORTED);
		assertEquals("refs/heads/topic", db.getFullBranch());
		checkFile(theFile, "1topic\n2topic\n3\ntopic4\n");
		RevWalk rw = new RevWalk(db);
		assertEquals(lastTopicCommit, rw
				.parseCommit(db.resolve(Constants.HEAD)));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());

		// rebase- dir in .git must be deleted
		assertFalse(new File(db.getDirectory(), "rebase-merge").exists());
	}

	public void testAbortOnConflictFileCreationAndDeletion() throws Exception {
		Git git = new Git(db);

		// create file1 on master
		writeTrashFile("file1", "Hello World");
		git.add().addFilepattern("file1").call();
		// create file2 on master
		File file2 = writeTrashFile("file2", "Hello World 2");
		git.add().addFilepattern("file2").call();
		// create file3 on master
		File file3 = writeTrashFile("file3", "Hello World 3");
		git.add().addFilepattern("file3").call();

		RevCommit firstInMaster = git.commit()
				.setMessage("Add file 1, 2 and 3").call();

		// create file4 on master
		File file4 = writeTrashFile("file4", "Hello World 4");
		git.add().addFilepattern("file4").call();

		deleteTrashFile("file2");
		git.add().setUpdate(true).addFilepattern("file2").call();
		// create folder folder6 on topic (conflicts with file folder6 on topic
		// later on)
		writeTrashFile("folder6/file1", "Hello World folder6");
		git.add().addFilepattern("folder6/file1").call();

		git.commit()
				.setMessage(
						"Add file 4 and folder folder6, delete file2 on master")
				.call();

		// create a topic branch based on second commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");

		deleteTrashFile("file3");
		git.add().setUpdate(true).addFilepattern("file3").call();
		// create file5 on topic
		File file5 = writeTrashFile("file5", "Hello World 5");
		git.add().addFilepattern("file5").call();
		git.commit().setMessage("Delete file3 and add file5 in topic").call();

		// create file folder6 on topic (conflicts with folder6 on master)
		writeTrashFile("folder6", "Hello World 6");
		git.add().addFilepattern("folder6").call();
		// create file7 on topic
		File file7 = writeTrashFile("file7", "Hello World 7");
		git.add().addFilepattern("file7").call();

		deleteTrashFile("file5");
		git.add().setUpdate(true).addFilepattern("file5").call();
		RevCommit conflicting = git.commit()
				.setMessage("Delete file5, add file folder6 and file7 in topic")
				.call();

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());
		assertEquals(conflicting, res.getCurrentCommit());

		assertEquals(RepositoryState.REBASING_MERGE, db.getRepositoryState());
		assertTrue(new File(db.getDirectory(), "rebase-merge").exists());
		// the first one should be included, so we should have left two picks in
		// the file
		assertEquals(1, countPicks());

		assertFalse(file2.exists());
		assertFalse(file3.exists());
		assertTrue(file4.exists());
		assertFalse(file5.exists());
		assertTrue(file7.exists());

		// abort should reset to topic branch
		res = git.rebase().setOperation(Operation.ABORT).call();
		assertEquals(res.getStatus(), Status.ABORTED);
		assertEquals("refs/heads/topic", db.getFullBranch());
		RevWalk rw = new RevWalk(db);
		assertEquals(conflicting,
				rw.parseCommit(db.resolve(Constants.HEAD)));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());

		// rebase- dir in .git must be deleted
		assertFalse(new File(db.getDirectory(), "rebase-merge").exists());

		assertTrue(file2.exists());
		assertFalse(file3.exists());
		assertFalse(file4.exists());
		assertFalse(file5.exists());
		assertTrue(file7.exists());

	}

	private int countPicks() throws IOException {
		int count = 0;
		File todoFile = new File(db.getDirectory(),
				"rebase-merge/git-rebase-todo");
		BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(todoFile), "UTF-8"));
		try {
			String line = br.readLine();
			while (line != null) {
				if (line.startsWith("pick "))
					count++;
				line = br.readLine();
			}
			return count;
		} finally {
			br.close();
		}
	}
}
