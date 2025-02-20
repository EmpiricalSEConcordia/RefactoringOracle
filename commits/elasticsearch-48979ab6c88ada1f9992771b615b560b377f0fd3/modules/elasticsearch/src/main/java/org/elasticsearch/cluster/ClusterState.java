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

package org.elasticsearch.cluster;

import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.RoutingNodes;
import org.elasticsearch.cluster.routing.RoutingTable;
import org.elasticsearch.util.io.stream.BytesStreamInput;
import org.elasticsearch.util.io.stream.BytesStreamOutput;
import org.elasticsearch.util.io.stream.StreamInput;
import org.elasticsearch.util.io.stream.StreamOutput;
import org.elasticsearch.util.settings.Settings;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * @author kimchy (shay.banon)
 */
public class ClusterState {

    private final long version;

    private final RoutingTable routingTable;

    private final DiscoveryNodes nodes;

    private final MetaData metaData;

    // built on demand
    private volatile RoutingNodes routingNodes;

    public ClusterState(long version, MetaData metaData, RoutingTable routingTable, DiscoveryNodes nodes) {
        this.version = version;
        this.metaData = metaData;
        this.routingTable = routingTable;
        this.nodes = nodes;
    }

    public long version() {
        return this.version;
    }

    public long getVersion() {
        return version();
    }

    public DiscoveryNodes nodes() {
        return this.nodes;
    }

    public DiscoveryNodes getNodes() {
        return nodes();
    }

    public MetaData metaData() {
        return this.metaData;
    }

    public MetaData getMetaData() {
        return metaData();
    }

    public RoutingTable routingTable() {
        return routingTable;
    }

    public RoutingTable getRoutingTable() {
        return routingTable();
    }

    public RoutingNodes routingNodes() {
        return routingTable.routingNodes(metaData);
    }

    public RoutingNodes getRoutingNodes() {
        return readOnlyRoutingNodes();
    }

    /**
     * Returns a built (on demand) routing nodes view of the routing table. <b>NOTE, the routing nodes
     * are mutable, use them just for read operations</b>
     */
    public RoutingNodes readOnlyRoutingNodes() {
        if (routingNodes != null) {
            return routingNodes;
        }
        routingNodes = routingTable.routingNodes(metaData);
        return routingNodes;
    }

    public static Builder newClusterStateBuilder() {
        return new Builder();
    }

    public static class Builder {

        private long version = 0;

        private MetaData metaData = MetaData.EMPTY_META_DATA;

        private RoutingTable routingTable = RoutingTable.EMPTY_ROUTING_TABLE;

        private DiscoveryNodes nodes = DiscoveryNodes.EMPTY_NODES;

        public Builder nodes(DiscoveryNodes.Builder nodesBuilder) {
            return nodes(nodesBuilder.build());
        }

        public Builder nodes(DiscoveryNodes nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder routingTable(RoutingTable.Builder routingTable) {
            return routingTable(routingTable.build());
        }

        public Builder routingTable(RoutingTable routingTable) {
            this.routingTable = routingTable;
            return this;
        }

        public Builder metaData(MetaData.Builder metaDataBuilder) {
            return metaData(metaDataBuilder.build());
        }

        public Builder metaData(MetaData metaData) {
            this.metaData = metaData;
            return this;
        }

        public Builder state(ClusterState state) {
            this.version = state.version();
            this.nodes = state.nodes();
            this.routingTable = state.routingTable();
            this.metaData = state.metaData();
            return this;
        }

        public ClusterState build() {
            return new ClusterState(version, metaData, routingTable, nodes);
        }

        public static byte[] toBytes(ClusterState state) throws IOException {
            BytesStreamOutput os = BytesStreamOutput.Cached.cached();
            writeTo(state, os);
            return os.copiedByteArray();
        }

        public static ClusterState fromBytes(byte[] data, Settings globalSettings, DiscoveryNode localNode) throws IOException {
            return readFrom(new BytesStreamInput(data), globalSettings, localNode);
        }

        public static void writeTo(ClusterState state, StreamOutput out) throws IOException {
            out.writeLong(state.version());
            MetaData.Builder.writeTo(state.metaData(), out);
            RoutingTable.Builder.writeTo(state.routingTable(), out);
            DiscoveryNodes.Builder.writeTo(state.nodes(), out);
        }

        public static ClusterState readFrom(StreamInput in, @Nullable Settings globalSettings, @Nullable DiscoveryNode localNode) throws IOException {
            Builder builder = new Builder();
            builder.version = in.readLong();
            builder.metaData = MetaData.Builder.readFrom(in, globalSettings);
            builder.routingTable = RoutingTable.Builder.readFrom(in);
            builder.nodes = DiscoveryNodes.Builder.readFrom(in, localNode);
            return builder.build();
        }
    }
}
