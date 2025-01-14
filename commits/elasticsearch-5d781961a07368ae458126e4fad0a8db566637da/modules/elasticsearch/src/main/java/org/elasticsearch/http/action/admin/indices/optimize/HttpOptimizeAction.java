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

package org.elasticsearch.http.action.admin.indices.optimize;

import com.google.inject.Inject;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.optimize.OptimizeRequest;
import org.elasticsearch.action.admin.indices.optimize.OptimizeResponse;
import org.elasticsearch.action.support.broadcast.BroadcastOperationThreading;
import org.elasticsearch.client.Client;
import org.elasticsearch.http.*;
import org.elasticsearch.http.action.support.HttpActions;
import org.elasticsearch.http.action.support.HttpJsonBuilder;
import org.elasticsearch.util.json.JsonBuilder;
import org.elasticsearch.util.settings.Settings;

import java.io.IOException;

import static org.elasticsearch.http.HttpResponse.Status.*;

/**
 * @author kimchy (Shay Banon)
 */
public class HttpOptimizeAction extends BaseHttpServerHandler {

    @Inject public HttpOptimizeAction(Settings settings, HttpServer httpService, Client client) {
        super(settings, client);
        httpService.registerHandler(HttpRequest.Method.POST, "/_optimize", this);
        httpService.registerHandler(HttpRequest.Method.POST, "/{index}/_optimize", this);
    }

    @Override public void handleRequest(final HttpRequest request, final HttpChannel channel) {
        OptimizeRequest optimizeRequest = new OptimizeRequest(HttpActions.splitIndices(request.param("index")));
        try {
            optimizeRequest.waitForMerge(request.paramAsBoolean("waitForMerge", optimizeRequest.waitForMerge()));
            optimizeRequest.maxNumSegments(request.paramAsInt("maxNumSegments", optimizeRequest.maxNumSegments()));
            optimizeRequest.onlyExpungeDeletes(request.paramAsBoolean("onlyExpungeDeletes", optimizeRequest.onlyExpungeDeletes()));
            optimizeRequest.flush(request.paramAsBoolean("flush", optimizeRequest.flush()));
            optimizeRequest.refresh(request.paramAsBoolean("refresh", optimizeRequest.refresh()));

            // we just send back a response, no need to fork a listener
            optimizeRequest.listenerThreaded(false);
            BroadcastOperationThreading operationThreading = BroadcastOperationThreading.fromString(request.param("operationThreading"), BroadcastOperationThreading.SINGLE_THREAD);
            if (operationThreading == BroadcastOperationThreading.NO_THREADS) {
                // since we don't spawn, don't allow no_threads, but change it to a single thread
                operationThreading = BroadcastOperationThreading.THREAD_PER_SHARD;
            }
            optimizeRequest.operationThreading(operationThreading);
        } catch (Exception e) {
            try {
                channel.sendResponse(new JsonHttpResponse(request, BAD_REQUEST, JsonBuilder.jsonBuilder().startObject().field("error", e.getMessage()).endObject()));
            } catch (IOException e1) {
                logger.error("Failed to send failure response", e1);
            }
            return;
        }
        client.admin().indices().execOptimize(optimizeRequest, new ActionListener<OptimizeResponse>() {
            @Override public void onResponse(OptimizeResponse response) {
                try {
                    JsonBuilder builder = HttpJsonBuilder.cached(request);
                    builder.startObject();
                    builder.field("ok", true);

                    builder.startObject("_shards");
                    builder.field("total", response.totalShards());
                    builder.field("successful", response.successfulShards());
                    builder.field("failed", response.failedShards());
                    builder.endObject();

                    builder.endObject();
                    channel.sendResponse(new JsonHttpResponse(request, OK, builder));
                } catch (Exception e) {
                    onFailure(e);
                }
            }

            @Override public void onFailure(Throwable e) {
                try {
                    channel.sendResponse(new JsonThrowableHttpResponse(request, e));
                } catch (IOException e1) {
                    logger.error("Failed to send failure response", e1);
                }
            }
        });
    }


    @Override public boolean spawn() {
        // we don't spawn since we fork in index replication based on operation
        return false;
    }
}