/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.stubbing;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

/**
 * Stubs void method with an exception. E.g:
 *
 * <pre>
 * stubVoid(mock).toThrow(new RuntimeException()).on().someMethod();
 *
 * //you can stub with different behavior for consecutive method calls.
 * //Last stubbing (e.g: toReturn()) determines the behavior for further consecutive calls.
 * stub(mock)
 *  .toThrow(new RuntimeException())
 *  .toReturn()
 *  .on().someMethod();
 * </pre>
 *
 * See examples in javadoc for {@link Mockito#stubVoid}
 */
public interface VoidMethodStubbable<T> {

    /**
     * Stubs void method with an exception. E.g:
     *
     * <pre>
     * stubVoid(mock).toThrow(new RuntimeException()).on().someMethod();
     * </pre>
     *
     * If throwable is a checked exception then it has to
     * match one of the checked exceptions of method signature.
     *
     * See examples in javadoc for {@link Mockito#stubVoid}
     *
     * @param throwable to be thrown on method invocation
     *
     * @return VoidMethodStubbable - typically to choose void method and finish stubbing
     */
    VoidMethodStubbable<T> toThrow(Throwable throwable);

    /**
     * Stubs void method to 'just return' (e.g. to <b>not</b> to throw any exception)
     * <p>
     * <b>Only use this method if you're stubbing consecutive calls.</b>
     * <p>
     * For example:
     * <pre>
     * stubVoid(mock)
     *   .toReturn()
     *   .toThrow(new RuntimeException())
     *   .on().foo(10);
     * </pre>
     * <ul>
     * <li>first time foo(10) is called the mock will 'just return' (e.g. don't throw any exception)</li>
     * <li>second time foo(10) is called the mock will throw RuntimeException</li>
     * <li>every consecutive time foo(10) is called the mock will throw RuntimeException</li>
     * </ul>
     * <p>
     * See examples in javadoc for {@link Mockito#stubVoid}
     *
     * @return VoidMethodStubbable - typically to choose void method and finish stubbing
     */
    VoidMethodStubbable<T> toReturn();

    /**
     * Stubs void method to execute a custom answer.
     * <p>
     * For Example:
     * <pre>
     * stubVoid(mock)
     *   .toAnswer(new Answer() {
     *                 public Object answer(InvocationOnMOck invocation) {
     *                     Visitor v = (Visitor) invocation.getArguments()[0];
     *                     v.visitMock(invocation.getMock());
     *
     *                     return null;
     *                 }
     *             })
     *    .on().accept(any());
     * </pre>
     *
     * @param answer the custom answer to execute.
     *
     * @return VoidMethodStubbable - typically to choose void method and finish stubbing
     */
    VoidMethodStubbable<T> toAnswer(Answer<?> answer);

    /**
     * Choose void method for stubbing. E.g:
     *
     * <pre>
     * stubVoid(mock).toThrow(new RuntimeException()).on().someMethod("some arg");
     * </pre>
     *
     * See examples in javadoc for {@link Mockito#stubVoid}
     *
     * @return mock object itself
     */
    T on();
}