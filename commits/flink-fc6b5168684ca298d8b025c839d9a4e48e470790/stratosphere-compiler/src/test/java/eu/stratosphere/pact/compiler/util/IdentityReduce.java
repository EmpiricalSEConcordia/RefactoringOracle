/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
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

package eu.stratosphere.pact.compiler.util;

import java.io.Serializable;
import java.util.Iterator;

import eu.stratosphere.api.functions.StubAnnotation.ConstantFieldsExcept;
import eu.stratosphere.pact.common.stubs.ReduceStub;
import eu.stratosphere.types.PactRecord;
import eu.stratosphere.util.Collector;

@ConstantFieldsExcept({})
public final class IdentityReduce extends ReduceStub implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@Override
	public void reduce(Iterator<PactRecord> records, Collector<PactRecord> out) throws Exception {
		while (records.hasNext()) {
			out.collect(records.next());
		}
	}
}