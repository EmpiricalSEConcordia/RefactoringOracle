/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.field.data.ints;

import org.elasticsearch.index.field.data.FieldDataOptions;
import org.elasticsearch.index.field.data.doubles.DoubleFieldData;
import org.elasticsearch.util.ThreadLocals;

/**
 * @author kimchy (shay.banon)
 */
public class MultiValueIntFieldData extends IntFieldData {

    private static final int VALUE_CACHE_SIZE = 10;

    private ThreadLocal<ThreadLocals.CleanableValue<double[][]>> doublesValuesCache = new ThreadLocal<ThreadLocals.CleanableValue<double[][]>>() {
        @Override protected ThreadLocals.CleanableValue<double[][]> initialValue() {
            double[][] value = new double[VALUE_CACHE_SIZE][];
            for (int i = 0; i < value.length; i++) {
                value[i] = new double[i];
            }
            return new ThreadLocals.CleanableValue<double[][]>(value);
        }
    };

    private ThreadLocal<ThreadLocals.CleanableValue<int[][]>> valuesCache = new ThreadLocal<ThreadLocals.CleanableValue<int[][]>>() {
        @Override protected ThreadLocals.CleanableValue<int[][]> initialValue() {
            int[][] value = new int[VALUE_CACHE_SIZE][];
            for (int i = 0; i < value.length; i++) {
                value[i] = new int[i];
            }
            return new ThreadLocals.CleanableValue<int[][]>(value);
        }
    };

    // order with value 0 indicates no value
    private final int[][] order;

    public MultiValueIntFieldData(String fieldName, FieldDataOptions options, int[][] order, int[] values, int[] freqs) {
        super(fieldName, options, values, freqs);
        this.order = order;
    }

    @Override public boolean multiValued() {
        return true;
    }

    @Override public boolean hasValue(int docId) {
        return order[docId] != null;
    }

    @Override public void forEachValueInDoc(int docId, StringValueInDocProc proc) {
        int[] docOrders = order[docId];
        if (docOrders == null) {
            return;
        }
        for (int docOrder : docOrders) {
            proc.onValue(docId, Integer.toString(values[docOrder]));
        }
    }

    @Override public void forEachValueInDoc(int docId, DoubleValueInDocProc proc) {
        int[] docOrders = order[docId];
        if (docOrders == null) {
            return;
        }
        for (int docOrder : docOrders) {
            proc.onValue(docId, values[docOrder]);
        }
    }

    @Override public double[] doubleValues(int docId) {
        int[] docOrders = order[docId];
        if (docOrders == null) {
            return DoubleFieldData.EMPTY_DOUBLE_ARRAY;
        }
        double[] doubles;
        if (docOrders.length < VALUE_CACHE_SIZE) {
            doubles = doublesValuesCache.get().get()[docOrders.length];
        } else {
            doubles = new double[docOrders.length];
        }
        for (int i = 0; i < docOrders.length; i++) {
            doubles[i] = values[docOrders[i]];
        }
        return doubles;
    }

    @Override public int value(int docId) {
        int[] docOrders = order[docId];
        if (docOrders == null) {
            return 0;
        }
        return values[docOrders[0]];
    }

    @Override public int[] values(int docId) {
        int[] docOrders = order[docId];
        if (docOrders == null) {
            return EMPTY_INT_ARRAY;
        }
        int[] ints;
        if (docOrders.length < VALUE_CACHE_SIZE) {
            ints = valuesCache.get().get()[docOrders.length];
        } else {
            ints = new int[docOrders.length];
        }
        for (int i = 0; i < docOrders.length; i++) {
            ints[i] = values[docOrders[i]];
        }
        return ints;
    }
}