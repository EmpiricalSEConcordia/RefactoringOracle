/***********************************************************************************************************************
 *
 * Copyright (C) 2010-2014 by the Apache Flink project (http://flink.incubator.apache.org)
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
package eu.stratosphere.pact.runtime.hash;

import java.io.IOException;
import java.util.List;

import org.apache.flink.api.common.typeutils.TypeComparator;
import org.apache.flink.api.common.typeutils.TypePairComparator;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.core.memory.MemorySegment;
import org.apache.flink.util.MutableObjectIterator;

public abstract class AbstractMutableHashTable<T> {
	
	/**
	 * The utilities to serialize the build side data types.
	 */
	protected final TypeSerializer<T> buildSideSerializer;
	
	/**
	 * The utilities to hash and compare the build side data types.
	 */
	protected final TypeComparator<T> buildSideComparator;
	
	
	public AbstractMutableHashTable (TypeSerializer<T> buildSideSerializer, TypeComparator<T> buildSideComparator) {
		this.buildSideSerializer = buildSideSerializer;
		this.buildSideComparator = buildSideComparator;
	}
	
	public TypeSerializer<T> getBuildSideSerializer() {
		return this.buildSideSerializer;
	}
	
	public TypeComparator<T> getBuildSideComparator() {
		return this.buildSideComparator;
	}
	
	// ------------- Life-cycle functions -------------
	
	public abstract void open();
	
	public abstract void close();
	
	public abstract void abort();
	
	public abstract void buildTable(final MutableObjectIterator<T> input) throws IOException;
	
	public abstract List<MemorySegment> getFreeMemory();
	
	// ------------- Modifier -------------
	
	public abstract void insert(T record) throws IOException;
	
	public abstract void insertOrReplaceRecord(T record, T tempHolder) throws IOException;
	
	// ------------- Accessors -------------
	
	public abstract MutableObjectIterator<T> getEntryIterator();
	
	/**
	 * 
	 * @param probeSideComparator
	 * @param pairComparator
	 * @param <PT> The type of the probe side.
	 * @return
	 */
	public abstract <PT> AbstractHashTableProber<PT, T> getProber(TypeComparator<PT> probeSideComparator, TypePairComparator<PT, T> pairComparator);

}
