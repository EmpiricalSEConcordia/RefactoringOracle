/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.monitor.process;

import org.elasticsearch.util.component.AbstractComponent;
import org.elasticsearch.util.inject.Inject;
import org.elasticsearch.util.settings.Settings;

import static org.elasticsearch.monitor.jvm.JvmInfo.*;

/**
 * @author kimchy (shay.banon)
 */
public class JmxProcessProbe extends AbstractComponent implements ProcessProbe {

    @Inject public JmxProcessProbe(Settings settings) {
        super(settings);
    }

    @Override public ProcessInfo processInfo() {
        return new ProcessInfo(jvmInfo().pid());
    }

    @Override public ProcessStats processStats() {
        ProcessStats stats = new ProcessStats();
        stats.timestamp = System.currentTimeMillis();
        return stats;
    }
}
