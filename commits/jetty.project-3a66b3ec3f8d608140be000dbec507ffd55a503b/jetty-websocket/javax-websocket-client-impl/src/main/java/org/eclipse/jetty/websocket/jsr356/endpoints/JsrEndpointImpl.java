//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.websocket.jsr356.endpoints;

import javax.websocket.Endpoint;

import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.common.events.EventDriver;
import org.eclipse.jetty.websocket.common.events.EventDriverImpl;
import org.eclipse.jetty.websocket.jsr356.JettyWebSocketContainer;

public class JsrEndpointImpl implements EventDriverImpl
{
    private final JettyWebSocketContainer container;

    public JsrEndpointImpl(JettyWebSocketContainer container)
    {
        this.container = container;
    }

    @Override
    public EventDriver create(Object websocket, WebSocketPolicy policy)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String describeRule()
    {
        return "class extends " + Endpoint.class.getName();
    }

    @Override
    public boolean supports(Object websocket)
    {
        return (websocket instanceof Endpoint);
    }
}
