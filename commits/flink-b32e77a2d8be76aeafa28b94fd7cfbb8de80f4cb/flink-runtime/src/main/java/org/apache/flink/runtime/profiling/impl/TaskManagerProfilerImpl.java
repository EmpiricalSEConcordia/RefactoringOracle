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

package org.apache.flink.runtime.profiling.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.GlobalConfiguration;
import org.apache.flink.runtime.execution.Environment;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.instance.InstanceConnectionInfo;
import org.apache.flink.runtime.ipc.RPC;
import org.apache.flink.runtime.net.NetUtils;
import org.apache.flink.runtime.profiling.ProfilingException;
import org.apache.flink.runtime.profiling.ProfilingUtils;
import org.apache.flink.runtime.profiling.TaskManagerProfiler;
import org.apache.flink.runtime.profiling.impl.types.InternalExecutionVertexThreadProfilingData;
import org.apache.flink.runtime.profiling.impl.types.InternalInstanceProfilingData;
import org.apache.flink.runtime.profiling.impl.types.ProfilingDataContainer;
import org.apache.flink.runtime.taskmanager.Task;
import org.apache.flink.util.StringUtils;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class TaskManagerProfilerImpl extends TimerTask implements TaskManagerProfiler {

	private static final Logger LOG = LoggerFactory.getLogger(TaskManagerProfilerImpl.class);

	private final ProfilerImplProtocol jobManagerProfiler;

	private final Timer timer;

	private final ThreadMXBean tmx;

	private final long timerInterval;

	private final ProfilingDataContainer profilingDataContainer = new ProfilingDataContainer();

	private final InstanceProfiler instanceProfiler;

	private final Map<Environment, EnvironmentThreadSet> monitoredThreads = new HashMap<Environment, EnvironmentThreadSet>();

	public TaskManagerProfilerImpl(InetAddress jobManagerAddress, InstanceConnectionInfo instanceConnectionInfo)
			throws ProfilingException {

		// Create RPC stub for communication with job manager's profiling component.
		final InetSocketAddress profilingAddress = new InetSocketAddress(jobManagerAddress, GlobalConfiguration
			.getInteger(ProfilingUtils.JOBMANAGER_RPC_PORT_KEY, ProfilingUtils.JOBMANAGER_DEFAULT_RPC_PORT));
		ProfilerImplProtocol jobManagerProfilerTmp = null;
		try {
			jobManagerProfilerTmp = (ProfilerImplProtocol) RPC.getProxy(ProfilerImplProtocol.class, profilingAddress,
				NetUtils.getSocketFactory());
		} catch (IOException e) {
			throw new ProfilingException(StringUtils.stringifyException(e));
		}
		this.jobManagerProfiler = jobManagerProfilerTmp;

		// Initialize MX interface and check if thread contention monitoring is supported
		this.tmx = ManagementFactory.getThreadMXBean();
		if (this.tmx.isThreadContentionMonitoringSupported()) {
			this.tmx.setThreadContentionMonitoringEnabled(true);
		} else {
			throw new ProfilingException("The thread contention monitoring is not supported.");
		}

		// Create instance profiler
		this.instanceProfiler = new InstanceProfiler(instanceConnectionInfo);

		// Set and trigger timer
		this.timerInterval = (long) (GlobalConfiguration.getInteger(ProfilingUtils.TASKMANAGER_REPORTINTERVAL_KEY,
			ProfilingUtils.DEFAULT_TASKMANAGER_REPORTINTERVAL) * 1000);
		// The initial delay is based on a random value, so the task managers will not send data to the job manager all
		// at once.
		final long initialDelay = (long) (Math.random() * this.timerInterval);
		this.timer = new Timer(true);
		this.timer.schedule(this, initialDelay, this.timerInterval);
	}


	@Override
	public void registerExecutionListener(final Task task, final Configuration jobConfiguration) {

		// Register profiling hook for the environment
		task.registerExecutionListener(new EnvironmentListenerImpl(this, task.getEnvironment()));
	}

	@Override
	public void unregisterExecutionListener(ExecutionAttemptID id) {
		/*
		 * Nothing to do here, the task will unregister itself when its
		 * execution state has either switched to FINISHED, CANCELLED,
		 * or FAILED.
		 */
	}

	@Override
	public void shutdown() {
		// Stop the timer task
		this.timer.cancel();
	}

	@Override
	public void run() {

		final long timestamp = System.currentTimeMillis();
		InternalInstanceProfilingData instanceProfilingData = null;

		// Collect profiling information of the threads
		synchronized (this.monitoredThreads) {

			final Iterator<Environment> iterator = this.monitoredThreads.keySet().iterator();
			while (iterator.hasNext()) {
				final Environment environment = iterator.next();
				final EnvironmentThreadSet environmentThreadSet = this.monitoredThreads.get(environment);
				final InternalExecutionVertexThreadProfilingData threadProfilingData = environmentThreadSet
					.captureCPUUtilization(environment.getJobID(), this.tmx, timestamp);
				if (threadProfilingData != null) {
					this.profilingDataContainer.addProfilingData(threadProfilingData);
				}
			}

			// If there is at least one registered environment, also create an instance profiling object
			if (!this.monitoredThreads.isEmpty()) {
				try {
					instanceProfilingData = this.instanceProfiler.generateProfilingData(timestamp);
				} catch (ProfilingException e) {
					LOG.error("Error while retrieving instance profiling data: ", e);
				}
			}
		}

		// Send all queued profiling records to the job manager and clear container
		synchronized (this.profilingDataContainer) {

			if (instanceProfilingData != null) {
				this.profilingDataContainer.addProfilingData(instanceProfilingData);
			}

			if (!this.profilingDataContainer.isEmpty()) {
				try {
					this.jobManagerProfiler.reportProfilingData(this.profilingDataContainer);
					this.profilingDataContainer.clear();
				} catch (IOException e) {
					LOG.error("Could not report profiling data.", e);
				}
			}
		}
	}

	public void registerMainThreadForCPUProfiling(Environment environment, Thread thread, ExecutionAttemptID executionID) {

		synchronized (this.monitoredThreads) {
			LOG.debug("Registering thread " + thread.getName() + " for CPU monitoring");
			if (this.monitoredThreads.containsKey(environment)) {
				LOG.error("There is already a main thread registered for environment object "
					+ environment.getTaskName());
			}

			this.monitoredThreads.put(environment, new EnvironmentThreadSet(this.tmx, thread, executionID));
		}
	}

	public void registerUserThreadForCPUProfiling(Environment environment, Thread userThread) {

		synchronized (this.monitoredThreads) {

			final EnvironmentThreadSet environmentThreadList = this.monitoredThreads.get(environment);
			if (environmentThreadList == null) {
				LOG.error("Trying to register " + userThread.getName() + " but no main thread found!");
				return;
			}

			environmentThreadList.addUserThread(this.tmx, userThread);
		}

	}

	public void unregisterMainThreadFromCPUProfiling(Environment environment, Thread thread) {

		synchronized (this.monitoredThreads) {
			LOG.debug("Unregistering thread " + thread.getName() + " from CPU monitoring");
			final EnvironmentThreadSet environmentThreadSet = this.monitoredThreads.remove(environment);
			if (environmentThreadSet != null) {

				if (environmentThreadSet.getMainThread() != thread) {
					LOG.error("The thread " + thread.getName() + " is not the main thread of this environment");
				}

				if (environmentThreadSet.getNumberOfUserThreads() > 0) {
					LOG.error("Thread " + environmentThreadSet.getMainThread().getName()
						+ " has still unfinished user threads!");
				}
			}
		}
	}

	public void unregisterUserThreadFromCPUProfiling(Environment environment, Thread userThread) {

		synchronized (this.monitoredThreads) {

			final EnvironmentThreadSet environmentThreadSet = this.monitoredThreads.get(environment);
			if (environmentThreadSet == null) {
				LOG.error("Trying to unregister " + userThread.getName() + " but no main thread found!");
				return;
			}

			environmentThreadSet.removeUserThread(userThread);
		}

	}
}
