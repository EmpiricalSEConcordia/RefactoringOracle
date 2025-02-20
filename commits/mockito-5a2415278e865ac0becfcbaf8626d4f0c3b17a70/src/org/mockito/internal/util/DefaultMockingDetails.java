package org.mockito.internal.util;

import org.mockito.MockingDetails;

/**
 * Class to inspect any object, and identify whether a particular object is either a mock or a spy.  This is
 * a wrapper for {@link org.mockito.internal.util.MockUtil}.
 */
public class DefaultMockingDetails extends MockingDetails {

    private Object toInspect;
    private MockUtil delegate;

    public DefaultMockingDetails(Object toInspect, MockUtil delegate){
        this.toInspect = toInspect;
        this.delegate = delegate;
    }
    /**
     * Find out whether the object is a mock.
     * @return true if the object is a mock or a spy.
     */
    public boolean isMock(){
        return delegate.isMock( toInspect );
    }

    /**
     * Find out whether the object is a spy.
     * @return true if the object is a spy.
     */
    public boolean isSpy(){
        return delegate.isSpy( toInspect );
    }
}

