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

package org.elasticsearch.indices;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.indices.analysis.IndicesAnalysisService;
import org.elasticsearch.indices.cluster.IndicesClusterStateService;
import org.elasticsearch.indices.recovery.throttler.RecoveryThrottler;

/**
 * @author kimchy (Shay Banon)
 */
public class IndicesModule extends AbstractModule {

    private final Settings settings;

    public IndicesModule(Settings settings) {
        this.settings = settings;
    }

    @Override protected void configure() {
        bind(IndicesLifecycle.class).to(InternalIndicesLifecycle.class).asEagerSingleton();
        bind(IndicesService.class).to(InternalIndicesService.class).asEagerSingleton();
        bind(RecoveryThrottler.class).asEagerSingleton();
        bind(IndicesClusterStateService.class).asEagerSingleton();
        bind(IndicesMemoryCleaner.class).asEagerSingleton();
        bind(IndicesAnalysisService.class).asEagerSingleton();
    }
}
