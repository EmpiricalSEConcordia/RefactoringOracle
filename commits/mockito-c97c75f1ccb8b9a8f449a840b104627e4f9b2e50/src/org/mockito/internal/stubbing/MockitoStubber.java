/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.stubbing;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.progress.MockingProgress;
import org.mockito.stubbing.Answer;

@SuppressWarnings("unchecked")
public class MockitoStubber {

    private final LinkedList<StubbedInvocationMatcher> stubbed = new LinkedList<StubbedInvocationMatcher>();
    private final MockingProgress mockingProgress;
    private final List<Answer> answersForStubbing = new ArrayList<Answer>();

    private InvocationMatcher invocationForStubbing;

    public MockitoStubber(MockingProgress mockingProgress) {
        this.mockingProgress = mockingProgress;
    }

    public void setInvocationForPotentialStubbing(InvocationMatcher invocation) {
        this.invocationForStubbing = invocation;
    }

    public void addAnswer(Answer answer) {
        addAnswer(answer, false);
    }

    public void addConsecutiveAnswer(Answer answer) {
        addAnswer(answer, true);
    }
    
    private void addAnswer(Answer answer, boolean isConsecutive) {
        mockingProgress.stubbingCompleted();
        AnswersValidator answersValidator = new AnswersValidator();
        answersValidator.validate(answer, invocationForStubbing.getInvocation());
        
        if (isConsecutive) {
            stubbed.getFirst().addAnswer(answer);
        } else {
            stubbed.addFirst(new StubbedInvocationMatcher(invocationForStubbing, answer));
        }
    } 
    
    public Object getResultFor(Invocation invocation) throws Throwable {
        return findMatchFor(invocation).answer(invocation);
    }

    public StubbedInvocationMatcher findMatchFor(Invocation invocation) {
        for (StubbedInvocationMatcher s : stubbed) {
            if (s.matches(invocation)) {
                return s;
            }
        }
        
        return null;
    }

    public void addAnswerForVoidMethod(Answer answer) {
        answersForStubbing.add(answer);
    }
    
    public void setAnswersForStubbing(List<Answer> answers) {
        answersForStubbing.addAll(answers);
    }

    public boolean hasAnswersForStubbing() {
        return !answersForStubbing.isEmpty();
    }

    public void setMethodForStubbing(InvocationMatcher invocation) {
        invocationForStubbing = invocation;
        assert hasAnswersForStubbing();
        for (int i = 0; i < answersForStubbing.size(); i++) {
            addAnswer(answersForStubbing.get(i), i != 0);
        }
        answersForStubbing.clear();
    }
}