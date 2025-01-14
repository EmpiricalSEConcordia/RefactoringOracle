package org.mockito.internal.verification;

import static java.util.Arrays.asList;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.RequiresValidState;
import org.mockito.exceptions.Reporter;
import org.mockito.exceptions.parents.HasStackTrace;
import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.invocation.InvocationBuilder;
import org.mockito.internal.invocation.InvocationsCalculator;
import org.mockito.internal.progress.VerificationMode;
import static org.junit.Assert.*;

public class NoMoreInvocationsVerifierTest extends RequiresValidState {

    private NoMoreInvocationsVerifier verifier;
    private InvocationsCalculatorStub calculator;
    private ReporterStub reporterStub;

    @Before
    public void setup() {
        calculator = new InvocationsCalculatorStub();
        reporterStub = new ReporterStub();
        verifier = new NoMoreInvocationsVerifier(calculator, reporterStub);
    }
    
    @Test
    public void shouldNeverVerifyWhenVerificationIsExplicit() throws Exception {
        verifier.verify(null, null, VerificationMode.atLeastOnce());
    }
    
    @Test
    public void shouldPassVerification() throws Exception {
        calculator.invocationToReturn = null;
        verifier.verify(null, null, VerificationMode.noMoreInteractions());
    }
    
    @Test
    public void shouldReportError() throws Exception {
        Invocation firstUnverified = new InvocationBuilder().toInvocation();
        calculator.invocationToReturn = firstUnverified;
        List<Invocation> invocations = asList(new InvocationBuilder().toInvocation());
        
        verifier.verify(invocations, null, VerificationMode.noMoreInteractions());
        
        assertSame(invocations, calculator.invocations);
        
        assertEquals(firstUnverified.toString(), reporterStub.undesired);
        assertSame(firstUnverified.getStackTrace(), reporterStub.actualInvocationStackTrace);
    }
    
    class InvocationsCalculatorStub extends InvocationsCalculator {
        private List<Invocation> invocations;
        private Invocation invocationToReturn;
        @Override public Invocation getFirstUnverified(List<Invocation> invocations) {
            this.invocations = invocations;
            return invocationToReturn;
        }
    }
    
    class ReporterStub extends Reporter {
        private String undesired;
        private HasStackTrace actualInvocationStackTrace;
        @Override public void noMoreInteractionsWanted(String undesired, HasStackTrace actualInvocationStackTrace) {
            this.undesired = undesired;
            this.actualInvocationStackTrace = actualInvocationStackTrace;
        }
    }
}
