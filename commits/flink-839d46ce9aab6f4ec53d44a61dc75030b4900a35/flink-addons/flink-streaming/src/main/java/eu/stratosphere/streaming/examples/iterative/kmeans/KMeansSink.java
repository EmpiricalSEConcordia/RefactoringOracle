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

package eu.stratosphere.streaming.examples.iterative.kmeans;

import eu.stratosphere.streaming.api.invokable.UserSinkInvokable;
import eu.stratosphere.streaming.api.streamrecord.StreamRecord;

public class KMeansSink extends UserSinkInvokable {
	private static final long serialVersionUID = 1L;

	@Override
	public void invoke(StreamRecord record) throws Exception {
		// TODO Auto-generated method stub
		//int tupleNum = record.getNumOfTuples();
		System.out.println("============================================");
		System.out.println("record=" + record.getString(0, 0));
		System.out.println("============================================");		
	}
}