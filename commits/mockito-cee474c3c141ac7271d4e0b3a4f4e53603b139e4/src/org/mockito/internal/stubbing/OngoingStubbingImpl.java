/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.stubbing;

import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.verification.RegisteredInvocations;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.DeprecatedOngoingStubbing;
import org.mockito.stubbing.OngoingStubbing;

import java.util.List;

public class OngoingStubbingImpl<T> extends BaseStubbing<T> {
    
    private final MockitoStubber mockitoStubber;

    public OngoingStubbingImpl(MockitoStubber mockitoStubber) {
        this.mockitoStubber = mockitoStubber;
    }

    public OngoingStubbing<T> thenAnswer(Answer<?> answer) {
        mockitoStubber.addAnswer(answer);
        return new ConsecutiveStubbing<T>(mockitoStubber);
    }

    public DeprecatedOngoingStubbing<T> toAnswer(Answer<?> answer) {
        mockitoStubber.addAnswer(answer);
        return new ConsecutiveStubbing<T>(mockitoStubber);
    }

    public List<Invocation> getRegisteredInvocations() {
        //TODO interface for tests
        return mockitoStubber.getInvocations();
    }
}