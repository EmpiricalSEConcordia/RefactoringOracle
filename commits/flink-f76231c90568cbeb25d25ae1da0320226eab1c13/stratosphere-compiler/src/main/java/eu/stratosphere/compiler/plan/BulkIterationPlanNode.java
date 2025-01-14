/***********************************************************************************************************************
 *
 * Copyright (C) 2012 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.compiler.plan;

import static eu.stratosphere.compiler.plan.PlanNode.SourceAndDamReport.FOUND_SOURCE;
import static eu.stratosphere.compiler.plan.PlanNode.SourceAndDamReport.FOUND_SOURCE_AND_DAM;

import eu.stratosphere.api.typeutils.TypeSerializerFactory;
import eu.stratosphere.compiler.costs.Costs;
import eu.stratosphere.compiler.dag.BulkIterationNode;
import eu.stratosphere.pact.runtime.task.DriverStrategy;
import eu.stratosphere.util.Visitor;

/**
 * 
 */
public class BulkIterationPlanNode extends SingleInputPlanNode implements IterationPlanNode {
	
	private final BulkPartialSolutionPlanNode partialSolutionPlanNode;
	
	private final PlanNode rootOfStepFunction;
	
	private TypeSerializerFactory<?> serializerForIterationChannel;
	
	// --------------------------------------------------------------------------------------------

	public BulkIterationPlanNode(BulkIterationNode template, String nodeName, Channel input,
			BulkPartialSolutionPlanNode pspn, PlanNode rootOfStepFunction) {
		super(template, nodeName, input, DriverStrategy.NONE);
		this.partialSolutionPlanNode = pspn;
		this.rootOfStepFunction = rootOfStepFunction;
	}

	// --------------------------------------------------------------------------------------------
	
	public BulkIterationNode getIterationNode() {
		if (this.template instanceof BulkIterationNode) {
			return (BulkIterationNode) this.template;
		} else {
			throw new RuntimeException();
		}
	}
	
	public BulkPartialSolutionPlanNode getPartialSolutionPlanNode() {
		return this.partialSolutionPlanNode;
	}
	
	public PlanNode getRootOfStepFunction() {
		return this.rootOfStepFunction;
	}
	
	// --------------------------------------------------------------------------------------------

	
	public TypeSerializerFactory<?> getSerializerForIterationChannel() {
		return serializerForIterationChannel;
	}
	
	public void setSerializerForIterationChannel(TypeSerializerFactory<?> serializerForIterationChannel) {
		this.serializerForIterationChannel = serializerForIterationChannel;
	}

	public void setCosts(Costs nodeCosts) {
		// add the costs from the step function
		nodeCosts.addCosts(this.rootOfStepFunction.getCumulativeCosts());
		super.setCosts(nodeCosts);
	}
	
	public int getMemoryConsumerWeight() {
		return 1;
	}
	

	@Override
	public SourceAndDamReport hasDamOnPathDownTo(PlanNode source) {
		SourceAndDamReport fromOutside = super.hasDamOnPathDownTo(source);
		
		if (fromOutside == FOUND_SOURCE_AND_DAM) {
			return FOUND_SOURCE_AND_DAM;
		}
		else if (fromOutside == FOUND_SOURCE) {
			// we always have a dam in the back channel
			return FOUND_SOURCE_AND_DAM;
		} else {
			return fromOutside;
		}
	}


	@Override
	public void acceptForStepFunction(Visitor<PlanNode> visitor) {
		this.rootOfStepFunction.accept(visitor);
	}
}
