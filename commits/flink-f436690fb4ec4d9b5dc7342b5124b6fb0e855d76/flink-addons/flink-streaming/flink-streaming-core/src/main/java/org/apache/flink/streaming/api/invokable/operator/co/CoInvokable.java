/**
 *
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
 *
 */

package org.apache.flink.streaming.api.invokable.operator.co;

import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.streaming.api.invokable.StreamComponentInvokable;
import org.apache.flink.streaming.api.streamrecord.StreamRecord;
import org.apache.flink.streaming.api.streamrecord.StreamRecordSerializer;
import org.apache.flink.util.Collector;
import org.apache.flink.util.MutableObjectIterator;

public abstract class CoInvokable<IN1 extends Tuple, IN2 extends Tuple, OUT extends Tuple> extends
		StreamComponentInvokable {

	private static final long serialVersionUID = 1L;

	protected Collector<OUT> collector;
	protected MutableObjectIterator<StreamRecord<IN1>> recordIterator1;
	protected MutableObjectIterator<StreamRecord<IN2>> recordIterator2;
	protected StreamRecord<IN1> reuse1;
	protected StreamRecord<IN2> reuse2;

	public void initialize(Collector<OUT> collector,
			MutableObjectIterator<StreamRecord<IN1>> recordIterator1,
			StreamRecordSerializer<IN1> serializer1,
			MutableObjectIterator<StreamRecord<IN2>> recordIterator2,
			StreamRecordSerializer<IN2> serializer2) {
		this.collector = collector;
	
		this.recordIterator1 = recordIterator1;
		this.reuse1 = serializer1.createInstance();
		
		this.recordIterator2 = recordIterator2;
		this.reuse2 = serializer2.createInstance();
	}

	public abstract void invoke() throws Exception;
}
