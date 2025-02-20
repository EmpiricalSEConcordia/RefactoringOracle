/***********************************************************************************************************************
 * Copyright (C) 2010-2013 by the Stratosphere project (http://stratosphere.eu)
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

package eu.stratosphere.pact.runtime.task.util;


import java.io.IOException;

import eu.stratosphere.api.functions.GenericJoiner;
import eu.stratosphere.nephele.services.memorymanager.MemoryAllocationException;
import eu.stratosphere.util.Collector;

/**
 * Interface of an iterator that performs the logic of a match task. The iterator follows the
 * <i>open/next/close</i> principle. The <i>next</i> logic here calls the match stub with all
 * value pairs that share the same key.
 *
 * @author Erik Nijkamp
 * @author Stephan Ewen
 */
public interface MatchTaskIterator<V1, V2, O>
{
	/**
	 * General-purpose open method. Initializes the internal strategy (for example triggers the
	 * sorting of the inputs or starts building hash tables).
	 * 
	 * @throws IOException Thrown, if an I/O error occurred while preparing the data. An example is a failing
	 *                     external sort.
	 * @throws MemoryAllocationException Thrown, if the internal strategy could not allocate the memory it needs.
	 * @throws InterruptedException Thrown, if the thread was interrupted during the initialization process. 
	 */
	void open() throws IOException, MemoryAllocationException, InterruptedException;

	/**
	 * General-purpose close method. Works after the principle of best effort. The internal structures are
	 * released, but errors that occur on the way are not reported.
	 */
	void close();

	/**
	 * Moves the internal pointer to the next key that both inputs share. It calls the match stub with the
	 * cross product of all values that share the same key.
	 * 
	 * @param matchFunction The match stub containing the match function which is called with the keys.
	 * @param collector The collector to pass the match function.
	 * @return True, if a next key exists, false if no more keys exist.
	 * @throws Exception Exceptions from the user code are forwarded.
	 */
	boolean callWithNextKey(GenericJoiner<V1, V2, O> matchFunction, Collector<O> collector) throws Exception;
	
	/**
	 * Aborts the matching process. This extra abort method is supplied, because a significant time may pass while
	 * calling the match stub with the cross product of all values that share the same key. A call to this abort
	 * method signals an interrupt to that procedure.
	 */
	void abort();
}
