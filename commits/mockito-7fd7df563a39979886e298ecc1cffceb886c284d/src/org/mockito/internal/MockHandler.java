/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import net.sf.cglib.proxy.MethodProxy;

import org.mockito.internal.configuration.Configuration;
import org.mockito.internal.creation.ClassNameFinder;
import org.mockito.internal.creation.MockAwareInterceptor;
import org.mockito.internal.invocation.AllInvocationsFinder;
import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.invocation.MatchersBinder;
import org.mockito.internal.progress.MockingProgress;
import org.mockito.internal.progress.OngoingStubbing;
import org.mockito.internal.progress.VerificationModeImpl;
import org.mockito.internal.stubbing.Answer;
import org.mockito.internal.stubbing.Stubber;
import org.mockito.internal.stubbing.Returns;
import org.mockito.internal.stubbing.VoidMethodStubbable;
import org.mockito.internal.stubbing.ThrowsException;
import org.mockito.internal.verification.MissingInvocationInOrderVerifier;
import org.mockito.internal.verification.MissingInvocationVerifier;
import org.mockito.internal.verification.NoMoreInvocationsVerifier;
import org.mockito.internal.verification.NumberOfInvocationsInOrderVerifier;
import org.mockito.internal.verification.NumberOfInvocationsVerifier;
import org.mockito.internal.verification.Verifier;
import org.mockito.internal.verification.VerifyingRecorder;

/**
 * Invocation handler set on mock objects.
 *
 * @param <T> type of mock object to handle
 */
public class MockHandler<T> implements MockAwareInterceptor<T> {

    private final VerifyingRecorder verifyingRecorder;
    private final Stubber stubber;
    private final MatchersBinder matchersBinder;
    private final MockingProgress mockingProgress;
    private final String mockName;

    private T mock;

    public MockHandler(String mockName, MockingProgress mockingProgress, MatchersBinder matchersBinder) {
        this.mockName = mockName;
        this.mockingProgress = mockingProgress;
        this.matchersBinder = matchersBinder;
        stubber = new Stubber(mockingProgress);

        verifyingRecorder = createRecorder();
    }

    public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        if (stubber.hasAnswerForVoidMethod()) {
            Invocation invocation = new Invocation(proxy, method, args, mockingProgress.nextSequenceNumber());
            InvocationMatcher invocationMatcher = matchersBinder.bindMatchers(invocation);
            stubber.addVoidMethodForStubbing(invocationMatcher);
            return null;
        }

        VerificationModeImpl verificationMode = mockingProgress.pullVerificationMode();
        mockingProgress.validateState();

        Invocation invocation = new Invocation(proxy, method, args, mockingProgress.nextSequenceNumber());
        InvocationMatcher invocationMatcher = matchersBinder.bindMatchers(invocation);

        if (verificationMode != null) {
            verifyingRecorder.verify(invocationMatcher, verificationMode);
            return Configuration.instance().getReturnValues().valueFor(invocationMatcher.getInvocation());
        }

        stubber.setInvocationForPotentialStubbing(invocationMatcher);
        verifyingRecorder.recordInvocation(invocationMatcher.getInvocation());

        mockingProgress.reportOngoingStubbing(new OngoingStubbingImpl());

        return stubber.resultFor(invocationMatcher.getInvocation());
    }

    public void verifyNoMoreInteractions() {
        verifyingRecorder.verify(VerificationModeImpl.noMoreInteractions());
    }

    public VoidMethodStubbable<T> voidMethodStubbable() {
        return new VoidMethodStubbableImpl();
    }

    public void setMock(T mock) {
        this.mock = mock;
    }

    public List<Invocation> getRegisteredInvocations() {
        return verifyingRecorder.getRegisteredInvocations();
    }

    public String getMockName() {
        if (mockName != null) {
            return mockName;
        } else {
            return toInstanceName(ClassNameFinder.classNameForMock(mock));
        }
    }

    //lower case first letter
    private String toInstanceName(String className) {
        return className.substring(0, 1).toLowerCase() + className.substring(1);
    }

    private VerifyingRecorder createRecorder() {
        List<Verifier> verifiers = Arrays.asList(
                new MissingInvocationInOrderVerifier(),
                new NumberOfInvocationsInOrderVerifier(),
                new MissingInvocationVerifier(),
                new NumberOfInvocationsVerifier(),
                new NoMoreInvocationsVerifier());
        return new VerifyingRecorder(new AllInvocationsFinder(), verifiers);
    }

    private final class VoidMethodStubbableImpl implements VoidMethodStubbable<T> {
        public VoidMethodStubbable<T> toThrow(Throwable throwable) {
            stubber.addAnswerForVoidMethod(new ThrowsException(throwable));
            return this;
        }

        public VoidMethodStubbable<T> toReturn() {
            stubber.addAnswerForVoidMethod(new Returns());
            return this;
        }

        public VoidMethodStubbable<T> toAnswer(Answer<?> answer) {
            stubber.addAnswerForVoidMethod(answer);
            return this;
        }

        public T on() {
            return mock;
        }
    }

    private class OngoingStubbingImpl implements OngoingStubbing<T> {
        public OngoingStubbing<T> toReturn(Object value) {
            verifyingRecorder.eraseLastInvocation();
            stubber.addAnswer(new Returns(value));
            return new ConsecutiveStubbing();
        }

        public OngoingStubbing<T> toThrow(Throwable throwable) {
            verifyingRecorder.eraseLastInvocation();
            stubber.addAnswer(new ThrowsException(throwable));
            return new ConsecutiveStubbing();
        }

        public OngoingStubbing<T> toAnswer(Answer<?> answer) {
            verifyingRecorder.eraseLastInvocation();
            stubber.addAnswer(answer);
            return new ConsecutiveStubbing();
        }
    }

    private class ConsecutiveStubbing implements OngoingStubbing<T> {
        public OngoingStubbing<T> toReturn(Object value) {
            stubber.addConsecutiveAnswer(new Returns(value));
            return this;
        }

        public OngoingStubbing<T> toThrow(Throwable throwable) {
            stubber.addConsecutiveAnswer(new ThrowsException(throwable));
            return this;
        }

        public OngoingStubbing<T> toAnswer(Answer<?> answer) {
            stubber.addConsecutiveAnswer(answer);
            return this;
        }
    }
}