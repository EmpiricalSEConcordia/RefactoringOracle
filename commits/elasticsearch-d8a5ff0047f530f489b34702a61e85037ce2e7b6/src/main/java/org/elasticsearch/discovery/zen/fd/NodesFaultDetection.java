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

package org.elasticsearch.discovery.zen.fd;

import org.elasticsearch.ElasticsearchIllegalStateException;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.*;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.elasticsearch.cluster.node.DiscoveryNodes.EMPTY_NODES;
import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;
import static org.elasticsearch.common.util.concurrent.ConcurrentCollections.newConcurrentMap;
import static org.elasticsearch.transport.TransportRequestOptions.options;

/**
 * A fault detection of multiple nodes.
 */
public class NodesFaultDetection extends AbstractComponent {

    public static final String PING_ACTION_NAME = "internal:discovery/zen/fd/ping";
    
    public abstract static class Listener {

        public void onNodeFailure(DiscoveryNode node, String reason) {}

        public void onPingReceived(PingRequest pingRequest) {}

    }

    private final ThreadPool threadPool;

    private final TransportService transportService;
    private final ClusterName clusterName;


    private final boolean connectOnNetworkDisconnect;

    private final TimeValue pingInterval;

    private final TimeValue pingRetryTimeout;

    private final int pingRetryCount;

    // used mainly for testing, should always be true
    private final boolean registerConnectionListener;


    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private final ConcurrentMap<DiscoveryNode, NodeFD> nodesFD = newConcurrentMap();

    private final FDConnectionListener connectionListener;

    private volatile DiscoveryNodes latestNodes = EMPTY_NODES;

    private volatile long clusterStateVersion = ClusterState.UNKNOWN_VERSION;

    private volatile boolean running = false;

    public NodesFaultDetection(Settings settings, ThreadPool threadPool, TransportService transportService, ClusterName clusterName) {
        super(settings);
        this.threadPool = threadPool;
        this.transportService = transportService;
        this.clusterName = clusterName;

        this.connectOnNetworkDisconnect = componentSettings.getAsBoolean("connect_on_network_disconnect", false);
        this.pingInterval = componentSettings.getAsTime("ping_interval", timeValueSeconds(1));
        this.pingRetryTimeout = componentSettings.getAsTime("ping_timeout", timeValueSeconds(30));
        this.pingRetryCount = componentSettings.getAsInt("ping_retries", 3);
        this.registerConnectionListener = componentSettings.getAsBoolean("register_connection_listener", true);

        logger.debug("[node  ] uses ping_interval [{}], ping_timeout [{}], ping_retries [{}]", pingInterval, pingRetryTimeout, pingRetryCount);

        transportService.registerHandler(PING_ACTION_NAME, new PingRequestHandler());

        this.connectionListener = new FDConnectionListener();
        if (registerConnectionListener) {
            transportService.addConnectionListener(connectionListener);
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void updateNodes(DiscoveryNodes nodes, long clusterStateVersion) {
        DiscoveryNodes prevNodes = latestNodes;
        this.latestNodes = nodes;
        this.clusterStateVersion = clusterStateVersion;
        if (!running) {
            return;
        }
        DiscoveryNodes.Delta delta = nodes.delta(prevNodes);
        for (DiscoveryNode newNode : delta.addedNodes()) {
            if (newNode.id().equals(nodes.localNodeId())) {
                // no need to monitor the local node
                continue;
            }
            if (!nodesFD.containsKey(newNode)) {
                nodesFD.put(newNode, new NodeFD());
                // we use schedule with a 0 time value to run the pinger on the pool as it will run on later
                threadPool.schedule(TimeValue.timeValueMillis(0), ThreadPool.Names.SAME, new SendPingRequest(newNode));
            }
        }
        for (DiscoveryNode removedNode : delta.removedNodes()) {
            nodesFD.remove(removedNode);
        }
    }

    public NodesFaultDetection start() {
        if (running) {
            return this;
        }
        running = true;
        return this;
    }

    public NodesFaultDetection stop() {
        if (!running) {
            return this;
        }
        running = false;
        return this;
    }

    public void close() {
        stop();
        transportService.removeHandler(PING_ACTION_NAME);
        transportService.removeConnectionListener(connectionListener);
    }

    private void handleTransportDisconnect(DiscoveryNode node) {
        if (!latestNodes.nodeExists(node.id())) {
            return;
        }
        NodeFD nodeFD = nodesFD.remove(node);
        if (nodeFD == null) {
            return;
        }
        if (!running) {
            return;
        }
        nodeFD.running = false;
        if (connectOnNetworkDisconnect) {
            try {
                transportService.connectToNode(node);
                nodesFD.put(node, new NodeFD());
                // we use schedule with a 0 time value to run the pinger on the pool as it will run on later
                threadPool.schedule(TimeValue.timeValueMillis(0), ThreadPool.Names.SAME, new SendPingRequest(node));
            } catch (Exception e) {
                logger.trace("[node  ] [{}] transport disconnected (with verified connect)", node);
                notifyNodeFailure(node, "transport disconnected (with verified connect)");
            }
        } else {
            logger.trace("[node  ] [{}] transport disconnected", node);
            notifyNodeFailure(node, "transport disconnected");
        }
    }

    private void notifyNodeFailure(final DiscoveryNode node, final String reason) {
        threadPool.generic().execute(new Runnable() {
            @Override
            public void run() {
                for (Listener listener : listeners) {
                    listener.onNodeFailure(node, reason);
                }
            }
        });
    }

    private void notifyPingReceived(final PingRequest pingRequest) {
        threadPool.generic().execute(new Runnable() {

            @Override
            public void run() {
                for (Listener listener : listeners) {
                    listener.onPingReceived(pingRequest);
                }
            }

        });
    }

    private class SendPingRequest implements Runnable {

        private final DiscoveryNode node;

        private SendPingRequest(DiscoveryNode node) {
            this.node = node;
        }

        @Override
        public void run() {
            if (!running) {
                return;
            }
            final PingRequest pingRequest = new PingRequest(node.id(), clusterName, latestNodes.localNode(), clusterStateVersion);
            final TransportRequestOptions options = options().withType(TransportRequestOptions.Type.PING).withTimeout(pingRetryTimeout);
            transportService.sendRequest(node, PING_ACTION_NAME, pingRequest, options, new BaseTransportResponseHandler<PingResponse>() {
                        @Override
                        public PingResponse newInstance() {
                            return new PingResponse();
                        }

                        @Override
                        public void handleResponse(PingResponse response) {
                            if (!running) {
                                return;
                            }
                            NodeFD nodeFD = nodesFD.get(node);
                            if (nodeFD != null) {
                                if (!nodeFD.running) {
                                    return;
                                }
                                nodeFD.retryCount = 0;
                                threadPool.schedule(pingInterval, ThreadPool.Names.SAME, SendPingRequest.this);
                            }
                        }

                        @Override
                        public void handleException(TransportException exp) {
                            // check if the master node did not get switched on us...
                            if (!running) {
                                return;
                            }
                            NodeFD nodeFD = nodesFD.get(node);
                            if (nodeFD != null) {
                                if (!nodeFD.running) {
                                    return;
                                }
                                if (exp instanceof ConnectTransportException || exp.getCause() instanceof ConnectTransportException) {
                                    handleTransportDisconnect(node);
                                    return;
                                }

                                int retryCount = ++nodeFD.retryCount;
                                logger.trace("[node  ] failed to ping [{}], retry [{}] out of [{}]", exp, node, retryCount, pingRetryCount);
                                if (retryCount >= pingRetryCount) {
                                    logger.debug("[node  ] failed to ping [{}], tried [{}] times, each with  maximum [{}] timeout", node, pingRetryCount, pingRetryTimeout);
                                    // not good, failure
                                    if (nodesFD.remove(node) != null) {
                                        notifyNodeFailure(node, "failed to ping, tried [" + pingRetryCount + "] times, each with maximum [" + pingRetryTimeout + "] timeout");
                                    }
                                } else {
                                    // resend the request, not reschedule, rely on send timeout
                                    transportService.sendRequest(node, PING_ACTION_NAME, pingRequest, options, this);
                                }
                            }
                        }

                        @Override
                        public String executor() {
                            return ThreadPool.Names.SAME;
                        }
                    }
            );
        }
    }

    static class NodeFD {
        volatile int retryCount;
        volatile boolean running = true;
    }

    private class FDConnectionListener implements TransportConnectionListener {
        @Override
        public void onNodeConnected(DiscoveryNode node) {
        }

        @Override
        public void onNodeDisconnected(DiscoveryNode node) {
            handleTransportDisconnect(node);
        }
    }


    class PingRequestHandler extends BaseTransportRequestHandler<PingRequest> {

        @Override
        public PingRequest newInstance() {
            return new PingRequest();
        }

        @Override
        public void messageReceived(PingRequest request, TransportChannel channel) throws Exception {
            // if we are not the node we are supposed to be pinged, send an exception
            // this can happen when a kill -9 is sent, and another node is started using the same port
            if (!latestNodes.localNodeId().equals(request.nodeId)) {
                throw new ElasticsearchIllegalStateException("Got pinged as node [" + request.nodeId + "], but I am node [" + latestNodes.localNodeId() + "]");
            }

            // PingRequest will have clusterName set to null if it came from a node of version <1.4.0
            if (request.clusterName != null && !request.clusterName.equals(clusterName)) {
                // Don't introduce new exception for bwc reasons
                throw new ElasticsearchIllegalStateException("Got pinged with cluster name [" + request.clusterName + "], but I'm part of cluster [" + clusterName + "]");
            }

            notifyPingReceived(request);

            channel.sendResponse(new PingResponse());
        }

        @Override
        public String executor() {
            return ThreadPool.Names.SAME;
        }
    }


    public static class PingRequest extends TransportRequest {

        // the (assumed) node id we are pinging
        private String nodeId;

        private ClusterName clusterName;

        private DiscoveryNode masterNode;

        private long clusterStateVersion = ClusterState.UNKNOWN_VERSION;

        PingRequest() {
        }

        PingRequest(String nodeId, ClusterName clusterName, DiscoveryNode masterNode, long clusterStateVersion) {
            this.nodeId = nodeId;
            this.clusterName = clusterName;
            this.masterNode = masterNode;
            this.clusterStateVersion = clusterStateVersion;
        }

        public String nodeId() {
            return nodeId;
        }

        public ClusterName clusterName() {
            return clusterName;
        }

        public DiscoveryNode masterNode() {
            return masterNode;
        }

        public long clusterStateVersion() {
            return clusterStateVersion;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            nodeId = in.readString();
            if (in.getVersion().onOrAfter(Version.V_1_4_0)) {
                clusterName = ClusterName.readClusterName(in);
                masterNode = DiscoveryNode.readNode(in);
                clusterStateVersion = in.readLong();
            }
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(nodeId);
            if (out.getVersion().onOrAfter(Version.V_1_4_0)) {
                clusterName.writeTo(out);
                masterNode.writeTo(out);
                out.writeLong(clusterStateVersion);
            }
        }
    }

    private static class PingResponse extends TransportResponse {

        private PingResponse() {
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
        }
    }
}
