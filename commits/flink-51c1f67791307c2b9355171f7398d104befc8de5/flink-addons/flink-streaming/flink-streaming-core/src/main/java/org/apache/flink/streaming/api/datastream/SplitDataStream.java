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

package org.apache.flink.streaming.api.datastream;

import java.util.Arrays;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.collector.OutputSelector;

/**
 * The SplitDataStream represents an operator that has been split using an
 * {@link OutputSelector}. Named outputs can be selected using the
 * {@link #select} function.
 *
 * @param <OUT>
 *            The type of the output.
 */
public class SplitDataStream<OUT> {

	DataStream<OUT> dataStream;

	protected SplitDataStream(DataStream<OUT> dataStream) {
		this.dataStream = dataStream.copy();
	}

	/**
	 * Gets the output type.
	 * 
	 * @return The output type.
	 */
	public TypeInformation<OUT> getOutputType() {
		return dataStream.getType();
	}
	
	/**
	 * Sets the output names for which the next operator will receive values.
	 * 
	 * @param outputNames
	 *            The output names for which the operator will receive the
	 *            input.
	 * @return Returns the selected DataStream
	 */
	public DataStream<OUT> select(String... outputNames) {
		return selectOutput(outputNames);
	}

	/**
	 * Selects all output names from a split data stream.
	 * 
	 * @return Returns the selected DataStream
	 */
	public DataStream<OUT> selectAll() {
		DataStream<OUT> returnStream = dataStream.copy();
		returnStream.selectAll = true;
		return returnStream;
	}

	private DataStream<OUT> selectOutput(String[] outputName) {
		DataStream<OUT> returnStream = dataStream.copy();
		returnStream.userDefinedNames = Arrays.asList(outputName);
		return returnStream;
	}

}
