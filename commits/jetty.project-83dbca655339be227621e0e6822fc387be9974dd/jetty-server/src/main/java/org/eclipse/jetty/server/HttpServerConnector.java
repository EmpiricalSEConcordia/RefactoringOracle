//
//  ========================================================================
//  Copyright (c) 1995-2012 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.server;

import java.util.concurrent.Executor;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;

public class HttpServerConnector extends SelectChannelConnector
{
    public HttpServerConnector(Server server)
    {
        this(server, null);
    }

    public HttpServerConnector(Server server, SslContextFactory sslContextFactory)
    {
        this(server, null, null, null, sslContextFactory, 0, 0);
    }

    public HttpServerConnector(@Name("server") Server server, @Name("executor") Executor executor, @Name("scheduler") Scheduler scheduler, @Name("bufferPool") ByteBufferPool pool, @Name("sslContextFactory") SslContextFactory sslContextFactory, @Name("acceptors") int acceptors, @Name("selectors") int selectors)
    {
        super(server, executor, scheduler, pool, sslContextFactory, acceptors, selectors);
        setDefaultConnectionFactory(new HttpServerConnectionFactory(this));
    }
}
