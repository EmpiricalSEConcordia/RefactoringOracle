package org.mockito.internal.returnvalues;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import org.mockito.Mockito;
import org.mockito.configuration.IMockitoConfiguration;
import org.mockito.configuration.ReturnValues;
import org.mockito.exceptions.cause.BecauseThisMethodWasNotStubbed;
import org.mockito.exceptions.verification.SmartNullPointerException;
import org.mockito.internal.creation.jmock.ClassImposterizer;
import org.mockito.invocation.InvocationOnMock;

/**
 * Optional ReturnValues to be used with {@link Mockito#mock(Class, ReturnValues)}
 * <p>
 * {@link ReturnValues} defines the return values of unstubbed calls.
 * <p>
 * This implementation can be helpful when working with legacy code.
 * Unstubbed methods often return null. If your code uses the object returned by an unstubbed call you get a NullPointerException.
 * This implementation of ReturnValues makes unstubbed methods return SmartNulls instead of nulls.
 * SmartNull gives nicer exception message than NPE because it points out the line where unstubbed method was called. You just click on the stack trace.
 * <p>
 * SmartNullReturnValues first tries to return ordinary return values (see {@link MoreEmptyReturnValues})
 * then it tries to return SmartNull. If the return type is final then plain null is returned.
 * <p>
 * If you would like to apply this return values strategy globally have a look at {@link IMockitoConfiguration} class
 * <p>
 * SmartNullReturnValues will be probably the default return values strategy in Mockito 2.0
 */
public class SmartNullReturnValues implements ReturnValues {

    private final ReturnValues delegate = new MoreEmptyReturnValues();

    public Object valueFor(InvocationOnMock invocation) {
        Object defaultReturnValue = delegate.valueFor(invocation);
        if (defaultReturnValue != null) {
            return defaultReturnValue;
        }
        Class<?> type = invocation.getMethod().getReturnType();
        if (ClassImposterizer.INSTANCE.canImposterise(type)) {
            return ClassImposterizer.INSTANCE.imposterise(new MethodInterceptor() {
                Exception whenCreated = new BecauseThisMethodWasNotStubbed("\nBecause this method was not stubbed correctly:");
                public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
                    throw new SmartNullPointerException("\nYou have a NullPointerException here:", whenCreated);
                }}, type);
        }
        return null;
    }
}