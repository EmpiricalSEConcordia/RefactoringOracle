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

package org.elasticsearch.rest.action.admin.indices.optimize;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.optimize.OptimizeRequest;
import org.elasticsearch.action.admin.indices.optimize.OptimizeResponse;
import org.elasticsearch.action.support.broadcast.BroadcastOperationThreading;
import org.elasticsearch.client.Client;
import org.elasticsearch.rest.*;
import org.elasticsearch.rest.action.support.RestActions;
import org.elasticsearch.rest.action.support.RestXContentBuilder;
import org.elasticsearch.util.inject.Inject;
import org.elasticsearch.util.settings.Settings;
import org.elasticsearch.util.xcontent.builder.XContentBuilder;

import java.io.IOException;

import static org.elasticsearch.rest.RestRequest.Method.*;
import static org.elasticsearch.rest.RestResponse.Status.*;
import static org.elasticsearch.rest.action.support.RestActions.*;

/**
 * @author kimchy (Shay Banon)
 */
public class RestOptimizeAction extends BaseRestHandler {

    @Inject public RestOptimizeAction(Settings settings, Client client, RestController controller) {
        super(settings, client);
        controller.registerHandler(POST, "/_optimize", this);
        controller.registerHandler(POST, "/{index}/_optimize", this);

        controller.registerHandler(GET, "/_optimize", this);
        controller.registerHandler(GET, "/{index}/_optimize", this);
    }

    @Override public void handleRequest(final RestRequest request, final RestChannel channel) {
        OptimizeRequest optimizeRequest = new OptimizeRequest(RestActions.splitIndices(request.param("index")));
        try {
            optimizeRequest.waitForMerge(request.paramAsBoolean("wait_for_merge", optimizeRequest.waitForMerge()));
            optimizeRequest.maxNumSegments(request.paramAsInt("max_num_segments", optimizeRequest.maxNumSegments()));
            optimizeRequest.onlyExpungeDeletes(request.paramAsBoolean("only_expunge_deletes", optimizeRequest.onlyExpungeDeletes()));
            optimizeRequest.flush(request.paramAsBoolean("flush", optimizeRequest.flush()));
            optimizeRequest.refresh(request.paramAsBoolean("refresh", optimizeRequest.refresh()));

            // we just send back a response, no need to fork a listener
            optimizeRequest.listenerThreaded(false);
            BroadcastOperationThreading operationThreading = BroadcastOperationThreading.fromString(request.param("operation_threading"), BroadcastOperationThreading.SINGLE_THREAD);
            if (operationThreading == BroadcastOperationThreading.NO_THREADS) {
                // since we don't spawn, don't allow no_threads, but change it to a single thread
                operationThreading = BroadcastOperationThreading.THREAD_PER_SHARD;
            }
            optimizeRequest.operationThreading(operationThreading);
        } catch (Exception e) {
            try {
                XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                channel.sendResponse(new XContentRestResponse(request, BAD_REQUEST, builder.startObject().field("error", e.getMessage()).endObject()));
            } catch (IOException e1) {
                logger.error("Failed to send failure response", e1);
            }
            return;
        }
        client.admin().indices().optimize(optimizeRequest, new ActionListener<OptimizeResponse>() {
            @Override public void onResponse(OptimizeResponse response) {
                try {
                    XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                    builder.startObject();
                    builder.field("ok", true);

                    buildBroadcastShardsHeader(builder, response);

                    builder.endObject();
                    channel.sendResponse(new XContentRestResponse(request, OK, builder));
                } catch (Exception e) {
                    onFailure(e);
                }
            }

            @Override public void onFailure(Throwable e) {
                try {
                    channel.sendResponse(new XContentThrowableRestResponse(request, e));
                } catch (IOException e1) {
                    logger.error("Failed to send failure response", e1);
                }
            }
        });
    }
}