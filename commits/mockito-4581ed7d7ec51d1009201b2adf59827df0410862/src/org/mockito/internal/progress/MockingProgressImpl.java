/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.progress;

import org.mockito.exceptions.Reporter;
import org.mockito.internal.verification.api.VerificationMode;

@SuppressWarnings("unchecked")
public class MockingProgressImpl implements MockingProgress {
    
    private final Reporter reporter = new Reporter();
    
    OngoingStubbing ongoingStubbing;
    private VerificationMode verificationMode;
    private boolean stubbingInProgress = false;

    public void reportOngoingStubbing(OngoingStubbing ongoingStubbing) {
        this.ongoingStubbing = ongoingStubbing;
    }

    public OngoingStubbing pullOngoingStubbing() {
        OngoingStubbing temp = ongoingStubbing;
        ongoingStubbing = null;
        return temp;
    }
    
    public void verificationStarted(VerificationMode verify) {
        validateState();
        resetOngoingStubbing();
        verificationMode = (VerificationMode) verify;
    }

    /* (non-Javadoc)
     * @see org.mockito.internal.progress.MockingProgress#resetOngoingStubbing()
     */
    public void resetOngoingStubbing() {
        ongoingStubbing = null;
    }

    public VerificationMode pullVerificationMode() {
        VerificationMode temp = verificationMode;
        verificationMode = null;
        return temp;
    }

    public void stubbingStarted() {
        validateState();
        stubbingInProgress = true;
    }

    public void validateState() {
        if (verificationMode != null) {
            verificationMode = null;
            reporter.unfinishedVerificationException();
        }
        
        if (stubbingInProgress) {
            stubbingInProgress = false;
            reporter.unfinishedStubbing();
        }
      
        //TODO LastArguments should be somewhere here...
        LastArguments.instance().validateState();
    }

    public void stubbingCompleted() {
        stubbingInProgress = false;
    }
    
    public String toString() {
        return  "ongoingStubbing: " + ongoingStubbing + 
        ", verificationMode: " + verificationMode +
        ", stubbingInProgress: " + stubbingInProgress;
    }

    public void reset() {
        stubbingInProgress = false;
        verificationMode = null;
        //TODO LastArguments should be somewhere here...
        LastArguments.instance().reset();
    }
}