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
package eu.stratosphere.test.compiler.examples;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import eu.stratosphere.api.Job;
import eu.stratosphere.api.distributions.SimpleDistribution;
import eu.stratosphere.api.operators.FileDataSink;
import eu.stratosphere.api.operators.FileDataSource;
import eu.stratosphere.api.operators.Order;
import eu.stratosphere.api.operators.Ordering;
import eu.stratosphere.api.operators.util.FieldList;
import eu.stratosphere.api.record.io.CsvOutputFormat;
import eu.stratosphere.api.record.io.TextInputFormat;
import eu.stratosphere.api.record.operators.MapOperator;
import eu.stratosphere.api.record.operators.ReduceOperator;
import eu.stratosphere.compiler.plan.Channel;
import eu.stratosphere.compiler.plan.OptimizedPlan;
import eu.stratosphere.compiler.plan.SingleInputPlanNode;
import eu.stratosphere.compiler.plan.SinkPlanNode;
import eu.stratosphere.example.record.wordcount.WordCount;
import eu.stratosphere.example.record.wordcount.WordCount.CountWords;
import eu.stratosphere.example.record.wordcount.WordCount.TokenizeLine;
import eu.stratosphere.pact.runtime.shipping.ShipStrategyType;
import eu.stratosphere.pact.runtime.task.DriverStrategy;
import eu.stratosphere.pact.runtime.task.util.LocalStrategy;
import eu.stratosphere.test.compiler.CompilerTestBase;
import eu.stratosphere.types.PactInteger;
import eu.stratosphere.types.PactString;

/**
 *
 */
public class WordCountCompilerTest extends CompilerTestBase {
	
	/**
	 * This method tests the simple word count.
	 */
	@Test
	public void testWordCount() {
		checkWordCount(true);
		checkWordCount(false);
	}
	
	private void checkWordCount(boolean estimates) {
		try {
			WordCount wc = new WordCount();
			Job p = wc.createJob(DEFAULT_PARALLELISM_STRING, IN_FILE, OUT_FILE);
			
			OptimizedPlan plan;
			if (estimates) {
				FileDataSource source = getContractResolver(p).getNode("Input Lines");
				setSourceStatistics(source, 1024*1024*1024*1024L, 24f);
				plan = compileWithStats(p);
			} else {
				plan = compileNoStats(p);
			}
			
			// get the optimizer plan nodes
			OptimizerPlanNodeResolver resolver = getOptimizerPlanNodeResolver(plan);
			SinkPlanNode sink = resolver.getNode("Word Counts");
			SingleInputPlanNode reducer = resolver.getNode("Count Words");
			SingleInputPlanNode mapper = resolver.getNode("Tokenize Lines");
			
			// verify the strategies
			Assert.assertEquals(ShipStrategyType.FORWARD, mapper.getInput().getShipStrategy());
			Assert.assertEquals(ShipStrategyType.PARTITION_HASH, reducer.getInput().getShipStrategy());
			Assert.assertEquals(ShipStrategyType.FORWARD, sink.getInput().getShipStrategy());
			
			Channel c = reducer.getInput();
			Assert.assertEquals(LocalStrategy.COMBININGSORT, c.getLocalStrategy());
			FieldList l = new FieldList(0);
			Assert.assertEquals(l, c.getShipStrategyKeys());
			Assert.assertEquals(l, c.getLocalStrategyKeys());
			Assert.assertTrue(Arrays.equals(c.getLocalStrategySortOrder(), reducer.getSortOrders()));
			
			// check the combiner
			SingleInputPlanNode combiner = (SingleInputPlanNode) reducer.getPredecessor();
			Assert.assertEquals(DriverStrategy.PARTIAL_GROUP, combiner.getDriverStrategy());
			Assert.assertEquals(l, combiner.getKeys());
			Assert.assertEquals(ShipStrategyType.FORWARD, combiner.getInput().getShipStrategy());
			
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}
	
	/**
	 * This method tests that with word count and a range partitioned sink, the range partitioner is pushed down.
	 */
	@Test
	public void testWordCountWithSortedSink() {
		checkWordCountWithSortedSink(true);
		checkWordCountWithSortedSink(false);
	}
	
	private void checkWordCountWithSortedSink(boolean estimates) {
		try {
			FileDataSource sourceNode = new FileDataSource(new TextInputFormat(), IN_FILE, "Input Lines");
			MapOperator mapNode = MapOperator.builder(new TokenizeLine())
				.input(sourceNode)
				.name("Tokenize Lines")
				.build();
			ReduceOperator reduceNode = ReduceOperator.builder(new CountWords(), PactString.class, 0)
				.input(mapNode)
				.name("Count Words")
				.build();
			FileDataSink out = new FileDataSink(new CsvOutputFormat(), OUT_FILE, reduceNode, "Word Counts");
			CsvOutputFormat.configureRecordFormat(out)
				.recordDelimiter('\n')
				.fieldDelimiter(' ')
				.lenient(true)
				.field(PactString.class, 0)
				.field(PactInteger.class, 1);
			
			Ordering ordering = new Ordering(0, PactString.class, Order.DESCENDING);
			out.setGlobalOrder(ordering, new SimpleDistribution(new PactString[] {new PactString("N")}));
			
			Job p = new Job(out, "WordCount Example");
			p.setDefaultParallelism(DEFAULT_PARALLELISM);
	
			OptimizedPlan plan;
			if (estimates) {
				setSourceStatistics(sourceNode, 1024*1024*1024*1024L, 24f);
				plan = compileWithStats(p);
			} else {
				plan = compileNoStats(p);
			}
			
			OptimizerPlanNodeResolver resolver = getOptimizerPlanNodeResolver(plan);
			SinkPlanNode sink = resolver.getNode("Word Counts");
			SingleInputPlanNode reducer = resolver.getNode("Count Words");
			SingleInputPlanNode mapper = resolver.getNode("Tokenize Lines");
			
			Assert.assertEquals(ShipStrategyType.FORWARD, mapper.getInput().getShipStrategy());
			Assert.assertEquals(ShipStrategyType.PARTITION_RANGE, reducer.getInput().getShipStrategy());
			Assert.assertEquals(ShipStrategyType.FORWARD, sink.getInput().getShipStrategy());
			
			Channel c = reducer.getInput();
			Assert.assertEquals(LocalStrategy.COMBININGSORT, c.getLocalStrategy());
			FieldList l = new FieldList(0);
			Assert.assertEquals(l, c.getShipStrategyKeys());
			Assert.assertEquals(l, c.getLocalStrategyKeys());
			
			// check that the sort orders are descending
			Assert.assertFalse(c.getShipStrategySortOrder()[0]);
			Assert.assertFalse(c.getLocalStrategySortOrder()[0]);
			
			// check the combiner
			SingleInputPlanNode combiner = (SingleInputPlanNode) reducer.getPredecessor();
			Assert.assertEquals(DriverStrategy.PARTIAL_GROUP, combiner.getDriverStrategy());
			Assert.assertEquals(l, combiner.getKeys());
			Assert.assertEquals(ShipStrategyType.FORWARD, combiner.getInput().getShipStrategy());
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}
	
}
