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

package org.apache.hadoop.yarn.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceAudience.Private;
import org.apache.hadoop.classification.InterfaceAudience.Public;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.service.AbstractService;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.api.records.Token;
import org.apache.hadoop.yarn.exceptions.YarnException;

@InterfaceAudience.Public
@InterfaceStability.Unstable
public abstract class NMClient extends AbstractService {

  /**
   * Create a new instance of NMClient.
   */
  @Public
  public static NMClient createNMClient() {
    NMClient client = new NMClientImpl();
    return client;
  }

  /**
   * Create a new instance of NMClient.
   */
  @Public
  public static NMClient createNMClient(String name) {
    NMClient client = new NMClientImpl(name);
    return client;
  }

  @Private
  protected NMClient(String name) {
    super(name);
  }

  /**
   * <p>Start an allocated container.</p>
   *
   * <p>The <code>ApplicationMaster</code> or other applications that use the
   * client must provide the details of the allocated container, including the
   * Id, the assigned node's Id and the token via {@link Container}. In
   * addition, the AM needs to provide the {@link ContainerLaunchContext} as
   * well.</p>
   *
   * @param container the allocated container
   * @param containerLaunchContext the context information needed by the
   *                               <code>NodeManager</code> to launch the
   *                               container
   * @return a map between the auxiliary service names and their outputs
   * @throws YarnException
   * @throws IOException
   */
  public abstract Map<String, ByteBuffer> startContainer(Container container,
      ContainerLaunchContext containerLaunchContext)
          throws YarnException, IOException;

  /**
   * <p>Stop an started container.</p>
   *
   * @param containerId the Id of the started container
   * @param nodeId the Id of the <code>NodeManager</code>
   * @param containerToken the security token to verify authenticity of the
   *                       started container
   * @throws YarnException
   * @throws IOException
   */
  public abstract void stopContainer(ContainerId containerId, NodeId nodeId,
      Token containerToken) throws YarnException, IOException;

  /**
   * <p>Query the status of a container.</p>
   *
   * @param containerId the Id of the started container
   * @param nodeId the Id of the <code>NodeManager</code>
   * @param containerToken the security token to verify authenticity of the
   *                       started container
   * @return the status of a container
   * @throws YarnException
   * @throws IOException
   */
  public abstract ContainerStatus getContainerStatus(ContainerId containerId, NodeId nodeId,
      Token containerToken) throws YarnException, IOException;

  /**
   * <p>Set whether the containers that are started by this client, and are
   * still running should be stopped when the client stops. By default, the
   * feature should be enabled.</p>
   *
   * @param enabled whether the feature is enabled or not
   */
  public abstract void cleanupRunningContainersOnStop(boolean enabled);

}
