/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.configuration;

import org.mockito.configuration.ReturnValues;
import org.mockito.exceptions.base.MockitoException;

/**
 * Singleton implementation of MockitoConfiguration
 */
public class Configuration implements MockitoConfiguration {
    
    public static final ThreadLocal<Configuration> CONFIG = new ThreadLocal<Configuration>();

    private ReturnValues returnValues;
    
    private Configuration() {
        resetReturnValues();
    }
    
    /**
     * gets the singleton instance of a configuration
     */
    public static Configuration instance() {
        if (CONFIG.get() == null) {
            CONFIG.set(new Configuration());
        }
        return CONFIG.get();
    }
    
    /* (non-Javadoc)
     * @see org.mockito.internal.configuration.MockitoConfiguration#getReturnValues()
     */
    public ReturnValues getReturnValues() {
        return returnValues;
    }

    /* (non-Javadoc)
     * @see org.mockito.internal.configuration.MockitoConfiguration#setReturnValues(org.mockito.configuration.ReturnValues)
     */
    public void setReturnValues(ReturnValues returnValues) {
        if (returnValues == null) {
            throw new MockitoException("Cannot set null ReturnValues!");
        }
        this.returnValues = returnValues;
    }

    /* (non-Javadoc)
     * @see org.mockito.internal.configuration.MockitoConfiguration#resetReturnValues()
     */
    public void resetReturnValues() {
        returnValues = MockitoProperties.DEFAULT_RETURN_VALUES;
    }
}