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

package eu.stratosphere.nephele.metrics.util;

import eu.stratosphere.nephele.metrics.MetricsRecord;

/**
 * This is base class for all metrics
 */
public abstract class MetricsBase {
	public static final String NO_DESCRIPTION = "NoDescription";

	final private String name;

	final private String description;

	protected MetricsBase(final String nam) {
		name = nam;
		description = NO_DESCRIPTION;
	}

	protected MetricsBase(final String nam, final String desc) {
		name = nam;
		description = desc;
	}

	public abstract void pushMetric(final MetricsRecord mr);

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	};

}
