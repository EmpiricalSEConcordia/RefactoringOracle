// ========================================================================
// Copyright (c) 2004-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses.
// ========================================================================

package org.eclipse.jetty.io;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.SocketChannel;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * Channel End Point.
 * <p>Holds the channel and socket for an NIO endpoint.
 */
public class ChannelEndPoint extends AbstractEndPoint
{
    private static final Logger LOG = Log.getLogger(ChannelEndPoint.class);

    private final ByteChannel _channel;
    private final Socket _socket;
    private volatile boolean _ishut;
    private volatile boolean _oshut;

    public ChannelEndPoint(SocketChannel channel) throws IOException
    {
        super((InetSocketAddress)channel.socket().getLocalSocketAddress(),
              (InetSocketAddress)channel.socket().getRemoteSocketAddress());
        _channel = channel;
        _socket=channel.socket();
    }

    @Override
    public boolean isOpen()
    {
        return _channel.isOpen();
    }

    protected void shutdownInput()
    {
        LOG.debug("ishut {}", this);
        _ishut=true;
        if (_oshut)
            close();
    }

    @Override
    public void shutdownOutput()
    {
        LOG.debug("oshut {}", this);
        _oshut = true;
        if (_channel.isOpen())
        {
            try
            {
                if (!_socket.isOutputShutdown())
                    _socket.shutdownOutput();
            }
            catch (IOException e)
            {
                LOG.debug(e);
            }
            finally
            {
                if (_ishut)
                {
                    close();
                }
            }
        }
    }

    @Override
    public boolean isOutputShutdown()
    {
        return _oshut || !_channel.isOpen() || _socket.isOutputShutdown();
    }

    @Override
    public boolean isInputShutdown()
    {
        return _ishut || !_channel.isOpen() || _socket.isInputShutdown();
    }

    @Override
    public void close()
    {
        LOG.debug("close {}", this);
        try
        {
            _channel.close();
        }
        catch (IOException e)
        {
            LOG.debug(e);
        }
    }

    @Override
    public int fill(ByteBuffer buffer) throws IOException
    {
        if (_ishut)
            return -1;

        int pos=BufferUtil.flipToFill(buffer);
        try
        {
            int filled = _channel.read(buffer);

            if (filled>0)
                notIdle();
            else if (filled==-1)
                shutdownInput();
            
            return filled;
        }
        catch(IOException e)
        {
            LOG.debug(e);
            shutdownInput();
            return -1;
        }
        finally
        {
            BufferUtil.flipToFlush(buffer,pos);
        }
    }

    @Override
    public int flush(ByteBuffer... buffers) throws IOException
    {
        int flushed=0;
        try
        {
            if (buffers.length==1)
                flushed=_channel.write(buffers[0]);
            else if (buffers.length>1 && _channel instanceof GatheringByteChannel)
                flushed= (int)((GatheringByteChannel)_channel).write(buffers,0,buffers.length);
            else
            {
                for (ByteBuffer b : buffers)
                {
                    if (b.hasRemaining())
                    {
                        int l=_channel.write(b);
                        if (l>0)
                            flushed+=l;
                        else
                            break;
                    }
                }
            }
        }
        catch (ClosedChannelException | EOFException | SocketException e)
        {
            throw new EofException(e);
        }
        if (flushed>0)
            notIdle();
        return flushed;
    }

    public ByteChannel getChannel()
    {
        return _channel;
    }

    @Override
    public Object getTransport()
    {
        return _channel;
    }

    public Socket getSocket()
    {
        return _socket;
    }
}
