/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.junit5;

import static org.assertj.core.api.Assertions.allOf;
import static org.junit.gen5.api.Assertions.assertEquals;
import static org.junit.gen5.engine.ExecutionEventConditions.*;
import static org.junit.gen5.engine.TestExecutionResultConditions.*;
import static org.junit.gen5.engine.specification.dsl.DiscoveryRequestBuilder.request;
import static org.junit.gen5.engine.specification.dsl.MethodSelectorBuilder.byMethod;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.gen5.api.*;
import org.junit.gen5.engine.DiscoveryRequest;
import org.junit.gen5.engine.ExecutionEventRecorder;
import org.opentest4j.AssertionFailedError;

/**
 * Integration tests that verify correct exception handling in the {@link JUnit5TestEngine}.
 */
public class ExceptionHandlingTests extends AbstractJUnit5TestEngineTests {

	@Test
	public void failureInTestMethodIsRegistered() throws NoSuchMethodException {
		Method method = FailureTestCase.class.getDeclaredMethod("failingTest");
		DiscoveryRequest request = request().select(byMethod(FailureTestCase.class, method)).build();

		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertEquals(1L, eventRecorder.getTestStartedCount(), "# tests started");
		assertEquals(1L, eventRecorder.getTestFailedCount(), "# tests failed");

		assertRecordedExecutionEventsContainsExactly(eventRecorder.getFailedTestFinishedEvents(), //
			event(test("failingTest"),
				finishedWithFailure(allOf(isA(AssertionFailedError.class), message("always fails")))));
	}

	@Test
	public void uncheckedExceptionInTestMethodIsRegistered() throws NoSuchMethodException {
		Method method = FailureTestCase.class.getDeclaredMethod("testWithUncheckedException");
		DiscoveryRequest request = request().select(byMethod(FailureTestCase.class, method)).build();

		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertEquals(1L, eventRecorder.getTestStartedCount(), "# tests started");
		assertEquals(1L, eventRecorder.getTestFailedCount(), "# tests failed");

		assertRecordedExecutionEventsContainsExactly(eventRecorder.getFailedTestFinishedEvents(), //
			event(test("testWithUncheckedException"),
				finishedWithFailure(allOf(isA(RuntimeException.class), message("unchecked")))));
	}

	@Test
	public void checkedExceptionInTestMethodIsRegistered() throws NoSuchMethodException {
		Method method = FailureTestCase.class.getDeclaredMethod("testWithCheckedException");
		DiscoveryRequest request = request().select(byMethod(FailureTestCase.class, method)).build();

		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertEquals(1L, eventRecorder.getTestStartedCount(), "# tests started");
		assertEquals(1L, eventRecorder.getTestFailedCount(), "# tests failed");

		assertRecordedExecutionEventsContainsExactly(eventRecorder.getFailedTestFinishedEvents(), //
			event(test("testWithCheckedException"),
				finishedWithFailure(allOf(isA(IOException.class), message("checked")))));
	}

	@Test
	public void checkedExceptionInBeforeEachIsRegistered() throws NoSuchMethodException {
		Method method = FailureTestCase.class.getDeclaredMethod("succeedingTest");
		DiscoveryRequest request = request().select(byMethod(FailureTestCase.class, method)).build();

		FailureTestCase.exceptionToThrowInBeforeEach = Optional.of(new IOException("checked"));

		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertEquals(1L, eventRecorder.getTestStartedCount(), "# tests started");
		assertEquals(1L, eventRecorder.getTestFailedCount(), "# tests failed");

		assertRecordedExecutionEventsContainsExactly(eventRecorder.getFailedTestFinishedEvents(),
			event(test("succeedingTest"), finishedWithFailure(allOf(isA(IOException.class), message("checked")))));
	}

	@Test
	public void checkedExceptionInAfterEachIsRegistered() throws NoSuchMethodException {
		Method method = FailureTestCase.class.getDeclaredMethod("succeedingTest");
		DiscoveryRequest request = request().select(byMethod(FailureTestCase.class, method)).build();

		FailureTestCase.exceptionToThrowInAfterEach = Optional.of(new IOException("checked"));

		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertEquals(1L, eventRecorder.getTestStartedCount(), "# tests started");
		assertEquals(1L, eventRecorder.getTestFailedCount(), "# tests failed");

		assertRecordedExecutionEventsContainsExactly(eventRecorder.getFailedTestFinishedEvents(),
			event(test("succeedingTest"), finishedWithFailure(allOf(isA(IOException.class), message("checked")))));
	}

	@Test
	public void checkedExceptionInAfterEachIsSuppressedByExceptionInTest() throws NoSuchMethodException {
		Method method = FailureTestCase.class.getDeclaredMethod("testWithUncheckedException");
		DiscoveryRequest request = request().select(byMethod(FailureTestCase.class, method)).build();

		FailureTestCase.exceptionToThrowInAfterEach = Optional.of(new IOException("checked"));

		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertRecordedExecutionEventsContainsExactly(eventRecorder.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(FailureTestCase.class), started()), //
			event(test("testWithUncheckedException"), started()), //
			event(test("testWithUncheckedException"),
				finishedWithFailure(allOf( //
					isA(RuntimeException.class), //
					message("unchecked"), //
					suppressed(0, allOf(isA(IOException.class), message("checked")))))), //
			event(container(FailureTestCase.class), finishedSuccessfully()), //
			event(engine(), finishedSuccessfully()));
	}

	@Test
	public void checkedExceptionInBeforeAllIsRegistered() throws NoSuchMethodException {
		Method method = FailureTestCase.class.getDeclaredMethod("succeedingTest");
		DiscoveryRequest request = request().select(byMethod(FailureTestCase.class, method)).build();

		FailureTestCase.exceptionToThrowInBeforeAll = Optional.of(new IOException("checked"));

		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertRecordedExecutionEventsContainsExactly(eventRecorder.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(FailureTestCase.class), started()), //
			event(container(FailureTestCase.class),
				finishedWithFailure(allOf(isA(IOException.class), message("checked")))), //
			event(engine(), finishedSuccessfully()));
	}

	@Test
	public void checkedExceptionInAfterAllIsRegistered() throws NoSuchMethodException {
		Method method = FailureTestCase.class.getDeclaredMethod("succeedingTest");
		DiscoveryRequest request = request().select(byMethod(FailureTestCase.class, method)).build();

		FailureTestCase.exceptionToThrowInAfterAll = Optional.of(new IOException("checked"));

		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertRecordedExecutionEventsContainsExactly(eventRecorder.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(FailureTestCase.class), started()), //
			event(test("succeedingTest"), started()), //
			event(test("succeedingTest"), finishedSuccessfully()), //
			event(container(FailureTestCase.class),
				finishedWithFailure(allOf(isA(IOException.class), message("checked")))), //
			event(engine(), finishedSuccessfully()));
	}

	@AfterEach
	public void cleanUpExceptions() {
		FailureTestCase.exceptionToThrowInBeforeAll = Optional.empty();
		FailureTestCase.exceptionToThrowInAfterAll = Optional.empty();
		FailureTestCase.exceptionToThrowInBeforeEach = Optional.empty();
		FailureTestCase.exceptionToThrowInAfterEach = Optional.empty();
	}

	private static class FailureTestCase {

		static Optional<Throwable> exceptionToThrowInBeforeAll = Optional.empty();
		static Optional<Throwable> exceptionToThrowInAfterAll = Optional.empty();
		static Optional<Throwable> exceptionToThrowInBeforeEach = Optional.empty();
		static Optional<Throwable> exceptionToThrowInAfterEach = Optional.empty();

		@BeforeAll
		static void beforeAll() throws Throwable {
			if (exceptionToThrowInBeforeAll.isPresent())
				throw exceptionToThrowInBeforeAll.get();
		}

		@AfterAll
		static void afterAll() throws Throwable {
			if (exceptionToThrowInAfterAll.isPresent())
				throw exceptionToThrowInAfterAll.get();
		}

		@BeforeEach
		void beforeEach() throws Throwable {
			if (exceptionToThrowInBeforeEach.isPresent())
				throw exceptionToThrowInBeforeEach.get();
		}

		@AfterEach
		void afterEach() throws Throwable {
			if (exceptionToThrowInAfterEach.isPresent())
				throw exceptionToThrowInAfterEach.get();
		}

		@Test
		void succeedingTest() {
		}

		@Test
		void failingTest() {
			Assertions.fail("always fails");
		}

		@Test
		void testWithUncheckedException() {
			throw new RuntimeException("unchecked");
		}

		@Test
		void testWithCheckedException() throws IOException {
			throw new IOException("checked");
		}

	}

}
