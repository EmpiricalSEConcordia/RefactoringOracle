//
//  ========================================================================
//  Copyright (c) 1995-2015 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.common;

import java.net.URI;

import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.eclipse.jetty.websocket.common.events.EventDriver;

/**
 * Interface for creating jetty {@link WebSocketSession} objects.
 */
public interface SessionFactory
{
    public boolean supports(EventDriver websocket);
    
    public WebSocketSession createSession(URI requestURI, EventDriver websocket, LogicalConnection connection);

    public void setEnhancedInstantiator(DecoratedObjectFactory objectFactory);
}
