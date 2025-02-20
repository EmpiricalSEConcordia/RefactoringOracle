/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */

package org.mockito.internal.invocation;

import org.mockito.exceptions.PrintableInvocation;
import org.mockito.exceptions.Reporter;
import org.mockito.internal.debugging.LocationImpl;
import org.mockito.internal.exceptions.VerificationAwareInvocation;
import org.mockito.internal.invocation.realmethod.RealMethod;
import org.mockito.internal.reporting.PrintSettings;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.invocation.PublicInvocation;
import org.mockito.invocation.StubInfo;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Method call on a mock object.
 * <p>
 * Contains sequence number which should be globally unique and is used for
 * verification in order.
 * <p>
 * Contains stack trace of invocation
 */
@SuppressWarnings("unchecked")
public class Invocation implements PublicInvocation, PrintableInvocation, InvocationOnMock, VerificationAwareInvocation {

    private static final long serialVersionUID = 8240069639250980199L;
    public static final int MAX_LINE_LENGTH = 45;
    private final int sequenceNumber;
    private final Object mock;
    private final MockitoMethod method;
    private final Object[] arguments;
    private final Object[] rawArguments;

    private final LocationImpl location;
    private boolean verified;
    private boolean isIgnoredForVerification;

    final RealMethod realMethod;
    private StubInfo stubInfo;

    public Invocation(Object mock, MockitoMethod mockitoMethod, Object[] args, int sequenceNumber, RealMethod realMethod) {
        this.method = mockitoMethod;
        this.mock = mock;
        this.realMethod = realMethod;
        this.arguments = ArgumentsProcessor.expandVarArgs(mockitoMethod.isVarArgs(), args);
        this.rawArguments = args;
        this.sequenceNumber = sequenceNumber;
        this.location = new LocationImpl();
    }

    public Object getMock() {
        return mock;
    }

    public Method getMethod() {
        return method.getJavaMethod();
    }

    public Object[] getArguments() {
        return arguments;
    }

    public boolean isVerified() {
        return verified || isIgnoredForVerification;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public boolean equals(Object o) {
        if (o == null || !o.getClass().equals(this.getClass())) {
            return false;
        }

        Invocation other = (Invocation) o;

        return this.mock.equals(other.mock) && this.method.equals(other.method) && this.equalArguments(other.arguments);
    }

    private boolean equalArguments(Object[] arguments) {
        return Arrays.equals(arguments, this.arguments);
    }

    @Override
    public int hashCode() {
        return 1;
    }

    public String toString() {
        return new PrintSettings().print(ArgumentsProcessor.argumentsToMatchers(getArguments()), this);
    }

    public LocationImpl getLocation() {
        return location;
    }

    public Object[] getRawArguments() {
        return this.rawArguments;
    }

    public Object callRealMethod() throws Throwable {
        if (this.getMethod().getDeclaringClass().isInterface()) {
            new Reporter().cannotCallRealMethodOnInterface();
        }
        return realMethod.invoke(mock, rawArguments);
    }

    public void markVerified() {
        this.verified = true;
    }

    public StubInfo stubInfo() {
        return stubInfo;
    }

    public void markStubbed(StubInfo stubInfo) {
        this.stubInfo = stubInfo;
    }

    public boolean isIgnoredForVerification() {
        return isIgnoredForVerification;
    }

    public void ignoreForVerification() {
        isIgnoredForVerification = true;
    }
}