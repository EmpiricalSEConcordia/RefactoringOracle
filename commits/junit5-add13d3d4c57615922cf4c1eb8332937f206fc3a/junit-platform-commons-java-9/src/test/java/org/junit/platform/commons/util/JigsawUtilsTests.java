/*
 * Copyright 2015-2017 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.commons.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.engine.discovery.predicates.IsScannableTestClass;

class JigsawUtilsTests {

	private final ClassFilter testClasses = ClassFilter.of(new IsScannableTestClass());

	@Test
	void find() {
		List<Class<?>> classes = JigsawUtils.findAllClassesInModule("java.base", testClasses);
		assertTrue(classes.isEmpty());
	}
}
