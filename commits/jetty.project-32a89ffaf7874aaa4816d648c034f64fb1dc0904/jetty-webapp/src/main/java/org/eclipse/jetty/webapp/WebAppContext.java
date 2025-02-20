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

package org.eclipse.jetty.webapp;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.PermissionCollection;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionListener;

import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.LazyList;
import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;

/* ------------------------------------------------------------ */
/** Web Application Context Handler.
 * The WebAppContext handler is an extension of ContextHandler that
 * coordinates the construction and configuration of nested handlers:
 * {@link org.eclipse.jetty.security.ConstraintSecurityHandler}, {@link org.eclipse.jetty.server.session.SessionHandler}
 * and {@link org.eclipse.jetty.servlet.ServletHandler}.
 * The handlers are configured by pluggable configuration classes, with
 * the default being  {@link org.eclipse.jetty.webapp.WebXmlConfiguration} and
 * {@link org.eclipse.jetty.webapp.JettyWebXmlConfiguration}.
 * 
 * @org.apache.xbean.XBean description="Creates a servlet web application at a given context from a resource base"
 * 
 */
public class WebAppContext extends ServletContextHandler implements WebAppClassLoader.Context
{
    public static final String TEMPDIR = "javax.servlet.context.tempdir";
    public static final String BASETEMPDIR = "org.eclipse.jetty.webapp.basetempdir";
    public final static String WEB_DEFAULTS_XML="org/eclipse/jetty/webapp/webdefault.xml";
    public final static String ERROR_PAGE="org.eclipse.jetty.server.error_page";
    public final static String SERVER_CONFIG = "org.eclipse.jetty.webapp.configuration";
    public final static String SERVER_SYS_CLASSES = "org.eclipse.jetty.webapp.systemClasses";
    public final static String SERVER_SRV_CLASSES = "org.eclipse.jetty.webapp.serverClasses";
    
    private static String[] __dftConfigurationClasses =
    {
        "org.eclipse.jetty.webapp.WebInfConfiguration",
        "org.eclipse.jetty.webapp.WebXmlConfiguration",
        "org.eclipse.jetty.webapp.MetaInfConfiguration",
        "org.eclipse.jetty.webapp.FragmentConfiguration",
        "org.eclipse.jetty.webapp.JettyWebXmlConfiguration",
        "org.eclipse.jetty.webapp.TagLibConfiguration"
    } ;
    
    // System classes are classes that cannot be replaced by 
    // the web application, and they are *always* loaded via 
    // system classloader.
    private final static String[] __dftSystemClasses = 
    {
        "java.",                            // Java SE classes (per servlet spec v2.5 / SRV.9.7.2) 
        "javax.",                           // Java SE classes (per servlet spec v2.5 / SRV.9.7.2)
        "org.xml.",                         // needed by javax.xml
        "org.w3c.",                         // needed by javax.xml
        "org.apache.commons.logging.",      // TODO: review if special case still needed 
        "org.eclipse.jetty.continuation.",  // webapp cannot change continuation classes
        "org.eclipse.jetty.jndi.",          // webapp cannot change naming classes
        "org.eclipse.jetty.plus.jaas.",     // webapp cannot change jaas classes
        "org.eclipse.jetty.websocket.",     // WebSocket is a jetty extension
        "org.eclipse.jetty.servlet.DefaultServlet"  // webapp cannot change default servlets
    } ;
    
    // Server classes are classes that are hidden from being
    // loaded by the web application using system classloader,
    // so if web application needs to load any of such classes,
    // it has to include them in its distribution.
    private final static String[] __dftServerClasses = 
    {
            "-org.eclipse.jetty.continuation.", // don't hide continuation classes
            "-org.eclipse.jetty.jndi.",         // don't hide naming classes
            "-org.eclipse.jetty.plus.jaas.",    // don't hide jaas classes
            "-org.eclipse.jetty.websocket.",    // don't hide websocket extension
            "-org.eclipse.jetty.servlet.DefaultServlet", // don't hide default servlet
            "org.eclipse.jetty."                // hide other jetty classes
    } ; 

    private String[] _configurationClasses = __dftConfigurationClasses;
    private ClasspathPattern _systemClasses = null;
    private ClasspathPattern _serverClasses = null;

    private Configuration[] _configurations;
    private String _defaultsDescriptor=WEB_DEFAULTS_XML;
    private String _descriptor=null;
    private String _overrideDescriptor=null;
    private boolean _distributable=false;
    private boolean _extractWAR=true;
    private boolean _copyDir=false;
    private boolean _logUrlOnStart =false;
    private boolean _parentLoaderPriority= Boolean.getBoolean("org.eclipse.jetty.server.webapp.parentLoaderPriority");
    private PermissionCollection _permissions;

    private File _tmpDir;
    private String _war;
    private String _extraClasspath;
    private Throwable _unavailableException;
    
    private Map _resourceAliases;
    private boolean _ownClassLoader=false;
    private boolean _configurationDiscovered=true;
    private boolean _configurationClassesSet=false;
    private boolean _configurationsSet=false;
    
    private final MetaData _metadata;

    public static WebAppContext getCurrentWebAppContext()
    {
        ContextHandler.Context context=ContextHandler.getCurrentContext();
        if (context!=null)
        {
            ContextHandler handler = context.getContextHandler();
            if (handler instanceof WebAppContext)
                return (WebAppContext)handler;
        }
        return null;
    }
    
    /* ------------------------------------------------------------ */
    public WebAppContext()
    {
        super(SESSIONS|SECURITY); 
        _scontext=new Context();
        setErrorHandler(new ErrorPageErrorHandler());      
        //Make a new MetaData to hold descriptor and annotation metadata
        _metadata = new MetaData(this);
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param contextPath The context path
     * @param webApp The URL or filename of the webapp directory or war file.
     */
    public WebAppContext(String webApp,String contextPath)
    {
        super(null,contextPath,SESSIONS|SECURITY);
        _scontext=new Context();
        setContextPath(contextPath);
        setWar(webApp);
        setErrorHandler(new ErrorPageErrorHandler());      
        //Make a new MetaData to hold descriptor and annotation metadata
        _metadata = new MetaData(this);
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param parent The parent HandlerContainer.
     * @param contextPath The context path
     * @param webApp The URL or filename of the webapp directory or war file.
     */
    public WebAppContext(HandlerContainer parent, String webApp, String contextPath)
    {
        super(parent,contextPath,SESSIONS|SECURITY);
        _scontext=new Context();
        setWar(webApp);
        setErrorHandler(new ErrorPageErrorHandler());      
        //Make a new MetaData to hold descriptor and annotation metadata
        _metadata = new MetaData(this);
    }

    /* ------------------------------------------------------------ */
    /**
     */
    public WebAppContext(SessionHandler sessionHandler, SecurityHandler securityHandler, ServletHandler servletHandler, ErrorHandler errorHandler)
    {
        super(null,sessionHandler,securityHandler,servletHandler,errorHandler);
        _scontext=new Context();
        setErrorHandler(errorHandler!=null?errorHandler:new ErrorPageErrorHandler());      
        //Make a new MetaData to hold descriptor and annotation metadata
        _metadata = new MetaData(this);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param servletContextName The servletContextName to set.
     */
    @Override
    public void setDisplayName(String servletContextName)
    {
        super.setDisplayName(servletContextName);
        ClassLoader cl = getClassLoader();
        if (cl!=null && cl instanceof WebAppClassLoader)
            ((WebAppClassLoader)cl).setName(servletContextName);
    }
    
    /* ------------------------------------------------------------ */
    /** Get an exception that caused the webapp to be unavailable
     * @return A throwable if the webapp is unavailable or null
     */
    public Throwable getUnavailableException()
    {
        return _unavailableException;
    }

    
    /* ------------------------------------------------------------ */
    /** Set Resource Alias.
     * Resource aliases map resource uri's within a context.
     * They may optionally be used by a handler when looking for
     * a resource.
     * @param alias
     * @param uri
     */
    public void setResourceAlias(String alias, String uri)
    {
        if (_resourceAliases == null)
            _resourceAliases= new HashMap(5);
        _resourceAliases.put(alias, uri);
    }

    /* ------------------------------------------------------------ */
    public Map getResourceAliases()
    {
        if (_resourceAliases == null)
            return null;
        return _resourceAliases;
    }
    
    /* ------------------------------------------------------------ */
    public void setResourceAliases(Map map)
    {
        _resourceAliases = map;
    }
    
    /* ------------------------------------------------------------ */
    public String getResourceAlias(String alias)
    {
        if (_resourceAliases == null)
            return null;
        return (String)_resourceAliases.get(alias);
    }

    /* ------------------------------------------------------------ */
    public String removeResourceAlias(String alias)
    {
        if (_resourceAliases == null)
            return null;
        return (String)_resourceAliases.remove(alias);
    }

    /* ------------------------------------------------------------ */
    /* (non-Javadoc)
     * @see org.eclipse.jetty.server.server.handler.ContextHandler#setClassLoader(java.lang.ClassLoader)
     */
    @Override
    public void setClassLoader(ClassLoader classLoader)
    {
        super.setClassLoader(classLoader);
        if (classLoader!=null && classLoader instanceof WebAppClassLoader)
            ((WebAppClassLoader)classLoader).setName(getDisplayName());
    }
    
    /* ------------------------------------------------------------ */
    @Override
    public Resource getResource(String uriInContext) throws MalformedURLException
    {
        IOException ioe= null;
        Resource resource= null;
        int loop=0;
        while (uriInContext!=null && loop++<100)
        {
            try
            {
                resource= super.getResource(uriInContext);
                if (resource != null && resource.exists())
                    return resource;
                
                uriInContext = getResourceAlias(uriInContext);
            }
            catch (IOException e)
            {
                Log.ignore(e);
                if (ioe==null)
                    ioe= e;
            }
        }

        if (ioe != null && ioe instanceof MalformedURLException)
            throw (MalformedURLException)ioe;

        return resource;
    }
    

    /* ------------------------------------------------------------ */
    /** Is the context Automatically configured.
     * 
     * @return true if configuration discovery.
     */
    public boolean isConfigurationDiscovered()
    {
        return _configurationDiscovered;
    }

    /* ------------------------------------------------------------ */
    /** Set the configuration discovery mode.
     * If configuration discovery is set to true, then the JSR315
     * servlet 3.0 discovered configuration features are enabled.
     * These are:<ul>
     * <li>Web Fragments</li>
     * <li>META-INF/resource directories</li>
     * </ul>
     * @param discovered true if configuration discovery is enabled for automatic configuration from the context
     */
    public void setConfigurationDiscovered(boolean discovered)
    {
        _configurationDiscovered = discovered;
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.thread.AbstractLifeCycle#doStart()
     */
    @Override
    protected void doStart() throws Exception
    {
        try
        {
            // Setup configurations
            loadConfigurations();

            // Setup system classes
            loadSystemClasses();
            
            // Setup server classes
            loadServerClasses();

            // Configure classloader
            _ownClassLoader=false;
            if (getClassLoader()==null)
            {
                WebAppClassLoader classLoader = new WebAppClassLoader(this);
                setClassLoader(classLoader);
                _ownClassLoader=true;
            }

            if (Log.isDebugEnabled())
            {
                ClassLoader loader = getClassLoader();
                Log.debug("Thread Context class loader is: " + loader);
                loader=loader.getParent();
                while(loader!=null)
                {
                    Log.debug("Parent class loader is: " + loader);
                    loader=loader.getParent();
                }
            }
            
          
            // Prepare for configuration     
            
            for (int i=0;i<_configurations.length;i++)
                _configurations[i].preConfigure(this);
            
            super.doStart();
        
            
            // Clean up after configuration
            for (int i=0;i<_configurations.length;i++)
                _configurations[i].postConfigure(this);


            if (isLogUrlOnStart())
                dumpUrl();
        }
        catch (Exception e)
        {
            //start up of the webapp context failed, make sure it is not started
            Log.warn("Failed startup of context "+this, e);
            _unavailableException=e;
            setAvailable(false);
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * Dumps the current web app name and URL to the log
     */
    public void dumpUrl()
    {
        Connector[] connectors = getServer().getConnectors();
        for (int i=0;i<connectors.length;i++)
        {
            String connectorName = connectors[i].getName();
            String displayName = getDisplayName();
            if (displayName == null)
                displayName = "WebApp@"+connectors.hashCode();
           
            Log.info(displayName + " at http://" + connectorName + getContextPath());
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.thread.AbstractLifeCycle#doStop()
     */
    @Override
    protected void doStop() throws Exception
    {
        super.doStop();

        try
        {
            for (int i=_configurations.length;i-->0;)
                _configurations[i].deconfigure(this);
            
            _configurations=null;
            
        }
        finally
        {
            if (_ownClassLoader)
                setClassLoader(null);

            setAvailable(true);
            _unavailableException=null;
        }
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the configurations.
     */
    public String[] getConfigurationClasses()
    {
        return _configurationClasses;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the configurations.
     */
    public Configuration[] getConfigurations()
    {
        return _configurations;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * The default descriptor is a web.xml format file that is applied to the context before the standard WEB-INF/web.xml
     * @return Returns the defaultsDescriptor.
     */
    public String getDefaultsDescriptor()
    {
        return _defaultsDescriptor;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * The override descriptor is a web.xml format file that is applied to the context after the standard WEB-INF/web.xml
     * @return Returns the Override Descriptor.
     */
    public String getOverrideDescriptor()
    {
        return _overrideDescriptor;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the permissions.
     */
    public PermissionCollection getPermissions()
    {
        return _permissions;
    }

    /* ------------------------------------------------------------ */
    /**
     * @see #setServerClasses(String[])
     * @return Returns the serverClasses.
     */
    public String[] getServerClasses()
    {
        if (_serverClasses == null)
            loadServerClasses();
        
        return _serverClasses.getPatterns();
    }

    public void addServerClass(String classname)
    {
        if (_serverClasses == null)
            loadServerClasses();
        
        _serverClasses.addPattern(classname);
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @see #setSystemClasses(String[])
     * @return Returns the systemClasses.
     */
    public String[] getSystemClasses()
    {
        if (_systemClasses == null)
            loadSystemClasses();
        
        return _systemClasses.getPatterns();
    }
    
    /* ------------------------------------------------------------ */
    public void addSystemClass(String classname)
    {
        if (_systemClasses == null)
            loadSystemClasses();
        
        _systemClasses.addPattern(classname);
    }
    
    /* ------------------------------------------------------------ */
    public boolean isServerClass(String name)
    {
        if (_serverClasses == null)
            loadServerClasses();
        
        return _serverClasses.match(name);
    }

    /* ------------------------------------------------------------ */
    public boolean isSystemClass(String name)
    {
        if (_systemClasses == null)
            loadSystemClasses();
        
        return _systemClasses.match(name);
    }

    private void loadSystemClasses()
    {
        if (_systemClasses != null)
            return;
        
        //look for a Server attribute with the list of System classes
        //to apply to every web application. If not present, use our defaults.
        Server server = getServer();
        if (server != null)
        {
            Object systemClasses = server.getAttribute(SERVER_SYS_CLASSES);
            if (systemClasses != null && systemClasses instanceof String[])
                _systemClasses = ClasspathPattern.fromArray((String[])systemClasses);
        }
        
        if (_systemClasses == null)
            _systemClasses = ClasspathPattern.fromArray(__dftSystemClasses);
    }
    
    private void loadServerClasses()
    {
        if (_serverClasses != null)
            return;
        
        //look for a Server attribute with the list of Server classes
        //to apply to every web application. If not present, use our defaults.
        Server server = getServer();
        if (server != null)
        {
            Object serverClasses = server.getAttribute(SERVER_SRV_CLASSES);
            if (serverClasses != null || serverClasses instanceof String[])
                _serverClasses = ClasspathPattern.fromArray((String[])serverClasses);
        }
        
        if (_serverClasses == null)
            _serverClasses = ClasspathPattern.fromArray(__dftServerClasses);
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the war as a file or URL string (Resource)
     */
    public String getWar()
    {
        if (_war==null)
            _war=getResourceBase();
        return _war;
    }

    /* ------------------------------------------------------------ */
    public Resource getWebInf() throws IOException
    {
        if (super.getBaseResource() == null)
            return null;

        // Iw there a WEB-INF directory?
        Resource web_inf= super.getBaseResource().addPath("WEB-INF/");
        if (!web_inf.exists() || !web_inf.isDirectory())
            return null;
        
        return web_inf;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the distributable.
     */
    public boolean isDistributable()
    {
        return _distributable;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the extractWAR.
     */
    public boolean isExtractWAR()
    {
        return _extractWAR;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return True if the webdir is copied (to allow hot replacement of jars)
     */
    public boolean isCopyWebDir()
    {
        return _copyDir;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return True if the classloader should delegate first to the parent 
     * classloader (standard java behaviour) or false if the classloader 
     * should first try to load from WEB-INF/lib or WEB-INF/classes (servlet 
     * spec recommendation).
     */
    public boolean isParentLoaderPriority()
    {
        return _parentLoaderPriority;
    }
    
    
    /* ------------------------------------------------------------ */
    public String[] getDefaultConfigurationClasses ()
    {
        return __dftConfigurationClasses;
    }
    
    
    /* ------------------------------------------------------------ */
    protected void loadConfigurations()
    	throws Exception
    {
        //if the configuration instances have been set explicitly, use them
        if (_configurations!=null)
            return;

        //if the configuration classnames have been set explicitly use them
        if (!_configurationClassesSet)
            _configurationClasses=__dftConfigurationClasses;

        _configurations = new Configuration[_configurationClasses.length];
        for (int i = 0; i < _configurationClasses.length; i++)
        {
            _configurations[i]=(Configuration)Loader.loadClass(this.getClass(), _configurationClasses[i]).newInstance();
        }
    }
    
    /* ------------------------------------------------------------ */
    @Override
    protected boolean isProtectedTarget(String target)
    {
        while (target.startsWith("//"))
            target=URIUtil.compactPath(target);
         
        return StringUtil.startsWithIgnoreCase(target, "/web-inf") || StringUtil.startsWithIgnoreCase(target, "/meta-inf");
    }
    

    /* ------------------------------------------------------------ */
    @Override
    public String toString()
    {
        return super.toString()+(_war==null?"":(","+_war));
    }
    
   
       

    /* ------------------------------------------------------------ */
    /**
     * @param configurations The configuration class names.  If setConfigurations is not called
     * these classes are used to create a configurations array.
     */
    public void setConfigurationClasses(String[] configurations)
    {
        _configurationClasses = configurations==null?null:(String[])configurations.clone();
        _configurationClassesSet = true;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param configurations The configurations to set.
     */
    public void setConfigurations(Configuration[] configurations)
    {
        _configurations = configurations==null?null:(Configuration[])configurations.clone();
        _configurationsSet = true;
    }

    /* ------------------------------------------------------------ */
    /**
     * The default descriptor is a web.xml format file that is applied to the context before the standard WEB-INF/web.xml
     * @param defaultsDescriptor The defaultsDescriptor to set.
     */
    public void setDefaultsDescriptor(String defaultsDescriptor)
    {
        _defaultsDescriptor = defaultsDescriptor;
    }

    /* ------------------------------------------------------------ */
    /**
     * The override descriptor is a web.xml format file that is applied to the context after the standard WEB-INF/web.xml
     * @param overrideDescriptor The overrideDescritpor to set.
     */
    public void setOverrideDescriptor(String overrideDescriptor)
    {
        _overrideDescriptor = overrideDescriptor;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return the web.xml descriptor to use. If set to null, WEB-INF/web.xml is used if it exists.
     */
    public String getDescriptor()
    {
        return _descriptor;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param descriptor the web.xml descriptor to use. If set to null, WEB-INF/web.xml is used if it exists.
     */
    public void setDescriptor(String descriptor)
    {
        _descriptor=descriptor;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param distributable The distributable to set.
     */
    public void setDistributable(boolean distributable)
    {
        this._distributable = distributable;
    }

    /* ------------------------------------------------------------ */
    @Override
    public void setEventListeners(EventListener[] eventListeners)
    {
        if (_sessionHandler!=null)
            _sessionHandler.clearEventListeners();
            
        super.setEventListeners(eventListeners);
      
        for (int i=0; eventListeners!=null && i<eventListeners.length;i ++)
        {
            EventListener listener = eventListeners[i];
            
            if ((listener instanceof HttpSessionActivationListener)
                            || (listener instanceof HttpSessionAttributeListener)
                            || (listener instanceof HttpSessionBindingListener)
                            || (listener instanceof HttpSessionListener))
            {
                if (_sessionHandler!=null)
                    _sessionHandler.addEventListener(listener);
            }
            
        }
    }

    /* ------------------------------------------------------------ */
    /** Add EventListener
     * Conveniance method that calls {@link #setEventListeners(EventListener[])}
     * @param listener
     */
    @Override
    public void addEventListener(EventListener listener)
    {   
        setEventListeners((EventListener[])LazyList.addToArray(getEventListeners(), listener, EventListener.class));
    }

    
    /* ------------------------------------------------------------ */
    /**
     * @param extractWAR True if war files are extracted
     */
    public void setExtractWAR(boolean extractWAR)
    {
        _extractWAR = extractWAR;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * 
     * @param copy True if the webdir is copied (to allow hot replacement of jars)
     */
    public void setCopyWebDir(boolean copy)
    {
        _copyDir = copy;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param java2compliant The java2compliant to set.
     */
    public void setParentLoaderPriority(boolean java2compliant)
    {
        _parentLoaderPriority = java2compliant;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param permissions The permissions to set.
     */
    public void setPermissions(PermissionCollection permissions)
    {
        _permissions = permissions;
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the server classes patterns.
     * <p>
     * Server classes/packages are classes used to implement the server and are hidden
     * from the context.  If the context needs to load these classes, it must have its
     * own copy of them in WEB-INF/lib or WEB-INF/classes.
     * A class pattern is a string of one of the forms:<dl>
     * <dt>org.package.Classname</dt><dd>Match a specific class</dd>
     * <dt>org.package.</dt><dd>Match a specific package hierarchy</dd>
     * <dt>-org.package.Classname</dt><dd>Exclude a specific class</dd>
     * <dt>-org.package.</dt><dd>Exclude a specific package hierarchy</dd>
     * </dl>
     * @param serverClasses The serverClasses to set.
     */
    public void setServerClasses(String[] serverClasses)
    {
        _serverClasses = ClasspathPattern.fromArray(serverClasses);
    }
    
    /* ------------------------------------------------------------ */
    /**
     * Set the system classes patterns.
     * <p>
     * System classes/packages are classes provided by the JVM and that
     * cannot be replaced by classes of the same name from WEB-INF,
     * regardless of the value of {@link #setParentLoaderPriority(boolean)}.
     * A class pattern is a string of one of the forms:<dl>
     * <dt>org.package.Classname</dt><dd>Match a specific class</dd>
     * <dt>org.package.</dt><dd>Match a specific package hierarchy</dd>
     * <dt>-org.package.Classname</dt><dd>Exclude a specific class</dd>
     * <dt>-org.package.</dt><dd>Exclude a specific package hierarchy</dd>
     * </dl>
     * @param systemClasses The systemClasses to set.
     */
    public void setSystemClasses(String[] systemClasses)
    {
        _systemClasses = ClasspathPattern.fromArray(systemClasses);
    }
    

    /* ------------------------------------------------------------ */
    /** Set temporary directory for context.
     * The javax.servlet.context.tempdir attribute is also set.
     * @param dir Writable temporary directory.
     */
    public void setTempDirectory(File dir)
    {
        if (isStarted())
            throw new IllegalStateException("Started");

        if (dir!=null)
        {
            try{dir=new File(dir.getCanonicalPath());}
            catch (IOException e){Log.warn(Log.EXCEPTION,e);}
        }

        if (dir!=null && !dir.exists())
        {
            dir.mkdir();
            dir.deleteOnExit();
        }

        if (dir!=null && ( !dir.exists() || !dir.isDirectory() || !dir.canWrite()))
            throw new IllegalArgumentException("Bad temp directory: "+dir);

        _tmpDir=dir;
        setAttribute(TEMPDIR,_tmpDir);
    }
    
    public File getTempDirectory ()
    {
        return _tmpDir;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param war The war to set as a file name or URL
     */
    public void setWar(String war)
    {
        _war = war;
    }


    /* ------------------------------------------------------------ */
    /**
     * @return Comma or semicolon separated path of filenames or URLs
     * pointing to directories or jar files. Directories should end
     * with '/'.
     */
    public String getExtraClasspath()
    {
        return _extraClasspath;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param extraClasspath Comma or semicolon separated path of filenames or URLs
     * pointing to directories or jar files. Directories should end
     * with '/'.
     */
    public void setExtraClasspath(String extraClasspath)
    {
        _extraClasspath=extraClasspath;
    }

    /* ------------------------------------------------------------ */
    public boolean isLogUrlOnStart()
    {
        return _logUrlOnStart;
    }

    /* ------------------------------------------------------------ */
    /**
     * Sets whether or not the web app name and URL is logged on startup
     *
     * @param logOnStart whether or not the log message is created
     */
    public void setLogUrlOnStart(boolean logOnStart)
    {
        this._logUrlOnStart = logOnStart;
    }
    
    
    /* ------------------------------------------------------------ */
    @Override
    public void setServer(Server server)
    {
        super.setServer(server);
        //if we haven't been given a set of configuration instances to 
        //use, and we haven't been given a set of configuration classes
        //to use, use the configuration classes that came from the
        //Server (if there are any)
        if (!_configurationsSet && !_configurationClassesSet && server != null)
        {
            String[] serverConfigs = (String[])server.getAttribute(SERVER_CONFIG);
            if (serverConfigs != null)
                setConfigurationClasses(serverConfigs);
        }
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void startContext()
        throws Exception
    {
        // Configure webapp
        for (int i=0;i<_configurations.length;i++)
            _configurations[i].configure(this);
 
        //resolve the metadata
        _metadata.resolve(this);
        
        super.startContext();
    }
    
    /* ------------------------------------------------------------ */
    public class Context extends ServletContextHandler.Context
    {
        /* ------------------------------------------------------------ */
        public URL getResource(String path) throws MalformedURLException
        {
            Resource resource=WebAppContext.this.getResource(path);
            if (resource==null || !resource.exists())
                return null;
            
            // Should we go to the original war?
            if (resource.isDirectory() && resource instanceof ResourceCollection && !WebAppContext.this.isExtractWAR())
            {
                Resource[] resources = ((ResourceCollection)resource).getResources();
                for (int i=resources.length;i-->0;)
                {
                    if (resources[i].getName().startsWith("jar:file"))
                        return resources[i].getURL();
                }
            }
                
            return resource.getURL();
        }
    }

    /* ------------------------------------------------------------ */
    public MetaData getMetaData()
    {
        return _metadata;
    }

}
