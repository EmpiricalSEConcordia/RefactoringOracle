//
//  ========================================================================
//  Copyright (c) 1995-2015 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.servlets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.URIUtil;

/**
 * <p>This servlet may be used to concatenate multiple resources into
 * a single response.</p>
 * <p>It is intended to be used to load multiple
 * javascript or css files, but may be used for any content of the
 * same mime type that can be meaningfully concatenated.</p>
 * <p>The servlet uses {@link RequestDispatcher#include(javax.servlet.ServletRequest, javax.servlet.ServletResponse)}
 * to combine the requested content, so dynamically generated content
 * may be combined (Eg engine.js for DWR).</p>
 * <p>The servlet uses parameter names of the query string as resource names
 * relative to the context root.  So these script tags:</p>
 * <pre>
 * &lt;script type="text/javascript" src="../js/behaviour.js"&gt;&lt;/script&gt;
 * &lt;script type="text/javascript" src="../js/ajax.js&/chat/chat.js"&gt;&lt;/script&gt;
 * &lt;script type="text/javascript" src="../chat/chat.js"&gt;&lt;/script&gt;
 * </pre>
 * <p>can be replaced with the single tag (with the {@code ConcatServlet}
 * mapped to {@code /concat}):</p>
 * <pre>
 * &lt;script type="text/javascript" src="../concat?/js/behaviour.js&/js/ajax.js&/chat/chat.js"&gt;&lt;/script&gt;
 * </pre>
 * <p>The {@link ServletContext#getMimeType(String)} method is used to determine the
 * mime type of each resource. If the types of all resources do not match, then a 415
 * UNSUPPORTED_MEDIA_TYPE error is returned.</p>
 * <p>If the init parameter {@code development} is set to {@code true} then the servlet
 * will run in development mode and the content will be concatenated on every request.</p>
 * <p>Otherwise the init time of the servlet is used as the lastModifiedTime of the combined content
 * and If-Modified-Since requests are handled with 304 NOT Modified responses if
 * appropriate. This means that when not in development mode, the servlet must be
 * restarted before changed content will be served.</p>
 */
public class ConcatServlet extends HttpServlet
{
    private boolean _development;
    private long _lastModified;

    @Override
    public void init() throws ServletException
    {
        _lastModified = System.currentTimeMillis();
        _development = Boolean.parseBoolean(getInitParameter("development"));
    }

    /*
     * @return The start time of the servlet unless in development mode, in which case -1 is returned.
     */
    @Override
    protected long getLastModified(HttpServletRequest req)
    {
        return _development ? -1 : _lastModified;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        String query = request.getQueryString();
        if (query == null)
        {
            response.sendError(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        List<RequestDispatcher> dispatchers = new ArrayList<>();
        String[] parts = query.split("\\&");
        String type = null;
        for (String part : parts)
        {
            String path = URIUtil.canonicalPath(URIUtil.decodePath(part));
            if (path == null)
            {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Verify that the path is not protected.
            if (startsWith(path, "/WEB-INF/") || startsWith(path, "/META-INF/"))
            {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            String t = getServletContext().getMimeType(path);
            if (t != null)
            {
                if (type == null)
                {
                    type = t;
                }
                else if (!type.equals(t))
                {
                    response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
                    return;
                }
            }

            RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(path);
            if (dispatcher != null)
                dispatchers.add(dispatcher);
        }

        if (type != null)
            response.setContentType(type);

        for (RequestDispatcher dispatcher : dispatchers)
            dispatcher.include(request, response);
    }

    private boolean startsWith(String path, String prefix)
    {
        // Case insensitive match.
        return prefix.regionMatches(true, 0, path, 0, prefix.length());
    }
}
