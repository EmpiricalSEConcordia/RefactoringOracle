/***********************************************************************************************************************
 *
 * Copyright (C) 2012 by the Stratosphere project (http://stratosphere.eu)
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
package eu.stratosphere.pact.test.iterative.nephele.customdanglingpagerank.types;

import eu.stratosphere.api.typeutils.TypeComparatorFactory;
import eu.stratosphere.configuration.Configuration;

/**
 *
 */
public final class VertexWithRankComparatorFactory implements TypeComparatorFactory<VertexWithRank> {
	
	@Override
	public void writeParametersToConfig(Configuration config) {}

	@Override
	public void readParametersFromConfig(Configuration config, ClassLoader cl) throws ClassNotFoundException {}

	@Override
	public VertexWithRankComparator createComparator() {
		return new VertexWithRankComparator();
	}
}
