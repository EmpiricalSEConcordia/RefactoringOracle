/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.junit5.execution;

import java.util.List;

import org.junit.gen5.api.extension.TestExtensionContext;

@FunctionalInterface
public interface AfterEachCallback {

	void afterEach(TestExtensionContext testExtensionContext, Object testInstance, List<Throwable> throwablesCollector)
			throws Throwable;

}
