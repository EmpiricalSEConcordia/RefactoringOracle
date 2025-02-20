/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal;

import org.mockito.invocation.MockHandler;

/**
 * Deprecated, please use {@link MockHandler}.
 * <p>
 * This class was changed in Mockito 2.9.0 in order to tidy up Mockito API
 * and make it easier for other frameworks to integrate with Mockito.
 * Since this class is internal, e.g. it resides in "org.mockito.internal" package,
 * Mockito team can change it without the need for major version release of Mockito.
 * <p>
 * This interface will be deleted in Mockito 3.0.
 */
@Deprecated
public interface InternalMockHandler<T> extends MockHandler {}
