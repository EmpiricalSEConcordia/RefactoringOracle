/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package org.elasticsearch.index.mapper.core;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.Version;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.Mapper;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.ParseContext;
import org.elasticsearch.index.mapper.internal.AllFieldMapper;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.apache.lucene.index.IndexOptions.NONE;
import static org.elasticsearch.index.mapper.core.TypeParsers.parseMultiField;
import static org.elasticsearch.index.mapper.core.TypeParsers.parseTextField;

public class StringFieldMapper extends FieldMapper implements AllFieldMapper.IncludeInAll {

    public static final String CONTENT_TYPE = "string";
    private static final int POSITION_INCREMENT_GAP_USE_ANALYZER = -1;

    public static class Defaults {
        public static final MappedFieldType FIELD_TYPE = new StringFieldType();

        static {
            FIELD_TYPE.freeze();
        }

        // NOTE, when adding defaults here, make sure you add them in the builder
        public static final String NULL_VALUE = null;

        public static final int IGNORE_ABOVE = -1;
    }

    public static class Builder extends FieldMapper.Builder<Builder, StringFieldMapper> {

        protected String nullValue = Defaults.NULL_VALUE;

        /**
         * The distance between tokens from different values in the same field.
         * POSITION_INCREMENT_GAP_USE_ANALYZER means default to the analyzer's
         * setting which in turn defaults to Defaults.POSITION_INCREMENT_GAP.
         */
        protected int positionIncrementGap = POSITION_INCREMENT_GAP_USE_ANALYZER;

        protected int ignoreAbove = Defaults.IGNORE_ABOVE;

        public Builder(String name) {
            super(name, Defaults.FIELD_TYPE, Defaults.FIELD_TYPE);
            builder = this;
        }

        @Override
        public Builder searchAnalyzer(NamedAnalyzer searchAnalyzer) {
            super.searchAnalyzer(searchAnalyzer);
            return this;
        }

        public Builder positionIncrementGap(int positionIncrementGap) {
            this.positionIncrementGap = positionIncrementGap;
            return this;
        }

        public Builder ignoreAbove(int ignoreAbove) {
            this.ignoreAbove = ignoreAbove;
            return this;
        }

        @Override
        public StringFieldMapper build(BuilderContext context) {
            if (positionIncrementGap != POSITION_INCREMENT_GAP_USE_ANALYZER) {
                fieldType.setIndexAnalyzer(new NamedAnalyzer(fieldType.indexAnalyzer(), positionIncrementGap));
                fieldType.setSearchAnalyzer(new NamedAnalyzer(fieldType.searchAnalyzer(), positionIncrementGap));
                fieldType.setSearchQuoteAnalyzer(new NamedAnalyzer(fieldType.searchQuoteAnalyzer(), positionIncrementGap));
            }
            // if the field is not analyzed, then by default, we should omit norms and have docs only
            // index options, as probably what the user really wants
            // if they are set explicitly, we will use those values
            // we also change the values on the default field type so that toXContent emits what
            // differs from the defaults
            if (fieldType.indexOptions() != IndexOptions.NONE && !fieldType.tokenized()) {
                defaultFieldType.setOmitNorms(true);
                defaultFieldType.setIndexOptions(IndexOptions.DOCS);
                if (!omitNormsSet && fieldType.boost() == 1.0f) {
                    fieldType.setOmitNorms(true);
                }
                if (!indexOptionsSet) {
                    fieldType.setIndexOptions(IndexOptions.DOCS);
                }
            }
            setupFieldType(context);
            StringFieldMapper fieldMapper = new StringFieldMapper(
                    name, fieldType, defaultFieldType, positionIncrementGap, ignoreAbove,
                    context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo);
            return fieldMapper.includeInAll(includeInAll);
        }
    }

    public static class TypeParser implements Mapper.TypeParser {
        @Override
        public Mapper.Builder parse(String fieldName, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException {
            StringFieldMapper.Builder builder = new StringFieldMapper.Builder(fieldName);
            // hack for the fact that string can't just accept true/false for
            // the index property and still accepts no/not_analyzed/analyzed
            final Object index = node.remove("index");
            if (index != null) {
                final String normalizedIndex = Strings.toUnderscoreCase(index.toString());
                switch (normalizedIndex) {
                case "analyzed":
                    builder.tokenized(true);
                    node.put("index", true);
                    break;
                case "not_analyzed":
                    builder.tokenized(false);
                    node.put("index", true);
                    break;
                case "no":
                    node.put("index", false);
                    break;
                default:
                    throw new IllegalArgumentException("Can't parse [index] value [" + index + "] for field [" + fieldName + "], expected [true], [false], [no], [not_analyzed] or [analyzed]");
                }
            }
            parseTextField(builder, fieldName, node, parserContext);
            for (Iterator<Map.Entry<String, Object>> iterator = node.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<String, Object> entry = iterator.next();
                String propName = Strings.toUnderscoreCase(entry.getKey());
                Object propNode = entry.getValue();
                if (propName.equals("null_value")) {
                    if (propNode == null) {
                        throw new MapperParsingException("Property [null_value] cannot be null.");
                    }
                    builder.nullValue(propNode.toString());
                    iterator.remove();
                } else if (propName.equals("position_increment_gap")) {
                    int newPositionIncrementGap = XContentMapValues.nodeIntegerValue(propNode, -1);
                    if (newPositionIncrementGap < 0) {
                        throw new MapperParsingException("positions_increment_gap less than 0 aren't allowed.");
                    }
                    builder.positionIncrementGap(newPositionIncrementGap);
                    // we need to update to actual analyzers if they are not set in this case...
                    // so we can inject the position increment gap...
                    if (builder.fieldType().indexAnalyzer() == null) {
                        builder.fieldType().setIndexAnalyzer(parserContext.analysisService().defaultIndexAnalyzer());
                    }
                    if (builder.fieldType().searchAnalyzer() == null) {
                        builder.fieldType().setSearchAnalyzer(parserContext.analysisService().defaultSearchAnalyzer());
                    }
                    if (builder.fieldType().searchQuoteAnalyzer() == null) {
                        builder.fieldType().setSearchQuoteAnalyzer(parserContext.analysisService().defaultSearchQuoteAnalyzer());
                    }
                    iterator.remove();
                } else if (propName.equals("ignore_above")) {
                    builder.ignoreAbove(XContentMapValues.nodeIntegerValue(propNode, -1));
                    iterator.remove();
                } else if (parseMultiField(builder, fieldName, parserContext, propName, propNode)) {
                    iterator.remove();
                }
            }
            return builder;
        }
    }

    public static final class StringFieldType extends MappedFieldType {

        public StringFieldType() {}

        protected StringFieldType(StringFieldType ref) {
            super(ref);
        }

        public StringFieldType clone() {
            return new StringFieldType(this);
        }

        @Override
        public String typeName() {
            return CONTENT_TYPE;
        }

        @Override
        public String value(Object value) {
            if (value == null) {
                return null;
            }
            return value.toString();
        }

        @Override
        public Query nullValueQuery() {
            if (nullValue() == null) {
                return null;
            }
            return termQuery(nullValue(), null);
        }
    }

    private Boolean includeInAll;
    private int positionIncrementGap;
    private int ignoreAbove;

    protected StringFieldMapper(String simpleName, MappedFieldType fieldType, MappedFieldType defaultFieldType,
                                int positionIncrementGap, int ignoreAbove,
                                Settings indexSettings, MultiFields multiFields, CopyTo copyTo) {
        super(simpleName, fieldType, defaultFieldType, indexSettings, multiFields, copyTo);
        if (fieldType.tokenized() && fieldType.indexOptions() != NONE && fieldType().hasDocValues()) {
            throw new MapperParsingException("Field [" + fieldType.name() + "] cannot be analyzed and have doc values");
        }
        this.positionIncrementGap = positionIncrementGap;
        this.ignoreAbove = ignoreAbove;
    }

    @Override
    protected StringFieldMapper clone() {
        return (StringFieldMapper) super.clone();
    }

    @Override
    public StringFieldMapper includeInAll(Boolean includeInAll) {
        if (includeInAll != null) {
            StringFieldMapper clone = clone();
            clone.includeInAll = includeInAll;
            return clone;
        } else {
            return this;
        }
    }

    @Override
    public StringFieldMapper includeInAllIfNotSet(Boolean includeInAll) {
        if (includeInAll != null && this.includeInAll == null) {
            StringFieldMapper clone = clone();
            clone.includeInAll = includeInAll;
            return clone;
        } else {
            return this;
        }
    }

    @Override
    public StringFieldMapper unsetIncludeInAll() {
        if (includeInAll != null) {
            StringFieldMapper clone = clone();
            clone.includeInAll = null;
            return clone;
        } else {
            return this;
        }
    }

    @Override
    protected boolean customBoost() {
        return true;
    }

    public int getPositionIncrementGap() {
        return this.positionIncrementGap;
    }

    public int getIgnoreAbove() {
        return ignoreAbove;
    }

    @Override
    protected void parseCreateField(ParseContext context, List<Field> fields) throws IOException {
        ValueAndBoost valueAndBoost = parseCreateFieldForString(context, fieldType().nullValueAsString(), fieldType().boost());
        if (valueAndBoost.value() == null) {
            return;
        }
        if (ignoreAbove > 0 && valueAndBoost.value().length() > ignoreAbove) {
            return;
        }
        if (context.includeInAll(includeInAll, this)) {
            context.allEntries().addText(fieldType().name(), valueAndBoost.value(), valueAndBoost.boost());
        }

        if (fieldType().indexOptions() != IndexOptions.NONE || fieldType().stored()) {
            Field field = new Field(fieldType().name(), valueAndBoost.value(), fieldType());
            field.setBoost(valueAndBoost.boost());
            fields.add(field);
        }
        if (fieldType().hasDocValues()) {
            fields.add(new SortedSetDocValuesField(fieldType().name(), new BytesRef(valueAndBoost.value())));
        }
    }

    /**
     * Parse a field as though it were a string.
     * @param context parse context used during parsing
     * @param nullValue value to use for null
     * @param defaultBoost default boost value returned unless overwritten in the field
     * @return the parsed field and the boost either parsed or defaulted
     * @throws IOException if thrown while parsing
     */
    public static ValueAndBoost parseCreateFieldForString(ParseContext context, String nullValue, float defaultBoost) throws IOException {
        if (context.externalValueSet()) {
            return new ValueAndBoost(context.externalValue().toString(), defaultBoost);
        }
        XContentParser parser = context.parser();
        if (parser.currentToken() == XContentParser.Token.VALUE_NULL) {
            return new ValueAndBoost(nullValue, defaultBoost);
        }
        if (parser.currentToken() == XContentParser.Token.START_OBJECT
                && Version.indexCreated(context.indexSettings()).before(Version.V_5_0_0)) {
            XContentParser.Token token;
            String currentFieldName = null;
            String value = nullValue;
            float boost = defaultBoost;
            while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                if (token == XContentParser.Token.FIELD_NAME) {
                    currentFieldName = parser.currentName();
                } else {
                    if ("value".equals(currentFieldName) || "_value".equals(currentFieldName)) {
                        value = parser.textOrNull();
                    } else if ("boost".equals(currentFieldName) || "_boost".equals(currentFieldName)) {
                        boost = parser.floatValue();
                    } else {
                        throw new IllegalArgumentException("unknown property [" + currentFieldName + "]");
                    }
                }
            }
            return new ValueAndBoost(value, boost);
        }
        return new ValueAndBoost(parser.textOrNull(), defaultBoost);
    }

    @Override
    protected String contentType() {
        return CONTENT_TYPE;
    }

    @Override
    protected void doMerge(Mapper mergeWith, boolean updateAllTypes) {
        super.doMerge(mergeWith, updateAllTypes);
        this.includeInAll = ((StringFieldMapper) mergeWith).includeInAll;
        this.ignoreAbove = ((StringFieldMapper) mergeWith).ignoreAbove;
    }

    @Override
    protected String indexTokenizeOption(boolean indexed, boolean tokenized) {
        if (!indexed) {
            return "no";
        } else if (tokenized) {
            return "analyzed";
        } else {
            return "not_analyzed";
        }
    }

    @Override
    protected void doXContentBody(XContentBuilder builder, boolean includeDefaults, Params params) throws IOException {
        super.doXContentBody(builder, includeDefaults, params);
        doXContentAnalyzers(builder, includeDefaults);

        if (includeDefaults || fieldType().nullValue() != null) {
            builder.field("null_value", fieldType().nullValue());
        }
        if (includeInAll != null) {
            builder.field("include_in_all", includeInAll);
        } else if (includeDefaults) {
            builder.field("include_in_all", false);
        }

        if (includeDefaults || positionIncrementGap != POSITION_INCREMENT_GAP_USE_ANALYZER) {
            builder.field("position_increment_gap", positionIncrementGap);
        }

        if (includeDefaults || ignoreAbove != Defaults.IGNORE_ABOVE) {
            builder.field("ignore_above", ignoreAbove);
        }
    }

    /**
     * Parsed value and boost to be returned from {@link #parseCreateFieldForString}.
     */
    public static class ValueAndBoost {
        private final String value;
        private final float boost;

        public ValueAndBoost(String value, float boost) {
            this.value = value;
            this.boost = boost;
        }

        /**
         * Value of string field.
         * @return value of string field
         */
        public String value() {
            return value;
        }

        /**
         * Boost either parsed from the document or defaulted.
         * @return boost either parsed from the document or defaulted
         */
        public float boost() {
            return boost;
        }
    }
}
