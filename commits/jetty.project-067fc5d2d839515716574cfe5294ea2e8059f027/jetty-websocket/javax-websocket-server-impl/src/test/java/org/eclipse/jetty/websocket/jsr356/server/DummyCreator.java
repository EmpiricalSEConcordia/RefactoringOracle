//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.websocket.jsr356.server;

import org.eclipse.jetty.websocket.server.MappedWebSocketCreator;
import org.eclipse.jetty.websocket.server.pathmap.PathMappings;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;

public class DummyCreator implements MappedWebSocketCreator
{
    @Override
    public void addMapping(String spec, WebSocketCreator creator)
    {
        /* do nothing */
    }

    @Override
    public void addMapping(org.eclipse.jetty.websocket.server.pathmap.PathSpec spec, WebSocketCreator creator)
    {
        /* do nothing */
    }
    
    @Override
    public void addMapping(org.eclipse.jetty.http.pathmap.PathSpec spec, WebSocketCreator creator)
    {
        /* do nothing */
    }
    
    @Override
    public PathMappings<WebSocketCreator> getMappings()
    {
        return null;
    }

    @Override
    public WebSocketCreator getMapping(String spec)
    {
        return null;
    }

    @Override
    public boolean removeMapping(String spec)
    {
        return false;
    }
}
