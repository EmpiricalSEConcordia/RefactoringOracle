package org.mockito.internal;

import org.mockito.exceptions.Exceptions;

public class MissingInvocationVerifier implements Verifier {

    public void verify(RegisteredInvocations registeredInvocations, InvocationMatcher wanted, VerifyingMode mode) {
        int actualCount = registeredInvocations.countActual(wanted);
        Integer wantedCount = mode.wantedCount();
        boolean atLeastOnce = mode.atLeastOnceMode();
               
        if ((atLeastOnce || wantedCount == 1) && actualCount == 0) {
            reportMissingInvocationError(registeredInvocations, wanted);
        }
    }
    
    private void reportMissingInvocationError(RegisteredInvocations registeredInvocations, InvocationMatcher wanted) {
        Invocation actual = registeredInvocations.findActualInvocation(wanted);
        
        if (actual != null) {
            reportDiscrepancy(wanted, actual);
        } else {
            Exceptions.wantedButNotInvoked(wanted.toString());
        }
    }

    private void reportDiscrepancy(InvocationMatcher wantedInvocation, Invocation actualInvocation) {
        String wanted = wantedInvocation.toString();
        String actual = actualInvocation.toString();
        if (wanted.equals(actual)) {
            wanted = wantedInvocation.getInvocation().toStringWithArgumentTypes();
            actual = actualInvocation.toStringWithArgumentTypes();
        }
        
        Exceptions.wantedInvocationDiffersFromActual(wanted, actual, actualInvocation.getStackTrace());
    }
}
