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

package org.eclipse.jetty.http2.generator;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http2.frames.Flag;
import org.eclipse.jetty.http2.frames.Frame;
import org.eclipse.jetty.http2.frames.FrameType;
import org.eclipse.jetty.http2.frames.GoAwayFrame;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;

public class GoAwayGenerator extends FrameGenerator
{
    public GoAwayGenerator(HeaderGenerator headerGenerator)
    {
        super(headerGenerator);
    }

    @Override
    public void generate(ByteBufferPool.Lease lease, Frame frame, Callback callback)
    {
        GoAwayFrame goAwayFrame = (GoAwayFrame)frame;
        generateGoAway(lease, goAwayFrame.getLastStreamId(), goAwayFrame.getError(), null);
    }

    public void generateGoAway(ByteBufferPool.Lease lease, int lastStreamId, int error, byte[] payload)
    {
        if (lastStreamId < 0)
            throw new IllegalArgumentException("Invalid last stream id: " + lastStreamId);

        int length = 4 + 4 + (payload != null ? payload.length : 0);
        ByteBuffer header = generateHeader(lease, FrameType.GO_AWAY, length, Flag.NONE, 0);

        header.putInt(lastStreamId);
        header.putInt(error);

        if (payload != null)
        {
            header.put(payload);
        }

        BufferUtil.flipToFlush(header, 0);
        lease.append(header, true);
    }
}
