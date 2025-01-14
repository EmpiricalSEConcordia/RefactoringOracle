/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.vintage.engine.samples.junit4;

import org.junit.gen5.junit4.runner.JUnitPlatform;
import org.junit.gen5.junit4.runner.SelectClasses;
import org.junit.runner.RunWith;

/**
 * @since 5.0
 */
@RunWith(JUnitPlatform.class)
@SelectClasses(PlainJUnit4TestCaseWithSingleTestWhichFails.class)
public class TestCaseRunWithJUnitPlatformRunner {
}
