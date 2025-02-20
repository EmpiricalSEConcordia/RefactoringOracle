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
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.RebaseResult.Status;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A class used to execute a {@code Rebase} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 * <p>
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-rebase.html"
 *      >Git documentation about Rebase</a>
 */
public class RebaseCommand extends GitCommand<RebaseResult> {
	/**
	 * The name of the "rebase-merge" folder
	 */
	public static final String REBASE_MERGE = "rebase-merge";

	/**
	 * The name of the "stopped-sha" file
	 */
	public static final String STOPPED_SHA = "stopped-sha";

	private static final String AUTHOR_SCRIPT = "author-script";

	private static final String DONE = "done";

	private static final String GIT_AUTHOR_DATE = "GIT_AUTHOR_DATE";

	private static final String GIT_AUTHOR_EMAIL = "GIT_AUTHOR_EMAIL";

	private static final String GIT_AUTHOR_NAME = "GIT_AUTHOR_NAME";

	private static final String GIT_REBASE_TODO = "git-rebase-todo";

	private static final String HEAD_NAME = "head-name";

	private static final String INTERACTIVE = "interactive";

	private static final String MESSAGE = "message";

	private static final String ONTO = "onto";

	private static final String PATCH = "patch";

	private static final String REBASE_HEAD = "head";

	/**
	 * The available operations
	 */
	public enum Operation {
		/**
		 * Initiates rebase
		 */
		BEGIN,
		/**
		 * Continues after a conflict resolution
		 */
		CONTINUE,
		/**
		 * Skips the "current" commit
		 */
		SKIP,
		/**
		 * Aborts and resets the current rebase
		 */
		ABORT;
	}

	private Operation operation = Operation.BEGIN;

	private RevCommit upstreamCommit;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	private final RevWalk walk;

	private final File rebaseDir;

	/**
	 * @param repo
	 */
	protected RebaseCommand(Repository repo) {
		super(repo);
		walk = new RevWalk(repo);
		rebaseDir = new File(repo.getDirectory(), REBASE_MERGE);
	}

	/**
	 * Executes the {@code Rebase} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command. Don't call
	 * this method twice on an instance.
	 *
	 * @return an object describing the result of this command
	 */
	public RebaseResult call() throws NoHeadException, RefNotFoundException,
			JGitInternalException, GitAPIException {
		RevCommit newHead = null;
		boolean lastStepWasForward = false;
		checkCallable();
		checkParameters();
		try {
			switch (operation) {
			case ABORT:
				try {
					return abort(RebaseResult.ABORTED_RESULT);
				} catch (IOException ioe) {
					throw new JGitInternalException(ioe.getMessage(), ioe);
				}
			case SKIP:
				// fall through
			case CONTINUE:
				String upstreamCommitName = readFile(rebaseDir, ONTO);
				this.upstreamCommit = walk.parseCommit(repo
						.resolve(upstreamCommitName));
				break;
			case BEGIN:
				RebaseResult res = initFilesAndRewind();
				if (res != null)
					return res;
			}

			if (monitor.isCancelled())
				return abort(RebaseResult.ABORTED_RESULT);

			if (operation == Operation.CONTINUE) {
				newHead = continueRebase();

				if (newHead == null) {
					// continueRebase() returns null only if no commit was
					// neccessary. This means that no changes where left over
					// after resolving all conflicts. In this case, cgit stops
					// and displays a nice message to the user, telling him to
					// either do changes or skip the commit instead of continue.
					return RebaseResult.NOTHING_TO_COMMIT_RESULT;
				}
			}

			if (operation == Operation.SKIP)
				newHead = checkoutCurrentHead();

			ObjectReader or = repo.newObjectReader();

			List<Step> steps = loadSteps();
			for (Step step : steps) {
				popSteps(1);
				Collection<ObjectId> ids = or.resolve(step.commit);
				if (ids.size() != 1)
					throw new JGitInternalException(
							"Could not resolve uniquely the abbreviated object ID");
				RevCommit commitToPick = walk
						.parseCommit(ids.iterator().next());
				if (monitor.isCancelled())
					return new RebaseResult(commitToPick);
				try {
					monitor.beginTask(MessageFormat.format(
							JGitText.get().applyingCommit,
							commitToPick.getShortMessage()),
							ProgressMonitor.UNKNOWN);
					// if the first parent of commitToPick is the current HEAD,
					// we do a fast-forward instead of cherry-pick to avoid
					// unnecessary object rewriting
					newHead = tryFastForward(commitToPick);
					lastStepWasForward = newHead != null;
					if (!lastStepWasForward) {
						// TODO if the content of this commit is already merged
						// here we should skip this step in order to avoid
						// confusing pseudo-changed
						CherryPickResult cherryPickResult = new Git(repo)
								.cherryPick().include(commitToPick).call();
						switch (cherryPickResult.getStatus()) {
						case FAILED:
							if (operation == Operation.BEGIN)
								return abort(new RebaseResult(
										cherryPickResult.getFailingPaths()));
							else
								return stop(commitToPick);
						case CONFLICTING:
							return stop(commitToPick);
						case OK:
							newHead = cherryPickResult.getNewHead();
						}
					}
				} finally {
					monitor.endTask();
				}
			}
			if (newHead != null) {
				String headName = readFile(rebaseDir, HEAD_NAME);
				updateHead(headName, newHead);
				FileUtils.delete(rebaseDir, FileUtils.RECURSIVE);
				if (lastStepWasForward)
					return RebaseResult.FAST_FORWARD_RESULT;
				return RebaseResult.OK_RESULT;
			}
			return RebaseResult.FAST_FORWARD_RESULT;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	private void updateHead(String headName, RevCommit newHead)
			throws IOException {
		// point the previous head (if any) to the new commit

		if (headName.startsWith(Constants.R_REFS)) {
			RefUpdate rup = repo.updateRef(headName);
			rup.setNewObjectId(newHead);
			Result res = rup.forceUpdate();
			switch (res) {
			case FAST_FORWARD:
			case FORCED:
			case NO_CHANGE:
				break;
			default:
				throw new JGitInternalException("Updating HEAD failed");
			}
			rup = repo.updateRef(Constants.HEAD);
			res = rup.link(headName);
			switch (res) {
			case FAST_FORWARD:
			case FORCED:
			case NO_CHANGE:
				break;
			default:
				throw new JGitInternalException("Updating HEAD failed");
			}
		}
	}

	private RevCommit checkoutCurrentHead() throws IOException,
			NoHeadException, JGitInternalException {
		ObjectId headTree = repo.resolve(Constants.HEAD + "^{tree}");
		if (headTree == null)
			throw new NoHeadException(
					JGitText.get().cannotRebaseWithoutCurrentHead);
		DirCache dc = repo.lockDirCache();
		try {
			DirCacheCheckout dco = new DirCacheCheckout(repo, dc, headTree);
			dco.setFailOnConflict(false);
			boolean needsDeleteFiles = dco.checkout();
			if (needsDeleteFiles) {
				List<String> fileList = dco.getToBeDeleted();
				for (String filePath : fileList) {
					File fileToDelete = new File(repo.getWorkTree(), filePath);
					if (fileToDelete.exists())
						FileUtils.delete(fileToDelete, FileUtils.RECURSIVE
								| FileUtils.RETRY);
				}
			}
		} finally {
			dc.unlock();
		}
		RevWalk rw = new RevWalk(repo);
		RevCommit commit = rw.parseCommit(repo.resolve(Constants.HEAD));
		rw.release();
		return commit;
	}

	/**
	 * @return the commit if we had to do a commit, otherwise null
	 * @throws GitAPIException
	 * @throws IOException
	 */
	private RevCommit continueRebase() throws GitAPIException, IOException {
		// if there are still conflicts, we throw a specific Exception
		DirCache dc = repo.readDirCache();
		boolean hasUnmergedPaths = dc.hasUnmergedPaths();
		if (hasUnmergedPaths)
			throw new UnmergedPathsException();

		// determine whether we need to commit
		TreeWalk treeWalk = new TreeWalk(repo);
		treeWalk.reset();
		treeWalk.setRecursive(true);
		treeWalk.addTree(new DirCacheIterator(dc));
		ObjectId id = repo.resolve(Constants.HEAD + "^{tree}");
		if (id == null)
			throw new NoHeadException(
					JGitText.get().cannotRebaseWithoutCurrentHead);

		treeWalk.addTree(id);

		treeWalk.setFilter(TreeFilter.ANY_DIFF);

		boolean needsCommit = treeWalk.next();
		treeWalk.release();

		if (needsCommit) {
			CommitCommand commit = new Git(repo).commit();
			commit.setMessage(readFile(rebaseDir, MESSAGE));
			commit.setAuthor(parseAuthor());
			return commit.call();
		}
		return null;
	}

	private PersonIdent parseAuthor() throws IOException {
		File authorScriptFile = new File(rebaseDir, AUTHOR_SCRIPT);
		byte[] raw;
		try {
			raw = IO.readFully(authorScriptFile);
		} catch (FileNotFoundException notFound) {
			return null;
		}
		return parseAuthor(raw);
	}

	private RebaseResult stop(RevCommit commitToPick) throws IOException {
		PersonIdent author = commitToPick.getAuthorIdent();
		String authorScript = toAuthorScript(author);
		createFile(rebaseDir, AUTHOR_SCRIPT, authorScript);
		createFile(rebaseDir, MESSAGE, commitToPick.getFullMessage());
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DiffFormatter df = new DiffFormatter(bos);
		df.setRepository(repo);
		df.format(commitToPick.getParent(0), commitToPick);
		createFile(rebaseDir, PATCH, new String(bos.toByteArray(),
				Constants.CHARACTER_ENCODING));
		createFile(rebaseDir, STOPPED_SHA, repo.newObjectReader().abbreviate(
				commitToPick).name());
		// Remove cherry pick state file created by CherryPickCommand, it's not
		// needed for rebase
		repo.writeCherryPickHead(null);
		return new RebaseResult(commitToPick);
	}

	String toAuthorScript(PersonIdent author) {
		StringBuilder sb = new StringBuilder(100);
		sb.append(GIT_AUTHOR_NAME);
		sb.append("='");
		sb.append(author.getName());
		sb.append("'\n");
		sb.append(GIT_AUTHOR_EMAIL);
		sb.append("='");
		sb.append(author.getEmailAddress());
		sb.append("'\n");
		// the command line uses the "external String"
		// representation for date and timezone
		sb.append(GIT_AUTHOR_DATE);
		sb.append("='");
		String externalString = author.toExternalString();
		sb
				.append(externalString.substring(externalString
						.lastIndexOf('>') + 2));
		sb.append("'\n");
		return sb.toString();
	}

	/**
	 * Removes the number of lines given in the parameter from the
	 * <code>git-rebase-todo</code> file but preserves comments and other lines
	 * that can not be parsed as steps
	 *
	 * @param numSteps
	 * @throws IOException
	 */
	private void popSteps(int numSteps) throws IOException {
		if (numSteps == 0)
			return;
		List<String> todoLines = new ArrayList<String>();
		List<String> poppedLines = new ArrayList<String>();
		File todoFile = new File(rebaseDir, GIT_REBASE_TODO);
		File doneFile = new File(rebaseDir, DONE);
		BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(todoFile), Constants.CHARACTER_ENCODING));
		try {
			// check if the line starts with a action tag (pick, skip...)
			while (poppedLines.size() < numSteps) {
				String popCandidate = br.readLine();
				if (popCandidate == null)
					break;
				if (popCandidate.charAt(0) == '#')
					continue;
				int spaceIndex = popCandidate.indexOf(' ');
				boolean pop = false;
				if (spaceIndex >= 0) {
					String actionToken = popCandidate.substring(0, spaceIndex);
					pop = Action.parse(actionToken) != null;
				}
				if (pop)
					poppedLines.add(popCandidate);
				else
					todoLines.add(popCandidate);
			}
			String readLine = br.readLine();
			while (readLine != null) {
				todoLines.add(readLine);
				readLine = br.readLine();
			}
		} finally {
			br.close();
		}

		BufferedWriter todoWriter = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(todoFile), Constants.CHARACTER_ENCODING));
		try {
			for (String writeLine : todoLines) {
				todoWriter.write(writeLine);
				todoWriter.newLine();
			}
		} finally {
			todoWriter.close();
		}

		if (poppedLines.size() > 0) {
			// append here
			BufferedWriter doneWriter = new BufferedWriter(
					new OutputStreamWriter(
							new FileOutputStream(doneFile, true),
							Constants.CHARACTER_ENCODING));
			try {
				for (String writeLine : poppedLines) {
					doneWriter.write(writeLine);
					doneWriter.newLine();
				}
			} finally {
				doneWriter.close();
			}
		}
	}

	private RebaseResult initFilesAndRewind() throws RefNotFoundException,
			IOException, NoHeadException, JGitInternalException {
		// we need to store everything into files so that we can implement
		// --skip, --continue, and --abort

		Ref head = repo.getRef(Constants.HEAD);
		if (head == null || head.getObjectId() == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));

		String headName;
		if (head.isSymbolic())
			headName = head.getTarget().getName();
		else
			headName = "detached HEAD";
		ObjectId headId = head.getObjectId();
		if (headId == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));
		RevCommit headCommit = walk.lookupCommit(headId);
		RevCommit upstream = walk.lookupCommit(upstreamCommit.getId());

		if (walk.isMergedInto(upstream, headCommit))
			return RebaseResult.UP_TO_DATE_RESULT;
		else if (walk.isMergedInto(headCommit, upstream)) {
			// head is already merged into upstream, fast-foward
			monitor.beginTask(MessageFormat.format(
					JGitText.get().resettingHead,
					upstreamCommit.getShortMessage()), ProgressMonitor.UNKNOWN);
			checkoutCommit(upstreamCommit);
			monitor.endTask();

			updateHead(headName, upstreamCommit);
			return RebaseResult.FAST_FORWARD_RESULT;
		}

		monitor.beginTask(JGitText.get().obtainingCommitsForCherryPick,
				ProgressMonitor.UNKNOWN);

		// determine the commits to be applied
		LogCommand cmd = new Git(repo).log().addRange(upstreamCommit,
				headCommit);
		Iterable<RevCommit> commitsToUse = cmd.call();

		List<RevCommit> cherryPickList = new ArrayList<RevCommit>();
		for (RevCommit commit : commitsToUse) {
			if (commit.getParentCount() != 1)
				throw new JGitInternalException(
						MessageFormat.format(
								JGitText.get().canOnlyCherryPickCommitsWithOneParent,
								commit.name(),
								Integer.valueOf(commit.getParentCount())));
			cherryPickList.add(commit);
		}

		Collections.reverse(cherryPickList);
		// create the folder for the meta information
		FileUtils.mkdir(rebaseDir);

		createFile(repo.getDirectory(), Constants.ORIG_HEAD, headId.name());
		createFile(rebaseDir, REBASE_HEAD, headId.name());
		createFile(rebaseDir, HEAD_NAME, headName);
		createFile(rebaseDir, ONTO, upstreamCommit.name());
		createFile(rebaseDir, INTERACTIVE, "");
		BufferedWriter fw = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(new File(rebaseDir, GIT_REBASE_TODO)),
				Constants.CHARACTER_ENCODING));
		fw.write("# Created by EGit: rebasing " + upstreamCommit.name()
				+ " onto " + headId.name());
		fw.newLine();
		try {
			StringBuilder sb = new StringBuilder();
			ObjectReader reader = walk.getObjectReader();
			for (RevCommit commit : cherryPickList) {
				sb.setLength(0);
				sb.append(Action.PICK.toToken());
				sb.append(" ");
				sb.append(reader.abbreviate(commit).name());
				sb.append(" ");
				sb.append(commit.getShortMessage());
				fw.write(sb.toString());
				fw.newLine();
			}
		} finally {
			fw.close();
		}

		monitor.endTask();

		// we rewind to the upstream commit
		monitor.beginTask(MessageFormat.format(JGitText.get().rewinding,
				upstreamCommit.getShortMessage()), ProgressMonitor.UNKNOWN);
		boolean checkoutOk = false;
		try {
			checkoutOk = checkoutCommit(upstreamCommit);
		} finally {
			if (!checkoutOk)
				FileUtils.delete(rebaseDir, FileUtils.RECURSIVE);
		}
		monitor.endTask();

		return null;
	}

	/**
	 * checks if we can fast-forward and returns the new head if it is possible
	 *
	 * @param newCommit
	 * @return the new head, or null
	 * @throws RefNotFoundException
	 * @throws IOException
	 */
	public RevCommit tryFastForward(RevCommit newCommit)
			throws RefNotFoundException, IOException {
		Ref head = repo.getRef(Constants.HEAD);
		if (head == null || head.getObjectId() == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));

		ObjectId headId = head.getObjectId();
		if (headId == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved, Constants.HEAD));
		RevCommit headCommit = walk.lookupCommit(headId);
		if (walk.isMergedInto(newCommit, headCommit))
			return newCommit;

		String headName;
		if (head.isSymbolic())
			headName = head.getTarget().getName();
		else
			headName = "detached HEAD";
		return tryFastForward(headName, headCommit, newCommit);
	}

	private RevCommit tryFastForward(String headName, RevCommit oldCommit,
			RevCommit newCommit) throws IOException, JGitInternalException {
		boolean tryRebase = false;
		for (RevCommit parentCommit : newCommit.getParents())
			if (parentCommit.equals(oldCommit))
				tryRebase = true;
		if (!tryRebase)
			return null;

		CheckoutCommand co = new CheckoutCommand(repo);
		try {
			co.setName(newCommit.name()).call();
			if (headName.startsWith(Constants.R_HEADS)) {
				RefUpdate rup = repo.updateRef(headName);
				rup.setExpectedOldObjectId(oldCommit);
				rup.setNewObjectId(newCommit);
				rup.setRefLogMessage("Fast-foward from " + oldCommit.name()
						+ " to " + newCommit.name(), false);
				Result res = rup.update(walk);
				switch (res) {
				case FAST_FORWARD:
				case NO_CHANGE:
				case FORCED:
					break;
				default:
					throw new IOException("Could not fast-forward");
				}
			}
			return newCommit;
		} catch (RefAlreadyExistsException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (RefNotFoundException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (InvalidRefNameException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (CheckoutConflictException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	private void checkParameters() throws WrongRepositoryStateException {
		if (this.operation != Operation.BEGIN) {
			// these operations are only possible while in a rebasing state
			switch (repo.getRepositoryState()) {
			case REBASING_INTERACTIVE:
				break;
			default:
				throw new WrongRepositoryStateException(MessageFormat.format(
						JGitText.get().wrongRepositoryState, repo
								.getRepositoryState().name()));
			}
		} else
			switch (repo.getRepositoryState()) {
			case SAFE:
				if (this.upstreamCommit == null)
					throw new JGitInternalException(MessageFormat
							.format(JGitText.get().missingRequiredParameter,
									"upstream"));
				return;
			default:
				throw new WrongRepositoryStateException(MessageFormat.format(
						JGitText.get().wrongRepositoryState, repo
								.getRepositoryState().name()));

			}
	}

	private void createFile(File parentDir, String name, String content)
			throws IOException {
		File file = new File(parentDir, name);
		FileOutputStream fos = new FileOutputStream(file);
		try {
			fos.write(content.getBytes(Constants.CHARACTER_ENCODING));
			fos.write('\n');
		} finally {
			fos.close();
		}
	}

	private RebaseResult abort(RebaseResult result) throws IOException {
		try {
			String commitId = readFile(repo.getDirectory(), Constants.ORIG_HEAD);
			monitor.beginTask(MessageFormat.format(
					JGitText.get().abortingRebase, commitId),
					ProgressMonitor.UNKNOWN);

			DirCacheCheckout dco;
			RevCommit commit = walk.parseCommit(repo.resolve(commitId));
			if (result.getStatus().equals(Status.FAILED)) {
				RevCommit head = walk.parseCommit(repo.resolve(Constants.HEAD));
				dco = new DirCacheCheckout(repo, head.getTree(),
						repo.lockDirCache(), commit.getTree());
			} else {
				dco = new DirCacheCheckout(repo, repo.lockDirCache(),
						commit.getTree());
			}
			dco.setFailOnConflict(false);
			dco.checkout();
			walk.release();
		} finally {
			monitor.endTask();
		}
		try {
			String headName = readFile(rebaseDir, HEAD_NAME);
			if (headName.startsWith(Constants.R_REFS)) {
				monitor.beginTask(MessageFormat.format(
						JGitText.get().resettingHead, headName),
						ProgressMonitor.UNKNOWN);

				// update the HEAD
				RefUpdate refUpdate = repo.updateRef(Constants.HEAD, false);
				Result res = refUpdate.link(headName);
				switch (res) {
				case FAST_FORWARD:
				case FORCED:
				case NO_CHANGE:
					break;
				default:
					throw new JGitInternalException(
							JGitText.get().abortingRebaseFailed);
				}
			}
			// cleanup the files
			FileUtils.delete(rebaseDir, FileUtils.RECURSIVE);
			repo.writeCherryPickHead(null);
			return result;

		} finally {
			monitor.endTask();
		}
	}

	private String readFile(File directory, String fileName) throws IOException {
		byte[] content = IO.readFully(new File(directory, fileName));
		// strip off the last LF
		int end = content.length;
		while (0 < end && content[end - 1] == '\n')
			end--;
		return RawParseUtils.decode(content, 0, end);
	}

	private boolean checkoutCommit(RevCommit commit) throws IOException {
		try {
			RevCommit head = walk.parseCommit(repo.resolve(Constants.HEAD));
			DirCacheCheckout dco = new DirCacheCheckout(repo, head.getTree(),
					repo.lockDirCache(), commit.getTree());
			dco.setFailOnConflict(true);
			dco.checkout();
			// update the HEAD
			RefUpdate refUpdate = repo.updateRef(Constants.HEAD, true);
			refUpdate.setExpectedOldObjectId(head);
			refUpdate.setNewObjectId(commit);
			Result res = refUpdate.forceUpdate();
			switch (res) {
			case FAST_FORWARD:
			case NO_CHANGE:
			case FORCED:
				break;
			default:
				throw new IOException("Could not rewind to upstream commit");
			}
		} finally {
			walk.release();
			monitor.endTask();
		}
		return true;
	}

	private List<Step> loadSteps() throws IOException {
		byte[] buf = IO.readFully(new File(rebaseDir, GIT_REBASE_TODO));
		int ptr = 0;
		int tokenBegin = 0;
		ArrayList<Step> r = new ArrayList<Step>();
		while (ptr < buf.length) {
			tokenBegin = ptr;
			ptr = RawParseUtils.nextLF(buf, ptr);
			int nextSpace = 0;
			int tokenCount = 0;
			Step current = null;
			while (tokenCount < 3 && nextSpace < ptr) {
				switch (tokenCount) {
				case 0:
					nextSpace = RawParseUtils.next(buf, tokenBegin, ' ');
					String actionToken = new String(buf, tokenBegin, nextSpace
							- tokenBegin - 1);
					tokenBegin = nextSpace;
					if (actionToken.charAt(0) == '#') {
						tokenCount = 3;
						break;
					}
					Action action = Action.parse(actionToken);
					if (action != null)
						current = new Step(Action.parse(actionToken));
					break;
				case 1:
					if (current == null)
						break;
					nextSpace = RawParseUtils.next(buf, tokenBegin, ' ');
					String commitToken = new String(buf, tokenBegin, nextSpace
							- tokenBegin - 1);
					tokenBegin = nextSpace;
					current.commit = AbbreviatedObjectId
							.fromString(commitToken);
					break;
				case 2:
					if (current == null)
						break;
					nextSpace = ptr;
					int length = ptr - tokenBegin;
					current.shortMessage = new byte[length];
					System.arraycopy(buf, tokenBegin, current.shortMessage, 0,
							length);
					r.add(current);
					break;
				}
				tokenCount++;
			}
		}
		return r;
	}

	/**
	 * @param upstream
	 *            the upstream commit
	 * @return {@code this}
	 */
	public RebaseCommand setUpstream(RevCommit upstream) {
		this.upstreamCommit = upstream;
		return this;
	}

	/**
	 * @param upstream
	 *            id of the upstream commit
	 * @return {@code this}
	 */
	public RebaseCommand setUpstream(AnyObjectId upstream) {
		try {
			this.upstreamCommit = walk.parseCommit(upstream);
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().couldNotReadObjectWhileParsingCommit,
					upstream.name()), e);
		}
		return this;
	}

	/**
	 * @param upstream
	 *            the upstream branch
	 * @return {@code this}
	 * @throws RefNotFoundException
	 */
	public RebaseCommand setUpstream(String upstream)
			throws RefNotFoundException {
		try {
			ObjectId upstreamId = repo.resolve(upstream);
			if (upstreamId == null)
				throw new RefNotFoundException(MessageFormat.format(JGitText
						.get().refNotResolved, upstream));
			upstreamCommit = walk.parseCommit(repo.resolve(upstream));
			return this;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	/**
	 * @param operation
	 *            the operation to perform
	 * @return {@code this}
	 */
	public RebaseCommand setOperation(Operation operation) {
		this.operation = operation;
		return this;
	}

	/**
	 * @param monitor
	 *            a progress monitor
	 * @return this instance
	 */
	public RebaseCommand setProgressMonitor(ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	static enum Action {
		PICK("pick"); // later add SQUASH, EDIT, etc.

		private final String token;

		private Action(String token) {
			this.token = token;
		}

		public String toToken() {
			return this.token;
		}

		static Action parse(String token) {
			if (token.equals("pick") || token.equals("p"))
				return PICK;
			throw new JGitInternalException(
					MessageFormat
							.format(
									"Unknown or unsupported command \"{0}\", only  \"pick\" is allowed",
									token));
		}
	}

	static class Step {
		Action action;

		AbbreviatedObjectId commit;

		byte[] shortMessage;

		Step(Action action) {
			this.action = action;
		}
	}

	PersonIdent parseAuthor(byte[] raw) {
		if (raw.length == 0)
			return null;

		Map<String, String> keyValueMap = new HashMap<String, String>();
		for (int p = 0; p < raw.length;) {
			int end = RawParseUtils.nextLF(raw, p);
			if (end == p)
				break;
			int equalsIndex = RawParseUtils.next(raw, p, '=');
			if (equalsIndex == end)
				break;
			String key = RawParseUtils.decode(raw, p, equalsIndex - 1);
			String value = RawParseUtils.decode(raw, equalsIndex + 1, end - 2);
			p = end;
			keyValueMap.put(key, value);
		}

		String name = keyValueMap.get(GIT_AUTHOR_NAME);
		String email = keyValueMap.get(GIT_AUTHOR_EMAIL);
		String time = keyValueMap.get(GIT_AUTHOR_DATE);

		// the time is saved as <seconds since 1970> <timezone offset>
		long when = Long.parseLong(time.substring(0, time.indexOf(' '))) * 1000;
		String tzOffsetString = time.substring(time.indexOf(' ') + 1);
		int multiplier = -1;
		if (tzOffsetString.charAt(0) == '+')
			multiplier = 1;
		int hours = Integer.parseInt(tzOffsetString.substring(1, 3));
		int minutes = Integer.parseInt(tzOffsetString.substring(3, 5));
		// this is in format (+/-)HHMM (hours and minutes)
		// we need to convert into minutes
		int tz = (hours * 60 + minutes) * multiplier;
		if (name != null && email != null)
			return new PersonIdent(name, email, when, tz);
		return null;
	}
}
