/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.junit5.descriptor;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

import org.junit.gen5.api.extension.ExtensionContext;
import org.junit.gen5.api.extension.TestExtensionContext;

final class MethodBasedTestExtensionContext extends AbstractExtensionContext implements TestExtensionContext {

	private final MethodTestDescriptor testDescriptor;
	private final Object testInstance;

	public MethodBasedTestExtensionContext(ExtensionContext parent, MethodTestDescriptor testDescriptor,
			Object testInstance) {
		super(parent);
		this.testDescriptor = testDescriptor;
		this.testInstance = testInstance;
	}

	@Override
	public Method getTestMethod() {
		return testDescriptor.getTestMethod();
	}

	@Override
	public Object getTestInstance() {
		return testInstance;
	}

	@Override
	public Class<?> getTestClass() {
		return testDescriptor.getTestClass();
	}

	@Override
	public String getDisplayName() {
		return testDescriptor.getDisplayName();
	}

	@Override
	public AnnotatedElement getElement() {
		return getTestMethod();
	}
}
