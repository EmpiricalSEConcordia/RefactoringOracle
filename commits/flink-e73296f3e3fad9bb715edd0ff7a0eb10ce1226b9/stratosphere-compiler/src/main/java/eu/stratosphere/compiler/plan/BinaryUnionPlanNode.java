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

package eu.stratosphere.compiler.plan;


import org.apache.flink.runtime.operators.DriverStrategy;

import eu.stratosphere.compiler.dag.BinaryUnionNode;

/**
 * A special subclass for the union to make it identifiable.
 */
public class BinaryUnionPlanNode extends DualInputPlanNode {
	
	/**
	 * @param template
	 */
	public BinaryUnionPlanNode(BinaryUnionNode template, Channel in1, Channel in2) {
		super(template, "Union", in1, in2, DriverStrategy.UNION);
	}
	
	public BinaryUnionNode getOptimizerNode() {
		return (BinaryUnionNode) this.template;
	}
}
