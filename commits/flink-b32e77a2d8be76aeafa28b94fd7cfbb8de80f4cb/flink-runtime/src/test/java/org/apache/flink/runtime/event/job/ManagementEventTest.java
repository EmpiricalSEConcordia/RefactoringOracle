/**
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

package org.apache.flink.runtime.event.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.flink.runtime.event.job.ExecutionStateChangeEvent;
import org.apache.flink.runtime.event.job.RecentJobEvent;
import org.apache.flink.runtime.execution.ExecutionState2;
import org.apache.flink.runtime.jobgraph.JobID;
import org.apache.flink.runtime.jobgraph.JobStatus;
import org.apache.flink.runtime.managementgraph.ManagementVertexID;
import org.apache.flink.runtime.testutils.ManagementTestUtils;
import org.junit.Test;

/**
 * This test checks the proper serialization and deserialization of job events.
 */
public class ManagementEventTest {

	/**
	 * The time stamp used during the tests.
	 */
	private static final long TIMESTAMP = 123456789L;

	/**
	 * The name of the job used during the tests.
	 */
	private static final String JOBNAME = "Test Job Name";

	/**
	 * Tests serialization/deserialization for {@link ExecutionStateChangeEvent}.
	 */
	@Test
	public void testExecutionStateChangeEvent() {

		final ExecutionStateChangeEvent orig = new ExecutionStateChangeEvent(TIMESTAMP, new ManagementVertexID(),
			ExecutionState2.DEPLOYING);

		final ExecutionStateChangeEvent copy = (ExecutionStateChangeEvent) ManagementTestUtils.createCopy(orig);

		assertEquals(orig.getTimestamp(), copy.getTimestamp());
		assertEquals(orig.getVertexID(), copy.getVertexID());
		assertEquals(orig.getNewExecutionState(), copy.getNewExecutionState());
		assertEquals(orig.hashCode(), copy.hashCode());
		assertTrue(orig.equals(copy));
	}

	/**
	 * Tests serialization/deserialization for {@link RecentJobEvent}.
	 */
	@Test
	public void testRecentJobEvent() {

		final RecentJobEvent orig = new RecentJobEvent(new JobID(), JOBNAME, JobStatus.RUNNING, true, TIMESTAMP, TIMESTAMP);

		final RecentJobEvent copy = (RecentJobEvent) ManagementTestUtils.createCopy(orig);

		assertEquals(orig.getJobID(), copy.getJobID());
		assertEquals(orig.getJobName(), copy.getJobName());
		assertEquals(orig.getJobStatus(), copy.getJobStatus());
		assertEquals(orig.isProfilingAvailable(), copy.isProfilingAvailable());
		assertEquals(orig.getTimestamp(), copy.getTimestamp());
		assertEquals(orig.getSubmissionTimestamp(), copy.getSubmissionTimestamp());
		assertEquals(orig.hashCode(), copy.hashCode());
		assertTrue(orig.equals(copy));
	}
}
