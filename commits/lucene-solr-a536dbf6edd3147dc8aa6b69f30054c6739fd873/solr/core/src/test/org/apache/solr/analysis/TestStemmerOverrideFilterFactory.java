package org.apache.solr.analysis;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.solr.core.SolrResourceLoader;

/**
 * Simple tests to ensure the stemmer override filter factory is working.
 */
public class TestStemmerOverrideFilterFactory extends BaseTokenStreamTestCase {
  public void testKeywords() throws IOException {
    // our stemdict stems dogs to 'cat'
    Reader reader = new StringReader("testing dogs");
    Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
    StemmerOverrideFilterFactory factory = new StemmerOverrideFilterFactory();
    Map<String,String> args = new HashMap<String,String>();
    ResourceLoader loader = new SolrResourceLoader("solr/collection1");
    args.put("dictionary", "stemdict.txt");
    factory.setLuceneMatchVersion(TEST_VERSION_CURRENT);
    factory.init(args);
    factory.inform(loader);
    
    TokenStream ts = new PorterStemFilter(factory.create(tokenizer));
    assertTokenStreamContents(ts, new String[] { "test", "cat" });
  }
  
  public void testKeywordsCaseInsensitive() throws IOException {
    Reader reader = new StringReader("testing DoGs");
    Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
    StemmerOverrideFilterFactory factory = new StemmerOverrideFilterFactory();
    Map<String,String> args = new HashMap<String,String>();
    ResourceLoader loader = new SolrResourceLoader("solr/collection1");
    args.put("dictionary", "stemdict.txt");
    args.put("ignoreCase", "true");
    factory.setLuceneMatchVersion(TEST_VERSION_CURRENT);
    factory.init(args);
    factory.inform(loader);
    
    TokenStream ts = new PorterStemFilter(factory.create(tokenizer));
    assertTokenStreamContents(ts, new String[] { "test", "cat" });
  }
}
