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

package org.eclipse.jetty.servlets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.io.SelectChannelEndPoint;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.eclipse.jetty.toolchain.test.TestTracker;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class DefaultServletStarvationTest
{
    @Rule
    public TestTracker tracker = new TestTracker();
    private Server _server;

    @After
    public void dispose() throws Exception
    {
        if (_server != null)
            _server.stop();
    }

    @Test
    public void testDefaultServletStarvation() throws Exception
    {
        int maxThreads = 2;
        QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads, maxThreads);
        threadPool.setDetailedDump(true);
        _server = new Server(threadPool);

        // Prepare a big file to download.
        File directory = MavenTestingUtils.getTargetTestingDir();
        Files.createDirectories(directory.toPath());
        String resourceName = "resource.bin";
        Path resourcePath = Paths.get(directory.getPath(), resourceName);
        try (OutputStream output = Files.newOutputStream(resourcePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE))
        {
            byte[] chunk = new byte[1024];
            Arrays.fill(chunk,(byte)'X');
            chunk[chunk.length-2]='\r';
            chunk[chunk.length-1]='\n';
            for (int i = 0; i < 256 * 1024; ++i)
                output.write(chunk);
        }

        final CountDownLatch writePending = new CountDownLatch(1);
        ServerConnector connector = new ServerConnector(_server, 0, 1)
        {
            @Override
            protected SelectChannelEndPoint newEndPoint(SocketChannel channel, ManagedSelector selectSet, SelectionKey key) throws IOException
            {
                return new SelectChannelEndPoint(channel, selectSet, key, getScheduler(), getIdleTimeout())
                {
                    @Override
                    protected void onIncompleteFlush()
                    {
                        super.onIncompleteFlush();
                        writePending.countDown();
                    }
                };
            }
        };
        _server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(_server, "/");
        context.setResourceBase(directory.toURI().toString());
        context.addServlet(DefaultServlet.class, "/*").setAsyncSupported(false);
        _server.setHandler(context);

        _server.start();

        List<Socket> sockets = new ArrayList<>();
        for (int i = 0; i < maxThreads; ++i)
        {
            Socket socket = new Socket("localhost", connector.getLocalPort());
            sockets.add(socket);
            OutputStream output = socket.getOutputStream();
            String request = "" +
                    "GET /" + resourceName + " HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
//                    "Connection: close\r\n" +
                    "\r\n";
            output.write(request.getBytes(StandardCharsets.UTF_8));
            output.flush();
            Thread.sleep(100);
        }
        
        
        // Wait for a the servlet to block.
        Assert.assertTrue(writePending.await(5, TimeUnit.SECONDS));

        Thread.sleep(1000);
        _server.dumpStdErr();
        Thread.sleep(1000);

        
        ScheduledFuture<?> dumper = Executors.newSingleThreadScheduledExecutor().schedule(new Runnable()
        {
            @Override
            public void run()
            {
                _server.dumpStdErr();
            }
        }, 10, TimeUnit.SECONDS);
        

        long expected = Files.size(resourcePath);
        byte[] buffer = new byte[48 * 1024];
        for (Socket socket : sockets)
        {
            String socketString = socket.toString();
            System.out.println("Reading socket " + socketString+"...");
            long total = 0;
            InputStream input = socket.getInputStream();
            
            // look for CRLFCRLF
            StringBuilder header = new StringBuilder();
            int state=0;
            while (state<4 && header.length()<2048)
            {
                int ch=input.read();
                if (ch<0)
                    break;
                header.append((char)ch);
                switch(state)
                {
                    case 0:
                        if (ch=='\r')
                            state=1;
                        break;
                    case 1:
                        if (ch=='\n')
                            state=2;
                        else
                            state=0;
                        break;
                    case 2:
                        if (ch=='\r')
                            state=3;
                        else
                            state=0;
                        break;
                    case 3:
                        if (ch=='\n')
                            state=4;
                        else
                            state=0;
                        break;
                }                
            }
            System.out.println("Header socket " + socketString+"\n"+header.toString());
            
            while (total<expected)
            {
                int read=input.read(buffer);
                if (read<0)
                    break;
                total+=read;
                System.out.printf("READ %d of %d/%d from %s%n",read,total,expected,socketString);
            }

            Assert.assertEquals(expected,total);
        }

        dumper.cancel(false);

        // We could read everything, good.
        for (Socket socket : sockets)
            socket.close();
    }
}
