/***********************************************************************************************************************
 *
 * Copyright (C) 2010-2014 by the Stratosphere project (http://stratosphere.eu)
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

package eu.stratosphere.streaming.examples.window.sum;

import eu.stratosphere.api.java.tuple.Tuple2;
import eu.stratosphere.streaming.api.DataStream;
import eu.stratosphere.streaming.api.StreamExecutionEnvironment;

public class WindowSumLocal {
	
	public static void main(String[] args) {
		StreamExecutionEnvironment env = new StreamExecutionEnvironment();
		
		@SuppressWarnings("unused")
		DataStream<Tuple2<Integer, Long>> dataStream = env
				.addSource(new WindowSumSource())
				.map(new WindowSumMultiple())
				.flatMap(new WindowSumAggregate())
				.addSink(new WindowSumSink());
		
		env.execute();
	}
}
