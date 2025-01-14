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

package org.apache.hadoop.yarn.server.nodemanager;

import static org.junit.Assert.fail;

import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.exceptions.YarnRemoteException;
import org.apache.hadoop.yarn.security.ContainerTokenIdentifier;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.ContainerManagerImpl;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.application.Application;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.application.ApplicationEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.application.ApplicationEventType;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.application.ApplicationInitedEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.Container;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.ContainerEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.ContainerEventType;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.ContainerExitEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.ContainerResourceLocalizedEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.launcher.ContainersLauncher;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.launcher.ContainersLauncherEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.LocalResourceRequest;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.ResourceLocalizationService;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.event.ApplicationLocalizationEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.event.ContainerLocalizationEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.event.ContainerLocalizationRequestEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.event.LocalizationEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.loghandler.LogHandler;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.loghandler.event.LogHandlerEvent;
import org.apache.hadoop.yarn.server.nodemanager.metrics.NodeManagerMetrics;
import org.apache.hadoop.yarn.server.security.ApplicationACLsManager;

public class DummyContainerManager extends ContainerManagerImpl {

  private static final Log LOG = LogFactory
      .getLog(DummyContainerManager.class);
  
  public DummyContainerManager(Context context, ContainerExecutor exec,
      DeletionService deletionContext, NodeStatusUpdater nodeStatusUpdater,
      NodeManagerMetrics metrics,
      ApplicationACLsManager applicationACLsManager,
      LocalDirsHandlerService dirsHandler) {
    super(context, exec, deletionContext, nodeStatusUpdater, metrics,
      applicationACLsManager, dirsHandler);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected ResourceLocalizationService createResourceLocalizationService(
      ContainerExecutor exec, DeletionService deletionContext) {
    return new ResourceLocalizationService(super.dispatcher, exec,
        deletionContext, super.dirsHandler) {
      @Override
      public void handle(LocalizationEvent event) {
        switch (event.getType()) {
        case INIT_APPLICATION_RESOURCES:
          Application app =
              ((ApplicationLocalizationEvent) event).getApplication();
          // Simulate event from ApplicationLocalization.
          dispatcher.getEventHandler().handle(new ApplicationInitedEvent(
                app.getAppId()));
          break;
        case INIT_CONTAINER_RESOURCES:
          ContainerLocalizationRequestEvent rsrcReqs =
            (ContainerLocalizationRequestEvent) event;
          // simulate localization of all requested resources
            for (Collection<LocalResourceRequest> rc : rsrcReqs
                .getRequestedResources().values()) {
              for (LocalResourceRequest req : rc) {
                LOG.info("DEBUG: " + req + ":"
                    + rsrcReqs.getContainer().getContainerId());
                dispatcher.getEventHandler().handle(
                    new ContainerResourceLocalizedEvent(rsrcReqs.getContainer()
                        .getContainerId(), req, new Path("file:///local"
                        + req.getPath().toUri().getPath())));
              }
            }
          break;
        case CLEANUP_CONTAINER_RESOURCES:
          Container container =
              ((ContainerLocalizationEvent) event).getContainer();
          // TODO: delete the container dir
          this.dispatcher.getEventHandler().handle(
              new ContainerEvent(container.getContainerId(),
                  ContainerEventType.CONTAINER_RESOURCES_CLEANEDUP));
          break;
        case DESTROY_APPLICATION_RESOURCES:
          Application application =
            ((ApplicationLocalizationEvent) event).getApplication();

          // decrement reference counts of all resources associated with this
          // app
          this.dispatcher.getEventHandler().handle(
              new ApplicationEvent(application.getAppId(),
                  ApplicationEventType.APPLICATION_RESOURCES_CLEANEDUP));
          break;
        default:
          fail("Unexpected event: " + event.getType());
        }
      }
    };
  }

  @Override
  @SuppressWarnings("unchecked")
  protected ContainersLauncher createContainersLauncher(Context context,
      ContainerExecutor exec) {
    return new ContainersLauncher(context, super.dispatcher, exec,
                                  super.dirsHandler) {
      @Override
      public void handle(ContainersLauncherEvent event) {
        Container container = event.getContainer();
        ContainerId containerId = container.getContainerId();
        switch (event.getType()) {
        case LAUNCH_CONTAINER:
          dispatcher.getEventHandler().handle(
              new ContainerEvent(containerId,
                  ContainerEventType.CONTAINER_LAUNCHED));
          break;
        case CLEANUP_CONTAINER:
          dispatcher.getEventHandler().handle(
              new ContainerExitEvent(containerId,
                  ContainerEventType.CONTAINER_KILLED_ON_REQUEST, 0,
                  "Container exited with exit code 0."));
          break;
        }
      }
    };
  }

  @Override
  protected LogHandler createLogHandler(Configuration conf,
      Context context, DeletionService deletionService) {
    return new LogHandler() {
      
      @Override
      public void handle(LogHandlerEvent event) {
        switch (event.getType()) {
          case APPLICATION_STARTED:
            break;
          case CONTAINER_FINISHED:
            break;
          case APPLICATION_FINISHED:
            break;
          default:
            // Ignore
          }
      }
    };
  }

  @Override
  public void setBlockNewContainerRequests(boolean blockNewContainerRequests) {
    // do nothing
  }
  
  @Override
  protected void authorizeRequest(String containerIDStr,
      ContainerLaunchContext launchContext,
      UserGroupInformation remoteUgi, ContainerTokenIdentifier tokenId)
      throws YarnRemoteException {
    // do Nothing
  }

  @Override
  protected ContainerTokenIdentifier
      getContainerTokenIdentifier(UserGroupInformation remoteUgi,
          ContainerTokenIdentifier containerTokenId) throws YarnRemoteException {
    return containerTokenId;
  }
}