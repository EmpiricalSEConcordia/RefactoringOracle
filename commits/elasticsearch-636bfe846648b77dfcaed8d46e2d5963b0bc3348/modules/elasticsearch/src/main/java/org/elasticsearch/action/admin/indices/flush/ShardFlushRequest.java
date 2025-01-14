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

package org.elasticsearch.action.admin.indices.flush;

import org.elasticsearch.action.support.broadcast.BroadcastShardOperationRequest;
import org.elasticsearch.util.io.stream.StreamInput;
import org.elasticsearch.util.io.stream.StreamOutput;

import java.io.IOException;

/**
 * @author kimchy (shay.banon)
 */
class ShardFlushRequest extends BroadcastShardOperationRequest {

    private boolean refresh;

    private boolean full;

    ShardFlushRequest() {
    }

    public ShardFlushRequest(String index, int shardId, FlushRequest request) {
        super(index, shardId);
        this.refresh = request.refresh();
        this.full = request.full();
    }

    public boolean refresh() {
        return this.refresh;
    }

    public boolean full() {
        return this.full;
    }

    @Override public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        refresh = in.readBoolean();
        full = in.readBoolean();
    }

    @Override public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeBoolean(refresh);
        out.writeBoolean(full);
    }
}