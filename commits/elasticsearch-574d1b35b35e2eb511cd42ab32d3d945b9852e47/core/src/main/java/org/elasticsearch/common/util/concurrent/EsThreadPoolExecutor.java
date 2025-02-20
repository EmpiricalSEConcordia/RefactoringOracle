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

package org.elasticsearch.common.util.concurrent;


import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * An extension to thread pool executor, allowing (in the future) to add specific additional stats to it.
 */
public class EsThreadPoolExecutor extends ThreadPoolExecutor {

    private final ThreadContext contextHolder;
    private volatile ShutdownListener listener;

    private final Object monitor = new Object();
    /**
     * Name used in error reporting.
     */
    private final String name;

    EsThreadPoolExecutor(String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, ThreadContext contextHolder) {
        this(name, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, new EsAbortPolicy(), contextHolder);
    }

    EsThreadPoolExecutor(String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, XRejectedExecutionHandler handler, ThreadContext contextHolder) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        this.name = name;
        this.contextHolder = contextHolder;
    }

    public void shutdown(ShutdownListener listener) {
        synchronized (monitor) {
            if (this.listener != null) {
                throw new IllegalStateException("Shutdown was already called on this thread pool");
            }
            if (isTerminated()) {
                listener.onTerminated();
            } else {
                this.listener = listener;
            }
        }
        shutdown();
    }

    @Override
    protected synchronized void terminated() {
        super.terminated();
        synchronized (monitor) {
            if (listener != null) {
                try {
                    listener.onTerminated();
                } finally {
                    listener = null;
                }
            }
        }
    }

    public interface ShutdownListener {
        void onTerminated();
    }

    @Override
    public void execute(final Runnable command) {
        doExecute(wrapRunnable(command));
    }

    protected void doExecute(final Runnable command) {
        try {
            super.execute(command);
        } catch (EsRejectedExecutionException ex) {
            if (command instanceof AbstractRunnable) {
                // If we are an abstract runnable we can handle the rejection
                // directly and don't need to rethrow it.
                try {
                    ((AbstractRunnable) command).onRejection(ex);
                } finally {
                    ((AbstractRunnable) command).onAfter();

                }
            } else {
                throw ex;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(getClass().getSimpleName()).append('[');
        b.append(name).append(", ");
        if (getQueue() instanceof SizeBlockingQueue) {
            @SuppressWarnings("rawtypes")
            SizeBlockingQueue queue = (SizeBlockingQueue) getQueue();
            b.append("queue capacity = ").append(queue.capacity()).append(", ");
        }
        /*
         * ThreadPoolExecutor has some nice information in its toString but we
         * can't get at it easily without just getting the toString.
         */
        b.append(super.toString()).append(']');
        return b.toString();
    }

    protected Runnable wrapRunnable(Runnable command) {
        final Runnable wrappedCommand;
        if (command instanceof AbstractRunnable) {
            wrappedCommand = new FilterAbstractRunnable(contextHolder, (AbstractRunnable) command);
        } else {
            wrappedCommand = new FilterRunnable(contextHolder, command);
        }
        return wrappedCommand;
    }

    protected Runnable unwrap(Runnable runnable) {
        if (runnable instanceof FilterAbstractRunnable) {
            return ((FilterAbstractRunnable) runnable).in;
        } else if (runnable instanceof FilterRunnable) {
            return ((FilterRunnable) runnable).in;
        }
        return runnable;
    }

    private static class FilterAbstractRunnable extends AbstractRunnable {
        private final ThreadContext contextHolder;
        private final AbstractRunnable in;
        private final ThreadContext.StoredContext ctx;

        FilterAbstractRunnable(ThreadContext contextHolder, AbstractRunnable in) {
            this.contextHolder = contextHolder;
            ctx = contextHolder.newStoredContext();
            this.in = in;
        }

        @Override
        public boolean isForceExecution() {
            return in.isForceExecution();
        }

        @Override
        public void onAfter() {
            in.onAfter();
        }

        @Override
        public void onFailure(Throwable t) {
            in.onFailure(t);
        }

        @Override
        public void onRejection(Throwable t) {
            in.onRejection(t);
        }

        @Override
        protected void doRun() throws Exception {
            try (ThreadContext.StoredContext ingore = contextHolder.stashContext()){
                ctx.restore();
                in.doRun();
            }
        }

        @Override
        public String toString() {
            return in.toString();
        }

    }

    private static class FilterRunnable implements Runnable {
        private final ThreadContext contextHolder;
        private final Runnable in;
        private final ThreadContext.StoredContext ctx;

        FilterRunnable(ThreadContext contextHolder, Runnable in) {
            this.contextHolder = contextHolder;
            ctx = contextHolder.newStoredContext();
            this.in = in;
        }

        @Override
        public void run() {
            try (ThreadContext.StoredContext ingore = contextHolder.stashContext()){
                ctx.restore();
                in.run();
            }
        }
        @Override
        public String toString() {
            return in.toString();
        }
    }

}
