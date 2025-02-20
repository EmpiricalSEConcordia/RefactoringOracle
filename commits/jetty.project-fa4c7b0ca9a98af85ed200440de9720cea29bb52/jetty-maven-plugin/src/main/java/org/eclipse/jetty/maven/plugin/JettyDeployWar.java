//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * <p>
 * This goal is used to run Jetty with a pre-assembled war.
 * </p>
 * <p>
 * It accepts exactly the same options as the <a href="run-war-mojo.html">run-war</a> goal. 
 * However, it doesn't assume that the current artifact is a
 * webapp and doesn't try to assemble it into a war before its execution. 
 * So using it makes sense only when used in conjunction with the 
 * <a href="run-war-mojo.html#webApp">war</a> configuration parameter pointing to a pre-built WAR.
 * </p>
 * <p>
 * This goal is useful e.g. for launching a web app in Jetty as a target for unit-tested 
 * HTTP client components.
 * </p>
 * 
 * @goal deploy-war
 * @requiresDependencyResolution runtime
 * @execute phase="validate"
 * @description Deploy a pre-assembled war
 * 
 */
public class JettyDeployWar extends JettyRunWarMojo
{

    
    /**
     * If true, the plugin should continue and not block. Otherwise the
     * plugin will block further execution and you will need to use
     * cntrl-c to stop it.
     * 
     * 
     * @parameter  default-value="true"
     */
    protected boolean daemon = true;
    
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        nonblocking = daemon; 
        super.execute();
    }
    


    @Override
    public void finishConfigurationBeforeStart() throws Exception
    {
        super.finishConfigurationBeforeStart();
        //only stop the server at shutdown if we are blocking
        server.setStopAtShutdown(!nonblocking); 
    }

}
