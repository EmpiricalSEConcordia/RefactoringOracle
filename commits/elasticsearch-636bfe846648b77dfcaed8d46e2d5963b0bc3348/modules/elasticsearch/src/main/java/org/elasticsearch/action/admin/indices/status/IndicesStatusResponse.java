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

package org.elasticsearch.action.admin.indices.status;

import org.elasticsearch.action.ShardOperationFailedException;
import org.elasticsearch.action.support.broadcast.BroadcastOperationResponse;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.util.io.stream.StreamInput;
import org.elasticsearch.util.io.stream.StreamOutput;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.action.admin.indices.status.ShardStatus.*;
import static org.elasticsearch.common.collect.Lists.*;
import static org.elasticsearch.common.collect.Maps.*;
import static org.elasticsearch.common.settings.ImmutableSettings.*;

/**
 * @author kimchy (shay.banon)
 */
public class IndicesStatusResponse extends BroadcastOperationResponse {

    protected ShardStatus[] shards;

    private Map<String, Settings> indicesSettings = ImmutableMap.of();

    private Map<String, IndexStatus> indicesStatus;

    IndicesStatusResponse() {
    }

    IndicesStatusResponse(ShardStatus[] shards, ClusterState clusterState, int totalShards, int successfulShards, int failedShards, List<ShardOperationFailedException> shardFailures) {
        super(totalShards, successfulShards, failedShards, shardFailures);
        this.shards = shards;
        indicesSettings = newHashMap();
        for (ShardStatus shard : shards) {
            if (!indicesSettings.containsKey(shard.shardRouting().index())) {
                indicesSettings.put(shard.shardRouting().index(), clusterState.metaData().index(shard.shardRouting().index()).settings());
            }
        }
    }

    public ShardStatus[] shards() {
        return this.shards;
    }

    public ShardStatus[] getShards() {
        return this.shards;
    }

    public ShardStatus getAt(int position) {
        return shards[position];
    }

    public IndexStatus index(String index) {
        return indices().get(index);
    }

    public Map<String, IndexStatus> getIndices() {
        return indices();
    }

    public Map<String, IndexStatus> indices() {
        if (indicesStatus != null) {
            return indicesStatus;
        }
        Map<String, IndexStatus> indicesStatus = newHashMap();
        for (String index : indicesSettings.keySet()) {
            List<ShardStatus> shards = newArrayList();
            for (ShardStatus shard : shards()) {
                if (shard.shardRouting().index().equals(index)) {
                    shards.add(shard);
                }
            }
            indicesStatus.put(index, new IndexStatus(index, indicesSettings.get(index), shards.toArray(new ShardStatus[shards.size()])));
        }
        this.indicesStatus = indicesStatus;
        return indicesStatus;
    }

    @Override public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeVInt(shards().length);
        for (ShardStatus status : shards()) {
            status.writeTo(out);
        }
        out.writeVInt(indicesSettings.size());
        for (Map.Entry<String, Settings> entry : indicesSettings.entrySet()) {
            out.writeUTF(entry.getKey());
            writeSettingsToStream(entry.getValue(), out);
        }
    }

    @Override public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        shards = new ShardStatus[in.readVInt()];
        for (int i = 0; i < shards.length; i++) {
            shards[i] = readIndexShardStatus(in);
        }
        indicesSettings = newHashMap();
        int size = in.readVInt();
        for (int i = 0; i < size; i++) {
            indicesSettings.put(in.readUTF(), readSettingsFromStream(in));
        }
    }
}
