/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2006-2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org>
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

import java.nio.charset.Charset;
import java.util.List;

/**
 * Mutable builder to construct a commit recording the state of a project.
 *
 * Applications should use this object when they need to manually construct a
 * commit and want precise control over its fields. For a higher level interface
 * see {@link org.eclipse.jgit.api.CommitCommand}.
 */
public class Commit {
	private static final ObjectId[] EMPTY_OBJECTID_LIST = new ObjectId[0];

	private ObjectId commitId;

	private ObjectId treeId;

	private ObjectId[] parentIds;

	private PersonIdent author;

	private PersonIdent committer;

	private String message;

	private Charset encoding;

	/** Initialize an empty commit. */
	public Commit() {
		parentIds = EMPTY_OBJECTID_LIST;
		encoding = Constants.CHARSET;
	}

	/** @return this commit's object id. */
	public ObjectId getCommitId() {
		return commitId;
	}

	/**
	 * Set the id of this commit object.
	 *
	 * @param id
	 *            the id that we calculated for this object.
	 */
	public void setCommitId(final ObjectId id) {
		commitId = id;
	}

	/** @return id of the root tree listing this commit's snapshot. */
	public ObjectId getTreeId() {
		return treeId;
	}

	/**
	 * Set the tree id for this commit object
	 *
	 * @param id
	 *            the tree identity.
	 */
	public void setTreeId(AnyObjectId id) {
		treeId = id.copy();
		commitId = null;
	}

	/** @return the author of this commit (who wrote it). */
	public PersonIdent getAuthor() {
		return author;
	}

	/**
	 * Set the author (name, email address, and date) of who wrote the commit.
	 *
	 * @param newAuthor
	 *            the new author. Should not be null.
	 */
	public void setAuthor(PersonIdent newAuthor) {
		author = newAuthor;
		commitId = null;
	}

	/** @return the committer and commit time for this object. */
	public PersonIdent getCommitter() {
		return committer;
	}

	/**
	 * Set the committer and commit time for this object
	 *
	 * @param newCommitter
	 *            the committer information. Should not be null.
	 */
	public void setCommitter(PersonIdent newCommitter) {
		committer = newCommitter;
		commitId = null;
	}

	/** @return the ancestors of this commit. Never null. */
	public ObjectId[] getParentIds() {
		return parentIds;
	}

	/**
	 * Set the parent of this commit.
	 *
	 * @param newParent
	 *            the single parent for the commit.
	 */
	public void setParentId(AnyObjectId newParent) {
		parentIds = new ObjectId[] { newParent.copy() };
		commitId = null;
	}

	/**
	 * Set the parents of this commit.
	 *
	 * @param parent1
	 *            the first parent of this commit. Typically this is the current
	 *            value of the {@code HEAD} reference and is thus the current
	 *            branch's position in history.
	 * @param parent2
	 *            the second parent of this merge commit. Usually this is the
	 *            branch being merged into the current branch.
	 */
	public void setParentIds(AnyObjectId parent1, AnyObjectId parent2) {
		parentIds = new ObjectId[] { parent1.copy(), parent2.copy() };
		commitId = null;
	}

	/**
	 * Set the parents of this commit.
	 *
	 * @param newParents
	 *            the entire list of parents for this commit.
	 */
	public void setParentIds(ObjectId... newParents) {
		parentIds = new ObjectId[newParents.length];
		for (int i = 0; i < newParents.length; i++)
			parentIds[i] = newParents[i].copy();
		commitId = null;
	}

	/**
	 * Set the parents of this commit.
	 *
	 * @param newParents
	 *            the entire list of parents for this commit.
	 */
	public void setParentIds(List<? extends AnyObjectId> newParents) {
		parentIds = new ObjectId[newParents.size()];
		for (int i = 0; i < newParents.size(); i++)
			parentIds[i] = newParents.get(i).copy();
		commitId = null;
	}

	/**
	 * Add a parent onto the end of the parent list.
	 *
	 * @param additionalParent
	 *            new parent to add onto the end of the current parent list.
	 */
	public void addParentId(AnyObjectId additionalParent) {
		if (parentIds.length == 0) {
			setParentId(additionalParent);
		} else {
			ObjectId[] newParents = new ObjectId[parentIds.length + 1];
			for (int i = 0; i < parentIds.length; i++)
				newParents[i] = parentIds[i];
			newParents[parentIds.length] = additionalParent.copy();
			parentIds = newParents;
			commitId = null;
		}
	}

	/** @return the complete commit message. */
	public String getMessage() {
		return message;
	}

	/**
	 * Set the commit message.
	 *
	 * @param newMessage
	 *            the commit message. Should not be null.
	 */
	public void setMessage(final String newMessage) {
		message = newMessage;
	}

	/**
	 * Set the encoding for the commit information
	 *
	 * @param encodingName
	 *            the encoding name. See {@link Charset#forName(String)}.
	 */
	public void setEncoding(String encodingName) {
		encoding = Charset.forName(encodingName);
	}

	/**
	 * Set the encoding for the commit information
	 *
	 * @param enc
	 *            the encoding to use.
	 */
	public void setEncoding(Charset enc) {
		encoding = enc;
	}

	/** @return the encoding that should be used for the commit message text. */
	public Charset getEncoding() {
		return encoding;
	}

	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append("Commit");
		if (commitId != null)
			r.append("[" + commitId.name() + "]");
		r.append("={\n");

		r.append("tree ");
		r.append(treeId != null ? treeId.name() : "NOT_SET");
		r.append("\n");

		for (ObjectId p : parentIds) {
			r.append("parent ");
			r.append(p.name());
			r.append("\n");
		}

		r.append("author ");
		r.append(author != null ? author.toString() : "NOT_SET");
		r.append("\n");

		r.append("committer ");
		r.append(committer != null ? committer.toString() : "NOT_SET");
		r.append("\n");

		if (encoding != null && encoding != Constants.CHARSET) {
			r.append("encoding ");
			r.append(encoding.name());
			r.append("\n");
		}

		r.append("\n");
		r.append(message != null ? message : "");
		r.append("}");
		return r.toString();
	}
}
