/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and
 * other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.pgm;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.JGitInternalException;
import org.eclipse.jgit.api.NoHeadException;
import org.eclipse.jgit.api.NoMessageException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.RawParseUtils;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_recordChangesToRepository")
class Commit extends TextBuiltin {
	// I don't support setting the committer, because also the native git
	// command doesn't allow this.

	@Option(name = "--author", metaVar="metaVar_author", usage = "usage_CommitAuthor")
	private String author;

	@Option(name = "--message", aliases = { "-m" }, metaVar="metaVar_message", usage="usage_CommitMessage", required=true)
	private String message;

	@Override
	protected void run() throws NoHeadException, NoMessageException,
			ConcurrentRefUpdateException, JGitInternalException, Exception {
		CommitCommand commitCmd = new Git(db).commit();
		if (author != null)
			commitCmd.setAuthor(RawParseUtils.parsePersonIdent(author));
		if (message != null)
			commitCmd.setMessage(message);
		Ref head = db.getRef(Constants.HEAD);
		RevCommit commit = commitCmd.call();

		String branchName;
		if (!head.isSymbolic())
			branchName = CLIText.get().branchDetachedHEAD;
		else {
			branchName = head.getTarget().getName();
			if (branchName.startsWith(Constants.R_HEADS))
				branchName = branchName.substring(Constants.R_HEADS.length());
		}
		out.println("[" + branchName + " " + commit.name() + "] "
				+ commit.getShortMessage());
	}
}
