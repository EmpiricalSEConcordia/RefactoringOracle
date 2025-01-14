package org.eclipse.jetty.io;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class PooledBuffers extends AbstractBuffers
{
    private final Queue<ByteBuffer> _headers;
    private final Queue<ByteBuffer> _buffers;
    private final Queue<ByteBuffer> _others;
    private final AtomicInteger _size = new AtomicInteger();
    private final int _maxSize;
    private final boolean _otherHeaders;
    private final boolean _otherBuffers;

    /* ------------------------------------------------------------ */
    public PooledBuffers(Buffers.Type headerType, int headerSize, Buffers.Type bufferType, int bufferSize, Buffers.Type otherType,int maxSize)
    {
        super(headerType,headerSize,bufferType,bufferSize,otherType);
        _headers=new ConcurrentLinkedQueue<ByteBuffer>();
        _buffers=new ConcurrentLinkedQueue<ByteBuffer>();
        _others=new ConcurrentLinkedQueue<ByteBuffer>();
        _otherHeaders=headerType==otherType;
        _otherBuffers=bufferType==otherType;
        _maxSize=maxSize;
    }

    /* ------------------------------------------------------------ */
    public ByteBuffer getHeader()
    {
        ByteBuffer buffer = _headers.poll();
        if (buffer==null)
            buffer=newHeader();
        else
            _size.decrementAndGet();
        return buffer;
    }

    /* ------------------------------------------------------------ */
    public ByteBuffer getBuffer()
    {
        ByteBuffer buffer = _buffers.poll();
        if (buffer==null)
            buffer=newBuffer();
        else
            _size.decrementAndGet();
        return buffer;
    }

    /* ------------------------------------------------------------ */
    public ByteBuffer getBuffer(int size)
    {
        if (_otherHeaders && size==getHeaderSize())
            return getHeader();
        if (_otherBuffers && size==getBufferSize())
            return getBuffer();

        // Look for an other buffer
        ByteBuffer buffer = _others.poll();

        // consume all other buffers until one of the right size is found
        while (buffer!=null && buffer.capacity()!=size)
        {
            _size.decrementAndGet();
            buffer = _others.poll();
        }

        if (buffer==null)
            buffer=newBuffer(size);
        else
            _size.decrementAndGet();
        return buffer;
    }

    /* ------------------------------------------------------------ */
    public void returnBuffer(ByteBuffer buffer)
    {
        buffer.clear().limit(0);
        if (buffer.isReadOnly())
            return;

        if (_size.incrementAndGet() > _maxSize)
            _size.decrementAndGet();
        else
        {
            if (isHeader(buffer))
                _headers.add(buffer);
            else if (isBuffer(buffer))
                _buffers.add(buffer);
            else
                _others.add(buffer);
        }
    }

    /* ------------------------------------------------------------ */
    public String toString()
    {
        return String.format("%s [%d/%d@%d,%d/%d@%d,%d/%d@-]",
                getClass().getSimpleName(),
                _headers.size(),_maxSize,_headerSize,
                _buffers.size(),_maxSize,_bufferSize,
                _others.size(),_maxSize);
    }
}
