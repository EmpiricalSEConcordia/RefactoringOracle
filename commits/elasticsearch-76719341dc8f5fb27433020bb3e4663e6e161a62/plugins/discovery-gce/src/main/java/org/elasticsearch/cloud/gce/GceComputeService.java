/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cloud.gce;

import com.google.api.services.compute.model.Instance;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.SettingsProperty;
import org.elasticsearch.common.unit.TimeValue;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public interface GceComputeService extends LifecycleComponent<GceComputeService> {

    /**
     * GCE API Version: Elasticsearch/GceCloud/1.0
     */
    String VERSION = "Elasticsearch/GceCloud/1.0";

    // cloud.gce settings

    /**
     * cloud.gce.project_id: Google project id
     */
    Setting<String> PROJECT_SETTING = Setting.simpleString("cloud.gce.project_id", SettingsProperty.ClusterScope);

    /**
     * cloud.gce.zone: Google Compute Engine zones
     */
    Setting<List<String>> ZONE_SETTING =
        Setting.listSetting("cloud.gce.zone", Collections.emptyList(), Function.identity(), SettingsProperty.ClusterScope);

    /**
     * cloud.gce.refresh_interval: How long the list of hosts is cached to prevent further requests to the AWS API. 0 disables caching.
     * A negative value will cause infinite caching. Defaults to 0s.
     */
    Setting<TimeValue> REFRESH_SETTING =
        Setting.timeSetting("cloud.gce.refresh_interval", TimeValue.timeValueSeconds(0), SettingsProperty.ClusterScope);

    /**
     * cloud.gce.retry: Should we retry calling GCE API in case of error? Defaults to true.
     */
    Setting<Boolean> RETRY_SETTING = Setting.boolSetting("cloud.gce.retry", true, SettingsProperty.ClusterScope);

    /**
     * cloud.gce.max_wait: How long exponential backoff should retry before definitely failing.
     * It's a total time since the the initial call is made.
     * A negative value will retry indefinitely. Defaults to `-1s` (retry indefinitely).
     */
    Setting<TimeValue> MAX_WAIT_SETTING =
        Setting.timeSetting("cloud.gce.max_wait", TimeValue.timeValueSeconds(-1), SettingsProperty.ClusterScope);

    /**
     * Return a collection of running instances within the same GCE project
     * @return a collection of running instances within the same GCE project
     */
    Collection<Instance> instances();

    /**
     * <p>Gets metadata on the current running machine (call to
     * http://metadata.google.internal/computeMetadata/v1/instance/xxx).</p>
     * <p>For example, you can retrieve network information by replacing xxx with:</p>
     * <ul>
     *     <li>`hostname` when we need to resolve the host name</li>
     *     <li>`network-interfaces/0/ip` when we need to resolve private IP</li>
     * </ul>
     * @see org.elasticsearch.cloud.gce.network.GceNameResolver for bindings
     * @param metadataPath path to metadata information
     * @return extracted information (for example a hostname or an IP address)
     * @throws IOException in case metadata URL is not accessible
     */
    String metadata(String metadataPath) throws IOException;
}
