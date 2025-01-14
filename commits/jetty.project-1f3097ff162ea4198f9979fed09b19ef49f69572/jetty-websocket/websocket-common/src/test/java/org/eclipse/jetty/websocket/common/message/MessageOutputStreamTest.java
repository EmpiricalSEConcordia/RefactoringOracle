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

package org.eclipse.jetty.websocket.common.message;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;

import org.eclipse.jetty.toolchain.test.TestTracker;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.extensions.OutgoingFrames;
import org.eclipse.jetty.websocket.common.events.EventDriver;
import org.eclipse.jetty.websocket.common.events.EventDriverFactory;
import org.eclipse.jetty.websocket.common.io.FramePipes;
import org.eclipse.jetty.websocket.common.io.LocalWebSocketSession;
import org.eclipse.jetty.websocket.common.test.LeakTrackingBufferPoolRule;
import org.eclipse.jetty.websocket.common.scopes.WebSocketContainerScope;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class MessageOutputStreamTest
{
    private static final Logger LOG = Log.getLogger(MessageOutputStreamTest.class);

    @Rule
    public TestTracker testtracker = new TestTracker();

    @Rule
    public TestName testname = new TestName();

    @Rule
    public LeakTrackingBufferPoolRule bufferPool = new LeakTrackingBufferPoolRule("Test");

    private WebSocketPolicy policy;
    private TrackingSocket socket;
    private LocalWebSocketSession session;

    @After
    public void closeSession()
    {
        session.close();
    }

    @Before
    public void setupSession()
    {
        policy = WebSocketPolicy.newServerPolicy();
        policy.setInputBufferSize(1024);
        policy.setMaxBinaryMessageBufferSize(1024);

        // Event Driver factory
        EventDriverFactory factory = new EventDriverFactory(policy);
        
        // Container
        WebSocketContainerScope containerScope = new SimpleContainerScope(policy,bufferPool);

        // local socket
        EventDriver driver = factory.wrap(new TrackingSocket("local"));

        // remote socket
        socket = new TrackingSocket("remote");
        OutgoingFrames socketPipe = FramePipes.to(factory.wrap(socket));

        session = new LocalWebSocketSession(containerScope,testname,driver);

        session.setPolicy(policy);
        // talk to our remote socket
        session.setOutgoingHandler(socketPipe);
        // open connection
        session.open();
    }

    @Test
    public void testMultipleWrites() throws Exception
    {
        try (MessageOutputStream stream = new MessageOutputStream(session))
        {
            stream.write("Hello".getBytes("UTF-8"));
            stream.write(" ".getBytes("UTF-8"));
            stream.write("World".getBytes("UTF-8"));
        }

        Assert.assertThat("Socket.messageQueue.size",socket.messageQueue.size(),is(1));
        String msg = socket.messageQueue.poll();
        Assert.assertThat("Message",msg,allOf(containsString("byte[11]"),containsString("Hello World")));
    }

    @Test
    public void testSingleWrite() throws Exception
    {
        try (MessageOutputStream stream = new MessageOutputStream(session))
        {
            stream.write("Hello World".getBytes("UTF-8"));
        }

        Assert.assertThat("Socket.messageQueue.size",socket.messageQueue.size(),is(1));
        String msg = socket.messageQueue.poll();
        Assert.assertThat("Message",msg,allOf(containsString("byte[11]"),containsString("Hello World")));
    }

    @Test
    public void testWriteMultipleBuffers() throws Exception
    {
        int bufsize = (int)(policy.getMaxBinaryMessageBufferSize() * 2.5);
        byte buf[] = new byte[bufsize];
        LOG.debug("Buffer sizes: max:{}, test:{}",policy.getMaxBinaryMessageBufferSize(),bufsize);
        Arrays.fill(buf,(byte)'x');
        buf[bufsize - 1] = (byte)'o'; // mark last entry for debugging

        try (MessageOutputStream stream = new MessageOutputStream(session))
        {
            stream.write(buf);
        }

        Assert.assertThat("Socket.messageQueue.size",socket.messageQueue.size(),is(1));
        String msg = socket.messageQueue.poll();
        Assert.assertThat("Message",msg,allOf(containsString("byte[" + bufsize + "]"),containsString("xxxo>>>")));
    }
}
