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

package eu.stratosphere.runtime.io.gates;

import org.apache.flink.core.io.IOReadableWritable;

import eu.stratosphere.runtime.io.gates.InputGate;

/**
 * This interface can be implemented by a class which shall be notified by an input gate when one of the its connected
 * input channels has at least one record available for reading.
 * 
 * @param <T>
 *        the type of record transported through the corresponding input gate
 */
public interface RecordAvailabilityListener<T extends IOReadableWritable> {

	/**
	 * This method is called by an input gate when one of its connected input channels has at least one record available
	 * for reading.
	 * 
	 * @param inputGate
	 *        the input gate which has at least one record available
	 */
	void reportRecordAvailability(InputGate<T> inputGate);
}
