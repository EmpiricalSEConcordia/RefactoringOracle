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

package org.elasticsearch.search.searchafter;

import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.NamedWriteableAwareStreamInput;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.index.query.MatchAllQueryParser;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.indices.query.IndicesQueriesRegistry;
import org.elasticsearch.test.ESTestCase;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;

public class SearchAfterBuilderTests extends ESTestCase {
    private static final int NUMBER_OF_TESTBUILDERS = 20;
    private static NamedWriteableRegistry namedWriteableRegistry;
    private static IndicesQueriesRegistry indicesQueriesRegistry;

    /**
     * setup for the whole base test class
     */
    @BeforeClass
    public static void init() {
        namedWriteableRegistry = new NamedWriteableRegistry();
        indicesQueriesRegistry = new IndicesQueriesRegistry(Settings.settingsBuilder().build(),
            Collections.singletonMap("match_all", new MatchAllQueryParser()));
    }

    @AfterClass
    public static void afterClass() throws Exception {
        namedWriteableRegistry = null;
        indicesQueriesRegistry = null;
    }

    private final SearchAfterBuilder randomSearchFromBuilder() throws IOException {
        int numSearchFrom = randomIntBetween(1, 10);
        SearchAfterBuilder searchAfterBuilder = new SearchAfterBuilder();
        Object[] values = new Object[numSearchFrom];
        for (int i = 0; i < numSearchFrom; i++) {
            int branch = randomInt(8);
            switch (branch) {
                case 0:
                    values[i] = randomInt();
                    break;
                case 1:
                    values[i] = randomFloat();
                    break;
                case 2:
                    values[i] = randomLong();
                    break;
                case 3:
                    values[i] = randomDouble();
                    break;
                case 4:
                    values[i] = randomAsciiOfLengthBetween(5, 20);
                    break;
                case 5:
                    values[i] = randomBoolean();
                    break;
                case 6:
                    values[i] = randomByte();
                    break;
                case 7:
                    values[i] = randomShort();
                    break;
                case 8:
                    values[i] = new Text(randomAsciiOfLengthBetween(5, 20));
                    break;
            }
        }
        searchAfterBuilder.setSortValues(values);
        return searchAfterBuilder;
    }

    // We build a json version of the search_after first in order to
    // ensure that every number type remain the same before/after xcontent (de)serialization.
    // This is not a problem because the final type of each field value is extracted from associated sort field.
    // This little trick ensure that equals and hashcode are the same when using the xcontent serialization.
    private final SearchAfterBuilder randomJsonSearchFromBuilder() throws IOException {
        int numSearchAfter = randomIntBetween(1, 10);
        XContentBuilder jsonBuilder = XContentFactory.jsonBuilder();
        jsonBuilder.startObject();
        jsonBuilder.startArray("search_after");
        for (int i = 0; i < numSearchAfter; i++) {
            int branch = randomInt(8);
            switch (branch) {
                case 0:
                    jsonBuilder.value(randomInt());
                    break;
                case 1:
                    jsonBuilder.value(randomFloat());
                    break;
                case 2:
                    jsonBuilder.value(randomLong());
                    break;
                case 3:
                    jsonBuilder.value(randomDouble());
                    break;
                case 4:
                    jsonBuilder.value(randomAsciiOfLengthBetween(5, 20));
                    break;
                case 5:
                    jsonBuilder.value(randomBoolean());
                    break;
                case 6:
                    jsonBuilder.value(randomByte());
                    break;
                case 7:
                    jsonBuilder.value(randomShort());
                    break;
                case 8:
                    jsonBuilder.value(new Text(randomAsciiOfLengthBetween(5, 20)));
                    break;
            }
        }
        jsonBuilder.endArray();
        jsonBuilder.endObject();
        XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(jsonBuilder.bytes());
        parser.nextToken();
        parser.nextToken();
        parser.nextToken();
        return SearchAfterBuilder.PROTOTYPE.fromXContent(parser, null);
    }

    private static SearchAfterBuilder serializedCopy(SearchAfterBuilder original) throws IOException {
        try (BytesStreamOutput output = new BytesStreamOutput()) {
            original.writeTo(output);
            try (StreamInput in = new NamedWriteableAwareStreamInput(StreamInput.wrap(output.bytes()), namedWriteableRegistry)) {
                return SearchAfterBuilder.PROTOTYPE.readFrom(in);
            }
        }
    }

    public void testSerialization() throws Exception {
        for (int runs = 0; runs < NUMBER_OF_TESTBUILDERS; runs++) {
            SearchAfterBuilder original = randomSearchFromBuilder();
            SearchAfterBuilder deserialized = serializedCopy(original);
            assertEquals(deserialized, original);
            assertEquals(deserialized.hashCode(), original.hashCode());
            assertNotSame(deserialized, original);
        }
    }

    public void testEqualsAndHashcode() throws Exception {
        for (int runs = 0; runs < NUMBER_OF_TESTBUILDERS; runs++) {
            SearchAfterBuilder firstBuilder = randomSearchFromBuilder();
            assertFalse("searchFrom is equal to null", firstBuilder.equals(null));
            assertFalse("searchFrom is equal to incompatible type", firstBuilder.equals(""));
            assertTrue("searchFrom is not equal to self", firstBuilder.equals(firstBuilder));
            assertThat("same searchFrom's hashcode returns different values if called multiple times", firstBuilder.hashCode(),
                    equalTo(firstBuilder.hashCode()));

            SearchAfterBuilder secondBuilder = serializedCopy(firstBuilder);
            assertTrue("searchFrom is not equal to self", secondBuilder.equals(secondBuilder));
            assertTrue("searchFrom is not equal to its copy", firstBuilder.equals(secondBuilder));
            assertTrue("equals is not symmetric", secondBuilder.equals(firstBuilder));
            assertThat("searchFrom copy's hashcode is different from original hashcode", secondBuilder.hashCode(), equalTo(firstBuilder.hashCode()));

            SearchAfterBuilder thirdBuilder = serializedCopy(secondBuilder);
            assertTrue("searchFrom is not equal to self", thirdBuilder.equals(thirdBuilder));
            assertTrue("searchFrom is not equal to its copy", secondBuilder.equals(thirdBuilder));
            assertThat("searchFrom copy's hashcode is different from original hashcode", secondBuilder.hashCode(), equalTo(thirdBuilder.hashCode()));
            assertTrue("equals is not transitive", firstBuilder.equals(thirdBuilder));
            assertThat("searchFrom copy's hashcode is different from original hashcode", firstBuilder.hashCode(), equalTo(thirdBuilder.hashCode()));
            assertTrue("searchFrom is not symmetric", thirdBuilder.equals(secondBuilder));
            assertTrue("searchFrom is not symmetric", thirdBuilder.equals(firstBuilder));
        }
    }

    public void testFromXContent() throws Exception {
        QueryParseContext context = new QueryParseContext(indicesQueriesRegistry);
        context.parseFieldMatcher(new ParseFieldMatcher(Settings.EMPTY));
        for (int runs = 0; runs < 20; runs++) {
            SearchAfterBuilder searchAfterBuilder = randomJsonSearchFromBuilder();
            XContentBuilder builder = XContentFactory.contentBuilder(randomFrom(XContentType.values()));
            if (randomBoolean()) {
                builder.prettyPrint();
            }
            builder.startObject();
            searchAfterBuilder.innerToXContent(builder);
            builder.endObject();
            XContentParser parser = XContentHelper.createParser(builder.bytes());
            context.reset(parser);
            parser.nextToken();
            parser.nextToken();
            parser.nextToken();
            SearchAfterBuilder secondSearchAfterBuilder = SearchAfterBuilder.PROTOTYPE.fromXContent(parser, null);
            assertNotSame(searchAfterBuilder, secondSearchAfterBuilder);
            assertEquals(searchAfterBuilder, secondSearchAfterBuilder);
            assertEquals(searchAfterBuilder.hashCode(), secondSearchAfterBuilder.hashCode());
        }
    }

    public void testWithNullValue() throws Exception {
        SearchAfterBuilder builder = new SearchAfterBuilder();
        builder.setSortValues(new Object[] {1, "1", null});
        try {
            serializedCopy(builder);
            fail("Should fail on null values");
        } catch (IOException e) {
            assertThat(e.getMessage(), Matchers.equalTo("Can't handle search_after field value of type [null]"));
        }
    }

    public void testWithNullArray() throws Exception {
        SearchAfterBuilder builder = new SearchAfterBuilder();
        try {
            builder.setSortValues(null);
            fail("Should fail on null array.");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), Matchers.equalTo("Values cannot be null."));
        }
    }

    public void testWithEmptyArray() throws Exception {
        SearchAfterBuilder builder = new SearchAfterBuilder();
        try {
            builder.setSortValues(new Object[0]);
            fail("Should fail on empty array.");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), Matchers.equalTo("Values must contains at least one value."));
        }
    }
}
