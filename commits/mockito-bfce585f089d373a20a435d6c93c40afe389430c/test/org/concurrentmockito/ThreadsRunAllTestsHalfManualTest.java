/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.concurrentmockito;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.mockito.MockitoTest;
import org.mockito.exceptions.ReporterTest;
import org.mockito.exceptions.base.MockitoAssertionErrorTest;
import org.mockito.exceptions.base.MockitoExceptionTest;
import org.mockito.internal.AllInvocationsFinderTest;
import org.mockito.internal.MockHandlerTest;
import org.mockito.internal.creation.jmock.ClassImposterizerTest;
import org.mockito.internal.invocation.InvocationMatcherTest;
import org.mockito.internal.invocation.InvocationTest;
import org.mockito.internal.invocation.InvocationsFinderTest;
import org.mockito.internal.matchers.EqualsTest;
import org.mockito.internal.progress.MockingProgressImplTest;
import org.mockito.internal.progress.TimesTest;
import org.mockito.internal.returnvalues.EmptyReturnValuesTest;
import org.mockito.internal.util.ListUtilTest;
import org.mockito.internal.util.MockUtilTest;
import org.mockito.internal.verification.RegisteredInvocationsTest;
import org.mockito.internal.verification.checkers.MissingInvocationCheckerTest;
import org.mockito.internal.verification.checkers.MissingInvocationInOrderCheckerTest;
import org.mockito.internal.verification.checkers.NumberOfInvocationsCheckerTest;
import org.mockito.internal.verification.checkers.NumberOfInvocationsInOrderCheckerTest;
import org.mockitousage.ReplacingObjectMethodsTest;
import org.mockitousage.UsingVarargsTest;
import org.mockitousage.examples.configure.withbaseclass.ConfiguringDefaultReturnValuesUsingBaseClassTest;
import org.mockitousage.examples.configure.withrunner.ConfiguringDefaultReturnValuesUsingRunnerTest;
import org.mockitousage.examples.configure.withstaticutility.ConfiguringSelectedMocksToReturnFakesTest;
import org.mockitousage.examples.use.ExampleTest;
import org.mockitousage.matchers.ComparableMatchersTest;
import org.mockitousage.matchers.CustomMatchersTest;
import org.mockitousage.matchers.InvalidUseOfMatchersTest;
import org.mockitousage.matchers.MatchersTest;
import org.mockitousage.matchers.MatchersToStringTest;
import org.mockitousage.matchers.VerificationAndStubbingUsingMatchersTest;
import org.mockitousage.misuse.InvalidStateDetectionTest;
import org.mockitousage.misuse.InvalidUsageTest;
import org.mockitousage.puzzlers.BridgeMethodPuzzleTest;
import org.mockitousage.puzzlers.OverloadingPuzzleTest;
import org.mockitousage.reset.ResetTest;
import org.mockitousage.stacktrace.ClickableStackTracesTest;
import org.mockitousage.stacktrace.PointingStackTraceToActualInvocationTest;
import org.mockitousage.stacktrace.StackTraceFilteringTest;
import org.mockitousage.stubbing.BasicStubbingTest;
import org.mockitousage.stubbing.ReturningDefaultValuesTest;
import org.mockitousage.stubbing.StubbingWithThrowablesTest;
import org.mockitousage.verification.AtMostXVerificationTest;
import org.mockitousage.verification.BasicVerificationInOrderTest;
import org.mockitousage.verification.BasicVerificationTest;
import org.mockitousage.verification.DescriptiveMessagesOnVerificationInOrderErrorsTest;
import org.mockitousage.verification.DescriptiveMessagesWhenTimesXVerificationFailsTest;
import org.mockitousage.verification.DescriptiveMessagesWhenVerificationFailsTest;
import org.mockitousage.verification.ExactNumberOfTimesVerificationTest;
import org.mockitousage.verification.NoMoreInteractionsVerificationTest;
import org.mockitousage.verification.RelaxedVerificationInOrderTest;
import org.mockitousage.verification.SelectedMocksInOrderVerificationTest;
import org.mockitousage.verification.VerificationInOrderMixedWithOrdiraryVerificationTest;
import org.mockitousage.verification.VerificationInOrderTest;
import org.mockitousage.verification.VerificationOnMultipleMocksUsingMatchersTest;
import org.mockitousage.verification.VerificationUsingMatchersTest;
import org.mockitoutil.TestBase;

public class ThreadsRunAllTestsHalfManualTest extends TestBase {
    
    private static class AllTestsRunner extends Thread {
        
        private boolean failed;

        public void run() {
            Result result = JUnitCore.runClasses(
                    ConfiguringDefaultReturnValuesUsingBaseClassTest.class,
                    ConfiguringDefaultReturnValuesUsingRunnerTest.class,
                    ConfiguringSelectedMocksToReturnFakesTest.class,
                    EqualsTest.class,
                    ListUtilTest.class,
                    MockingProgressImplTest.class,
                    TimesTest.class,
                    MockHandlerTest.class,
                    AllInvocationsFinderTest.class,
                    EmptyReturnValuesTest.class,
                    NumberOfInvocationsCheckerTest.class,
                    RegisteredInvocationsTest.class,
                    MissingInvocationCheckerTest.class,
                    NumberOfInvocationsInOrderCheckerTest.class,
                    MissingInvocationInOrderCheckerTest.class,
                    ClassImposterizerTest.class,
                    InvocationMatcherTest.class,
                    InvocationsFinderTest.class,
                    InvocationTest.class,
                    MockitoTest.class,
                    MockUtilTest.class,
                    ReporterTest.class,
                    MockitoAssertionErrorTest.class,
                    MockitoExceptionTest.class,
                    StackTraceFilteringTest.class,
                    BridgeMethodPuzzleTest.class,
                    OverloadingPuzzleTest.class,
                    InvalidUsageTest.class,
                    UsingVarargsTest.class,
                    CustomMatchersTest.class,
                    ComparableMatchersTest.class,
                    InvalidUseOfMatchersTest.class,
                    MatchersTest.class,
                    MatchersToStringTest.class,
                    VerificationAndStubbingUsingMatchersTest.class,
                    BasicStubbingTest.class,
                    ReturningDefaultValuesTest.class,
                    StubbingWithThrowablesTest.class,
                    AtMostXVerificationTest.class,
                    BasicVerificationTest.class,
                    ExactNumberOfTimesVerificationTest.class,
                    VerificationInOrderTest.class,
                    NoMoreInteractionsVerificationTest.class,
                    SelectedMocksInOrderVerificationTest.class,
                    VerificationOnMultipleMocksUsingMatchersTest.class,
                    VerificationUsingMatchersTest.class,
                    RelaxedVerificationInOrderTest.class,
                    DescriptiveMessagesWhenVerificationFailsTest.class,
                    DescriptiveMessagesWhenTimesXVerificationFailsTest.class,
                    BasicVerificationInOrderTest.class,
                    VerificationInOrderMixedWithOrdiraryVerificationTest.class,
                    DescriptiveMessagesOnVerificationInOrderErrorsTest.class,
                    InvalidStateDetectionTest.class,
                    ReplacingObjectMethodsTest.class,
                    ClickableStackTracesTest.class,
                    ExampleTest.class,
                    PointingStackTraceToActualInvocationTest.class,
                    VerificationInOrderFromMultipleThreadsTest.class,
                    ResetTest.class
                );
                
                if (!result.wasSuccessful()) {
                    System.err.println("Thread[" + Thread.currentThread().getId() + "]: error!");
                    List<Failure> failures = result.getFailures();
                    System.err.println(failures.size());
                    for (Failure failure : failures) {
                        System.err.println(failure.getTrace());
                        failed = true;
                    }
                }
        }

        public boolean isFailed() {
            return failed;
        }
    }
    
    @Test
    public void shouldRunInMultipleThreads() throws Exception {
        //this test ALWAYS fails if there is a single failing unit
        assertFalse("Run in multiple thread failed", runInMultipleThreads(3));
    }
    
    public static boolean runInMultipleThreads(int numberOfThreads) throws Exception {
        List<AllTestsRunner> threads = new LinkedList<AllTestsRunner>();
        for (int i = 1; i <= numberOfThreads; i++) {
            threads.add(new AllTestsRunner());
        }

        for (Thread t : threads) {
            t.start();
        }

        boolean failed = false;
        for (AllTestsRunner t : threads) {
            t.join();
            failed = failed ? true : t.isFailed();
        }
        
        return failed;
    }
    
    public static void main(String[] args) throws Exception {
        int numberOfThreads = 20; 
        long before = System.currentTimeMillis();
        runInMultipleThreads(numberOfThreads);
        long after = System.currentTimeMillis();
        long executionTime = (after-before)/1000;
        System.out.println("Finished tests in " + numberOfThreads + " threads in " + executionTime + " seconds.");
    }
}