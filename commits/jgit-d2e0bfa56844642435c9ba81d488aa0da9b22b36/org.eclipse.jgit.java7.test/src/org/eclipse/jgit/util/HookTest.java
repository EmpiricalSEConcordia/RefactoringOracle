/*
 * Copyright (C) 2014 Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Assume;
import org.junit.Test;

public class HookTest extends RepositoryTestCase {

	@Test
	public void testFindHook() throws Exception {
		assumeSupportedPlatform();

		Hook h = Hook.PRE_COMMIT;
		assertNull("no hook should be installed", FS.DETECTED.findHook(db, h));
		File hookFile = writeHookFile(h.getName(),
				"#!/bin/bash\necho \"test $1 $2\"");
		assertEquals("exected to find pre-commit hook", hookFile,
				FS.DETECTED.findHook(db, h));
	}

	@Test
	public void testRunHook() throws Exception {
		assumeSupportedPlatform();

		Hook h = Hook.PRE_COMMIT;
		writeHookFile(
				h.getName(),
				"#!/bin/sh\necho \"test $1 $2\"\nread INPUT\necho $INPUT\necho 1>&2 \"stderr\"");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		ProcessResult res = FS.DETECTED.runIfPresent(db, h, new String[] {
				"arg1", "arg2" },
				new PrintStream(out), new PrintStream(err), "stdin");
		assertEquals("unexpected hook output", "test arg1 arg2\nstdin\n",
				out.toString("UTF-8"));
		assertEquals("unexpected output on stderr stream", "stderr\n",
				err.toString("UTF-8"));
		assertEquals("unexpected exit code", 0, res.getExitCode());
		assertEquals("unexpected process status", ProcessResult.Status.OK,
				res.getStatus());
	}

	private File writeHookFile(final String name, final String data)
			throws IOException {
		File path = new File(db.getWorkTree() + "/.git/hooks/", name);
		JGitTestUtil.write(path, data);
		FS.DETECTED.setExecute(path, true);
		return path;
	}

	private void assumeSupportedPlatform() {
		Assume.assumeTrue(FS.DETECTED instanceof FS_POSIX
				|| FS.DETECTED instanceof FS_Win32_Java7Cygwin);
	}
}
