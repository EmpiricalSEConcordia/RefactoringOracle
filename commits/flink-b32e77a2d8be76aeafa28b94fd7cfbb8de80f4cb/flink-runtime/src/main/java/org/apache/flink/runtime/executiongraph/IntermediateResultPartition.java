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

package org.apache.flink.runtime.executiongraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IntermediateResultPartition {
	
	private final IntermediateResult totalResut;
	
	private final ExecutionVertex2 producer;
	
	private final int partition;
	
	private List<List<ExecutionEdge2>> consumers;
	
	
	public IntermediateResultPartition(IntermediateResult totalResut, ExecutionVertex2 producer, int partition) {
		this.totalResut = totalResut;
		this.producer = producer;
		this.partition = partition;
		this.consumers = new ArrayList<List<ExecutionEdge2>>(0);
	}
	
	public ExecutionVertex2 getProducer() {
		return producer;
	}
	
	public int getPartition() {
		return partition;
	}
	
	public IntermediateResult getIntermediateResult() {
		return totalResut;
	}
	
	public List<List<ExecutionEdge2>> getConsumers() {
		return consumers;
	}
	
	int addConsumerGroup() {
		int pos = consumers.size();
		consumers.add(new ArrayList<ExecutionEdge2>());
		return pos;
	}
	
	public void addConsumer(ExecutionEdge2 edge, int consumerNumber) {
		consumers.get(consumerNumber).add(edge);
	}
}
