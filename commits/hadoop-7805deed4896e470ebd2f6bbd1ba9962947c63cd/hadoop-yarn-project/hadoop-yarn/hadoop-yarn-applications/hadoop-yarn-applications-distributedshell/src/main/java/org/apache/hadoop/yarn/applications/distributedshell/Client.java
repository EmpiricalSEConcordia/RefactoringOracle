/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.applications.distributedshell;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.yarn.api.ApplicationClientProtocol;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationResponse;
import org.apache.hadoop.yarn.api.protocolrecords.KillApplicationRequest;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.NodeState;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.ProfileCapability;
import org.apache.hadoop.yarn.api.records.QueueACL;
import org.apache.hadoop.yarn.api.records.QueueInfo;
import org.apache.hadoop.yarn.api.records.QueueUserACLInfo;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.ResourceRequest;
import org.apache.hadoop.yarn.api.records.URL;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.api.records.YarnClusterMetrics;
import org.apache.hadoop.yarn.api.records.timeline.TimelineDomain;
import org.apache.hadoop.yarn.client.api.TimelineClient;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.client.util.YarnClientUtils;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.ResourceProfilesNotEnabledException;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.resource.Resources;
import org.apache.hadoop.yarn.util.timeline.TimelineUtils;

/**
 * Client for Distributed Shell application submission to YARN.
 * 
 * <p> The distributed shell client allows an application master to be launched that in turn would run 
 * the provided shell command on a set of containers. </p>
 * 
 * <p>This client is meant to act as an example on how to write yarn-based applications. </p>
 * 
 * <p> To submit an application, a client first needs to connect to the <code>ResourceManager</code> 
 * aka ApplicationsManager or ASM via the {@link ApplicationClientProtocol}. The {@link ApplicationClientProtocol} 
 * provides a way for the client to get access to cluster information and to request for a
 * new {@link ApplicationId}. <p>
 * 
 * <p> For the actual job submission, the client first has to create an {@link ApplicationSubmissionContext}. 
 * The {@link ApplicationSubmissionContext} defines the application details such as {@link ApplicationId} 
 * and application name, the priority assigned to the application and the queue
 * to which this application needs to be assigned. In addition to this, the {@link ApplicationSubmissionContext}
 * also defines the {@link ContainerLaunchContext} which describes the <code>Container</code> with which 
 * the {@link ApplicationMaster} is launched. </p>
 * 
 * <p> The {@link ContainerLaunchContext} in this scenario defines the resources to be allocated for the 
 * {@link ApplicationMaster}'s container, the local resources (jars, configuration files) to be made available 
 * and the environment to be set for the {@link ApplicationMaster} and the commands to be executed to run the 
 * {@link ApplicationMaster}. <p>
 * 
 * <p> Using the {@link ApplicationSubmissionContext}, the client submits the application to the 
 * <code>ResourceManager</code> and then monitors the application by requesting the <code>ResourceManager</code> 
 * for an {@link ApplicationReport} at regular time intervals. In case of the application taking too long, the client 
 * kills the application by submitting a {@link KillApplicationRequest} to the <code>ResourceManager</code>. </p>
 *
 */
@InterfaceAudience.Public
@InterfaceStability.Unstable
public class Client {

  private static final Log LOG = LogFactory.getLog(Client.class);

  private static final int DEFAULT_AM_MEMORY = 100;
  private static final int DEFAULT_AM_VCORES = 1;
  private static final int DEFAULT_CONTAINER_MEMORY = 10;
  private static final int DEFAULT_CONTAINER_VCORES = 1;
  
  // Configuration
  private Configuration conf;
  private YarnClient yarnClient;
  // Application master specific info to register a new Application with RM/ASM
  private String appName = "";
  // App master priority
  private int amPriority = 0;
  // Queue for App master
  private String amQueue = "";
  // Amt. of memory resource to request for to run the App Master
  private long amMemory = DEFAULT_AM_MEMORY;
  // Amt. of virtual core resource to request for to run the App Master
  private int amVCores = DEFAULT_AM_VCORES;

  // AM resource profile
  private String amResourceProfile = "";

  // Application master jar file
  private String appMasterJar = ""; 
  // Main class to invoke application master
  private final String appMasterMainClass;

  // Shell command to be executed 
  private String shellCommand = ""; 
  // Location of shell script 
  private String shellScriptPath = ""; 
  // Args to be passed to the shell command
  private String[] shellArgs = new String[] {};
  // Env variables to be setup for the shell command 
  private Map<String, String> shellEnv = new HashMap<String, String>();
  // Shell Command Container priority 
  private int shellCmdPriority = 0;

  // Amt of memory to request for container in which shell script will be executed
  private long containerMemory = DEFAULT_CONTAINER_MEMORY;
  // Amt. of virtual cores to request for container in which shell script will be executed
  private int containerVirtualCores = DEFAULT_CONTAINER_VCORES;
  // container resource profile
  private String containerResourceProfile = "";
  // No. of containers in which the shell script needs to be executed
  private int numContainers = 1;
  private String nodeLabelExpression = null;

  // log4j.properties file 
  // if available, add to local resources and set into classpath 
  private String log4jPropFile = "";	

  // Start time for client
  private final long clientStartTime = System.currentTimeMillis();
  // Timeout threshold for client. Kill app after time interval expires.
  private long clientTimeout = 600000;

  // flag to indicate whether to keep containers across application attempts.
  private boolean keepContainers = false;

  private long attemptFailuresValidityInterval = -1;

  private Vector<CharSequence> containerRetryOptions = new Vector<>(5);

  // Debug flag
  boolean debugFlag = false;

  // Timeline domain ID
  private String domainId = null;

  // Flag to indicate whether to create the domain of the given ID
  private boolean toCreateDomain = false;

  // Timeline domain reader access control
  private String viewACLs = null;

  // Timeline domain writer access control
  private String modifyACLs = null;

  private String flowName = null;
  private String flowVersion = null;
  private long flowRunId = 0L;

  // Command line options
  private Options opts;

  private static final String shellCommandPath = "shellCommands";
  private static final String shellArgsPath = "shellArgs";
  private static final String appMasterJarPath = "AppMaster.jar";
  // Hardcoded path to custom log_properties
  private static final String log4jPath = "log4j.properties";

  public static final String SCRIPT_PATH = "ExecScript";

  /**
   * @param args Command line arguments 
   */
  public static void main(String[] args) {
    boolean result = false;
    try {
      Client client = new Client();
      LOG.info("Initializing Client");
      try {
        boolean doRun = client.init(args);
        if (!doRun) {
          System.exit(0);
        }
      } catch (IllegalArgumentException e) {
        System.err.println(e.getLocalizedMessage());
        client.printUsage();
        System.exit(-1);
      }
      result = client.run();
    } catch (Throwable t) {
      LOG.fatal("Error running Client", t);
      System.exit(1);
    }
    if (result) {
      LOG.info("Application completed successfully");
      System.exit(0);			
    } 
    LOG.error("Application failed to complete successfully");
    System.exit(2);
  }

  /**
   */
  public Client(Configuration conf) throws Exception  {
    this(
      "org.apache.hadoop.yarn.applications.distributedshell.ApplicationMaster",
      conf);
  }

  Client(String appMasterMainClass, Configuration conf) {
    this.conf = conf;
    this.appMasterMainClass = appMasterMainClass;
    yarnClient = YarnClient.createYarnClient();
    yarnClient.init(conf);
    opts = new Options();
    opts.addOption("appname", true, "Application Name. Default value - DistributedShell");
    opts.addOption("priority", true, "Application Priority. Default 0");
    opts.addOption("queue", true, "RM Queue in which this application is to be submitted");
    opts.addOption("timeout", true, "Application timeout in milliseconds");
    opts.addOption("master_memory", true, "Amount of memory in MB to be requested to run the application master");
    opts.addOption("master_vcores", true, "Amount of virtual cores to be requested to run the application master");
    opts.addOption("jar", true, "Jar file containing the application master");
    opts.addOption("master_resource_profile", true, "Resource profile for the application master");
    opts.addOption("shell_command", true, "Shell command to be executed by " +
        "the Application Master. Can only specify either --shell_command " +
        "or --shell_script");
    opts.addOption("shell_script", true, "Location of the shell script to be " +
        "executed. Can only specify either --shell_command or --shell_script");
    opts.addOption("shell_args", true, "Command line args for the shell script." +
        "Multiple args can be separated by empty space.");
    opts.getOption("shell_args").setArgs(Option.UNLIMITED_VALUES);
    opts.addOption("shell_env", true,
        "Environment for shell script. Specified as env_key=env_val pairs");
    opts.addOption("shell_cmd_priority", true, "Priority for the shell command containers");
    opts.addOption("container_memory", true, "Amount of memory in MB to be requested to run the shell command");
    opts.addOption("container_vcores", true, "Amount of virtual cores to be requested to run the shell command");
    opts.addOption("container_resource_profile", true, "Resource profile for the shell command");
    opts.addOption("num_containers", true, "No. of containers on which the shell command needs to be executed");
    opts.addOption("log_properties", true, "log4j.properties file");
    opts.addOption("keep_containers_across_application_attempts", false,
      "Flag to indicate whether to keep containers across application attempts." +
      " If the flag is true, running containers will not be killed when" +
      " application attempt fails and these containers will be retrieved by" +
      " the new application attempt ");
    opts.addOption("attempt_failures_validity_interval", true,
      "when attempt_failures_validity_interval in milliseconds is set to > 0," +
      "the failure number will not take failures which happen out of " +
      "the validityInterval into failure count. " +
      "If failure count reaches to maxAppAttempts, " +
      "the application will be failed.");
    opts.addOption("debug", false, "Dump out debug information");
    opts.addOption("domain", true, "ID of the timeline domain where the "
        + "timeline entities will be put");
    opts.addOption("view_acls", true, "Users and groups that allowed to "
        + "view the timeline entities in the given domain");
    opts.addOption("modify_acls", true, "Users and groups that allowed to "
        + "modify the timeline entities in the given domain");
    opts.addOption("create", false, "Flag to indicate whether to create the "
        + "domain specified with -domain.");
    opts.addOption("flow_name", true, "Flow name which the distributed shell "
        + "app belongs to");
    opts.addOption("flow_version", true, "Flow version which the distributed "
        + "shell app belongs to");
    opts.addOption("flow_run_id", true, "Flow run ID which the distributed "
        + "shell app belongs to");
    opts.addOption("help", false, "Print usage");
    opts.addOption("node_label_expression", true,
        "Node label expression to determine the nodes"
            + " where all the containers of this application"
            + " will be allocated, \"\" means containers"
            + " can be allocated anywhere, if you don't specify the option,"
            + " default node_label_expression of queue will be used.");
    opts.addOption("container_retry_policy", true,
        "Retry policy when container fails to run, "
            + "0: NEVER_RETRY, 1: RETRY_ON_ALL_ERRORS, "
            + "2: RETRY_ON_SPECIFIC_ERROR_CODES");
    opts.addOption("container_retry_error_codes", true,
        "When retry policy is set to RETRY_ON_SPECIFIC_ERROR_CODES, error "
            + "codes is specified with this option, "
            + "e.g. --container_retry_error_codes 1,2,3");
    opts.addOption("container_max_retries", true,
        "If container could retry, it specifies max retires");
    opts.addOption("container_retry_interval", true,
        "Interval between each retry, unit is milliseconds");
  }

  /**
   */
  public Client() throws Exception  {
    this(new YarnConfiguration());
  }

  /**
   * Helper function to print out usage
   */
  private void printUsage() {
    new HelpFormatter().printHelp("Client", opts);
  }

  /**
   * Parse command line options
   * @param args Parsed command line options 
   * @return Whether the init was successful to run the client
   * @throws ParseException
   */
  public boolean init(String[] args) throws ParseException {

    CommandLine cliParser = new GnuParser().parse(opts, args);

    if (args.length == 0) {
      throw new IllegalArgumentException("No args specified for client to initialize");
    }

    if (cliParser.hasOption("log_properties")) {
      String log4jPath = cliParser.getOptionValue("log_properties");
      try {
        Log4jPropertyHelper.updateLog4jConfiguration(Client.class, log4jPath);
      } catch (Exception e) {
        LOG.warn("Can not set up custom log4j properties. " + e);
      }
    }

    if (cliParser.hasOption("help")) {
      printUsage();
      return false;
    }

    if (cliParser.hasOption("debug")) {
      debugFlag = true;

    }

    if (cliParser.hasOption("keep_containers_across_application_attempts")) {
      LOG.info("keep_containers_across_application_attempts");
      keepContainers = true;
    }

    appName = cliParser.getOptionValue("appname", "DistributedShell");
    amPriority = Integer.parseInt(cliParser.getOptionValue("priority", "0"));
    amQueue = cliParser.getOptionValue("queue", "default");
    amMemory =
        Integer.parseInt(cliParser.getOptionValue("master_memory", "-1"));
    amVCores =
        Integer.parseInt(cliParser.getOptionValue("master_vcores", "-1"));
    amResourceProfile = cliParser.getOptionValue("master_resource_profile", "");

    if (!cliParser.hasOption("jar")) {
      throw new IllegalArgumentException("No jar file specified for application master");
    }		

    appMasterJar = cliParser.getOptionValue("jar");

    if (!cliParser.hasOption("shell_command") && !cliParser.hasOption("shell_script")) {
      throw new IllegalArgumentException(
          "No shell command or shell script specified to be executed by application master");
    } else if (cliParser.hasOption("shell_command") && cliParser.hasOption("shell_script")) {
      throw new IllegalArgumentException("Can not specify shell_command option " +
          "and shell_script option at the same time");
    } else if (cliParser.hasOption("shell_command")) {
      shellCommand = cliParser.getOptionValue("shell_command");
    } else {
      shellScriptPath = cliParser.getOptionValue("shell_script");
    }
    if (cliParser.hasOption("shell_args")) {
      shellArgs = cliParser.getOptionValues("shell_args");
    }
    if (cliParser.hasOption("shell_env")) { 
      String envs[] = cliParser.getOptionValues("shell_env");
      for (String env : envs) {
        env = env.trim();
        int index = env.indexOf('=');
        if (index == -1) {
          shellEnv.put(env, "");
          continue;
        }
        String key = env.substring(0, index);
        String val = "";
        if (index < (env.length()-1)) {
          val = env.substring(index+1);
        }
        shellEnv.put(key, val);
      }
    }
    shellCmdPriority = Integer.parseInt(cliParser.getOptionValue("shell_cmd_priority", "0"));

    containerMemory =
        Integer.parseInt(cliParser.getOptionValue("container_memory", "-1"));
    containerVirtualCores =
        Integer.parseInt(cliParser.getOptionValue("container_vcores", "-1"));
    containerResourceProfile =
        cliParser.getOptionValue("container_resource_profile", "");
    numContainers =
        Integer.parseInt(cliParser.getOptionValue("num_containers", "1"));

    if (numContainers < 1) {
      throw new IllegalArgumentException("Invalid no. of containers specified,"
          + " exiting. Specified numContainer=" + numContainers);
    }
    
    nodeLabelExpression = cliParser.getOptionValue("node_label_expression", null);

    clientTimeout = Integer.parseInt(cliParser.getOptionValue("timeout", "600000"));

    attemptFailuresValidityInterval =
        Long.parseLong(cliParser.getOptionValue(
          "attempt_failures_validity_interval", "-1"));

    log4jPropFile = cliParser.getOptionValue("log_properties", "");

    // Get timeline domain options
    if (cliParser.hasOption("domain")) {
      domainId = cliParser.getOptionValue("domain");
      toCreateDomain = cliParser.hasOption("create");
      if (cliParser.hasOption("view_acls")) {
        viewACLs = cliParser.getOptionValue("view_acls");
      }
      if (cliParser.hasOption("modify_acls")) {
        modifyACLs = cliParser.getOptionValue("modify_acls");
      }
    }

    // Get container retry options
    if (cliParser.hasOption("container_retry_policy")) {
      containerRetryOptions.add("--container_retry_policy "
          + cliParser.getOptionValue("container_retry_policy"));
    }
    if (cliParser.hasOption("container_retry_error_codes")) {
      containerRetryOptions.add("--container_retry_error_codes "
          + cliParser.getOptionValue("container_retry_error_codes"));
    }
    if (cliParser.hasOption("container_max_retries")) {
      containerRetryOptions.add("--container_max_retries "
          + cliParser.getOptionValue("container_max_retries"));
    }
    if (cliParser.hasOption("container_retry_interval")) {
      containerRetryOptions.add("--container_retry_interval "
          + cliParser.getOptionValue("container_retry_interval"));
    }

    if (cliParser.hasOption("flow_name")) {
      flowName = cliParser.getOptionValue("flow_name");
    }
    if (cliParser.hasOption("flow_version")) {
      flowVersion = cliParser.getOptionValue("flow_version");
    }
    if (cliParser.hasOption("flow_run_id")) {
      try {
        flowRunId = Long.parseLong(cliParser.getOptionValue("flow_run_id"));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Flow run is not a valid long value", e);
      }
    }
    return true;
  }

  /**
   * Main run function for the client
   * @return true if application completed successfully
   * @throws IOException
   * @throws YarnException
   */
  public boolean run() throws IOException, YarnException {

    LOG.info("Running Client");
    yarnClient.start();

    YarnClusterMetrics clusterMetrics = yarnClient.getYarnClusterMetrics();
    LOG.info("Got Cluster metric info from ASM" 
        + ", numNodeManagers=" + clusterMetrics.getNumNodeManagers());

    List<NodeReport> clusterNodeReports = yarnClient.getNodeReports(
        NodeState.RUNNING);
    LOG.info("Got Cluster node info from ASM");
    for (NodeReport node : clusterNodeReports) {
      LOG.info("Got node report from ASM for"
          + ", nodeId=" + node.getNodeId() 
          + ", nodeAddress=" + node.getHttpAddress()
          + ", nodeRackName=" + node.getRackName()
          + ", nodeNumContainers=" + node.getNumContainers());
    }

    QueueInfo queueInfo = yarnClient.getQueueInfo(this.amQueue);
    LOG.info("Queue info"
        + ", queueName=" + queueInfo.getQueueName()
        + ", queueCurrentCapacity=" + queueInfo.getCurrentCapacity()
        + ", queueMaxCapacity=" + queueInfo.getMaximumCapacity()
        + ", queueApplicationCount=" + queueInfo.getApplications().size()
        + ", queueChildQueueCount=" + queueInfo.getChildQueues().size());		

    List<QueueUserACLInfo> listAclInfo = yarnClient.getQueueAclsInfo();
    for (QueueUserACLInfo aclInfo : listAclInfo) {
      for (QueueACL userAcl : aclInfo.getUserAcls()) {
        LOG.info("User ACL Info for Queue"
            + ", queueName=" + aclInfo.getQueueName()			
            + ", userAcl=" + userAcl.name());
      }
    }		

    if (domainId != null && domainId.length() > 0 && toCreateDomain) {
      prepareTimelineDomain();
    }

    Map<String, Resource> profiles;
    try {
      profiles = yarnClient.getResourceProfiles();
    } catch (ResourceProfilesNotEnabledException re) {
      profiles = null;
    }

    List<String> appProfiles = new ArrayList<>(2);
    appProfiles.add(amResourceProfile);
    appProfiles.add(containerResourceProfile);
    for (String appProfile : appProfiles) {
      if (appProfile != null && !appProfile.isEmpty()) {
        if (profiles == null) {
          String message = "Resource profiles is not enabled";
          LOG.error(message);
          throw new IOException(message);
        }
        if (!profiles.containsKey(appProfile)) {
          String message = "Unknown resource profile '" + appProfile
              + "'. Valid resource profiles are " + profiles.keySet();
          LOG.error(message);
          throw new IOException(message);
        }
      }
    }

    // Get a new application id
    YarnClientApplication app = yarnClient.createApplication();
    GetNewApplicationResponse appResponse = app.getNewApplicationResponse();
    // TODO get min/max resource capabilities from RM and change memory ask if needed
    // If we do not have min/max, we may not be able to correctly request 
    // the required resources from the RM for the app master
    // Memory ask has to be a multiple of min and less than max. 
    // Dump out information about cluster capability as seen by the resource manager
    long maxMem = appResponse.getMaximumResourceCapability().getMemorySize();
    LOG.info("Max mem capability of resources in this cluster " + maxMem);

    // A resource ask cannot exceed the max. 
    if (amMemory > maxMem) {
      LOG.info("AM memory specified above max threshold of cluster. Using max value."
          + ", specified=" + amMemory
          + ", max=" + maxMem);
      amMemory = maxMem;
    }				

    int maxVCores = appResponse.getMaximumResourceCapability().getVirtualCores();
    LOG.info("Max virtual cores capability of resources in this cluster " + maxVCores);
    
    if (amVCores > maxVCores) {
      LOG.info("AM virtual cores specified above max threshold of cluster. " 
          + "Using max value." + ", specified=" + amVCores 
          + ", max=" + maxVCores);
      amVCores = maxVCores;
    }
    
    // set the application name
    ApplicationSubmissionContext appContext = app.getApplicationSubmissionContext();
    ApplicationId appId = appContext.getApplicationId();

    // Set up resource type requirements
    // For now, both memory and vcores are supported, so we set memory and
    // vcores requirements
    setAMResourceCapability(appContext, amMemory, amVCores, amResourceProfile,
        amPriority, profiles);
    setContainerResources(containerMemory, containerVirtualCores, profiles);

    appContext.setKeepContainersAcrossApplicationAttempts(keepContainers);
    appContext.setApplicationName(appName);

    if (attemptFailuresValidityInterval >= 0) {
      appContext
        .setAttemptFailuresValidityInterval(attemptFailuresValidityInterval);
    }

    Set<String> tags = new HashSet<String>();
    if (flowName != null) {
      tags.add(TimelineUtils.generateFlowNameTag(flowName));
    }
    if (flowVersion != null) {
      tags.add(TimelineUtils.generateFlowVersionTag(flowVersion));
    }
    if (flowRunId != 0) {
      tags.add(TimelineUtils.generateFlowRunIdTag(flowRunId));
    }
    appContext.setApplicationTags(tags);

    // set local resources for the application master
    // local files or archives as needed
    // In this scenario, the jar file for the application master is part of the local resources			
    Map<String, LocalResource> localResources = new HashMap<String, LocalResource>();

    LOG.info("Copy App Master jar from local filesystem and add to local environment");
    // Copy the application master jar to the filesystem 
    // Create a local resource to point to the destination jar path 
    FileSystem fs = FileSystem.get(conf);
    addToLocalResources(fs, appMasterJar, appMasterJarPath, appId.toString(),
        localResources, null);

    // Set the log4j properties if needed 
    if (!log4jPropFile.isEmpty()) {
      addToLocalResources(fs, log4jPropFile, log4jPath, appId.toString(),
          localResources, null);
    }			

    // The shell script has to be made available on the final container(s)
    // where it will be executed. 
    // To do this, we need to first copy into the filesystem that is visible 
    // to the yarn framework. 
    // We do not need to set this as a local resource for the application 
    // master as the application master does not need it. 		
    String hdfsShellScriptLocation = ""; 
    long hdfsShellScriptLen = 0;
    long hdfsShellScriptTimestamp = 0;
    if (!shellScriptPath.isEmpty()) {
      Path shellSrc = new Path(shellScriptPath);
      String shellPathSuffix =
          appName + "/" + appId.toString() + "/" + SCRIPT_PATH;
      Path shellDst =
          new Path(fs.getHomeDirectory(), shellPathSuffix);
      fs.copyFromLocalFile(false, true, shellSrc, shellDst);
      hdfsShellScriptLocation = shellDst.toUri().toString(); 
      FileStatus shellFileStatus = fs.getFileStatus(shellDst);
      hdfsShellScriptLen = shellFileStatus.getLen();
      hdfsShellScriptTimestamp = shellFileStatus.getModificationTime();
    }

    if (!shellCommand.isEmpty()) {
      addToLocalResources(fs, null, shellCommandPath, appId.toString(),
          localResources, shellCommand);
    }

    if (shellArgs.length > 0) {
      addToLocalResources(fs, null, shellArgsPath, appId.toString(),
          localResources, StringUtils.join(shellArgs, " "));
    }

    // Set the necessary security tokens as needed
    //amContainer.setContainerTokens(containerToken);

    // Set the env variables to be setup in the env where the application master will be run
    LOG.info("Set the environment for the application master");
    Map<String, String> env = new HashMap<String, String>();

    // put location of shell script into env
    // using the env info, the application master will create the correct local resource for the 
    // eventual containers that will be launched to execute the shell scripts
    env.put(DSConstants.DISTRIBUTEDSHELLSCRIPTLOCATION, hdfsShellScriptLocation);
    env.put(DSConstants.DISTRIBUTEDSHELLSCRIPTTIMESTAMP, Long.toString(hdfsShellScriptTimestamp));
    env.put(DSConstants.DISTRIBUTEDSHELLSCRIPTLEN, Long.toString(hdfsShellScriptLen));
    if (domainId != null && domainId.length() > 0) {
      env.put(DSConstants.DISTRIBUTEDSHELLTIMELINEDOMAIN, domainId);
    }

    // Add AppMaster.jar location to classpath 		
    // At some point we should not be required to add 
    // the hadoop specific classpaths to the env. 
    // It should be provided out of the box. 
    // For now setting all required classpaths including
    // the classpath to "." for the application jar
    StringBuilder classPathEnv = new StringBuilder(Environment.CLASSPATH.$$())
      .append(ApplicationConstants.CLASS_PATH_SEPARATOR).append("./*");
    for (String c : conf.getStrings(
        YarnConfiguration.YARN_APPLICATION_CLASSPATH,
        YarnConfiguration.DEFAULT_YARN_CROSS_PLATFORM_APPLICATION_CLASSPATH)) {
      classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR);
      classPathEnv.append(c.trim());
    }
    classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR).append(
      "./log4j.properties");

    // add the runtime classpath needed for tests to work
    if (conf.getBoolean(YarnConfiguration.IS_MINI_YARN_CLUSTER, false)) {
      classPathEnv.append(':');
      classPathEnv.append(System.getProperty("java.class.path"));
    }

    env.put("CLASSPATH", classPathEnv.toString());

    // Set the necessary command to execute the application master 
    Vector<CharSequence> vargs = new Vector<CharSequence>(30);

    // Set java executable command 
    LOG.info("Setting up app master command");
    vargs.add(Environment.JAVA_HOME.$$() + "/bin/java");
    // Set Xmx based on am memory size
    vargs.add("-Xmx" + amMemory + "m");
    // Set class name 
    vargs.add(appMasterMainClass);
    // Set params for Application Master
    if (containerMemory > 0) {
      vargs.add("--container_memory " + String.valueOf(containerMemory));
    }
    if (containerVirtualCores > 0) {
      vargs.add("--container_vcores " + String.valueOf(containerVirtualCores));
    }
    if (containerResourceProfile != null && !containerResourceProfile
        .isEmpty()) {
      vargs.add("--container_resource_profile " + containerResourceProfile);
    }
    vargs.add("--num_containers " + String.valueOf(numContainers));
    if (null != nodeLabelExpression) {
      appContext.setNodeLabelExpression(nodeLabelExpression);
    }
    vargs.add("--priority " + String.valueOf(shellCmdPriority));

    for (Map.Entry<String, String> entry : shellEnv.entrySet()) {
      vargs.add("--shell_env " + entry.getKey() + "=" + entry.getValue());
    }
    if (debugFlag) {
      vargs.add("--debug");
    }

    vargs.addAll(containerRetryOptions);

    vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/AppMaster.stdout");
    vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/AppMaster.stderr");

    // Get final commmand
    StringBuilder command = new StringBuilder();
    for (CharSequence str : vargs) {
      command.append(str).append(" ");
    }

    LOG.info("Completed setting up app master command " + command.toString());
    List<String> commands = new ArrayList<String>();
    commands.add(command.toString());		

    // Set up the container launch context for the application master
    ContainerLaunchContext amContainer = ContainerLaunchContext.newInstance(
      localResources, env, commands, null, null, null);

    // Service data is a binary blob that can be passed to the application
    // Not needed in this scenario
    // amContainer.setServiceData(serviceData);

    // Setup security tokens
    if (UserGroupInformation.isSecurityEnabled()) {
      // Note: Credentials class is marked as LimitedPrivate for HDFS and MapReduce
      Credentials credentials = new Credentials();
      String tokenRenewer = YarnClientUtils.getRmPrincipal(conf);
      if (tokenRenewer == null || tokenRenewer.length() == 0) {
        throw new IOException(
          "Can't get Master Kerberos principal for the RM to use as renewer");
      }

      // For now, only getting tokens for the default file-system.
      final Token<?> tokens[] =
          fs.addDelegationTokens(tokenRenewer, credentials);
      if (tokens != null) {
        for (Token<?> token : tokens) {
          LOG.info("Got dt for " + fs.getUri() + "; " + token);
        }
      }
      DataOutputBuffer dob = new DataOutputBuffer();
      credentials.writeTokenStorageToStream(dob);
      ByteBuffer fsTokens = ByteBuffer.wrap(dob.getData(), 0, dob.getLength());
      amContainer.setTokens(fsTokens);
    }

    appContext.setAMContainerSpec(amContainer);

    // Set the priority for the application master
    // TODO - what is the range for priority? how to decide? 
    Priority pri = Priority.newInstance(amPriority);
    appContext.setPriority(pri);

    // Set the queue to which this application is to be submitted in the RM
    appContext.setQueue(amQueue);

    // Submit the application to the applications manager
    // SubmitApplicationResponse submitResp = applicationsManager.submitApplication(appRequest);
    // Ignore the response as either a valid response object is returned on success 
    // or an exception thrown to denote some form of a failure
    LOG.info("Submitting application to ASM");

    yarnClient.submitApplication(appContext);

    // TODO
    // Try submitting the same request again
    // app submission failure?

    // Monitor the application
    return monitorApplication(appId);

  }

  /**
   * Monitor the submitted application for completion. 
   * Kill application if time expires. 
   * @param appId Application Id of application to be monitored
   * @return true if application completed successfully
   * @throws YarnException
   * @throws IOException
   */
  private boolean monitorApplication(ApplicationId appId)
      throws YarnException, IOException {

    while (true) {

      // Check app status every 1 second.
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        LOG.debug("Thread sleep in monitoring loop interrupted");
      }

      // Get application report for the appId we are interested in 
      ApplicationReport report = yarnClient.getApplicationReport(appId);

      LOG.info("Got application report from ASM for"
          + ", appId=" + appId.getId()
          + ", clientToAMToken=" + report.getClientToAMToken()
          + ", appDiagnostics=" + report.getDiagnostics()
          + ", appMasterHost=" + report.getHost()
          + ", appQueue=" + report.getQueue()
          + ", appMasterRpcPort=" + report.getRpcPort()
          + ", appStartTime=" + report.getStartTime()
          + ", yarnAppState=" + report.getYarnApplicationState().toString()
          + ", distributedFinalState=" + report.getFinalApplicationStatus().toString()
          + ", appTrackingUrl=" + report.getTrackingUrl()
          + ", appUser=" + report.getUser());

      YarnApplicationState state = report.getYarnApplicationState();
      FinalApplicationStatus dsStatus = report.getFinalApplicationStatus();
      if (YarnApplicationState.FINISHED == state) {
        if (FinalApplicationStatus.SUCCEEDED == dsStatus) {
          LOG.info("Application has completed successfully. Breaking monitoring loop");
          return true;        
        }
        else {
          LOG.info("Application did finished unsuccessfully."
              + " YarnState=" + state.toString() + ", DSFinalStatus=" + dsStatus.toString()
              + ". Breaking monitoring loop");
          return false;
        }			  
      }
      else if (YarnApplicationState.KILLED == state	
          || YarnApplicationState.FAILED == state) {
        LOG.info("Application did not finish."
            + " YarnState=" + state.toString() + ", DSFinalStatus=" + dsStatus.toString()
            + ". Breaking monitoring loop");
        return false;
      }			

      if (System.currentTimeMillis() > (clientStartTime + clientTimeout)) {
        LOG.info("Reached client specified timeout for application. Killing application");
        forceKillApplication(appId);
        return false;				
      }
    }			

  }

  /**
   * Kill a submitted application by sending a call to the ASM
   * @param appId Application Id to be killed. 
   * @throws YarnException
   * @throws IOException
   */
  private void forceKillApplication(ApplicationId appId)
      throws YarnException, IOException {
    // TODO clarify whether multiple jobs with the same app id can be submitted and be running at 
    // the same time. 
    // If yes, can we kill a particular attempt only?

    // Response can be ignored as it is non-null on success or 
    // throws an exception in case of failures
    yarnClient.killApplication(appId);	
  }

  private void addToLocalResources(FileSystem fs, String fileSrcPath,
      String fileDstPath, String appId, Map<String, LocalResource> localResources,
      String resources) throws IOException {
    String suffix =
        appName + "/" + appId + "/" + fileDstPath;
    Path dst =
        new Path(fs.getHomeDirectory(), suffix);
    if (fileSrcPath == null) {
      FSDataOutputStream ostream = null;
      try {
        ostream = FileSystem
            .create(fs, dst, new FsPermission((short) 0710));
        ostream.writeUTF(resources);
      } finally {
        IOUtils.closeQuietly(ostream);
      }
    } else {
      fs.copyFromLocalFile(new Path(fileSrcPath), dst);
    }
    FileStatus scFileStatus = fs.getFileStatus(dst);
    LocalResource scRsrc =
        LocalResource.newInstance(
            URL.fromURI(dst.toUri()),
            LocalResourceType.FILE, LocalResourceVisibility.APPLICATION,
            scFileStatus.getLen(), scFileStatus.getModificationTime());
    localResources.put(fileDstPath, scRsrc);
  }

  private void prepareTimelineDomain() {
    TimelineClient timelineClient = null;
    if (conf.getBoolean(YarnConfiguration.TIMELINE_SERVICE_ENABLED,
        YarnConfiguration.DEFAULT_TIMELINE_SERVICE_ENABLED)) {
      timelineClient = TimelineClient.createTimelineClient();
      timelineClient.init(conf);
      timelineClient.start();
    } else {
      LOG.warn("Cannot put the domain " + domainId +
          " because the timeline service is not enabled");
      return;
    }
    try {
      //TODO: we need to check and combine the existing timeline domain ACLs,
      //but let's do it once we have client java library to query domains.
      TimelineDomain domain = new TimelineDomain();
      domain.setId(domainId);
      domain.setReaders(
          viewACLs != null && viewACLs.length() > 0 ? viewACLs : " ");
      domain.setWriters(
          modifyACLs != null && modifyACLs.length() > 0 ? modifyACLs : " ");
      timelineClient.putDomain(domain);
      LOG.info("Put the timeline domain: " +
          TimelineUtils.dumpTimelineRecordtoJSON(domain));
    } catch (Exception e) {
      LOG.error("Error when putting the timeline domain", e);
    } finally {
      timelineClient.stop();
    }
  }

  private void setAMResourceCapability(ApplicationSubmissionContext appContext,
      long memory, int vcores, String profile, int priority,
      Map<String, Resource> profiles) throws IllegalArgumentException {
    if (memory < -1 || memory == 0) {
      throw new IllegalArgumentException("Invalid memory specified for"
          + " application master, exiting. Specified memory=" + memory);
    }
    if (vcores < -1 || vcores == 0) {
      throw new IllegalArgumentException("Invalid virtual cores specified for"
          + " application master, exiting. Specified virtual cores=" + vcores);
    }
    String tmp = profile;
    if (profile.isEmpty()) {
      tmp = "default";
    }
    if (appContext.getAMContainerResourceRequest() == null) {
      appContext.setAMContainerResourceRequest(ResourceRequest
          .newInstance(Priority.newInstance(priority), "*",
              Resources.clone(Resources.none()), 1));
    }

    if (appContext.getAMContainerResourceRequest().getProfileCapability()
        == null) {
      appContext.getAMContainerResourceRequest().setProfileCapability(
          ProfileCapability.newInstance(tmp, Resource.newInstance(0, 0)));
    }
    Resource capability = Resource.newInstance(0, 0);
    // set amMemory because it's used to set Xmx param
    if (profiles == null) {
      amMemory = memory == -1 ? DEFAULT_AM_MEMORY : memory;
      amVCores = vcores == -1 ? DEFAULT_AM_VCORES : vcores;
      capability.setMemorySize(amMemory);
      capability.setVirtualCores(amVCores);
    } else {
      amMemory = memory == -1 ? profiles.get(tmp).getMemorySize() : memory;
      amVCores = vcores == -1 ? profiles.get(tmp).getVirtualCores() : vcores;
      capability.setMemorySize(memory);
      capability.setVirtualCores(vcores);
    }
    appContext.getAMContainerResourceRequest().getProfileCapability()
        .setProfileCapabilityOverride(capability);
  }

  private void setContainerResources(long memory, int vcores,
      Map<String, Resource> profiles) throws IllegalArgumentException {
    if (memory < -1 || memory == 0) {
      throw new IllegalArgumentException(
          "Container memory '" + memory + "' has to be greated than 0");
    }
    if (vcores < -1 || vcores == 0) {
      throw new IllegalArgumentException(
          "Container vcores '" + vcores + "' has to be greated than 0");
    }
    if (profiles == null) {
      containerMemory = memory == -1 ? DEFAULT_CONTAINER_MEMORY : memory;
      containerVirtualCores = vcores == -1 ? DEFAULT_CONTAINER_VCORES : vcores;
    }
  }
}
