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

package org.eclipse.jetty.websocket.server.ab;

import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.common.CloseInfo;
import org.eclipse.jetty.websocket.common.Generator;
import org.eclipse.jetty.websocket.common.OpCode;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.common.io.IOState;
import org.eclipse.jetty.websocket.server.ByteBufferAssert;
import org.eclipse.jetty.websocket.server.blockhead.BlockheadClient;
import org.eclipse.jetty.websocket.server.helper.IncomingFramesCapture;
import org.junit.Assert;

/**
 * Fuzzing utility for the AB tests.
 */
public class Fuzzer
{
    public static enum CloseState
    {
        OPEN,
        REMOTE_INITIATED,
        LOCAL_INITIATED
    }

    public static enum SendMode
    {
        BULK,
        PER_FRAME,
        SLOW
    }

    private static final int KBYTE = 1024;
    private static final int MBYTE = KBYTE * KBYTE;

    public static final boolean CLEAN_CLOSE = true;
    public static final boolean NOT_CLEAN_CLOSE = false;

    private static final Logger LOG = Log.getLogger(Fuzzer.class);

    // Client side framing mask
    protected static final byte[] MASK =
    { 0x11, 0x22, 0x33, 0x44 };

    private final BlockheadClient client;
    private final Generator generator;
    private final String testname;
    private SendMode sendMode = SendMode.BULK;
    private int slowSendSegmentSize = 5;

    public Fuzzer(AbstractABCase testcase) throws Exception
    {
        WebSocketPolicy policy = WebSocketPolicy.newClientPolicy();

        int bigMessageSize = 20 * MBYTE;

        policy.setMaxPayloadSize(bigMessageSize);
        policy.setMaxTextMessageSize(bigMessageSize);
        policy.setMaxBinaryMessageSize(bigMessageSize);

        this.client = new BlockheadClient(policy,testcase.getServer().getServerUri());
        this.generator = testcase.getLaxGenerator();
        this.testname = testcase.testname.getMethodName();
    }

    public ByteBuffer asNetworkBuffer(List<WebSocketFrame> send)
    {
        int buflen = 0;
        for (Frame f : send)
        {
            buflen += f.getPayloadLength() + Generator.OVERHEAD;
        }
        ByteBuffer buf = ByteBuffer.allocate(buflen);
        BufferUtil.clearToFill(buf);

        // Generate frames
        for (WebSocketFrame f : send)
        {
            setClientMask(f);
            BufferUtil.put(generator.generate(f),buf);
        }
        BufferUtil.flipToFlush(buf,0);
        return buf;
    }

    public void close()
    {
        this.client.disconnect();
    }

    public void connect() throws IOException
    {
        if (!client.isConnected())
        {
            client.connect();
            client.addHeader("X-TestCase: " + testname + "\r\n");
            client.sendStandardRequest();
            client.expectUpgradeResponse();
        }
    }

    public void expect(List<WebSocketFrame> expect) throws IOException, TimeoutException
    {
        expect(expect,TimeUnit.SECONDS,10);
    }

    public void expect(List<WebSocketFrame> expect, TimeUnit unit, int duration) throws IOException, TimeoutException
    {
        int expectedCount = expect.size();
        LOG.debug("expect() {} frame(s)",expect.size());

        // Read frames
        IncomingFramesCapture capture = client.readFrames(expect.size(),unit,duration);
        if (LOG.isDebugEnabled())
        {
            capture.dump();
        }

        String prefix = "";
        for (int i = 0; i < expectedCount; i++)
        {
            WebSocketFrame expected = expect.get(i);
            WebSocketFrame actual = capture.getFrames().pop();

            prefix = "Frame[" + i + "]";

            LOG.debug("{} {}",prefix,actual);

            Assert.assertThat(prefix + ".opcode",OpCode.name(actual.getOpCode()),is(OpCode.name(expected.getOpCode())));
            prefix += "/" + actual.getOpCode();
            if (expected.getOpCode() == OpCode.CLOSE)
            {
                CloseInfo expectedClose = new CloseInfo(expected);
                CloseInfo actualClose = new CloseInfo(actual);
                Assert.assertThat(prefix + ".statusCode",actualClose.getStatusCode(),is(expectedClose.getStatusCode()));
            }
            else
            {
                Assert.assertThat(prefix + ".payloadLength",actual.getPayloadLength(),is(expected.getPayloadLength()));
                ByteBufferAssert.assertEquals(prefix + ".payload",expected.getPayload(),actual.getPayload());
            }
        }
    }

    public void expect(WebSocketFrame expect) throws IOException, TimeoutException
    {
        expect(Collections.singletonList(expect));
    }

    public void expectNoMoreFrames()
    {
        // TODO Should test for no more frames. success if connection closed.
    }

    public void expectServerClose(boolean wasClean) throws IOException, InterruptedException
    {
        // we expect that the close handshake to have occurred and the server should have closed the connection
        try
        {
            @SuppressWarnings("unused")
            int val = client.read();

            Assert.fail("Server has not closed socket");
        }
        catch (SocketException e)
        {

        }

        IOState ios = client.getIOState();

        if (wasClean)
        {
            Assert.assertTrue(ios.wasRemoteCloseInitiated());
            Assert.assertTrue(ios.wasCleanClose());
        }
        else
        {
            Assert.assertTrue(ios.wasRemoteCloseInitiated());
        }

    }

    public CloseState getCloseState()
    {
        IOState ios = client.getIOState();

        if (ios.wasLocalCloseInitiated())
        {
            return CloseState.LOCAL_INITIATED;
        }
        else if (ios.wasRemoteCloseInitiated())
        {
            return CloseState.REMOTE_INITIATED;
        }
        else
        {
            return CloseState.OPEN;
        }
    }

    public SendMode getSendMode()
    {
        return sendMode;
    }

    public int getSlowSendSegmentSize()
    {
        return slowSendSegmentSize;
    }

    public void send(ByteBuffer buf) throws IOException
    {
        Assert.assertThat("Client connected",client.isConnected(),is(true));
        LOG.debug("Sending bytes {}",BufferUtil.toDetailString(buf));
        if (sendMode == SendMode.SLOW)
        {
            client.writeRawSlowly(buf,slowSendSegmentSize);
        }
        else
        {
            client.writeRaw(buf);
        }
    }

    public void send(ByteBuffer buf, int numBytes) throws IOException
    {
        client.writeRaw(buf,numBytes);
        client.flush();
    }

    public void send(List<WebSocketFrame> send) throws IOException
    {
        Assert.assertThat("Client connected",client.isConnected(),is(true));
        LOG.debug("[{}] Sending {} frames (mode {})",testname,send.size(),sendMode);
        if ((sendMode == SendMode.BULK) || (sendMode == SendMode.SLOW))
        {
            int buflen = 0;
            for (Frame f : send)
            {
                buflen += f.getPayloadLength() + Generator.OVERHEAD;
            }
            ByteBuffer buf = ByteBuffer.allocate(buflen);
            BufferUtil.clearToFill(buf);

            // Generate frames
            for (WebSocketFrame f : send)
            {
                setClientMask(f);
                ByteBuffer rawbytes = generator.generate(f);
                if (LOG.isDebugEnabled())
                {
                    LOG.debug("frame: {}",f);
                    LOG.debug("bytes: {}",BufferUtil.toDetailString(rawbytes));
                }
                BufferUtil.put(rawbytes,buf);
            }
            BufferUtil.flipToFlush(buf,0);

            // Write Data Frame
            switch (sendMode)
            {
                case BULK:
                    client.writeRaw(buf);
                    break;
                case SLOW:
                    client.writeRawSlowly(buf,slowSendSegmentSize);
                    break;
                default:
                    throw new RuntimeException("Whoops, unsupported sendMode: " + sendMode);
            }
        }
        else if (sendMode == SendMode.PER_FRAME)
        {
            for (WebSocketFrame f : send)
            {
                f.setMask(MASK); // make sure we have mask set
                // Using lax generator, generate and send
                client.writeRaw(generator.generate(f));
                client.flush();
            }
        }
    }

    public void send(WebSocketFrame send) throws IOException
    {
        send(Collections.singletonList(send));
    }

    public void sendAndIgnoreBrokenPipe(List<WebSocketFrame> send) throws IOException
    {
        try
        {
            send(send);
        }
        catch (SocketException ignore)
        {
            // Potential for SocketException (Broken Pipe) here.
            // But not in 100% of testing scenarios. It is a safe
            // exception to ignore in this testing scenario, as the
            // slow writing of the frames can result in the server
            // throwing a PROTOCOL ERROR termination/close when it
            // encounters the bad continuation frame above (this
            // termination is the expected behavior), and this
            // early socket close can propagate back to the client
            // before it has a chance to finish writing out the
            // remaining frame octets
            Assert.assertThat("Allowed to be a broken pipe",ignore.getMessage().toLowerCase(Locale.ENGLISH),containsString("broken pipe"));
        }
    }

    public void sendExpectingIOException(ByteBuffer part3)
    {
        try
        {
            send(part3);
            Assert.fail("Expected a IOException on this send");
        }
        catch (IOException ignore)
        {
            // Send, but expect the send to fail with a IOException.
            // Usually, this is a SocketException("Socket Closed") condition.
        }
    }

    private void setClientMask(WebSocketFrame f)
    {
        if (LOG.isDebugEnabled())
        {
            f.setMask(new byte[]
            { 0x00, 0x00, 0x00, 0x00 });
        }
        else
        {
            f.setMask(MASK); // make sure we have mask set
        }
    }

    public void setSendMode(SendMode sendMode)
    {
        this.sendMode = sendMode;
    }

    public void setSlowSendSegmentSize(int segmentSize)
    {
        this.slowSendSegmentSize = segmentSize;
    }
}
