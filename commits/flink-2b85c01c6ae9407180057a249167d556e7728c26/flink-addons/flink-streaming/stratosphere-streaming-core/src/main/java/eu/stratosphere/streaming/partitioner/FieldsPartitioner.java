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

package eu.stratosphere.streaming.partitioner;

import eu.stratosphere.runtime.io.api.ChannelSelector;
import eu.stratosphere.streaming.api.streamrecord.StreamRecord;

//Grouping by a key
public class FieldsPartitioner implements ChannelSelector<StreamRecord> {

	private int keyPosition;
	private int[] returnArray;

	public FieldsPartitioner(int keyPosition) {
		this.keyPosition = keyPosition;
		this.returnArray = new int[1];
	}

	@Override
	public int[] selectChannels(StreamRecord record, int numberOfOutputChannels) {
		returnArray[0] = record.hashPartition;
		return returnArray;
	}
}
