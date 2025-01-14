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

package org.elasticsearch.discovery.zen.fd;

import org.elasticsearch.ElasticSearchIllegalStateException;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.discovery.zen.DiscoveryNodesProvider;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.*;
import org.elasticsearch.util.TimeValue;
import org.elasticsearch.util.io.stream.StreamInput;
import org.elasticsearch.util.io.stream.StreamOutput;
import org.elasticsearch.util.io.stream.Streamable;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.elasticsearch.util.TimeValue.*;

/**
 * A fault detection that pings the master periodically to see if its alive.
 *
 * @author kimchy (shay.banon)
 */
public class MasterFaultDetection extends AbstractComponent {

    public static interface Listener {

        void onMasterFailure(DiscoveryNode masterNode, String reason);

        void onDisconnectedFromMaster();
    }

    private final ThreadPool threadPool;

    private final TransportService transportService;

    private final DiscoveryNodesProvider nodesProvider;

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();


    private final boolean connectOnNetworkDisconnect;

    private final TimeValue pingInterval;

    private final TimeValue pingRetryTimeout;

    private final int pingRetryCount;

    private final FDConnectionListener connectionListener;

    private volatile DiscoveryNode masterNode;

    private volatile int retryCount;

    private final AtomicBoolean notifiedMasterFailure = new AtomicBoolean();

    public MasterFaultDetection(Settings settings, ThreadPool threadPool, TransportService transportService, DiscoveryNodesProvider nodesProvider) {
        super(settings);
        this.threadPool = threadPool;
        this.transportService = transportService;
        this.nodesProvider = nodesProvider;

        this.connectOnNetworkDisconnect = componentSettings.getAsBoolean("connect_on_network_disconnect", false);
        this.pingInterval = componentSettings.getAsTime("ping_interval", timeValueSeconds(1));
        this.pingRetryTimeout = componentSettings.getAsTime("ping_timeout", timeValueSeconds(30));
        this.pingRetryCount = componentSettings.getAsInt("ping_retries", 3);

        logger.debug("Master FD uses ping_interval [{}], ping_timeout [{}], ping_retries [{}]", pingInterval, pingRetryTimeout, pingRetryCount);

        this.connectionListener = new FDConnectionListener();
        transportService.addConnectionListener(connectionListener);

        transportService.registerHandler(MasterPingRequestHandler.ACTION, new MasterPingRequestHandler());
    }

    public DiscoveryNode masterNode() {
        return this.masterNode;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void restart(DiscoveryNode masterNode) {
        stop();
        start(masterNode);
    }

    public void start(DiscoveryNode masterNode) {
        this.masterNode = masterNode;
        this.retryCount = 0;
        this.notifiedMasterFailure.set(false);

        // try and connect to make sure we are connected
        try {
            transportService.connectToNode(masterNode);
        } catch (Exception e) {
            notifyMasterFailure(masterNode, "failed to perform initial connect [" + e.getMessage() + "]");
        }

        // start the ping process
        threadPool.schedule(new SendPingRequest(), pingInterval);
    }

    public void stop() {
        // also will stop the next ping schedule
        this.retryCount = 0;
        this.masterNode = null;
    }

    public void close() {
        stop();
        this.listeners.clear();
        transportService.removeConnectionListener(connectionListener);
        transportService.removeHandler(MasterPingRequestHandler.ACTION);
    }

    private void handleTransportDisconnect(DiscoveryNode node) {
        if (!node.equals(this.masterNode)) {
            return;
        }
        if (connectOnNetworkDisconnect) {
            try {
                transportService.connectToNode(node);
            } catch (Exception e) {
                logger.trace("Master [{}] failed on disconnect (with verified connect)", masterNode);
                notifyMasterFailure(masterNode, "Failed on disconnect (with verified connect)");
            }
        } else {
            logger.trace("Master [{}] failed on disconnect", masterNode);
            notifyMasterFailure(masterNode, "Failed on disconnect");
        }
    }

    private void notifyDisconnectedFromMaster() {
        for (Listener listener : listeners) {
            listener.onDisconnectedFromMaster();
        }
        // we don't stop on disconnection from master, we keep pinging it
    }

    private void notifyMasterFailure(DiscoveryNode masterNode, String reason) {
        if (notifiedMasterFailure.compareAndSet(false, true)) {
            for (Listener listener : listeners) {
                listener.onMasterFailure(masterNode, reason);
            }
            stop();
        }
    }

    private class FDConnectionListener implements TransportConnectionListener {
        @Override public void onNodeConnected(DiscoveryNode node) {
        }

        @Override public void onNodeDisconnected(DiscoveryNode node) {
            handleTransportDisconnect(node);
        }
    }

    private class SendPingRequest implements Runnable {
        @Override public void run() {
            if (masterNode != null) {
                final DiscoveryNode sentToNode = masterNode;
                transportService.sendRequest(masterNode, MasterPingRequestHandler.ACTION, new MasterPingRequest(nodesProvider.nodes().localNode().id(), sentToNode.id()), pingRetryTimeout,
                        new BaseTransportResponseHandler<MasterPingResponseResponse>() {
                            @Override public MasterPingResponseResponse newInstance() {
                                return new MasterPingResponseResponse();
                            }

                            @Override public void handleResponse(MasterPingResponseResponse response) {
                                // reset the counter, we got a good result
                                MasterFaultDetection.this.retryCount = 0;
                                // check if the master node did not get switched on us...
                                if (sentToNode.equals(MasterFaultDetection.this.masterNode())) {
                                    if (!response.connectedToMaster) {
                                        logger.trace("Master [{}] does not have us registered with it...", masterNode);
                                        notifyDisconnectedFromMaster();
                                    } else {
                                        threadPool.schedule(SendPingRequest.this, pingInterval);
                                    }
                                }
                            }

                            @Override public void handleException(RemoteTransportException exp) {
                                // check if the master node did not get switched on us...
                                if (sentToNode.equals(MasterFaultDetection.this.masterNode())) {
                                    int retryCount = ++MasterFaultDetection.this.retryCount;
                                    logger.trace("Master [{}] failed to ping, retry [{}] out of [{}]", exp, masterNode, retryCount, pingRetryCount);
                                    if (retryCount >= pingRetryCount) {
                                        logger.debug("Master [{}] failed on ping, tried [{}] times, each with [{}] timeout", masterNode, pingRetryCount, pingRetryTimeout);
                                        // not good, failure
                                        notifyMasterFailure(sentToNode, "Failed on ping, tried [" + pingRetryCount + "] times, each with [" + pingRetryTimeout + "] timeout");
                                    } else {
                                        // resend the request, not reschedule, rely on send timeout
                                        transportService.sendRequest(sentToNode, MasterPingRequestHandler.ACTION, new MasterPingRequest(nodesProvider.nodes().localNode().id(), sentToNode.id()), pingRetryTimeout, this);
                                    }
                                }
                            }
                        });
            }
        }
    }

    private class MasterPingRequestHandler extends BaseTransportRequestHandler<MasterPingRequest> {

        public static final String ACTION = "discovery/zen/fd/masterPing";

        @Override public MasterPingRequest newInstance() {
            return new MasterPingRequest();
        }

        @Override public void messageReceived(MasterPingRequest request, TransportChannel channel) throws Exception {
            DiscoveryNodes nodes = nodesProvider.nodes();
            // check if we are really the same master as the one we seemed to be think we are
            // this can happen if the master got "kill -9" and then another node started using the same port
            if (!request.masterNodeId.equals(nodes.localNodeId())) {
                throw new ElasticSearchIllegalStateException("Got ping as master with id [" + request.masterNodeId + "], but not master and no id");
            }
            // send a response, and note if we are connected to the master or not
            channel.sendResponse(new MasterPingResponseResponse(nodes.nodeExists(request.nodeId)));
        }
    }


    private static class MasterPingRequest implements Streamable {

        private String nodeId;

        private String masterNodeId;

        private MasterPingRequest() {
        }

        private MasterPingRequest(String nodeId, String masterNodeId) {
            this.nodeId = nodeId;
            this.masterNodeId = masterNodeId;
        }

        @Override public void readFrom(StreamInput in) throws IOException {
            nodeId = in.readUTF();
            masterNodeId = in.readUTF();
        }

        @Override public void writeTo(StreamOutput out) throws IOException {
            out.writeUTF(nodeId);
            out.writeUTF(masterNodeId);
        }
    }

    private static class MasterPingResponseResponse implements Streamable {

        private boolean connectedToMaster;

        private MasterPingResponseResponse() {
        }

        private MasterPingResponseResponse(boolean connectedToMaster) {
            this.connectedToMaster = connectedToMaster;
        }

        @Override public void readFrom(StreamInput in) throws IOException {
            connectedToMaster = in.readBoolean();
        }

        @Override public void writeTo(StreamOutput out) throws IOException {
            out.writeBoolean(connectedToMaster);
        }
    }
}
