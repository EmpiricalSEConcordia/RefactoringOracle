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

package org.eclipse.jetty.client.http;

import java.nio.channels.AsynchronousCloseException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.client.HttpConnection;
import org.eclipse.jetty.client.HttpDestination;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.client.api.Connection;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Sweeper;

public class HttpConnectionOverHTTP extends AbstractConnection implements Connection, Sweeper.Sweepable
{
    private static final Logger LOG = Log.getLogger(HttpConnectionOverHTTP.class);

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger sweeps = new AtomicInteger();
    private final Promise<Connection> promise;
    private final Delegate delegate;
    private final HttpChannelOverHTTP channel;
    private long idleTimeout;
    private int idleTimeoutGuard;
    private long idleTimeoutStamp;

    /**
     * @deprecated use {@link #HttpConnectionOverHTTP(EndPoint, HttpDestination, Promise)} instead
     */
    @Deprecated
    public HttpConnectionOverHTTP(EndPoint endPoint, HttpDestination destination)
    {
        this(endPoint, destination, new Promise.Adapter<Connection>());
        throw new UnsupportedOperationException("Deprecated, use HttpConnectionOverHTTP(EndPoint, HttpDestination, Promise<Connection>) instead");
    }

    public HttpConnectionOverHTTP(EndPoint endPoint, HttpDestination destination, Promise<Connection> promise)
    {
        super(endPoint, destination.getHttpClient().getExecutor(), destination.getHttpClient().isDispatchIO());
        this.promise = promise;
        this.delegate = new Delegate(destination);
        this.channel = newHttpChannel();
    }

    protected HttpChannelOverHTTP newHttpChannel()
    {
        return new HttpChannelOverHTTP(this);
    }

    public HttpChannelOverHTTP getHttpChannel()
    {
        return channel;
    }

    public HttpDestinationOverHTTP getHttpDestination()
    {
        return (HttpDestinationOverHTTP)delegate.getHttpDestination();
    }

    @Override
    public void send(Request request, Response.CompleteListener listener)
    {
        delegate.send(request, listener);
    }

    protected void send(HttpExchange exchange)
    {
        delegate.send(exchange);
    }

    @Override
    public void onOpen()
    {
        super.onOpen();
        fillInterested();
        promise.succeeded(this);
    }

    @Override
    public boolean isClosed()
    {
        return closed.get();
    }

    private void send(HttpChannelOverHTTP channel, HttpExchange exchange)
    {
        boolean send;
        synchronized (this)
        {
            send = idleTimeoutGuard >= 0;
            if (send)
                ++idleTimeoutGuard;
        }

        if (send)
        {
            if (channel.associate(exchange))
                channel.send();
            else
                channel.release();

            synchronized (this)
            {
                --idleTimeoutGuard;
                idleTimeoutStamp = System.nanoTime();
            }
        }
        else
        {
            // This connection idle timed out before we could send the exchange, retry.
            HttpDestinationOverHTTP destination = getHttpDestination();
            destination.send(exchange);
        }
    }

    @Override
    protected boolean onReadTimeout()
    {
        synchronized (this)
        {
            if (idleTimeoutGuard == 0)
            {
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - idleTimeoutStamp);
                boolean idle = elapsed > idleTimeout / 2;
                if (LOG.isDebugEnabled())
                    LOG.debug("Idle timeout {}/{}ms - {}", elapsed, idleTimeout, this);
                if (idle)
                {
                    idleTimeoutGuard = -1;
                    close(new TimeoutException("Idle timeout expired: " + idleTimeout + "ms"));
                }
            }
            else
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("Idle timeout skipped - {}", this);
            }
            return false;
        }
    }

    @Override
    public void onFillable()
    {
        HttpExchange exchange = channel.getHttpExchange();
        if (exchange != null)
        {
            channel.receive();
        }
        else
        {
            // If there is no exchange, then could be either a remote close,
            // or garbage bytes; in both cases we close the connection
            close();
        }
    }

    public void release()
    {
        // Restore idle timeout
        getEndPoint().setIdleTimeout(idleTimeout);
        getHttpDestination().release(this);
    }

    @Override
    public void close()
    {
        close(new AsynchronousCloseException());
    }

    protected void close(Throwable failure)
    {
        if (softClose())
        {
            // First close then abort, to be sure that the connection cannot be reused
            // from an onFailure() handler or by blocking code waiting for completion.
            getHttpDestination().close(this);
            getEndPoint().shutdownOutput();
            if (LOG.isDebugEnabled())
                LOG.debug("{} oshut", this);
            getEndPoint().close();
            if (LOG.isDebugEnabled())
                LOG.debug("{} closed", this);

            abort(failure);
        }
    }

    public boolean softClose()
    {
        return closed.compareAndSet(false, true);
    }

    protected boolean abort(Throwable failure)
    {
        HttpExchange exchange = channel.getHttpExchange();
        return exchange != null && exchange.getRequest().abort(failure);
    }

    @Override
    public boolean sweep()
    {
        if (!closed.get())
            return false;

        if (sweeps.incrementAndGet() < 4)
            return false;

        return true;
    }

    @Override
    public String toString()
    {
        return String.format("%s@%h(l:%s <-> r:%s,closed=%b)[%s]",
                getClass().getSimpleName(),
                this,
                getEndPoint().getLocalAddress(),
                getEndPoint().getRemoteAddress(),
                closed.get(),
                channel);
    }

    private class Delegate extends HttpConnection
    {
        private Delegate(HttpDestination destination)
        {
            super(destination);
        }

        @Override
        protected void send(HttpExchange exchange)
        {
            Request request = exchange.getRequest();
            normalizeRequest(request);

            // Save the old idle timeout to restore it
            EndPoint endPoint = getEndPoint();
            idleTimeout = endPoint.getIdleTimeout();
            endPoint.setIdleTimeout(request.getIdleTimeout());

            // One channel per connection, just delegate the send
            HttpConnectionOverHTTP.this.send(channel, exchange);
        }

        @Override
        public void close()
        {
            HttpConnectionOverHTTP.this.close();
        }

        @Override
        public boolean isClosed()
        {
            return HttpConnectionOverHTTP.this.isClosed();
        }

        @Override
        public String toString()
        {
            return HttpConnectionOverHTTP.this.toString();
        }
    }
}
