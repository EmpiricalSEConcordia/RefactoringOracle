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

package org.eclipse.jetty.security.authentication;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Authentication.User;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.security.Constraint;

/**
 * FORM Authenticator.
 *
 * <p>This authenticator implements form authentication will use dispatchers to
 * the login page if the {@link #__FORM_DISPATCH} init parameter is set to true.
 * Otherwise it will redirect.</p>
 *
 * <p>The form authenticator redirects unauthenticated requests to a log page
 * which should use a form to gather username/password from the user and send them
 * to the /j_security_check URI within the context.  FormAuthentication uses
 * {@link SessionAuthentication} to wrap Authentication results so that they
 * are  associated with the session.</p>
 *
 *
 */
public class FormAuthenticator extends LoginAuthenticator
{
    private static final Logger LOG = Log.getLogger(FormAuthenticator.class);

    public final static String __FORM_LOGIN_PAGE="org.eclipse.jetty.security.form_login_page";
    public final static String __FORM_ERROR_PAGE="org.eclipse.jetty.security.form_error_page";
    public final static String __FORM_DISPATCH="org.eclipse.jetty.security.dispatch";
    public final static String __J_URI = "org.eclipse.jetty.security.form_URI";
    public final static String __J_POST = "org.eclipse.jetty.security.form_POST";
    public final static String __J_SECURITY_CHECK = "/j_security_check";
    public final static String __J_USERNAME = "j_username";
    public final static String __J_PASSWORD = "j_password";

    private String _formErrorPage;
    private String _formErrorPath;
    private String _formLoginPage;
    private String _formLoginPath;
    private boolean _dispatch;
    private boolean _alwaysSaveUri;

    public FormAuthenticator()
    {
    }

    /* ------------------------------------------------------------ */
    public FormAuthenticator(String login,String error,boolean dispatch)
    {
        this();
        if (login!=null)
            setLoginPage(login);
        if (error!=null)
            setErrorPage(error);
        _dispatch=dispatch;
    }

    /* ------------------------------------------------------------ */
    /**
     * If true, uris that cause a redirect to a login page will always
     * be remembered. If false, only the first uri that leads to a login
     * page redirect is remembered.
     * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=379909
     * @param alwaysSave
     */
    public void setAlwaysSaveUri (boolean alwaysSave)
    {
        _alwaysSaveUri = alwaysSave;
    }


    /* ------------------------------------------------------------ */
    public boolean getAlwaysSaveUri ()
    {
        return _alwaysSaveUri;
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.security.authentication.LoginAuthenticator#setConfiguration(org.eclipse.jetty.security.Authenticator.AuthConfiguration)
     */
    @Override
    public void setConfiguration(AuthConfiguration configuration)
    {
        super.setConfiguration(configuration);
        String login=configuration.getInitParameter(FormAuthenticator.__FORM_LOGIN_PAGE);
        if (login!=null)
            setLoginPage(login);
        String error=configuration.getInitParameter(FormAuthenticator.__FORM_ERROR_PAGE);
        if (error!=null)
            setErrorPage(error);
        String dispatch=configuration.getInitParameter(FormAuthenticator.__FORM_DISPATCH);
        _dispatch = dispatch==null?_dispatch:Boolean.valueOf(dispatch);
    }

    /* ------------------------------------------------------------ */
    public String getAuthMethod()
    {
        return Constraint.__FORM_AUTH;
    }

    /* ------------------------------------------------------------ */
    private void setLoginPage(String path)
    {
        if (!path.startsWith("/"))
        {
            LOG.warn("form-login-page must start with /");
            path = "/" + path;
        }
        _formLoginPage = path;
        _formLoginPath = path;
        if (_formLoginPath.indexOf('?') > 0)
            _formLoginPath = _formLoginPath.substring(0, _formLoginPath.indexOf('?'));
    }

    /* ------------------------------------------------------------ */
    private void setErrorPage(String path)
    {
        if (path == null || path.trim().length() == 0)
        {
            _formErrorPath = null;
            _formErrorPage = null;
        }
        else
        {
            if (!path.startsWith("/"))
            {
                LOG.warn("form-error-page must start with /");
                path = "/" + path;
            }
            _formErrorPage = path;
            _formErrorPath = path;

            if (_formErrorPath.indexOf('?') > 0)
                _formErrorPath = _formErrorPath.substring(0, _formErrorPath.indexOf('?'));
        }
    }

    /* ------------------------------------------------------------ */
    public Authentication validateRequest(ServletRequest req, ServletResponse res, boolean mandatory) throws ServerAuthException
    {
        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)res;
        String uri = request.getRequestURI();
        if (uri==null)
            uri=URIUtil.SLASH;

        mandatory|=isJSecurityCheck(uri);
        if (!mandatory)
            return _deferred;

        if (isLoginOrErrorPage(URIUtil.addPaths(request.getServletPath(),request.getPathInfo())) &&!DeferredAuthentication.isDeferred(response))
            return _deferred;

        HttpSession session = request.getSession(true);

        try
        {
            // Handle a request for authentication.
            if (isJSecurityCheck(uri))
            {
                final String username = request.getParameter(__J_USERNAME);
                final String password = request.getParameter(__J_PASSWORD);

                UserIdentity user = _loginService.login(username,password);
                if (user!=null)
                {
                    session=renewSession(request,response);

                    // Redirect to original request
                    String nuri;
                    synchronized(session)
                    {
                        nuri = (String) session.getAttribute(__J_URI);
                    }

                    if (nuri == null || nuri.length() == 0)
                    {
                        nuri = request.getContextPath();
                        if (nuri.length() == 0)
                            nuri = URIUtil.SLASH;
                    }
                    response.setContentLength(0);
                    response.sendRedirect(response.encodeRedirectURL(nuri));

                    Authentication cached=new SessionAuthentication(getAuthMethod(),user,password);
                    session.setAttribute(SessionAuthentication.__J_AUTHENTICATED, cached);
                    return new FormAuthentication(getAuthMethod(),user);
                }

                // not authenticated
                if (LOG.isDebugEnabled())
                    LOG.debug("Form authentication FAILED for " + StringUtil.printable(username));
                if (_formErrorPage == null)
                {
                    if (response != null)
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                }
                else if (_dispatch)
                {
                    RequestDispatcher dispatcher = request.getRequestDispatcher(_formErrorPage);
                    response.setHeader(HttpHeader.CACHE_CONTROL.asString(),HttpHeaderValue.NO_CACHE.asString());
                    response.setDateHeader(HttpHeader.EXPIRES.asString(),1);
                    dispatcher.forward(new FormRequest(request), new FormResponse(response));
                }
                else
                {
                    response.sendRedirect(response.encodeRedirectURL(URIUtil.addPaths(request.getContextPath(),_formErrorPage)));
                }

                return Authentication.SEND_FAILURE;
            }

            // Look for cached authentication
            Authentication authentication = (Authentication) session.getAttribute(SessionAuthentication.__J_AUTHENTICATED);
            if (authentication != null)
            {
                // Has authentication been revoked?
                if (authentication instanceof Authentication.User &&
                    _loginService!=null &&
                    !_loginService.validate(((Authentication.User)authentication).getUserIdentity()))
                {

                    session.removeAttribute(SessionAuthentication.__J_AUTHENTICATED);
                }
                else
                {
                    String j_uri=(String)session.getAttribute(__J_URI);
                    if (j_uri!=null)
                    {
                        MultiMap j_post = (MultiMap)session.getAttribute(__J_POST);
                        if (j_post!=null)
                        {
                            StringBuffer buf = request.getRequestURL();
                            if (request.getQueryString() != null)
                                buf.append("?").append(request.getQueryString());

                            if (j_uri.equals(buf.toString()))
                            {
                                // This is a retry of an original POST request
                                // so restore method and parameters

                                session.removeAttribute(__J_POST);
                                Request base_request = HttpChannel.getCurrentHttpChannel().getRequest();
                                base_request.setMethod(HttpMethod.POST,HttpMethod.POST.asString());
                                base_request.setParameters(j_post);
                            }
                        }
                        else
                            session.removeAttribute(__J_URI);

                    }
                    return authentication;
                }
            }

            // if we can't send challenge
            if (DeferredAuthentication.isDeferred(response))
                return Authentication.UNAUTHENTICATED;

            // remember the current URI
            synchronized (session)
            {
                // But only if it is not set already, or we save every uri that leads to a login form redirect
                if (session.getAttribute(__J_URI)==null || _alwaysSaveUri)
                {
                    StringBuffer buf = request.getRequestURL();
                    if (request.getQueryString() != null)
                        buf.append("?").append(request.getQueryString());
                    session.setAttribute(__J_URI, buf.toString());

                    if (MimeTypes.Type.FORM_ENCODED.is(req.getContentType()) && HttpMethod.POST.is(request.getMethod()))
                    {
                        Request base_request = (req instanceof Request)?(Request)req:HttpChannel.getCurrentHttpChannel().getRequest();
                        base_request.extractParameters();
                        session.setAttribute(__J_POST, new MultiMap(base_request.getParameters()));
                    }
                }
            }

            // send the the challenge
            if (_dispatch)
            {
                RequestDispatcher dispatcher = request.getRequestDispatcher(_formLoginPage);
                response.setHeader(HttpHeader.CACHE_CONTROL.asString(),HttpHeaderValue.NO_CACHE.asString());
                response.setDateHeader(HttpHeader.EXPIRES.asString(),1);
                dispatcher.forward(new FormRequest(request), new FormResponse(response));
            }
            else
            {
                response.sendRedirect(response.encodeRedirectURL(URIUtil.addPaths(request.getContextPath(),_formLoginPage)));
            }
            return Authentication.SEND_CONTINUE;


        }
        catch (IOException | ServletException e)
        {
            throw new ServerAuthException(e);
        }
    }

    /* ------------------------------------------------------------ */
    public boolean isJSecurityCheck(String uri)
    {
        int jsc = uri.indexOf(__J_SECURITY_CHECK);

        if (jsc<0)
            return false;
        int e=jsc+__J_SECURITY_CHECK.length();
        if (e==uri.length())
            return true;
        char c = uri.charAt(e);
        return c==';'||c=='#'||c=='/'||c=='?';
    }

    /* ------------------------------------------------------------ */
    public boolean isLoginOrErrorPage(String pathInContext)
    {
        return pathInContext != null && (pathInContext.equals(_formErrorPath) || pathInContext.equals(_formLoginPath));
    }

    /* ------------------------------------------------------------ */
    public boolean secureResponse(ServletRequest req, ServletResponse res, boolean mandatory, User validatedUser) throws ServerAuthException
    {
        return true;
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    protected static class FormRequest extends HttpServletRequestWrapper
    {
        public FormRequest(HttpServletRequest request)
        {
            super(request);
        }

        @Override
        public long getDateHeader(String name)
        {
            if (name.toLowerCase().startsWith("if-"))
                return -1;
            return super.getDateHeader(name);
        }

        @Override
        public String getHeader(String name)
        {
            if (name.toLowerCase().startsWith("if-"))
                return null;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaderNames()
        {
            return Collections.enumeration(Collections.list(super.getHeaderNames()));
        }

        @Override
        public Enumeration<String> getHeaders(String name)
        {
            if (name.toLowerCase().startsWith("if-"))
                return Collections.<String>enumeration(Collections.<String>emptyList());
            return super.getHeaders(name);
        }
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    protected static class FormResponse extends HttpServletResponseWrapper
    {
        public FormResponse(HttpServletResponse response)
        {
            super(response);
        }

        @Override
        public void addDateHeader(String name, long date)
        {
            if (notIgnored(name))
                super.addDateHeader(name,date);
        }

        @Override
        public void addHeader(String name, String value)
        {
            if (notIgnored(name))
                super.addHeader(name,value);
        }

        @Override
        public void setDateHeader(String name, long date)
        {
            if (notIgnored(name))
                super.setDateHeader(name,date);
        }

        @Override
        public void setHeader(String name, String value)
        {
            if (notIgnored(name))
                super.setHeader(name,value);
        }

        private boolean notIgnored(String name)
        {
            if (HttpHeader.CACHE_CONTROL.is(name) ||
                HttpHeader.PRAGMA.is(name) ||
                HttpHeader.ETAG.is(name) ||
                HttpHeader.EXPIRES.is(name) ||
                HttpHeader.LAST_MODIFIED.is(name) ||
                HttpHeader.AGE.is(name))
                return false;
            return true;
        }
    }

    /* ------------------------------------------------------------ */
    /** This Authentication represents a just completed Form authentication.
     * Subsequent requests from the same user are authenticated by the presents
     * of a {@link SessionAuthentication} instance in their session.
     */
    public static class FormAuthentication extends UserAuthentication implements Authentication.ResponseSent
    {
        public FormAuthentication(String method, UserIdentity userIdentity)
        {
            super(method,userIdentity);
        }

        @Override
        public String toString()
        {
            return "Form"+super.toString();
        }
    }
}
