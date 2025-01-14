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

package org.elasticsearch.index.mapper.xcontent;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.mapper.AllFieldMapper;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.MergeMappingException;
import org.elasticsearch.util.lucene.Lucene;
import org.elasticsearch.util.lucene.all.AllField;
import org.elasticsearch.util.lucene.all.AllTermQuery;
import org.elasticsearch.util.xcontent.builder.XContentBuilder;

import java.io.IOException;

/**
 * @author kimchy (shay.banon)
 */
public class XContentAllFieldMapper extends XContentFieldMapper<Void> implements AllFieldMapper {

    public static final String CONTENT_TYPE = "_all";

    public static class Defaults extends XContentFieldMapper.Defaults {
        public static final String NAME = AllFieldMapper.NAME;
        public static final String INDEX_NAME = AllFieldMapper.NAME;
        public static final boolean ENABLED = true;
    }


    public static class Builder extends XContentFieldMapper.Builder<Builder, XContentAllFieldMapper> {

        private boolean enabled = Defaults.ENABLED;

        public Builder() {
            super(Defaults.NAME);
            builder = this;
            indexName = Defaults.INDEX_NAME;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        @Override public Builder store(Field.Store store) {
            return super.store(store);
        }

        @Override public Builder termVector(Field.TermVector termVector) {
            return super.termVector(termVector);
        }

        @Override protected Builder indexAnalyzer(NamedAnalyzer indexAnalyzer) {
            return super.indexAnalyzer(indexAnalyzer);
        }

        @Override protected Builder searchAnalyzer(NamedAnalyzer searchAnalyzer) {
            return super.searchAnalyzer(searchAnalyzer);
        }

        @Override public XContentAllFieldMapper build(BuilderContext context) {
            return new XContentAllFieldMapper(name, store, termVector, omitNorms, omitTermFreqAndPositions,
                    indexAnalyzer, searchAnalyzer, enabled);
        }
    }


    private boolean enabled;

    public XContentAllFieldMapper() {
        this(Defaults.NAME, Defaults.STORE, Defaults.TERM_VECTOR, Defaults.OMIT_NORMS, Defaults.OMIT_TERM_FREQ_AND_POSITIONS, null, null, Defaults.ENABLED);
    }

    protected XContentAllFieldMapper(String name, Field.Store store, Field.TermVector termVector, boolean omitNorms, boolean omitTermFreqAndPositions,
                                     NamedAnalyzer indexAnalyzer, NamedAnalyzer searchAnalyzer, boolean enabled) {
        super(new Names(name, name, name, name), Field.Index.ANALYZED, store, termVector, 1.0f, omitNorms, omitTermFreqAndPositions,
                indexAnalyzer, searchAnalyzer);
        this.enabled = enabled;
    }

    public boolean enabled() {
        return this.enabled;
    }

    @Override public Query queryStringTermQuery(Term term) {
        return new AllTermQuery(term);
    }

    @Override public Query fieldQuery(String value) {
        return new AllTermQuery(new Term(names.indexName(), value));
    }

    @Override protected Fieldable parseCreateField(ParseContext context) throws IOException {
        if (!enabled) {
            return null;
        }
        // reset the entries
        context.allEntries().reset();

        Analyzer analyzer = findAnalyzer(context.docMapper());
        return new AllField(names.indexName(), store, termVector, context.allEntries(), analyzer);
    }

    private Analyzer findAnalyzer(DocumentMapper docMapper) {
        Analyzer analyzer = indexAnalyzer;
        if (analyzer == null) {
            analyzer = docMapper.indexAnalyzer();
            if (analyzer == null) {
                analyzer = Lucene.STANDARD_ANALYZER;
            }
        }
        return analyzer;
    }

    @Override public Void value(Fieldable field) {
        return null;
    }

    @Override public String valueAsString(Fieldable field) {
        return null;
    }

    @Override public Object valueForSearch(Fieldable field) {
        return null;
    }

    @Override public String indexedValue(Void value) {
        return null;
    }

    @Override protected String contentType() {
        return CONTENT_TYPE;
    }

    @Override public void toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(CONTENT_TYPE);
        builder.field("enabled", enabled);
        builder.field("store", store.name().toLowerCase());
        builder.field("term_vector", termVector.name().toLowerCase());
        if (indexAnalyzer != null && !indexAnalyzer.name().startsWith("_")) {
            builder.field("index_analyzer", indexAnalyzer.name());
        }
        if (searchAnalyzer != null && !searchAnalyzer.name().startsWith("_")) {
            builder.field("search_analyzer", searchAnalyzer.name());
        }
        builder.endObject();
    }

    @Override public void merge(XContentMapper mergeWith, MergeContext mergeContext) throws MergeMappingException {
        // do nothing here, no merging, but also no exception
    }
}
