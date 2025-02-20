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

package org.apache.flink.streaming.connectors.rabbitmq;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.util.SerializationScheme;
import org.apache.flink.streaming.connectors.util.SimpleStringScheme;

public class RMQTopology {

	public static void main(String[] args) throws Exception {

		StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(1);

		@SuppressWarnings("unused")
		DataStream<String> dataStream1 = env.addSource(
				new RMQSource<String>("localhost", "hello", new SimpleStringScheme())).print();

		@SuppressWarnings("unused")
		DataStream<String> dataStream2 = env.fromElements("one", "two", "three", "four", "five",
				"q").addSink(
				new RMQSink<String>("localhost", "hello", new StringToByteSerializer()));

		env.execute();
	}

	public static class StringToByteSerializer implements SerializationScheme<String, byte[]> {

		private static final long serialVersionUID = 1L;

		@Override
		public byte[] serialize(String element) {
			return element.getBytes();
		}
	}
}
