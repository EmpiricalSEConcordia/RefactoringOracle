// ========================================================================
// Copyright (c) 2006-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// ========================================================================

package org.eclipse.jetty;

import java.io.File;
import java.lang.management.ManagementFactory;

import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StdErrLog;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

public class TestServer
{
    public static void main(String[] args) throws Exception
    {
        Log.getLog().setDebugEnabled(false);
        ((StdErrLog)Log.getLog()).setSource(false);
        
        String jetty_root = "..";

        Server server = new Server();
        server.setSendDateHeader(true);
        
        // Setup JMX
        MBeanContainer mbContainer=new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        server.getContainer().addEventListener(mbContainer);
        server.addBean(mbContainer);
        mbContainer.addBean(Log.getLog());
        
        // Setup Threadpool
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(100);
        server.setThreadPool(threadPool);

        // Setup Connectors
        SelectChannelConnector connector0 = new SelectChannelConnector();
        connector0.setPort(8080);
        connector0.setMaxIdleTime(30000);
        connector0.setConfidentialPort(8443);
        connector0.setUseDirectBuffers(true);
        server.addConnector(connector0);
        
        // Setup Connectors
        SelectChannelConnector connector1 = new SelectChannelConnector();
        connector1.setPort(8081);
        connector1.setMaxIdleTime(30000);
        connector1.setConfidentialPort(8443);
        connector1.setUseDirectBuffers(false);
        server.addConnector(connector1);
        
        // Setup Connectors
        SocketConnector connector2 = new SocketConnector();
        connector2.setPort(8082);
        connector2.setMaxIdleTime(30000);
        connector2.setConfidentialPort(8443);
        server.addConnector(connector2);

        SslSelectChannelConnector ssl_connector = new SslSelectChannelConnector();
        ssl_connector.setPort(8443);
        ssl_connector.setKeystore(jetty_root + "/jetty-server/src/main/config/etc/keystore");
        ssl_connector.setPassword("OBF:1vny1zlo1x8e1vnw1vn61x8g1zlu1vn4");
        ssl_connector.setKeyPassword("OBF:1u2u1wml1z7s1z7a1wnl1u2g");
        ssl_connector.setTruststore(jetty_root + "/jetty-server/src/main/config/etc/keystore");
        ssl_connector.setTrustPassword("OBF:1vny1zlo1x8e1vnw1vn61x8g1zlu1vn4");
        server.addConnector(ssl_connector);

        HandlerCollection handlers = new HandlerCollection();
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        handlers.setHandlers(new Handler[]
        { contexts, new DefaultHandler(), requestLogHandler });
        server.setHandler(handlers);

        
        // Setup deployers

        HashLoginService login = new HashLoginService();
        login.setName("Test Realm");
        login.setConfig(jetty_root + "/test-jetty-webapp/src/main/config/etc/realm.properties");
        server.addBean(login);

        File log=File.createTempFile("jetty-yyyy_mm_dd", "log");
        NCSARequestLog requestLog = new NCSARequestLog(log.toString());
        requestLog.setExtended(false);
        requestLogHandler.setRequestLog(requestLog);

        server.setStopAtShutdown(true);
        server.setSendServerVersion(true);
        
        WebAppContext webapp = new WebAppContext();
        webapp.setParentLoaderPriority(true);
        webapp.setResourceBase("./src/main/webapp");
        webapp.setAttribute("testAttribute","testValue");
        
        contexts.addHandler(webapp);
        
        server.start();
        server.join();
    }

}
