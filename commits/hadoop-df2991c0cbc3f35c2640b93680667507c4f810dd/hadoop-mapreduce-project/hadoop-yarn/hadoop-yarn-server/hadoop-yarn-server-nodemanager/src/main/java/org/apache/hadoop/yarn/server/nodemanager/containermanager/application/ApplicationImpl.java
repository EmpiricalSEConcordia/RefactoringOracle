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

package org.apache.hadoop.yarn.server.nodemanager.containermanager.application;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.event.Dispatcher;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.AuxServicesEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.AuxServicesEventType;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.Container;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.ContainerInitEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.ContainerKillEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.ResourceLocalizationService;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.event.ApplicationLocalizationEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.event.LocalizationEventType;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.logaggregation.ContainerLogsRetentionPolicy;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.logaggregation.event.LogAggregatorAppFinishedEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.logaggregation.event.LogAggregatorAppStartedEvent;
import org.apache.hadoop.yarn.server.security.ApplicationACLsManager;
import org.apache.hadoop.yarn.state.InvalidStateTransitonException;
import org.apache.hadoop.yarn.state.MultipleArcTransition;
import org.apache.hadoop.yarn.state.SingleArcTransition;
import org.apache.hadoop.yarn.state.StateMachine;
import org.apache.hadoop.yarn.state.StateMachineFactory;
import org.apache.hadoop.yarn.util.ConverterUtils;

/**
 * The state machine for the representation of an Application
 * within the NodeManager.
 */
public class ApplicationImpl implements Application {

  final Dispatcher dispatcher;
  final String user;
  final ApplicationId appId;
  final Credentials credentials;
  final ApplicationACLsManager aclsManager;
  private final ReadLock readLock;
  private final WriteLock writeLock;

  private static final Log LOG = LogFactory.getLog(Application.class);

  Map<ContainerId, Container> containers =
      new HashMap<ContainerId, Container>();

  public ApplicationImpl(Dispatcher dispatcher,
      ApplicationACLsManager aclsManager, String user, ApplicationId appId,
      Credentials credentials) {
    this.dispatcher = dispatcher;
    this.user = user.toString();
    this.appId = appId;
    this.credentials = credentials;
    this.aclsManager = aclsManager;
    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    readLock = lock.readLock();
    writeLock = lock.writeLock();
    stateMachine = stateMachineFactory.make(this);
  }

  @Override
  public String getUser() {
    return user.toString();
  }

  @Override
  public ApplicationId getAppId() {
    return appId;
  }

  @Override
  public ApplicationState getApplicationState() {
    this.readLock.lock();
    try {
      return this.stateMachine.getCurrentState();
    } finally {
      this.readLock.unlock();
    }
  }

  @Override
  public Map<ContainerId, Container> getContainers() {
    this.readLock.lock();
    try {
      return this.containers;
    } finally {
      this.readLock.unlock();
    }
  }

  private static final ContainerDoneTransition CONTAINER_DONE_TRANSITION =
      new ContainerDoneTransition();

  private static StateMachineFactory<ApplicationImpl, ApplicationState,
          ApplicationEventType, ApplicationEvent> stateMachineFactory =
      new StateMachineFactory<ApplicationImpl, ApplicationState,
          ApplicationEventType, ApplicationEvent>(ApplicationState.NEW)

           // Transitions from NEW state
           .addTransition(ApplicationState.NEW, ApplicationState.INITING,
               ApplicationEventType.INIT_APPLICATION, new AppInitTransition())
           .addTransition(ApplicationState.NEW, ApplicationState.NEW,
               ApplicationEventType.INIT_CONTAINER,
               new InitContainerTransition())

           // Transitions from INITING state
           .addTransition(ApplicationState.INITING, ApplicationState.INITING,
               ApplicationEventType.INIT_CONTAINER,
               new InitContainerTransition())
           .addTransition(ApplicationState.INITING,
               EnumSet.of(ApplicationState.FINISHING_CONTAINERS_WAIT,
                   ApplicationState.APPLICATION_RESOURCES_CLEANINGUP),
               ApplicationEventType.FINISH_APPLICATION,
               new AppFinishTriggeredTransition())
           .addTransition(ApplicationState.INITING, ApplicationState.RUNNING,
               ApplicationEventType.APPLICATION_INITED,
               new AppInitDoneTransition())

           // Transitions from RUNNING state
           .addTransition(ApplicationState.RUNNING,
               ApplicationState.RUNNING,
               ApplicationEventType.INIT_CONTAINER,
               new InitContainerTransition())
           .addTransition(ApplicationState.RUNNING,
               ApplicationState.RUNNING,
               ApplicationEventType.APPLICATION_CONTAINER_FINISHED,
               CONTAINER_DONE_TRANSITION)
           .addTransition(
               ApplicationState.RUNNING,
               EnumSet.of(ApplicationState.FINISHING_CONTAINERS_WAIT,
                   ApplicationState.APPLICATION_RESOURCES_CLEANINGUP),
               ApplicationEventType.FINISH_APPLICATION,
               new AppFinishTriggeredTransition())

           // Transitions from FINISHING_CONTAINERS_WAIT state.
           .addTransition(
               ApplicationState.FINISHING_CONTAINERS_WAIT,
               EnumSet.of(ApplicationState.FINISHING_CONTAINERS_WAIT,
                   ApplicationState.APPLICATION_RESOURCES_CLEANINGUP),
               ApplicationEventType.APPLICATION_CONTAINER_FINISHED,
               new AppFinishTransition())

           // Transitions from APPLICATION_RESOURCES_CLEANINGUP state
           .addTransition(ApplicationState.APPLICATION_RESOURCES_CLEANINGUP,
               ApplicationState.APPLICATION_RESOURCES_CLEANINGUP,
               ApplicationEventType.APPLICATION_CONTAINER_FINISHED)
           .addTransition(ApplicationState.APPLICATION_RESOURCES_CLEANINGUP,
               ApplicationState.FINISHED,
               ApplicationEventType.APPLICATION_RESOURCES_CLEANEDUP,
               new AppCompletelyDoneTransition())

           // create the topology tables
           .installTopology();

  private final StateMachine<ApplicationState, ApplicationEventType, ApplicationEvent> stateMachine;

  /**
   * Notify services of new application.
   * 
   * In particular, this requests that the {@link ResourceLocalizationService}
   * localize the application-scoped resources.
   */
  @SuppressWarnings("unchecked")
  static class AppInitTransition implements
      SingleArcTransition<ApplicationImpl, ApplicationEvent> {
    @Override
    public void transition(ApplicationImpl app, ApplicationEvent event) {
      ApplicationInitEvent initEvent = (ApplicationInitEvent)event;
      app.aclsManager.addApplication(app.getAppId(), initEvent
          .getApplicationACLs());
      app.dispatcher.getEventHandler().handle(
          new ApplicationLocalizationEvent(
              LocalizationEventType.INIT_APPLICATION_RESOURCES, app));
    }
  }

  /**
   * Handles INIT_CONTAINER events which request that we launch a new
   * container. When we're still in the INITTING state, we simply
   * queue these up. When we're in the RUNNING state, we pass along
   * an ContainerInitEvent to the appropriate ContainerImpl.
   */
  @SuppressWarnings("unchecked")
  static class InitContainerTransition implements
      SingleArcTransition<ApplicationImpl, ApplicationEvent> {
    @Override
    public void transition(ApplicationImpl app, ApplicationEvent event) {
      ApplicationContainerInitEvent initEvent =
        (ApplicationContainerInitEvent) event;
      Container container = initEvent.getContainer();
      app.containers.put(container.getContainerID(), container);
      LOG.info("Adding " + container.getContainerID()
          + " to application " + app.toString());
      
      switch (app.getApplicationState()) {
      case RUNNING:
        app.dispatcher.getEventHandler().handle(new ContainerInitEvent(
            container.getContainerID()));
        break;
      case INITING:
      case NEW:
        // these get queued up and sent out in AppInitDoneTransition
        break;
      default:
        assert false : "Invalid state for InitContainerTransition: " +
            app.getApplicationState();
      }
    }
  }

  @SuppressWarnings("unchecked")
  static class AppInitDoneTransition implements
      SingleArcTransition<ApplicationImpl, ApplicationEvent> {
    @Override
    public void transition(ApplicationImpl app, ApplicationEvent event) {

      // Inform the logAggregator
      app.dispatcher.getEventHandler().handle(
            new LogAggregatorAppStartedEvent(app.appId, app.user,
                app.credentials,
                ContainerLogsRetentionPolicy.ALL_CONTAINERS)); // TODO: Fix

      // Start all the containers waiting for ApplicationInit
      for (Container container : app.containers.values()) {
        app.dispatcher.getEventHandler().handle(new ContainerInitEvent(
              container.getContainerID()));
      }
    }
  }

  
  static final class ContainerDoneTransition implements
      SingleArcTransition<ApplicationImpl, ApplicationEvent> {
    @Override
    public void transition(ApplicationImpl app, ApplicationEvent event) {
      ApplicationContainerFinishedEvent containerEvent =
          (ApplicationContainerFinishedEvent) event;
      if (null == app.containers.remove(containerEvent.getContainerID())) {
        LOG.warn("Removing unknown " + containerEvent.getContainerID() +
            " from application " + app.toString());
      } else {
        LOG.info("Removing " + containerEvent.getContainerID() +
            " from application " + app.toString());
      }
    }
  }

  @SuppressWarnings("unchecked")
  void handleAppFinishWithContainersCleanedup() {
    // Delete Application level resources
    this.dispatcher.getEventHandler().handle(
        new ApplicationLocalizationEvent(
            LocalizationEventType.DESTROY_APPLICATION_RESOURCES, this));

    // tell any auxiliary services that the app is done 
    this.dispatcher.getEventHandler().handle(
        new AuxServicesEvent(AuxServicesEventType.APPLICATION_STOP, appId));

    // TODO: Trigger the LogsManager
  }

  @SuppressWarnings("unchecked")
  static class AppFinishTriggeredTransition
      implements
      MultipleArcTransition<ApplicationImpl, ApplicationEvent, ApplicationState> {
    @Override
    public ApplicationState transition(ApplicationImpl app,
        ApplicationEvent event) {

      if (app.containers.isEmpty()) {
        // No container to cleanup. Cleanup app level resources.
        app.handleAppFinishWithContainersCleanedup();
        return ApplicationState.APPLICATION_RESOURCES_CLEANINGUP;
      }

      // Send event to ContainersLauncher to finish all the containers of this
      // application.
      for (ContainerId containerID : app.containers.keySet()) {
        app.dispatcher.getEventHandler().handle(
            new ContainerKillEvent(containerID,
                "Container killed on application-finish event from RM."));
      }
      return ApplicationState.FINISHING_CONTAINERS_WAIT;
    }
  }

  static class AppFinishTransition implements
    MultipleArcTransition<ApplicationImpl, ApplicationEvent, ApplicationState> {

    @Override
    public ApplicationState transition(ApplicationImpl app,
        ApplicationEvent event) {

      ApplicationContainerFinishedEvent containerFinishEvent =
          (ApplicationContainerFinishedEvent) event;
      LOG.info("Removing " + containerFinishEvent.getContainerID()
          + " from application " + app.toString());
      app.containers.remove(containerFinishEvent.getContainerID());

      if (app.containers.isEmpty()) {
        // All containers are cleanedup.
        app.handleAppFinishWithContainersCleanedup();
        return ApplicationState.APPLICATION_RESOURCES_CLEANINGUP;
      }

      return ApplicationState.FINISHING_CONTAINERS_WAIT;
    }

  }

  @SuppressWarnings("unchecked")
  static class AppCompletelyDoneTransition implements
      SingleArcTransition<ApplicationImpl, ApplicationEvent> {
    @Override
    public void transition(ApplicationImpl app, ApplicationEvent event) {

      app.aclsManager.removeApplication(app.getAppId());

      // Inform the logService
      app.dispatcher.getEventHandler().handle(
          new LogAggregatorAppFinishedEvent(app.appId));

      // TODO: Also make logService write the acls to the aggregated file.
    }
  }

  @Override
  public void handle(ApplicationEvent event) {

    this.writeLock.lock();

    try {
      ApplicationId applicationID = event.getApplicationID();
      LOG.info("Processing " + applicationID + " of type " + event.getType());

      ApplicationState oldState = stateMachine.getCurrentState();
      ApplicationState newState = null;
      try {
        // queue event requesting init of the same app
        newState = stateMachine.doTransition(event.getType(), event);
      } catch (InvalidStateTransitonException e) {
        LOG.warn("Can't handle this event at current state", e);
      }
      if (oldState != newState) {
        LOG.info("Application " + applicationID + " transitioned from "
            + oldState + " to " + newState);
      }
    } finally {
      this.writeLock.unlock();
    }
  }

  @Override
  public String toString() {
    return ConverterUtils.toString(appId);
  }
}
