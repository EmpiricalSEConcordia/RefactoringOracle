/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.runners;

import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.debugging.DebuggingInfo;
import org.mockito.internal.progress.MockingProgress;
import org.mockito.internal.progress.ThreadSafeMockingProgress;
import org.mockito.internal.util.MockitoLogger;
import org.mockito.internal.util.MockitoLoggerImpl;

/**
 * Uses <b>JUnit 4.5</b> runner {@link BlockJUnit4ClassRunner}.
 * <p>
 * JUnit 4.5 runner initializes mocks annotated with {@link Mock},
 * so that explicit usage of {@link MockitoAnnotations#initMocks(Object)} is not necessary. 
 * Mocks are initialized before each test method. 
 * <p>
 * Runner is completely optional - there are other ways you can get &#064;Mock working, for example by writing a base class.
 * <p>
 * Read more in javadoc for {@link MockitoAnnotations}
 * <p>
 * Example:
 * <pre>
 * <b>&#064;RunWith(MockitoJUnit44Runner.class)</b>
 * public class ExampleTest {
 * 
 *     &#064;Mock
 *     private List list;
 * 
 *     &#064;Test
 *     public void shouldDoSomething() {
 *         list.add(100);
 *     }
 * }
 * <p>
 * 
 * </pre>
 */
public class ExperimentalMockitoJUnitRunner extends MockitoJUnitRunner {

    private final MockitoLogger logger;
    
    public ExperimentalMockitoJUnitRunner(Class<?> klass) throws InitializationError {
        this(klass, new MockitoLoggerImpl());
    }
    
    public ExperimentalMockitoJUnitRunner(Class<?> klass, MockitoLogger logger) throws InitializationError {
        super(klass);
        this.logger = logger;
    }
    
    //this is what is really executed when the test runs
    static interface JunitTestBody {
        void run(RunNotifier notifier);
    }
    
    @Override
    public void run(RunNotifier notifier) {
        this.run(notifier, new JunitTestBody() {
            public void run(RunNotifier notifier) {
                ExperimentalMockitoJUnitRunner.super.run(notifier);
            }
        });
    }
    
    public void run(RunNotifier notifier, JunitTestBody junitTestBody) {
        MockingProgress progress = new ThreadSafeMockingProgress();
        DebuggingInfo debuggingInfo = progress.getDebuggingInfo();
        
        beforeRun(notifier, debuggingInfo);
        
        junitTestBody.run(notifier);
        
        afterRun(debuggingInfo);
    }

    private void afterRun(final DebuggingInfo debuggingInfo) {
        debuggingInfo.clearData();
    }

    private void beforeRun(RunNotifier notifier, final DebuggingInfo debuggingInfo) {
        debuggingInfo.collectData();

        RunListener listener = new RunListener() {
            @Override public void testFailure(Failure failure) throws Exception {
                debuggingInfo.printWarnings(logger);
            }
        };
        
        notifier.addListener(listener);
    }
}