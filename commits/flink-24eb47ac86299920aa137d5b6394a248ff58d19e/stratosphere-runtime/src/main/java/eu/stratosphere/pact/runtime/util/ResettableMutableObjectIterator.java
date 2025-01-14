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

package eu.stratosphere.pact.runtime.util;

import java.io.IOException;

import org.apache.flink.util.MutableObjectIterator;

/**
 * The resettable iterator is a specialization of the iterator, allowing to reset the iterator and re-retrieve elements.
 * Whether the iterator is completely reset or only partially depends on the actual implementation.
 */
public interface ResettableMutableObjectIterator<E> extends MutableObjectIterator<E> {
	
	/**
	 * Resets the iterator.
	 * 
	 * @throws IOException May be thrown when the serialization into buffers or the spilling to secondary
	 *                     storage fails.
	 */
	public void reset() throws IOException;
	
}
