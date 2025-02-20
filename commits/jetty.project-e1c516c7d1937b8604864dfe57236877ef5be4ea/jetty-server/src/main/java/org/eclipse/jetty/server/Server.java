//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
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
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpGenerator;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.AttributesMap;
import org.eclipse.jetty.util.DateCache;
import org.eclipse.jetty.util.Jetty;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.component.Graceful;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ShutdownThread;
import org.eclipse.jetty.util.thread.ThreadPool;

/* ------------------------------------------------------------ */
/** Jetty HTTP Servlet Server.
 * This class is the main class for the Jetty HTTP Servlet server.
 * It aggregates Connectors (HTTP request receivers) and request Handlers.
 * The server is itself a handler and a ThreadPool.  Connectors use the ThreadPool methods
 * to run jobs that will eventually call the handle method.
 */
@ManagedObject(value="Jetty HTTP Servlet server")
public class Server extends HandlerWrapper implements Attributes
{
    private static final Logger LOG = Log.getLogger(Server.class);

    private final AttributesMap _attributes = new AttributesMap();
    private final ThreadPool _threadPool;
    private final List<Connector> _connectors = new CopyOnWriteArrayList<>();
    private SessionIdManager _sessionIdManager;
    private boolean _stopAtShutdown;
    private boolean _dumpAfterStart=false;
    private boolean _dumpBeforeStop=false;
    private HttpField _dateField;


    /* ------------------------------------------------------------ */
    public Server()
    {
        this((ThreadPool)null);
    }

    /* ------------------------------------------------------------ */
    /** Convenience constructor
     * Creates server and a {@link ServerConnector} at the passed port.
     */
    public Server(@Name("port")int port)
    {
        this((ThreadPool)null);
        ServerConnector connector=new ServerConnector(this);
        connector.setPort(port);
        setConnectors(new Connector[]{connector});
    }

    /* ------------------------------------------------------------ */
    /** Convenience constructor
     * Creates server and a {@link ServerConnector} at the passed address.
     */
    public Server(@Name("address")InetSocketAddress addr)
    {
        this((ThreadPool)null);
        ServerConnector connector=new ServerConnector(this);
        connector.setHost(addr.getHostName());
        connector.setPort(addr.getPort());
        setConnectors(new Connector[]{connector});
    }



    /* ------------------------------------------------------------ */
    public Server(@Name("threadpool") ThreadPool pool)
    {
        _threadPool=pool!=null?pool:new QueuedThreadPool();
        addBean(_threadPool);
        setServer(this);
    }


    /* ------------------------------------------------------------ */
    @ManagedAttribute("version of this server")
    public static String getVersion()
    {
        return Jetty.VERSION;
    }

    /* ------------------------------------------------------------ */
    public boolean getStopAtShutdown()
    {
        return _stopAtShutdown;
    }

    /* ------------------------------------------------------------ */
    public void setStopAtShutdown(boolean stop)
    {
        //if we now want to stop
        if (stop)
        {
            //and we weren't stopping before
            if (!_stopAtShutdown)
            {
                //only register to stop if we're already started (otherwise we'll do it in doStart())
                if (isStarted())
                    ShutdownThread.register(this);
            }
        }
        else
            ShutdownThread.deregister(this);

        _stopAtShutdown=stop;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the connectors.
     */
    @ManagedAttribute(value="connectors for this server", readonly=true)
    public Connector[] getConnectors()
    {
        List<Connector> connectors = new ArrayList<>(_connectors);
        return connectors.toArray(new Connector[connectors.size()]);
    }

    /* ------------------------------------------------------------ */
    public void addConnector(Connector connector)
    {
        if (connector.getServer() != this)
            throw new IllegalArgumentException("Connector " + connector +
                    " cannot be shared among server " + connector.getServer() + " and server " + this);
        if (_connectors.add(connector))
            addBean(connector);
    }

    /* ------------------------------------------------------------ */
    /**
     * Convenience method which calls {@link #getConnectors()} and {@link #setConnectors(Connector[])} to
     * remove a connector.
     * @param connector The connector to remove.
     */
    public void removeConnector(Connector connector)
    {
        if (_connectors.remove(connector))
            removeBean(connector);
    }

    /* ------------------------------------------------------------ */
    /** Set the connectors for this server.
     * Each connector has this server set as it's ThreadPool and its Handler.
     * @param connectors The connectors to set.
     */
    public void setConnectors(Connector[] connectors)
    {
        if (connectors != null)
        {
            for (Connector connector : connectors)
            {
                if (connector.getServer() != this)
                    throw new IllegalArgumentException("Connector " + connector +
                            " cannot be shared among server " + connector.getServer() + " and server " + this);
            }
        }

        Connector[] oldConnectors = getConnectors();
        updateBeans(oldConnectors, connectors);
        _connectors.removeAll(Arrays.asList(oldConnectors));
        if (connectors != null)
            _connectors.addAll(Arrays.asList(connectors));
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the threadPool.
     */
    @ManagedAttribute("the server thread pool")
    public ThreadPool getThreadPool()
    {
        return _threadPool;
    }

    /**
     * @return true if {@link #dumpStdErr()} is called after starting
     */
    @ManagedAttribute("dump state to stderr after start")
    public boolean isDumpAfterStart()
    {
        return _dumpAfterStart;
    }

    /**
     * @param dumpAfterStart true if {@link #dumpStdErr()} is called after starting
     */
    public void setDumpAfterStart(boolean dumpAfterStart)
    {
        _dumpAfterStart = dumpAfterStart;
    }

    /**
     * @return true if {@link #dumpStdErr()} is called before stopping
     */
    @ManagedAttribute("dump state to stderr before stop")
    public boolean isDumpBeforeStop()
    {
        return _dumpBeforeStop;
    }

    /**
     * @param dumpBeforeStop true if {@link #dumpStdErr()} is called before stopping
     */
    public void setDumpBeforeStop(boolean dumpBeforeStop)
    {
        _dumpBeforeStop = dumpBeforeStop;
    }


    /* ------------------------------------------------------------ */
    public HttpField getDateField()
    {
        return _dateField;
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doStart() throws Exception
    {
        if (getStopAtShutdown()) {
            ShutdownThread.register(this);
            ShutdownMonitor.getInstance().start(); // initialize
        }

        LOG.info("jetty-"+getVersion());
        HttpGenerator.setServerVersion(getVersion());
        MultiException mex=new MultiException();

        try
        {
            super.doStart();
        }
        catch(Throwable e)
        {
            mex.add(e);
        }

        if (mex.size()==0)
        {
            for (Connector _connector : _connectors)
            {
                try
                {
                    _connector.start();
                }
                catch (Throwable e)
                {
                    mex.add(e);
                }
            }
        }

        if (isDumpAfterStart())
            dumpStdErr();

        
        // use DateCache timer for Date field reformat
        final HttpFields.DateGenerator date = new HttpFields.DateGenerator();
        long now=System.currentTimeMillis();
        long tick=1000*((now/1000)+1)-now;
        _dateField=new HttpField.CachedHttpField(HttpHeader.DATE,date.formatDate(now));
        DateCache.getTimer().scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                long now=System.currentTimeMillis();
                _dateField=new HttpField.CachedHttpField(HttpHeader.DATE,date.formatDate(now));
                if (!isRunning())
                    this.cancel();
            }
        },tick,1000);
        
        
        mex.ifExceptionThrow();
    }

    @Override
    protected void start(LifeCycle l) throws Exception
    {
        // start connectors last
        if (!(l instanceof Connector))
            super.start(l);
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doStop() throws Exception
    {
        if (isDumpBeforeStop())
            dumpStdErr();

        MultiException mex=new MultiException();

        // list if graceful futures
        List<Future<Void>> futures = new ArrayList<>();

        // First close the network connectors to stop accepting new connections
        for (Connector connector : _connectors)
            futures.add(connector.shutdown());

        // Then tell the contexts that we are shutting down
        Handler[] contexts = getChildHandlersByClass(Graceful.class);
        for (Handler context : contexts)
            futures.add(((Graceful)context).shutdown());

        // Shall we gracefully wait for zero connections?
        long stopTimeout = getStopTimeout();
        if (stopTimeout>0)
        {
            long stop_by=System.currentTimeMillis()+stopTimeout;
            LOG.info("Graceful shutdown {} by ",this,new Date(stop_by));

            // Wait for shutdowns
            for (Future<Void> future: futures)
            {
                try
                {
                    if (!future.isDone())
                        future.get(Math.max(1L,stop_by-System.currentTimeMillis()),TimeUnit.MILLISECONDS);
                }
                catch (Exception e)
                {
                    mex.add(e.getCause());
                }
            }
        }

        // Cancel any shutdowns not done
        for (Future<Void> future: futures)
            if (!future.isDone())
                future.cancel(true);

        // Now stop the connectors (this will close existing connections)
        for (Connector connector : _connectors)
        {
            try
            {
                connector.stop();
            }
            catch (Throwable e)
            {
                mex.add(e);
            }
        }

        // And finally stop everything else
        try
        {
            super.doStop();
        }
        catch (Throwable e)
        {
            mex.add(e);
        }

        if (getStopAtShutdown())
            ShutdownThread.deregister(this);

        mex.ifExceptionThrow();

    }

    /* ------------------------------------------------------------ */
    /* Handle a request from a connection.
     * Called to handle a request on the connection when either the header has been received,
     * or after the entire request has been received (for short requests of known length), or
     * on the dispatch of an async request.
     */
    public void handle(HttpChannel<?> connection) throws IOException, ServletException
    {
        final String target=connection.getRequest().getPathInfo();
        final Request request=connection.getRequest();
        final Response response=connection.getResponse();

        if (LOG.isDebugEnabled())
            LOG.debug("REQUEST "+target+" on "+connection);

        if ("*".equals(target))
        {
            handleOptions(request,response);
            if (!request.isHandled())
                handle(target, request, request, response);
        }
        else
            handle(target, request, request, response);

        if (LOG.isDebugEnabled())
            LOG.debug("RESPONSE "+target+"  "+connection.getResponse().getStatus()+" handled="+request.isHandled());
    }

    /* ------------------------------------------------------------ */
    /* Handle Options request to server
     */
    protected void handleOptions(Request request,Response response) throws IOException
    {
        if (!HttpMethod.OPTIONS.is(request.getMethod()))
            response.sendError(HttpStatus.BAD_REQUEST_400);
        request.setHandled(true);
        response.setStatus(200);
        response.getHttpFields().put(HttpHeader.ALLOW,"GET,POST,HEAD,OPTIONS");
        response.setContentLength(0);
        response.complete();
    }

    /* ------------------------------------------------------------ */
    /* Handle a request from a connection.
     * Called to handle a request on the connection when either the header has been received,
     * or after the entire request has been received (for short requests of known length), or
     * on the dispatch of an async request.
     */
    public void handleAsync(HttpChannel<?> connection) throws IOException, ServletException
    {
        final HttpChannelState async = connection.getRequest().getHttpChannelState();
        final HttpChannelState.AsyncEventState state = async.getAsyncEventState();

        final Request baseRequest=connection.getRequest();
        final String path=state.getPath();

        if (path!=null)
        {
            // this is a dispatch with a path
            ServletContext context=state.getServletContext();
            HttpURI uri = new HttpURI(context==null?path:URIUtil.addPaths(context.getContextPath(),path));
            baseRequest.setUri(uri);
            baseRequest.setRequestURI(null);
            baseRequest.setPathInfo(baseRequest.getRequestURI());
            if (uri.getQuery()!=null)
                baseRequest.mergeQueryString(uri.getQuery()); //we have to assume dispatch path and query are UTF8
        }

        final String target=baseRequest.getPathInfo();
        final HttpServletRequest request=(HttpServletRequest)async.getRequest();
        final HttpServletResponse response=(HttpServletResponse)async.getResponse();

        if (LOG.isDebugEnabled())
        {
            LOG.debug("REQUEST "+target+" on "+connection);
            handle(target, baseRequest, request, response);
            LOG.debug("RESPONSE "+target+"  "+connection.getResponse().getStatus());
        }
        else
            handle(target, baseRequest, request, response);

    }


    /* ------------------------------------------------------------ */
    public void join() throws InterruptedException
    {
        getThreadPool().join();
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the sessionIdManager.
     */
    public SessionIdManager getSessionIdManager()
    {
        return _sessionIdManager;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param sessionIdManager The sessionIdManager to set.
     */
    public void setSessionIdManager(SessionIdManager sessionIdManager)
    {
        updateBean(_sessionIdManager,sessionIdManager);
        _sessionIdManager=sessionIdManager;
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.util.AttributesMap#clearAttributes()
     */
    @Override
    public void clearAttributes()
    {
        Enumeration names = _attributes.getAttributeNames();
        while (names.hasMoreElements())
            removeBean(_attributes.getAttribute((String)names.nextElement()));
        _attributes.clearAttributes();
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.util.AttributesMap#getAttribute(java.lang.String)
     */
    @Override
    public Object getAttribute(String name)
    {
        return _attributes.getAttribute(name);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.util.AttributesMap#getAttributeNames()
     */
    @Override
    public Enumeration<String> getAttributeNames()
    {
        return AttributesMap.getAttributeNamesCopy(_attributes);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.util.AttributesMap#removeAttribute(java.lang.String)
     */
    @Override
    public void removeAttribute(String name)
    {
        Object bean=_attributes.getAttribute(name);
        if (bean!=null)
            removeBean(bean);
        _attributes.removeAttribute(name);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.util.AttributesMap#setAttribute(java.lang.String, java.lang.Object)
     */
    @Override
    public void setAttribute(String name, Object attribute)
    {
        addBean(attribute);
        _attributes.setAttribute(name, attribute);
    }

    /* ------------------------------------------------------------ */
    @Override
    public String toString()
    {
        return this.getClass().getName()+"@"+Integer.toHexString(hashCode());
    }

    @Override
    public void dump(Appendable out,String indent) throws IOException
    {
        dumpBeans(out,indent,Collections.singleton(new ClassLoaderDump(this.getClass().getClassLoader())));
    }
    
    /* ------------------------------------------------------------ */
    public static void main(String...args) throws Exception
    {
        System.err.println(getVersion());
    }
}
