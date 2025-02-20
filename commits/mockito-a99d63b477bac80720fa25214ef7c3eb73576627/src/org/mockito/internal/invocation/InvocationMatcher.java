/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */

package org.mockito.internal.invocation;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.hamcrest.Matcher;
import org.mockito.exceptions.PrintableInvocation;
import org.mockito.internal.debugging.LocationImpl;
import org.mockito.internal.matchers.CapturesArguments;
import org.mockito.internal.reporting.PrintSettings;

@SuppressWarnings("unchecked")
public class InvocationMatcher implements PrintableInvocation, CapturesArgumensFromInvocation, Serializable {

    private static final long serialVersionUID = -3047126096857467610L;
    private final InvocationImpl invocation;
    private final List<Matcher> matchers;

    public InvocationMatcher(InvocationImpl invocation, List<Matcher> matchers) {
        this.invocation = invocation;
        if (matchers.isEmpty()) {
            this.matchers = ArgumentsProcessor.argumentsToMatchers(invocation.getArguments());
        } else {
            this.matchers = matchers;
        }
    }
    
    public InvocationMatcher(InvocationImpl invocation) {
        this(invocation, Collections.<Matcher>emptyList());
    }

    public Method getMethod() {
        return invocation.getMethod();
    }
    
    public InvocationImpl getInvocation() {
        return this.invocation;
    }
    
    public List<Matcher> getMatchers() {
        return this.matchers;
    }
    
    public String toString() {
        return new PrintSettings().print(matchers, invocation);
    }

    public boolean matches(InvocationImpl actual) {
        return invocation.getMock().equals(actual.getMock())
                && hasSameMethod(actual)
                && new ArgumentsComparator().argumentsMatch(this, actual);
    }

    private boolean safelyArgumentsMatch(Object[] actualArgs) {
        try {
            return new ArgumentsComparator().argumentsMatch(this, actualArgs);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * similar means the same method name, same mock, unverified 
     * and: if arguments are the same cannot be overloaded
     */
    public boolean hasSimilarMethod(InvocationImpl candidate) {
        String wantedMethodName = getMethod().getName();
        String currentMethodName = candidate.getMethod().getName();
        
        final boolean methodNameEquals = wantedMethodName.equals(currentMethodName);
        final boolean isUnverified = !candidate.isVerified();
        final boolean mockIsTheSame = getInvocation().getMock() == candidate.getMock();
        final boolean methodEquals = hasSameMethod(candidate);

        if (!methodNameEquals || !isUnverified || !mockIsTheSame) {
            return false;
        }

        final boolean overloadedButSameArgs = !methodEquals && safelyArgumentsMatch(candidate.getArguments());

        return !overloadedButSameArgs;
    }

    public boolean hasSameMethod(InvocationImpl candidate) {
        //not using method.equals() for 1 good reason:
        //sometimes java generates forwarding methods when generics are in play see JavaGenericsForwardingMethodsTest
        Method m1 = invocation.getMethod();
        Method m2 = candidate.getMethod();
        
        if (m1.getName() != null && m1.getName().equals(m2.getName())) {
        	/* Avoid unnecessary cloning */
        	Class[] params1 = m1.getParameterTypes();
        	Class[] params2 = m2.getParameterTypes();
        	if (params1.length == params2.length) {
        	    for (int i = 0; i < params1.length; i++) {
        		if (params1[i] != params2[i])
        		    return false;
        	    }
        	    return true;
        	}
        }
        return false;
    }
    
    public LocationImpl getLocation() {
        return invocation.getLocation();
    }

    public void captureArgumentsFrom(InvocationImpl i) {
        int k = 0;
        for (Matcher m : matchers) {
            if (m instanceof CapturesArguments && i.getArguments().length > k) {
                ((CapturesArguments) m).captureFrom(i.getArguments()[k]);
            }
            k++;
        }
    }

    public static List<InvocationMatcher> createFrom(List<InvocationImpl> invocations) {
        LinkedList<InvocationMatcher> out = new LinkedList<InvocationMatcher>();

        for (InvocationImpl i : invocations) {
            out.add(new InvocationMatcher(i));
        }

        return out;
    }
}