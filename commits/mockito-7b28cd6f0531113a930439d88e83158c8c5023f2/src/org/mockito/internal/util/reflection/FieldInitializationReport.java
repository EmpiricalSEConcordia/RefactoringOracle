package org.mockito.internal.util.reflection;

public class FieldInitializationReport {
    private Object fieldInstance;
    private boolean wasInitialized;

    public FieldInitializationReport(Object fieldInstance, boolean wasInitialized) {
        this.fieldInstance = fieldInstance;
        this.wasInitialized = wasInitialized;
    }

    public Object fieldInstance() {
        return fieldInstance;
    }

    public boolean fieldWasInitialized() {
        return wasInitialized;
    }

    public Class<?> fieldClass() {
        return fieldInstance != null ? fieldInstance.getClass() : null;
    }
}

