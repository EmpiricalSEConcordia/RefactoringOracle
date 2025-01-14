//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.util.thread.QueuedThreadPool;


/**
 * <p>
 *  This goal is used to deploy your unassembled webapp into a forked JVM.
 *  </p>
 *  <p>
 *  You need to define a jetty.xml file to configure connectors etc. You can use the normal setters of o.e.j.webapp.WebAppContext on the <b>webApp</b>
 *  configuration element for this plugin. You may also need context xml file for any particularly complex webapp setup.
 *  about your webapp.
 *  </p>
 *  <p>
 *  Unlike the other jetty goals, this does NOT support the <b>scanIntervalSeconds</b> parameter: the webapp will be deployed only once.
 *  </p>
 *  <p>
 *  The <b>stopKey</b>, <b>stopPort</b> configuration elements can be used to control the stopping of the forked process. By default, this plugin will launch
 *  the forked jetty instance and wait for it to complete (in which case it acts much like the <b>jetty:run</b> goal, and you will need to Cntrl-C to stop).
 *  By setting the configuration element <b>waitForChild</b> to <b>false</b>, the plugin will terminate after having forked the jetty process. In this case
 *  you can use the <b>jetty:stop</b> goal to terminate the process.
 *  <p>
 *  See <a href="http://www.eclipse.org/jetty/documentation/">http://www.eclipse.org/jetty/documentation</a> for more information on this and other jetty plugins.
 *  </p>
 * 
 * @goal run-forked
 * @requiresDependencyResolution test
 * @execute phase="test-compile"
 * @description Runs Jetty in forked JVM on an unassembled webapp
 *
 */
public class JettyRunForkedMojo extends JettyRunMojo
{    
    public static final String DEFAULT_WEBAPP_SRC = "src"+File.separator+"main"+File.separator+"webapp";
    public static final String FAKE_WEBAPP = "webapp-tmp";
    
    
    public String PORT_SYSPROPERTY = "jetty.port";

    
 
    
    
    
    /**
     * The target directory
     * 
     * @parameter expression="${project.build.directory}"
     * @required
     * @readonly
     */
    protected File target;
    
    
    /**
     * Arbitrary jvm args to pass to the forked process
     * @parameter
     */
    private String jvmArgs;
    
    
    /**
     * @parameter expression="${plugin.artifacts}"
     * @readonly
     */
    private List pluginArtifacts;
    
    
    /**
     * @parameter expression="${plugin}"
     * @readonly
     */
    private PluginDescriptor plugin;
    
    
    /**
     * @parameter expression="true" default-value="true"
     */
    private boolean waitForChild;

    /**
     * @parameter default-value="50"
     */
    private int maxStartupLines;

    /**
     * The forked jetty instance
     */
    private Process forkedProcess;
    
    
    /**
     * Random number generator
     */
    private Random random;    
    
 
    
    private Resource originalBaseResource;
    
    
    /**
     * ShutdownThread
     *
     *
     */
    public class ShutdownThread extends Thread
    {
        public ShutdownThread()
        {
            super("RunForkedShutdown");
        }
        
        public void run ()
        {
            if (forkedProcess != null && waitForChild)
            {
                forkedProcess.destroy();
            }
        }
    }
    

    
    
    /**
     * ConsoleStreamer
     * 
     * Simple streamer for the console output from a Process
     */
    private static class ConsoleStreamer implements Runnable
    {
        private String mode;
        private BufferedReader reader;

        public ConsoleStreamer(String mode, InputStream is)
        {
            this.mode = mode;
            this.reader = new BufferedReader(new InputStreamReader(is));
        }


        public void run()
        {
            String line;
            try
            {
                while ((line = reader.readLine()) != (null))
                {
                    System.out.println("[" + mode + "] " + line);
                }
            }
            catch (IOException ignore)
            {
                /* ignore */
            }
            finally
            {
                IO.close(reader);
            }
        }
    }
    
    
    
    
    
    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        Runtime.getRuntime().addShutdownHook(new ShutdownThread());
        random = new Random();
        super.execute();
    }
    
    


    @Override
    public void startJetty() throws MojoExecutionException
    {
        //Only do enough setup to be able to produce a quickstart-web.xml file to
        //pass onto the forked process to run      
        try
        {
            printSystemProperties();

            //apply any config from a jetty.xml file first to our "fake" server instance
            //TODO probably not necessary
            applyJettyXml ();  

        
            server.configureHandlers();
                   
            //ensure config of the webapp based on settings in plugin
            configureWebApplication();
            
            //copy the base resource as configured by the plugin
            originalBaseResource = webApp.getBaseResource();
            
            //set the webapp up to do very little other than generate the quickstart-web.xml
            webApp.setCopyWebDir(false);
            webApp.setCopyWebInf(false);
            webApp.setGenerateQuickStart(true);
            webApp.setQuickStartDir(target);
            
            server.addWebApplication(webApp);
                       
            //if our server has a thread pool associated we can do any annotation scanning multithreaded,
            //otherwise scanning will be single threaded
            QueuedThreadPool tpool = server.getBean(QueuedThreadPool.class);
            if (tpool != null)
                tpool.start();
            else
                webApp.setAttribute(AnnotationConfiguration.MULTI_THREADED, Boolean.FALSE.toString());
            
             webApp.start(); //just enough to generate the quickstart           
           
            //save config of the webapp BEFORE we stop
            File props = prepareConfiguration();
            
            webApp.stop();
            
            if (tpool != null)
                tpool.stop();
            
            List<String> cmd = new ArrayList<String>();
            cmd.add(getJavaBin());
            
            if (jvmArgs != null)
            {
                String[] args = jvmArgs.split(" ");
                for (int i=0;args != null && i<args.length;i++)
                {
                    if (args[i] !=null && !"".equals(args[i]))
                        cmd.add(args[i].trim());
                }
            }
            
            String classPath = getContainerClassPath();
            if (classPath != null && classPath.length() > 0)
            {
                cmd.add("-cp");
                cmd.add(classPath);
            }
            cmd.add(Starter.class.getCanonicalName());
            
            if (stopPort > 0 && stopKey != null)
            {
                cmd.add("--stop-port");
                cmd.add(Integer.toString(stopPort));
                cmd.add("--stop-key");
                cmd.add(stopKey);
            }
            if (jettyXml != null)
            {
                cmd.add("--jetty-xml");
                cmd.add(jettyXml);
            }
        
            if (contextXml != null)
            {
                cmd.add("--context-xml");
                cmd.add(contextXml);
            }
            
            cmd.add("--props");
            cmd.add(props.getAbsolutePath());
            
            String token = createToken();
            cmd.add("--token");
            cmd.add(token);
            
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.directory(project.getBasedir());
            
            if (PluginLog.getLog().isDebugEnabled())
                PluginLog.getLog().debug(Arrays.toString(cmd.toArray()));
            
            PluginLog.getLog().info("Forked process starting");

            if (waitForChild)
            {
                forkedProcess = builder.start();
                startPump("STDOUT",forkedProcess.getInputStream());
                startPump("STDERR",forkedProcess.getErrorStream());
                int exitcode = forkedProcess.waitFor();            
                PluginLog.getLog().info("Forked execution exit: "+exitcode);
            }
            else
            {   //merge stderr and stdout from child
                builder.redirectErrorStream(true);
                forkedProcess = builder.start();

                //wait for the child to be ready before terminating.
                //child indicates it has finished starting by printing on stdout the token passed to it
                try
                {
                    String line = "";
                    try (InputStream is = forkedProcess.getInputStream();
                            LineNumberReader reader = new LineNumberReader(new InputStreamReader(is)))
                    {
                        int attempts = maxStartupLines; //max lines we'll read trying to get token
                        while (attempts>0 && line != null)
                        {
                            --attempts;
                            line = reader.readLine();
                            if (line != null && line.startsWith(token))
                                break;
                        }

                    }

                    if (line != null && line.trim().equals(token))
                        PluginLog.getLog().info("Forked process started.");
                    else
                    {
                        String err = (line == null?"":(line.startsWith(token)?line.substring(token.length()):line));
                        PluginLog.getLog().info("Forked process startup errors"+(!"".equals(err)?", received: "+err:""));
                    }
                }
                catch (Exception e)
                {
                    throw new MojoExecutionException ("Problem determining if forked process is ready: "+e.getMessage());
                }

            }
        }
        catch (InterruptedException ex)
        {
            if (forkedProcess != null && waitForChild)
                forkedProcess.destroy();
            
            throw new MojoExecutionException("Failed to start Jetty within time limit");
        }
        catch (Exception ex)
        {
            if (forkedProcess != null && waitForChild)
                forkedProcess.destroy();
            
            throw new MojoExecutionException("Failed to create Jetty process", ex);
        }
    }




    /**
     * @return
     * @throws MojoExecutionException
     */
    public List<String> getProvidedJars() throws MojoExecutionException
    {  
        //if we are configured to include the provided dependencies on the plugin's classpath
        //(which mimics being on jetty's classpath vs being on the webapp's classpath), we first
        //try and filter out ones that will clash with jars that are plugin dependencies, then
        //create a new classloader that we setup in the parent chain.
        if (useProvidedScope)
        {
            
                List<String> provided = new ArrayList<String>();        
                for ( Iterator<Artifact> iter = project.getArtifacts().iterator(); iter.hasNext(); )
                {                   
                    Artifact artifact = iter.next();
                    if (Artifact.SCOPE_PROVIDED.equals(artifact.getScope()) && !isPluginArtifact(artifact))
                    {
                        provided.add(artifact.getFile().getAbsolutePath());
                        if (getLog().isDebugEnabled()) { getLog().debug("Adding provided artifact: "+artifact);}
                    }
                }
                return provided;

        }
        else
            return null;
    }
    
   
    
    
    /**
     * @return
     * @throws MojoExecutionException
     */
    public File prepareConfiguration() throws MojoExecutionException
    {
        try
        {   
            //work out the configuration based on what is configured in the pom
            File propsFile = new File (target, "fork.props");
            if (propsFile.exists())
                propsFile.delete();   

            propsFile.createNewFile();
            //propsFile.deleteOnExit();

            Properties props = new Properties();


            //web.xml
            if (webApp.getDescriptor() != null)
            {
                props.put("web.xml", webApp.getDescriptor());
            }
            
            if (webApp.getQuickStartWebDescriptor() != null)
            {
                props.put("quickstart.web.xml", webApp.getQuickStartWebDescriptor().getFile().getAbsolutePath());
            }

            //sort out the context path
            if (webApp.getContextPath() != null)
            {
                props.put("context.path", webApp.getContextPath());       
            }

            //tmp dir
            props.put("tmp.dir", webApp.getTempDirectory().getAbsolutePath());
            props.put("tmp.dir.persist", Boolean.toString(webApp.isPersistTempDirectory()));
            
            //resource bases - these are what has been configured BEFORE the webapp started and 
            //potentially reordered them and included any resources from META-INF
            if (originalBaseResource != null)
            {
                StringBuffer rb = new StringBuffer();
                if (originalBaseResource instanceof ResourceCollection)
                {
                    ResourceCollection resources = ((ResourceCollection)originalBaseResource);
                    for (Resource r:resources.getResources())
                    {
                        if (rb.length() > 0) rb.append(",");
                        rb.append(r.toString());
                    }        
                }
                else  
                    rb.append(originalBaseResource.toString());
                
               props.put("base.dirs", rb.toString());                    
            }

            //sort out the resource base directories of the webapp
            props.put("base.first", Boolean.toString(webApp.getBaseAppFirst()));

            //web-inf classes
            if (webApp.getClasses() != null)
            {
                props.put("classes.dir",webApp.getClasses().getAbsolutePath());
            }
            
            if (useTestScope && webApp.getTestClasses() != null)
            {
                props.put("testClasses.dir", webApp.getTestClasses().getAbsolutePath());
            }

            //web-inf lib
            List<File> deps = webApp.getWebInfLib();
            StringBuffer strbuff = new StringBuffer();
            for (int i=0; i<deps.size(); i++)
            {
                File d = deps.get(i);
                strbuff.append(d.getAbsolutePath());
                if (i < deps.size()-1)
                    strbuff.append(",");
            }
            props.put("lib.jars", strbuff.toString());

            //any war files
            List<Artifact> warArtifacts = getWarArtifacts(); 
            for (int i=0; i<warArtifacts.size(); i++)
            {
                strbuff.setLength(0);           
                Artifact a  = warArtifacts.get(i);
                strbuff.append(a.getGroupId()+",");
                strbuff.append(a.getArtifactId()+",");
                strbuff.append(a.getFile().getAbsolutePath());
                props.put("maven.war.artifact."+i, strbuff.toString());
            }
          
            
            //any overlay configuration
            WarPluginInfo warPlugin = new WarPluginInfo(project);
            
            //add in the war plugins default includes and excludes
            props.put("maven.war.includes", toCSV(warPlugin.getDependentMavenWarIncludes()));
            props.put("maven.war.excludes", toCSV(warPlugin.getDependentMavenWarExcludes()));
            
            
            List<OverlayConfig> configs = warPlugin.getMavenWarOverlayConfigs();
            int i=0;
            for (OverlayConfig c:configs)
            {
                props.put("maven.war.overlay."+(i++), c.toString());
            }
            
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(propsFile)))
            {
                props.store(out, "properties for forked webapp");
            }
            return propsFile;
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Prepare webapp configuration", e);
        }
    }
    

    
    
  
    
    /**
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    private List<Artifact> getWarArtifacts()
    throws MalformedURLException, IOException
    {
        List<Artifact> warArtifacts = new ArrayList<Artifact>();
        for ( Iterator<Artifact> iter = project.getArtifacts().iterator(); iter.hasNext(); )
        {
            Artifact artifact = (Artifact) iter.next();  
            
            if (artifact.getType().equals("war"))
                warArtifacts.add(artifact);
        }

        return warArtifacts;
    }
    
    
    
    
    
    
    /**
     * @param artifact
     * @return
     */
    public boolean isPluginArtifact(Artifact artifact)
    {
        if (pluginArtifacts == null || pluginArtifacts.isEmpty())
            return false;
        
        boolean isPluginArtifact = false;
        for (Iterator<Artifact> iter = pluginArtifacts.iterator(); iter.hasNext() && !isPluginArtifact; )
        {
            Artifact pluginArtifact = iter.next();
            if (getLog().isDebugEnabled()) { getLog().debug("Checking "+pluginArtifact);}
            if (pluginArtifact.getGroupId().equals(artifact.getGroupId()) && pluginArtifact.getArtifactId().equals(artifact.getArtifactId()))
                isPluginArtifact = true;
        }
        
        return isPluginArtifact;
    }
    
    
    
    
    /**
     * @return
     * @throws Exception
     */
    private Set<Artifact> getExtraJars()
    throws Exception
    {
        Set<Artifact> extraJars = new HashSet<Artifact>();
  
        
        List l = pluginArtifacts;
        Artifact pluginArtifact = null;

        if (l != null)
        {
            Iterator itor = l.iterator();
            while (itor.hasNext() && pluginArtifact == null)
            {              
                Artifact a = (Artifact)itor.next();
                if (a.getArtifactId().equals(plugin.getArtifactId())) //get the jetty-maven-plugin jar
                {
                    extraJars.add(a);
                }
            }
        }

        return extraJars;
    }

    

   

    
    /**
     * @return
     * @throws Exception
     */
    public String getContainerClassPath() throws Exception
    {
        StringBuilder classPath = new StringBuilder();
        for (Object obj : pluginArtifacts)
        {
            Artifact artifact = (Artifact) obj;
            if ("jar".equals(artifact.getType()))
            {
                if (classPath.length() > 0)
                {
                    classPath.append(File.pathSeparator);
                }
                classPath.append(artifact.getFile().getAbsolutePath());

            }
        }
        
        //Any jars that we need from the plugin environment (like the ones containing Starter class)
        Set<Artifact> extraJars = getExtraJars();
        for (Artifact a:extraJars)
        { 
            classPath.append(File.pathSeparator);
            classPath.append(a.getFile().getAbsolutePath());
        }
        
        
        //Any jars that we need from the project's dependencies because we're useProvided
        List<String> providedJars = getProvidedJars();
        if (providedJars != null && !providedJars.isEmpty())
        {
            for (String jar:providedJars)
            {
                classPath.append(File.pathSeparator);
                classPath.append(jar);
                if (getLog().isDebugEnabled()) getLog().debug("Adding provided jar: "+jar);
            }
        }

        return classPath.toString();
    }

    

    
    /**
     * @return
     */
    private String getJavaBin()
    {
        String javaexes[] = new String[]
        { "java", "java.exe" };

        File javaHomeDir = new File(System.getProperty("java.home"));
        for (String javaexe : javaexes)
        {
            File javabin = new File(javaHomeDir,fileSeparators("bin/" + javaexe));
            if (javabin.exists() && javabin.isFile())
            {
                return javabin.getAbsolutePath();
            }
        }

        return "java";
    }
    

    
    
    /**
     * @param path
     * @return
     */
    public static String fileSeparators(String path)
    {
        StringBuilder ret = new StringBuilder();
        for (char c : path.toCharArray())
        {
            if ((c == '/') || (c == '\\'))
            {
                ret.append(File.separatorChar);
            }
            else
            {
                ret.append(c);
            }
        }
        return ret.toString();
    }


    
    
    /**
     * @param path
     * @return
     */
    public static String pathSeparators(String path)
    {
        StringBuilder ret = new StringBuilder();
        for (char c : path.toCharArray())
        {
            if ((c == ',') || (c == ':'))
            {
                ret.append(File.pathSeparatorChar);
            }
            else
            {
                ret.append(c);
            }
        }
        return ret.toString();
    }


    
    
    /**
     * @return
     */
    private String createToken ()
    {
        return Long.toString(random.nextLong()^System.currentTimeMillis(), 36).toUpperCase(Locale.ENGLISH);
    }
    

    
    
    /**
     * @param mode
     * @param inputStream
     */
    private void startPump(String mode, InputStream inputStream)
    {
        ConsoleStreamer pump = new ConsoleStreamer(mode,inputStream);
        Thread thread = new Thread(pump,"ConsoleStreamer/" + mode);
        thread.setDaemon(true);
        thread.start();
    }


    
    
    /**
     * @param strings
     * @return
     */
    private String toCSV (List<String> strings)
    {
        if (strings == null)
            return "";
        StringBuffer strbuff = new StringBuffer();
        Iterator<String> itor = strings.iterator();
        while (itor.hasNext())
        {
            strbuff.append(itor.next());
            if (itor.hasNext())
                strbuff.append(",");
        }
        return strbuff.toString();
    }
}
