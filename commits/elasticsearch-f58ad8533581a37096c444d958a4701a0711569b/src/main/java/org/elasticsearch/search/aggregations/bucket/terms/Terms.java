/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.aggregations.bucket.terms;

import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.support.ScriptValueType;

import java.util.Collection;
import java.util.Comparator;

/**
 *
 */
public interface Terms extends Aggregation, Iterable<Terms.Bucket> {

    static enum ValueType {

        STRING(ScriptValueType.STRING),
        LONG(ScriptValueType.LONG),
        DOUBLE(ScriptValueType.DOUBLE);

        final ScriptValueType scriptValueType;

        private ValueType(ScriptValueType scriptValueType) {
            this.scriptValueType = scriptValueType;
        }

        static ValueType resolveType(String type) {
            if ("string".equals(type)) {
                return STRING;
            }
            if ("double".equals(type) || "float".equals(type)) {
                return DOUBLE;
            }
            if ("long".equals(type) || "integer".equals(type) || "short".equals(type) || "byte".equals(type)) {
                return LONG;
            }
            return null;
        }
    }

    static abstract class Bucket implements org.elasticsearch.search.aggregations.bucket.Bucket {

        public abstract Text getKey();

        public abstract Number getKeyAsNumber();

        abstract int compareTerm(Terms.Bucket other);

    }

    Collection<Bucket> buckets();

    Bucket getByTerm(String term);


    /**
     *
     */
    static abstract class Order implements ToXContent {

        /**
         * @return a bucket ordering strategy that sorts buckets by their document counts (ascending or descending)
         */
        public static Order count(boolean asc) {
            return asc ? InternalOrder.COUNT_ASC : InternalOrder.COUNT_DESC;
        }

        /**
         * @return a bucket ordering strategy that sorts buckets by their terms (ascending or descending)
         */
        public static Order term(boolean asc) {
            return asc ? InternalOrder.TERM_ASC : InternalOrder.TERM_DESC;
        }

        /**
         * Creates a bucket ordering strategy which sorts buckets based on a single-valued calc get
         *
         * @param   aggregationName the name of the get
         * @param   asc             The direction of the order (ascending or descending)
         */
        public static Order aggregation(String aggregationName, boolean asc) {
            return new InternalOrder.Aggregation(aggregationName, null, asc);
        }

        /**
         * Creates a bucket ordering strategy which sorts buckets based on a multi-valued calc get
         *
         * @param   aggregationName the name of the get
         * @param   metricName       The name of the value of the multi-value get by which the sorting will be applied
         * @param   asc             The direction of the order (ascending or descending)
         */
        public static Order aggregation(String aggregationName, String metricName, boolean asc) {
            return new InternalOrder.Aggregation(aggregationName, metricName, asc);
        }

        /**
         * @return  A comparator for the bucket based on the given terms aggregator. The comparator is used in two phases:
         *
         *          - aggregation phase, where each shard builds a list of term buckets to be sent to the coordinating node.
         *            In this phase, the passed in aggregator will be the terms aggregator that aggregates the buckets on the
         *            shard level.
         *
         *          - reduce phase, where the coordinating node gathers all the buckets from all the shards and reduces them
         *            to a final bucket list. In this case, the passed in aggregator will be {@code null}
         */
        protected abstract Comparator<Bucket> comparator(Aggregator aggregator);

    }
}
