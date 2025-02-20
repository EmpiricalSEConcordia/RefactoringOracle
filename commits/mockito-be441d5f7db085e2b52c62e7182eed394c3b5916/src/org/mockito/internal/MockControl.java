/*
 * Copyright (c) 2007 Mockito contributors 
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal;

import java.lang.reflect.Method;
import java.util.*;

import org.mockito.exceptions.*;
import org.mockito.internal.matchers.*;

public class MockControl<T> implements MockAwareInvocationHandler<T>, MockitoExpectation<T>, VoidMethodExpectation<T>, MethodSelector<T> {

    private final MockitoBehavior<T> behavior = new MockitoBehavior<T>();
    private final MockitoState mockitoState;
    private final LastArguments lastArguments;

    private Throwable throwableToBeSetOnVoidMethod;
    
    public MockControl(MockitoState mockitoState, LastArguments lastArguments) {
        this.mockitoState = mockitoState;
        this.lastArguments = lastArguments;
    }
    
    /**
     * if user passed bare arguments then create EqualsMatcher for every argument
     */
    private List<IArgumentMatcher> createEqualsMatchers(Invocation invocation,
            List<IArgumentMatcher> matchers) {
        if (matchers != null) {
            return matchers;
        }
        List<IArgumentMatcher> result = new ArrayList<IArgumentMatcher>();
        for (Object argument : invocation.getArguments()) {
            result.add(new Equals(argument));
        }
        return result;
    }

    private void validateMatchers(Invocation invocation, List<IArgumentMatcher> matchers) throws InvalidUseOfMatchersException {
        if (matchers != null) {
            if (matchers.size() != invocation.getArguments().length) {
                throw new InvalidUseOfMatchersException(
                        + invocation.getArguments().length
                        + " matchers expected, " + matchers.size()
                        + " recorded.");
            }
        }
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (throwableToBeSetOnVoidMethod != null) {
            Invocation invocation = new Invocation(proxy, method, args, mockitoState.nextSequenceNumber());
            ExpectedInvocation invocationWithMatchers = expectedInvocation(invocation);
            //TODO this is a bit dodgy, we should set result directly on behavior
            behavior.addInvocation(invocationWithMatchers);
            andThrows(throwableToBeSetOnVoidMethod);
            throwableToBeSetOnVoidMethod = null;
            return null;
        }
        
        VerifyingMode verifyingMode = mockitoState.pullVerifyingMode();
        mockitoState.validateState();
        
        Invocation invocation = new Invocation(proxy, method, args, mockitoState.nextSequenceNumber());
        ExpectedInvocation invocationWithMatchers = expectedInvocation(invocation);
        
        if (verifyingMode != null) {
            behavior.verify(invocationWithMatchers, verifyingMode);
            return ToTypeMappings.emptyReturnValueFor(method.getReturnType());
        } 
        
        mockitoState.reportControlForStubbing(this);

        behavior.addInvocation(invocationWithMatchers);
        
        if (throwableToBeSetOnVoidMethod != null) {
            andThrows(throwableToBeSetOnVoidMethod);
            throwableToBeSetOnVoidMethod = null;
            return null;
        }
        
        return behavior.resultFor(invocation);
    }

    private ExpectedInvocation expectedInvocation(Invocation invocation) {
        List<IArgumentMatcher> lastMatchers = lastArguments.pullMatchers();
        validateMatchers(invocation, lastMatchers);

        List<IArgumentMatcher> processedMatchers = createEqualsMatchers(invocation, lastMatchers);
        
        ExpectedInvocation invocationWithMatchers = new ExpectedInvocation(invocation, processedMatchers);
        return invocationWithMatchers;
    }

    public void verifyNoMoreInteractions() {
        behavior.verifyNoMoreInteractions();
    }
    
    public void verifyZeroInteractions() {
        behavior.verifyZeroInteractions();
    }

    public void andReturn(T value) {
        mockitoState.stubbingCompleted();
        behavior.addResult(Result.createReturnResult(value));
    }

    public void andThrows(Throwable throwable) {
        mockitoState.stubbingCompleted();
        validateThrowable(throwable);
        behavior.addResult(Result.createThrowResult(throwable));
    }
    
    private void validateThrowable(Throwable throwable) {
        if (throwable == null) {
            Exceptions.cannotStubWithNullThrowable();
        }

        if (throwable instanceof RuntimeException || throwable instanceof Error) {
            return;
        }
    
        if (!isValidCheckedException(throwable)) {
            Exceptions.checkedExceptionInvalid(throwable);
        }
    }

    private boolean isValidCheckedException(Throwable throwable) {
        //TODO move validation logic to behavior, so that we don't need to expose getInvocationForStubbing()
        Invocation lastInvocation = behavior.getInvocationForStubbing().getInvocation();

        Class<?>[] exceptions = lastInvocation.getMethod().getExceptionTypes();
        Class<?> throwableClass = throwable.getClass();
        for (Class<?> exception : exceptions) {
            if (exception.isAssignableFrom(throwableClass)) {
                return true;
            }
        }
        
        return false;
    }

    public MethodSelector<T> toThrow(Throwable throwable) {
        throwableToBeSetOnVoidMethod = throwable;
        return this;
    }

    public T on() {
        return (T) behavior.getMock();
    }

    public void setMock(T mock) {
        behavior.setMock(mock);
    }

    public List<Invocation> getRegisteredInvocations() {
        return behavior.getRegisteredInvocations();
    }
}