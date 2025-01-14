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

package org.elasticsearch.monitor.jvm;

import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.SettingsProperty;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;

/**
 *
 */
public class JvmService extends AbstractComponent {

    private final JvmInfo jvmInfo;

    private final TimeValue refreshInterval;

    private JvmStats jvmStats;

    public final static Setting<TimeValue> REFRESH_INTERVAL_SETTING =
        Setting.timeSetting("monitor.jvm.refresh_interval", TimeValue.timeValueSeconds(1), TimeValue.timeValueSeconds(1), false,
            SettingsProperty.ClusterScope);

    public JvmService(Settings settings) {
        super(settings);
        this.jvmInfo = JvmInfo.jvmInfo();
        this.jvmStats = JvmStats.jvmStats();

        this.refreshInterval = REFRESH_INTERVAL_SETTING.get(settings);

        logger.debug("Using refresh_interval [{}]", refreshInterval);
    }

    public JvmInfo info() {
        return this.jvmInfo;
    }

    public synchronized JvmStats stats() {
        if ((System.currentTimeMillis() - jvmStats.getTimestamp()) > refreshInterval.millis()) {
            jvmStats = JvmStats.jvmStats();
        }
        return jvmStats;
    }
}
