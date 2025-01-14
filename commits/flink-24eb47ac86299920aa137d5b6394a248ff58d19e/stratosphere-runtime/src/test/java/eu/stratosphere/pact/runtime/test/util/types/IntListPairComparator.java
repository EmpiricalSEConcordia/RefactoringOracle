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
package eu.stratosphere.pact.runtime.test.util.types;

import org.apache.flink.api.common.typeutils.TypePairComparator;

public class IntListPairComparator extends TypePairComparator<IntList, IntList> {
	
	private int key;

	@Override
	public void setReference(IntList reference) {
		this.key = reference.getKey();
	}

	@Override
	public boolean equalToReference(IntList candidate) {
		return this.key == candidate.getKey();
	}

	@Override
	public int compareToReference(IntList candidate) {
		return candidate.getKey() - this.key;
	}

}
