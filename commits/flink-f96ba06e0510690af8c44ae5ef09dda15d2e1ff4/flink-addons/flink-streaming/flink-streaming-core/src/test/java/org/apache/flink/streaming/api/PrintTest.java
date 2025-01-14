/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.GroupReduceFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.invokable.util.TimeStamp;
import org.apache.flink.streaming.api.windowing.extractor.Extractor;
import org.apache.flink.streaming.api.windowing.helper.Count;
import org.apache.flink.streaming.api.windowing.helper.Time;
import org.apache.flink.util.Collector;
import org.junit.Test;

public class PrintTest implements Serializable {

	private static final long MEMORYSIZE = 32;

	private static final class IdentityMap implements MapFunction<Long, Long> {
		private static final long serialVersionUID = 1L;

		@Override
		public Long map(Long value) throws Exception {
			return value;
		}
	}

	private static final class FilterAll implements FilterFunction<Long> {
		private static final long serialVersionUID = 1L;

		@Override
		public boolean filter(Long value) throws Exception {
			return true;
		}
	}

	@Test
	public void test() throws Exception {
		LocalStreamEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(1);

		List<Tuple2<String, Integer>> input = new ArrayList<Tuple2<String, Integer>>();

		env.fromElements(1, 2, 3, 4, 5, 6, 7, 8, 9)
				.window(Time.of(2, TimeUnit.MILLISECONDS).withTimeStamp(new TimeStamp<Integer>() {

					/**
					 * 
					 */
					private static final long serialVersionUID = 1L;

					@Override
					public long getTimestamp(Integer value) {

						return value;
					}

					@Override
					public long getStartTime() {
						return 1;
					}
				}, new Extractor<Long, Integer>() {

					private static final long serialVersionUID = 1L;

					@Override
					public Integer extract(Long in) {
						return in.intValue();
					}
				})).every(Count.of(2)).reduceGroup(new GroupReduceFunction<Integer, String>() {

					@Override
					public void reduce(Iterable<Integer> values, Collector<String> out)
							throws Exception {
						String o = "|";
						for (Integer v : values) {
							o = o + v + "|";
						}
						out.collect(o);
					}
				}).print();
		env.executeTest(MEMORYSIZE);

	}
}
