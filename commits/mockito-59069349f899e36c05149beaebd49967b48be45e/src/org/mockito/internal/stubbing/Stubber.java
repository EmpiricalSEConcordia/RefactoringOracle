package org.mockito.internal.stubbing;

import java.util.LinkedList;

import org.mockito.exceptions.Exceptions;
import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.state.MockingProgress;

public class Stubber {

    private InvocationMatcher invocationForStubbing;
    private LinkedList<StubbedInvocationMatcher> stubbed = new LinkedList<StubbedInvocationMatcher>();
    private Throwable throwableForVoidMethod;
    private final MockingProgress mockingProgress;
    
    public Stubber(MockingProgress mockingProgress) {
        this.mockingProgress = mockingProgress;
    }

    public void setInvocationForPotentialStubbing(InvocationMatcher invocation) {
        this.invocationForStubbing = invocation;
    }
    
    public void addReturnValue(Object value) {
        mockingProgress.stubbingCompleted();
        addResult(Result.createReturnResult(value));
    }
    
    public void addThrowable(Throwable throwable) {
        mockingProgress.stubbingCompleted();
        validateThrowable(throwable);
        addResult(Result.createThrowResult(throwable));
    }

    private void addResult(Result result) {
        assert invocationForStubbing != null;
        stubbed.addFirst(new StubbedInvocationMatcher(invocationForStubbing, result));
    }

    public Object resultFor(Invocation wanted) throws Throwable {
        for (StubbedInvocationMatcher s : stubbed) {
            if (s.matches(wanted)) {
                return s.getResult().answer();
            }
        }

        return EmptyReturnValues.emptyValueFor(wanted.getMethod().getReturnType());
    }

    public void addThrowableForVoidMethod(Throwable throwable) {
        throwableForVoidMethod = throwable;
    }

    public boolean hasThrowableForVoidMethod() {
        return throwableForVoidMethod != null;
    }
    
    public void addVoidMethodForThrowable(InvocationMatcher voidMethodInvocationMatcher) {
        invocationForStubbing = voidMethodInvocationMatcher;
        addThrowable(throwableForVoidMethod);
        throwableForVoidMethod = null;
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
        Invocation lastInvocation = invocationForStubbing.getInvocation();

        Class<?>[] exceptions = lastInvocation.getMethod().getExceptionTypes();
        Class<?> throwableClass = throwable.getClass();
        for (Class<?> exception : exceptions) {
            if (exception.isAssignableFrom(throwableClass)) {
                return true;
            }
        }
        
        return false;
    }
}
