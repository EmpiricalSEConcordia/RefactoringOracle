/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.runtime.io;

import org.apache.flink.annotation.Internal;
import org.apache.flink.runtime.checkpoint.CheckpointMetaData;
import org.apache.flink.runtime.io.disk.iomanager.IOManager;
import org.apache.flink.runtime.io.network.api.CancelCheckpointMarker;
import org.apache.flink.runtime.io.network.api.CheckpointBarrier;
import org.apache.flink.runtime.io.network.api.EndOfPartitionEvent;
import org.apache.flink.runtime.io.network.partition.consumer.BufferOrEvent;
import org.apache.flink.runtime.io.network.partition.consumer.InputGate;
import org.apache.flink.runtime.jobgraph.tasks.StatefulTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;

/**
 * The barrier buffer is {@link CheckpointBarrierHandler} that blocks inputs with barriers until
 * all inputs have received the barrier for a given checkpoint.
 * 
 * <p>To avoid back-pressuring the input streams (which may cause distributed deadlocks), the
 * BarrierBuffer continues receiving buffers from the blocked channels and stores them internally until 
 * the blocks are released.
 */
@Internal
public class BarrierBuffer implements CheckpointBarrierHandler {

	private static final Logger LOG = LoggerFactory.getLogger(BarrierBuffer.class);

	/** The gate that the buffer draws its input from */
	private final InputGate inputGate;

	/** Flags that indicate whether a channel is currently blocked/buffered */
	private final boolean[] blockedChannels;

	/** The total number of channels that this buffer handles data from */
	private final int totalNumberOfInputChannels;

	/** To utility to write blocked data to a file channel */
	private final BufferSpiller bufferSpiller;

	/** The pending blocked buffer/event sequences. Must be consumed before requesting
	 * further data from the input gate. */
	private final ArrayDeque<BufferSpiller.SpilledBufferOrEventSequence> queuedBuffered;

	/** The sequence of buffers/events that has been unblocked and must now be consumed
	 * before requesting further data from the input gate */
	private BufferSpiller.SpilledBufferOrEventSequence currentBuffered;

	/** Handler that receives the checkpoint notifications */
	private StatefulTask toNotifyOnCheckpoint;

	/** The ID of the checkpoint for which we expect barriers */
	private long currentCheckpointId = -1L;

	/** The number of received barriers (= number of blocked/buffered channels)
	 * IMPORTANT: A canceled checkpoint must always have 0 barriers */
	private int numBarriersReceived;

	/** The number of already closed channels */
	private int numClosedChannels;

	/** The timestamp as in {@link System#nanoTime()} at which the last alignment started */
	private long startOfAlignmentTimestamp;

	/** The time (in nanoseconds) that the latest alignment took */
	private long latestAlignmentDurationNanos;

	/** Flag to indicate whether we have drawn all available input */
	private boolean endOfStream;

	/**
	 * 
	 * @param inputGate The input gate to draw the buffers and events from.
	 * @param ioManager The I/O manager that gives access to the temp directories.
	 * 
	 * @throws IOException Thrown, when the spilling to temp files cannot be initialized.
	 */
	public BarrierBuffer(InputGate inputGate, IOManager ioManager) throws IOException {
		this.inputGate = inputGate;
		this.totalNumberOfInputChannels = inputGate.getNumberOfInputChannels();
		this.blockedChannels = new boolean[this.totalNumberOfInputChannels];

		this.bufferSpiller = new BufferSpiller(ioManager, inputGate.getPageSize());
		this.queuedBuffered = new ArrayDeque<BufferSpiller.SpilledBufferOrEventSequence>();
	}

	// ------------------------------------------------------------------------
	//  Buffer and barrier handling
	// ------------------------------------------------------------------------

	@Override
	public BufferOrEvent getNextNonBlocked() throws Exception {
		while (true) {
			// process buffered BufferOrEvents before grabbing new ones
			BufferOrEvent next;
			if (currentBuffered == null) {
				next = inputGate.getNextBufferOrEvent();
			}
			else {
				next = currentBuffered.getNext();
				if (next == null) {
					completeBufferedSequence();
					return getNextNonBlocked();
				}
			}

			if (next != null) {
				if (isBlocked(next.getChannelIndex())) {
					// if the channel is blocked we, we just store the BufferOrEvent
					bufferSpiller.add(next);
				}
				else if (next.isBuffer()) {
					return next;
				}
				else if (next.getEvent().getClass() == CheckpointBarrier.class) {
					if (!endOfStream) {
						// process barriers only if there is a chance of the checkpoint completing
						processBarrier((CheckpointBarrier) next.getEvent(), next.getChannelIndex());
					}
				}
				else if (next.getEvent().getClass() == CancelCheckpointMarker.class) {
					processCancellationBarrier((CancelCheckpointMarker) next.getEvent());
				}
				else {
					if (next.getEvent().getClass() == EndOfPartitionEvent.class) {
						processEndOfPartition(next.getChannelIndex());
					}
					return next;
				}
			}
			else if (!endOfStream) {
				// end of input stream. stream continues with the buffered data
				endOfStream = true;
				releaseBlocksAndResetBarriers();
				return getNextNonBlocked();
			}
			else {
				// final end of both input and buffered data
				return null;
			}
		}
	}

	private void completeBufferedSequence() throws IOException {
		currentBuffered.cleanup();
		currentBuffered = queuedBuffered.pollFirst();
		if (currentBuffered != null) {
			currentBuffered.open();
		}
	}

	private void processBarrier(CheckpointBarrier receivedBarrier, int channelIndex) throws Exception {
		final long barrierId = receivedBarrier.getId();

		// fast path for single channel cases
		if (totalNumberOfInputChannels == 1) {
			if (barrierId > currentCheckpointId) {
				// new checkpoint
				currentCheckpointId = barrierId;
				notifyCheckpoint(receivedBarrier);
			}
			return;
		}

		// -- general code path for multiple input channels --

		if (numBarriersReceived > 0) {
			// this is only true if some alignment is already progress and was not canceled

			if (barrierId == currentCheckpointId) {
				// regular case
				onBarrier(channelIndex);
			}
			else if (barrierId > currentCheckpointId) {
				// we did not complete the current checkpoint, another started before
				LOG.warn("Received checkpoint barrier for checkpoint {} before completing current checkpoint {}. " +
						"Skipping current checkpoint.", barrierId, currentCheckpointId);

				// let the task know we are not completing this
				notifyAbort(currentCheckpointId);

				// abort the current checkpoint
				releaseBlocksAndResetBarriers();

				// begin a the new checkpoint
				beginNewAlignment(barrierId, channelIndex);
			}
			else {
				// ignore trailing barrier from an earlier checkpoint (obsolete now)
				return;
			}
		}
		else if (barrierId > currentCheckpointId) {
			// first barrier of a new checkpoint
			beginNewAlignment(barrierId, channelIndex);
		}
		else {
			// either the current checkpoint was canceled (numBarriers == 0) or
			// this barrier is from an old subsumed checkpoint
			return;
		}

		// check if we have all barriers - since canceled checkpoints always have zero barriers
		// this can only happen on a non canceled checkpoint
		if (numBarriersReceived + numClosedChannels == totalNumberOfInputChannels) {
			// actually trigger checkpoint
			if (LOG.isDebugEnabled()) {
				LOG.debug("Received all barriers, triggering checkpoint {} at {}",
						receivedBarrier.getId(), receivedBarrier.getTimestamp());
			}

			releaseBlocksAndResetBarriers();
			notifyCheckpoint(receivedBarrier);
		}
	}

	private void processCancellationBarrier(CancelCheckpointMarker cancelBarrier) throws Exception {
		final long barrierId = cancelBarrier.getCheckpointId();

		// fast path for single channel cases
		if (totalNumberOfInputChannels == 1) {
			if (barrierId > currentCheckpointId) {
				// new checkpoint
				currentCheckpointId = barrierId;
				notifyAbort(barrierId);
			}
			return;
		}

		// -- general code path for multiple input channels --

		if (numBarriersReceived > 0) {
			// this is only true if some alignment is in progress and nothing was canceled

			if (barrierId == currentCheckpointId) {
				// cancel this alignment
				if (LOG.isDebugEnabled()) {
					LOG.debug("Checkpoint {} canceled, aborting alignment", barrierId);
				}

				releaseBlocksAndResetBarriers();
				notifyAbort(barrierId);
			}
			else if (barrierId > currentCheckpointId) {
				// we canceled the next which also cancels the current
				LOG.warn("Received cancellation barrier for checkpoint {} before completing current checkpoint {}. " +
						"Skipping current checkpoint.", barrierId, currentCheckpointId);

				// this stops the current alignment
				releaseBlocksAndResetBarriers();

				// the next checkpoint starts as canceled
				currentCheckpointId = barrierId;
				startOfAlignmentTimestamp = 0L;
				latestAlignmentDurationNanos = 0L;
				notifyAbort(barrierId);
			}

			// else: ignore trailing (cancellation) barrier from an earlier checkpoint (obsolete now)

		}
		else if (barrierId > currentCheckpointId) {
			// first barrier of a new checkpoint is directly a cancellation

			// by setting the currentCheckpointId to this checkpoint while keeping the numBarriers
			// at zero means that no checkpoint barrier can start a new alignment
			currentCheckpointId = barrierId;

			startOfAlignmentTimestamp = 0L;
			latestAlignmentDurationNanos = 0L;

			if (LOG.isDebugEnabled()) {
				LOG.debug("Checkpoint {} canceled, skipping alignment", barrierId);
			}

			notifyAbort(barrierId);
		}

		// else: trailing barrier from either
		//   - a previous (subsumed) checkpoint
		//   - the current checkpoint if it was already canceled
	}

	private void processEndOfPartition(int channel) throws Exception {
		numClosedChannels++;

		if (numBarriersReceived > 0) {
			// let the task know we skip a checkpoint
			notifyAbort(currentCheckpointId);

			// no chance to complete this checkpoint
			releaseBlocksAndResetBarriers();
		}
	}

	private void notifyCheckpoint(CheckpointBarrier checkpointBarrier) throws Exception {
		if (toNotifyOnCheckpoint != null) {
			CheckpointMetaData checkpointMetaData =
					new CheckpointMetaData(checkpointBarrier.getId(), checkpointBarrier.getTimestamp());

			checkpointMetaData
					.setBytesBufferedInAlignment(bufferSpiller.getBytesWritten())
					.setAlignmentDurationNanos(latestAlignmentDurationNanos);

			toNotifyOnCheckpoint.triggerCheckpointOnBarrier(checkpointMetaData);
		}
	}

	private void notifyAbort(long checkpointId) throws Exception {
		if (toNotifyOnCheckpoint != null) {
			toNotifyOnCheckpoint.abortCheckpointOnBarrier(checkpointId);
		}
	}


	@Override
	public void registerCheckpointEventHandler(StatefulTask toNotifyOnCheckpoint) {
		if (this.toNotifyOnCheckpoint == null) {
			this.toNotifyOnCheckpoint = toNotifyOnCheckpoint;
		}
		else {
			throw new IllegalStateException("BarrierBuffer already has a registered checkpoint notifyee");
		}
	}

	@Override
	public boolean isEmpty() {
		return currentBuffered == null;
	}

	@Override
	public void cleanup() throws IOException {
		bufferSpiller.close();
		if (currentBuffered != null) {
			currentBuffered.cleanup();
		}
		for (BufferSpiller.SpilledBufferOrEventSequence seq : queuedBuffered) {
			seq.cleanup();
		}
		queuedBuffered.clear();
	}

	private void beginNewAlignment(long checkpointId, int channelIndex) throws IOException {
		currentCheckpointId = checkpointId;
		onBarrier(channelIndex);

		startOfAlignmentTimestamp = System.nanoTime();

		if (LOG.isDebugEnabled()) {
			LOG.debug("Starting stream alignment for checkpoint " + checkpointId);
		}
	}

	/**
	 * Checks whether the channel with the given index is blocked.
	 * 
	 * @param channelIndex The channel index to check.
	 * @return True if the channel is blocked, false if not.
	 */
	private boolean isBlocked(int channelIndex) {
		return blockedChannels[channelIndex];
	}

	/**
	 * Blocks the given channel index, from which a barrier has been received.
	 * 
	 * @param channelIndex The channel index to block.
	 */
	private void onBarrier(int channelIndex) throws IOException {
		if (!blockedChannels[channelIndex]) {
			blockedChannels[channelIndex] = true;

			numBarriersReceived++;

			if (LOG.isDebugEnabled()) {
				LOG.debug("Received barrier from channel " + channelIndex);
			}
		}
		else {
			throw new IOException("Stream corrupt: Repeated barrier for same checkpoint on input " + channelIndex);
		}
	}

	/**
	 * Releases the blocks on all channels and resets the barrier count.
	 * Makes sure the just written data is the next to be consumed.
	 */
	private void releaseBlocksAndResetBarriers() throws IOException {
		LOG.debug("End of stream alignment, feeding buffered data back");

		for (int i = 0; i < blockedChannels.length; i++) {
			blockedChannels[i] = false;
		}

		if (currentBuffered == null) {
			// common case: no more buffered data
			currentBuffered = bufferSpiller.rollOver();
			if (currentBuffered != null) {
				currentBuffered.open();
			}
		}
		else {
			// uncommon case: buffered data pending
			// push back the pending data, if we have any
			
			// since we did not fully drain the previous sequence, we need to allocate a new buffer for this one
			BufferSpiller.SpilledBufferOrEventSequence bufferedNow = bufferSpiller.rollOverWithNewBuffer();
			if (bufferedNow != null) {
				bufferedNow.open();
				queuedBuffered.addFirst(currentBuffered);
				currentBuffered = bufferedNow;
			}
		}

		// the next barrier that comes must assume it is the first
		numBarriersReceived = 0;

		if (startOfAlignmentTimestamp > 0) {
			latestAlignmentDurationNanos = System.nanoTime() - startOfAlignmentTimestamp;
			startOfAlignmentTimestamp = 0;
		}
	}

	// ------------------------------------------------------------------------
	//  Properties
	// ------------------------------------------------------------------------

	/**
	 * Gets the ID defining the current pending, or just completed, checkpoint.
	 * 
	 * @return The ID of the pending of completed checkpoint. 
	 */
	public long getCurrentCheckpointId() {
		return this.currentCheckpointId;
	}

	@Override
	public long getAlignmentDurationNanos() {
		long start = this.startOfAlignmentTimestamp;
		if (start <= 0) {
			return latestAlignmentDurationNanos;
		} else {
			return System.nanoTime() - start;
		}
	}

	// ------------------------------------------------------------------------
	// Utilities 
	// ------------------------------------------------------------------------
	
	@Override
	public String toString() {
		return String.format("last checkpoint: %d, current barriers: %d, closed channels: %d",
				currentCheckpointId, numBarriersReceived, numClosedChannels);
	}
}
