/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.exceptions;

import static org.mockito.exceptions.StringJoiner.*;

import org.mockito.exceptions.base.HasStackTrace;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.exceptions.cause.TooLittleInvocations;
import org.mockito.exceptions.cause.UndesiredInvocation;
import org.mockito.exceptions.cause.WantedAnywhereAfterFollowingInteraction;
import org.mockito.exceptions.cause.WantedDiffersFromActual;
import org.mockito.exceptions.misusing.MissingMethodInvocationException;
import org.mockito.exceptions.misusing.NotAMockException;
import org.mockito.exceptions.misusing.UnfinishedStubbingException;
import org.mockito.exceptions.misusing.UnfinishedVerificationException;
import org.mockito.exceptions.verification.InvocationDiffersFromActual;
import org.mockito.exceptions.verification.NoInteractionsWanted;
import org.mockito.exceptions.verification.TooLittleActualInvocations;
import org.mockito.exceptions.verification.TooManyActualInvocations;
import org.mockito.exceptions.verification.VerifcationInOrderFailure;
import org.mockito.exceptions.verification.WantedButNotInvoked;

/**
 * Reports verification and misusing errors.
 * <p>
 * One of the key points of mocking library is proper verification/exception
 * messages. All messages in one place makes it easier to tune and amend them.
 * <p>
 * Reporter can be injected and therefore is easily testable.
 * <p>
 * Generally, exception messages are full of line breaks to make them easy to
 * read (xunit plugins take only fraction of screen on modern IDEs).
 */
public class Reporter {

    private String pluralize(int number) {
        return number == 1 ? "1 time" : number + " times";
    }

    public void mocksHaveToBePassedAsArguments() {
        throw new MockitoException(join(
                "Method requires argument(s).",
                "Pass mocks that should be verified, e.g:",
                "  verifyNoMoreInteractions(mockOne, mockTwo);",
                "  verifyZeroInteractions(mockOne, mockTwo);"
                ));
    }

    public void inOrderRequiresFamiliarMock() {
        throw new MockitoException(join(
                "InOrder can only verify mocks that were passed in during creation of InOrder. E.g:",
                "  InOrder inOrder = inOrder(mockOne);",
                "  inOrder.verify(mockOne).doStuff();"
                ));
    }

    public void mocksHaveToBePassedWhenCreatingInOrder() {
        throw new MockitoException(join(
                "Method requires argument(s).",
                "Pass mocks that require verification in order, e.g:",
                "  InOrder inOrder = inOrder(mockOne, mockTwo);"
                ));
    }

    public void checkedExceptionInvalid(Throwable t) {
        throw new MockitoException(join(
                "Checked exception is invalid for this method",
                "Invalid: " + t
                ));
    }

    public void cannotStubWithNullThrowable() {
        throw new MockitoException(join(
                "Cannot stub with null throwable"
                ));

    }
    
    public void unfinishedStubbing() {
        throw new UnfinishedStubbingException(join(
                "Unifinished stubbing detected, e.g. toReturn() may be missing",
                "Examples of correct stubbing:",
                "  stub(mock.isOk()).toReturn(true);",
                "  stub(mock.isOk()).toThrow(exception);",
                "  stubVoid(mock).toThrow(exception).on().someMethod();"
        ));
    }

    public void missingMethodInvocation() {
        throw new MissingMethodInvocationException(join(
                "stub() requires an argument which has to be a method call on a mock",
                "For example:",
                "  stub(mock.getArticles()).toReturn(articles);"
        ));
    }

    public void unfinishedVerificationException() {
        throw new UnfinishedVerificationException(join(
                "Previous verify(mock) doesn't have a method call.",
                "Example of correct verification:",
                "  verify(mock).doSomething()"
        ));
    }

    public void wantedDiffersFromActual(Printable wanted, Printable actual, HasStackTrace actualInvocationStackTrace) {
        WantedDiffersFromActual cause1 = new WantedDiffersFromActual(join(
                "Actual invocation:",
                actual.toString()
            ));
        
        cause1.setStackTrace(actualInvocationStackTrace.getStackTrace());
        WantedDiffersFromActual cause = cause1;

        throw new InvocationDiffersFromActual(join(
                "Invocation differs from actual",
                "Wanted invocation:",
                wanted.toString()
            ), cause);
    }
    
    public void wantedButNotInvoked(Printable wanted) {
        throw new WantedButNotInvoked(join(
                    "Wanted but not invoked:",
                    wanted.toString()
        ));
    }
    
    public void wantedButNotInvokedInOrder(Printable wanted, Printable previous, HasStackTrace previousStackTrace) {
        WantedAnywhereAfterFollowingInteraction cause = new WantedAnywhereAfterFollowingInteraction(join(
                        "Wanted anywhere AFTER following interaction:",
                        previous.toString()));
        cause.setStackTrace(previousStackTrace.getStackTrace());
        
        throw new VerifcationInOrderFailure(join(
                    "Verification in order failure",
                    "Wanted but not invoked:",
                    wanted.toString()
        ), cause);
    }

    public void tooManyActualInvocations(int wantedCount, int actualCount, Printable wanted, HasStackTrace firstUndesired) {
        UndesiredInvocation cause = createUndesiredInvocationCause(firstUndesired);

        throw new TooManyActualInvocations(join(
                wanted.toString(),
                "Wanted " + pluralize(wantedCount) + " but was " + actualCount
        ), cause);
    }
    
    public void tooManyActualInvocationsInOrder(int wantedCount, int actualCount, Printable wanted, HasStackTrace firstUndesired) {
        UndesiredInvocation cause = createUndesiredInvocationCause(firstUndesired);

        throw new VerifcationInOrderFailure(join(
                "Verification in order failure",
                wanted.toString(),
                "Wanted " + pluralize(wantedCount) + " but was " + actualCount
        ), cause);
    }

    private UndesiredInvocation createUndesiredInvocationCause(HasStackTrace firstUndesired) {
        UndesiredInvocation cause = new UndesiredInvocation(join("Undesired invocation:"));
        cause.setStackTrace(firstUndesired.getStackTrace());
        return cause;
    }    

    public void tooLittleActualInvocations(int wantedCount, int actualCount, Printable wanted, HasStackTrace lastActualInvocationStackTrace) {
        TooLittleInvocations cause = createTooLittleInvocationsCause(lastActualInvocationStackTrace);

        throw new TooLittleActualInvocations(join(
                wanted.toString(),
                "Wanted " + pluralize(wantedCount) + " but was " + actualCount
        ), cause);
    }

    
    public void tooLittleActualInvocationsInOrder(int wantedCount, int actualCount, Printable wanted, HasStackTrace lastActualStackTrace) {
        TooLittleInvocations cause = createTooLittleInvocationsCause(lastActualStackTrace);

        throw new VerifcationInOrderFailure(join(
                "Verification in order failure",
                wanted.toString(),
                "Wanted " + pluralize(wantedCount) + " but was " + actualCount
        ), cause);
    }
    
    private TooLittleInvocations createTooLittleInvocationsCause(HasStackTrace lastActualInvocationStackTrace) {
        TooLittleInvocations cause = null;
        if (lastActualInvocationStackTrace != null) {
            cause = new TooLittleInvocations(join("Too little invocations:"));
            cause.setStackTrace(lastActualInvocationStackTrace.getStackTrace());
        }
        return cause;
    }

    public void noMoreInteractionsWanted(Printable undesired, HasStackTrace actualInvocationStackTrace) {
        UndesiredInvocation cause = new UndesiredInvocation(join(
                "Undesired invocation:", 
                undesired.toString()
        ));
        
        cause.setStackTrace(actualInvocationStackTrace.getStackTrace());
        throw new NoInteractionsWanted(join("No interactions wanted"), cause);
    }
    
    public void cannotMockFinalClass(Class<?> clazz) {
        throw new MockitoException(join(
                "Mockito cannot mock final classes like: ",
                clazz.toString()
        ));
    }

    public void notAMockPassedToVerify() {
        throw new NotAMockException(join(
                "Not a mock passed to verify() method",
                "Examples of correct verifications:",
                "  verify(mock).someMethod();",
                "  verify(mock, times(10)).someMethod();",
                "  verify(mock, atLeastOnce()).someMethod();"
                
        ));
    }

    public void notAMockPassedToVerifyNoMoreInteractions() {
        throw new NotAMockException(join(
            "Not a mock passed to method",
            "Examples of correct verifications:",
            "  verifyNoMoreInteractions(mockOne, mockTwo);",
            "  verifyZeroInteractions(mockOne, mockTwo);"
        ));
    }
}