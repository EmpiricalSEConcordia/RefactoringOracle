/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
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

package eu.stratosphere.pact.example.connectedcomponents;

import java.io.Serializable;
import java.util.Iterator;

import eu.stratosphere.api.functions.StubAnnotation.ConstantFieldsFirst;
import eu.stratosphere.api.functions.StubAnnotation.ConstantFieldsSecond;
import eu.stratosphere.api.operators.FileDataSink;
import eu.stratosphere.api.operators.FileDataSource;
import eu.stratosphere.api.operators.WorksetIteration;
import eu.stratosphere.api.plan.Plan;
import eu.stratosphere.api.plan.PlanAssembler;
import eu.stratosphere.api.plan.PlanAssemblerDescription;
import eu.stratosphere.pact.common.contract.CoGroupContract;
import eu.stratosphere.pact.common.contract.CoGroupContract.CombinableFirst;
import eu.stratosphere.pact.common.contract.MatchContract;
import eu.stratosphere.pact.common.io.RecordOutputFormat;
import eu.stratosphere.pact.common.stubs.CoGroupStub;
import eu.stratosphere.pact.common.stubs.MatchStub;
import eu.stratosphere.pact.example.connectedcomponents.DuplicateLongInputFormat;
import eu.stratosphere.pact.example.connectedcomponents.LongLongInputFormat;
import eu.stratosphere.types.PactLong;
import eu.stratosphere.types.PactRecord;
import eu.stratosphere.util.Collector;

/**
 *
 */
public class WorksetConnectedComponentsWithCoGroup implements PlanAssembler, PlanAssemblerDescription {
	
	public static final class NeighborWithComponentIDJoin extends MatchStub implements Serializable {
		private static final long serialVersionUID = 1L;

		private final PactRecord result = new PactRecord();

		@Override
		public void match(PactRecord vertexWithComponent, PactRecord edge, Collector<PactRecord> out) {
			this.result.setField(0, edge.getField(1, PactLong.class));
			this.result.setField(1, vertexWithComponent.getField(1, PactLong.class));
			out.collect(this.result);
		}
	}
	
	@CombinableFirst
	@ConstantFieldsFirst(0)
	@ConstantFieldsSecond(0)
	public static final class MinIdAndUpdate extends CoGroupStub implements Serializable {
		private static final long serialVersionUID = 1L;

		private final PactLong newComponentId = new PactLong();
		
		@Override
		public void coGroup(Iterator<PactRecord> candidates, Iterator<PactRecord> current, Collector<PactRecord> out) throws Exception {
			if (!current.hasNext()) {
				throw new Exception("Error: Id not encountered before.");
			}
			PactRecord old = current.next();
			long oldId = old.getField(1, PactLong.class).getValue();
			
			long minimumComponentID = Long.MAX_VALUE;

			while (candidates.hasNext()) {
				long candidateComponentID = candidates.next().getField(1, PactLong.class).getValue();
				if (candidateComponentID < minimumComponentID) {
					minimumComponentID = candidateComponentID;
				}
			}
			
			if (minimumComponentID < oldId) {
				newComponentId.setValue(minimumComponentID);
				old.setField(1, newComponentId);
				out.collect(old);
			}
		}
		
		@Override
		public void combineFirst(Iterator<PactRecord> records, Collector<PactRecord> out) throws Exception {
			PactRecord next = null;
			long min = Long.MAX_VALUE;
			while (records.hasNext()) {
				next = records.next();
				min = Math.min(min, next.getField(1, PactLong.class).getValue());
			}
			
			newComponentId.setValue(min);
			next.setField(1, newComponentId);
			out.collect(next);
		}
	}
	
	@Override
	public Plan getPlan(String... args) {
		// parse job parameters
		final int numSubTasks = (args.length > 0 ? Integer.parseInt(args[0]) : 1);
		final String verticesInput = (args.length > 1 ? args[1] : "");
		final String edgeInput = (args.length > 2 ? args[2] : "");
		final String output = (args.length > 3 ? args[3] : "");
		final int maxIterations = (args.length > 4 ? Integer.parseInt(args[4]) : 1);

		// create DataSourceContract for the vertices
		FileDataSource initialVertices = new FileDataSource(new DuplicateLongInputFormat(), verticesInput, "Vertices");
		
		WorksetIteration iteration = new WorksetIteration(0, "Connected Components Iteration");
		iteration.setInitialSolutionSet(initialVertices);
		iteration.setInitialWorkset(initialVertices);
		iteration.setMaximumNumberOfIterations(maxIterations);
		
		// create DataSourceContract for the edges
		FileDataSource edges = new FileDataSource(new LongLongInputFormat(), edgeInput, "Edges");

		// create CrossContract for distance computation
		MatchContract joinWithNeighbors = MatchContract.builder(new NeighborWithComponentIDJoin(), PactLong.class, 0, 0)
				.input1(iteration.getWorkset())
				.input2(edges)
				.name("Join Candidate Id With Neighbor")
				.build();

		CoGroupContract minAndUpdate = CoGroupContract.builder(new MinIdAndUpdate(), PactLong.class, 0, 0)
				.input1(joinWithNeighbors)
				.input2(iteration.getSolutionSet())
				.name("Min Id and Update")
				.build();
		
		iteration.setNextWorkset(minAndUpdate);
		iteration.setSolutionSetDelta(minAndUpdate);

		// create DataSinkContract for writing the new cluster positions
		FileDataSink result = new FileDataSink(new RecordOutputFormat(), output, iteration, "Result");
		RecordOutputFormat.configureRecordFormat(result)
			.recordDelimiter('\n')
			.fieldDelimiter(' ')
			.field(PactLong.class, 0)
			.field(PactLong.class, 1);

		// return the PACT plan
		Plan plan = new Plan(result, "Workset Connected Components");
		plan.setDefaultParallelism(numSubTasks);
		return plan;
	}

	@Override
	public String getDescription() {
		return "Parameters: <numberOfSubTasks> <vertices> <edges> <out> <maxIterations>";
	}
}
