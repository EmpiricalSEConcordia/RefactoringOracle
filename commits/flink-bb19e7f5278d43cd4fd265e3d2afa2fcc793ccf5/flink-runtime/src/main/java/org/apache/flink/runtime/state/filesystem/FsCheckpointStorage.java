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

package org.apache.flink.runtime.state.filesystem;

import org.apache.flink.api.common.JobID;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.Path;
import org.apache.flink.runtime.state.CheckpointStorageLocation;
import org.apache.flink.runtime.state.CheckpointStorageLocationReference;

import javax.annotation.Nullable;

import java.io.IOException;

import static org.apache.flink.util.Preconditions.checkArgument;

/**
 * An implementation of durable checkpoint storage to file systems.
 */
public class FsCheckpointStorage extends AbstractFsCheckpointStorage {

	private final FileSystem fileSystem;

	private final Path checkpointsDirectory;

	private final Path sharedStateDirectory;

	private final Path taskOwnedStateDirectory;

	public FsCheckpointStorage(
			Path checkpointBaseDirectory,
			@Nullable Path defaultSavepointDirectory,
			JobID jobId) throws IOException {

		super(jobId, defaultSavepointDirectory);

		this.fileSystem = checkpointBaseDirectory.getFileSystem();
		this.checkpointsDirectory = getCheckpointDirectoryForJob(checkpointBaseDirectory, jobId);
		this.sharedStateDirectory = new Path(checkpointsDirectory, CHECKPOINT_SHARED_STATE_DIR);
		this.taskOwnedStateDirectory = new Path(checkpointsDirectory, CHECKPOINT_TASK_OWNED_STATE_DIR);

		// initialize the dedicated directories
		fileSystem.mkdirs(checkpointsDirectory);
		fileSystem.mkdirs(sharedStateDirectory);
		fileSystem.mkdirs(taskOwnedStateDirectory);
	}

	// ------------------------------------------------------------------------
	//  CheckpointStorage implementation
	// ------------------------------------------------------------------------

	@Override
	public boolean supportsHighlyAvailableStorage() {
		return true;
	}

	@Override
	public CheckpointStorageLocation initializeLocationForCheckpoint(long checkpointId) throws IOException {
		checkArgument(checkpointId >= 0);

		// prepare all the paths needed for the checkpoints
		final Path checkpointDir = createCheckpointDirectory(checkpointsDirectory, checkpointId);

		// create the checkpoint exclusive directory
		fileSystem.mkdirs(checkpointDir);

		return new FsCheckpointStorageLocation(
						fileSystem, checkpointDir, sharedStateDirectory, taskOwnedStateDirectory,
						CheckpointStorageLocationReference.getDefault());
	}
}
