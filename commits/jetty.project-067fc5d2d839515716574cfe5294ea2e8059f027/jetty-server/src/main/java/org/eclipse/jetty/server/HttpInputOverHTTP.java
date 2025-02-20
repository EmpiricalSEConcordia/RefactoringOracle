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

package org.eclipse.jetty.server;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.SharedBlockingCallback;
import org.eclipse.jetty.util.SharedBlockingCallback.Blocker;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class HttpInputOverHTTP extends HttpInput<ByteBuffer> implements Callback
{
    private static final Logger LOG = Log.getLogger(HttpInputOverHTTP.class);
    private final SharedBlockingCallback _readBlocker = new SharedBlockingCallback();
    private final HttpConnection _httpConnection;
    private ByteBuffer _content;

    /**
     * @param httpConnection
     */
    public HttpInputOverHTTP(HttpConnection httpConnection)
    {
        _httpConnection = httpConnection;
    }

    @Override
    public void recycle()
    {
        synchronized (lock())
        {
            super.recycle();
            _content=null;
        }
    }

    @Override
    protected void blockForContent() throws IOException
    {
        while(true)
        {
            try (Blocker blocker=_readBlocker.acquire())
            {            
                _httpConnection.fillInterested(blocker);
                if (LOG.isDebugEnabled())
                    LOG.debug("{} block readable on {}",this,blocker);
                blocker.block();
            }

            Object content=getNextContent();
            if (content!=null || isFinished())
                break;
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x",getClass().getSimpleName(),hashCode());
    }

    @Override
    protected ByteBuffer nextContent() throws IOException
    {
        // If we have some content available, return it
        if (BufferUtil.hasContent(_content))
            return _content;

        // No - then we are going to need to parse some more content
        _content=null;
        _httpConnection.parseContent();
        
        // If we have some content available, return it
        if (BufferUtil.hasContent(_content))
            return _content;

        return null;

    }

    @Override
    protected int remaining(ByteBuffer item)
    {
        return item.remaining();
    }

    @Override
    protected int get(ByteBuffer item, byte[] buffer, int offset, int length)
    {
        int l = Math.min(item.remaining(), length);
        item.get(buffer, offset, l);
        return l;
    }

    @Override
    protected void consume(ByteBuffer item, int length)
    {
        item.position(item.position()+length);
    }

    @Override
    public void content(ByteBuffer item)
    {
        if (BufferUtil.hasContent(_content))
            throw new IllegalStateException();
        _content=item;
    }

    @Override
    protected void unready()
    {
        _httpConnection.fillInterested(this);
    }

    @Override
    public void succeeded()
    {
        _httpConnection.getHttpChannel().getState().onReadPossible();
    }

    @Override
    public void failed(Throwable x)
    {
        super.failed(x);
        _httpConnection.getHttpChannel().getState().onReadPossible();
    }
}
