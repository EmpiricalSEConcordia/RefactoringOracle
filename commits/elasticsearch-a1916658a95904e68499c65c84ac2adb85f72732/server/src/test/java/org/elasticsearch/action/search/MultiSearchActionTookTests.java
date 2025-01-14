/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package org.elasticsearch.action.search;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Randomness;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.AtomicArray;
import org.elasticsearch.tasks.TaskManager;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.elasticsearch.test.ClusterServiceUtils.createClusterService;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MultiSearch took time tests
 */
public class MultiSearchActionTookTests extends ESTestCase {

    private ThreadPool threadPool;
    private ClusterService clusterService;

    @BeforeClass
    public static void beforeClass() {
    }

    @AfterClass
    public static void afterClass() {
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        threadPool = new TestThreadPool("MultiSearchActionTookTests");
        clusterService = createClusterService(threadPool);
    }

    @After
    public void tearDown() throws Exception {
        clusterService.close();
        ThreadPool.terminate(threadPool, 30, TimeUnit.SECONDS);
        super.tearDown();
    }

    // test unit conversion using a controller clock
    public void testTookWithControlledClock() throws Exception {
        runTestTook(true);
    }

    // test using System#nanoTime
    public void testTookWithRealClock() throws Exception {
        runTestTook(false);
    }

    private void runTestTook(boolean controlledClock) throws Exception {
        MultiSearchRequest multiSearchRequest = new MultiSearchRequest().add(new SearchRequest());
        AtomicLong expected = new AtomicLong();

        TransportMultiSearchAction action = createTransportMultiSearchAction(controlledClock, expected);

        action.doExecute(multiSearchRequest, new ActionListener<MultiSearchResponse>() {
            @Override
            public void onResponse(MultiSearchResponse multiSearchResponse) {
                if (controlledClock) {
                    assertThat(TimeUnit.MILLISECONDS.convert(expected.get(), TimeUnit.NANOSECONDS),
                            equalTo(multiSearchResponse.getTook().getMillis()));
                } else {
                    assertThat(multiSearchResponse.getTook().getMillis(),
                            greaterThanOrEqualTo(TimeUnit.MILLISECONDS.convert(expected.get(), TimeUnit.NANOSECONDS)));
                }
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private TransportMultiSearchAction createTransportMultiSearchAction(boolean controlledClock, AtomicLong expected) {
        Settings settings = Settings.builder().put("node.name", TransportMultiSearchActionTests.class.getSimpleName()).build();
        TaskManager taskManager = mock(TaskManager.class);
        TransportService transportService = new TransportService(Settings.EMPTY, null, null, TransportService.NOOP_TRANSPORT_INTERCEPTOR,
            boundAddress -> DiscoveryNode.createLocal(settings, boundAddress.publishAddress(), UUIDs.randomBase64UUID()), null,
            Collections.emptySet()) {
            @Override
            public TaskManager getTaskManager() {
                return taskManager;
            }
        };
        ActionFilters actionFilters = new ActionFilters(new HashSet<>());
        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.state()).thenReturn(ClusterState.builder(new ClusterName("test")).build());
        IndexNameExpressionResolver resolver = new Resolver(Settings.EMPTY);

        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        AtomicInteger counter = new AtomicInteger();
        final List<String> threadPoolNames = Arrays.asList(ThreadPool.Names.GENERIC, ThreadPool.Names.SAME);
        Randomness.shuffle(threadPoolNames);
        final ExecutorService commonExecutor = threadPool.executor(threadPoolNames.get(0));
        final Set<SearchRequest> requests = Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<>()));

        TransportAction<SearchRequest, SearchResponse> searchAction = new TransportAction<SearchRequest, SearchResponse>(Settings.EMPTY,
                "action", threadPool, actionFilters, resolver, taskManager) {
            @Override
            protected void doExecute(SearchRequest request, ActionListener<SearchResponse> listener) {
                requests.add(request);
                commonExecutor.execute(() -> {
                    counter.decrementAndGet();
                    listener.onResponse(new SearchResponse());
                });
            }
        };

        if (controlledClock) {
            return new TransportMultiSearchAction(threadPool, actionFilters, transportService, clusterService, searchAction, resolver,
                    availableProcessors, expected::get) {
                @Override
                void executeSearch(final Queue<SearchRequestSlot> requests, final AtomicArray<MultiSearchResponse.Item> responses,
                        final AtomicInteger responseCounter, final ActionListener<MultiSearchResponse> listener, long startTimeInNanos) {
                    expected.set(1000000);
                    super.executeSearch(requests, responses, responseCounter, listener, startTimeInNanos);
                }
            };
        } else {
            return new TransportMultiSearchAction(threadPool, actionFilters, transportService, clusterService, searchAction, resolver,
                    availableProcessors, System::nanoTime) {

                @Override
                void executeSearch(final Queue<SearchRequestSlot> requests, final AtomicArray<MultiSearchResponse.Item> responses,
                        final AtomicInteger responseCounter, final ActionListener<MultiSearchResponse> listener, long startTimeInNanos) {
                    long elapsed = spinForAtLeastNMilliseconds(randomIntBetween(0, 10));
                    expected.set(elapsed);
                    super.executeSearch(requests, responses, responseCounter, listener, startTimeInNanos);
                }
            };
        }
    }

    static class Resolver extends IndexNameExpressionResolver {

        Resolver(Settings settings) {
            super(settings);
        }

        @Override
        public String[] concreteIndexNames(ClusterState state, IndicesRequest request) {
            return request.indices();
        }
    }
}
