/***********************************************************************************************************************
 * Copyright (C) 2010-2013 by the Stratosphere project (http://stratosphere.eu)
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

package eu.stratosphere.api;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

import eu.stratosphere.api.operators.Operator;
import eu.stratosphere.api.operators.GenericDataSink;
import eu.stratosphere.util.Visitable;
import eu.stratosphere.util.Visitor;


/**
 * This class encapsulates a single stratosphere job (an instantiated data flow), together with some parameters.
 * Parameters include the name and a default degree of parallelism. The job is referenced by the data sinks,
 * from which a traversal reaches all connected nodes of the job.
 */
public class Job implements Visitable<Operator> {
	
	/**
	 * A collection of all sinks in the plan. Since the plan is traversed from the sinks to the sources, this
	 * collection must contain all the sinks.
	 */
	protected final Collection<GenericDataSink> sinks;

	/**
	 * The name of the job.
	 */
	protected final String jobName;

	/**
	 * The default parallelism to use for nodes that have no explicitly specified parallelism.
	 */
	protected int defaultParallelism = -1;
	
	/**
	 * The maximal number of machines to use in the job.
	 */
	protected int maxNumberMachines;

	// ------------------------------------------------------------------------

	/**
	 * Creates a new Stratosphere job with the given name, describing the data flow that ends at the
	 * given data sinks.
	 * <p>
	 * If not all of the sinks of a data flow are given to the plan, the flow might
	 * not be translated entirely. 
	 *  
	 * @param sinks The collection will the sinks of the job's data flow.
	 * @param jobName The name to display for the job.
	 */
	public Job(Collection<GenericDataSink> sinks, String jobName) {
		this.sinks = sinks;
		this.jobName = jobName;
	}

	/**
	 * Creates a new Stratosphere job with the given name, containing initially a single data sink.
	 * <p>
	 * If not all of the sinks of a data flow are given, the flow might
	 * not be translated entirely, but only the parts of the flow reachable by traversing backwards
	 * from the given data sinks.
	 * 
	 * @param sink The data sink of the data flow.
	 * @param jobName The name to display for the job.
	 */
	public Job(GenericDataSink sink, String jobName) {
		this.sinks = new ArrayList<GenericDataSink>();
		this.sinks.add(sink);
		this.jobName = jobName;
	}

	/**
	 * Creates a new Stratosphere job, describing the data flow that ends at the
	 * given data sinks. The display name for the job is generated using a timestamp.
	 * <p>
	 * If not all of the sinks of a data flow are given, the flow might
	 * not be translated entirely, but only the parts of the flow reachable by traversing backwards
	 * from the given data sinks. 
	 *  
	 * @param sinks The collection will the sinks of the data flow.
	 */
	public Job(Collection<GenericDataSink> sinks) {
		this(sinks, "Stratosphere Job at " + Calendar.getInstance().getTime());
	}

	/**
	 * Creates a new Stratosphere Job with single data sink.
	 * The display name for the job is generated using a timestamp.
	 * <p>
	 * If not all of the sinks of a data flow are given to the plan, the flow might
	 * not be translated entirely. 
	 * 
	 * @param sink The data sink of the data flow.
	 */
	public Job(GenericDataSink sink) {
		this(sink, "Stratosphere Job at " + Calendar.getInstance().getTime());
	}

	// ------------------------------------------------------------------------

	/**
	 * Adds a data sink to the set of sinks in this program.
	 * 
	 * @param sink The data sink to add.
	 */
	public void addDataSink(GenericDataSink sink) {
		if (!this.sinks.contains(sink)) {
			this.sinks.add(sink);
		}
	}

	/**
	 * Gets all the data sinks of this job.
	 * 
	 * @return All sinks of the program.
	 */
	public Collection<GenericDataSink> getDataSinks() {
		return this.sinks;
	}

	/**
	 * Gets the name of this job.
	 * 
	 * @return The name of the job.
	 */
	public String getJobName() {
		return this.jobName;
	}

	/**
	 * Gets the maximum number of machines to be used for this job.
	 * 
	 * @return The maximum number of machines to be used for this job.
	 */
	public int getMaxNumberMachines() {
		return this.maxNumberMachines;
	}

	/**
	 * Sets the maximum number of machines to be used for this job.
	 * 
	 * @param maxNumberMachines The the maximum number to set.
	 */
	public void setMaxNumberMachines(int maxNumberMachines) {
		this.maxNumberMachines = maxNumberMachines;
	}
	
	/**
	 * Gets the default degree of parallelism for this job. That degree is always used when an operator
	 * is not explicitly given a degree of parallelism.
	 *
	 * @return The default parallelism for the plan.
	 */
	public int getDefaultParallelism() {
		return this.defaultParallelism;
	}
	
	/**
	 * Sets the default degree of parallelism for this plan. That degree is always used when an operator
	 * is not explicitly given a degree of parallelism.
	 *
	 * @param defaultParallelism The default parallelism for the plan.
	 */
	public void setDefaultParallelism(int defaultParallelism) {
		this.defaultParallelism = defaultParallelism;
	}
	
	/**
	 * Gets the optimizer post-pass class for this job. The post-pass typically creates utility classes
	 * for data types and is specific to a particular data model (record, tuple, Scala, ...)
	 *
	 * @return The name of the class implementing the optimizer post-pass.
	 */
	public String getPostPassClassName() {
		return "eu.stratosphere.compiler.postpass.GenericPactRecordPostPass";
	}
	
	// ------------------------------------------------------------------------

	/**
	 * Traverses the job depth first from all data sinks on towards the sources.
	 * 
	 * @see Visitable#accept(Visitor)
	 */
	@Override
	public void accept(Visitor<Operator> visitor) {
		for (GenericDataSink sink : this.sinks) {
			sink.accept(visitor);
		}
	}
}
