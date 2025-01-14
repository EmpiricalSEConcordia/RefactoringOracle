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

package org.elasticsearch.cluster.node;

import org.elasticsearch.util.collect.ImmutableList;
import org.elasticsearch.util.collect.ImmutableMap;
import org.elasticsearch.util.collect.Maps;
import org.elasticsearch.util.io.stream.StreamInput;
import org.elasticsearch.util.io.stream.StreamOutput;
import org.elasticsearch.util.io.stream.Streamable;
import org.elasticsearch.util.settings.Settings;
import org.elasticsearch.util.transport.TransportAddress;
import org.elasticsearch.util.transport.TransportAddressSerializers;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import static org.elasticsearch.util.transport.TransportAddressSerializers.*;

/**
 * A discovery node represents a node that is part of the cluster.
 *
 * @author kimchy (shay.banon)
 */
public class DiscoveryNode implements Streamable, Serializable {

    public static Map<String, String> buildCommonNodesAttributes(Settings settings) {
        Map<String, String> attributes = Maps.newHashMap(settings.getByPrefix("node.").getAsMap());
        if (attributes.containsKey("client")) {
            if (attributes.get("client").equals("false")) {
                attributes.remove("client"); // this is the default
            } else {
                // if we are client node, don't store data ...
                attributes.put("data", "false");
            }
        }
        if (attributes.containsKey("data")) {
            if (attributes.get("data").equals("true")) {
                attributes.remove("data");
            }
        }
        return attributes;
    }

    public static final ImmutableList<DiscoveryNode> EMPTY_LIST = ImmutableList.of();

    private String nodeName = "".intern();

    private String nodeId;

    private TransportAddress address;

    private ImmutableMap<String, String> attributes;

    private DiscoveryNode() {
    }

    public DiscoveryNode(String nodeId, TransportAddress address) {
        this("", nodeId, address, ImmutableMap.<String, String>of());
    }

    public DiscoveryNode(String nodeName, String nodeId, TransportAddress address, Map<String, String> attributes) {
        if (nodeName == null) {
            this.nodeName = "".intern();
        } else {
            this.nodeName = nodeName.intern();
        }
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            builder.put(entry.getKey().intern(), entry.getValue().intern());
        }
        this.attributes = builder.build();
        this.nodeId = nodeId.intern();
        this.address = address;
    }

    /**
     * The address that the node can be communicated with.
     */
    public TransportAddress address() {
        return address;
    }

    /**
     * The address that the node can be communicated with.
     */
    public TransportAddress getAddress() {
        return address();
    }

    /**
     * The unique id of the node.
     */
    public String id() {
        return nodeId;
    }

    /**
     * The unique id of the node.
     */
    public String getId() {
        return id();
    }

    /**
     * The name of the node.
     */
    public String name() {
        return this.nodeName;
    }

    /**
     * The name of the node.
     */
    public String getName() {
        return name();
    }

    /**
     * The node attributes.
     */
    public ImmutableMap<String, String> attributes() {
        return this.attributes;
    }

    /**
     * The node attributes.
     */
    public ImmutableMap<String, String> getAttributes() {
        return attributes();
    }

    /**
     * Should this node hold data (shards) or not.
     */
    public boolean dataNode() {
        String data = attributes.get("data");
        return data == null || data.equals("true");
    }

    /**
     * Should this node hold data (shards) or not.
     */
    public boolean isDataNode() {
        return dataNode();
    }

    /**
     * Is the node a client node or not.
     */
    public boolean clientNode() {
        String client = attributes.get("client");
        return client != null && client.equals("true");
    }

    public boolean isClientNode() {
        return clientNode();
    }

    public static DiscoveryNode readNode(StreamInput in) throws IOException {
        DiscoveryNode node = new DiscoveryNode();
        node.readFrom(in);
        return node;
    }

    @Override public void readFrom(StreamInput in) throws IOException {
        nodeName = in.readUTF().intern();
        nodeId = in.readUTF().intern();
        address = TransportAddressSerializers.addressFromStream(in);
        int size = in.readVInt();
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (int i = 0; i < size; i++) {
            builder.put(in.readUTF().intern(), in.readUTF().intern());
        }
        attributes = builder.build();
    }

    @Override public void writeTo(StreamOutput out) throws IOException {
        out.writeUTF(nodeName);
        out.writeUTF(nodeId);
        addressToStream(out, address);
        out.writeVInt(attributes.size());
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeUTF(entry.getValue());
        }
    }

    @Override public boolean equals(Object obj) {
        if (!(obj instanceof DiscoveryNode))
            return false;

        DiscoveryNode other = (DiscoveryNode) obj;
        return this.nodeId.equals(other.nodeId);
    }

    @Override public int hashCode() {
        return nodeId.hashCode();
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        if (nodeName.length() > 0) {
            sb.append('[').append(nodeName).append(']');
        }
        if (nodeId != null) {
            sb.append('[').append(nodeId).append(']');
        }
        if (address != null) {
            sb.append('[').append(address).append(']');
        }
        if (!attributes.isEmpty()) {
            sb.append(attributes);
        }
        return sb.toString();
    }
}
