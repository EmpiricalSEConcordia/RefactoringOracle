/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.jupiter.engine.extension;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.ClassSelector.selectClass;
import static org.junit.platform.launcher.core.TestDiscoveryRequestBuilder.request;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ContainerExtensionContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.engine.AbstractJupiterTestEngineTests;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.test.event.ExecutionEventRecorder;
import org.junit.platform.launcher.TestDiscoveryRequest;

/**
 * Integration tests that verify support for {@link BeforeAll}, {@link AfterAll},
 * {@link BeforeAllCallback}, and {@link AfterAllCallback} in the {@link JupiterTestEngine}.
 *
 * @since 5.0
 */
public class BeforeAndAfterAllTests extends AbstractJupiterTestEngineTests {

	private static final List<String> callSequence = new ArrayList<>();

	@Test
	void beforeAllAndAfterAllCallbacks() {
		TestDiscoveryRequest request = request().selectors(selectClass(InstancePerMethodTestCase.class)).build();
		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertEquals(1, eventRecorder.getTestStartedCount(), "# tests started");
		assertEquals(1, eventRecorder.getTestSuccessfulCount(), "# tests succeeded");

		// @formatter:off
		assertEquals(asList(
			"fooBeforeAll",
			"barBeforeAll",
				"beforeAllMethod",
					"firstTest",
				"afterAllMethod",
			"barAfterAll",
			"fooAfterAll"
		), callSequence, "wrong call sequence");
		// @formatter:on
	}

	// -------------------------------------------------------------------------

	@ExtendWith({ FooClassLevelCallbacks.class, BarClassLevelCallbacks.class })
	private static class InstancePerMethodTestCase {

		@BeforeAll
		static void beforeAll() {
			callSequence.add("beforeAllMethod");
		}

		@AfterAll
		static void afterAll() {
			callSequence.add("afterAllMethod");
		}

		@Test
		void firstTest() {
			callSequence.add("firstTest");
		}
	}

	private static class FooClassLevelCallbacks implements BeforeAllCallback, AfterAllCallback {

		@Override
		public void beforeAll(ContainerExtensionContext testExecutionContext) {
			callSequence.add("fooBeforeAll");
		}

		@Override
		public void afterAll(ContainerExtensionContext testExecutionContext) {
			callSequence.add("fooAfterAll");
		}
	}

	private static class BarClassLevelCallbacks implements BeforeAllCallback, AfterAllCallback {

		@Override
		public void beforeAll(ContainerExtensionContext testExecutionContext) {
			callSequence.add("barBeforeAll");
		}

		@Override
		public void afterAll(ContainerExtensionContext testExecutionContext) {
			callSequence.add("barAfterAll");
		}
	}

}
