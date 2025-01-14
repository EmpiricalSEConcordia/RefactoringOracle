//
//  ========================================================================
//  Copyright (c) 1995-2012 Mort Bay Consulting Pty. Ltd.
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
import java.io.InterruptedIOException;
import javax.servlet.ServletInputStream;

import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.util.ArrayQueue;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * <p>{@link HttpInput} provides an implementation of {@link ServletInputStream} for {@link HttpChannel}.</p>
 * <p>{@link HttpInput} holds a queue of items passed to it by calls to {@link #content(T)}.</p>
 * <p>{@link HttpInput} stores the items directly; if the items contain byte buffers, it does not copy them
 * but simply holds references to the item, thus the caller must organize for those buffers to valid while
 * held by this class.</p>
 * <p>To assist the caller, subclasses may override methods {@link #onContentQueued(T)},
 * {@link #onContentConsumed(T)} and {@link #onAllContentConsumed()} that can be implemented so that the
 * caller will know when buffers are queued and consumed.</p>
 */
public abstract class HttpInput<T> extends ServletInputStream
{
    private final static Logger LOG = Log.getLogger(HttpInput.class);
    private final ArrayQueue<T> _inputQ = new ArrayQueue<>();
    private boolean _earlyEOF;
    private boolean _inputEOF;

    public Object lock()
    {
        return _inputQ.lock();
    }

    public void recycle()
    {
        synchronized (lock())
        {
            T item = _inputQ.peekUnsafe();
            while (item != null)
            {
                _inputQ.pollUnsafe();
                onContentConsumed(item);

                item = _inputQ.peekUnsafe();
                if (item == null)
                    onAllContentConsumed();
            }
            _inputEOF = false;
            _earlyEOF = false;
        }
    }

    @Override
    public int read() throws IOException
    {
        byte[] oneByte = new byte[1];
        int len = read(oneByte, 0, 1);
        return len < 0 ? len : 0xFF & oneByte[0];
    }

    @Override
    public int available()
    {
        synchronized (lock())
        {
            T item = _inputQ.peekUnsafe();
            if (item == null)
                return 0;
            return remaining(item);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        T item = null;
        synchronized (lock())
        {
            while (item == null)
            {
                item = _inputQ.peekUnsafe();
                while (item != null && remaining(item) == 0)
                {
                    _inputQ.pollUnsafe();
                    onContentConsumed(item);
                    LOG.debug("{} consumed {}", this, item);
                    item = _inputQ.peekUnsafe();
                }

                if (item == null)
                {
                    onAllContentConsumed();

                    if (isEarlyEOF())
                        throw new EofException();

                    // check for EOF
                    if (isShutdown())
                    {
                        onEOF();
                        return -1;
                    }

                    blockForContent();
                }
            }
        }
        return get(item, b, off, len);
    }

    protected abstract int remaining(T item);

    protected abstract int get(T item, byte[] buffer, int offset, int length);

    protected abstract void onContentConsumed(T item);

    protected void blockForContent() throws IOException
    {
        synchronized (lock())
        {
            while (_inputQ.isEmpty() && !isShutdown() && !isEarlyEOF())
            {
                try
                {
                    LOG.debug("{} waiting for content", this);
                    lock().wait();
                }
                catch (InterruptedException e)
                {
                    throw (IOException)new InterruptedIOException().initCause(e);
                }
            }
        }
    }

    protected void onContentQueued(T item)
    {
        lock().notify();
    }

    protected void onAllContentConsumed()
    {
    }

    protected void onEOF()
    {
    }

    public boolean content(T item)
    {
        synchronized (lock())
        {
            // The buffer is not copied here.  This relies on the caller not recycling the buffer
            // until the it is consumed.  The onAllContentConsumed() callback is the signal to the
            // caller that the buffers can be recycled.
            _inputQ.add(item);
            onContentQueued(item);
            LOG.debug("{} queued {}", this, item);
        }
        return true;
    }

    public void earlyEOF()
    {
        synchronized (lock())
        {
            _earlyEOF = true;
            lock().notify();
            LOG.debug("{} early EOF", this);
        }
    }

    public boolean isEarlyEOF()
    {
        synchronized (lock())
        {
            return _earlyEOF;
        }
    }

    public void shutdown()
    {
        synchronized (lock())
        {
            _inputEOF = true;
            lock().notify();
            LOG.debug("{} shutdown", this);
        }
    }

    public boolean isShutdown()
    {
        synchronized (lock())
        {
            return _inputEOF;
        }
    }

    public void consumeAll()
    {
        synchronized (lock())
        {
            while (!isShutdown() && !isEarlyEOF())
            {
                T item = _inputQ.peekUnsafe();
                while (item != null)
                {
                    _inputQ.pollUnsafe();
                    onContentConsumed(item);

                    item = _inputQ.peekUnsafe();
                    if (item == null)
                        onAllContentConsumed();
                }

                try
                {
                    blockForContent();
                }
                catch (IOException e)
                {
                    throw new RuntimeIOException(e);
                }
            }
        }
    }
}
