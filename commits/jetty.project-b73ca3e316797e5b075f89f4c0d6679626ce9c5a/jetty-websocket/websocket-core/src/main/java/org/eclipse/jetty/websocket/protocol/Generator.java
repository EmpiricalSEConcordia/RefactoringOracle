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
package org.eclipse.jetty.websocket.protocol;

import java.nio.ByteBuffer;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.ProtocolException;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;

/**
 * Generating a frame in WebSocket land.
 * 
 * <pre>
 *    0                   1                   2                   3
 *    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *   +-+-+-+-+-------+-+-------------+-------------------------------+
 *   |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
 *   |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
 *   |N|V|V|V|       |S|             |   (if payload len==126/127)   |
 *   | |1|2|3|       |K|             |                               |
 *   +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
 *   |     Extended payload length continued, if payload len == 127  |
 *   + - - - - - - - - - - - - - - - +-------------------------------+
 *   |                               |Masking-key, if MASK set to 1  |
 *   +-------------------------------+-------------------------------+
 *   | Masking-key (continued)       |          Payload Data         |
 *   +-------------------------------- - - - - - - - - - - - - - - - +
 *   :                     Payload Data continued ...                :
 *   + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
 *   |                     Payload Data continued ...                |
 *   +---------------------------------------------------------------+
 * </pre>
 */
public class Generator
{
    private static final Logger LOG = Log.getLogger(Generator.class);
    /**
     * The overhead (maximum) for a framing header. Assuming a maximum sized payload with masking key.
     */
    public static final int OVERHEAD = 28;

    private final WebSocketPolicy policy;
    private final ByteBufferPool bufferPool;
    private boolean validating;

    /** Is there an extension using RSV1 */
    private boolean rsv1InUse = false;
    /** Is there an extension using RSV2 */
    private boolean rsv2InUse = false;
    /** Is there an extension using RSV3 */
    private boolean rsv3InUse = false;

    /**
     * Construct Generator with provided policy and bufferPool
     * 
     * @param policy
     *            the policy to use
     * @param bufferPool
     *            the buffer pool to use
     */
    public Generator(WebSocketPolicy policy, ByteBufferPool bufferPool)
    {
        this(policy,bufferPool,true);
    }

    /**
     * Construct Generator with provided policy and bufferPool
     * 
     * @param policy
     *            the policy to use
     * @param bufferPool
     *            the buffer pool to use
     * @param validating
     *            true to enable RFC frame validation
     */
    public Generator(WebSocketPolicy policy, ByteBufferPool bufferPool, boolean validating)
    {
        this.policy = policy;
        this.bufferPool = bufferPool;
        this.validating = validating;
    }

    public void assertFrameValid(WebSocketFrame frame)
    {
        if (!validating)
        {
            return;
        }

        /*
         * RFC 6455 Section 5.2
         * 
         * MUST be 0 unless an extension is negotiated that defines meanings for non-zero values. If a nonzero value is received and none of the negotiated
         * extensions defines the meaning of such a nonzero value, the receiving endpoint MUST _Fail the WebSocket Connection_.
         */
        if (!rsv1InUse && frame.isRsv1())
        {
            throw new ProtocolException("RSV1 not allowed to be set");
        }

        if (!rsv2InUse && frame.isRsv2())
        {
            throw new ProtocolException("RSV2 not allowed to be set");
        }

        if (!rsv3InUse && frame.isRsv3())
        {
            throw new ProtocolException("RSV3 not allowed to be set");
        }

        if (frame.isControlFrame())
        {
            /*
             * RFC 6455 Section 5.5
             * 
             * All control frames MUST have a payload length of 125 bytes or less and MUST NOT be fragmented.
             */
            if (frame.getPayloadLength() > 125)
            {
                throw new ProtocolException("Invalid control frame payload length");
            }

            if (!frame.isFin())
            {
                throw new ProtocolException("Control Frames must be FIN=true");
            }

            /*
             * RFC 6455 Section 5.5.1
             * 
             * close frame payload is specially formatted which is checked in CloseInfo
             */
            if (frame.getOpCode() == OpCode.CLOSE)
            {

                ByteBuffer payload = frame.getPayload();
                if (payload != null)
                {
                    new CloseInfo(payload,true);
                }
            }
        }

    }

    /**
     * Generate, into a ByteBuffer, no more than bufferSize of contents from the frame. If the frame exceeds the bufferSize, then multiple calls to
     * {@link #generate(int, WebSocketFrame)} are required to obtain each window of ByteBuffer to complete the frame.
     */
    public ByteBuffer generate(int windowSize, WebSocketFrame frame)
    {
        if (windowSize < OVERHEAD)
        {
            throw new IllegalArgumentException("Cannot have windowSize less than " + OVERHEAD);
        }

        if (LOG.isDebugEnabled())
        {
            StringBuilder dbg = new StringBuilder();
            dbg.append(policy.getBehavior());
            dbg.append(" Generate.Frame[");
            dbg.append("opcode=").append(frame.getOpCode());
            dbg.append(",fin=").append(frame.isFin());
            dbg.append(",cont=").append(frame.isContinuation());
            dbg.append(",rsv1=").append(frame.isRsv1());
            dbg.append(",rsv2=").append(frame.isRsv2());
            dbg.append(",rsv3=").append(frame.isRsv3());
            dbg.append(",mask=").append(frame.isMasked());
            dbg.append(",payloadLength=").append(frame.getPayloadLength());
            dbg.append(",payloadStart=").append(frame.getPayloadStart());
            dbg.append(",remaining=").append(frame.remaining());
            dbg.append(",position=").append(frame.position());
            dbg.append(']');
            LOG.debug(dbg.toString());
        }

        /*
         * prepare the byte buffer to put frame into
         */
        ByteBuffer buffer = bufferPool.acquire(windowSize,true);
        BufferUtil.clearToFill(buffer);
        // since the buffer from the pool can exceed the window size, artificially
        // limit the buffer to the window size.
        buffer.limit(buffer.position() + windowSize);

        if (frame.remaining() == frame.getPayloadLength())
        {
            // we need a framing header
            assertFrameValid(frame);

            /*
             * start the generation process
             */
            byte b;

            // Setup fin thru opcode
            b = 0x00;
            if (frame.isFin())
            {
                b |= 0x80; // 1000_0000
            }
            if (frame.isRsv1())
            {
                b |= 0x40; // 0100_0000
            }
            if (frame.isRsv2())
            {
                b |= 0x20; // 0010_0000
            }
            if (frame.isRsv3())
            {
                b |= 0x10;
            }

            byte opcode = frame.getOpCode();

            if (frame.isContinuation())
            {
                // Continuations are not the same OPCODE
                opcode = OpCode.CONTINUATION;
            }

            b |= opcode & 0x0F;

            buffer.put(b);

            // is masked
            b = 0x00;
            b |= (frame.isMasked()?0x80:0x00);

            // payload lengths
            int payloadLength = frame.getPayloadLength();

            /*
             * if length is over 65535 then its a 7 + 64 bit length
             */
            if (payloadLength > 0xFF_FF)
            {
                // we have a 64 bit length
                b |= 0x7F;
                buffer.put(b); // indicate 8 byte length
                buffer.put((byte)0); //
                buffer.put((byte)0); // anything over an
                buffer.put((byte)0); // int is just
                buffer.put((byte)0); // intsane!
                buffer.put((byte)((payloadLength >> 24) & 0xFF));
                buffer.put((byte)((payloadLength >> 16) & 0xFF));
                buffer.put((byte)((payloadLength >> 8) & 0xFF));
                buffer.put((byte)(payloadLength & 0xFF));
            }
            /*
             * if payload is ge 126 we have a 7 + 16 bit length
             */
            else if (payloadLength >= 0x7E)
            {
                b |= 0x7E;
                buffer.put(b); // indicate 2 byte length
                buffer.put((byte)(payloadLength >> 8));
                buffer.put((byte)(payloadLength & 0xFF));
            }
            /*
             * we have a 7 bit length
             */
            else
            {
                b |= (payloadLength & 0x7F);
                buffer.put(b);
            }

            // masking key
            if (frame.isMasked())
            {
                buffer.put(frame.getMask());
            }
        }

        // copy payload
        if (frame.hasPayload())
        {
            // remember the position
            int maskingStartPosition = buffer.position();

            // remember the offset within the frame payload (for working with
            // windowed frames that don't split on 4 byte barriers)
            int payloadOffset = frame.getPayload().position();
            int payloadStart = frame.getPayloadStart();

            // put as much as possible into the buffer
            BufferUtil.put(frame.getPayload(),buffer);

            // mask it if needed
            if (frame.isMasked())
            {
                // move back to remembered position.
                int size = buffer.position() - maskingStartPosition;
                byte[] mask = frame.getMask();
                byte b;
                int posBuf;
                int posFrame;
                for (int i = 0; i < size; i++)
                {
                    posBuf = i + maskingStartPosition;
                    posFrame = i + (payloadOffset - payloadStart);

                    // get raw byte from buffer.
                    b = buffer.get(posBuf);

                    // mask, using offset information from frame windowing.
                    b ^= mask[posFrame % 4];

                    // Mask each byte by its absolute position in the bytebuffer
                    buffer.put(posBuf,b);
                }
            }
        }

        BufferUtil.flipToFlush(buffer,0);
        return buffer;
    }

    /**
     * generate a byte buffer based on the frame being passed in
     * 
     * bufferSize is determined by the length of the payload + 28 for frame overhead
     * 
     * @param frame
     * @return
     */
    public ByteBuffer generate(WebSocketFrame frame)
    {
        int bufferSize = frame.getPayloadLength() + OVERHEAD;
        return generate(bufferSize,frame);
    }

    public boolean isRsv1InUse()
    {
        return rsv1InUse;
    }

    public boolean isRsv2InUse()
    {
        return rsv2InUse;
    }

    public boolean isRsv3InUse()
    {
        return rsv3InUse;
    }

    public void setRsv1InUse(boolean rsv1InUse)
    {
        this.rsv1InUse = rsv1InUse;
    }

    public void setRsv2InUse(boolean rsv2InUse)
    {
        this.rsv2InUse = rsv2InUse;
    }

    public void setRsv3InUse(boolean rsv3InUse)
    {
        this.rsv3InUse = rsv3InUse;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Generator[");
        builder.append(policy.getBehavior());
        if (validating)
        {
            builder.append(",validating");
        }
        if (rsv1InUse)
        {
            builder.append(",+rsv1");
        }
        if (rsv2InUse)
        {
            builder.append(",+rsv2");
        }
        if (rsv3InUse)
        {
            builder.append(",+rsv3");
        }
        builder.append("]");
        return builder.toString();
    }
}
