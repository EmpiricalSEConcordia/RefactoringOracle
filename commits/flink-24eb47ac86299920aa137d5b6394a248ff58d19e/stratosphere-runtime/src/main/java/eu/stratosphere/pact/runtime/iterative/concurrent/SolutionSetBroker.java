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

package eu.stratosphere.pact.runtime.iterative.concurrent;

import eu.stratosphere.pact.runtime.hash.CompactingHashTable;

/**
 * Used to hand over the hash-join from the iteration head to the solution-set match.
 */
public class SolutionSetBroker extends Broker<CompactingHashTable<?>> {

	/**
	 * Singleton instance
	 */
	private static final SolutionSetBroker INSTANCE = new SolutionSetBroker();

	/**
	 * Retrieve the singleton instance.
	 */
	public static Broker<CompactingHashTable<?>> instance() {
		return INSTANCE;
	}
	
	private SolutionSetBroker() {}
}
