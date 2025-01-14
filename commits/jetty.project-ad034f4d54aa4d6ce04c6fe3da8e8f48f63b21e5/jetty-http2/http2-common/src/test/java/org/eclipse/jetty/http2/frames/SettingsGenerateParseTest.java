//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.http2.frames;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.http2.generator.HeaderGenerator;
import org.eclipse.jetty.http2.generator.SettingsGenerator;
import org.eclipse.jetty.http2.parser.ErrorCode;
import org.eclipse.jetty.http2.parser.Parser;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.junit.Assert;
import org.junit.Test;

public class SettingsGenerateParseTest
{
    private final ByteBufferPool byteBufferPool = new MappedByteBufferPool();

    @Test
    public void testGenerateParseNoSettings() throws Exception
    {
        List<SettingsFrame> frames = testGenerateParse(Collections.<Integer, Integer>emptyMap());
        Assert.assertEquals(1, frames.size());
        SettingsFrame frame = frames.get(0);
        Assert.assertEquals(0, frame.getSettings().size());
        Assert.assertTrue(frame.isReply());
    }

    @Test
    public void testGenerateParseSettings() throws Exception
    {
        Map<Integer, Integer> settings1 = new HashMap<>();
        int key1 = 13;
        Integer value1 = 17;
        settings1.put(key1, value1);
        int key2 = 19;
        Integer value2 = 23;
        settings1.put(key2, value2);
        List<SettingsFrame> frames = testGenerateParse(settings1);
        Assert.assertEquals(1, frames.size());
        SettingsFrame frame = frames.get(0);
        Map<Integer, Integer> settings2 = frame.getSettings();
        Assert.assertEquals(2, settings2.size());
        Assert.assertEquals(value1, settings2.get(key1));
        Assert.assertEquals(value2, settings2.get(key2));
    }

    private List<SettingsFrame> testGenerateParse(Map<Integer, Integer> settings)
    {
        SettingsGenerator generator = new SettingsGenerator(new HeaderGenerator());

        // Iterate a few times to be sure generator and parser are properly reset.
        final List<SettingsFrame> frames = new ArrayList<>();
        for (int i = 0; i < 2; ++i)
        {
            ByteBufferPool.Lease lease = new ByteBufferPool.Lease(byteBufferPool);
            generator.generateSettings(lease, settings, true);
            Parser parser = new Parser(byteBufferPool, new Parser.Listener.Adapter()
            {
                @Override
                public boolean onSettings(SettingsFrame frame)
                {
                    frames.add(frame);
                    return false;
                }
            });

            frames.clear();
            for (ByteBuffer buffer : lease.getByteBuffers())
            {
                while (buffer.hasRemaining())
                {
                    parser.parse(buffer);
                }
            }
        }

        return frames;
    }

    @Test
    public void testGenerateParseInvalidSettings() throws Exception
    {
        SettingsGenerator generator = new SettingsGenerator(new HeaderGenerator());
        Map<Integer, Integer> settings1 = new HashMap<>();
        settings1.put(13, 17);
        ByteBufferPool.Lease lease = new ByteBufferPool.Lease(byteBufferPool);
        generator.generateSettings(lease, settings1, true);
        // Modify the length of the frame to make it invalid
        ByteBuffer bytes = lease.getByteBuffers().get(0);
        bytes.putShort(0, (short)(bytes.getShort(0) - 1));

        final AtomicInteger errorRef = new AtomicInteger();
        Parser parser = new Parser(byteBufferPool, new Parser.Listener.Adapter()
        {
            @Override
            public void onConnectionFailure(int error, String reason)
            {
                errorRef.set(error);
            }
        });

        for (ByteBuffer buffer : lease.getByteBuffers())
        {
            while (buffer.hasRemaining())
            {
                parser.parse(ByteBuffer.wrap(new byte[]{buffer.get()}));
            }
        }

        Assert.assertEquals(ErrorCode.PROTOCOL_ERROR, errorRef.get());
    }

    @Test
    public void testGenerateParseOneByteAtATime() throws Exception
    {
        SettingsGenerator generator = new SettingsGenerator(new HeaderGenerator());

        Map<Integer, Integer> settings1 = new HashMap<>();
        int key = 13;
        Integer value = 17;
        settings1.put(key, value);

        final List<SettingsFrame> frames = new ArrayList<>();
        ByteBufferPool.Lease lease = new ByteBufferPool.Lease(byteBufferPool);
        generator.generateSettings(lease, settings1, true);
        Parser parser = new Parser(byteBufferPool, new Parser.Listener.Adapter()
        {
            @Override
            public boolean onSettings(SettingsFrame frame)
            {
                frames.add(frame);
                return false;
            }
        });

        for (ByteBuffer buffer : lease.getByteBuffers())
        {
            while (buffer.hasRemaining())
            {
                parser.parse(ByteBuffer.wrap(new byte[]{buffer.get()}));
            }
        }

        Assert.assertEquals(1, frames.size());
        SettingsFrame frame = frames.get(0);
        Map<Integer, Integer> settings2 = frame.getSettings();
        Assert.assertEquals(1, settings2.size());
        Assert.assertEquals(value, settings2.get(key));
        Assert.assertTrue(frame.isReply());
    }
}
