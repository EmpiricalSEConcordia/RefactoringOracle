/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
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

package org.elasticsearch.action.get;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.single.shard.SingleShardOperationRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.internal.InternalClient;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.index.VersionType;

/**
 * A get document action request builder.
 */
public class GetRequestBuilder extends SingleShardOperationRequestBuilder<GetRequest, GetResponse, GetRequestBuilder> {

    public GetRequestBuilder(Client client) {
        super((InternalClient) client, new GetRequest());
    }

    public GetRequestBuilder(Client client, @Nullable String index) {
        super((InternalClient) client, new GetRequest(index));
    }

    /**
     * Sets the type of the document to fetch. If set to <tt>null</tt>, will use just the id to fetch the
     * first document matching it.
     */
    public GetRequestBuilder setType(@Nullable String type) {
        request.type(type);
        return this;
    }

    /**
     * Sets the id of the document to fetch.
     */
    public GetRequestBuilder setId(String id) {
        request.id(id);
        return this;
    }

    /**
     * Sets the parent id of this document. Will simply set the routing to this value, as it is only
     * used for routing with delete requests.
     */
    public GetRequestBuilder setParent(String parent) {
        request.parent(parent);
        return this;
    }

    /**
     * Controls the shard routing of the request. Using this value to hash the shard
     * and not the id.
     */
    public GetRequestBuilder setRouting(String routing) {
        request.routing(routing);
        return this;
    }

    /**
     * Sets the preference to execute the search. Defaults to randomize across shards. Can be set to
     * <tt>_local</tt> to prefer local shards, <tt>_primary</tt> to execute only on primary shards, or
     * a custom value, which guarantees that the same order will be used across different requests.
     */
    public GetRequestBuilder setPreference(String preference) {
        request.preference(preference);
        return this;
    }

    /**
     * Explicitly specify the fields that will be returned. By default, the <tt>_source</tt>
     * field will be returned.
     */
    public GetRequestBuilder setFields(String... fields) {
        request.fields(fields);
        return this;
    }

    /**
     * Should a refresh be executed before this get operation causing the operation to
     * return the latest value. Note, heavy get should not set this to <tt>true</tt>. Defaults
     * to <tt>false</tt>.
     */
    public GetRequestBuilder setRefresh(boolean refresh) {
        request.refresh(refresh);
        return this;
    }

    public GetRequestBuilder setRealtime(Boolean realtime) {
        request.realtime(realtime);
        return this;
    }

    /**
     * Sets the version, which will cause the get operation to only be performed if a matching
     * version exists and no changes happened on the doc since then.
     */
    public GetRequestBuilder setVersion(long version) {
        request.version(version);
        return this;
    }

    /**
     * Sets the versioning type. Defaults to {@link org.elasticsearch.index.VersionType#INTERNAL}.
     */
    public GetRequestBuilder setVersionType(VersionType versionType) {
        request.versionType(versionType);
        return this;
    }

    @Override
    protected void doExecute(ActionListener<GetResponse> listener) {
        ((Client) client).get(request, listener);
    }
}
