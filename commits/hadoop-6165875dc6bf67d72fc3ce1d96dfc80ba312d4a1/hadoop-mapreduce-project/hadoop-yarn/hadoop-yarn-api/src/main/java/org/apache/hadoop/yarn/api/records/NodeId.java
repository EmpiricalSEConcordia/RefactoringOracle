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

package org.apache.hadoop.yarn.api.records;

import org.apache.hadoop.classification.InterfaceAudience.Private;
import org.apache.hadoop.classification.InterfaceAudience.Public;
import org.apache.hadoop.classification.InterfaceStability.Stable;
import org.apache.hadoop.classification.InterfaceStability.Unstable;

/**
 * <p><code>NodeId</code> is the unique identifier for a node.</p>
 * 
 * <p>It includes the <em>hostname</em> and <em>port</em> to uniquely 
 * identify the node. Thus, it is unique across restarts of any 
 * <code>NodeManager</code>.</p>
 */
@Public
@Stable
public interface NodeId extends Comparable<NodeId> {

  /**
   * Get the <em>hostname</em> of the node.
   * @return <em>hostname</em> of the node
   */ 
  @Public
  @Stable
  String getHost();
  
  @Private
  @Unstable
  void setHost(String host);

  /**
   * Get the <em>port</em> for communicating with the node.
   * @return <em>port</em> for communicating with the node
   */
  @Public
  @Stable
  int getPort();
  
  @Private
  @Unstable
  void setPort(int port);
}
