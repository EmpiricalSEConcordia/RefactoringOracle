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

package org.elasticsearch.action.ingest;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.bulk.BulkAction;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexAction;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.elasticsearch.node.NodeModule;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.VersionUtils;
import org.elasticsearch.transport.TransportException;
import org.elasticsearch.transport.TransportRequest;
import org.elasticsearch.transport.TransportResponse;
import org.elasticsearch.transport.TransportResponseHandler;
import org.elasticsearch.transport.TransportService;
import org.hamcrest.CustomTypeSafeMatcher;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class IngestProxyActionFilterTests extends ESTestCase {

    private TransportService transportService;

    @SuppressWarnings("unchecked")
    private IngestProxyActionFilter buildFilter(int ingestNodes, int totalNodes) {
        ClusterState clusterState = mock(ClusterState.class);
        DiscoveryNodes.Builder builder = new DiscoveryNodes.Builder();
        DiscoveryNode localNode = null;
        for (int i = 0; i < totalNodes; i++) {
            String nodeId = "node" + i;
            Map<String, String> attributes = new HashMap<>();
            if (i >= ingestNodes) {
                attributes.put("ingest", "false");
            } else if (randomBoolean()) {
                attributes.put("ingest", "true");
            }
            DiscoveryNode node = new DiscoveryNode(nodeId, nodeId, DummyTransportAddress.INSTANCE, attributes, VersionUtils.randomVersion(random()));
            builder.put(node);
            if (i == totalNodes - 1) {
                localNode = node;
            }
        }
        when(clusterState.nodes()).thenReturn(builder.build());
        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.state()).thenReturn(clusterState);
        transportService = mock(TransportService.class);
        return new IngestProxyActionFilter(clusterService, transportService);
    }

    public void testApplyNoIngestNodes() {
        Task task = mock(Task.class);
        ActionListener actionListener = mock(ActionListener.class);
        ActionFilterChain actionFilterChain = mock(ActionFilterChain.class);
        int totalNodes = randomIntBetween(1, 5);
        IngestProxyActionFilter filter = buildFilter(0, totalNodes);

        String action;
        ActionRequest request;
        if (randomBoolean()) {
            action = IndexAction.NAME;
            request = new IndexRequest().pipeline("_id");
        } else {
            action = BulkAction.NAME;
            request = new BulkRequest().add(new IndexRequest().pipeline("_id"));
        }
        try {
            filter.apply(task, action, request, actionListener, actionFilterChain);
            fail("should have failed because there are no ingest nodes");
        } catch(IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("There are no ingest nodes in this cluster, unable to forward request to an ingest node."));
        }
        verifyZeroInteractions(transportService);
        verifyZeroInteractions(actionFilterChain);
        verifyZeroInteractions(actionListener);
    }

    public void testApplyNoPipelineId() {
        Task task = mock(Task.class);
        ActionListener actionListener = mock(ActionListener.class);
        ActionFilterChain actionFilterChain = mock(ActionFilterChain.class);
        int totalNodes = randomIntBetween(1, 5);
        IngestProxyActionFilter filter = buildFilter(randomIntBetween(0, totalNodes - 1), totalNodes);

        String action;
        ActionRequest request;
        if (randomBoolean()) {
            action = IndexAction.NAME;
            request = new IndexRequest();
        } else {
            action = BulkAction.NAME;
            request = new BulkRequest().add(new IndexRequest());
        }
        filter.apply(task, action, request, actionListener, actionFilterChain);
        verifyZeroInteractions(transportService);
        verify(actionFilterChain).proceed(any(Task.class), eq(action), same(request), same(actionListener));
        verifyZeroInteractions(actionListener);
    }

    public void testApplyAnyAction() {
        Task task = mock(Task.class);
        ActionListener actionListener = mock(ActionListener.class);
        ActionFilterChain actionFilterChain = mock(ActionFilterChain.class);
        ActionRequest request = mock(ActionRequest.class);
        int totalNodes = randomIntBetween(1, 5);
        IngestProxyActionFilter filter = buildFilter(randomIntBetween(0, totalNodes - 1), totalNodes);

        String action = randomAsciiOfLengthBetween(1, 20);
        filter.apply(task, action, request, actionListener, actionFilterChain);
        verifyZeroInteractions(transportService);
        verify(actionFilterChain).proceed(any(Task.class), eq(action), same(request), same(actionListener));
        verifyZeroInteractions(actionListener);
    }

    @SuppressWarnings("unchecked")
    public void testApplyIndexRedirect() {
        Task task = mock(Task.class);
        ActionListener actionListener = mock(ActionListener.class);
        ActionFilterChain actionFilterChain = mock(ActionFilterChain.class);
        int totalNodes = randomIntBetween(2, 5);
        IngestProxyActionFilter filter = buildFilter(randomIntBetween(1, totalNodes - 1), totalNodes);
        Answer<Void> answer = invocationOnMock -> {
            TransportResponseHandler transportResponseHandler = (TransportResponseHandler) invocationOnMock.getArguments()[3];
            transportResponseHandler.handleResponse(new IndexResponse());
            return null;
        };
        doAnswer(answer).when(transportService).sendRequest(any(DiscoveryNode.class), any(String.class), any(TransportRequest.class), any(TransportResponseHandler.class));

        IndexRequest indexRequest = new IndexRequest().pipeline("_id");
        filter.apply(task, IndexAction.NAME, indexRequest, actionListener, actionFilterChain);

        verify(transportService).sendRequest(argThat(new IngestNodeMatcher()), eq(IndexAction.NAME), same(indexRequest), any(TransportResponseHandler.class));
        verifyZeroInteractions(actionFilterChain);
        verify(actionListener).onResponse(any(IndexResponse.class));
        verify(actionListener, never()).onFailure(any(TransportException.class));
    }

    @SuppressWarnings("unchecked")
    public void testApplyBulkRedirect() {
        Task task = mock(Task.class);
        ActionListener actionListener = mock(ActionListener.class);
        ActionFilterChain actionFilterChain = mock(ActionFilterChain.class);
        int totalNodes = randomIntBetween(2, 5);
        IngestProxyActionFilter filter = buildFilter(randomIntBetween(1, totalNodes - 1), totalNodes);
        Answer<Void> answer = invocationOnMock -> {
            TransportResponseHandler transportResponseHandler = (TransportResponseHandler) invocationOnMock.getArguments()[3];
            transportResponseHandler.handleResponse(new BulkResponse(null, -1));
            return null;
        };
        doAnswer(answer).when(transportService).sendRequest(any(DiscoveryNode.class), any(String.class), any(TransportRequest.class), any(TransportResponseHandler.class));

        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.add(new IndexRequest().pipeline("_id"));
        int numNoPipelineRequests = randomIntBetween(0, 10);
        for (int i = 0; i < numNoPipelineRequests; i++) {
            bulkRequest.add(new IndexRequest());
        }
        filter.apply(task, BulkAction.NAME, bulkRequest, actionListener, actionFilterChain);

        verify(transportService).sendRequest(argThat(new IngestNodeMatcher()), eq(BulkAction.NAME), same(bulkRequest), any(TransportResponseHandler.class));
        verifyZeroInteractions(actionFilterChain);
        verify(actionListener).onResponse(any(BulkResponse.class));
        verify(actionListener, never()).onFailure(any(TransportException.class));
    }

    @SuppressWarnings("unchecked")
    public void testApplyFailures() {
        Task task = mock(Task.class);
        ActionListener actionListener = mock(ActionListener.class);
        ActionFilterChain actionFilterChain = mock(ActionFilterChain.class);
        int totalNodes = randomIntBetween(2, 5);
        IngestProxyActionFilter filter = buildFilter(randomIntBetween(1, totalNodes - 1), totalNodes);
        Answer<Void> answer = invocationOnMock -> {
            TransportResponseHandler transportResponseHandler = (TransportResponseHandler) invocationOnMock.getArguments()[3];
            transportResponseHandler.handleException(new TransportException(new IllegalArgumentException()));
            return null;
        };
        doAnswer(answer).when(transportService).sendRequest(any(DiscoveryNode.class), any(String.class), any(TransportRequest.class), any(TransportResponseHandler.class));

        String action;
        ActionRequest request;
        if (randomBoolean()) {
            action = IndexAction.NAME;
            request = new IndexRequest().pipeline("_id");
        } else {
            action = BulkAction.NAME;
            request = new BulkRequest().add(new IndexRequest().pipeline("_id"));
        }

        filter.apply(task, action, request, actionListener, actionFilterChain);

        verify(transportService).sendRequest(argThat(new IngestNodeMatcher()), eq(action), same(request), any(TransportResponseHandler.class));
        verifyZeroInteractions(actionFilterChain);
        verify(actionListener).onFailure(any(TransportException.class));
        verify(actionListener, never()).onResponse(any(TransportResponse.class));
    }

    private static class IngestNodeMatcher extends CustomTypeSafeMatcher<DiscoveryNode> {
        private IngestNodeMatcher() {
            super("discovery node should be an ingest node");
        }

        @Override
        protected boolean matchesSafely(DiscoveryNode node) {
            return NodeModule.isNodeIngestEnabled(node.getAttributes());
        }
    }
}
