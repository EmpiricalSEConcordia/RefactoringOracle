// ========================================================================
// Copyright 2011-2012 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
//
//     The Eclipse Public License is available at
//     http://www.eclipse.org/legal/epl-v10.html
//
//     The Apache License v2.0 is available at
//     http://www.opensource.org/licenses/apache2.0.php
//
// You may elect to redistribute this code under either of these licenses.
//========================================================================
package org.eclipse.jetty.websocket.ab;

import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.eclipse.jetty.websocket.ByteBufferAssert;
import org.eclipse.jetty.websocket.api.ProtocolException;
import org.eclipse.jetty.websocket.api.WebSocketBehavior;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.protocol.Generator;
import org.eclipse.jetty.websocket.protocol.IncomingFramesCapture;
import org.eclipse.jetty.websocket.protocol.OpCode;
import org.eclipse.jetty.websocket.protocol.Parser;
import org.eclipse.jetty.websocket.protocol.UnitGenerator;
import org.eclipse.jetty.websocket.protocol.WebSocketFrame;
import org.junit.Assert;
import org.junit.Test;

public class TestABCase2
{
    WebSocketPolicy policy = new WebSocketPolicy(WebSocketBehavior.SERVER);

    @Test
    public void testGenerate125OctetPingCase2_4()
    {
        byte[] bytes = new byte[125];

        for ( int i = 0 ; i < bytes.length ; ++i )
        {
            bytes[i] = Integer.valueOf(Integer.toOctalString(i)).byteValue();
        }

        WebSocketFrame pingFrame = WebSocketFrame.ping().setPayload(bytes);

        Generator generator = new UnitGenerator();
        ByteBuffer actual = generator.generate(pingFrame);

        ByteBuffer expected = ByteBuffer.allocate(bytes.length + 32);

        expected.put(new byte[]
                { (byte)0x89 });

        byte b = 0x00; // no masking
        b |= bytes.length & 0x7F;
        expected.put(b);
        expected.put(bytes);

        expected.flip();

        ByteBufferAssert.assertEquals("buffers do not match",expected,actual);
    }

    @Test
    public void testGenerateBinaryPingCase2_3()
    {
        byte[] bytes = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 };

        WebSocketFrame pingFrame = WebSocketFrame.ping().setPayload(bytes);

        Generator generator = new UnitGenerator();
        ByteBuffer actual = generator.generate(pingFrame);

        ByteBuffer expected = ByteBuffer.allocate(32);

        expected.put(new byte[]
                { (byte)0x89 });

        byte b = 0x00; // no masking
        b |= bytes.length & 0x7F;
        expected.put(b);
        expected.put(bytes);

        expected.flip();

        ByteBufferAssert.assertEquals("buffers do not match",expected,actual);
    }


    @Test
    public void testGenerateEmptyPingCase2_1()
    {
        WebSocketFrame pingFrame = WebSocketFrame.ping();


        Generator generator = new UnitGenerator();
        ByteBuffer actual = generator.generate(pingFrame);

        ByteBuffer expected = ByteBuffer.allocate(5);

        expected.put(new byte[]
                { (byte)0x89, (byte)0x00 });

        expected.flip();

        ByteBufferAssert.assertEquals("buffers do not match",expected,actual);
    }

    @Test
    public void testGenerateHelloPingCase2_2()
    {
        String message = "Hello, world!";
        byte[] messageBytes = message.getBytes();

        WebSocketFrame pingFrame = WebSocketFrame.ping().setPayload(messageBytes);

        Generator generator = new UnitGenerator();
        ByteBuffer actual = generator.generate(pingFrame);

        ByteBuffer expected = ByteBuffer.allocate(32);

        expected.put(new byte[]
                { (byte)0x89 });

        byte b = 0x00; // no masking
        b |= messageBytes.length & 0x7F;
        expected.put(b);
        expected.put(messageBytes);

        expected.flip();

        ByteBufferAssert.assertEquals("buffers do not match",expected,actual);
    }

    @Test( expected=WebSocketException.class )
    public void testGenerateOversizedBinaryPingCase2_5_A()
    {
        byte[] bytes = new byte[126];

        for ( int i = 0 ; i < bytes.length ; ++i )
        {
            bytes[i] = 0x00;
        }

        WebSocketFrame.ping().setPayload(bytes);
    }

    @Test( expected=WebSocketException.class )
    public void testGenerateOversizedBinaryPingCase2_5_B()
    {
        byte[] bytes = new byte[126];

        for ( int i = 0 ; i < bytes.length ; ++i )
        {
            bytes[i] = 0x00;
        }

        WebSocketFrame pingFrame = WebSocketFrame.ping().setPayload(bytes);

        Generator generator = new UnitGenerator();
        generator.generate(pingFrame);
    }

    @Test
    public void testParse125OctetPingCase2_4()
    {
        byte[] bytes = new byte[125];

        for ( int i = 0 ; i < bytes.length ; ++i )
        {
            bytes[i] = Integer.valueOf(Integer.toOctalString(i)).byteValue();
        }

        ByteBuffer expected = ByteBuffer.allocate(bytes.length + 32);

        expected.put(new byte[]
                { (byte)0x89 });

        byte b = 0x00; // no masking
        b |= bytes.length & 0x7F;
        expected.put(b);
        expected.put(bytes);

        expected.flip();

        Parser parser = new Parser(policy);
        IncomingFramesCapture capture = new IncomingFramesCapture();
        parser.setIncomingFramesHandler(capture);
        parser.parse(expected);

        capture.assertNoErrors();
        capture.assertHasFrame(OpCode.PING,1);

        WebSocketFrame pActual = capture.getFrames().get(0);
        Assert.assertThat("PingFrame.payloadLength",pActual.getPayloadLength(),is(bytes.length));
        Assert.assertEquals("PingFrame.payload",bytes.length,pActual.getPayloadLength());
    }

    @Test
    public void testParseBinaryPingCase2_3()
    {
        byte[] bytes = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 };

        ByteBuffer expected = ByteBuffer.allocate(32);

        expected.put(new byte[]
                { (byte)0x89 });

        byte b = 0x00; // no masking
        b |= bytes.length & 0x7F;
        expected.put(b);
        expected.put(bytes);

        expected.flip();

        Parser parser = new Parser(policy);
        IncomingFramesCapture capture = new IncomingFramesCapture();
        parser.setIncomingFramesHandler(capture);
        parser.parse(expected);

        capture.assertNoErrors();
        capture.assertHasFrame(OpCode.PING,1);

        WebSocketFrame pActual = capture.getFrames().get(0);
        Assert.assertThat("PingFrame.payloadLength",pActual.getPayloadLength(),is(bytes.length));
        Assert.assertEquals("PingFrame.payload",bytes.length,pActual.getPayloadLength());
    }

    @Test
    public void testParseEmptyPingCase2_1()
    {
        ByteBuffer expected = ByteBuffer.allocate(5);

        expected.put(new byte[]
                { (byte)0x89, (byte)0x00 });

        expected.flip();

        Parser parser = new Parser(policy);
        IncomingFramesCapture capture = new IncomingFramesCapture();
        parser.setIncomingFramesHandler(capture);
        parser.parse(expected);

        capture.assertNoErrors();
        capture.assertHasFrame(OpCode.PING,1);

        WebSocketFrame pActual = capture.getFrames().get(0);
        Assert.assertThat("PingFrame.payloadLength",pActual.getPayloadLength(),is(0));
        Assert.assertEquals("PingFrame.payload",0,pActual.getPayloadLength());
    }

    @Test
    public void testParseHelloPingCase2_2()
    {
        String message = "Hello, world!";
        byte[] messageBytes = message.getBytes();

        ByteBuffer expected = ByteBuffer.allocate(32);

        expected.put(new byte[]
                { (byte)0x89 });

        byte b = 0x00; // no masking
        b |= messageBytes.length & 0x7F;
        expected.put(b);
        expected.put(messageBytes);

        expected.flip();

        Parser parser = new Parser(policy);
        IncomingFramesCapture capture = new IncomingFramesCapture();
        parser.setIncomingFramesHandler(capture);
        parser.parse(expected);

        capture.assertNoErrors();
        capture.assertHasFrame(OpCode.PING,1);

        WebSocketFrame pActual = capture.getFrames().get(0);
        Assert.assertThat("PingFrame.payloadLength",pActual.getPayloadLength(),is(message.length()));
        Assert.assertEquals("PingFrame.payload",message.length(),pActual.getPayloadLength());
    }

    @Test
    public void testParseOversizedBinaryPingCase2_5()
    {
        byte[] bytes = new byte[126];
        Arrays.fill(bytes,(byte)0x00);

        ByteBuffer expected = ByteBuffer.allocate(bytes.length + Generator.OVERHEAD);

        byte b;

        // fin + op
        b = 0x00;
        b |= 0x80; // fin on
        b |= 0x09; // ping
        expected.put(b);

        // mask + len
        b = 0x00;
        b |= 0x00; // no masking
        b |= 0x7E; // 2 byte len
        expected.put(b);

        // 2 byte len
        expected.putChar((char)bytes.length);

        // payload
        expected.put(bytes);

        expected.flip();

        Parser parser = new Parser(policy);
        IncomingFramesCapture capture = new IncomingFramesCapture();
        parser.setIncomingFramesHandler(capture);
        parser.parse(expected);

        Assert.assertEquals("error should be returned for too large of ping payload",1,capture.getErrorCount(ProtocolException.class));
    }

}
