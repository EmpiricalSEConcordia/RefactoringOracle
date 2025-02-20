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

package org.eclipse.jetty.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.IllegalSelectorException;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpGenerator;
import org.eclipse.jetty.http.HttpGenerator.ResponseInfo;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.ByteArrayISO8859Writer;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/** Response.
 * <p>
 * Implements {@link javax.servlet.http.HttpServletResponse} from the <code>javax.servlet.http</code> package.
 * </p>
 */
public class Response implements HttpServletResponse
{
    private static final Logger LOG = Log.getLogger(Response.class);

    public enum OutputState {NONE,STREAM,WRITER}

    /**
     * If a header name starts with this string,  the header (stripped of the prefix)
     * can be set during include using only {@link #setHeader(String, String)} or
     * {@link #addHeader(String, String)}.
     */
    public final static String SET_INCLUDE_HEADER_PREFIX = "org.eclipse.jetty.server.include.";

    /**
     * If this string is found within the comment of a cookie added with {@link #addCookie(Cookie)}, then the cookie
     * will be set as HTTP ONLY.
     */
    public final static String HTTP_ONLY_COMMENT="__HTTP_ONLY__";

    private final HttpChannel _channel;
    private final HttpFields _fields;
    private final AtomicBoolean _committed = new AtomicBoolean(false);
    private int _status=HttpStatus.NOT_SET_000;
    private String _reason;
    private Locale _locale;
    private MimeTypes.Type _mimeType;
    private String _characterEncoding;
    private String _contentType;
    private OutputState _outputState=OutputState.NONE;
    private PrintWriter _writer;
    private long _contentLength=-1;

    /* ------------------------------------------------------------ */
    /**
     *
     */
    public Response(HttpChannel channel)
    {
        _channel=channel;
        _fields=channel.getResponseFields();
    }

    /* ------------------------------------------------------------ */
    public HttpChannel getHttpChannel()
    {
        return _channel;
    }
    
    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#reset()
     */
    protected void recycle()
    {
        _status=HttpStatus.NOT_SET_000;
        _reason=null;
        _locale=null;
        _mimeType=null;
        _characterEncoding=null;
        _contentType=null;
        _writer=null;
        _outputState=OutputState.NONE;
        _contentLength=-1;
        _committed.set(false);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#addCookie(javax.servlet.http.Cookie)
     */
    public void addCookie(HttpCookie cookie)
    {
        _fields.addSetCookie(cookie);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#addCookie(javax.servlet.http.Cookie)
     */
    @Override
    public void addCookie(Cookie cookie)
    {
        String comment=cookie.getComment();
        boolean http_only=false;

        if (comment!=null)
        {
            int i=comment.indexOf(HTTP_ONLY_COMMENT);
            if (i>=0)
            {
                http_only=true;
                comment=comment.replace(HTTP_ONLY_COMMENT,"").trim();
                if (comment.length()==0)
                    comment=null;
            }
        }
        _fields.addSetCookie(cookie.getName(),
                cookie.getValue(),
                cookie.getDomain(),
                cookie.getPath(),
                cookie.getMaxAge(),
                comment,
                cookie.getSecure(),
                http_only || cookie.isHttpOnly(),
                cookie.getVersion());
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#containsHeader(java.lang.String)
     */
    @Override
    public boolean containsHeader(String name)
    {
        return _fields.containsKey(name);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#encodeURL(java.lang.String)
     */
    @Override
    public String encodeURL(String url)
    {
        final Request request=_channel.getRequest();
        SessionManager sessionManager = request.getSessionManager();
        if (sessionManager==null)
            return url;

        HttpURI uri = null;
        if (sessionManager.isCheckingRemoteSessionIdEncoding() && URIUtil.hasScheme(url))
        {
            uri = new HttpURI(url);
            String path = uri.getPath();
            path = (path == null?"":path);
            int port=uri.getPort();
            if (port<0)
                port = HttpScheme.HTTPS.asString().equalsIgnoreCase(uri.getScheme())?443:80;
            if (!request.getServerName().equalsIgnoreCase(uri.getHost()) ||
                request.getServerPort()!=port ||
                !path.startsWith(request.getContextPath())) //TODO the root context path is "", with which every non null string starts
                return url;
        }

        String sessionURLPrefix = sessionManager.getSessionIdPathParameterNamePrefix();
        if (sessionURLPrefix==null)
            return url;

        if (url==null)
            return null;

        // should not encode if cookies in evidence
        if (request.isRequestedSessionIdFromCookie())
        {
            int prefix=url.indexOf(sessionURLPrefix);
            if (prefix!=-1)
            {
                int suffix=url.indexOf("?",prefix);
                if (suffix<0)
                    suffix=url.indexOf("#",prefix);

                if (suffix<=prefix)
                    return url.substring(0,prefix);
                return url.substring(0,prefix)+url.substring(suffix);
            }
            return url;
        }

        // get session;
        HttpSession session=request.getSession(false);

        // no session
        if (session == null)
            return url;

        // invalid session
        if (!sessionManager.isValid(session))
            return url;

        String id=sessionManager.getNodeId(session);

        if (uri == null)
                uri = new HttpURI(url);


        // Already encoded
        int prefix=url.indexOf(sessionURLPrefix);
        if (prefix!=-1)
        {
            int suffix=url.indexOf("?",prefix);
            if (suffix<0)
                suffix=url.indexOf("#",prefix);

            if (suffix<=prefix)
                return url.substring(0,prefix+sessionURLPrefix.length())+id;
            return url.substring(0,prefix+sessionURLPrefix.length())+id+
                url.substring(suffix);
        }

        // edit the session
        int suffix=url.indexOf('?');
        if (suffix<0)
            suffix=url.indexOf('#');
        if (suffix<0)
        {
            return url+
                   ((HttpScheme.HTTPS.is(uri.getScheme()) || HttpScheme.HTTP.is(uri.getScheme())) && uri.getPath()==null?"/":"") + //if no path, insert the root path
                   sessionURLPrefix+id;
        }


        return url.substring(0,suffix)+
            ((HttpScheme.HTTPS.is(uri.getScheme()) || HttpScheme.HTTP.is(uri.getScheme())) && uri.getPath()==null?"/":"")+ //if no path so insert the root path
            sessionURLPrefix+id+url.substring(suffix);
    }

    /* ------------------------------------------------------------ */
    /**
     * @see javax.servlet.http.HttpServletResponse#encodeRedirectURL(java.lang.String)
     */
    @Override
    public String encodeRedirectURL(String url)
    {
        return encodeURL(url);
    }

    /* ------------------------------------------------------------ */
    @Override
    @Deprecated
    public String encodeUrl(String url)
    {
        return encodeURL(url);
    }

    /* ------------------------------------------------------------ */
    @Override
    @Deprecated
    public String encodeRedirectUrl(String url)
    {
        return encodeRedirectURL(url);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#sendError(int, java.lang.String)
     */
    @Override
    public void sendError(int code, String message) throws IOException
    {
    	if (_channel.isIncluding())
    		return;

        if (isCommitted())
            LOG.warn("Committed before "+code+" "+message);

        resetBuffer();
        _characterEncoding=null;
        setHeader(HttpHeader.EXPIRES,null);
        setHeader(HttpHeader.LAST_MODIFIED,null);
        setHeader(HttpHeader.CACHE_CONTROL,null);
        setHeader(HttpHeader.CONTENT_TYPE,null);
        setHeader(HttpHeader.CONTENT_LENGTH,null);

        _outputState=OutputState.NONE;
        setStatus(code,message);

        if (message==null)
            message=HttpStatus.getMessage(code);

        // If we are allowed to have a body
        if (code!=SC_NO_CONTENT &&
            code!=SC_NOT_MODIFIED &&
            code!=SC_PARTIAL_CONTENT &&
            code>=SC_OK)
        {
            Request request = _channel.getRequest();

            ErrorHandler error_handler = null;
            ContextHandler.Context context = request.getContext();
            if (context!=null)
                error_handler=context.getContextHandler().getErrorHandler();
            if (error_handler==null)
                error_handler = _channel.getServer().getBean(ErrorHandler.class);
            if (error_handler!=null)
            {
                request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE,new Integer(code));
                request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
                request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());
                request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME,request.getServletName());
                error_handler.handle(null,_channel.getRequest(),_channel.getRequest(),this );
            }
            else
            {
                setHeader(HttpHeader.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
                setContentType(MimeTypes.Type.TEXT_HTML_8859_1.toString());
                ByteArrayISO8859Writer writer= new ByteArrayISO8859Writer(2048);
                if (message != null)
                {
                    message= StringUtil.replace(message, "&", "&amp;");
                    message= StringUtil.replace(message, "<", "&lt;");
                    message= StringUtil.replace(message, ">", "&gt;");
                }
                String uri= request.getRequestURI();
                if (uri!=null)
                {
                    uri= StringUtil.replace(uri, "&", "&amp;");
                    uri= StringUtil.replace(uri, "<", "&lt;");
                    uri= StringUtil.replace(uri, ">", "&gt;");
                }

                writer.write("<html>\n<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html;charset=ISO-8859-1\"/>\n");
                writer.write("<title>Error ");
                writer.write(Integer.toString(code));
                writer.write(' ');
                if (message==null)
                    message=HttpStatus.getMessage(code);
                writer.write(message);
                writer.write("</title>\n</head>\n<body>\n<h2>HTTP ERROR: ");
                writer.write(Integer.toString(code));
                writer.write("</h2>\n<p>Problem accessing ");
                writer.write(uri);
                writer.write(". Reason:\n<pre>    ");
                writer.write(message);
                writer.write("</pre>");
                writer.write("</p>\n<hr /><i><small>Powered by Jetty://</small></i>");
                writer.write("\n</body>\n</html>\n");

                writer.flush();
                setContentLength(writer.size());
                writer.writeTo(getOutputStream());
                writer.destroy();
            }
        }
        else if (code!=SC_PARTIAL_CONTENT)
        {
            _channel.getRequestFields().remove(HttpHeader.CONTENT_TYPE);
            _channel.getRequestFields().remove(HttpHeader.CONTENT_LENGTH);
            _characterEncoding=null;
            _mimeType=null;
        }

        complete();
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#sendError(int)
     */
    @Override
    public void sendError(int sc) throws IOException
    {
        if (sc==102)
            sendProcessing();
        else
            sendError(sc,null);
    }

    /* ------------------------------------------------------------ */
    /* Send a 102-Processing response.
     * If the connection is a HTTP connection, the version is 1.1 and the
     * request has a Expect header starting with 102, then a 102 response is
     * sent. This indicates that the request still be processed and real response
     * can still be sent.   This method is called by sendError if it is passed 102.
     * @see javax.servlet.http.HttpServletResponse#sendError(int)
     */
    public void sendProcessing() throws IOException
    {
        if (_channel.isExpecting102Processing() && !isCommitted())
            _channel.commitResponse(HttpGenerator.PROGRESS_102_INFO,null);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#sendRedirect(java.lang.String)
     */
    @Override
    public void sendRedirect(String location) throws IOException
    {
    	if (_channel.isIncluding())
    		return;

        if (location==null)
            throw new IllegalArgumentException();

        if (!URIUtil.hasScheme(location))
        {
            StringBuilder buf = _channel.getRequest().getRootURL();
            if (location.startsWith("/"))
                buf.append(location);
            else
            {
                String path=_channel.getRequest().getRequestURI();
                String parent=(path.endsWith("/"))?path:URIUtil.parentPath(path);
                location=URIUtil.addPaths(parent,location);
                if(location==null)
                    throw new IllegalStateException("path cannot be above root");
                if (!location.startsWith("/"))
                    buf.append('/');
                buf.append(location);
            }

            location=buf.toString();
            HttpURI uri = new HttpURI(location);
            String path=uri.getDecodedPath();
            String canonical=URIUtil.canonicalPath(path);
            if (canonical==null)
                throw new IllegalArgumentException();
            if (!canonical.equals(path))
            {
                buf = _channel.getRequest().getRootURL();
                buf.append(URIUtil.encodePath(canonical));
                if (uri.getQuery()!=null)
                {
                    buf.append('?');
                    buf.append(uri.getQuery());
                }
                if (uri.getFragment()!=null)
                {
                    buf.append('#');
                    buf.append(uri.getFragment());
                }
                location=buf.toString();
            }
        }

        resetBuffer();
        setHeader(HttpHeader.LOCATION,location);
        setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
        complete();

    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#setDateHeader(java.lang.String, long)
     */
    @Override
    public void setDateHeader(String name, long date)
    {
        if (!_channel.isIncluding())
            _fields.putDateField(name, date);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#addDateHeader(java.lang.String, long)
     */
    @Override
    public void addDateHeader(String name, long date)
    {
        if (!_channel.isIncluding())
            _fields.addDateField(name, date);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#setHeader(java.lang.String, java.lang.String)
     */
    public void setHeader(HttpHeader name, String value)
    {
        if (HttpHeader.CONTENT_TYPE == name)
            setContentType(value);
        else
        {
            if (_channel.isIncluding())
                    return;

            _fields.put(name, value);

            if (HttpHeader.CONTENT_LENGTH==name)
            {
                if (value==null)
                    _contentLength=-1l;
                else
                    _contentLength=Long.parseLong(value);
            }
        }
    }
    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#setHeader(java.lang.String, java.lang.String)
     */
    @Override
    public void setHeader(String name, String value)
    {
        if (HttpHeader.CONTENT_TYPE.is(name))
            setContentType(value);
        else
        {
            if (_channel.isIncluding())
            {
                if (name.startsWith(SET_INCLUDE_HEADER_PREFIX))
                    name=name.substring(SET_INCLUDE_HEADER_PREFIX.length());
                else
                    return;
            }
            _fields.put(name, value);
            if (HttpHeader.CONTENT_LENGTH.is(name))
            {
                if (value==null)
                    _contentLength=-1l;
                else
                    _contentLength=Long.parseLong(value);
            }
        }
    }


    /* ------------------------------------------------------------ */
    @Override
    public Collection<String> getHeaderNames()
    {
        final HttpFields fields=_fields;
        return fields.getFieldNamesCollection();
    }

    /* ------------------------------------------------------------ */
    /*
     */
    @Override
    public String getHeader(String name)
    {
        return _fields.getStringField(name);
    }

    /* ------------------------------------------------------------ */
    /*
     */
    @Override
    public Collection<String> getHeaders(String name)
    {
        final HttpFields fields=_fields;
        Collection<String> i = fields.getValuesCollection(name);
        if (i==null)
            return Collections.emptyList();
        return i;
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#addHeader(java.lang.String, java.lang.String)
     */
    public void addHeader(HttpHeader name, String value)
    {
        if (_channel.isIncluding())
            return;

        _fields.add(name, value);
        if (HttpHeader.CONTENT_LENGTH==name)
        {
            if (value==null)
                _contentLength=-1l;
            else
                _contentLength=Long.parseLong(value);
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#addHeader(java.lang.String, java.lang.String)
     */
    @Override
    public void addHeader(String name, String value)
    {
        if (_channel.isIncluding())
        {
            if (name.startsWith(SET_INCLUDE_HEADER_PREFIX))
                name=name.substring(SET_INCLUDE_HEADER_PREFIX.length());
            else
                return;
        }

        _fields.add(name, value);
        if (HttpHeader.CONTENT_LENGTH.is(name))
        {
            if (value==null)
                _contentLength=-1l;
            else
                _contentLength=Long.parseLong(value);
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#setIntHeader(java.lang.String, int)
     */
    @Override
    public void setIntHeader(String name, int value)
    {
        if (!_channel.isIncluding())
        {
            _fields.putLongField(name, value);
            if (HttpHeader.CONTENT_LENGTH.is(name))
                _contentLength=value;
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#addIntHeader(java.lang.String, int)
     */
    @Override
    public void addIntHeader(String name, int value)
    {
        if (!_channel.isIncluding())
        {
            _fields.add(name, Integer.toString(value));
            if (HttpHeader.CONTENT_LENGTH.is(name))
                _contentLength=value;
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#setStatus(int)
     */
    @Override
    public void setStatus(int sc)
    {
        setStatus(sc,null);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.http.HttpServletResponse#setStatus(int, java.lang.String)
     */
    @Override
    public void setStatus(int sc, String sm)
    {
        if (sc<=0)
            throw new IllegalArgumentException();
        if (!_channel.isIncluding())
        {
            _status=sc;
            _reason=sm;
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#getCharacterEncoding()
     */
    @Override
    public String getCharacterEncoding()
    {
        if (_characterEncoding==null)
            _characterEncoding=StringUtil.__ISO_8859_1;
        return _characterEncoding;
    }

    /* ------------------------------------------------------------ */
    String getSetCharacterEncoding()
    {
        return _characterEncoding;
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#getContentType()
     */
    @Override
    public String getContentType()
    {
        return _contentType;
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#getOutputStream()
     */
    @Override
    public ServletOutputStream getOutputStream() throws IOException
    {
        if (_outputState==OutputState.WRITER)
            throw new IllegalStateException("WRITER");

        ServletOutputStream out = _channel.getOutputStream();
        _outputState=OutputState.STREAM;
        return out;
    }

    /* ------------------------------------------------------------ */
    public boolean isWriting()
    {
        return _outputState==OutputState.WRITER;
    }

    /* ------------------------------------------------------------ */
    public boolean isOutputing()
    {
        return _outputState!=OutputState.NONE;
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#getWriter()
     */
    @Override
    public PrintWriter getWriter() throws IOException
    {
        if (_outputState==OutputState.STREAM)
            throw new IllegalStateException("STREAM");

        /* if there is no writer yet */
        if (_writer==null)
        {
            /* get encoding from Content-Type header */
            String encoding = _characterEncoding;

            if (encoding==null)
            {
                encoding=MimeTypes.inferCharsetFromContentType(_contentType);
                if (encoding==null)
                    encoding = StringUtil.__ISO_8859_1;

                setCharacterEncoding(encoding);
            }

            /* construct Writer using correct encoding */
            _writer = _channel.getPrintWriter(encoding);
        }
        _outputState=OutputState.WRITER;
        return _writer;
    }



    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#setContentLength(int)
     */
    @Override
    public void setContentLength(int len)
    {
        // Protect from setting after committed as default handling
        // of a servlet HEAD request ALWAYS sets _content length, even
        // if the getHandling committed the response!
        if (isCommitted() || _channel.isIncluding())
            return;
        
        long written=_channel.getOutputStream().getWritten();
        if (written>len)
            throw new IllegalArgumentException("setContent("+len+") when already written "+written);
        
        _contentLength=len;
        _fields.putLongField(HttpHeader.CONTENT_LENGTH.toString(), len);

        if (_contentLength>0)
            checkAllContentWritten(written);
    }

    /* ------------------------------------------------------------ */
    public void checkAllContentWritten(long written)
    {
        if (_contentLength>=0 && written>=_contentLength)
        {
            try
            {
                switch(_outputState)
                {
                    case WRITER:
                        _writer.close();
                        break;
                    case STREAM:
                        getOutputStream().close();

                } 
            }
            catch(IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    /* ------------------------------------------------------------ */
    public long getLongContentLength()
    {
        return _contentLength;
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#setContentLength(int)
     */
    public void setLongContentLength(long len)
    {
        // Protect from setting after committed as default handling
        // of a servlet HEAD request ALWAYS sets _content length, even
        // if the getHandling committed the response!
        if (isCommitted() || _channel.isIncluding())
        	return;
        _contentLength=len;
        _fields.putLongField(HttpHeader.CONTENT_LENGTH.toString(), len);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#setCharacterEncoding(java.lang.String)
     */
    @Override
    public void setCharacterEncoding(String encoding)
    {
        if (_channel.isIncluding())
                return;

        if (_outputState==OutputState.NONE && !isCommitted())
        {
            if (encoding==null)
            {
                // Clear any encoding.
                if (_characterEncoding!=null)
                {
                    _characterEncoding=null;
                    if (_contentType!=null)
                    {
                        _contentType=MimeTypes.getContentTypeWithoutCharset(_contentType);
                        _fields.put(HttpHeader.CONTENT_TYPE,_contentType);
                    }
                }
            }
            else
            {
                // No, so just add this one to the mimetype
                _characterEncoding=StringUtil.normalizeCharset(encoding);
                if (_contentType!=null)
                {
                    _contentType=MimeTypes.getContentTypeWithoutCharset(_contentType)+";charset="+_characterEncoding;
                    _fields.put(HttpHeader.CONTENT_TYPE,_contentType);
                }
            }
        }
        
        
        /* TODO merged code not used???
                    else if (_mimeType!=null)
                        _contentType=_mimeType;



                    if (_contentType==null)
                        _connection.getResponseFields().remove(HttpHeaders.CONTENT_TYPE_BUFFER);
                    else
                        _connection.getResponseFields().put(HttpHeaders.CONTENT_TYPE_BUFFER,_contentType);
*/
        }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#setContentType(java.lang.String)
     */
    @Override
    public void setContentType(String contentType)
    {
        if (isCommitted() || _channel.isIncluding())
            return;

        if (contentType==null)
        {
            if (isWriting() && _characterEncoding!=null)
                throw new IllegalSelectorException();
            
            if (_locale==null)
                _characterEncoding=null;
            _mimeType=null;
            _contentType=null;
            _fields.remove(HttpHeader.CONTENT_TYPE);
        }
        else
        {
            _contentType=contentType;
            _mimeType=MimeTypes.CACHE.get(contentType);
            String charset;
            if (_mimeType!=null && _mimeType.getCharset()!=null)
                charset=_mimeType.getCharset().toString();
            else
                charset=MimeTypes.getCharsetFromContentType(contentType);
            
            if (charset==null)
            {
                if (_characterEncoding!=null)
                {
                    _contentType=contentType+";charset="+_characterEncoding;
                    _mimeType=null;
                }
            }
            else if (isWriting() && !charset.equals(_characterEncoding))
            {
                // too late to change the character encoding;
                _mimeType=null;
                _contentType=MimeTypes.getContentTypeWithoutCharset(_contentType);
                if (_characterEncoding!=null)
                    _contentType=_contentType+";charset="+_characterEncoding;
            }
            else
            {
                _characterEncoding=charset;
            }
            
            _fields.put(HttpHeader.CONTENT_TYPE,_contentType);
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#setBufferSize(int)
     */
    @Override
    public void setBufferSize(int size)
    {
        if (isCommitted() || getContentCount()>0 )
            throw new IllegalStateException("Committed or content written");
        _channel.increaseContentBufferSize(size);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#getBufferSize()
     */
    @Override
    public int getBufferSize()
    {
        return _channel.getContentBufferSize();
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#flushBuffer()
     */
    @Override
    public void flushBuffer() throws IOException
    {
        _channel.flushResponse();
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#reset()
     */
    @Override
    public void reset()
    {
        resetBuffer();
        fwdReset();
        _status=200;
        _reason=null;
        _contentLength=-1;

        HttpFields response_fields=_fields;

        response_fields.clear();
        String connection=_channel.getRequestFields().getStringField(HttpHeader.CONNECTION);
        if (connection!=null)
        {
            String[] values = connection.split(",");
            for  (int i=0;values!=null && i<values.length;i++)
            {
                HttpHeaderValue cb = HttpHeaderValue.CACHE.get(values[0].trim());

                if (cb!=null)
                {
                    switch(cb)
                    {
                        case CLOSE:
                            response_fields.put(HttpHeader.CONNECTION,HttpHeaderValue.CLOSE.toString());
                            break;

                        case KEEP_ALIVE:
                            if (HttpVersion.HTTP_1_0.is(_channel.getRequest().getProtocol()))
                                response_fields.put(HttpHeader.CONNECTION,HttpHeaderValue.KEEP_ALIVE.toString());
                            break;
                        case TE:
                            response_fields.put(HttpHeader.CONNECTION,HttpHeaderValue.TE.toString());
                            break;
                    }
                }
            }
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#reset()
     */
    public void fwdReset()
    {
        resetBuffer();

        _writer=null;
        _outputState=OutputState.NONE;
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#resetBuffer()
     */
    @Override
    public void resetBuffer()
    {
        if (isCommitted())
            throw new IllegalStateException("Committed");

        switch(_outputState)
        {
            case STREAM:
            case WRITER:
                _channel.getOutputStream().reset();
        }

        _channel.resetBuffer();
    }
    
    /* ------------------------------------------------------------ */
    public ResponseInfo commit()
    {
        if (!_committed.compareAndSet(false,true))
            throw new IllegalStateException();
        
        if (_status==HttpStatus.NOT_SET_000)
            _status=HttpStatus.OK_200;
        
        return new ResponseInfo(_channel.getRequest().getHttpVersion(),_fields,getLongContentLength(),getStatus(),getReason(),_channel.getRequest().isHead());
    }
    
    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#isCommitted()
     */
    @Override
    public boolean isCommitted()
    {
        return _committed.get();
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#setLocale(java.util.Locale)
     */
    @Override
    public void setLocale(Locale locale)
    {
        if (locale == null || isCommitted() ||_channel.isIncluding())
            return;

        _locale = locale;
        _fields.put(HttpHeader.CONTENT_LANGUAGE,locale.toString().replace('_','-'));

        if (_outputState!=OutputState.NONE )
            return;

        if (_channel.getRequest().getContext()==null)
            return;

        String charset = _channel.getRequest().getContext().getContextHandler().getLocaleEncoding(locale);

        if (charset!=null && charset.length()>0 && _characterEncoding==null)
            setCharacterEncoding(charset);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see javax.servlet.ServletResponse#getLocale()
     */
    @Override
    public Locale getLocale()
    {
        if (_locale==null)
            return Locale.getDefault();
        return _locale;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return The HTTP status code that has been set for this request. This will be <code>200<code>
     *    ({@link HttpServletResponse#SC_OK}), unless explicitly set through one of the <code>setStatus</code> methods.
     */
    @Override
    public int getStatus()
    {
        return _status;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return The reason associated with the current {@link #getStatus() status}. This will be <code>null</code>,
     *    unless one of the <code>setStatus</code> methods have been called.
     */
    public String getReason()
    {
        return _reason;
    }

    /* ------------------------------------------------------------ */
    /**
     */
    public void complete()
        throws IOException
    {
        _channel.completeResponse();
    }

    /* ------------------------------------------------------------ */
    public HttpFields getHttpFields()
    {
        return _fields;
    }
    
    /* ------------------------------------------------------------ */
    public long getContentCount()
    {
        return _channel.getOutputStream().getWritten();
    }
    
    /* ------------------------------------------------------------ */
    @Override
    public String toString()
    {
        return "HTTP/1.1 "+_status+" "+ (_reason==null?"":_reason) +System.getProperty("line.separator")+
        _fields.toString();
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private static class NullOutput extends ServletOutputStream
    {
        @Override
        public void write(int b) throws IOException
        {
        }

        @Override
        public void print(String s) throws IOException
        {
        }

        @Override
        public void println(String s) throws IOException
        {
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException
        {
        }

    }
}
