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

import org.apache.hadoop.classification.InterfaceAudience.Public;
import org.apache.hadoop.classification.InterfaceStability.Evolving;

/**
 * Container exit statuses indicating special exit circumstances.
 */
@Public
@Evolving
public class ContainerExitStatus {
  public static final int SUCCESS = 0;
  public static final int INVALID = -1000;

  /**
   * Containers killed by the framework, either due to being released by
   * the application or being 'lost' due to node failures etc.
   */
  public static final int ABORTED = -100;
  
  /**
   * When threshold number of the nodemanager-local-directories or
   * threshold number of the nodemanager-log-directories become bad.
   */
  public static final int DISKS_FAILED = -101;
}
