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

package org.eclipse.jetty.server.session;

import java.io.IOException;
import java.util.EnumSet;
import java.util.EventListener;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RetryRequest;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.log.Log;

/* ------------------------------------------------------------ */
/** SessionHandler.
 *
 * 
 *
 */
public class SessionHandler extends HandlerWrapper
{
    public final static EnumSet<SessionTrackingMode> DEFAULT_TRACKING = EnumSet.of(SessionTrackingMode.COOKIE,SessionTrackingMode.URL);
    
    /* -------------------------------------------------------------- */
    private SessionManager _sessionManager;

    /* ------------------------------------------------------------ */
    /** Constructor.
     * Construct a SessionHandler witha a HashSessionManager with a standard
     * java.util.Random generator is created.
     */
    public SessionHandler()
    {
        this(new HashSessionManager());
    }

    /* ------------------------------------------------------------ */
    /**
     * @param manager The session manager
     */
    public SessionHandler(SessionManager manager)
    {
        setSessionManager(manager);
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the sessionManager.
     */
    public SessionManager getSessionManager()
    {
        return _sessionManager;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param sessionManager The sessionManager to set.
     */
    public void setSessionManager(SessionManager sessionManager)
    {
        if (isStarted())
            throw new IllegalStateException();
        SessionManager old_session_manager = _sessionManager;

        if (getServer()!=null)
            getServer().getContainer().update(this, old_session_manager, sessionManager, "sessionManager",true);

        if (sessionManager!=null)
            sessionManager.setSessionHandler(this);

        _sessionManager = sessionManager;

        if (old_session_manager!=null)
            old_session_manager.setSessionHandler(null);
    }


    /* ------------------------------------------------------------ */
    public void setServer(Server server)
    {
        Server old_server=getServer();
        if (old_server!=null && old_server!=server)
            old_server.getContainer().update(this, _sessionManager, null, "sessionManager",true);
        super.setServer(server);
        if (server!=null && server!=old_server)
            server.getContainer().update(this, null,_sessionManager, "sessionManager",true);
    }


    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.thread.AbstractLifeCycle#doStart()
     */
    protected void doStart() throws Exception
    {
        _sessionManager.start();
        super.doStart();
    }
    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.thread.AbstractLifeCycle#doStop()
     */
    protected void doStop() throws Exception
    {
        super.doStop();
        _sessionManager.stop();
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.jetty.server.Handler#handle(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, int)
     */
    public void handle(String target, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException
    {
        setRequestedId(request);

        Request base_request = (request instanceof Request) ? (Request)request:HttpConnection.getCurrentConnection().getRequest();
        SessionManager old_session_manager=null;
        HttpSession old_session=null;

        try
        {
            old_session_manager = base_request.getSessionManager();
            old_session = base_request.getSession(false);

            if (old_session_manager != _sessionManager)
            {
                // new session context
                base_request.setSessionManager(_sessionManager);
                base_request.setSession(null);
            }

            // access any existing session
            HttpSession session=null;
            if (_sessionManager!=null)
            {
                session=base_request.getSession(false);
                if (session!=null)
                {
                    if(session!=old_session)
                    {
                        HttpCookie cookie = _sessionManager.access(session,request.isSecure());
                        if (cookie!=null ) // Handle changed ID or max-age refresh
                            base_request.getResponse().addCookie(cookie);
                    }
                }
                else
                {
                    session=base_request.recoverNewSession(_sessionManager);
                    if (session!=null)
                        base_request.setSession(session);
                }
            }

            if(Log.isDebugEnabled())
            {
                Log.debug("sessionManager="+_sessionManager);
                Log.debug("session="+session);
            }

            getHandler().handle(target, request, response);
        }
        catch (RetryRequest r)
        {
            HttpSession session=base_request.getSession(false);
            if (session!=null && session.isNew())
                base_request.saveNewSession(_sessionManager,session);
            throw r;
        }
        finally
        {
            HttpSession session=request.getSession(false);

            if (old_session_manager != _sessionManager)
            {
                //leaving context, free up the session
                if (session!=null)
                    _sessionManager.complete(session);
                base_request.setSessionManager(old_session_manager);
                base_request.setSession(old_session);
            }
        }
    }

    /* ------------------------------------------------------------ */
    /** Look for a requested session ID in cookies and URI parameters
     * @param request
     * @param dispatch
     */
    protected void setRequestedId(HttpServletRequest request)
    {
        Request base_request = (request instanceof Request) ? (Request)request:HttpConnection.getCurrentConnection().getRequest();
        String requested_session_id=request.getRequestedSessionId();
        if (!DispatcherType.REQUEST.equals(base_request.getDispatcherType()) || requested_session_id!=null)
        {
            return;
        }

        SessionManager sessionManager = getSessionManager();
        boolean requested_session_id_from_cookie=false;

        // Look for session id cookie
        if (_sessionManager.isUsingCookies())
        {
            Cookie[] cookies=request.getCookies();
            if (cookies!=null && cookies.length>0)
            {
                for (int i=0;i<cookies.length;i++)
                {
                    if (sessionManager.getSessionCookie().equalsIgnoreCase(cookies[i].getName()))
                    {
                        if (requested_session_id!=null)
                        {
                            // Multiple jsessionid cookies. Probably due to
                            // multiple paths and/or domains. Pick the first
                            // known session or the last defined cookie.
                            if (sessionManager.getHttpSession(requested_session_id)!=null)
                                break;
                        }

                        requested_session_id=cookies[i].getValue();
                        requested_session_id_from_cookie = true;
                        if(Log.isDebugEnabled())Log.debug("Got Session ID "+requested_session_id+" from cookie");
                    }
                }
            }
        }

        if (requested_session_id==null)
        {
            String uri = request.getRequestURI();

            int semi = uri.lastIndexOf(';');
            if (semi>=0)
            {
                String path_params=uri.substring(semi+1);

                // check if there is a url encoded session param.
                String param=sessionManager.getSessionIdPathParameterName();
                if (param!=null && path_params!=null && path_params.startsWith(param))
                {
                    requested_session_id = path_params.substring(sessionManager.getSessionIdPathParameterName().length()+1);
                    if(Log.isDebugEnabled())Log.debug("Got Session ID "+requested_session_id+" from URL");
                }
            }
        }

        base_request.setRequestedSessionId(requested_session_id);
        base_request.setRequestedSessionIdFromCookie(requested_session_id!=null && requested_session_id_from_cookie);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param listener
     */
    public void addEventListener(EventListener listener)
    {
        if(_sessionManager!=null)
            _sessionManager.addEventListener(listener);
    }

    /* ------------------------------------------------------------ */
    public void clearEventListeners()
    {
        if(_sessionManager!=null)
            _sessionManager.clearEventListeners();
    }
}
