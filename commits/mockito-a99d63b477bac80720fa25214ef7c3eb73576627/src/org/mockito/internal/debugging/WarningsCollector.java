/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.debugging;

import org.mockito.internal.invocation.InvocationImpl;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.invocation.UnusedStubsFinder;
import org.mockito.internal.invocation.finder.AllInvocationsFinder;
import org.mockito.internal.listeners.CollectCreatedMocks;
import org.mockito.internal.progress.MockingProgress;
import org.mockito.internal.progress.ThreadSafeMockingProgress;

import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("unchecked")
public class WarningsCollector {
   
    List createdMocks;

    public WarningsCollector() {
        createdMocks = new LinkedList();
        MockingProgress progress = new ThreadSafeMockingProgress();
        progress.setListener(new CollectCreatedMocks(createdMocks));
    }

    public String getWarnings() {
        List<InvocationImpl> unused = new UnusedStubsFinder().find(createdMocks);
        List<InvocationImpl> all = new AllInvocationsFinder().find(createdMocks);
        List<InvocationMatcher> allInvocationMatchers = InvocationMatcher.createFrom(all);

        String warnings = new WarningsPrinterImpl(unused, allInvocationMatchers, false).print();

        return warnings;
    }
}