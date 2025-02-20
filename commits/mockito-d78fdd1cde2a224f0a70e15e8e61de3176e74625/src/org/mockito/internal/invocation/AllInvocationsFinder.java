/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.invocation;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.mockito.internal.stubbing.StubbedInvocationMatcher;
import org.mockito.internal.util.ListUtil;
import org.mockito.internal.util.MockUtil;

public class AllInvocationsFinder {
    
    /**
     * gets all invocations from mocks. Invocations are ordered earlier first. 
     * 
     * @param mocks mocks
     * @return invocations
     */
    public List<Invocation> getAllInvocations(List<?> mocks) {
        Set<Invocation> invocationsInOrder = new TreeSet<Invocation>(new SequenceNumberComparator());
        for (Object mock : mocks) {
            List<Invocation> fromSingleMock = new MockUtil().getMockHandler(mock).getRegisteredInvocations();
            invocationsInOrder.addAll(fromSingleMock);
        }
        
        return new LinkedList<Invocation>(invocationsInOrder);
    }

    public List<Invocation> getAllUnusedStubs(List<?> mocks) {
        List<Invocation> unused = new LinkedList<Invocation>();
        for (Object mock : mocks) {
            List<StubbedInvocationMatcher> fromSingleMock = new MockUtil().getMockHandler(mock).getStubbedInvocations();
            for(StubbedInvocationMatcher s : fromSingleMock) {
                if (!s.wasUsed()) {
                     unused.add(s.getInvocation());
                }
            }
        }
        return unused;
    }

    private final class SequenceNumberComparator implements Comparator<Invocation> {
        public int compare(Invocation o1, Invocation o2) {
            return o1.getSequenceNumber().compareTo(o2.getSequenceNumber());
        }
    }
}