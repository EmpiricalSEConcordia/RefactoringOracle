/***********************************************************************************************************************
 * Copyright (C) 2010-2013 by the Apache Flink project (http://flink.incubator.apache.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 **********************************************************************************************************************/

package eu.stratosphere.pact.runtime.task.chaining;

import org.apache.flink.api.common.functions.Function;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.util.Collector;

import eu.stratosphere.nephele.execution.Environment;
import eu.stratosphere.nephele.template.AbstractInvokable;
import eu.stratosphere.pact.runtime.task.RegularPactTask;
import eu.stratosphere.pact.runtime.task.util.TaskConfig;
import eu.stratosphere.pact.runtime.udf.RuntimeUDFContext;

/**
 * The interface to be implemented by drivers that do not run in an own pact task context, but are chained to other
 * tasks.
 */
public abstract class ChainedDriver<IT, OT> implements Collector<IT> {

	protected TaskConfig config;

	protected String taskName;

	protected Collector<OT> outputCollector;
	
	protected ClassLoader userCodeClassLoader;
	
	private RuntimeUDFContext udfContext;

	
	public void setup(TaskConfig config, String taskName, Collector<OT> outputCollector,
			AbstractInvokable parent, ClassLoader userCodeClassLoader)
	{
		this.config = config;
		this.taskName = taskName;
		this.outputCollector = outputCollector;
		this.userCodeClassLoader = userCodeClassLoader;
		
		if (parent instanceof RegularPactTask) {
			this.udfContext = ((RegularPactTask<?, ?>) parent).createRuntimeContext(taskName);
		} else {
			Environment env = parent.getEnvironment();
			this.udfContext = new RuntimeUDFContext(taskName, env.getCurrentNumberOfSubtasks(), env.getIndexInSubtaskGroup(), env.getCopyTask());
		}

		setup(parent);
	}

	public abstract void setup(AbstractInvokable parent);

	public abstract void openTask() throws Exception;

	public abstract void closeTask() throws Exception;

	public abstract void cancelTask();

	public abstract Function getStub();

	public abstract String getTaskName();

	@Override
	public abstract void collect(IT record);

	
	protected RuntimeContext getUdfRuntimeContext() {
		return this.udfContext;
	}

	@SuppressWarnings("unchecked")
	public void setOutputCollector(Collector<?> outputCollector) {
		this.outputCollector = (Collector<OT>) outputCollector;
	}

	public Collector<OT> getOutputCollector() {
		return outputCollector;
	}
	
	public TaskConfig getTaskConfig() {
		return this.config;
	}
}
