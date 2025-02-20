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

package org.elasticsearch.rest.action.search;

import com.google.inject.Inject;
import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchOperationThreading;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.json.JsonQueryBuilders;
import org.elasticsearch.index.query.json.QueryStringJsonQueryBuilder;
import org.elasticsearch.rest.*;
import org.elasticsearch.rest.action.support.RestActions;
import org.elasticsearch.rest.action.support.RestJsonBuilder;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.util.json.JsonBuilder;
import org.elasticsearch.util.settings.Settings;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import static org.elasticsearch.rest.RestRequest.Method.*;
import static org.elasticsearch.rest.RestResponse.Status.*;
import static org.elasticsearch.util.TimeValue.*;

/**
 * @author kimchy (Shay Banon)
 */
public class RestSearchAction extends BaseRestHandler {

    private final static Pattern fieldsPattern;

    private final static Pattern indicesBoostPattern;

    static {
        fieldsPattern = Pattern.compile(",");
        indicesBoostPattern = Pattern.compile(",");
    }

    @Inject public RestSearchAction(Settings settings, Client client, RestController controller) {
        super(settings, client);
        controller.registerHandler(GET, "/_search", this);
        controller.registerHandler(POST, "/_search", this);
        controller.registerHandler(GET, "/{index}/_search", this);
        controller.registerHandler(POST, "/{index}/_search", this);
        controller.registerHandler(GET, "/{index}/{type}/_search", this);
        controller.registerHandler(POST, "/{index}/{type}/_search", this);
    }

    @Override public void handleRequest(final RestRequest request, final RestChannel channel) {
        SearchRequest searchRequest;
        try {
            searchRequest = parseSearchRequest(request);
            searchRequest.listenerThreaded(false);
            SearchOperationThreading operationThreading = SearchOperationThreading.fromString(request.param("operationThreading"), SearchOperationThreading.SINGLE_THREAD);
            if (operationThreading == SearchOperationThreading.NO_THREADS) {
                // since we don't spawn, don't allow no_threads, but change it to a single thread
                operationThreading = SearchOperationThreading.SINGLE_THREAD;
            }
            searchRequest.operationThreading(operationThreading);
        } catch (Exception e) {
            try {
                JsonBuilder builder = RestJsonBuilder.restJsonBuilder(request);
                channel.sendResponse(new JsonRestResponse(request, BAD_REQUEST, builder.startObject().field("error", e.getMessage()).endObject()));
            } catch (IOException e1) {
                logger.error("Failed to send failure response", e1);
            }
            return;
        }
        client.execSearch(searchRequest, new ActionListener<SearchResponse>() {
            @Override public void onResponse(SearchResponse result) {
                try {
                    JsonBuilder builder = RestJsonBuilder.restJsonBuilder(request);
                    builder.startObject();
                    result.toJson(builder, request);
                    builder.endObject();
                    channel.sendResponse(new JsonRestResponse(request, OK, builder));
                } catch (Exception e) {
                    onFailure(e);
                }
            }

            @Override public void onFailure(Throwable e) {
                try {
                    channel.sendResponse(new JsonThrowableRestResponse(request, e));
                } catch (IOException e1) {
                    logger.error("Failed to send failure response", e1);
                }
            }
        });
    }

    private SearchRequest parseSearchRequest(RestRequest request) {
        String[] indices = RestActions.splitIndices(request.param("index"));
        SearchRequest searchRequest = new SearchRequest(indices, parseSearchSource(request));

        String searchType = request.param("searchType");
        if (searchType != null) {
            if ("dfs_query_then_fetch".equals(searchType)) {
                searchRequest.searchType(SearchType.DFS_QUERY_THEN_FETCH);
            } else if ("dfs_query_and_fetch".equals(searchType)) {
                searchRequest.searchType(SearchType.DFS_QUERY_AND_FETCH);
            } else if ("query_then_fetch".equals(searchType)) {
                searchRequest.searchType(SearchType.QUERY_THEN_FETCH);
            } else if ("query_and_fetch".equals(searchType)) {
                searchRequest.searchType(SearchType.QUERY_AND_FETCH);
            } else {
                throw new ElasticSearchIllegalArgumentException("No search type for [" + searchType + "]");
            }
        } else {
            searchRequest.searchType(SearchType.QUERY_THEN_FETCH);
        }

        String from = request.param("from");
        if (from != null) {
            searchRequest.from(Integer.parseInt(from));
        }

        String size = request.param("size");
        if (size != null) {
            searchRequest.size(Integer.parseInt(size));
        }

        String sIndicesBoost = request.param("indicesBoost");
        if (sIndicesBoost != null) {
            String[] indicesBoost = indicesBoostPattern.split(sIndicesBoost);
            for (String indexBoost : indicesBoost) {
                int divisor = indexBoost.indexOf(',');
                if (divisor == -1) {
                    throw new ElasticSearchIllegalArgumentException("Illegal index boost [" + indexBoost + "], no ','");
                }
                String indexName = indexBoost.substring(0, divisor);
                String sBoost = indexBoost.substring(divisor + 1);
                try {
                    searchRequest.indexBoost(indexName, Float.parseFloat(sBoost));
                } catch (NumberFormatException e) {
                    throw new ElasticSearchIllegalArgumentException("Illegal index boost [" + indexBoost + "], boost not a float number");
                }
            }
        }

        String scroll = request.param("scroll");
        if (scroll != null) {
            searchRequest.scroll(new Scroll(parseTimeValue(scroll, null)));
        }

        String timeout = request.param("timeout");
        if (timeout != null) {
            searchRequest.timeout(parseTimeValue(timeout, null));
        }

        String typesParam = request.param("type");
        if (typesParam != null) {
            searchRequest.types(RestActions.splitTypes(typesParam));
        }

        searchRequest.queryHint(request.param("queryHint"));

        return searchRequest;
    }

    private byte[] parseSearchSource(RestRequest request) {
        if (request.hasContent()) {
            return request.contentAsBytes();
        }
        String queryString = request.param("q");
        if (queryString == null) {
            throw new ElasticSearchIllegalArgumentException("No query to execute, not in body, and not bounded to 'q' parameter");
        }
        QueryStringJsonQueryBuilder queryBuilder = JsonQueryBuilders.queryString(queryString);
        queryBuilder.defaultField(request.param("df"));
        queryBuilder.analyzer(request.param("analyzer"));
        String defaultOperator = request.param("defaultOperator");
        if (defaultOperator != null) {
            if ("OR".equals(defaultOperator)) {
                queryBuilder.defualtOperator(QueryStringJsonQueryBuilder.Operator.OR);
            } else if ("AND".equals(defaultOperator)) {
                queryBuilder.defualtOperator(QueryStringJsonQueryBuilder.Operator.AND);
            } else {
                throw new ElasticSearchIllegalArgumentException("Unsupported defaultOperator [" + defaultOperator + "], can either be [OR] or [AND]");
            }
        }
        // TODO add different parameters to the query

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(queryBuilder);

        searchSourceBuilder.queryParserName(request.param("queryParserName"));
        searchSourceBuilder.explain(request.paramAsBoolean("explain", false));

        List<String> fields = request.params("field");
        if (fields != null && !fields.isEmpty()) {
            searchSourceBuilder.fields(fields);
        }
        String sField = request.param("fields");
        if (sField != null) {
            String[] sFields = fieldsPattern.split(sField);
            if (sFields != null) {
                for (String field : sFields) {
                    searchSourceBuilder.field(field);
                }
            }
        }

        List<String> sorts = request.params("sort");
        if (sorts != null && !sorts.isEmpty()) {
            for (String sort : sorts) {
                int delimiter = sort.lastIndexOf(":");
                if (delimiter != -1) {
                    String sortField = sort.substring(0, delimiter);
                    String reverse = sort.substring(delimiter + 1);
                    searchSourceBuilder.sort(sortField, reverse.equals("reverse"));
                } else {
                    searchSourceBuilder.sort(sort);
                }
            }
        }

        // TODO add different parameters to the source
        return searchSourceBuilder.build();
    }
}
