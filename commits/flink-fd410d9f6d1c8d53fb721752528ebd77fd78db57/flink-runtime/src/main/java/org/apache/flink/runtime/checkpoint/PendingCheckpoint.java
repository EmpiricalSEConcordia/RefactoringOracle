/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.checkpoint;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.checkpoint.savepoint.Savepoint;
import org.apache.flink.runtime.checkpoint.savepoint.SavepointStore;
import org.apache.flink.runtime.checkpoint.savepoint.SavepointV1;
import org.apache.flink.runtime.concurrent.Future;
import org.apache.flink.runtime.concurrent.impl.FlinkCompletableFuture;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.executiongraph.ExecutionVertex;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.state.ChainedStateHandle;
import org.apache.flink.runtime.state.CheckpointStateHandles;
import org.apache.flink.runtime.state.KeyGroupsStateHandle;
import org.apache.flink.runtime.state.OperatorStateHandle;
import org.apache.flink.runtime.state.StateUtil;
import org.apache.flink.runtime.state.StreamStateHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * A pending checkpoint is a checkpoint that has been started, but has not been
 * acknowledged by all tasks that need to acknowledge it. Once all tasks have
 * acknowledged it, it becomes a {@link CompletedCheckpoint}.
 * 
 * <p>Note that the pending checkpoint, as well as the successful checkpoint keep the
 * state handles always as serialized values, never as actual values.
 */
public class PendingCheckpoint {

	private static final Logger LOG = LoggerFactory.getLogger(CheckpointCoordinator.class);

	private final Object lock = new Object();

	private final JobID jobId;

	private final long checkpointId;

	private final long checkpointTimestamp;

	private final Map<JobVertexID, TaskState> taskStates;

	private final Map<ExecutionAttemptID, ExecutionVertex> notYetAcknowledgedTasks;

	/**
	 * The checkpoint properties. If the checkpoint should be persisted
	 * externally, it happens in {@link #finalizeCheckpoint()}.
	 */
	private final CheckpointProperties props;

	/** Target directory to potentially persist checkpoint to; <code>null</code> if none configured. */
	private final String targetDirectory;

	/** The promise to fulfill once the checkpoint has been completed. */
	private final FlinkCompletableFuture<CompletedCheckpoint> onCompletionPromise = new FlinkCompletableFuture<>();

	private int numAcknowledgedTasks;

	private boolean discarded;

	// --------------------------------------------------------------------------------------------

	public PendingCheckpoint(
			JobID jobId,
			long checkpointId,
			long checkpointTimestamp,
			Map<ExecutionAttemptID, ExecutionVertex> verticesToConfirm,
			CheckpointProperties props,
			String targetDirectory) {
		this.jobId = checkNotNull(jobId);
		this.checkpointId = checkpointId;
		this.checkpointTimestamp = checkpointTimestamp;
		this.notYetAcknowledgedTasks = checkNotNull(verticesToConfirm);
		this.taskStates = new HashMap<>();
		this.props = checkNotNull(props);
		this.targetDirectory = targetDirectory;

		// Sanity check
		if (props.externalizeCheckpoint() && targetDirectory == null) {
			throw new NullPointerException("No target directory specified to persist checkpoint to.");
		}

		checkArgument(verticesToConfirm.size() > 0,
				"Checkpoint needs at least one vertex that commits the checkpoint");
	}

	// --------------------------------------------------------------------------------------------

	// ------------------------------------------------------------------------
	//  Properties
	// ------------------------------------------------------------------------

	public JobID getJobId() {
		return jobId;
	}

	public long getCheckpointId() {
		return checkpointId;
	}

	public long getCheckpointTimestamp() {
		return checkpointTimestamp;
	}

	public int getNumberOfNonAcknowledgedTasks() {
		return notYetAcknowledgedTasks.size();
	}

	public int getNumberOfAcknowledgedTasks() {
		return numAcknowledgedTasks;
	}

	public Map<JobVertexID, TaskState> getTaskStates() {
		return taskStates;
	}

	public boolean isFullyAcknowledged() {
		return this.notYetAcknowledgedTasks.isEmpty() && !discarded;
	}

	public boolean isDiscarded() {
		return discarded;
	}

	/**
	 * Checks whether this checkpoint can be subsumed or whether it should always continue, regardless
	 * of newer checkpoints in progress.
	 * 
	 * @return True if the checkpoint can be subsumed, false otherwise.
	 */
	public boolean canBeSubsumed() {
		// If the checkpoint is forced, it cannot be subsumed.
		return !props.forceCheckpoint();
	}

	CheckpointProperties getProps() {
		return props;
	}

	String getTargetDirectory() {
		return targetDirectory;
	}

	// ------------------------------------------------------------------------
	//  Progress and Completion
	// ------------------------------------------------------------------------

	/**
	 * Returns the completion future.
	 *
	 * @return A future to the completed checkpoint
	 */
	public Future<CompletedCheckpoint> getCompletionFuture() {
		return onCompletionPromise;
	}

	public CompletedCheckpoint finalizeCheckpoint() throws Exception {
		synchronized (lock) {
			try {
				if (discarded) {
					throw new IllegalStateException("pending checkpoint is discarded");
				}
				if (notYetAcknowledgedTasks.isEmpty()) {
					// Persist if required
					String externalPath = null;
					if (props.externalizeCheckpoint()) {
						try {
							Savepoint savepoint = new SavepointV1(checkpointId, taskStates.values());
							externalPath = SavepointStore.storeSavepoint(
									targetDirectory,
									savepoint);
						} catch (Throwable t) {
							LOG.error("Failed to persist checkpoints " + checkpointId + ".", t);
						}
					}

					CompletedCheckpoint completed = new CompletedCheckpoint(
							jobId,
							checkpointId,
							checkpointTimestamp,
							System.currentTimeMillis(),
							new HashMap<>(taskStates),
							props,
							externalPath);

					onCompletionPromise.complete(completed);

					dispose(false);

					return completed;
				} else {
					throw new IllegalStateException("Cannot complete checkpoint while not all tasks are acknowledged");
				}
			} catch (Throwable t) {
				onCompletionPromise.completeExceptionally(t);
				throw t;
			}
		}
	}
	
	public boolean acknowledgeTask(
			ExecutionAttemptID attemptID,
			CheckpointStateHandles checkpointStateHandles) {

		synchronized (lock) {
			if (discarded) {
				return false;
			}

			ExecutionVertex vertex = notYetAcknowledgedTasks.remove(attemptID);

			if (vertex != null) {
				if (checkpointStateHandles != null) {
					List<KeyGroupsStateHandle> keyGroupsState = checkpointStateHandles.getKeyGroupsStateHandle();
					ChainedStateHandle<StreamStateHandle> nonPartitionedState =
							checkpointStateHandles.getNonPartitionedStateHandles();
					ChainedStateHandle<OperatorStateHandle> partitioneableState =
							checkpointStateHandles.getPartitioneableStateHandles();

					if (nonPartitionedState != null || partitioneableState != null || keyGroupsState != null) {

						JobVertexID jobVertexID = vertex.getJobvertexId();

						int subtaskIndex = vertex.getParallelSubtaskIndex();

						TaskState taskState;

						if (taskStates.containsKey(jobVertexID)) {
							taskState = taskStates.get(jobVertexID);
						} else {
							//TODO this should go away when we remove chained state, assigning state to operators directly instead
							int chainLength;
							if (nonPartitionedState != null) {
								chainLength = nonPartitionedState.getLength();
							} else if (partitioneableState != null) {
								chainLength = partitioneableState.getLength();
							} else {
								chainLength = 1;
							}

							taskState = new TaskState(
								jobVertexID,
								vertex.getTotalNumberOfParallelSubtasks(),
								vertex.getMaxParallelism(),
								chainLength);

							taskStates.put(jobVertexID, taskState);
						}

						long duration = System.currentTimeMillis() - checkpointTimestamp;

						if (nonPartitionedState != null) {
							taskState.putState(
									subtaskIndex,
									new SubtaskState(nonPartitionedState, duration));
						}

						if(partitioneableState != null && !partitioneableState.isEmpty()) {
							taskState.putPartitionableState(subtaskIndex, partitioneableState);
						}

						// currently a checkpoint can only contain keyed state
						// for the head operator
						if (keyGroupsState != null && !keyGroupsState.isEmpty()) {
							KeyGroupsStateHandle keyGroupsStateHandle = keyGroupsState.get(0);
							taskState.putKeyedState(subtaskIndex, keyGroupsStateHandle);
						}
					}
				}

				++numAcknowledgedTasks;

				return true;
			} else {
				return false;
			}
		}
	}

	// ------------------------------------------------------------------------
	//  Cancellation
	// ------------------------------------------------------------------------

	/**
	 * Aborts a checkpoint because it expired (took too long).
	 */
	public void abortExpired() throws Exception {
		try {
			onCompletionPromise.completeExceptionally(new Exception("Checkpoint expired before completing"));
		} finally {
			dispose(true);
		}
	}

	/**
	 * Aborts the pending checkpoint because a newer completed checkpoint subsumed it.
	 */
	public void abortSubsumed() throws Exception {
		try {
			if (props.forceCheckpoint()) {
				onCompletionPromise.completeExceptionally(new Exception("Bug: forced checkpoints must never be subsumed"));

				throw new IllegalStateException("Bug: forced checkpoints must never be subsumed");
			} else {
				onCompletionPromise.completeExceptionally(new Exception("Checkpoints has been subsumed"));
			}
		} finally {
			dispose(true);
		}
	}

	public void abortDeclined() throws Exception {
		try {
			onCompletionPromise.completeExceptionally(new Exception("Checkpoint was declined (tasks not ready)"));
		} finally {
			dispose(true);
		}
	}

	/**
	 * Aborts the pending checkpoint due to an error.
	 * @param cause The error's exception.
	 */
	public void abortError(Throwable cause) throws Exception {
		try {
			onCompletionPromise.completeExceptionally(new Exception("Checkpoint failed: " + cause.getMessage(), cause));
		} finally {
			dispose(true);
		}
	}

	private void dispose(boolean releaseState) throws Exception {
		synchronized (lock) {
			try {
				discarded = true;
				numAcknowledgedTasks = -1;
				if (releaseState) {
					StateUtil.bestEffortDiscardAllStateObjects(taskStates.values());
				}
			} finally {
				taskStates.clear();
				notYetAcknowledgedTasks.clear();
			}
		}
	}

	// --------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		return String.format("Pending Checkpoint %d @ %d - confirmed=%d, pending=%d",
				checkpointId, checkpointTimestamp, getNumberOfAcknowledgedTasks(), getNumberOfNonAcknowledgedTasks());
	}
}
