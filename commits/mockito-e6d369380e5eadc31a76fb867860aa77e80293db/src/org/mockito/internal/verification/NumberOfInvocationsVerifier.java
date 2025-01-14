/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.verification;

import java.util.List;

import org.mockito.exceptions.Reporter;
import org.mockito.exceptions.base.HasStackTrace;
import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.invocation.InvocationsFinder;

public class NumberOfInvocationsVerifier {
    
    private final Reporter reporter;
    private final InvocationsFinder finder;

    public NumberOfInvocationsVerifier() {
        this(new Reporter(), new InvocationsFinder());
    }
    
    NumberOfInvocationsVerifier(Reporter reporter, InvocationsFinder finder) {
        this.reporter = reporter;
        this.finder = finder;
    }
    
    public void verify(List<Invocation> invocations, InvocationMatcher wanted, VerificationMode mode) {
        VerificationModeDecoder decoder = new VerificationModeDecoder(mode);
        List<Invocation> actualInvocations = finder.findInvocations(invocations, wanted, mode);
        
        int actualCount = actualInvocations.size();
        if (decoder.tooLittleActualInvocations(actualCount)) {
            HasStackTrace lastInvocation = finder.getLastStackTrace(actualInvocations);
            reporter.tooLittleActualInvocations(mode.wantedCount(), actualCount, wanted, lastInvocation);
        } else if (decoder.tooLittleActualInvocationsInAtLeastMode(actualCount)) {
            HasStackTrace lastInvocation = finder.getLastStackTrace(actualInvocations);
            reporter.tooLittleActualInvocationsInAtLeastMode(mode.wantedCount(), actualCount, wanted, lastInvocation);        
        } else if (decoder.neverWantedButInvoked(actualCount)) {
            HasStackTrace firstUndesired = actualInvocations.get(mode.wantedCount()).getStackTrace();
            reporter.neverWantedButInvoked(wanted, firstUndesired); 
        } else if (decoder.tooManyActualInvocations(actualCount)) {
            HasStackTrace firstUndesired = actualInvocations.get(mode.wantedCount()).getStackTrace();
            reporter.tooManyActualInvocations(mode.wantedCount(), actualCount, wanted, firstUndesired);
        }
        
        for (Invocation i : actualInvocations) {
            i.markVerified();
        }
    }
}
