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

package eu.stratosphere.test.iterative.nephele.customdanglingpagerank;

import org.apache.flink.api.common.operators.util.UserCodeClassWrapper;
import org.apache.flink.api.common.typeutils.TypeComparatorFactory;
import org.apache.flink.api.common.typeutils.TypePairComparatorFactory;
import org.apache.flink.api.common.typeutils.TypeSerializerFactory;
import org.apache.flink.api.java.record.io.FileOutputFormat;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.Path;
import org.apache.flink.runtime.io.network.channels.ChannelType;
import org.apache.flink.runtime.iterative.task.IterationHeadPactTask;
import org.apache.flink.runtime.iterative.task.IterationIntermediatePactTask;
import org.apache.flink.runtime.iterative.task.IterationTailPactTask;
import org.apache.flink.runtime.jobgraph.DistributionPattern;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobInputVertex;
import org.apache.flink.runtime.jobgraph.JobOutputVertex;
import org.apache.flink.runtime.jobgraph.JobTaskVertex;
import org.apache.flink.runtime.operators.BuildSecondCachedMatchDriver;
import org.apache.flink.runtime.operators.CoGroupDriver;
import org.apache.flink.runtime.operators.CollectorMapDriver;
import org.apache.flink.runtime.operators.DriverStrategy;
import org.apache.flink.runtime.operators.chaining.SynchronousChainedCombineDriver;
import org.apache.flink.runtime.operators.shipping.ShipStrategyType;
import org.apache.flink.runtime.operators.util.LocalStrategy;
import org.apache.flink.runtime.operators.util.TaskConfig;

import eu.stratosphere.test.iterative.nephele.JobGraphUtils;
import eu.stratosphere.test.iterative.nephele.customdanglingpagerank.types.VertexWithAdjacencyList;
import eu.stratosphere.test.iterative.nephele.customdanglingpagerank.types.VertexWithAdjacencyListComparatorFactory;
import eu.stratosphere.test.iterative.nephele.customdanglingpagerank.types.VertexWithAdjacencyListSerializerFactory;
import eu.stratosphere.test.iterative.nephele.customdanglingpagerank.types.VertexWithRank;
import eu.stratosphere.test.iterative.nephele.customdanglingpagerank.types.VertexWithRankAndDangling;
import eu.stratosphere.test.iterative.nephele.customdanglingpagerank.types.VertexWithRankAndDanglingComparatorFactory;
import eu.stratosphere.test.iterative.nephele.customdanglingpagerank.types.VertexWithRankAndDanglingSerializerFactory;
import eu.stratosphere.test.iterative.nephele.customdanglingpagerank.types.VertexWithRankComparatorFactory;
import eu.stratosphere.test.iterative.nephele.customdanglingpagerank.types.VertexWithRankDanglingToVertexWithAdjacencyListPairComparatorFactory;
import eu.stratosphere.test.iterative.nephele.customdanglingpagerank.types.VertexWithRankDanglingToVertexWithRankPairComparatorFactory;
import eu.stratosphere.test.iterative.nephele.customdanglingpagerank.types.VertexWithRankSerializerFactory;
import eu.stratosphere.test.iterative.nephele.danglingpagerank.DiffL1NormConvergenceCriterion;
import eu.stratosphere.test.iterative.nephele.danglingpagerank.PageRankStatsAggregator;

public class CustomCompensatableDanglingPageRankWithCombiner {
	
	private static final int NUM_FILE_HANDLES_PER_SORT = 64;
	
	private static final float SORT_SPILL_THRESHOLD = 0.85f;
	
	private static final int ITERATION_ID = 1;
	
	
	private static TypeSerializerFactory<VertexWithRank> vertexWithRankSerializer = new VertexWithRankSerializerFactory();
	
	private static TypeSerializerFactory<VertexWithRankAndDangling> vertexWithRankAndDanglingSerializer = new VertexWithRankAndDanglingSerializerFactory();
	
	private static TypeSerializerFactory<VertexWithAdjacencyList> vertexWithAdjacencyListSerializer = new VertexWithAdjacencyListSerializerFactory();
	
	private static TypeComparatorFactory<VertexWithRank> vertexWithRankComparator = new VertexWithRankComparatorFactory();
	
	private static TypeComparatorFactory<VertexWithRankAndDangling> vertexWithRankAndDanglingComparator = new VertexWithRankAndDanglingComparatorFactory();
	
	private static TypeComparatorFactory<VertexWithAdjacencyList> vertexWithAdjacencyListComparator = new VertexWithAdjacencyListComparatorFactory();
	
	private static TypePairComparatorFactory<VertexWithRankAndDangling, VertexWithAdjacencyList> matchComparator =
			new VertexWithRankDanglingToVertexWithAdjacencyListPairComparatorFactory();
	
	private static TypePairComparatorFactory<VertexWithRankAndDangling, VertexWithRank> coGroupComparator =
			new VertexWithRankDanglingToVertexWithRankPairComparatorFactory();
	

//	public static void main(String[] args) throws Exception {
//		String confPath = args.length >= 6 ? confPath = args[5] : PlayConstants.PLAY_DIR + "local-conf";
//		
//		GlobalConfiguration.loadConfiguration(confPath);
//		Configuration conf = GlobalConfiguration.getConfiguration();
//		
//		JobGraph jobGraph = getJobGraph(args);
//		JobGraphUtils.submit(jobGraph, conf);
//	}
		
	public static JobGraph getJobGraph(String[] args) throws Exception {

		int degreeOfParallelism = 2;
		String pageWithRankInputPath = ""; //"file://" + PlayConstants.PLAY_DIR + "test-inputs/danglingpagerank/pageWithRank";
		String adjacencyListInputPath = ""; //"file://" + PlayConstants.PLAY_DIR +
//			"test-inputs/danglingpagerank/adjacencylists";
		String outputPath =  Path.constructTestURI("stratosphere_iterations");
		int minorConsumer = 2;
		int matchMemory = 5;
		int coGroupSortMemory = 5;
		int numIterations = 25;
		long numVertices = 5;
		long numDanglingVertices = 1;

		String failingWorkers = "1";
		int failingIteration = 2;
		double messageLoss = 0.75;

		if (args.length >= 14) {
			degreeOfParallelism = Integer.parseInt(args[0]);
			pageWithRankInputPath = args[1];
			adjacencyListInputPath = args[2];
			outputPath = args[3];
			// [4] is config path
			minorConsumer = Integer.parseInt(args[5]);
			matchMemory = Integer.parseInt(args[6]);
			coGroupSortMemory = Integer.parseInt(args[7]);
			numIterations = Integer.parseInt(args[8]);
			numVertices = Long.parseLong(args[9]);
			numDanglingVertices = Long.parseLong(args[10]);
			failingWorkers = args[11];
			failingIteration = Integer.parseInt(args[12]);
			messageLoss = Double.parseDouble(args[13]);
		}

		int totalMemoryConsumption = 3*minorConsumer + 2*coGroupSortMemory + matchMemory;

		JobGraph jobGraph = new JobGraph("CompensatableDanglingPageRank");
		
		// --------------- the inputs ---------------------

		// page rank input
		JobInputVertex pageWithRankInput = JobGraphUtils.createInput(new CustomImprovedDanglingPageRankInputFormat(),
			pageWithRankInputPath, "DanglingPageWithRankInput", jobGraph, degreeOfParallelism);
		TaskConfig pageWithRankInputConfig = new TaskConfig(pageWithRankInput.getConfiguration());
		pageWithRankInputConfig.addOutputShipStrategy(ShipStrategyType.PARTITION_HASH);
		pageWithRankInputConfig.setOutputComparator(vertexWithRankAndDanglingComparator, 0);
		pageWithRankInputConfig.setOutputSerializer(vertexWithRankAndDanglingSerializer);
		pageWithRankInputConfig.setStubParameter("pageRank.numVertices", String.valueOf(numVertices));

		// edges as adjacency list
		JobInputVertex adjacencyListInput = JobGraphUtils.createInput(new CustomImprovedAdjacencyListInputFormat(),
			adjacencyListInputPath, "AdjancencyListInput", jobGraph, degreeOfParallelism);
		TaskConfig adjacencyListInputConfig = new TaskConfig(adjacencyListInput.getConfiguration());
		adjacencyListInputConfig.addOutputShipStrategy(ShipStrategyType.PARTITION_HASH);
		adjacencyListInputConfig.setOutputSerializer(vertexWithAdjacencyListSerializer);
		adjacencyListInputConfig.setOutputComparator(vertexWithAdjacencyListComparator, 0);

		// --------------- the head ---------------------
		JobTaskVertex head = JobGraphUtils.createTask(IterationHeadPactTask.class, "IterationHead", jobGraph,
			degreeOfParallelism);
		TaskConfig headConfig = new TaskConfig(head.getConfiguration());
		headConfig.setIterationId(ITERATION_ID);
		
		// initial input / partial solution
		headConfig.addInputToGroup(0);
		headConfig.setIterationHeadPartialSolutionOrWorksetInputIndex(0);
		headConfig.setInputSerializer(vertexWithRankAndDanglingSerializer, 0);
		headConfig.setInputComparator(vertexWithRankAndDanglingComparator, 0);
		headConfig.setInputLocalStrategy(0, LocalStrategy.SORT);
		headConfig.setRelativeMemoryInput(0, (double)minorConsumer/totalMemoryConsumption);
		headConfig.setFilehandlesInput(0, NUM_FILE_HANDLES_PER_SORT);
		headConfig.setSpillingThresholdInput(0, SORT_SPILL_THRESHOLD);
		
		// back channel / iterations
		headConfig.setRelativeBackChannelMemory((double)minorConsumer/totalMemoryConsumption);
		
		// output into iteration
		headConfig.setOutputSerializer(vertexWithRankAndDanglingSerializer);
		headConfig.addOutputShipStrategy(ShipStrategyType.FORWARD);
		headConfig.addOutputShipStrategy(ShipStrategyType.FORWARD);
		
		// final output
		TaskConfig headFinalOutConfig = new TaskConfig(new Configuration());
		headFinalOutConfig.setOutputSerializer(vertexWithRankAndDanglingSerializer);
		headFinalOutConfig.addOutputShipStrategy(ShipStrategyType.FORWARD);
		headConfig.setIterationHeadFinalOutputConfig(headFinalOutConfig);
		
		// the sync
		headConfig.setIterationHeadIndexOfSyncOutput(3);
		headConfig.setNumberOfIterations(numIterations);
		
		// the driver 
		headConfig.setDriver(CollectorMapDriver.class);
		headConfig.setDriverStrategy(DriverStrategy.COLLECTOR_MAP);
		headConfig.setStubWrapper(new UserCodeClassWrapper<CustomCompensatingMap>(CustomCompensatingMap.class));
		headConfig.setStubParameter("pageRank.numVertices", String.valueOf(numVertices));
		headConfig.setStubParameter("compensation.failingWorker", failingWorkers);
		headConfig.setStubParameter("compensation.failingIteration", String.valueOf(failingIteration));
		headConfig.setStubParameter("compensation.messageLoss", String.valueOf(messageLoss));
		headConfig.addIterationAggregator(CustomCompensatableDotProductCoGroup.AGGREGATOR_NAME, new PageRankStatsAggregator());

		// --------------- the join ---------------------
		
		JobTaskVertex intermediate = JobGraphUtils.createTask(IterationIntermediatePactTask.class,
			"IterationIntermediate", jobGraph, degreeOfParallelism);
		TaskConfig intermediateConfig = new TaskConfig(intermediate.getConfiguration());
		intermediateConfig.setIterationId(ITERATION_ID);
//		intermediateConfig.setDriver(RepeatableHashjoinMatchDriverWithCachedBuildside.class);
		intermediateConfig.setDriver(BuildSecondCachedMatchDriver.class);
		intermediateConfig.setDriverStrategy(DriverStrategy.HYBRIDHASH_BUILD_SECOND);
		intermediateConfig.setRelativeMemoryDriver((double)matchMemory/totalMemoryConsumption);
		intermediateConfig.addInputToGroup(0);
		intermediateConfig.addInputToGroup(1);
		intermediateConfig.setInputSerializer(vertexWithRankAndDanglingSerializer, 0);
		intermediateConfig.setInputSerializer(vertexWithAdjacencyListSerializer, 1);
		intermediateConfig.setDriverComparator(vertexWithRankAndDanglingComparator, 0);
		intermediateConfig.setDriverComparator(vertexWithAdjacencyListComparator, 1);
		intermediateConfig.setDriverPairComparator(matchComparator);
		
		intermediateConfig.setOutputSerializer(vertexWithRankSerializer);
		intermediateConfig.addOutputShipStrategy(ShipStrategyType.FORWARD);
		
		intermediateConfig.setStubWrapper(new UserCodeClassWrapper<CustomCompensatableDotProductMatch>(CustomCompensatableDotProductMatch.class));
		intermediateConfig.setStubParameter("pageRank.numVertices", String.valueOf(numVertices));
		intermediateConfig.setStubParameter("compensation.failingWorker", failingWorkers);
		intermediateConfig.setStubParameter("compensation.failingIteration", String.valueOf(failingIteration));
		intermediateConfig.setStubParameter("compensation.messageLoss", String.valueOf(messageLoss));
		
		// the combiner and the output
		TaskConfig combinerConfig = new TaskConfig(new Configuration());
		combinerConfig.addInputToGroup(0);
		combinerConfig.setInputSerializer(vertexWithRankSerializer, 0);
		combinerConfig.setDriverStrategy(DriverStrategy.SORTED_GROUP_COMBINE);
		combinerConfig.setDriverComparator(vertexWithRankComparator, 0);
		combinerConfig.setRelativeMemoryDriver((double)coGroupSortMemory/totalMemoryConsumption);
		combinerConfig.setOutputSerializer(vertexWithRankSerializer);
		combinerConfig.addOutputShipStrategy(ShipStrategyType.PARTITION_HASH);
		combinerConfig.setOutputComparator(vertexWithRankComparator, 0);
		combinerConfig.setStubWrapper(new UserCodeClassWrapper<CustomRankCombiner>(CustomRankCombiner.class));
		intermediateConfig.addChainedTask(SynchronousChainedCombineDriver.class, combinerConfig, "Combiner");

		// ---------------- the tail (co group) --------------------
		
		JobTaskVertex tail = JobGraphUtils.createTask(IterationTailPactTask.class, "IterationTail", jobGraph,
			degreeOfParallelism);
		TaskConfig tailConfig = new TaskConfig(tail.getConfiguration());
		tailConfig.setIterationId(ITERATION_ID);
        tailConfig.setIsWorksetUpdate();
		
		// inputs and driver
		tailConfig.setDriver(CoGroupDriver.class);
		tailConfig.setDriverStrategy(DriverStrategy.CO_GROUP);
		tailConfig.addInputToGroup(0);
		tailConfig.addInputToGroup(1);
		tailConfig.setInputSerializer(vertexWithRankAndDanglingSerializer, 0);
		tailConfig.setInputSerializer(vertexWithRankSerializer, 1);
		tailConfig.setDriverComparator(vertexWithRankAndDanglingComparator, 0);
		tailConfig.setDriverComparator(vertexWithRankComparator, 1);
		tailConfig.setDriverPairComparator(coGroupComparator);
		tailConfig.setInputAsynchronouslyMaterialized(0, true);
		tailConfig.setRelativeInputMaterializationMemory(0, (double)minorConsumer/totalMemoryConsumption);
		tailConfig.setInputLocalStrategy(1, LocalStrategy.SORT);
		tailConfig.setInputComparator(vertexWithRankComparator, 1);
		tailConfig.setRelativeMemoryInput(1, (double)coGroupSortMemory/totalMemoryConsumption);
		tailConfig.setFilehandlesInput(1, NUM_FILE_HANDLES_PER_SORT);
		tailConfig.setSpillingThresholdInput(1, SORT_SPILL_THRESHOLD);
		tailConfig.addIterationAggregator(CustomCompensatableDotProductCoGroup.AGGREGATOR_NAME, new PageRankStatsAggregator());
		
		// output
		tailConfig.addOutputShipStrategy(ShipStrategyType.FORWARD);
		tailConfig.setOutputSerializer(vertexWithRankAndDanglingSerializer);
		
		// the stub
		tailConfig.setStubWrapper(new UserCodeClassWrapper<CustomCompensatableDotProductCoGroup>(CustomCompensatableDotProductCoGroup.class));
		tailConfig.setStubParameter("pageRank.numVertices", String.valueOf(numVertices));
		tailConfig.setStubParameter("pageRank.numDanglingVertices", String.valueOf(numDanglingVertices));
		tailConfig.setStubParameter("compensation.failingWorker", failingWorkers);
		tailConfig.setStubParameter("compensation.failingIteration", String.valueOf(failingIteration));
		tailConfig.setStubParameter("compensation.messageLoss", String.valueOf(messageLoss));
		
		// --------------- the output ---------------------

		JobOutputVertex output = JobGraphUtils.createFileOutput(jobGraph, "FinalOutput", degreeOfParallelism);
		TaskConfig outputConfig = new TaskConfig(output.getConfiguration());
		outputConfig.addInputToGroup(0);
		outputConfig.setInputSerializer(vertexWithRankAndDanglingSerializer, 0);
		outputConfig.setStubWrapper(new UserCodeClassWrapper<CustomPageWithRankOutFormat>(CustomPageWithRankOutFormat.class));
		outputConfig.setStubParameter(FileOutputFormat.FILE_PARAMETER_KEY, outputPath);
		
		// --------------- the auxiliaries ---------------------
		
		JobOutputVertex fakeTailOutput = JobGraphUtils.createFakeOutput(jobGraph, "FakeTailOutput",
			degreeOfParallelism);

		JobOutputVertex sync = JobGraphUtils.createSync(jobGraph, degreeOfParallelism);
		TaskConfig syncConfig = new TaskConfig(sync.getConfiguration());
		syncConfig.setNumberOfIterations(numIterations);
		syncConfig.addIterationAggregator(CustomCompensatableDotProductCoGroup.AGGREGATOR_NAME, new PageRankStatsAggregator());
		syncConfig.setConvergenceCriterion(CustomCompensatableDotProductCoGroup.AGGREGATOR_NAME, new DiffL1NormConvergenceCriterion());
		syncConfig.setIterationId(ITERATION_ID);
		
		// --------------- the wiring ---------------------

		JobGraphUtils.connect(pageWithRankInput, head, ChannelType.NETWORK, DistributionPattern.BIPARTITE);

		JobGraphUtils.connect(head, intermediate, ChannelType.IN_MEMORY, DistributionPattern.POINTWISE);
		intermediateConfig.setGateIterativeWithNumberOfEventsUntilInterrupt(0, 1);
		
		JobGraphUtils.connect(adjacencyListInput, intermediate, ChannelType.NETWORK, DistributionPattern.BIPARTITE);
		
		JobGraphUtils.connect(head, tail, ChannelType.NETWORK, DistributionPattern.POINTWISE);
		JobGraphUtils.connect(intermediate, tail, ChannelType.NETWORK, DistributionPattern.BIPARTITE);
		tailConfig.setGateIterativeWithNumberOfEventsUntilInterrupt(0, 1);
		tailConfig.setGateIterativeWithNumberOfEventsUntilInterrupt(1, degreeOfParallelism);

		JobGraphUtils.connect(head, output, ChannelType.IN_MEMORY, DistributionPattern.POINTWISE);
		JobGraphUtils.connect(tail, fakeTailOutput, ChannelType.IN_MEMORY, DistributionPattern.POINTWISE);

		JobGraphUtils.connect(head, sync, ChannelType.NETWORK, DistributionPattern.POINTWISE);
		
		fakeTailOutput.setVertexToShareInstancesWith(tail);
		tail.setVertexToShareInstancesWith(head);
		pageWithRankInput.setVertexToShareInstancesWith(head);
		adjacencyListInput.setVertexToShareInstancesWith(head);
		intermediate.setVertexToShareInstancesWith(head);
		output.setVertexToShareInstancesWith(head);
		sync.setVertexToShareInstancesWith(head);

		return jobGraph;
	}
}
