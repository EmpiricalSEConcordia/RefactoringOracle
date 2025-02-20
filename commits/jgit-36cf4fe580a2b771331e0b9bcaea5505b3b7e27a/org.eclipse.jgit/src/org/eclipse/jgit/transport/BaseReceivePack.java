/*
 * Copyright (C) 2008-2010, Google Inc.
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

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_ATOMIC;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_DELETE_REFS;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_OFS_DELTA;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_QUIET;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_REPORT_STATUS;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_PUSH_OPTIONS;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_SIDE_BAND_64K;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_AGENT;
import static org.eclipse.jgit.transport.SideBandOutputStream.CH_DATA;
import static org.eclipse.jgit.transport.SideBandOutputStream.CH_ERROR;
import static org.eclipse.jgit.transport.SideBandOutputStream.CH_PROGRESS;
import static org.eclipse.jgit.transport.SideBandOutputStream.MAX_BUF;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TooLargePackException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.PackLock;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.util.io.InterruptTimer;
import org.eclipse.jgit.util.io.LimitedInputStream;
import org.eclipse.jgit.util.io.TimeoutInputStream;
import org.eclipse.jgit.util.io.TimeoutOutputStream;

/**
 * Base implementation of the side of a push connection that receives objects.
 * <p>
 * Contains high-level operations for initializing and closing streams,
 * advertising refs, reading commands, and receiving and applying a pack.
 * Subclasses compose these operations into full service implementations.
 */
public abstract class BaseReceivePack {
	/** Data in the first line of a request, the line itself plus capabilities. */
	public static class FirstLine {
		private final String line;
		private final Set<String> capabilities;

		/**
		 * Parse the first line of a receive-pack request.
		 *
		 * @param line
		 *            line from the client.
		 */
		public FirstLine(String line) {
			final HashSet<String> caps = new HashSet<String>();
			final int nul = line.indexOf('\0');
			if (nul >= 0) {
				for (String c : line.substring(nul + 1).split(" ")) //$NON-NLS-1$
					caps.add(c);
				this.line = line.substring(0, nul);
			} else
				this.line = line;
			this.capabilities = Collections.unmodifiableSet(caps);
		}

		/** @return non-capabilities part of the line. */
		public String getLine() {
			return line;
		}

		/** @return capabilities parsed from the line. */
		public Set<String> getCapabilities() {
			return capabilities;
		}
	}

	/** Database we write the stored objects into. */
	private final Repository db;

	/** Revision traversal support over {@link #db}. */
	private final RevWalk walk;

	/**
	 * Is the client connection a bi-directional socket or pipe?
	 * <p>
	 * If true, this class assumes it can perform multiple read and write cycles
	 * with the client over the input and output streams. This matches the
	 * functionality available with a standard TCP/IP connection, or a local
	 * operating system or in-memory pipe.
	 * <p>
	 * If false, this class runs in a read everything then output results mode,
	 * making it suitable for single round-trip systems RPCs such as HTTP.
	 */
	private boolean biDirectionalPipe = true;

	/** Expecting data after the pack footer */
	private boolean expectDataAfterPackFooter;

	/** Should an incoming transfer validate objects? */
	private ObjectChecker objectChecker;

	/** Should an incoming transfer permit create requests? */
	private boolean allowCreates;

	/** Should an incoming transfer permit delete requests? */
	private boolean allowAnyDeletes;
	private boolean allowBranchDeletes;

	/** Should an incoming transfer permit non-fast-forward requests? */
	private boolean allowNonFastForwards;

	/** Should an incoming transfer permit push options? **/
	private boolean allowPushOptions;

	/**
	 * Should the requested ref updates be performed as a single atomic
	 * transaction?
	 */
	private boolean atomic;

	private boolean allowOfsDelta;
	private boolean allowQuiet = true;

	/** Identity to record action as within the reflog. */
	private PersonIdent refLogIdent;

	/** Hook used while advertising the refs to the client. */
	private AdvertiseRefsHook advertiseRefsHook;

	/** Filter used while advertising the refs to the client. */
	private RefFilter refFilter;

	/** Timeout in seconds to wait for client interaction. */
	private int timeout;

	/** Timer to manage {@link #timeout}. */
	private InterruptTimer timer;

	private TimeoutInputStream timeoutIn;

	// Original stream passed to init(), since rawOut may be wrapped in a
	// sideband.
	private OutputStream origOut;

	/** Raw input stream. */
	protected InputStream rawIn;

	/** Raw output stream. */
	protected OutputStream rawOut;

	/** Optional message output stream. */
	protected OutputStream msgOut;
	private SideBandOutputStream errOut;

	/** Packet line input stream around {@link #rawIn}. */
	protected PacketLineIn pckIn;

	/** Packet line output stream around {@link #rawOut}. */
	protected PacketLineOut pckOut;

	private final MessageOutputWrapper msgOutWrapper = new MessageOutputWrapper();

	private PackParser parser;

	/** The refs we advertised as existing at the start of the connection. */
	private Map<String, Ref> refs;

	/** All SHA-1s shown to the client, which can be possible edges. */
	private Set<ObjectId> advertisedHaves;

	/** Capabilities requested by the client. */
	private Set<String> enabledCapabilities;
	String userAgent;
	private Set<ObjectId> clientShallowCommits;
	private List<ReceiveCommand> commands;

	private StringBuilder advertiseError;

	/** If {@link BasePackPushConnection#CAPABILITY_SIDE_BAND_64K} is enabled. */
	private boolean sideBand;

	private boolean quiet;

	/**
	 * A list of option strings associated with a push.
	 * @since 4.5
	 */
	protected List<String> pushOptions;

	/**
	 * Whether the client intends to use push options.
	 * @since 4.5
	 */
	protected boolean usePushOptions;

	/** Lock around the received pack file, while updating refs. */
	private PackLock packLock;

	private boolean checkReferencedIsReachable;

	/** Git object size limit */
	private long maxObjectSizeLimit;

	/** Total pack size limit */
	private long maxPackSizeLimit = -1;

	/** The size of the received pack, including index size */
	private Long packSize;

	private PushCertificateParser pushCertificateParser;
	private SignedPushConfig signedPushConfig;
	private PushCertificate pushCert;

	/**
	 * Get the push certificate used to verify the pusher's identity.
	 * <p>
	 * Only valid after commands are read from the wire.
	 *
	 * @return the parsed certificate, or null if push certificates are disabled
	 *         or no cert was presented by the client.
	 * @since 4.1
	 */
	public PushCertificate getPushCertificate() {
		return pushCert;
	}

	/**
	 * Set the push certificate used to verify the pusher's identity.
	 * <p>
	 * Should only be called if reconstructing an instance without going through
	 * the normal {@link #recvCommands()} flow.
	 *
	 * @param cert
	 *            the push certificate to set.
	 * @since 4.1
	 */
	public void setPushCertificate(PushCertificate cert) {
		pushCert = cert;
	}

	/**
	 * Create a new pack receive for an open repository.
	 *
	 * @param into
	 *            the destination repository.
	 */
	protected BaseReceivePack(final Repository into) {
		db = into;
		walk = new RevWalk(db);

		TransferConfig tc = db.getConfig().get(TransferConfig.KEY);
		objectChecker = tc.newReceiveObjectChecker();

		ReceiveConfig rc = db.getConfig().get(ReceiveConfig.KEY);
		allowCreates = rc.allowCreates;
		allowAnyDeletes = true;
		allowBranchDeletes = rc.allowDeletes;
		allowNonFastForwards = rc.allowNonFastForwards;
		allowOfsDelta = rc.allowOfsDelta;
		allowPushOptions = rc.allowPushOptions;
		advertiseRefsHook = AdvertiseRefsHook.DEFAULT;
		refFilter = RefFilter.DEFAULT;
		advertisedHaves = new HashSet<ObjectId>();
		clientShallowCommits = new HashSet<ObjectId>();
		signedPushConfig = rc.signedPush;
	}

	/** Configuration for receive operations. */
	protected static class ReceiveConfig {
		static final SectionParser<ReceiveConfig> KEY = new SectionParser<ReceiveConfig>() {
			public ReceiveConfig parse(final Config cfg) {
				return new ReceiveConfig(cfg);
			}
		};

		final boolean allowCreates;
		final boolean allowDeletes;
		final boolean allowNonFastForwards;
		final boolean allowOfsDelta;
		final boolean allowPushOptions;

		final SignedPushConfig signedPush;

		ReceiveConfig(final Config config) {
			allowCreates = true;
			allowDeletes = !config.getBoolean("receive", "denydeletes", false); //$NON-NLS-1$ //$NON-NLS-2$
			allowNonFastForwards = !config.getBoolean("receive", //$NON-NLS-1$
					"denynonfastforwards", false); //$NON-NLS-1$
			allowOfsDelta = config.getBoolean("repack", "usedeltabaseoffset", //$NON-NLS-1$ //$NON-NLS-2$
					true);
			allowPushOptions = config.getBoolean("receive", "pushoptions", //$NON-NLS-1$ //$NON-NLS-2$
					false);
			signedPush = SignedPushConfig.KEY.parse(config);
		}
	}

	/**
	 * Output stream that wraps the current {@link #msgOut}.
	 * <p>
	 * We don't want to expose {@link #msgOut} directly because it can change
	 * several times over the course of a session.
	 */
	class MessageOutputWrapper extends OutputStream {
		@Override
		public void write(int ch) {
			if (msgOut != null) {
				try {
					msgOut.write(ch);
				} catch (IOException e) {
					// Ignore write failures.
				}
			}
		}

		@Override
		public void write(byte[] b, int off, int len) {
			if (msgOut != null) {
				try {
					msgOut.write(b, off, len);
				} catch (IOException e) {
					// Ignore write failures.
				}
			}
		}

		@Override
		public void write(byte[] b) {
			write(b, 0, b.length);
		}

		@Override
		public void flush() {
			if (msgOut != null) {
				try {
					msgOut.flush();
				} catch (IOException e) {
					// Ignore write failures.
				}
			}
		}
	}

	/** @return the process name used for pack lock messages. */
	protected abstract String getLockMessageProcessName();

	/** @return the repository this receive completes into. */
	public final Repository getRepository() {
		return db;
	}

	/** @return the RevWalk instance used by this connection. */
	public final RevWalk getRevWalk() {
		return walk;
	}

	/**
	 * Get refs which were advertised to the client.
	 *
	 * @return all refs which were advertised to the client, or null if
	 *         {@link #setAdvertisedRefs(Map, Set)} has not been called yet.
	 */
	public final Map<String, Ref> getAdvertisedRefs() {
		return refs;
	}

	/**
	 * Set the refs advertised by this ReceivePack.
	 * <p>
	 * Intended to be called from a {@link PreReceiveHook}.
	 *
	 * @param allRefs
	 *            explicit set of references to claim as advertised by this
	 *            ReceivePack instance. This overrides any references that
	 *            may exist in the source repository. The map is passed
	 *            to the configured {@link #getRefFilter()}. If null, assumes
	 *            all refs were advertised.
	 * @param additionalHaves
	 *            explicit set of additional haves to claim as advertised. If
	 *            null, assumes the default set of additional haves from the
	 *            repository.
	 */
	public void setAdvertisedRefs(Map<String, Ref> allRefs, Set<ObjectId> additionalHaves) {
		refs = allRefs != null ? allRefs : db.getAllRefs();
		refs = refFilter.filter(refs);

		Ref head = refs.get(Constants.HEAD);
		if (head != null && head.isSymbolic())
			refs.remove(Constants.HEAD);

		for (Ref ref : refs.values()) {
			if (ref.getObjectId() != null)
				advertisedHaves.add(ref.getObjectId());
		}
		if (additionalHaves != null)
			advertisedHaves.addAll(additionalHaves);
		else
			advertisedHaves.addAll(db.getAdditionalHaves());
	}

	/**
	 * Get objects advertised to the client.
	 *
	 * @return the set of objects advertised to the as present in this repository,
	 *         or null if {@link #setAdvertisedRefs(Map, Set)} has not been called
	 *         yet.
	 */
	public final Set<ObjectId> getAdvertisedObjects() {
		return advertisedHaves;
	}

	/**
	 * @return true if this instance will validate all referenced, but not
	 *         supplied by the client, objects are reachable from another
	 *         reference.
	 */
	public boolean isCheckReferencedObjectsAreReachable() {
		return checkReferencedIsReachable;
	}

	/**
	 * Validate all referenced but not supplied objects are reachable.
	 * <p>
	 * If enabled, this instance will verify that references to objects not
	 * contained within the received pack are already reachable through at least
	 * one other reference displayed as part of {@link #getAdvertisedRefs()}.
	 * <p>
	 * This feature is useful when the application doesn't trust the client to
	 * not provide a forged SHA-1 reference to an object, in an attempt to
	 * access parts of the DAG that they aren't allowed to see and which have
	 * been hidden from them via the configured {@link AdvertiseRefsHook} or
	 * {@link RefFilter}.
	 * <p>
	 * Enabling this feature may imply at least some, if not all, of the same
	 * functionality performed by {@link #setCheckReceivedObjects(boolean)}.
	 * Applications are encouraged to enable both features, if desired.
	 *
	 * @param b
	 *            {@code true} to enable the additional check.
	 */
	public void setCheckReferencedObjectsAreReachable(boolean b) {
		this.checkReferencedIsReachable = b;
	}

	/**
	 * @return true if this class expects a bi-directional pipe opened between
	 *         the client and itself. The default is true.
	 */
	public boolean isBiDirectionalPipe() {
		return biDirectionalPipe;
	}

	/**
	 * @param twoWay
	 *            if true, this class will assume the socket is a fully
	 *            bidirectional pipe between the two peers and takes advantage
	 *            of that by first transmitting the known refs, then waiting to
	 *            read commands. If false, this class assumes it must read the
	 *            commands before writing output and does not perform the
	 *            initial advertising.
	 */
	public void setBiDirectionalPipe(final boolean twoWay) {
		biDirectionalPipe = twoWay;
	}

	/** @return true if there is data expected after the pack footer. */
	public boolean isExpectDataAfterPackFooter() {
		return expectDataAfterPackFooter;
	}

	/**
	 * @param e
	 *            true if there is additional data in InputStream after pack.
	 */
	public void setExpectDataAfterPackFooter(boolean e) {
		expectDataAfterPackFooter = e;
	}

	/**
	 * @return true if this instance will verify received objects are formatted
	 *         correctly. Validating objects requires more CPU time on this side
	 *         of the connection.
	 */
	public boolean isCheckReceivedObjects() {
		return objectChecker != null;
	}

	/**
	 * @param check
	 *            true to enable checking received objects; false to assume all
	 *            received objects are valid.
	 * @see #setObjectChecker(ObjectChecker)
	 */
	public void setCheckReceivedObjects(final boolean check) {
		if (check && objectChecker == null)
			setObjectChecker(new ObjectChecker());
		else if (!check && objectChecker != null)
			setObjectChecker(null);
	}

	/**
	 * @param impl if non-null the object checking instance to verify each
	 *        received object with; null to disable object checking.
	 * @since 3.4
	 */
	public void setObjectChecker(ObjectChecker impl) {
		objectChecker = impl;
	}

	/** @return true if the client can request refs to be created. */
	public boolean isAllowCreates() {
		return allowCreates;
	}

	/**
	 * @param canCreate
	 *            true to permit create ref commands to be processed.
	 */
	public void setAllowCreates(final boolean canCreate) {
		allowCreates = canCreate;
	}

	/** @return true if the client can request refs to be deleted. */
	public boolean isAllowDeletes() {
		return allowAnyDeletes;
	}

	/**
	 * @param canDelete
	 *            true to permit delete ref commands to be processed.
	 */
	public void setAllowDeletes(final boolean canDelete) {
		allowAnyDeletes = canDelete;
	}

	/**
	 * @return true if the client can delete from {@code refs/heads/}.
	 * @since 3.6
	 */
	public boolean isAllowBranchDeletes() {
		return allowBranchDeletes;
	}

	/**
	 * @param canDelete
	 *            true to permit deletion of branches from the
	 *            {@code refs/heads/} namespace.
	 * @since 3.6
	 */
	public void setAllowBranchDeletes(boolean canDelete) {
		allowBranchDeletes = canDelete;
	}

	/**
	 * @return true if the client can request non-fast-forward updates of a ref,
	 *         possibly making objects unreachable.
	 */
	public boolean isAllowNonFastForwards() {
		return allowNonFastForwards;
	}

	/**
	 * @param canRewind
	 *            true to permit the client to ask for non-fast-forward updates
	 *            of an existing ref.
	 */
	public void setAllowNonFastForwards(final boolean canRewind) {
		allowNonFastForwards = canRewind;
	}

	/**
	 * @return true if the client's commands should be performed as a single
	 *         atomic transaction.
	 * @since 4.4
	 */
	public boolean isAtomic() {
		return atomic;
	}

	/**
	 * @param atomic
	 *            true to perform the client's commands as a single atomic
	 *            transaction.
	 * @since 4.4
	 */
	public void setAtomic(boolean atomic) {
		this.atomic = atomic;
	}

	/** @return identity of the user making the changes in the reflog. */
	public PersonIdent getRefLogIdent() {
		return refLogIdent;
	}

	/**
	 * Set the identity of the user appearing in the affected reflogs.
	 * <p>
	 * The timestamp portion of the identity is ignored. A new identity with the
	 * current timestamp will be created automatically when the updates occur
	 * and the log records are written.
	 *
	 * @param pi
	 *            identity of the user. If null the identity will be
	 *            automatically determined based on the repository
	 *            configuration.
	 */
	public void setRefLogIdent(final PersonIdent pi) {
		refLogIdent = pi;
	}

	/** @return the hook used while advertising the refs to the client */
	public AdvertiseRefsHook getAdvertiseRefsHook() {
		return advertiseRefsHook;
	}

	/** @return the filter used while advertising the refs to the client */
	public RefFilter getRefFilter() {
		return refFilter;
	}

	/**
	 * Set the hook used while advertising the refs to the client.
	 * <p>
	 * If the {@link AdvertiseRefsHook} chooses to call
	 * {@link #setAdvertisedRefs(Map,Set)}, only refs set by this hook
	 * <em>and</em> selected by the {@link RefFilter} will be shown to the client.
	 * Clients may still attempt to create or update a reference not advertised by
	 * the configured {@link AdvertiseRefsHook}. These attempts should be rejected
	 * by a matching {@link PreReceiveHook}.
	 *
	 * @param advertiseRefsHook
	 *            the hook; may be null to show all refs.
	 */
	public void setAdvertiseRefsHook(final AdvertiseRefsHook advertiseRefsHook) {
		if (advertiseRefsHook != null)
			this.advertiseRefsHook = advertiseRefsHook;
		else
			this.advertiseRefsHook = AdvertiseRefsHook.DEFAULT;
	}

	/**
	 * Set the filter used while advertising the refs to the client.
	 * <p>
	 * Only refs allowed by this filter will be shown to the client.
	 * The filter is run against the refs specified by the
	 * {@link AdvertiseRefsHook} (if applicable).
	 *
	 * @param refFilter
	 *            the filter; may be null to show all refs.
	 */
	public void setRefFilter(final RefFilter refFilter) {
		this.refFilter = refFilter != null ? refFilter : RefFilter.DEFAULT;
	}

	/** @return timeout (in seconds) before aborting an IO operation. */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Set the timeout before willing to abort an IO call.
	 *
	 * @param seconds
	 *            number of seconds to wait (with no data transfer occurring)
	 *            before aborting an IO read or write operation with the
	 *            connected client.
	 */
	public void setTimeout(final int seconds) {
		timeout = seconds;
	}

	/**
	 * Set the maximum allowed Git object size.
	 * <p>
	 * If an object is larger than the given size the pack-parsing will throw an
	 * exception aborting the receive-pack operation.
	 *
	 * @param limit
	 *            the Git object size limit. If zero then there is not limit.
	 */
	public void setMaxObjectSizeLimit(final long limit) {
		maxObjectSizeLimit = limit;
	}


	/**
	 * Set the maximum allowed pack size.
	 * <p>
	 * A pack exceeding this size will be rejected.
	 *
	 * @param limit
	 *            the pack size limit, in bytes
	 *
	 * @since 3.3
	 */
	public void setMaxPackSizeLimit(final long limit) {
		if (limit < 0)
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().receivePackInvalidLimit, Long.valueOf(limit)));
		maxPackSizeLimit = limit;
	}

	/**
	 * Check whether the client expects a side-band stream.
	 *
	 * @return true if the client has advertised a side-band capability, false
	 *     otherwise.
	 * @throws RequestNotYetReadException
	 *             if the client's request has not yet been read from the wire, so
	 *             we do not know if they expect side-band. Note that the client
	 *             may have already written the request, it just has not been
	 *             read.
	 */
	public boolean isSideBand() throws RequestNotYetReadException {
		if (enabledCapabilities == null)
			throw new RequestNotYetReadException();
		return enabledCapabilities.contains(CAPABILITY_SIDE_BAND_64K);
	}

	/**
	 * @return true if clients may request avoiding noisy progress messages.
	 * @since 4.0
	 */
	public boolean isAllowQuiet() {
		return allowQuiet;
	}

	/**
	 * Configure if clients may request the server skip noisy messages.
	 *
	 * @param allow
	 *            true to allow clients to request quiet behavior; false to
	 *            refuse quiet behavior and send messages anyway. This may be
	 *            necessary if processing is slow and the client-server network
	 *            connection can timeout.
	 * @since 4.0
	 */
	public void setAllowQuiet(boolean allow) {
		allowQuiet = allow;
	}

	/**
	 * @return true if the server supports the receiving of push options.
	 * @since 4.5
	 */
	public boolean isAllowPushOptions() {
		return allowPushOptions;
	}

	/**
	 * Configure if the server supports the receiving of push options.
	 *
	 * @param allow
	 *            true to permit option strings.
	 * @since 4.5
	 */
	public void setAllowPushOptions(boolean allow) {
		allowPushOptions = allow;
	}

	/**
	 * True if the client wants less verbose output.
	 *
	 * @return true if the client has requested the server to be less verbose.
	 * @throws RequestNotYetReadException
	 *             if the client's request has not yet been read from the wire,
	 *             so we do not know if they expect side-band. Note that the
	 *             client may have already written the request, it just has not
	 *             been read.
	 * @since 4.0
	 */
	public boolean isQuiet() throws RequestNotYetReadException {
		if (enabledCapabilities == null)
			throw new RequestNotYetReadException();
		return quiet;
	}

	/**
	 * Gets an unmodifiable view of the option strings associated with the push.
	 *
	 * @return an unmodifiable view of pushOptions, or null (if pushOptions is).
	 * @throws IllegalStateException
	 *             if allowPushOptions has not been set to true.
	 * @throws RequestNotYetReadException
	 *             if the client's request has not yet been read from the wire,
	 *             so we do not know if they expect push options. Note that the
	 *             client may have already written the request, it just has not
	 *             been read.
	 * @since 4.5
	 */
	@Nullable
	public List<String> getPushOptions() {
		if (!allowPushOptions) {
			// Reading push options without a prior setAllowPushOptions(true)
			// call doesn't make sense.
			throw new IllegalStateException();
		}
		if (enabledCapabilities == null) {
			// Push options are not available until receive() has been called.
			throw new RequestNotYetReadException();
		}
		if (pushOptions == null) {
			// The client doesn't support push options. Return null to
			// distinguish this from the case where the client declared support
			// for push options and sent an empty list of them.
			return null;
		}
		return Collections.unmodifiableList(pushOptions);
	}

	/**
	 * Set the configuration for push certificate verification.
	 *
	 * @param cfg
	 *            new configuration; if this object is null or its {@link
	 *            SignedPushConfig#getCertNonceSeed()} is null, push certificate
	 *            verification will be disabled.
	 * @since 4.1
	 */
	public void setSignedPushConfig(SignedPushConfig cfg) {
		signedPushConfig = cfg;
	}

	private PushCertificateParser getPushCertificateParser() {
		if (pushCertificateParser == null) {
			pushCertificateParser = new PushCertificateParser(db, signedPushConfig);
		}
		return pushCertificateParser;
	}

	/**
	 * Get the user agent of the client.
	 * <p>
	 * If the client is new enough to use {@code agent=} capability that value
	 * will be returned. Older HTTP clients may also supply their version using
	 * the HTTP {@code User-Agent} header. The capability overrides the HTTP
	 * header if both are available.
	 * <p>
	 * When an HTTP request has been received this method returns the HTTP
	 * {@code User-Agent} header value until capabilities have been parsed.
	 *
	 * @return user agent supplied by the client. Available only if the client
	 *         is new enough to advertise its user agent.
	 * @since 4.0
	 */
	public String getPeerUserAgent() {
		return UserAgent.getAgent(enabledCapabilities, userAgent);
	}

	/** @return all of the command received by the current request. */
	public List<ReceiveCommand> getAllCommands() {
		return Collections.unmodifiableList(commands);
	}

	/**
	 * Send an error message to the client.
	 * <p>
	 * If any error messages are sent before the references are advertised to
	 * the client, the errors will be sent instead of the advertisement and the
	 * receive operation will be aborted. All clients should receive and display
	 * such early stage errors.
	 * <p>
	 * If the reference advertisements have already been sent, messages are sent
	 * in a side channel. If the client doesn't support receiving messages, the
	 * message will be discarded, with no other indication to the caller or to
	 * the client.
	 * <p>
	 * {@link PreReceiveHook}s should always try to use
	 * {@link ReceiveCommand#setResult(Result, String)} with a result status of
	 * {@link Result#REJECTED_OTHER_REASON} to indicate any reasons for
	 * rejecting an update. Messages attached to a command are much more likely
	 * to be returned to the client.
	 *
	 * @param what
	 *            string describing the problem identified by the hook. The
	 *            string must not end with an LF, and must not contain an LF.
	 */
	public void sendError(final String what) {
		if (refs == null) {
			if (advertiseError == null)
				advertiseError = new StringBuilder();
			advertiseError.append(what).append('\n');
		} else {
			msgOutWrapper.write(Constants.encode("error: " + what + "\n")); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private void fatalError(String msg) {
		if (errOut != null) {
			try {
				errOut.write(Constants.encode(msg));
				errOut.flush();
			} catch (IOException e) {
				// Ignore write failures
			}
		} else {
			sendError(msg);
		}
	}

	/**
	 * Send a message to the client, if it supports receiving them.
	 * <p>
	 * If the client doesn't support receiving messages, the message will be
	 * discarded, with no other indication to the caller or to the client.
	 *
	 * @param what
	 *            string describing the problem identified by the hook. The
	 *            string must not end with an LF, and must not contain an LF.
	 */
	public void sendMessage(final String what) {
		msgOutWrapper.write(Constants.encode(what + "\n")); //$NON-NLS-1$
	}

	/** @return an underlying stream for sending messages to the client. */
	public OutputStream getMessageOutputStream() {
		return msgOutWrapper;
	}

	/**
	 * Get the size of the received pack file including the index size.
	 *
	 * This can only be called if the pack is already received.
	 *
	 * @return the size of the received pack including index size
	 * @throws IllegalStateException
	 *             if called before the pack has been received
	 * @since 3.3
	 */
	public long getPackSize() {
		if (packSize != null)
			return packSize.longValue();
		throw new IllegalStateException(JGitText.get().packSizeNotSetYet);
	}

	/**
	 * Get the commits from the client's shallow file.
	 *
	 * @return if the client is a shallow repository, the list of edge commits
	 *     that define the client's shallow boundary. Empty set if the client
	 *     is earlier than Git 1.9, or is a full clone.
	 * @since 3.5
	 */
	protected Set<ObjectId> getClientShallowCommits() {
		return clientShallowCommits;
	}

	/** @return true if any commands to be executed have been read. */
	protected boolean hasCommands() {
		return !commands.isEmpty();
	}

	/** @return true if an error occurred that should be advertised. */
	protected boolean hasError() {
		return advertiseError != null;
	}

	/**
	 * Initialize the instance with the given streams.
	 *
	 * @param input
	 *            raw input to read client commands and pack data from. Caller
	 *            must ensure the input is buffered, otherwise read performance
	 *            may suffer.
	 * @param output
	 *            response back to the Git network client. Caller must ensure
	 *            the output is buffered, otherwise write performance may
	 *            suffer.
	 * @param messages
	 *            secondary "notice" channel to send additional messages out
	 *            through. When run over SSH this should be tied back to the
	 *            standard error channel of the command execution. For most
	 *            other network connections this should be null.
	 */
	protected void init(final InputStream input, final OutputStream output,
			final OutputStream messages) {
		origOut = output;
		rawIn = input;
		rawOut = output;
		msgOut = messages;

		if (timeout > 0) {
			final Thread caller = Thread.currentThread();
			timer = new InterruptTimer(caller.getName() + "-Timer"); //$NON-NLS-1$
			timeoutIn = new TimeoutInputStream(rawIn, timer);
			TimeoutOutputStream o = new TimeoutOutputStream(rawOut, timer);
			timeoutIn.setTimeout(timeout * 1000);
			o.setTimeout(timeout * 1000);
			rawIn = timeoutIn;
			rawOut = o;
		}

		if (maxPackSizeLimit >= 0)
			rawIn = new LimitedInputStream(rawIn, maxPackSizeLimit) {
				@Override
				protected void limitExceeded() throws TooLargePackException {
					throw new TooLargePackException(limit);
				}
			};

		pckIn = new PacketLineIn(rawIn);
		pckOut = new PacketLineOut(rawOut);
		pckOut.setFlushOnEnd(false);

		enabledCapabilities = new HashSet<String>();
		commands = new ArrayList<ReceiveCommand>();
	}

	/** @return advertised refs, or the default if not explicitly advertised. */
	protected Map<String, Ref> getAdvertisedOrDefaultRefs() {
		if (refs == null)
			setAdvertisedRefs(null, null);
		return refs;
	}

	/**
	 * Receive a pack from the stream and check connectivity if necessary.
	 *
	 * @throws IOException
	 *             an error occurred during unpacking or connectivity checking.
	 */
	protected void receivePackAndCheckConnectivity() throws IOException {
		receivePack();
		if (needCheckConnectivity())
			checkConnectivity();
		parser = null;
	}

	/**
	 * Unlock the pack written by this object.
	 *
	 * @throws IOException
	 *             the pack could not be unlocked.
	 */
	protected void unlockPack() throws IOException {
		if (packLock != null) {
			packLock.unlock();
			packLock = null;
		}
	}

	/**
	 * Generate an advertisement of available refs and capabilities.
	 *
	 * @param adv
	 *            the advertisement formatter.
	 * @throws IOException
	 *             the formatter failed to write an advertisement.
	 * @throws ServiceMayNotContinueException
	 *             the hook denied advertisement.
	 */
	public void sendAdvertisedRefs(final RefAdvertiser adv)
			throws IOException, ServiceMayNotContinueException {
		if (advertiseError != null) {
			adv.writeOne("ERR " + advertiseError); //$NON-NLS-1$
			return;
		}

		try {
			advertiseRefsHook.advertiseRefs(this);
		} catch (ServiceMayNotContinueException fail) {
			if (fail.getMessage() != null) {
				adv.writeOne("ERR " + fail.getMessage()); //$NON-NLS-1$
				fail.setOutput();
			}
			throw fail;
		}

		adv.init(db);
		adv.advertiseCapability(CAPABILITY_SIDE_BAND_64K);
		adv.advertiseCapability(CAPABILITY_DELETE_REFS);
		adv.advertiseCapability(CAPABILITY_REPORT_STATUS);
		if (allowQuiet)
			adv.advertiseCapability(CAPABILITY_QUIET);
		String nonce = getPushCertificateParser().getAdvertiseNonce();
		if (nonce != null) {
			adv.advertiseCapability(nonce);
		}
		if (db.getRefDatabase().performsAtomicTransactions())
			adv.advertiseCapability(CAPABILITY_ATOMIC);
		if (allowOfsDelta)
			adv.advertiseCapability(CAPABILITY_OFS_DELTA);
		if (allowPushOptions) {
			adv.advertiseCapability(CAPABILITY_PUSH_OPTIONS);
		}
		adv.advertiseCapability(OPTION_AGENT, UserAgent.get());
		adv.send(getAdvertisedOrDefaultRefs());
		for (ObjectId obj : advertisedHaves)
			adv.advertiseHave(obj);
		if (adv.isEmpty())
			adv.advertiseId(ObjectId.zeroId(), "capabilities^{}"); //$NON-NLS-1$
		adv.end();
	}

	/**
	 * Receive a list of commands from the input.
	 *
	 * @throws IOException
	 */
	protected void recvCommands() throws IOException {
		PushCertificateParser certParser = getPushCertificateParser();
		boolean firstPkt = true;
		try {
			for (;;) {
				String line;
				try {
					line = pckIn.readString();
				} catch (EOFException eof) {
					if (commands.isEmpty())
						return;
					throw eof;
				}
				if (line == PacketLineIn.END) {
					break;
				}

				if (line.length() >= 48 && line.startsWith("shallow ")) { //$NON-NLS-1$
					parseShallow(line.substring(8, 48));
					continue;
				}

				if (firstPkt) {
					firstPkt = false;
					FirstLine firstLine = new FirstLine(line);
					enabledCapabilities = firstLine.getCapabilities();
					line = firstLine.getLine();
					enableCapabilities();

					if (line.equals(GitProtocolConstants.OPTION_PUSH_CERT)) {
						certParser.receiveHeader(pckIn, !isBiDirectionalPipe());
						continue;
					}
				}

				if (line.equals(PushCertificateParser.BEGIN_SIGNATURE)) {
					certParser.receiveSignature(pckIn);
					continue;
				}

				ReceiveCommand cmd = parseCommand(line);
				if (cmd.getRefName().equals(Constants.HEAD)) {
					cmd.setResult(Result.REJECTED_CURRENT_BRANCH);
				} else {
					cmd.setRef(refs.get(cmd.getRefName()));
				}
				commands.add(cmd);
				if (certParser.enabled()) {
					certParser.addCommand(cmd);
				}
			}
			pushCert = certParser.build();
		} catch (PackProtocolException e) {
			if (sideBand) {
				try {
					pckIn.discardUntilEnd();
				} catch (IOException e2) {
					// Ignore read failures attempting to discard.
				}
			}
			fatalError(e.getMessage());
			throw e;
		}
	}

	private void parseShallow(String idStr) throws PackProtocolException {
		ObjectId id;
		try {
			id = ObjectId.fromString(idStr);
		} catch (InvalidObjectIdException e) {
			throw new PackProtocolException(e.getMessage(), e);
		}
		clientShallowCommits.add(id);
	}

	static ReceiveCommand parseCommand(String line) throws PackProtocolException {
          if (line == null || line.length() < 83) {
			throw new PackProtocolException(
					JGitText.get().errorInvalidProtocolWantedOldNewRef);
		}
		String oldStr = line.substring(0, 40);
		String newStr = line.substring(41, 81);
		ObjectId oldId, newId;
		try {
			oldId = ObjectId.fromString(oldStr);
			newId = ObjectId.fromString(newStr);
		} catch (InvalidObjectIdException e) {
			throw new PackProtocolException(
					JGitText.get().errorInvalidProtocolWantedOldNewRef, e);
		}
		String name = line.substring(82);
		if (!Repository.isValidRefName(name)) {
			throw new PackProtocolException(
					JGitText.get().errorInvalidProtocolWantedOldNewRef);
		}
		return new ReceiveCommand(oldId, newId, name);
	}

	/** Enable capabilities based on a previously read capabilities line. */
	protected void enableCapabilities() {
		sideBand = isCapabilityEnabled(CAPABILITY_SIDE_BAND_64K);
		quiet = allowQuiet && isCapabilityEnabled(CAPABILITY_QUIET);
		usePushOptions = allowPushOptions
				&& isCapabilityEnabled(CAPABILITY_PUSH_OPTIONS);
		if (usePushOptions) {
			pushOptions = new ArrayList<>();
		}
		if (sideBand) {
			OutputStream out = rawOut;

			rawOut = new SideBandOutputStream(CH_DATA, MAX_BUF, out);
			msgOut = new SideBandOutputStream(CH_PROGRESS, MAX_BUF, out);
			errOut = new SideBandOutputStream(CH_ERROR, MAX_BUF, out);

			pckOut = new PacketLineOut(rawOut);
			pckOut.setFlushOnEnd(false);
		}
	}

	/**
	 * Sets the client's intention regarding push options.
	 *
	 * @param usePushOptions
	 *            whether the client intends to use push options
	 * @since 4.5
	 */
	public void setUsePushOptions(boolean usePushOptions) {
		this.usePushOptions = usePushOptions;
	}

	/**
	 * Check if the peer requested a capability.
	 *
	 * @param name
	 *            protocol name identifying the capability.
	 * @return true if the peer requested the capability to be enabled.
	 */
	protected boolean isCapabilityEnabled(String name) {
		return enabledCapabilities.contains(name);
	}

	/** @return true if a pack is expected based on the list of commands. */
	protected boolean needPack() {
		for (final ReceiveCommand cmd : commands) {
			if (cmd.getType() != ReceiveCommand.Type.DELETE)
				return true;
		}
		return false;
	}

	/**
	 * Receive a pack from the input and store it in the repository.
	 *
	 * @throws IOException
	 *             an error occurred reading or indexing the pack.
	 */
	private void receivePack() throws IOException {
		// It might take the client a while to pack the objects it needs
		// to send to us.  We should increase our timeout so we don't
		// abort while the client is computing.
		//
		if (timeoutIn != null)
			timeoutIn.setTimeout(10 * timeout * 1000);

		ProgressMonitor receiving = NullProgressMonitor.INSTANCE;
		ProgressMonitor resolving = NullProgressMonitor.INSTANCE;
		if (sideBand && !quiet)
			resolving = new SideBandProgressMonitor(msgOut);

		try (ObjectInserter ins = db.newObjectInserter()) {
			String lockMsg = "jgit receive-pack"; //$NON-NLS-1$
			if (getRefLogIdent() != null)
				lockMsg += " from " + getRefLogIdent().toExternalString(); //$NON-NLS-1$

			parser = ins.newPackParser(rawIn);
			parser.setAllowThin(true);
			parser.setNeedNewObjectIds(checkReferencedIsReachable);
			parser.setNeedBaseObjectIds(checkReferencedIsReachable);
			parser.setCheckEofAfterPackFooter(!biDirectionalPipe
					&& !isExpectDataAfterPackFooter());
			parser.setExpectDataAfterPackFooter(isExpectDataAfterPackFooter());
			parser.setObjectChecker(objectChecker);
			parser.setLockMessage(lockMsg);
			parser.setMaxObjectSizeLimit(maxObjectSizeLimit);
			packLock = parser.parse(receiving, resolving);
			packSize = Long.valueOf(parser.getPackSize());
			ins.flush();
		}

		if (timeoutIn != null)
			timeoutIn.setTimeout(timeout * 1000);
	}

	private boolean needCheckConnectivity() {
		return isCheckReceivedObjects()
				|| isCheckReferencedObjectsAreReachable()
				|| !getClientShallowCommits().isEmpty();
	}

	private void checkConnectivity() throws IOException {
		ObjectIdSubclassMap<ObjectId> baseObjects = null;
		ObjectIdSubclassMap<ObjectId> providedObjects = null;
		ProgressMonitor checking = NullProgressMonitor.INSTANCE;
		if (sideBand && !quiet) {
			SideBandProgressMonitor m = new SideBandProgressMonitor(msgOut);
			m.setDelayStart(750, TimeUnit.MILLISECONDS);
			checking = m;
		}

		if (checkReferencedIsReachable) {
			baseObjects = parser.getBaseObjectIds();
			providedObjects = parser.getNewObjectIds();
		}
		parser = null;

		try (final ObjectWalk ow = new ObjectWalk(db)) {
			if (baseObjects != null) {
				ow.sort(RevSort.TOPO);
				if (!baseObjects.isEmpty())
					ow.sort(RevSort.BOUNDARY, true);
			}

			for (final ReceiveCommand cmd : commands) {
				if (cmd.getResult() != Result.NOT_ATTEMPTED)
					continue;
				if (cmd.getType() == ReceiveCommand.Type.DELETE)
					continue;
				ow.markStart(ow.parseAny(cmd.getNewId()));
			}
			for (final ObjectId have : advertisedHaves) {
				RevObject o = ow.parseAny(have);
				ow.markUninteresting(o);

				if (baseObjects != null && !baseObjects.isEmpty()) {
					o = ow.peel(o);
					if (o instanceof RevCommit)
						o = ((RevCommit) o).getTree();
					if (o instanceof RevTree)
						ow.markUninteresting(o);
				}
			}

			checking.beginTask(JGitText.get().countingObjects,
					ProgressMonitor.UNKNOWN);
			RevCommit c;
			while ((c = ow.next()) != null) {
				checking.update(1);
				if (providedObjects != null //
						&& !c.has(RevFlag.UNINTERESTING) //
						&& !providedObjects.contains(c))
					throw new MissingObjectException(c, Constants.TYPE_COMMIT);
			}

			RevObject o;
			while ((o = ow.nextObject()) != null) {
				checking.update(1);
				if (o.has(RevFlag.UNINTERESTING))
					continue;

				if (providedObjects != null) {
					if (providedObjects.contains(o))
						continue;
					else
						throw new MissingObjectException(o, o.getType());
				}

				if (o instanceof RevBlob && !db.hasObject(o))
					throw new MissingObjectException(o, Constants.TYPE_BLOB);
			}
			checking.endTask();

			if (baseObjects != null) {
				for (ObjectId id : baseObjects) {
					o = ow.parseAny(id);
					if (!o.has(RevFlag.UNINTERESTING))
						throw new MissingObjectException(o, o.getType());
				}
			}
		}
	}

	/** Validate the command list. */
	protected void validateCommands() {
		for (final ReceiveCommand cmd : commands) {
			final Ref ref = cmd.getRef();
			if (cmd.getResult() != Result.NOT_ATTEMPTED)
				continue;

			if (cmd.getType() == ReceiveCommand.Type.DELETE) {
				if (!isAllowDeletes()) {
					// Deletes are not supported on this repository.
					cmd.setResult(Result.REJECTED_NODELETE);
					continue;
				}
				if (!isAllowBranchDeletes()
						&& ref.getName().startsWith(Constants.R_HEADS)) {
					// Branches cannot be deleted, but other refs can.
					cmd.setResult(Result.REJECTED_NODELETE);
					continue;
				}
			}

			if (cmd.getType() == ReceiveCommand.Type.CREATE) {
				if (!isAllowCreates()) {
					cmd.setResult(Result.REJECTED_NOCREATE);
					continue;
				}

				if (ref != null && !isAllowNonFastForwards()) {
					// Creation over an existing ref is certainly not going
					// to be a fast-forward update. We can reject it early.
					//
					cmd.setResult(Result.REJECTED_NONFASTFORWARD);
					continue;
				}

				if (ref != null) {
					// A well behaved client shouldn't have sent us a
					// create command for a ref we advertised to it.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON,
							JGitText.get().refAlreadyExists);
					continue;
				}
			}

			if (cmd.getType() == ReceiveCommand.Type.DELETE && ref != null) {
				ObjectId id = ref.getObjectId();
				if (id == null) {
					id = ObjectId.zeroId();
				}
				if (!ObjectId.zeroId().equals(cmd.getOldId())
						&& !id.equals(cmd.getOldId())) {
					// Delete commands can be sent with the old id matching our
					// advertised value, *OR* with the old id being 0{40}. Any
					// other requested old id is invalid.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON,
							JGitText.get().invalidOldIdSent);
					continue;
				}
			}

			if (cmd.getType() == ReceiveCommand.Type.UPDATE) {
				if (ref == null) {
					// The ref must have been advertised in order to be updated.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON, JGitText.get().noSuchRef);
					continue;
				}
				ObjectId id = ref.getObjectId();
				if (id == null) {
					// We cannot update unborn branch
					cmd.setResult(Result.REJECTED_OTHER_REASON,
							JGitText.get().cannotUpdateUnbornBranch);
					continue;
				}

				if (!id.equals(cmd.getOldId())) {
					// A properly functioning client will send the same
					// object id we advertised.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON,
							JGitText.get().invalidOldIdSent);
					continue;
				}

				// Is this possibly a non-fast-forward style update?
				//
				RevObject oldObj, newObj;
				try {
					oldObj = walk.parseAny(cmd.getOldId());
				} catch (IOException e) {
					cmd.setResult(Result.REJECTED_MISSING_OBJECT, cmd
							.getOldId().name());
					continue;
				}

				try {
					newObj = walk.parseAny(cmd.getNewId());
				} catch (IOException e) {
					cmd.setResult(Result.REJECTED_MISSING_OBJECT, cmd
							.getNewId().name());
					continue;
				}

				if (oldObj instanceof RevCommit && newObj instanceof RevCommit) {
					try {
						if (walk.isMergedInto((RevCommit) oldObj,
								(RevCommit) newObj))
							cmd.setTypeFastForwardUpdate();
						else
							cmd.setType(ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
					} catch (MissingObjectException e) {
						cmd.setResult(Result.REJECTED_MISSING_OBJECT, e
								.getMessage());
					} catch (IOException e) {
						cmd.setResult(Result.REJECTED_OTHER_REASON);
					}
				} else {
					cmd.setType(ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
				}

				if (cmd.getType() == ReceiveCommand.Type.UPDATE_NONFASTFORWARD
						&& !isAllowNonFastForwards()) {
					cmd.setResult(Result.REJECTED_NONFASTFORWARD);
					continue;
				}
			}

			if (!cmd.getRefName().startsWith(Constants.R_REFS)
					|| !Repository.isValidRefName(cmd.getRefName())) {
				cmd.setResult(Result.REJECTED_OTHER_REASON, JGitText.get().funnyRefname);
			}
		}
	}

	/**
	 * @return if any commands have been rejected so far.
	 * @since 3.6
	 */
	protected boolean anyRejects() {
		for (ReceiveCommand cmd : commands) {
			if (cmd.getResult() != Result.NOT_ATTEMPTED && cmd.getResult() != Result.OK)
				return true;
		}
		return false;
	}

	/**
	 * Set the result to fail for any command that was not processed yet.
	 * @since 3.6
	 */
	protected void failPendingCommands() {
		ReceiveCommand.abort(commands);
	}

	/**
	 * Filter the list of commands according to result.
	 *
	 * @param want
	 *            desired status to filter by.
	 * @return a copy of the command list containing only those commands with the
	 *         desired status.
	 */
	protected List<ReceiveCommand> filterCommands(final Result want) {
		return ReceiveCommand.filter(commands, want);
	}

	/** Execute commands to update references. */
	protected void executeCommands() {
		List<ReceiveCommand> toApply = filterCommands(Result.NOT_ATTEMPTED);
		if (toApply.isEmpty())
			return;

		ProgressMonitor updating = NullProgressMonitor.INSTANCE;
		if (sideBand) {
			SideBandProgressMonitor pm = new SideBandProgressMonitor(msgOut);
			pm.setDelayStart(250, TimeUnit.MILLISECONDS);
			updating = pm;
		}

		BatchRefUpdate batch = db.getRefDatabase().newBatchUpdate();
		batch.setAllowNonFastForwards(isAllowNonFastForwards());
		batch.setAtomic(isAtomic());
		batch.setRefLogIdent(getRefLogIdent());
		batch.setRefLogMessage("push", true); //$NON-NLS-1$
		batch.addCommand(toApply);
		try {
			batch.setPushCertificate(getPushCertificate());
			batch.execute(walk, updating);
		} catch (IOException err) {
			for (ReceiveCommand cmd : toApply) {
				if (cmd.getResult() == Result.NOT_ATTEMPTED)
					cmd.reject(err);
			}
		}
	}

	/**
	 * Send a status report.
	 *
	 * @param forClient
	 *            true if this report is for a Git client, false if it is for an
	 *            end-user.
	 * @param unpackError
	 *            an error that occurred during unpacking, or {@code null}
	 * @param out
	 *            the reporter for sending the status strings.
	 * @throws IOException
	 *             an error occurred writing the status report.
	 */
	protected void sendStatusReport(final boolean forClient,
			final Throwable unpackError, final Reporter out) throws IOException {
		if (unpackError != null) {
			out.sendString("unpack error " + unpackError.getMessage()); //$NON-NLS-1$
			if (forClient) {
				for (final ReceiveCommand cmd : commands) {
					out.sendString("ng " + cmd.getRefName() //$NON-NLS-1$
							+ " n/a (unpacker error)"); //$NON-NLS-1$
				}
			}
			return;
		}

		if (forClient)
			out.sendString("unpack ok"); //$NON-NLS-1$
		for (final ReceiveCommand cmd : commands) {
			if (cmd.getResult() == Result.OK) {
				if (forClient)
					out.sendString("ok " + cmd.getRefName()); //$NON-NLS-1$
				continue;
			}

			final StringBuilder r = new StringBuilder();
			if (forClient)
				r.append("ng ").append(cmd.getRefName()).append(" "); //$NON-NLS-1$ //$NON-NLS-2$
			else
				r.append(" ! [rejected] ").append(cmd.getRefName()).append(" ("); //$NON-NLS-1$ //$NON-NLS-2$

			switch (cmd.getResult()) {
			case NOT_ATTEMPTED:
				r.append("server bug; ref not processed"); //$NON-NLS-1$
				break;

			case REJECTED_NOCREATE:
				r.append("creation prohibited"); //$NON-NLS-1$
				break;

			case REJECTED_NODELETE:
				r.append("deletion prohibited"); //$NON-NLS-1$
				break;

			case REJECTED_NONFASTFORWARD:
				r.append("non-fast forward"); //$NON-NLS-1$
				break;

			case REJECTED_CURRENT_BRANCH:
				r.append("branch is currently checked out"); //$NON-NLS-1$
				break;

			case REJECTED_MISSING_OBJECT:
				if (cmd.getMessage() == null)
					r.append("missing object(s)"); //$NON-NLS-1$
				else if (cmd.getMessage().length() == Constants.OBJECT_ID_STRING_LENGTH) {
					r.append("object "); //$NON-NLS-1$
					r.append(cmd.getMessage());
					r.append(" missing"); //$NON-NLS-1$
				} else
					r.append(cmd.getMessage());
				break;

			case REJECTED_OTHER_REASON:
				if (cmd.getMessage() == null)
					r.append("unspecified reason"); //$NON-NLS-1$
				else
					r.append(cmd.getMessage());
				break;

			case LOCK_FAILURE:
				r.append("failed to lock"); //$NON-NLS-1$
				break;

			case OK:
				// We shouldn't have reached this case (see 'ok' case above).
				continue;
			}
			if (!forClient)
				r.append(")"); //$NON-NLS-1$
			out.sendString(r.toString());
		}
	}

	/**
	 * Close and flush (if necessary) the underlying streams.
	 *
	 * @throws IOException
	 */
	protected void close() throws IOException {
		if (sideBand) {
			// If we are using side band, we need to send a final
			// flush-pkt to tell the remote peer the side band is
			// complete and it should stop decoding. We need to
			// use the original output stream as rawOut is now the
			// side band data channel.
			//
			((SideBandOutputStream) msgOut).flushBuffer();
			((SideBandOutputStream) rawOut).flushBuffer();

			PacketLineOut plo = new PacketLineOut(origOut);
			plo.setFlushOnEnd(false);
			plo.end();
		}

		if (biDirectionalPipe) {
			// If this was a native git connection, flush the pipe for
			// the caller. For smart HTTP we don't do this flush and
			// instead let the higher level HTTP servlet code do it.
			//
			if (!sideBand && msgOut != null)
				msgOut.flush();
			rawOut.flush();
		}
	}

	/**
	 * Release any resources used by this object.
	 *
	 * @throws IOException
	 *             the pack could not be unlocked.
	 */
	protected void release() throws IOException {
		walk.close();
		unlockPack();
		timeoutIn = null;
		rawIn = null;
		rawOut = null;
		msgOut = null;
		pckIn = null;
		pckOut = null;
		refs = null;
		// Keep the capabilities. If responses are sent after this release
		// we need to remember at least whether sideband communication has to be
		// used
		commands = null;
		if (timer != null) {
			try {
				timer.terminate();
			} finally {
				timer = null;
			}
		}
	}

	/** Interface for reporting status messages. */
	static abstract class Reporter {
			abstract void sendString(String s) throws IOException;
	}
}
