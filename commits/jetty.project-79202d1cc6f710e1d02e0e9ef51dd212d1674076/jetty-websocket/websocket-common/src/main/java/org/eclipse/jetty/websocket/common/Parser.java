//
//  ========================================================================
//  Copyright (c) 1995-2016 Mort Bay Consulting Pty. Ltd.
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
import java.util.List;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.MessageTooLargeException;
import org.eclipse.jetty.websocket.api.ProtocolException;
import org.eclipse.jetty.websocket.api.WebSocketBehavior;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.extensions.Extension;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.api.extensions.IncomingFrames;
import org.eclipse.jetty.websocket.common.frames.BinaryFrame;
import org.eclipse.jetty.websocket.common.frames.CloseFrame;
import org.eclipse.jetty.websocket.common.frames.ContinuationFrame;
import org.eclipse.jetty.websocket.common.frames.ControlFrame;
import org.eclipse.jetty.websocket.common.frames.PingFrame;
import org.eclipse.jetty.websocket.common.frames.PongFrame;
import org.eclipse.jetty.websocket.common.frames.TextFrame;
import org.eclipse.jetty.websocket.common.io.payload.DeMaskProcessor;
import org.eclipse.jetty.websocket.common.io.payload.PayloadProcessor;

/**
 * Parsing of a frames in WebSocket land.
 */
public class Parser
{
    private enum State
    {
        START,
        PAYLOAD_LEN,
        PAYLOAD_LEN_BYTES,
        MASK,
        MASK_BYTES,
        PAYLOAD
    }

    private static final Logger LOG = Log.getLogger(Parser.class);
    private final WebSocketPolicy policy;
    private final ByteBufferPool bufferPool;

    // State specific
    private State state = State.START;
    private int cursor = 0;
    // Frame
    private WebSocketFrame frame;
    private boolean priorDataFrame;
    // payload specific
    private ByteBuffer payload;
    private int payloadLength;
    private PayloadProcessor maskProcessor = new DeMaskProcessor();
    // private PayloadProcessor strictnessProcessor;

    /** 
     * Is there an extension using RSV flag?
     * <p>
     * 
     * <pre>
     *   0100_0000 (0x40) = rsv1
     *   0010_0000 (0x20) = rsv2
     *   0001_0000 (0x10) = rsv3
     * </pre>
     */
    private byte flagsInUse=0x00;
    
    private IncomingFrames incomingFramesHandler;

    public Parser(WebSocketPolicy wspolicy, ByteBufferPool bufferPool)
    {
        this.bufferPool = bufferPool;
        this.policy = wspolicy;
    }

    private void assertSanePayloadLength(long len)
    {
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} Payload Length: {} - {}",policy.getBehavior(),len,this);
        }

        // Since we use ByteBuffer so often, having lengths over Integer.MAX_VALUE is really impossible.
        if (len > Integer.MAX_VALUE)
        {
            // OMG! Sanity Check! DO NOT WANT! Won't anyone think of the memory!
            throw new MessageTooLargeException("[int-sane!] cannot handle payload lengths larger than " + Integer.MAX_VALUE);
        }

        switch (frame.getOpCode())
        {
            case OpCode.CLOSE:
                if (len == 1)
                {
                    throw new ProtocolException("Invalid close frame payload length, [" + payloadLength + "]");
                }
                // fall thru
            case OpCode.PING:
            case OpCode.PONG:
                if (len > ControlFrame.MAX_CONTROL_PAYLOAD)
                {
                    throw new ProtocolException("Invalid control frame payload length, [" + payloadLength + "] cannot exceed ["
                            + ControlFrame.MAX_CONTROL_PAYLOAD + "]");
                }
                break;
            case OpCode.TEXT:
                policy.assertValidTextMessageSize((int)len);
                break;
            case OpCode.BINARY:
                policy.assertValidBinaryMessageSize((int)len);
                break;
        }
    }

    public void configureFromExtensions(List<? extends Extension> exts)
    {        
        // default
        flagsInUse = 0x00;

        // configure from list of extensions in use
        for (Extension ext : exts)
        {
            if (ext.isRsv1User())
            {
                flagsInUse = (byte)(flagsInUse | 0x40);
            }
            if (ext.isRsv2User())
            {
                flagsInUse = (byte)(flagsInUse | 0x20);
            }
            if (ext.isRsv3User())
            {
                flagsInUse = (byte)(flagsInUse | 0x10);
            }
        }
    }

    public IncomingFrames getIncomingFramesHandler()
    {
        return incomingFramesHandler;
    }

    public WebSocketPolicy getPolicy()
    {
        return policy;
    }

    public boolean isRsv1InUse()
    {
        return (flagsInUse & 0x40) != 0;
    }

    public boolean isRsv2InUse()
    {
        return (flagsInUse & 0x20) != 0;
    }

    public boolean isRsv3InUse()
    {
        return (flagsInUse & 0x10) != 0;
    }

    protected void notifyFrame(final Frame f)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("{} Notify {}",policy.getBehavior(),getIncomingFramesHandler());

        if (policy.getBehavior() == WebSocketBehavior.SERVER)
        {
            /* Parsing on server.
             * 
             * Then you MUST make sure all incoming frames are masked!
             * 
             * Technically, this test is in violation of RFC-6455, Section 5.1
             * http://tools.ietf.org/html/rfc6455#section-5.1
             * 
             * But we can't trust the client at this point, so Jetty opts to close
             * the connection as a Protocol error.
             */
            if (!f.isMasked())
            {
                throw new ProtocolException("Client MUST mask all frames (RFC-6455: Section 5.1)");
            }
        }
        else if(policy.getBehavior() == WebSocketBehavior.CLIENT)
        {
            // Required by RFC-6455 / Section 5.1
            if (f.isMasked())
            {
                throw new ProtocolException("Server MUST NOT mask any frames (RFC-6455: Section 5.1)");
            }
        }

        if (incomingFramesHandler == null)
        {
            return;
        }
        try
        {
            incomingFramesHandler.incomingFrame(f);
        }
        catch (WebSocketException e)
        {
            notifyWebSocketException(e);
        }
        catch (Throwable t)
        {
            LOG.warn(t);
            notifyWebSocketException(new WebSocketException(t));
        }
    }

    protected void notifyWebSocketException(WebSocketException e)
    {
        LOG.warn(e);
        if (incomingFramesHandler == null)
        {
            return;
        }
        incomingFramesHandler.incomingError(e);
    }

    public void parse(ByteBuffer buffer) throws WebSocketException
    {
        if (buffer.remaining() <= 0)
        {
            return;
        }
        try
        {
            // TODO: create DebugBuffer

            // parse through all the frames in the buffer
            while (parseFrame(buffer))
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("{} Parsed Frame: {}",policy.getBehavior(),frame);
                notifyFrame(frame);
                if (frame.isDataFrame())
                {
                    priorDataFrame = !frame.isFin();
                }
                reset();
            }
        }
        catch (WebSocketException e)
        {
            buffer.position(buffer.limit()); // consume remaining
            reset();
            // let session know
            notifyWebSocketException(e);
            // need to throw for proper close behavior in connection
            throw e;
        }
        catch (Throwable t)
        {
            buffer.position(buffer.limit()); // consume remaining
            reset();
            // let session know
            WebSocketException e = new WebSocketException(t);
            notifyWebSocketException(e);
            // need to throw for proper close behavior in connection
            throw e;
        }
    }

    private void reset()
    {
        if (frame != null)
            frame.reset();
        frame = null;
        bufferPool.release(payload);
        payload = null;
    }

    /**
     * Parse the base framing protocol buffer.
     * <p>
     * Note the first byte (fin,rsv1,rsv2,rsv3,opcode) are parsed by the {@link Parser#parse(ByteBuffer)} method
     * <p>
     * Not overridable
     * 
     * @param buffer
     *            the buffer to parse from.
     * @return true if done parsing base framing protocol and ready for parsing of the payload. false if incomplete parsing of base framing protocol.
     */
    private boolean parseFrame(ByteBuffer buffer)
    {
        if (LOG.isDebugEnabled())
        {
            LOG.debug("{} Parsing {} bytes",policy.getBehavior(),buffer.remaining());
        }
        while (buffer.hasRemaining())
        {
            switch (state)
            {
                case START:
                {
                    // peek at byte
                    byte b = buffer.get();
                    boolean fin = ((b & 0x80) != 0);
                    
                    byte opcode = (byte)(b & 0x0F);

                    if (!OpCode.isKnown(opcode))
                    {
                        throw new ProtocolException("Unknown opcode: " + opcode);
                    }
                    
                    if (LOG.isDebugEnabled())
                        LOG.debug("{} OpCode {}, fin={} rsv={}{}{}",
                                policy.getBehavior(),
                                OpCode.name(opcode),
                                fin,
                                (isRsv1InUse()?'1':'.'),
                                (isRsv2InUse()?'1':'.'),
                                (isRsv3InUse()?'1':'.'));

                    // base framing flags
                    switch(opcode)
                    {
                        case OpCode.TEXT:
                            frame = new TextFrame();
                            // data validation
                            if (priorDataFrame)
                            {
                                throw new ProtocolException("Unexpected " + OpCode.name(opcode) + " frame, was expecting CONTINUATION");
                            }
                            break;
                        case OpCode.BINARY:
                            frame = new BinaryFrame();
                            // data validation
                            if (priorDataFrame)
                            {
                                throw new ProtocolException("Unexpected " + OpCode.name(opcode) + " frame, was expecting CONTINUATION");
                            }
                            break;
                        case OpCode.CONTINUATION:
                            frame = new ContinuationFrame();
                            // continuation validation
                            if (!priorDataFrame)
                            {
                                throw new ProtocolException("CONTINUATION frame without prior !FIN");
                            }
                            // Be careful to use the original opcode
                            break;
                        case OpCode.CLOSE:
                            frame = new CloseFrame();
                            // control frame validation
                            if (!fin)
                            {
                                throw new ProtocolException("Fragmented Close Frame [" + OpCode.name(opcode) + "]");
                            }
                            break;
                        case OpCode.PING:
                            frame = new PingFrame();
                            // control frame validation
                            if (!fin)
                            {
                                throw new ProtocolException("Fragmented Ping Frame [" + OpCode.name(opcode) + "]");
                            }
                            break;
                        case OpCode.PONG:
                            frame = new PongFrame();
                            // control frame validation
                            if (!fin)
                            {
                                throw new ProtocolException("Fragmented Pong Frame [" + OpCode.name(opcode) + "]");
                            }
                            break;
                    }
                    
                    frame.setFin(fin);

                    // Are any flags set?
                    if ((b & 0x70) != 0)
                    {
                        /*
                         * RFC 6455 Section 5.2
                         * 
                         * MUST be 0 unless an extension is negotiated that defines meanings for non-zero values. If a nonzero value is received and none of the
                         * negotiated extensions defines the meaning of such a nonzero value, the receiving endpoint MUST _Fail the WebSocket Connection_.
                         */
                        if ((b & 0x40) != 0)
                        {
                            if (isRsv1InUse())
                                frame.setRsv1(true);
                            else
                                throw new ProtocolException("RSV1 not allowed to be set");   
                        }
                        if ((b & 0x20) != 0)
                        {
                            if (isRsv2InUse())
                                frame.setRsv2(true);
                            else
                                throw new ProtocolException("RSV2 not allowed to be set");   
                        }
                        if ((b & 0x10) != 0)
                        {
                            if (isRsv3InUse())
                                frame.setRsv3(true);
                            else
                                throw new ProtocolException("RSV3 not allowed to be set");   
                        }
                    }
                    
                    state = State.PAYLOAD_LEN;
                    break;
                }
                
                case PAYLOAD_LEN:
                {
                    byte b = buffer.get();
                    frame.setMasked((b & 0x80) != 0);
                    payloadLength = (byte)(0x7F & b);

                    if (payloadLength == 127) // 0x7F
                    {
                        // length 8 bytes (extended payload length)
                        payloadLength = 0;
                        state = State.PAYLOAD_LEN_BYTES;
                        cursor = 8;
                        break; // continue onto next state
                    }
                    else if (payloadLength == 126) // 0x7E
                    {
                        // length 2 bytes (extended payload length)
                        payloadLength = 0;
                        state = State.PAYLOAD_LEN_BYTES;
                        cursor = 2;
                        break; // continue onto next state
                    }

                    assertSanePayloadLength(payloadLength);
                    if (frame.isMasked())
                    {
                        state = State.MASK;
                    }
                    else
                    {
                        // special case for empty payloads (no more bytes left in buffer)
                        if (payloadLength == 0)
                        {
                            state = State.START;
                            return true;
                        }

                        maskProcessor.reset(frame);
                        state = State.PAYLOAD;
                    }

                    break;
                }
                
                case PAYLOAD_LEN_BYTES:
                {
                    byte b = buffer.get();
                    --cursor;
                    payloadLength |= (b & 0xFF) << (8 * cursor);
                    if (cursor == 0)
                    {
                        assertSanePayloadLength(payloadLength);
                        if (frame.isMasked())
                        {
                            state = State.MASK;
                        }
                        else
                        {
                            // special case for empty payloads (no more bytes left in buffer)
                            if (payloadLength == 0)
                            {
                                state = State.START;
                                return true;
                            }

                            maskProcessor.reset(frame);
                            state = State.PAYLOAD;
                        }
                    }
                    break;
                }
                
                case MASK:
                {
                    byte m[] = new byte[4];
                    frame.setMask(m);
                    if (buffer.remaining() >= 4)
                    {
                        buffer.get(m,0,4);
                        // special case for empty payloads (no more bytes left in buffer)
                        if (payloadLength == 0)
                        {
                            state = State.START;
                            return true;
                        }

                        maskProcessor.reset(frame);
                        state = State.PAYLOAD;
                    }
                    else
                    {
                        state = State.MASK_BYTES;
                        cursor = 4;
                    }
                    break;
                }
                
                case MASK_BYTES:
                {
                    byte b = buffer.get();
                    frame.getMask()[4 - cursor] = b;
                    --cursor;
                    if (cursor == 0)
                    {
                        // special case for empty payloads (no more bytes left in buffer)
                        if (payloadLength == 0)
                        {
                            state = State.START;
                            return true;
                        }

                        maskProcessor.reset(frame);
                        state = State.PAYLOAD;
                    }
                    break;
                }
                
                case PAYLOAD:
                {
                    frame.assertValid();
                    if (parsePayload(buffer))
                    {
                        // special check for close
                        if (frame.getOpCode() == OpCode.CLOSE)
                        {
                            // TODO: yuck. Don't create an object to do validation checks!
                            new CloseInfo(frame);
                        }
                        state = State.START;
                        // we have a frame!
                        return true;
                    }
                    break;
                }
            }
        }

        return false;
    }

    /**
     * Implementation specific parsing of a payload
     * 
     * @param buffer
     *            the payload buffer
     * @return true if payload is done reading, false if incomplete
     */
    private boolean parsePayload(ByteBuffer buffer)
    {        
        if (payloadLength == 0)
        {
            return true;
        }

        if (buffer.hasRemaining())
        {
            // Create a small window of the incoming buffer to work with.
            // this should only show the payload itself, and not any more
            // bytes that could belong to the start of the next frame.
            int bytesSoFar = payload == null ? 0 : payload.position();
            int bytesExpected = payloadLength - bytesSoFar;
            int bytesAvailable = buffer.remaining();
            int windowBytes = Math.min(bytesAvailable, bytesExpected);
            int limit = buffer.limit();
            buffer.limit(buffer.position() + windowBytes);
            ByteBuffer window = buffer.slice();
            buffer.limit(limit);
            buffer.position(buffer.position() + window.remaining());

            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Window: {}",policy.getBehavior(),BufferUtil.toDetailString(window));
            }

            maskProcessor.process(window);

            if (window.remaining() == payloadLength)
            {
                // We have the whole content, no need to copy.
                frame.setPayload(window);
                return true;
            }
            else
            {
                if (payload == null)
                {
                    payload = bufferPool.acquire(payloadLength,false);
                    BufferUtil.clearToFill(payload);
                }
                // Copy the payload.
                payload.put(window);

                if (payload.position() == payloadLength)
                {
                    BufferUtil.flipToFlush(payload, 0);
                    frame.setPayload(payload);
                    return true;
                }
            }
        }
        return false;
    }

    public void setIncomingFramesHandler(IncomingFrames incoming)
    {
        this.incomingFramesHandler = incoming;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Parser@").append(Integer.toHexString(hashCode()));
        builder.append("[");
        if (incomingFramesHandler == null)
        {
            builder.append("NO_HANDLER");
        }
        else
        {
            builder.append(incomingFramesHandler.getClass().getSimpleName());
        }
        builder.append(",s=").append(state);
        builder.append(",c=").append(cursor);
        builder.append(",len=").append(payloadLength);
        builder.append(",f=").append(frame);
        builder.append(",p=").append(policy);
        builder.append("]");
        return builder.toString();
    }
}
