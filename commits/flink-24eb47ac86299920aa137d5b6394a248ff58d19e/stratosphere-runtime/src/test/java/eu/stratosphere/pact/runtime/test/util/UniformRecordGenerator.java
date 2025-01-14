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

package eu.stratosphere.pact.runtime.test.util;

import org.apache.flink.types.IntValue;
import org.apache.flink.types.Record;
import org.apache.flink.util.MutableObjectIterator;

public class UniformRecordGenerator implements MutableObjectIterator<Record> {

	private final IntValue key = new IntValue();
	private final IntValue value = new IntValue();
	
	int numKeys;
	int numVals;
	
	int keyCnt = 0;
	int valCnt = 0;
	int startKey = 0;
	int startVal = 0;
	boolean repeatKey;
	
	public UniformRecordGenerator(int numKeys, int numVals, boolean repeatKey) {
		this(numKeys, numVals, 0, 0, repeatKey);
	}
	
	public UniformRecordGenerator(int numKeys, int numVals, int startKey, int startVal, boolean repeatKey) {
		this.numKeys = numKeys;
		this.numVals = numVals;
		this.startKey = startKey;
		this.startVal = startVal;
		this.repeatKey = repeatKey;
	}

	@Override
	public Record next(Record reuse) {
		if(!repeatKey) {
			if(valCnt >= numVals+startVal) {
				return null;
			}
			
			key.setValue(keyCnt++);
			value.setValue(valCnt);
			
			if(keyCnt == numKeys+startKey) {
				keyCnt = startKey;
				valCnt++;
			}
		} else {
			if(keyCnt >= numKeys+startKey) {
				return null;
			}
			key.setValue(keyCnt);
			value.setValue(valCnt++);
			
			if(valCnt == numVals+startVal) {
				valCnt = startVal;
				keyCnt++;
			}
		}
		
		reuse.setField(0, this.key);
		reuse.setField(1, this.value);
		reuse.updateBinaryRepresenation();
		return reuse;
	}
}
