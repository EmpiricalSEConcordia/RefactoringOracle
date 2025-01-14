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

package org.elasticsearch.action.admin.cluster.state;

import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.action.TransportActions;
import org.elasticsearch.action.support.master.TransportMasterNodeOperationAction;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

/**
 * @author kimchy (Shay Banon)
 */
public class TransportClusterStateAction extends TransportMasterNodeOperationAction<ClusterStateRequest, ClusterStateResponse> {

    private final ClusterName clusterName;

    @Inject public TransportClusterStateAction(Settings settings, TransportService transportService, ClusterService clusterService, ThreadPool threadPool,
                                               ClusterName clusterName) {
        super(settings, transportService, clusterService, threadPool);
        this.clusterName = clusterName;
    }

    @Override protected String transportAction() {
        return TransportActions.Admin.Cluster.STATE;
    }

    @Override protected ClusterStateRequest newRequest() {
        return new ClusterStateRequest();
    }

    @Override protected ClusterStateResponse newResponse() {
        return new ClusterStateResponse();
    }

    @Override protected ClusterStateResponse masterOperation(ClusterStateRequest request) throws ElasticSearchException {
        return new ClusterStateResponse(clusterName, clusterService.state());
    }
}