/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.specification.dsl;

import org.junit.gen5.engine.DiscoverySelector;
import org.junit.gen5.engine.specification.UniqueIdSelector;

/**
 * @since 5.0
 */
public class UniqueIdSelectorBuilder {
	public static DiscoverySelector byUniqueId(String uniqueId) {
		return new UniqueIdSelector(uniqueId);
	}
}
