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

package org.eclipse.jetty.websocket.common;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.common.frames.BinaryFrame;
import org.eclipse.jetty.websocket.common.frames.CloseFrame;
import org.eclipse.jetty.websocket.common.frames.PingFrame;
import org.eclipse.jetty.websocket.common.frames.TextFrame;
import org.eclipse.jetty.websocket.common.test.IncomingFramesCapture;
import org.eclipse.jetty.websocket.common.test.UnitGenerator;
import org.eclipse.jetty.websocket.common.test.UnitParser;
import org.eclipse.jetty.websocket.common.util.Hex;
import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.Matchers.is;

public class GeneratorTest
{
    private static final Logger LOG = Log.getLogger(GeneratorTest.WindowHelper.class);

    public static class WindowHelper
    {
        final int windowSize;
        int totalParts;
        int totalBytes;

        public WindowHelper(int windowSize)
        {
            this.windowSize = windowSize;
            this.totalParts = 0;
            this.totalBytes = 0;
        }

        public ByteBuffer generateWindowed(Frame... frames)
        {
            // Create Buffer to hold all generated frames in a single buffer
            int completeBufSize = 0;
            for (Frame f : frames)
            {
                completeBufSize += Generator.MAX_HEADER_LENGTH + f.getPayloadLength();
            }

            ByteBuffer completeBuf = ByteBuffer.allocate(completeBufSize);
            BufferUtil.clearToFill(completeBuf);

            // Generate from all frames
            Generator generator = new UnitGenerator();

            for (Frame f : frames)
            {
                ByteBuffer header = generator.generateHeaderBytes(f);
                totalBytes += BufferUtil.put(header,completeBuf);

                if (f.hasPayload())
                {
                    ByteBuffer payload=f.getPayload();
                    totalBytes += payload.remaining();
                    totalParts++;
                    completeBuf.put(payload.slice());
                }
            }

            // Return results
            BufferUtil.flipToFlush(completeBuf,0);
            return completeBuf;
        }

        public void assertTotalParts(int expectedParts)
        {
            Assert.assertThat("Generated Parts",totalParts,is(expectedParts));
        }

        public void assertTotalBytes(int expectedBytes)
        {
            Assert.assertThat("Generated Bytes",totalBytes,is(expectedBytes));
        }
    }

    private void assertGeneratedBytes(CharSequence expectedBytes, Frame... frames)
    {
        // collect up all frames as single ByteBuffer
        ByteBuffer allframes = UnitGenerator.generate(frames);
        // Get hex String form of all frames bytebuffer.
        String actual = Hex.asHex(allframes);
        // Validate
        Assert.assertThat("Buffer",actual,is(expectedBytes.toString()));
    }

    private String asMaskedHex(String str, byte[] maskingKey)
    {
        byte utf[] = StringUtil.getUtf8Bytes(str);
        mask(utf,maskingKey);
        return Hex.asHex(utf);
    }

    private void mask(byte[] buf, byte[] maskingKey)
    {
        int size = buf.length;
        for (int i = 0; i < size; i++)
        {
            buf[i] ^= maskingKey[i % 4];
        }
    }

    @Test
    public void testClose_Empty()
    {
        // 0 byte payload (no status code)
        assertGeneratedBytes("8800",new CloseFrame());
    }

    @Test
    public void testClose_CodeNoReason()
    {
        CloseInfo close = new CloseInfo(StatusCode.NORMAL);
        // 2 byte payload (2 bytes for status code)
        assertGeneratedBytes("880203E8",close.asFrame());
    }

    @Test
    public void testClose_CodeOkReason()
    {
        CloseInfo close = new CloseInfo(StatusCode.NORMAL,"OK");
        // 4 byte payload (2 bytes for status code, 2 more for "OK")
        assertGeneratedBytes("880403E84F4B",close.asFrame());
    }

    @Test
    public void testText_Hello()
    {
        WebSocketFrame frame = new TextFrame().setPayload("Hello");
        byte utf[] = StringUtil.getUtf8Bytes("Hello");
        assertGeneratedBytes("8105" + Hex.asHex(utf),frame);
    }

    @Test
    public void testText_Masked()
    {
        WebSocketFrame frame = new TextFrame().setPayload("Hello");
        byte maskingKey[] = Hex.asByteArray("11223344");
        frame.setMask(maskingKey);

        // what is expected
        StringBuilder expected = new StringBuilder();
        expected.append("8185").append("11223344");
        expected.append(asMaskedHex("Hello",maskingKey));

        // validate
        assertGeneratedBytes(expected,frame);
    }

    @Test
    public void testText_Masked_OffsetSourceByteBuffer()
    {
        ByteBuffer payload = ByteBuffer.allocate(100);
        payload.position(5);
        payload.put(StringUtil.getUtf8Bytes("Hello"));
        payload.flip();
        payload.position(5);
        // at this point, we have a ByteBuffer of 100 bytes.
        // but only a few bytes in the middle are made available for the payload.
        // we are testing that masking works as intended, even if the provided
        // payload does not start at position 0.
        LOG.debug("Payload = {}",BufferUtil.toDetailString(payload));
        WebSocketFrame frame = new TextFrame().setPayload(payload);
        byte maskingKey[] = Hex.asByteArray("11223344");
        frame.setMask(maskingKey);

        // what is expected
        StringBuilder expected = new StringBuilder();
        expected.append("8185").append("11223344");
        expected.append(asMaskedHex("Hello",maskingKey));

        // validate
        assertGeneratedBytes(expected,frame);
    }

    /**
     * Prevent regression of masking of many packets.
     */
    @Test
    public void testManyMasked()
    {
        int pingCount = 2;

        // Prepare frames
        WebSocketFrame[] frames = new WebSocketFrame[pingCount + 1];
        for (int i = 0; i < pingCount; i++)
        {
            frames[i] = new PingFrame().setPayload(String.format("ping-%d",i));
        }
        frames[pingCount] = new CloseInfo(StatusCode.NORMAL).asFrame();

        // Mask All Frames
        byte maskingKey[] = Hex.asByteArray("11223344");
        for (WebSocketFrame f : frames)
        {
            f.setMask(maskingKey);
        }

        // Validate result of generation
        StringBuilder expected = new StringBuilder();
        expected.append("8986").append("11223344");
        expected.append(asMaskedHex("ping-0",maskingKey)); // ping 0
        expected.append("8986").append("11223344");
        expected.append(asMaskedHex("ping-1",maskingKey)); // ping 1
        expected.append("8882").append("11223344");
        byte closure[] = Hex.asByteArray("03E8");
        mask(closure,maskingKey);
        expected.append(Hex.asHex(closure)); // normal closure

        assertGeneratedBytes(expected,frames);
    }

    /**
     * Test the windowed generate of a frame that has no masking.
     */
    @Test
    public void testWindowedGenerate()
    {
        // A decent sized frame, no masking
        byte payload[] = new byte[10240];
        Arrays.fill(payload,(byte)0x44);

        WebSocketFrame frame = new BinaryFrame().setPayload(payload);

        // Generate
        int windowSize = 1024;
        WindowHelper helper = new WindowHelper(windowSize);
        ByteBuffer completeBuffer = helper.generateWindowed(frame);

        // Validate
        int expectedHeaderSize = 4;
        int expectedSize = payload.length + expectedHeaderSize;
        int expectedParts = 1;

        helper.assertTotalParts(expectedParts);
        helper.assertTotalBytes(payload.length + expectedHeaderSize);

        Assert.assertThat("Generated Buffer",completeBuffer.remaining(),is(expectedSize));
    }

    @Test
    public void testWindowedGenerateWithMasking()
    {
        // A decent sized frame, with masking
        byte payload[] = new byte[10240];
        Arrays.fill(payload,(byte)0x55);

        byte mask[] = new byte[]
        { 0x2A, (byte)0xF0, 0x0F, 0x00 };

        WebSocketFrame frame = new BinaryFrame().setPayload(payload);
        frame.setMask(mask); // masking!

        // Generate
        int windowSize = 2929;
        WindowHelper helper = new WindowHelper(windowSize);
        ByteBuffer completeBuffer = helper.generateWindowed(frame);

        // Validate
        int expectedHeaderSize = 8;
        int expectedSize = payload.length + expectedHeaderSize;
        int expectedParts = 1;

        helper.assertTotalParts(expectedParts);
        helper.assertTotalBytes(payload.length + expectedHeaderSize);

        Assert.assertThat("Generated Buffer",completeBuffer.remaining(),is(expectedSize));

        // Parse complete buffer.
        WebSocketPolicy policy = WebSocketPolicy.newServerPolicy();
        Parser parser = new UnitParser(policy);
        IncomingFramesCapture capture = new IncomingFramesCapture();
        parser.setIncomingFramesHandler(capture);

        parser.parse(completeBuffer);

        // Assert validity of frame
        WebSocketFrame actual = capture.getFrames().poll();
        Assert.assertThat("Frame.opcode",actual.getOpCode(),is(OpCode.BINARY));
        Assert.assertThat("Frame.payloadLength",actual.getPayloadLength(),is(payload.length));

        // Validate payload contents for proper masking
        ByteBuffer actualData = actual.getPayload().slice();
        Assert.assertThat("Frame.payload.remaining",actualData.remaining(),is(payload.length));
        while (actualData.remaining() > 0)
        {
            Assert.assertThat("Actual.payload[" + actualData.position() + "]",actualData.get(),is((byte)0x55));
        }
    }
}
