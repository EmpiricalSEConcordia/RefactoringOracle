//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.server.handler.gzip;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.zip.Deflater;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.GzipHttpContent;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.pathmap.PathSpecSet;
import org.eclipse.jetty.server.HttpOutput;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.IncludeExclude;
import org.eclipse.jetty.util.RegexSet;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * A Handler that can dynamically GZIP compress responses.   Unlike
 * previous and 3rd party GzipFilters, this mechanism works with asynchronously
 * generated responses and does not need to wrap the response or it's output
 * stream.  Instead it uses the efficient {@link org.eclipse.jetty.server.HttpOutput.Interceptor} mechanism.
 * <p>
 * The handler can be applied to the entire server (a gzip.mod is included in
 * the distribution) or it may be applied to individual contexts.
 * </p>
 */
public class GzipHandler extends HandlerWrapper implements GzipFactory
{
    private static final Logger LOG = Log.getLogger(GzipHandler.class);

    public final static String GZIP = "gzip";
    public final static String DEFLATE = "deflate";
    public final static int DEFAULT_MIN_GZIP_SIZE=16;
    private int _minGzipSize=DEFAULT_MIN_GZIP_SIZE;
    private int _compressionLevel=Deflater.DEFAULT_COMPRESSION;
    private boolean _checkGzExists = true;
    private boolean _syncFlush = false;
    private EnumSet<DispatcherType> _dispatchers = EnumSet.of(DispatcherType.REQUEST);

    // non-static, as other GzipHandler instances may have different configurations
    private final ThreadLocal<Deflater> _deflater = new ThreadLocal<>();

    private final IncludeExclude<String> _agentPatterns=new IncludeExclude<>(RegexSet.class);
    private final IncludeExclude<String> _methods = new IncludeExclude<>();
    private final IncludeExclude<String> _paths = new IncludeExclude<>(PathSpecSet.class);
    private final IncludeExclude<String> _mimeTypes = new IncludeExclude<>();

    private HttpField _vary;


    /* ------------------------------------------------------------ */
    /**
     * Instantiates a new gzip handler.
     * The excluded Mime Types are initialized to common known
     * images, audio, video and other already compressed types.
     * The included methods is initialized to GET.
     * The excluded agent patterns are set to exclude MSIE 6.0
     */
    public GzipHandler()
    {
        _methods.include(HttpMethod.GET.asString());
        for (String type:MimeTypes.getKnownMimeTypes())
        {
            if ("image/svg+xml".equals(type))
                _paths.exclude("*.svgz");
            else if (type.startsWith("image/")||
                type.startsWith("audio/")||
                type.startsWith("video/"))
                _mimeTypes.exclude(type);
        }
        _mimeTypes.exclude("application/compress");
        _mimeTypes.exclude("application/zip");
        _mimeTypes.exclude("application/gzip");
        _mimeTypes.exclude("application/bzip2");
        _mimeTypes.exclude("application/x-rar-compressed");
        LOG.debug("{} mime types {}",this,_mimeTypes);

        _agentPatterns.exclude(".*MSIE 6.0.*");
    }

    /* ------------------------------------------------------------ */
    /**
     * @param patterns Regular expressions matching user agents to exclude
     */
    public void addExcludedAgentPatterns(String... patterns)
    {
        _agentPatterns.exclude(patterns);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param methods The methods to exclude in compression
     */
    public void addExcludedMethods(String... methods)
    {
        for (String m : methods)
            _methods.exclude(m);
    }

    
    /* ------------------------------------------------------------ */
    public EnumSet<DispatcherType> getDispatcherTypes()
    {
        return _dispatchers;
    }

    /* ------------------------------------------------------------ */
    public void setDispatcherTypes(EnumSet<DispatcherType> dispatchers)
    {
        _dispatchers = dispatchers;
    }

    /* ------------------------------------------------------------ */
    public void setDispatcherTypes(DispatcherType... dispatchers)
    {
        _dispatchers = EnumSet.copyOf(Arrays.asList(dispatchers));
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the mime types.
     * @param types The mime types to exclude (without charset or other parameters).
     * For backward compatibility the mimetypes may be comma separated strings, but this
     * will not be supported in future versions.
     */
    public void addExcludedMimeTypes(String... types)
    {
        for (String t : types)
            _mimeTypes.exclude(StringUtil.csvSplit(t));
    }

    /* ------------------------------------------------------------ */
    /**
     * @param pathspecs Path specs (as per servlet spec) to exclude. If a
     * ServletContext is available, the paths are relative to the context path,
     * otherwise they are absolute.
     * For backward compatibility the pathspecs may be comma separated strings, but this
     * will not be supported in future versions.
     */
    public void addExcludedPaths(String... pathspecs)
    {
        for (String p : pathspecs)
            _paths.exclude(StringUtil.csvSplit(p));
    }

    /* ------------------------------------------------------------ */
    /**
     * @param patterns Regular expressions matching user agents to exclude
     */
    public void addIncludedAgentPatterns(String... patterns)
    {
        _agentPatterns.include(patterns);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param methods The methods to include in compression
     */
    public void addIncludedMethods(String... methods)
    {
        for (String m : methods)
            _methods.include(m);
    }

    /* ------------------------------------------------------------ */
    /**
     * @return True if {@link Deflater#SYNC_FLUSH} is used, else {@link Deflater#NO_FLUSH}
     */
    public boolean isSyncFlush()
    {
        return _syncFlush;
    }

    /* ------------------------------------------------------------ */
    /**
     * <p>Set the {@link Deflater} flush mode to use.  {@link Deflater#SYNC_FLUSH}
     * should be used if the application wishes to stream the data, but this may
     * hurt compression performance.
     * @param syncFlush True if {@link Deflater#SYNC_FLUSH} is used, else {@link Deflater#NO_FLUSH}
     */
    public void setSyncFlush(boolean syncFlush)
    {
        _syncFlush = syncFlush;
    }

    /* ------------------------------------------------------------ */
    /**
     * Add included mime types. Inclusion takes precedence over
     * exclusion.
     * @param types The mime types to include (without charset or other parameters)
     * For backward compatibility the mimetypes may be comma separated strings, but this
     * will not be supported in future versions.
     */
    public void addIncludedMimeTypes(String... types)
    {
        for (String t : types)
            _mimeTypes.include(StringUtil.csvSplit(t));
    }

    /* ------------------------------------------------------------ */
    /**
     * Add path specs to include. Inclusion takes precedence over exclusion.
     * @param pathspecs Path specs (as per servlet spec) to include. If a
     * ServletContext is available, the paths are relative to the context path,
     * otherwise they are absolute
     * For backward compatibility the pathspecs may be comma separated strings, but this
     * will not be supported in future versions.
     */
    public void addIncludedPaths(String... pathspecs)
    {
        for (String p : pathspecs)
            _paths.include(StringUtil.csvSplit(p));
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doStart() throws Exception
    {
        _vary=(_agentPatterns.size()>0)?GzipHttpOutputInterceptor.VARY_ACCEPT_ENCODING_USER_AGENT:GzipHttpOutputInterceptor.VARY_ACCEPT_ENCODING;
        super.doStart();
    }

    /* ------------------------------------------------------------ */
    public boolean getCheckGzExists()
    {
        return _checkGzExists;
    }

    /* ------------------------------------------------------------ */
    public int getCompressionLevel()
    {
        return _compressionLevel;
    }

    /* ------------------------------------------------------------ */
    @Override
    public Deflater getDeflater(Request request, long content_length)
    {
        String ua = request.getHttpFields().get(HttpHeader.USER_AGENT);
        if (ua!=null && !isAgentGzipable(ua))
        {
            LOG.debug("{} excluded user agent {}",this,request);
            return null;
        }

        if (content_length>=0 && content_length<_minGzipSize)
        {
            LOG.debug("{} excluded minGzipSize {}",this,request);
            return null;
        }

        // check the accept encoding header
        HttpField accept = request.getHttpFields().getField(HttpHeader.ACCEPT_ENCODING);

        if (accept==null)
        {
            LOG.debug("{} excluded !accept {}",this,request);
            return null;
        }
        boolean gzip = accept.contains("gzip");

        if (!gzip)
        {
            LOG.debug("{} excluded not gzip accept {}",this,request);
            return null;
        }

        Deflater df = _deflater.get();
        if (df==null)
            df=new Deflater(_compressionLevel,true);
        else
            _deflater.set(null);

        return df;
    }

    /* ------------------------------------------------------------ */
    public String[] getExcludedAgentPatterns()
    {
        Set<String> excluded=_agentPatterns.getExcluded();
        return excluded.toArray(new String[excluded.size()]);
    }

    /* ------------------------------------------------------------ */
    public String[] getExcludedMethods()
    {
        Set<String> excluded=_methods.getExcluded();
        return excluded.toArray(new String[excluded.size()]);
    }

    /* ------------------------------------------------------------ */
    public String[] getExcludedMimeTypes()
    {
        Set<String> excluded=_mimeTypes.getExcluded();
        return excluded.toArray(new String[excluded.size()]);
    }

    /* ------------------------------------------------------------ */
    public String[] getExcludedPaths()
    {
        Set<String> excluded=_paths.getExcluded();
        return excluded.toArray(new String[excluded.size()]);
    }

    /* ------------------------------------------------------------ */
    public String[] getIncludedAgentPatterns()
    {
        Set<String> includes=_agentPatterns.getIncluded();
        return includes.toArray(new String[includes.size()]);
    }

    /* ------------------------------------------------------------ */
    public String[] getIncludedMethods()
    {
        Set<String> includes=_methods.getIncluded();
        return includes.toArray(new String[includes.size()]);
    }

    /* ------------------------------------------------------------ */
    public String[] getIncludedMimeTypes()
    {
        Set<String> includes=_mimeTypes.getIncluded();
        return includes.toArray(new String[includes.size()]);
    }

    /* ------------------------------------------------------------ */
    public String[] getIncludedPaths()
    {
        Set<String> includes=_paths.getIncluded();
        return includes.toArray(new String[includes.size()]);
    }

    /* ------------------------------------------------------------ */
    @Deprecated
    public String[] getMethods()
    {
        return getIncludedMethods();
    }

    /* ------------------------------------------------------------ */
    /**
     * Get the minimum response size.
     *
     * @return minimum response size
     */
    public int getMinGzipSize()
    {
        return _minGzipSize;
    }

    /* ------------------------------------------------------------ */
    protected HttpField getVaryField()
    {
        return _vary;
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.server.handler.HandlerWrapper#handle(java.lang.String, org.eclipse.jetty.server.Request, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        ServletContext context = baseRequest.getServletContext();
        String path = context==null?baseRequest.getRequestURI():URIUtil.addPaths(baseRequest.getServletPath(),baseRequest.getPathInfo());
        LOG.debug("{} handle {} in {}",this,baseRequest,context);

        if (!_dispatchers.contains(baseRequest.getDispatcherType()))
        {
            LOG.debug("{} excluded by dispatcherType {}",this,baseRequest.getDispatcherType());
            _handler.handle(target,baseRequest, request, response);
            return;
        }

        // Are we already being gzipped?
        HttpOutput out = baseRequest.getResponse().getHttpOutput();
        HttpOutput.Interceptor interceptor = out.getInterceptor();
        while (interceptor!=null)
        {
            if (interceptor instanceof GzipHttpOutputInterceptor)
            {
                LOG.debug("{} already intercepting {}",this,request);
                _handler.handle(target,baseRequest, request, response);
                return;
            }
            interceptor=interceptor.getNextInterceptor();
        }

        // If not a supported method - no Vary because no matter what client, this URI is always excluded
        if (!_methods.matches(baseRequest.getMethod()))
        {
            LOG.debug("{} excluded by method {}",this,request);
            _handler.handle(target,baseRequest, request, response);
            return;
        }

        // If not a supported URI- no Vary because no matter what client, this URI is always excluded
        // Use pathInfo because this is be
        if (!isPathGzipable(path))
        {
            LOG.debug("{} excluded by path {}",this,request);
            _handler.handle(target,baseRequest, request, response);
            return;
        }

        // Exclude non compressible mime-types known from URI extension. - no Vary because no matter what client, this URI is always excluded
        String mimeType = context==null?MimeTypes.getDefaultMimeByExtension(path):context.getMimeType(path);
        if (mimeType!=null)
        {
            mimeType = MimeTypes.getContentTypeWithoutCharset(mimeType);
            if (!isMimeTypeGzipable(mimeType))
            {
                LOG.debug("{} excluded by path suffix mime type {}",this,request);
                // handle normally without setting vary header
                _handler.handle(target,baseRequest, request, response);
                return;
            }
        }

        if (_checkGzExists && context!=null)
        {
            String realpath=request.getServletContext().getRealPath(path);
            if (realpath!=null)
            {
                File gz=new File(realpath+".gz");
                if (gz.exists())
                {
                    LOG.debug("{} gzip exists {}",this,request);
                    // allow default servlet to handle
                    _handler.handle(target,baseRequest, request, response);
                    return;
                }
            }
        }

        // Special handling for etags
        String etag = baseRequest.getHttpFields().get(HttpHeader.IF_NONE_MATCH);
        if (etag!=null)
        {
            int i=etag.indexOf(GzipHttpContent.ETAG_GZIP_QUOTE);
            if (i>0)
            {
                baseRequest.setAttribute("o.e.j.s.h.gzip.GzipHandler.etag",etag);
                while (i>=0)
                {
                    etag=etag.substring(0,i)+etag.substring(i+GzipHttpContent.ETAG_GZIP.length());
                    i=etag.indexOf(GzipHttpContent.ETAG_GZIP_QUOTE,i);
                }
                baseRequest.getHttpFields().put(new HttpField(HttpHeader.IF_NONE_MATCH,etag));
            }
        }

        HttpOutput.Interceptor orig_interceptor = out.getInterceptor();
        try
        {
            // install interceptor and handle
            out.setInterceptor(new GzipHttpOutputInterceptor(this,getVaryField(),baseRequest.getHttpChannel(),orig_interceptor,isSyncFlush()));

            if (_handler!=null)
                _handler.handle(target,baseRequest, request, response);
        }
        finally
        {
            // reset interceptor if request not handled
            if (!baseRequest.isHandled() && !baseRequest.isAsyncStarted())
                out.setInterceptor(orig_interceptor);
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * Checks to see if the userAgent is excluded
     *
     * @param ua the user agent
     * @return boolean true if excluded
     */
    protected boolean isAgentGzipable(String ua)
    {
        if (ua == null)
            return false;

        return _agentPatterns.matches(ua);
    }

    /* ------------------------------------------------------------ */
    @Override
    public boolean isMimeTypeGzipable(String mimetype)
    {
        return _mimeTypes.matches(mimetype);
    }

    /* ------------------------------------------------------------ */
    /**
     * Checks to see if the path is included or not excluded
     *
     * @param requestURI
     *            the request uri
     * @return boolean true if gzipable
     */
    protected boolean isPathGzipable(String requestURI)
    {
        if (requestURI == null)
            return true;

        return _paths.matches(requestURI);
    }

    /* ------------------------------------------------------------ */
    @Override
    public void recycle(Deflater deflater)
    {
        if (_deflater.get()==null)
        {
            deflater.reset();
            _deflater.set(deflater);
        }
        else
            deflater.end();
    }

    /* ------------------------------------------------------------ */
    /**
     * @param checkGzExists If true, check if a static gz file exists for
     * the resource that the DefaultServlet may serve as precompressed.
     */
    public void setCheckGzExists(boolean checkGzExists)
    {
        _checkGzExists = checkGzExists;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param compressionLevel  The compression level to use to initialize {@link Deflater#setLevel(int)}
     */
    public void setCompressionLevel(int compressionLevel)
    {
        _compressionLevel = compressionLevel;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param patterns Regular expressions matching user agents to exclude
     */
    public void setExcludedAgentPatterns(String... patterns)
    {
        _agentPatterns.getExcluded().clear();
        addExcludedAgentPatterns(patterns);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param method to exclude
     */
    public void setExcludedMethods(String... method)
    {
        _methods.getExcluded().clear();
        _methods.exclude(method);
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the mime types.
     * @param types The mime types to exclude (without charset or other parameters)
     */
    public void setExcludedMimeTypes(String... types)
    {
        _mimeTypes.getExcluded().clear();
        _mimeTypes.exclude(types);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param pathspecs Path specs (as per servlet spec) to exclude. If a
     * ServletContext is available, the paths are relative to the context path,
     * otherwise they are absolute.
     */
    public void setExcludedPaths(String... pathspecs)
    {
        _paths.getExcluded().clear();
        _paths.exclude(pathspecs);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param patterns Regular expressions matching user agents to include
     */
    public void setIncludedAgentPatterns(String... patterns)
    {
        _agentPatterns.getIncluded().clear();
        addIncludedAgentPatterns(patterns);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param methods The methods to include in compression
     */
    public void setIncludedMethods(String... methods)
    {
        _methods.getIncluded().clear();
        _methods.include(methods);
    }

    /* ------------------------------------------------------------ */
    /**
     * Set included mime types. Inclusion takes precedence over
     * exclusion.
     * @param types The mime types to include (without charset or other parameters)
     */
    public void setIncludedMimeTypes(String... types)
    {
        _mimeTypes.getIncluded().clear();
        _mimeTypes.include(types);
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the path specs to include. Inclusion takes precedence over exclusion.
     * @param pathspecs Path specs (as per servlet spec) to include. If a
     * ServletContext is available, the paths are relative to the context path,
     * otherwise they are absolute
     */
    public void setIncludedPaths(String... pathspecs)
    {
        _paths.getIncluded().clear();
        _paths.include(pathspecs);
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the minimum response size to trigger dynamic compresssion
     *
     * @param minGzipSize minimum response size in bytes
     */
    public void setMinGzipSize(int minGzipSize)
    {
        _minGzipSize = minGzipSize;
    }
}
