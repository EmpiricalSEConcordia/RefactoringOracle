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
package eu.stratosphere.compiler.operators;

import java.util.Collections;
import java.util.List;

import org.apache.flink.runtime.operators.DriverStrategy;
import org.apache.flink.runtime.operators.shipping.ShipStrategyType;

import eu.stratosphere.compiler.costs.Costs;
import eu.stratosphere.compiler.dag.ReduceNode;
import eu.stratosphere.compiler.dag.SingleInputNode;
import eu.stratosphere.compiler.dataproperties.GlobalProperties;
import eu.stratosphere.compiler.dataproperties.LocalProperties;
import eu.stratosphere.compiler.dataproperties.RequestedGlobalProperties;
import eu.stratosphere.compiler.dataproperties.RequestedLocalProperties;
import eu.stratosphere.compiler.plan.Channel;
import eu.stratosphere.compiler.plan.SingleInputPlanNode;

public final class AllReduceProperties extends OperatorDescriptorSingle
{

	@Override
	public DriverStrategy getStrategy() {
		return DriverStrategy.ALL_REDUCE;
	}

	@Override
	public SingleInputPlanNode instantiate(Channel in, SingleInputNode node) {
		if (in.getShipStrategy() == ShipStrategyType.FORWARD) {
			// locally connected, directly instantiate
			return new SingleInputPlanNode(node, "Reduce ("+node.getPactContract().getName()+")", in, DriverStrategy.ALL_REDUCE);
		} else {
			// non forward case.plug in a combiner
			Channel toCombiner = new Channel(in.getSource());
			toCombiner.setShipStrategy(ShipStrategyType.FORWARD);
			
			// create an input node for combine with same DOP as input node
			ReduceNode combinerNode = ((ReduceNode) node).getCombinerUtilityNode();
			combinerNode.setDegreeOfParallelism(in.getSource().getDegreeOfParallelism());

			SingleInputPlanNode combiner = new SingleInputPlanNode(combinerNode, "Combine ("+node.getPactContract().getName()+")", toCombiner, DriverStrategy.ALL_REDUCE);
			combiner.setCosts(new Costs(0, 0));
			combiner.initProperties(toCombiner.getGlobalProperties(), toCombiner.getLocalProperties());
			
			Channel toReducer = new Channel(combiner);
			toReducer.setShipStrategy(in.getShipStrategy(), in.getShipStrategyKeys(), in.getShipStrategySortOrder());
			toReducer.setLocalStrategy(in.getLocalStrategy(), in.getLocalStrategyKeys(), in.getLocalStrategySortOrder());
			return new SingleInputPlanNode(node, "Reduce("+node.getPactContract().getName()+")", toReducer, DriverStrategy.ALL_REDUCE);
		}
	}
	
	@Override
	protected List<RequestedGlobalProperties> createPossibleGlobalProperties() {
		return Collections.singletonList(new RequestedGlobalProperties());
	}

	@Override
	protected List<RequestedLocalProperties> createPossibleLocalProperties() {
		return Collections.singletonList(new RequestedLocalProperties());
	}
	
	@Override
	public GlobalProperties computeGlobalProperties(GlobalProperties gProps) {
		return new GlobalProperties();
	}
	
	@Override
	public LocalProperties computeLocalProperties(LocalProperties lProps) {
		return new LocalProperties();
	}
}