/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */

package org.mockito;

import org.mockito.internal.stubbing.answers.CallsRealMethods;
import org.mockito.internal.stubbing.defaultanswers.GloballyConfiguredAnswer;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.mockito.internal.stubbing.defaultanswers.ReturnsMocks;
import org.mockito.internal.stubbing.defaultanswers.ReturnsSmartNulls;
import org.mockito.stubbing.Answer;

/**
 * Enumeration of pre-configured mock answers
 * <p>
 * You can use it to pass extra parameters to &#064;Mock annotation, see more info here: {@link Mock}
 * <p>
 * Example:
 * <pre class="code"><code class="java">
 *   &#064;Mock(answer = RETURNS_DEEP_STUBS) UserProvider userProvider;
 * </code></pre>
 * <b>This is not the full list</b> of Answers available in Mockito. Some interesting answers can be found in org.mockito.stubbing.answers package.
 */
public enum Answers {

    /**
     * The default configured answer of every mock.
     *
     * <p>Please see the {@link org.mockito.Mockito#RETURNS_DEFAULTS} documentation for more details.</p>
     *
     * @see org.mockito.Mockito#RETURNS_DEFAULTS
     */
    RETURNS_DEFAULTS(new GloballyConfiguredAnswer()),

    /**
     * An answer that returns smart-nulls.
     *
     * <p>Please see the {@link org.mockito.Mockito#RETURNS_SMART_NULLS} documentation for more details.</p>
     *
     * @see org.mockito.Mockito#RETURNS_SMART_NULLS
     */
    RETURNS_SMART_NULLS(new ReturnsSmartNulls()),

    /**
     * An answer that returns <strong>mocks</strong> (not stubs).
     *
     * <p>Please see the {@link org.mockito.Mockito#RETURNS_MOCKS} documentation for more details.</p>
     *
     * @see org.mockito.Mockito#RETURNS_MOCKS
     */
    RETURNS_MOCKS(new ReturnsMocks()),


    /**
     * An answer that returns <strong>deep stubs</strong> (not mocks).
     *
     * <p>Please see the {@link org.mockito.Mockito#RETURNS_DEEP_STUBS} documentation for more details.</p>
     *
     * @see org.mockito.Mockito#RETURNS_DEEP_STUBS
     */
    RETURNS_DEEP_STUBS(new ReturnsDeepStubs()),

    /**
     * An answer that calls the real methods (used for partial mocks).
     *
     * <p>Please see the {@link org.mockito.Mockito#CALLS_REAL_METHODS} documentation for more details.</p>
     *
     * @see org.mockito.Mockito#CALLS_REAL_METHODS
     */
    CALLS_REAL_METHODS(new CallsRealMethods())
    ;

    private Answer<Object> implementation;

    private Answers(Answer<Object> implementation) {
        this.implementation = implementation;
    }

    public Answer<Object> get() {
        return implementation;
    }
}