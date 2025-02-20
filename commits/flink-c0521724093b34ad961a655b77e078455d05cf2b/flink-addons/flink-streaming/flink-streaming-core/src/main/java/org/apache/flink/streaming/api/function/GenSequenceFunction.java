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

package org.apache.flink.streaming.api.function;

import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.util.Collector;

/**
 * Source Function used to generate the number sequence
 * 
 */
public class GenSequenceFunction extends SourceFunction<Tuple1<Long>> {

	private static final long serialVersionUID = 1L;

	long from;
	long to;
	Tuple1<Long> outTuple = new Tuple1<Long>();

	public GenSequenceFunction(long from, long to) {
		this.from = from;
		this.to = to;
	}

	@Override
	public void invoke(Collector<Tuple1<Long>> collector) throws Exception {
		for (long i = from; i <= to; i++) {
			outTuple.f0 = i;
			collector.collect(outTuple);
		}
	}

}
