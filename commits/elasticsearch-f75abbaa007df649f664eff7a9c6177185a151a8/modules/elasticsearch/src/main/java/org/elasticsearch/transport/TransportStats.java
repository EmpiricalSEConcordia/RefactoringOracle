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

package org.elasticsearch.transport;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.unit.SizeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.builder.XContentBuilder;

import java.io.IOException;
import java.io.Serializable;

/**
 * @author kimchy (shay.banon)
 */
public class TransportStats implements Streamable, Serializable, ToXContent {

    private long rxCount;

    private long rxSize;

    private long txCount;

    private long txSize;

    TransportStats() {
    }

    public TransportStats(long rxCount, long rxSize, long txCount, long txSize) {
        this.rxCount = rxCount;
        this.rxSize = rxSize;
        this.txCount = txCount;
        this.txSize = txSize;
    }

    @Override public void toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("transport");
        builder.field("rx_count", rxCount);
        builder.field("rx_size", rxSize().toString());
        builder.field("rx_size_in_bytes", rxSize);
        builder.field("tx_count", txCount);
        builder.field("tx_size", txSize().toString());
        builder.field("tx_size_in_bytes", txSize);
        builder.endObject();
    }

    public static TransportStats readTransportStats(StreamInput in) throws IOException {
        TransportStats stats = new TransportStats();
        stats.readFrom(in);
        return stats;
    }

    @Override public void readFrom(StreamInput in) throws IOException {
        rxCount = in.readVLong();
        rxSize = in.readVLong();
        txCount = in.readVLong();
        txSize = in.readVLong();
    }

    @Override public void writeTo(StreamOutput out) throws IOException {
        out.writeVLong(rxCount);
        out.writeVLong(rxSize);
        out.writeVLong(txCount);
        out.writeVLong(txSize);
    }

    public long rxCount() {
        return rxCount;
    }

    public long getRxCount() {
        return rxCount();
    }

    public SizeValue rxSize() {
        return new SizeValue(rxSize);
    }

    public SizeValue getRxSize() {
        return rxSize();
    }

    public long txCount() {
        return txCount;
    }

    public long getTxCount() {
        return txCount();
    }

    public SizeValue txSize() {
        return new SizeValue(txSize);
    }

    public SizeValue getTxSize() {
        return txSize();
    }
}
