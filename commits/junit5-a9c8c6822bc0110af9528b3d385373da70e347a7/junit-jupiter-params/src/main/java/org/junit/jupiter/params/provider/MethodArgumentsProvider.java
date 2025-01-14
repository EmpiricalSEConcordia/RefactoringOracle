/*
 * Copyright 2015-2017 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.jupiter.params.provider;

import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ContainerExtensionContext;
import org.junit.jupiter.params.support.AnnotationInitialized;
import org.junit.platform.commons.JUnitException;
import org.junit.platform.commons.util.PreconditionViolationException;
import org.junit.platform.commons.util.ReflectionUtils;

class MethodArgumentsProvider implements ArgumentsProvider, AnnotationInitialized<MethodSource> {

	private String[] methodNames;

	@Override
	public void initialize(MethodSource annotation) {
		methodNames = annotation.names();
	}

	@Override
	public Stream<Arguments> arguments(ContainerExtensionContext context) {
		Class<?> testClass = context.getTestClass() //
				.orElseThrow(() -> new JUnitException("Cannot invoke method without test class"));
		// @formatter:off
		return Arrays.stream(methodNames)
				.map(methodName -> ReflectionUtils.findMethod(testClass, methodName) //
                        .orElseThrow(() -> new JUnitException("Could not find method: " + methodName)))
				.map(method -> ReflectionUtils.invokeMethod(method, null))
				.flatMap(MethodArgumentsProvider::toStream)
				.map(MethodArgumentsProvider::toArguments);
		// @formatter:on
	}

	private static Arguments toArguments(Object item) {
		if (item instanceof Arguments) {
			return (Arguments) item;
		}
		if (item instanceof Object[]) {
			return ObjectArrayArguments.create((Object[]) item);
		}
		return ObjectArrayArguments.create(item);
	}

	private static Stream<?> toStream(Object object) {
		// TODO Duplication with TestFactoryTestDescriptor
		if (object instanceof Stream) {
			return (Stream<?>) object;
		}
		if (object instanceof Collection) {
			return ((Collection<?>) object).stream();
		}
		if (object instanceof Iterable) {
			return stream(((Iterable<?>) object).spliterator(), false);
		}
		if (object instanceof Iterator) {
			return stream(spliteratorUnknownSize((Iterator<?>) object, ORDERED), false);
		}
		throw new PreconditionViolationException(
			"Cannot convert instance of " + object.getClass().getName() + " into a Stream: " + object);
	}

}
