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

package org.elasticsearch.action.support;

import org.elasticsearch.ElasticsearchTimeoutException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.tasks.TaskManager;
import org.elasticsearch.test.ESTestCase;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TransportActionFilterChainTests extends ESTestCase {

    private AtomicInteger counter;

    @Before
    public void init() throws Exception {
         counter = new AtomicInteger();
    }

    public void testActionFiltersRequest() throws ExecutionException, InterruptedException {
        int numFilters = randomInt(10);
        Set<Integer> orders = new HashSet<>(numFilters);
        while (orders.size() < numFilters) {
            orders.add(randomInt(10));
        }

        Set<ActionFilter> filters = new HashSet<>();
        for (Integer order : orders) {
            filters.add(new RequestTestFilter(order, randomFrom(RequestOperation.values())));
        }

        String actionName = randomAsciiOfLength(randomInt(30));
        ActionFilters actionFilters = new ActionFilters(filters);
        TransportAction<TestRequest, TestResponse> transportAction = new TransportAction<TestRequest, TestResponse>(Settings.EMPTY, actionName, null, actionFilters, null, new TaskManager(Settings.EMPTY)) {
            @Override
            protected void doExecute(TestRequest request, ActionListener<TestResponse> listener) {
                listener.onResponse(new TestResponse());
            }
        };

        ArrayList<ActionFilter> actionFiltersByOrder = new ArrayList<>(filters);
        Collections.sort(actionFiltersByOrder, new Comparator<ActionFilter>() {
            @Override
            public int compare(ActionFilter o1, ActionFilter o2) {
                return Integer.compare(o1.order(), o2.order());
            }
        });

        List<ActionFilter> expectedActionFilters = new ArrayList<>();
        boolean errorExpected = false;
        for (ActionFilter filter : actionFiltersByOrder) {
            RequestTestFilter testFilter = (RequestTestFilter) filter;
            expectedActionFilters.add(testFilter);
            if (testFilter.callback == RequestOperation.LISTENER_FAILURE) {
                errorExpected = true;
            }
            if (!(testFilter.callback == RequestOperation.CONTINUE_PROCESSING) ) {
                break;
            }
        }

        PlainListenableActionFuture<TestResponse> future = new PlainListenableActionFuture<>(null);
        transportAction.execute(new TestRequest(), future);
        try {
            assertThat(future.get(), notNullValue());
            assertThat("shouldn't get here if an error is expected", errorExpected, equalTo(false));
        } catch (ExecutionException e) {
            assertThat("shouldn't get here if an error is not expected " + e.getMessage(), errorExpected, equalTo(true));
        }

        List<RequestTestFilter> testFiltersByLastExecution = new ArrayList<>();
        for (ActionFilter actionFilter : actionFilters.filters()) {
            testFiltersByLastExecution.add((RequestTestFilter) actionFilter);
        }
        Collections.sort(testFiltersByLastExecution, new Comparator<RequestTestFilter>() {
            @Override
            public int compare(RequestTestFilter o1, RequestTestFilter o2) {
                return Integer.compare(o1.executionToken, o2.executionToken);
            }
        });

        ArrayList<RequestTestFilter> finalTestFilters = new ArrayList<>();
        for (ActionFilter filter : testFiltersByLastExecution) {
            RequestTestFilter testFilter = (RequestTestFilter) filter;
            finalTestFilters.add(testFilter);
            if (!(testFilter.callback == RequestOperation.CONTINUE_PROCESSING) ) {
                break;
            }
        }

        assertThat(finalTestFilters.size(), equalTo(expectedActionFilters.size()));
        for (int i = 0; i < finalTestFilters.size(); i++) {
            RequestTestFilter testFilter = finalTestFilters.get(i);
            assertThat(testFilter, equalTo(expectedActionFilters.get(i)));
            assertThat(testFilter.runs.get(), equalTo(1));
            assertThat(testFilter.lastActionName, equalTo(actionName));
        }
    }

    public void testActionFiltersResponse() throws ExecutionException, InterruptedException {
        int numFilters = randomInt(10);
        Set<Integer> orders = new HashSet<>(numFilters);
        while (orders.size() < numFilters) {
            orders.add(randomInt(10));
        }

        Set<ActionFilter> filters = new HashSet<>();
        for (Integer order : orders) {
            filters.add(new ResponseTestFilter(order, randomFrom(ResponseOperation.values())));
        }

        String actionName = randomAsciiOfLength(randomInt(30));
        ActionFilters actionFilters = new ActionFilters(filters);
        TransportAction<TestRequest, TestResponse> transportAction = new TransportAction<TestRequest, TestResponse>(Settings.EMPTY, actionName, null, actionFilters, null, new TaskManager(Settings.EMPTY)) {
            @Override
            protected void doExecute(TestRequest request, ActionListener<TestResponse> listener) {
                listener.onResponse(new TestResponse());
            }
        };

        ArrayList<ActionFilter> actionFiltersByOrder = new ArrayList<>(filters);
        Collections.sort(actionFiltersByOrder, new Comparator<ActionFilter>() {
            @Override
            public int compare(ActionFilter o1, ActionFilter o2) {
                return Integer.compare(o2.order(), o1.order());
            }
        });

        List<ActionFilter> expectedActionFilters = new ArrayList<>();
        boolean errorExpected = false;
        for (ActionFilter filter : actionFiltersByOrder) {
            ResponseTestFilter testFilter = (ResponseTestFilter) filter;
            expectedActionFilters.add(testFilter);
            if (testFilter.callback == ResponseOperation.LISTENER_FAILURE) {
                errorExpected = true;
            }
            if (testFilter.callback != ResponseOperation.CONTINUE_PROCESSING) {
                break;
            }
        }

        PlainListenableActionFuture<TestResponse> future = new PlainListenableActionFuture<>(null);
        transportAction.execute(new TestRequest(), future);
        try {
            assertThat(future.get(), notNullValue());
            assertThat("shouldn't get here if an error is expected", errorExpected, equalTo(false));
        } catch(ExecutionException e) {
            assertThat("shouldn't get here if an error is not expected " + e.getMessage(), errorExpected, equalTo(true));
        }

        List<ResponseTestFilter> testFiltersByLastExecution = new ArrayList<>();
        for (ActionFilter actionFilter : actionFilters.filters()) {
            testFiltersByLastExecution.add((ResponseTestFilter) actionFilter);
        }
        Collections.sort(testFiltersByLastExecution, new Comparator<ResponseTestFilter>() {
            @Override
            public int compare(ResponseTestFilter o1, ResponseTestFilter o2) {
                return Integer.compare(o1.executionToken, o2.executionToken);
            }
        });

        ArrayList<ResponseTestFilter> finalTestFilters = new ArrayList<>();
        for (ActionFilter filter : testFiltersByLastExecution) {
            ResponseTestFilter testFilter = (ResponseTestFilter) filter;
            finalTestFilters.add(testFilter);
            if (testFilter.callback != ResponseOperation.CONTINUE_PROCESSING) {
                break;
            }
        }

        assertThat(finalTestFilters.size(), equalTo(expectedActionFilters.size()));
        for (int i = 0; i < finalTestFilters.size(); i++) {
            ResponseTestFilter testFilter = finalTestFilters.get(i);
            assertThat(testFilter, equalTo(expectedActionFilters.get(i)));
            assertThat(testFilter.runs.get(), equalTo(1));
            assertThat(testFilter.lastActionName, equalTo(actionName));
        }
    }

    public void testTooManyContinueProcessingRequest() throws ExecutionException, InterruptedException {
        final int additionalContinueCount = randomInt(10);

        RequestTestFilter testFilter = new RequestTestFilter(randomInt(), new RequestCallback() {
            @Override
            public <Request extends ActionRequest<Request>, Response extends ActionResponse> void execute(Task task, String action, Request request,
                    ActionListener<Response> listener, ActionFilterChain<Request, Response> actionFilterChain) {
                for (int i = 0; i <= additionalContinueCount; i++) {
                    actionFilterChain.proceed(task, action, request, listener);
                }
            }
        });

        Set<ActionFilter> filters = new HashSet<>();
        filters.add(testFilter);

        String actionName = randomAsciiOfLength(randomInt(30));
        ActionFilters actionFilters = new ActionFilters(filters);
        TransportAction<TestRequest, TestResponse> transportAction = new TransportAction<TestRequest, TestResponse>(Settings.EMPTY, actionName, null, actionFilters, null, new TaskManager(Settings.EMPTY)) {
            @Override
            protected void doExecute(TestRequest request, ActionListener<TestResponse> listener) {
                listener.onResponse(new TestResponse());
            }
        };

        final CountDownLatch latch = new CountDownLatch(additionalContinueCount + 1);
        final AtomicInteger responses = new AtomicInteger();
        final List<Throwable> failures = new CopyOnWriteArrayList<>();

        transportAction.execute(new TestRequest(), new ActionListener<TestResponse>() {
            @Override
            public void onResponse(TestResponse testResponse) {
                responses.incrementAndGet();
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                failures.add(e);
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            fail("timeout waiting for the filter to notify the listener as many times as expected");
        }

        assertThat(testFilter.runs.get(), equalTo(1));
        assertThat(testFilter.lastActionName, equalTo(actionName));

        assertThat(responses.get(), equalTo(1));
        assertThat(failures.size(), equalTo(additionalContinueCount));
        for (Throwable failure : failures) {
            assertThat(failure, instanceOf(IllegalStateException.class));
        }
    }

    public void testTooManyContinueProcessingResponse() throws ExecutionException, InterruptedException {
        final int additionalContinueCount = randomInt(10);

        ResponseTestFilter testFilter = new ResponseTestFilter(randomInt(), new ResponseCallback() {
            @Override
            public <Response extends ActionResponse> void execute(String action, Response response, ActionListener<Response> listener,
                    ActionFilterChain<?, Response> chain) {
                for (int i = 0; i <= additionalContinueCount; i++) {
                    chain.proceed(action, response, listener);
                }
            }
        });

        Set<ActionFilter> filters = new HashSet<>();
        filters.add(testFilter);

        String actionName = randomAsciiOfLength(randomInt(30));
        ActionFilters actionFilters = new ActionFilters(filters);
        TransportAction<TestRequest, TestResponse> transportAction = new TransportAction<TestRequest, TestResponse>(Settings.EMPTY, actionName, null, actionFilters, null, new TaskManager(Settings.EMPTY)) {
            @Override
            protected void doExecute(TestRequest request, ActionListener<TestResponse> listener) {
                listener.onResponse(new TestResponse());
            }
        };

        final CountDownLatch latch = new CountDownLatch(additionalContinueCount + 1);
        final AtomicInteger responses = new AtomicInteger();
        final List<Throwable> failures = new CopyOnWriteArrayList<>();

        transportAction.execute(new TestRequest(), new ActionListener<TestResponse>() {
            @Override
            public void onResponse(TestResponse testResponse) {
                responses.incrementAndGet();
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                failures.add(e);
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            fail("timeout waiting for the filter to notify the listener as many times as expected");
        }

        assertThat(testFilter.runs.get(), equalTo(1));
        assertThat(testFilter.lastActionName, equalTo(actionName));

        assertThat(responses.get(), equalTo(1));
        assertThat(failures.size(), equalTo(additionalContinueCount));
        for (Throwable failure : failures) {
            assertThat(failure, instanceOf(IllegalStateException.class));
        }
    }

    private class RequestTestFilter implements ActionFilter {
        private final RequestCallback callback;
        private final int order;
        AtomicInteger runs = new AtomicInteger();
        volatile String lastActionName;
        volatile int executionToken = Integer.MAX_VALUE; //the filters that don't run will go last in the sorted list

        RequestTestFilter(int order, RequestCallback callback) {
            this.order = order;
            this.callback = callback;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public <Request extends ActionRequest<Request>, Response extends ActionResponse> void apply(Task task, String action, Request request,
                ActionListener<Response> listener, ActionFilterChain<Request, Response> chain) {
            this.runs.incrementAndGet();
            this.lastActionName = action;
            this.executionToken = counter.incrementAndGet();
            this.callback.execute(task, action, request, listener, chain);
        }

        @Override
        public <Response extends ActionResponse> void apply(String action, Response response, ActionListener<Response> listener,
                ActionFilterChain<?, Response> chain) {
            chain.proceed(action, response, listener);
        }
    }

    private class ResponseTestFilter implements ActionFilter {
        private final ResponseCallback callback;
        private final int order;
        AtomicInteger runs = new AtomicInteger();
        volatile String lastActionName;
        volatile int executionToken = Integer.MAX_VALUE; //the filters that don't run will go last in the sorted list

        ResponseTestFilter(int order, ResponseCallback callback) {
            this.order = order;
            this.callback = callback;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public <Request extends ActionRequest<Request>, Response extends ActionResponse> void apply(Task task, String action, Request request,
                ActionListener<Response> listener, ActionFilterChain<Request, Response> chain) {
            chain.proceed(task, action, request, listener);
        }

        @Override
        public <Response extends ActionResponse> void apply(String action, Response response, ActionListener<Response> listener,
                ActionFilterChain<?, Response> chain) {
            this.runs.incrementAndGet();
            this.lastActionName = action;
            this.executionToken = counter.incrementAndGet();
            this.callback.execute(action, response, listener, chain);
        }
    }

    private static enum RequestOperation implements RequestCallback {
        CONTINUE_PROCESSING {
            @Override
            public <Request extends ActionRequest<Request>, Response extends ActionResponse> void execute(Task task, String action, Request request,
                    ActionListener<Response> listener, ActionFilterChain<Request, Response> actionFilterChain) {
                actionFilterChain.proceed(task, action, request, listener);
            }
        },
        LISTENER_RESPONSE {
            @Override
            @SuppressWarnings("unchecked")  // Safe because its all we test with
            public <Request extends ActionRequest<Request>, Response extends ActionResponse> void execute(Task task, String action, Request request,
                    ActionListener<Response> listener, ActionFilterChain<Request, Response> actionFilterChain) {
                ((ActionListener<TestResponse>) listener).onResponse(new TestResponse());
            }
        },
        LISTENER_FAILURE {
            @Override
            public <Request extends ActionRequest<Request>, Response extends ActionResponse> void execute(Task task, String action, Request request,
                    ActionListener<Response> listener, ActionFilterChain<Request, Response> actionFilterChain) {
                listener.onFailure(new ElasticsearchTimeoutException(""));
            }
        }
    }

    private static enum ResponseOperation implements ResponseCallback {
        CONTINUE_PROCESSING {
            @Override
            public <Response extends ActionResponse> void execute(String action, Response response, ActionListener<Response> listener,
                    ActionFilterChain<?, Response> chain) {
                chain.proceed(action, response, listener);
            }
        },
        LISTENER_RESPONSE {
            @Override
            @SuppressWarnings("unchecked") // Safe because its all we test with
            public <Response extends ActionResponse> void execute(String action, Response response, ActionListener<Response> listener,
                    ActionFilterChain<?, Response> chain) {
                ((ActionListener<TestResponse>) listener).onResponse(new TestResponse());
            }
        },
        LISTENER_FAILURE {
            @Override
            public <Response extends ActionResponse> void execute(String action, Response response, ActionListener<Response> listener,
                    ActionFilterChain<?, Response> chain) {
                listener.onFailure(new ElasticsearchTimeoutException(""));
            }
        }
    }

    private static interface RequestCallback {
        <Request extends ActionRequest<Request>, Response extends ActionResponse> void execute(Task task, String action, Request request,
                ActionListener<Response> listener, ActionFilterChain<Request, Response> actionFilterChain);
    }

    private static interface ResponseCallback {
        <Response extends ActionResponse> void execute(String action, Response response, ActionListener<Response> listener,
                ActionFilterChain<?, Response> chain);
    }

    public static class TestRequest extends ActionRequest<TestRequest> {
        @Override
        public ActionRequestValidationException validate() {
            return null;
        }
    }

    private static class TestResponse extends ActionResponse {

    }
}
