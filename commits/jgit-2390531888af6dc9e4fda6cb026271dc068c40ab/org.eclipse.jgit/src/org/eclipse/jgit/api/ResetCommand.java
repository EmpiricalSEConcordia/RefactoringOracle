/*
 * Copyright (C) 2011-2013, Chris Aniszczyk <caniszczyk@gmail.com>
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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/**
 * A class used to execute a {@code Reset} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-reset.html"
 *      >Git documentation about Reset</a>
 */
public class ResetCommand extends GitCommand<Ref> {

	/**
	 * Kind of reset
	 */
	public enum ResetType {
		/**
		 * Just change the ref, the index and workdir are not changed.
		 */
		SOFT,

		/**
		 * Change the ref and the index, the workdir is not changed.
		 */
		MIXED,

		/**
		 * Change the ref, the index and the workdir
		 */
		HARD,

		/**
		 * Resets the index and updates the files in the working tree that are
		 * different between respective commit and HEAD, but keeps those which
		 * are different between the index and working tree
		 */
		MERGE, // TODO not implemented yet

		/**
		 * Change the ref, the index and the workdir that are different between
		 * respective commit and HEAD
		 */
		KEEP // TODO not implemented yet
	}

	// We need to be able to distinguish whether the caller set the ref
	// explicitly or not, so we apply the default (HEAD) only later.
	private String ref = null;

	private ResetType mode;

	private Collection<String> filepaths = new LinkedList<String>();

	/**
	 *
	 * @param repo
	 */
	public ResetCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Executes the {@code Reset} command. Each instance of this class should
	 * only be used for one invocation of the command. Don't call this method
	 * twice on an instance.
	 *
	 * @return the Ref after reset
	 * @throws GitAPIException
	 */
	public Ref call() throws GitAPIException, CheckoutConflictException {
		checkCallable();

		try {
			RepositoryState state = repo.getRepositoryState();
			final boolean merging = state.equals(RepositoryState.MERGING)
					|| state.equals(RepositoryState.MERGING_RESOLVED);
			final boolean cherryPicking = state
					.equals(RepositoryState.CHERRY_PICKING)
					|| state.equals(RepositoryState.CHERRY_PICKING_RESOLVED);
			final boolean reverting = state.equals(RepositoryState.REVERTING)
					|| state.equals(RepositoryState.REVERTING_RESOLVED);

			final ObjectId commitId = resolveRefToCommitId();
			// When ref is explicitly specified, it has to resolve
			if (ref != null && commitId == null) {
				// @TODO throw an InvalidRefNameException. We can't do that
				// now because this would break the API
				throw new JGitInternalException(MessageFormat
						.format(JGitText.get().invalidRefName, ref));
			}

			final ObjectId commitTree;
			if (commitId != null)
				commitTree = parseCommit(commitId).getTree();
			else
				commitTree = null;

			if (!filepaths.isEmpty()) {
				// reset [commit] -- paths
				resetIndexForPaths(commitTree);
				setCallable(false);
				return repo.getRef(Constants.HEAD);
			}

			final Ref result;
			if (commitId != null) {
				// write the ref
				final RefUpdate ru = repo.updateRef(Constants.HEAD);
				ru.setNewObjectId(commitId);

				String refName = Repository.shortenRefName(getRefOrHEAD());
				String message = refName + ": updating " + Constants.HEAD; //$NON-NLS-1$
				ru.setRefLogMessage(message, false);
				if (ru.forceUpdate() == RefUpdate.Result.LOCK_FAILURE)
					throw new JGitInternalException(MessageFormat.format(
							JGitText.get().cannotLock, ru.getName()));

				ObjectId origHead = ru.getOldObjectId();
				if (origHead != null)
					repo.writeOrigHead(origHead);
				result = ru.getRef();
			} else {
				result = repo.getRef(Constants.HEAD);
			}

			if (mode == null)
				mode = ResetType.MIXED;

			switch (mode) {
				case HARD:
					checkoutIndex(commitTree);
					break;
				case MIXED:
					resetIndex(commitTree);
					break;
				case SOFT: // do nothing, only the ref was changed
					break;
				case KEEP: // TODO
				case MERGE: // TODO
					throw new UnsupportedOperationException();

			}

			if (mode != ResetType.SOFT) {
				if (merging)
					resetMerge();
				else if (cherryPicking)
					resetCherryPick();
				else if (reverting)
					resetRevert();
				else if (repo.readSquashCommitMsg() != null)
					repo.writeSquashCommitMsg(null /* delete */);
			}

			setCallable(false);
			return result;
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().exceptionCaughtDuringExecutionOfResetCommand,
					e.getMessage()), e);
		}
	}

	private RevCommit parseCommit(final ObjectId commitId) {
		try (RevWalk rw = new RevWalk(repo)) {
			return rw.parseCommit(commitId);
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().cannotReadCommit, commitId.toString()), e);
		}
	}

	private ObjectId resolveRefToCommitId() {
		try {
			return repo.resolve(getRefOrHEAD() + "^{commit}"); //$NON-NLS-1$
		} catch (IOException e) {
			throw new JGitInternalException(
					MessageFormat.format(JGitText.get().cannotRead, getRefOrHEAD()),
					e);
		}
	}

	/**
	 * @param ref
	 *            the ref to reset to, defaults to HEAD if not specified
	 * @return this instance
	 */
	public ResetCommand setRef(String ref) {
		this.ref = ref;
		return this;
	}

	/**
	 * @param mode
	 *            the mode of the reset command
	 * @return this instance
	 */
	public ResetCommand setMode(ResetType mode) {
		if (!filepaths.isEmpty())
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().illegalCombinationOfArguments,
					"[--mixed | --soft | --hard]", "<paths>...")); //$NON-NLS-1$
		this.mode = mode;
		return this;
	}

	/**
	 * @param path
	 *            repository-relative path of file/directory to reset (with
	 *            <code>/</code> as separator)
	 * @return this instance
	 */
	public ResetCommand addPath(String path) {
		if (mode != null)
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().illegalCombinationOfArguments, "<paths>...",
					"[--mixed | --soft | --hard]")); //$NON-NLS-1$
		filepaths.add(path);
		return this;
	}

	private String getRefOrHEAD() {
		if (ref != null)
			return ref;
		else
			return Constants.HEAD;
	}

	private void resetIndexForPaths(ObjectId commitTree) {
		DirCache dc = null;
		try (final TreeWalk tw = new TreeWalk(repo)) {
			dc = repo.lockDirCache();
			DirCacheBuilder builder = dc.builder();

			tw.addTree(new DirCacheBuildIterator(builder));
			if (commitTree != null)
				tw.addTree(commitTree);
			else
				tw.addTree(new EmptyTreeIterator());
			tw.setFilter(PathFilterGroup.createFromStrings(filepaths));
			tw.setRecursive(true);

			while (tw.next()) {
				final CanonicalTreeParser tree = tw.getTree(1,
						CanonicalTreeParser.class);
				// only keep file in index if it's in the commit
				if (tree != null) {
				    // revert index to commit
					DirCacheEntry entry = new DirCacheEntry(tw.getRawPath());
					entry.setFileMode(tree.getEntryFileMode());
					entry.setObjectId(tree.getEntryObjectId());
					builder.add(entry);
				}
			}

			builder.commit();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (dc != null)
				dc.unlock();
		}
	}

	private void resetIndex(ObjectId commitTree) throws IOException {
		DirCache dc = repo.lockDirCache();
		try (TreeWalk walk = new TreeWalk(repo)) {
			DirCacheBuilder builder = dc.builder();

			if (commitTree != null)
				walk.addTree(commitTree);
			else
				walk.addTree(new EmptyTreeIterator());
			walk.addTree(new DirCacheIterator(dc));
			walk.setRecursive(true);

			while (walk.next()) {
				AbstractTreeIterator cIter = walk.getTree(0,
						AbstractTreeIterator.class);
				if (cIter == null) {
					// Not in commit, don't add to new index
					continue;
				}

				final DirCacheEntry entry = new DirCacheEntry(walk.getRawPath());
				entry.setFileMode(cIter.getEntryFileMode());
				entry.setObjectIdFromRaw(cIter.idBuffer(), cIter.idOffset());

				DirCacheIterator dcIter = walk.getTree(1,
						DirCacheIterator.class);
				if (dcIter != null && dcIter.idEqual(cIter)) {
					DirCacheEntry indexEntry = dcIter.getDirCacheEntry();
					entry.setLastModified(indexEntry.getLastModified());
					entry.setLength(indexEntry.getLength());
				}

				builder.add(entry);
			}

			builder.commit();
		} finally {
			dc.unlock();
		}
	}

	private void checkoutIndex(ObjectId commitTree) throws IOException,
			GitAPIException {
		DirCache dc = repo.lockDirCache();
		try {
			DirCacheCheckout checkout = new DirCacheCheckout(repo, dc,
					commitTree);
			checkout.setFailOnConflict(false);
			try {
				checkout.checkout();
			} catch (org.eclipse.jgit.errors.CheckoutConflictException cce) {
				throw new CheckoutConflictException(checkout.getConflicts(),
						cce);
			}
		} finally {
			dc.unlock();
		}
	}

	private void resetMerge() throws IOException {
		repo.writeMergeHeads(null);
		repo.writeMergeCommitMsg(null);
	}

	private void resetCherryPick() throws IOException {
		repo.writeCherryPickHead(null);
		repo.writeMergeCommitMsg(null);
	}

	private void resetRevert() throws IOException {
		repo.writeRevertHead(null);
		repo.writeMergeCommitMsg(null);
	}

}
