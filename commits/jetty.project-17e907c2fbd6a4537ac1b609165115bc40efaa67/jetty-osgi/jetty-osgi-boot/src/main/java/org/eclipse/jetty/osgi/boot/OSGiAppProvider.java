// ========================================================================
// Copyright (c) 2009-2010 Mortbay, Inc.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// Contributors:
//    Greg Wilkins - initial API and implementation
// ========================================================================
package org.eclipse.jetty.osgi.boot;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Map.Entry;

import org.eclipse.jetty.deploy.App;
import org.eclipse.jetty.deploy.AppProvider;
import org.eclipse.jetty.deploy.DeploymentManager;
import org.eclipse.jetty.deploy.providers.ContextProvider;
import org.eclipse.jetty.deploy.providers.ScanningAppProvider;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.osgi.framework.Bundle;

/**
 * AppProvider for OSGi. Supports the configuration of ContextHandlers and
 * WebApps. Extends the AbstractAppProvider to support the scanning of context
 * files located outside of the bundles.
 * <p>
 * This provider must not be called outside of jetty.boot: it should always be
 * called via the OSGi service listener.
 * </p>
 * <p>
 * This provider supports the same set of parameters than the WebAppProvider as
 * it supports the deployment of WebAppContexts. Except for the scanning of the
 * webapps directory.
 * </p>
 */
public class OSGiAppProvider extends ScanningAppProvider implements AppProvider
{

    private boolean _extractWars = true;
    private boolean _parentLoaderPriority = false;
    private String _defaultsDescriptor;
    private String _tldBundles;
    private String[] _configurationClasses;
    
    /**
     * When a context file corresponds to a deployed bundle and is changed we
     * reload the corresponding bundle.
     */
    private static class Filter implements FilenameFilter
    {
        OSGiAppProvider _enclosedInstance;

        public boolean accept(File dir, String name)
        {
            if (!new File(dir,name).isDirectory())
            {
                String contextName = getDeployedAppName(name);
                if (contextName != null)
                {
                    App app = _enclosedInstance.getDeployedApps().get(contextName);
                    return app != null;
                }
            }
            return false;
        }
    }

    /**
     * @param contextFileName
     *            for example myContext.xml
     * @return The context, for example: myContext; null if this was not a
     *         suitable contextFileName.
     */
    private static String getDeployedAppName(String contextFileName)
    {
        String lowername = contextFileName.toLowerCase();
        if (lowername.endsWith(".xml"))
        {
            String contextName = contextFileName.substring(0,lowername.length() - ".xml".length());
            return contextName;
        }
        return null;
    }

    /**
     * Reading the display name of a webapp is really not sufficient for indexing the various
     * deployed ContextHandlers.
     * 
     * @param context
     * @return
     */
    private String getContextHandlerAppName(ContextHandler context) {
        String appName = context.getDisplayName();
        if (appName == null || appName.length() == 0  || getDeployedApps().containsKey(appName)) {
        	if (context instanceof WebAppContext)
        	{
        		appName = ((WebAppContext)context).getContextPath();
        		if (getDeployedApps().containsKey(appName)) {
            		appName = "noDisplayName"+context.getClass().getSimpleName()+context.hashCode();
            	}
        	} else {
        		appName = "noDisplayName"+context.getClass().getSimpleName()+context.hashCode();
        	}
        }
        return appName;
    }
    
    /**
     * Default OSGiAppProvider consutructed when none are defined in the
     * jetty.xml configuration.
     */
    public OSGiAppProvider()
    {
        super(new Filter());
        ((Filter)super._filenameFilter)._enclosedInstance = this;
    }

    /**
     * Default OSGiAppProvider consutructed when none are defined in the
     * jetty.xml configuration.
     * 
     * @param contextsDir
     */
    public OSGiAppProvider(File contextsDir) throws IOException
    {
        this();
        setMonitoredDirResource(Resource.newResource(contextsDir.toURI()));
    }
    
    /**
     * Returns the ContextHandler that was created by WebappRegistractionHelper
     * 
     * @see AppProvider
     */
    public ContextHandler createContextHandler(App app) throws Exception
    {
        // return pre-created Context
        ContextHandler wah = app.getContextHandler();
        if (wah == null)
        {
            // for some reason it was not defined when the App was constructed.
            // we don't support this situation at this point.
            // once the WebAppRegistrationHelper is refactored, the code
            // that creates the ContextHandler will actually be here.
            throw new IllegalStateException("The App must be passed the " + "instance of the ContextHandler when it is construsted");
        }
        if (_configurationClasses != null && wah instanceof WebAppContext) 
        {
            ((WebAppContext)wah).setConfigurationClasses(_configurationClasses);
        }
        return app.getContextHandler();
    }

    /**
     * @see AppProvider
     */
    public void setDeploymentManager(DeploymentManager deploymentManager)
    {
        // _manager=deploymentManager;
        super.setDeploymentManager(deploymentManager);
    }

    private static String getOriginId(Bundle contributor, String pathInBundle)
    {
    	return contributor.getSymbolicName() + "-" + contributor.getVersion().toString() +
    		(pathInBundle.startsWith("/") ? pathInBundle : "/" + pathInBundle);
    }
    
    /**
     * @param context
     * @throws Exception
     */
    public void addContext(Bundle contributor, String pathInBundle, ContextHandler context) throws Exception
    {
    	addContext(getOriginId(contributor, pathInBundle), context);
    }
    /**
     * @param context
     * @throws Exception
     */
    public void addContext(String originId, ContextHandler context) throws Exception
    {
        // TODO apply configuration specific to this provider
    	if (context instanceof WebAppContext)
    	{
           ((WebAppContext)context).setExtractWAR(isExtract());
    	}

        // wrap context as an App
        App app = new App(getDeploymentManager(),this,originId,context);
        String appName = getContextHandlerAppName(context);
        getDeployedApps().put(appName,app);
        getDeploymentManager().addApp(app);
    }
    
    

    /**
     * Called by the scanner of the context files directory. If we find the
     * corresponding deployed App we reload it by returning the App. Otherwise
     * we return null and nothing happens: presumably the corresponding OSGi
     * webapp is not ready yet.
     * 
     * @return the corresponding already deployed App so that it will be
     *         reloaded. Otherwise returns null.
     */
    @Override
    protected App createApp(String filename)
    {
        // find the corresponding bundle and ContextHandler or WebAppContext
        // and reload the corresponding App.
        // see the 2 pass of the refactoring of the WebAppRegistrationHelper.
        String name = getDeployedAppName(filename);
        if (name != null)
        {
            return getDeployedApps().get(name);
        }
        return null;
    }

    public void removeContext(ContextHandler context) throws Exception
    {
    	String appName = getContextHandlerAppName(context);
        App app = getDeployedApps().remove(context.getDisplayName());
        if (app == null) {
        	//try harder to undeploy this context handler.
        	//see bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=330098
        	appName = null;
        	for (Entry<String,App> deployedApp : getDeployedApps().entrySet()) {
        		if (deployedApp.getValue().getContextHandler() == context) {
        			app = deployedApp.getValue();
        			appName = deployedApp.getKey();
        			break;
        		}
        	}
        	if (appName != null) {
        		getDeployedApps().remove(appName);
        	}
        }
        if (app != null)
        {
            getDeploymentManager().removeApp(app);
        }
    }

    // //copied from WebAppProvider as the parameters are identical.
    // //only removed the parameer related to extractWars.
    /* ------------------------------------------------------------ */
    /**
     * Get the parentLoaderPriority.
     * 
     * @return the parentLoaderPriority
     */
    public boolean isParentLoaderPriority()
    {
        return _parentLoaderPriority;
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the parentLoaderPriority.
     * 
     * @param parentLoaderPriority
     *            the parentLoaderPriority to set
     */
    public void setParentLoaderPriority(boolean parentLoaderPriority)
    {
        _parentLoaderPriority = parentLoaderPriority;
    }

    /* ------------------------------------------------------------ */
    /**
     * Get the defaultsDescriptor.
     * 
     * @return the defaultsDescriptor
     */
    public String getDefaultsDescriptor()
    {
        return _defaultsDescriptor;
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the defaultsDescriptor.
     * 
     * @param defaultsDescriptor
     *            the defaultsDescriptor to set
     */
    public void setDefaultsDescriptor(String defaultsDescriptor)
    {
        _defaultsDescriptor = defaultsDescriptor;
    }

    /**
     * The context xml directory. In fact it is the directory watched by the
     * scanner.
     */
    public File getContextXmlDirAsFile()
    {
        try
        {
            Resource monitoredDir = getMonitoredDirResource();
            if (monitoredDir == null)
                return null;
            return monitoredDir.getFile();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * The context xml directory. In fact it is the directory watched by the
     * scanner.
     */
    public String getContextXmlDir()
    {
        try
        {
            Resource monitoredDir = getMonitoredDirResource();
            if (monitoredDir == null)
                return null;
            return monitoredDir.getFile().toURI().toString();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return null;
        }
    }
    
    public boolean isExtract()
    {
        return _extractWars;
    }

    public void setExtract(boolean extract)
    {
        _extractWars=extract;
    }


    /* ------------------------------------------------------------ */
    /**
     * Set the directory in which to look for context XML files.
     * <p>
     * If a webapp call "foo/" or "foo.war" is discovered in the monitored
     * directory, then the ContextXmlDir is examined to see if a foo.xml file
     * exists. If it does, then this deployer will not deploy the webapp and the
     * ContextProvider should be used to act on the foo.xml file.
     * 
     * @see ContextProvider
     * @param contextsDir
     */
    public void setContextXmlDir(String contextsDir)
    {
        setMonitoredDirName(contextsDir);
    }
    
    /**
     * @param tldBundles Comma separated list of bundles that contain tld jars
     * that should be setup on the jetty instances created here.
     */
    public void setTldBundles(String tldBundles)
    {
    	_tldBundles = tldBundles;
    }
    
    /**
     * @return The list of bundles that contain tld jars that should be setup
     * on the jetty instances created here.
     */
    public String getTldBundles()
    {
    	return _tldBundles;
    }
    
    /**
     * @param configurations The configuration class names.
     */
    public void setConfigurationClasses(String[] configurations)
    {
        _configurationClasses = configurations==null?null:(String[])configurations.clone();
    }  
    
    /* ------------------------------------------------------------ */
    /**
     * 
     */
    public String[] getConfigurationClasses()
    {
        return _configurationClasses;
    }


}
