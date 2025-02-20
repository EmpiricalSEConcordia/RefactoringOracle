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

package org.apache.hadoop.yarn.server.resourcemanager.rmapp;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Method;

import junit.framework.Assert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.MockApps;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.event.AsyncDispatcher;
import org.apache.hadoop.yarn.event.Dispatcher;
import org.apache.hadoop.yarn.event.EventHandler;
import org.apache.hadoop.yarn.security.ApplicationTokenSecretManager;
import org.apache.hadoop.yarn.server.resourcemanager.RMContext;
import org.apache.hadoop.yarn.server.resourcemanager.RMContextImpl;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.RMApp;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.RMAppImpl;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.RMAppEventType;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.attempt.AMLivelinessMonitor;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.attempt.RMAppAttempt;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.attempt.RMAppAttemptEvent;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.attempt.RMAppAttemptEventType;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.attempt.RMAppAttemptImpl;
import org.apache.hadoop.yarn.server.resourcemanager.recovery.ApplicationsStore.ApplicationStore;
import org.apache.hadoop.yarn.server.resourcemanager.recovery.MemStore;
import org.apache.hadoop.yarn.server.resourcemanager.rmcontainer.ContainerAllocationExpirer;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.YarnScheduler;
import org.apache.hadoop.yarn.server.resourcemanager.ApplicationMasterService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class TestRMAppTransitions {
  private static final Log LOG = LogFactory.getLog(TestRMAppTransitions.class);
  
  private RMContext rmContext;
  private static int maxRetries = 4;
  private static int appId = 1;

  // ignore all the RM application attempt events
  private static final class TestApplicationAttemptEventDispatcher implements
      EventHandler<RMAppAttemptEvent> {

    public TestApplicationAttemptEventDispatcher() {
    }

    @Override
    public void handle(RMAppAttemptEvent event) {
    }
  }

  // handle all the RM application events - same as in ResourceManager.java
  private static final class TestApplicationEventDispatcher implements
      EventHandler<RMAppEvent> {

    private final RMContext rmContext;
    public TestApplicationEventDispatcher(RMContext rmContext) {
      this.rmContext = rmContext;
    }

    @Override
    public void handle(RMAppEvent event) {
      ApplicationId appID = event.getApplicationId();
      RMApp rmApp = this.rmContext.getRMApps().get(appID);
      if (rmApp != null) {
        try {
          rmApp.handle(event);
        } catch (Throwable t) {
          LOG.error("Error in handling event type " + event.getType()
              + " for application " + appID, t);
        }
      }
    }
  }

  @Before
  public void setUp() throws Exception {
    Configuration conf = new Configuration();
    Dispatcher rmDispatcher = new AsyncDispatcher();

    ContainerAllocationExpirer containerAllocationExpirer = mock(ContainerAllocationExpirer.class);
    AMLivelinessMonitor amLivelinessMonitor = mock(AMLivelinessMonitor.class);
    this.rmContext = new RMContextImpl(new MemStore(), rmDispatcher,
      containerAllocationExpirer, amLivelinessMonitor);

    rmDispatcher.register(RMAppAttemptEventType.class,
        new TestApplicationAttemptEventDispatcher());

    rmDispatcher.register(RMAppEventType.class,
        new TestApplicationEventDispatcher(rmContext));
  }

  protected RMApp createNewTestApp() {
    ApplicationId applicationId = MockApps.newAppID(appId++);
    String user = MockApps.newUserName();
    String name = MockApps.newAppName();
    String queue = MockApps.newQueue();
    Configuration conf = new YarnConfiguration();
    // ensure max retries set to known value
    conf.setInt("yarn.server.resourcemanager.application.max.retries", maxRetries);
    ApplicationSubmissionContext submissionContext = null; 
    String clientTokenStr = "bogusstring";
    ApplicationStore appStore = mock(ApplicationStore.class);
    YarnScheduler scheduler = mock(YarnScheduler.class);
    ApplicationMasterService masterService = new ApplicationMasterService(rmContext,
        new ApplicationTokenSecretManager(), scheduler);

    RMApp application = new RMAppImpl(applicationId, rmContext,
          conf, name, user,
          queue, submissionContext, clientTokenStr,
          appStore, rmContext.getAMLivelinessMonitor(), scheduler,
          masterService);

    testAppStartState(applicationId, user, name, queue, application);
    return application;
  }

  // Test expected newly created app state
  private static void testAppStartState(ApplicationId applicationId, String user, 
        String name, String queue, RMApp application) {
    Assert.assertTrue("application start time is not greater then 0", 
        application.getStartTime() > 0);
    Assert.assertTrue("application start time is before currentTime", 
        application.getStartTime() <= System.currentTimeMillis());
    Assert.assertEquals("application user is not correct",
        user, application.getUser());
    Assert.assertEquals("application id is not correct",
        applicationId, application.getApplicationId());
    Assert.assertEquals("application progress is not correct",
        (float)0.0, application.getProgress());
    Assert.assertEquals("application queue is not correct",
        queue, application.getQueue());
    Assert.assertEquals("application name is not correct",
        name, application.getName());
    Assert.assertEquals("application finish time is not 0 and should be",
        0, application.getFinishTime());
    Assert.assertEquals("application tracking url is not correct",
        null, application.getTrackingUrl());
    StringBuilder diag = application.getDiagnostics();
    Assert.assertEquals("application diagnostics is not correct",
        0, diag.length());
  }

  // test to make sure times are set when app finishes
  private static void assertStartTimeSet(RMApp application) {
    Assert.assertTrue("application start time is not greater then 0", 
        application.getStartTime() > 0);
    Assert.assertTrue("application start time is before currentTime", 
        application.getStartTime() <= System.currentTimeMillis());
  }

  private static void assertAppState(RMAppState state, RMApp application) {
    Assert.assertEquals("application state should have been" + state, 
        state, application.getState());
  }

  // test to make sure times are set when app finishes
  private static void assertTimesAtFinish(RMApp application) {
    assertStartTimeSet(application);
    Assert.assertTrue("application finish time is not greater then 0",
        (application.getFinishTime() > 0)); 
    Assert.assertTrue("application finish time is not >= then start time",
        (application.getFinishTime() >= application.getStartTime()));
  }

  private static void assertKilled(RMApp application) {
    assertTimesAtFinish(application);
    assertAppState(RMAppState.KILLED, application);
    StringBuilder diag = application.getDiagnostics();
    Assert.assertEquals("application diagnostics is not correct",
        "Application killed by user.", diag.toString());
  }

  private static void assertFailed(RMApp application, String regex) {
    assertTimesAtFinish(application);
    assertAppState(RMAppState.FAILED, application);
    StringBuilder diag = application.getDiagnostics();
    Assert.assertTrue("application diagnostics is not correct",
        diag.toString().matches(regex));
  }

  protected RMApp testCreateAppSubmitted() throws IOException {
    RMApp application = createNewTestApp();
    // NEW => SUBMITTED event RMAppEventType.START
    RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.START);
    application.handle(event);
    assertStartTimeSet(application);
    assertAppState(RMAppState.SUBMITTED, application);
    return application;
  }

  protected RMApp testCreateAppAccepted() throws IOException {
    RMApp application = testCreateAppSubmitted();
    // SUBMITTED => ACCEPTED event RMAppEventType.APP_ACCEPTED
    RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.APP_ACCEPTED);
    application.handle(event);
    assertStartTimeSet(application);
    assertAppState(RMAppState.ACCEPTED, application);
    return application;
  }

  protected RMApp testCreateAppRunning() throws IOException {
    RMApp application = testCreateAppAccepted();
    // ACCEPTED => RUNNING event RMAppEventType.ATTEMPT_REGISTERED
    RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.ATTEMPT_REGISTERED);
    application.handle(event);
    assertStartTimeSet(application);
    assertAppState(RMAppState.RUNNING, application);
    return application;
  }

  protected RMApp testCreateAppFinished() throws IOException {
    RMApp application = testCreateAppRunning();
    // RUNNING => FINISHED event RMAppEventType.ATTEMPT_FINISHED
    RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.ATTEMPT_FINISHED);
    application.handle(event);
    assertAppState(RMAppState.FINISHED, application);
    assertTimesAtFinish(application);
    return application;
  }

  @Test
  public void testAppSuccessPath() throws IOException {
    LOG.info("--- START: testAppSuccessPath ---");
    testCreateAppFinished();
  }

  @Test
  public void testAppNewKill() throws IOException {
    LOG.info("--- START: testAppNewKill ---");

    RMApp application = createNewTestApp();
    // NEW => KILLED event RMAppEventType.KILL
    RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.KILL);
    application.handle(event);
    assertKilled(application);
  }

  @Test
  public void testAppNewReject() throws IOException {
    LOG.info("--- START: testAppNewReject ---");

    RMApp application = createNewTestApp();
    // NEW => FAILED event RMAppEventType.APP_REJECTED
    String rejectedText = "Test Application Rejected";
    RMAppEvent event = new RMAppRejectedEvent(application.getApplicationId(), rejectedText);
    application.handle(event);
    assertFailed(application, rejectedText);
  }

  @Test
  public void testAppSubmittedRejected() throws IOException {
    LOG.info("--- START: testAppSubmittedRejected ---");

    RMApp application = testCreateAppSubmitted();
    // SUBMITTED => FAILED event RMAppEventType.APP_REJECTED
    String rejectedText = "app rejected";
    RMAppEvent event = new RMAppRejectedEvent(application.getApplicationId(), rejectedText);
    application.handle(event);
    assertFailed(application, rejectedText);
  }

  @Test
  public void testAppSubmittedKill() throws IOException {
    LOG.info("--- START: testAppSubmittedKill---");

    RMApp application = testCreateAppAccepted();
    // SUBMITTED => KILLED event RMAppEventType.KILL 
    RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.KILL);
    application.handle(event);
    assertKilled(application);
  }

  @Test
  public void testAppAcceptedFailed() throws IOException {
    LOG.info("--- START: testAppAcceptedFailed ---");

    RMApp application = testCreateAppAccepted();
    // ACCEPTED => ACCEPTED event RMAppEventType.RMAppEventType.ATTEMPT_FAILED
    for (int i=1; i<maxRetries; i++) {
      RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.ATTEMPT_FAILED);
      application.handle(event);
      assertAppState(RMAppState.ACCEPTED, application);
    }

    // ACCEPTED => FAILED event RMAppEventType.RMAppEventType.ATTEMPT_FAILED after max retries
    RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.ATTEMPT_FAILED);
    application.handle(event);
    assertFailed(application, ".*Failing the application.*");
  }

  @Test
  public void testAppAcceptedKill() throws IOException {
    LOG.info("--- START: testAppAcceptedKill ---");

    RMApp application = testCreateAppAccepted();
    // ACCEPTED => KILLED event RMAppEventType.KILL
    RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.KILL);
    application.handle(event);
    assertKilled(application);
  }

  @Test
  public void testAppRunningKill() throws IOException {
    LOG.info("--- START: testAppRunningKill ---");

    RMApp application = testCreateAppRunning();
    // RUNNING => KILLED event RMAppEventType.KILL
    RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.KILL);
    application.handle(event);
    assertKilled(application);
  }

  @Test
  public void testAppRunningFailed() throws IOException {
    LOG.info("--- START: testAppRunningFailed ---");

    RMApp application = testCreateAppRunning();
    // RUNNING => FAILED/RESTARTING event RMAppEventType.ATTEMPT_FAILED
    for (int i=1; i<maxRetries; i++) {
      RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.ATTEMPT_FAILED);
      application.handle(event);
      assertAppState(RMAppState.RUNNING, application);
    }

    // RUNNING => FAILED/RESTARTING event RMAppEventType.ATTEMPT_FAILED after max retries
    RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.ATTEMPT_FAILED);
    application.handle(event);
    assertFailed(application, ".*Failing the application.*");

    // FAILED => FAILED event RMAppEventType.KILL
    event = new RMAppEvent(application.getApplicationId(), RMAppEventType.KILL);
    application.handle(event);
    assertFailed(application, ".*Failing the application.*");
  }


  @Test
  public void testAppFinishedFinished() throws IOException {
    LOG.info("--- START: testAppFinishedFinished ---");

    RMApp application = testCreateAppFinished();
    // FINISHED => FINISHED event RMAppEventType.KILL
    RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.KILL);
    application.handle(event);
    assertTimesAtFinish(application);
    assertAppState(RMAppState.FINISHED, application);
    StringBuilder diag = application.getDiagnostics();
    Assert.assertEquals("application diagnostics is not correct",
        "", diag.toString());
  }

  @Test
  public void testAppKilledKilled() throws IOException {
    LOG.info("--- START: testAppKilledKilled ---");

    RMApp application = testCreateAppRunning();

    // RUNNING => KILLED event RMAppEventType.KILL
    RMAppEvent event = new RMAppEvent(application.getApplicationId(), RMAppEventType.KILL);
    application.handle(event);
    assertTimesAtFinish(application);
    assertAppState(RMAppState.KILLED, application);

    // KILLED => KILLED event RMAppEventType.ATTEMPT_FINISHED
    event = new RMAppEvent(application.getApplicationId(), RMAppEventType.ATTEMPT_FINISHED);
    application.handle(event);
    assertTimesAtFinish(application);
    assertAppState(RMAppState.KILLED, application);

    // KILLED => KILLED event RMAppEventType.ATTEMPT_FAILED
    event = new RMAppEvent(application.getApplicationId(), RMAppEventType.ATTEMPT_FAILED);
    application.handle(event);
    assertTimesAtFinish(application);
    assertAppState(RMAppState.KILLED, application);

    // KILLED => KILLED event RMAppEventType.ATTEMPT_KILLED
    event = new RMAppEvent(application.getApplicationId(), RMAppEventType.ATTEMPT_KILLED);
    application.handle(event);
    assertTimesAtFinish(application);
    assertAppState(RMAppState.KILLED, application);

    // KILLED => KILLED event RMAppEventType.KILL
    event = new RMAppEvent(application.getApplicationId(), RMAppEventType.KILL);
    application.handle(event);
    assertTimesAtFinish(application);
    assertAppState(RMAppState.KILLED, application);
  }
}
