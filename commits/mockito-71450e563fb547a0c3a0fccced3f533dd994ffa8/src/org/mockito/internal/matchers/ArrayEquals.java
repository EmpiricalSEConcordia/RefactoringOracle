/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.matchers;

import java.lang.reflect.Array;
import java.util.Arrays;

public class ArrayEquals extends Equals {

    public ArrayEquals(Object wanted) {
        super(wanted);
    }

    public boolean matches(Object actual) {
        Object wanted = getWanted();
        if (wanted == null) {
            return super.matches(actual);
        } else if (wanted instanceof boolean[]
                && (actual == null || actual instanceof boolean[])) {
            return Arrays.equals((boolean[]) wanted, (boolean[]) actual);
        } else if (wanted instanceof byte[]
                && (actual == null || actual instanceof byte[])) {
            return Arrays.equals((byte[]) wanted, (byte[]) actual);
        } else if (wanted instanceof char[]
                && (actual == null || actual instanceof char[])) {
            return Arrays.equals((char[]) wanted, (char[]) actual);
        } else if (wanted instanceof double[]
                && (actual == null || actual instanceof double[])) {
            return Arrays.equals((double[]) wanted, (double[]) actual);
        } else if (wanted instanceof float[]
                && (actual == null || actual instanceof float[])) {
            return Arrays.equals((float[]) wanted, (float[]) actual);
        } else if (wanted instanceof int[]
                && (actual == null || actual instanceof int[])) {
            return Arrays.equals((int[]) wanted, (int[]) actual);
        } else if (wanted instanceof long[]
                && (actual == null || actual instanceof long[])) {
            return Arrays.equals((long[]) wanted, (long[]) actual);
        } else if (wanted instanceof short[]
                && (actual == null || actual instanceof short[])) {
            return Arrays.equals((short[]) wanted, (short[]) actual);
        } else if (wanted instanceof Object[]
                && (actual == null || actual instanceof Object[])) {
            return Arrays.equals((Object[]) wanted, (Object[]) actual);
        } else {
            throw new IllegalArgumentException("Something went really wrong. Arguments passed to ArrayEquals have to be an array or null!");
        }
    }

    public void appendTo(StringBuilder buffer) {
        if (getWanted() != null && getWanted().getClass().isArray()) {
            appendArray(createObjectArray(getWanted()), buffer);
        } else {
            super.appendTo(buffer);
        }
    }

    private void appendArray(Object[] array, StringBuilder buffer) {
        buffer.append("[");
        for (int i = 0; i < array.length; i++) {
            new Equals(array[i]).appendTo(buffer);
            if (i != array.length - 1) {
                buffer.append(", ");
            }
        }
        buffer.append("]");
    }

    public static Object[] createObjectArray(Object array) {
        if (array instanceof Object[]) {
            return (Object[]) array;
        }
        Object[] result = new Object[Array.getLength(array)];
        for (int i = 0; i < Array.getLength(array); i++) {
            result[i] = Array.get(array, i);
        }
        return result;
    }
}
