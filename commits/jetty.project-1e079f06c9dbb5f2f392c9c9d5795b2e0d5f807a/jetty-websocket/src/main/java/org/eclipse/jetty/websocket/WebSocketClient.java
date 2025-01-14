package org.eclipse.jetty.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.util.log.Logger;


/* ------------------------------------------------------------ */
/**
 * <p>{@link WebSocketClient} allows to create multiple connections to multiple destinations
 * that can speak the websocket protocol.</p>
 * <p>When creating websocket connections, {@link WebSocketClient} accepts a {@link WebSocket}
 * object (to receive events from the server), and returns a {@link WebSocket.Connection} to
 * send data to the server.</p>
 * <p>Example usage is as follows:</p>
 * <pre>
 *   WebSocketClientFactory factory = new WebSocketClientFactory();
 *   factory.start();
 *
 *   WebSocketClient client = factory.newWebSocketClient();
 *   // Configure the client
 *
 *   WebSocket.Connection connection = client.open(new URI("ws://127.0.0.1:8080/"), new WebSocket.OnTextMessage()
 *   {
 *     public void onOpen(Connection connection)
 *     {
 *       // open notification
 *     }
 *
 *     public void onClose(int closeCode, String message)
 *     {
 *       // close notification
 *     }
 *
 *     public void onMessage(String data)
 *     {
 *       // handle incoming message
 *     }
 *   }).get(5, TimeUnit.SECONDS);
 *
 *   connection.sendMessage("Hello World");
 * </pre>
 */
public class WebSocketClient
{
    private final static Logger __log = org.eclipse.jetty.util.log.Log.getLogger(WebSocketClient.class.getName());

    private final WebSocketClientFactory _factory;
    private final Map<String,String> _cookies=new ConcurrentHashMap<String, String>();
    private final List<String> _extensions=new CopyOnWriteArrayList<String>();
    private String _origin;
    private String _protocol;
    private int _maxIdleTime=-1;
    private MaskGen _maskGen;
    private SocketAddress _bindAddress;

    /* ------------------------------------------------------------ */
    /**
     * <p>Creates a WebSocketClient from a private WebSocketClientFactory.</p>
     * <p>This can be wasteful of resources if many clients are created.</p>
     *
     * @deprecated Use {@link WebSocketClientFactory#newWebSocketClient()}
     * @throws Exception if the private WebSocketClientFactory fails to start
     */
    @Deprecated
    public WebSocketClient() throws Exception
    {
        _factory=new WebSocketClientFactory();
        _factory.start();
        _maskGen=_factory.getMaskGen();
    }

    /* ------------------------------------------------------------ */
    /**
     * <p>Creates a WebSocketClient with shared WebSocketClientFactory.</p>
     *
     * @param factory the shared {@link WebSocketClientFactory}
     */
    public WebSocketClient(WebSocketClientFactory factory)
    {
        _factory=factory;
        _maskGen=_factory.getMaskGen();
    }

    /* ------------------------------------------------------------ */
    /**
     * @return The WebSocketClientFactory this client was created with.
     */
    public WebSocketClientFactory getFactory()
    {
        return _factory;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return the address to bind the socket channel to
     * @see #setBindAddress(SocketAddress)
     */
    public SocketAddress getBindAddress()
    {
        return _bindAddress;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param bindAddress the address to bind the socket channel to
     * @see #getBindAddress()
     */
    public void setBindAddress(SocketAddress bindAddress)
    {
        this._bindAddress = bindAddress;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return The maxIdleTime in ms for connections opened by this client,
     * or -1 if the default from {@link WebSocketClientFactory#getSelectorManager()} is used.
     * @see #setMaxIdleTime(int)
     */
    public int getMaxIdleTime()
    {
        return _maxIdleTime;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param maxIdleTime The max idle time in ms for connections opened by this client
     * @see #getMaxIdleTime()
     */
    public void setMaxIdleTime(int maxIdleTime)
    {
        _maxIdleTime=maxIdleTime;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return The subprotocol string for connections opened by this client.
     * @see #setProtocol(String)
     */
    public String getProtocol()
    {
        return _protocol;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param protocol The subprotocol string for connections opened by this client.
     * @see #getProtocol()
     */
    public void setProtocol(String protocol)
    {
        _protocol = protocol;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return The origin URI of the client
     * @see #setOrigin(String)
     */
    public String getOrigin()
    {
        return _origin;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param origin The origin URI of the client (eg "http://example.com")
     * @see #getOrigin()
     */
    public void setOrigin(String origin)
    {
        _origin = origin;
    }

    /* ------------------------------------------------------------ */
    /**
     * <p>Returns the map of the cookies that are sent during the initial HTTP handshake
     * that upgrades to the websocket protocol.</p>
     * @return The read-write cookie map
     */
    public Map<String,String> getCookies()
    {
        return _cookies;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return The list of websocket protocol extensions
     */
    public List<String> getExtensions()
    {
        return _extensions;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return the mask generator to use, or null if not mask generator should be used
     * @see #setMaskGen(MaskGen)
     */
    public MaskGen getMaskGen()
    {
        return _maskGen;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param maskGen the mask generator to use, or null if not mask generator should be used
     * @see #getMaskGen()
     */
    public void setMaskGen(MaskGen maskGen)
    {
        _maskGen = maskGen;
    }

    /* ------------------------------------------------------------ */
    /**
     * <p>Opens a websocket connection to the URI and blocks until the connection is accepted or there is an error.</p>
     *
     * @param uri The URI to connect to.
     * @param websocket The {@link WebSocket} instance to handle incoming events.
     * @param maxConnectTime The interval to wait for a successful connection
     * @param units the units of the maxConnectTime
     * @return A {@link WebSocket.Connection}
     * @throws IOException if the connection fails
     * @throws InterruptedException if the thread is interrupted
     * @throws TimeoutException if the timeout elapses before the connection is completed
     * @see #open(URI, WebSocket)
     */
    public WebSocket.Connection open(URI uri, WebSocket websocket,long maxConnectTime,TimeUnit units) throws IOException, InterruptedException, TimeoutException
    {
        try
        {
            return open(uri,websocket).get(maxConnectTime,units);
        }
        catch (ExecutionException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof IOException)
                throw (IOException)cause;
            if (cause instanceof Error)
                throw (Error)cause;
            if (cause instanceof RuntimeException)
                throw (RuntimeException)cause;
            throw new RuntimeException(cause);
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * <p>Asynchronously opens a websocket connection and returns a {@link Future} to obtain the connection.</p>
     * <p>The caller must call {@link Future#get(long, TimeUnit)} if they wish to impose a connect timeout on the open.</p>
     *
     * @param uri The URI to connect to.
     * @param websocket The {@link WebSocket} instance to handle incoming events.
     * @return A {@link Future} to the {@link WebSocket.Connection}
     * @throws IOException if the connection fails
     * @see #open(URI, WebSocket, long, TimeUnit)
     */
    public Future<WebSocket.Connection> open(URI uri, WebSocket websocket) throws IOException
    {
        if (!_factory.isStarted())
            throw new IllegalStateException("Factory !started");
        String scheme=uri.getScheme();
        if (!("ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme)))
            throw new IllegalArgumentException("Bad WebSocket scheme '"+scheme+"'");
        if ("wss".equalsIgnoreCase(scheme))
            throw new IOException("wss not supported");

        SocketChannel channel = SocketChannel.open();
        if (_bindAddress != null)
            channel.socket().bind(_bindAddress);
        channel.socket().setTcpNoDelay(true);
        int maxIdleTime = getMaxIdleTime();

        InetSocketAddress address=new InetSocketAddress(uri.getHost(),uri.getPort());

        final WebSocketFuture holder=new WebSocketFuture(websocket,uri,_protocol,_origin,_maskGen,maxIdleTime,_cookies,_extensions,channel);

        channel.configureBlocking(false);
        channel.connect(address);
        _factory.getSelectorManager().register( channel, holder);

        return holder;
    }

    /* ------------------------------------------------------------ */
    /** The Future Websocket Connection.
     */
    static class WebSocketFuture implements Future<WebSocket.Connection>
    {
        final WebSocket _websocket;
        final URI _uri;
        final String _protocol;
        final String _origin;
        final MaskGen _maskGen;
        final int _maxIdleTime;
        final Map<String,String> _cookies;
        final List<String> _extensions;
        final CountDownLatch _done = new CountDownLatch(1);

        ByteChannel _channel;
        WebSocketConnection _connection;
        Throwable _exception;

        private WebSocketFuture(WebSocket websocket, URI uri, String protocol, String origin, MaskGen maskGen, int maxIdleTime, Map<String,String> cookies,List<String> extensions, ByteChannel channel)
        {
            _websocket=websocket;
            _uri=uri;
            _protocol=protocol;
            _origin=origin;
            _maskGen=maskGen;
            _maxIdleTime=maxIdleTime;
            _cookies=cookies;
            _extensions=extensions;
            _channel=channel;
        }

        public void onConnection(WebSocketConnection connection)
        {
            try
            {
                synchronized (this)
                {
                    if (_channel!=null)
                        _connection=connection;
                }

                if (_connection!=null)
                {
                    if (_websocket instanceof WebSocket.OnFrame)
                        ((WebSocket.OnFrame)_websocket).onHandshake((WebSocket.FrameConnection)connection.getConnection());

                    _websocket.onOpen(connection.getConnection());

                }
            }
            finally
            {
                _done.countDown();
            }
        }

        public void handshakeFailed(Throwable ex)
        {
            try
            {
                ByteChannel channel=null;
                synchronized (this)
                {
                    if (_channel!=null)
                    {
                        channel=_channel;
                        _channel=null;
                        _exception=ex;
                    }
                }

                if (channel!=null)
                {
                    if (ex instanceof ProtocolException)
                        closeChannel(channel,WebSocketConnectionD12.CLOSE_PROTOCOL,ex.getMessage());
                    else
                        closeChannel(channel,WebSocketConnectionD12.CLOSE_NOCLOSE,ex.getMessage());
                }
            }
            finally
            {
                _done.countDown();
            }
        }

        public Map<String,String> getCookies()
        {
            return _cookies;
        }

        public String getProtocol()
        {
            return _protocol;
        }

        public WebSocket getWebSocket()
        {
            return _websocket;
        }

        public URI getURI()
        {
            return _uri;
        }

        public int getMaxIdleTime()
        {
            return _maxIdleTime;
        }

        public String getOrigin()
        {
            return _origin;
        }

        public MaskGen getMaskGen()
        {
            return _maskGen;
        }

        public String toString()
        {
            return "[" + _uri + ","+_websocket+"]@"+hashCode();
        }

        public boolean cancel(boolean mayInterruptIfRunning)
        {
            try
            {
                ByteChannel channel=null;
                synchronized (this)
                {
                    if (_connection==null && _exception==null && _channel!=null)
                    {
                        channel=_channel;
                        _channel=null;
                    }
                }

                if (channel!=null)
                {
                    closeChannel(channel,WebSocketConnectionD12.CLOSE_NOCLOSE,"cancelled");
                    return true;
                }
                return false;
            }
            finally
            {
                _done.countDown();
            }
        }

        public boolean isCancelled()
        {
            synchronized (this)
            {
                return _channel==null && _connection==null;
            }
        }

        public boolean isDone()
        {
            synchronized (this)
            {
                return _connection!=null && _exception==null;
            }
        }

        public org.eclipse.jetty.websocket.WebSocket.Connection get() throws InterruptedException, ExecutionException
        {
            try
            {
                return get(Long.MAX_VALUE,TimeUnit.SECONDS);
            }
            catch(TimeoutException e)
            {
                throw new IllegalStateException("The universe has ended",e);
            }
        }

        public org.eclipse.jetty.websocket.WebSocket.Connection get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
                TimeoutException
        {
            _done.await(timeout,unit);
            ByteChannel channel=null;
            org.eclipse.jetty.websocket.WebSocket.Connection connection=null;
            Throwable exception;
            synchronized (this)
            {
                exception=_exception;
                if (_connection==null)
                {
                    exception=_exception;
                    channel=_channel;
                    _channel=null;
                }
                else
                    connection=_connection.getConnection();
            }

            if (channel!=null)
                closeChannel(channel,WebSocketConnectionD12.CLOSE_NOCLOSE,"timeout");
            if (exception!=null)
                throw new ExecutionException(exception);
            if (connection!=null)
                return connection;
            throw new TimeoutException();
        }

        private void closeChannel(ByteChannel channel,int code, String message)
        {
            try
            {
                _websocket.onClose(code,message);
            }
            catch(Exception e)
            {
                __log.warn(e);
            }

            try
            {
                channel.close();
            }
            catch(IOException e)
            {
                __log.debug(e);
            }
        }
    }
}
