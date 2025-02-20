package org.apache.lucene.search;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.LuceneTestCase.SuppressCodecs;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.UnicodeUtil;

/**
 * Tests the DocTermOrdsRangeFilter
 */
@SuppressCodecs({"Lucene40", "Lucene41", "Lucene42"}) // needs SORTED_SET
public class TestDocTermOrdsRangeFilter extends LuceneTestCase {
  protected IndexSearcher searcher1;
  protected IndexSearcher searcher2;
  private IndexReader reader;
  private Directory dir;
  protected String fieldName;
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    fieldName = random().nextBoolean() ? "field" : ""; // sometimes use an empty string as field name
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, 
        newIndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.KEYWORD, false))
        .setMaxBufferedDocs(TestUtil.nextInt(random(), 50, 1000)));
    List<String> terms = new ArrayList<>();
    int num = atLeast(200);
    for (int i = 0; i < num; i++) {
      Document doc = new Document();
      doc.add(newStringField("id", Integer.toString(i), Field.Store.NO));
      int numTerms = random().nextInt(4);
      for (int j = 0; j < numTerms; j++) {
        String s = TestUtil.randomUnicodeString(random());
        doc.add(newStringField(fieldName, s, Field.Store.NO));
        doc.add(new SortedSetDocValuesField(fieldName, new BytesRef(s)));
        terms.add(s);
      }
      writer.addDocument(doc);
    }
    
    if (VERBOSE) {
      // utf16 order
      Collections.sort(terms);
      System.out.println("UTF16 order:");
      for(String s : terms) {
        System.out.println("  " + UnicodeUtil.toHexString(s));
      }
    }
    
    int numDeletions = random().nextInt(num/10);
    for (int i = 0; i < numDeletions; i++) {
      writer.deleteDocuments(new Term("id", Integer.toString(random().nextInt(num))));
    }
    
    reader = writer.getReader();
    searcher1 = newSearcher(reader);
    searcher2 = newSearcher(reader);
    writer.shutdown();
  }
  
  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }
  
  /** test a bunch of random ranges */
  public void testRanges() throws Exception {
    int num = atLeast(1000);
    for (int i = 0; i < num; i++) {
      BytesRef lowerVal = new BytesRef(TestUtil.randomUnicodeString(random()));
      BytesRef upperVal = new BytesRef(TestUtil.randomUnicodeString(random()));
      if (upperVal.compareTo(lowerVal) < 0) {
        assertSame(upperVal, lowerVal, random().nextBoolean(), random().nextBoolean());
      } else {
        assertSame(lowerVal, upperVal, random().nextBoolean(), random().nextBoolean());
      }
    }
  }
  
  /** check that the # of hits is the same as if the query
   * is run against the inverted index
   */
  protected void assertSame(BytesRef lowerVal, BytesRef upperVal, boolean includeLower, boolean includeUpper) throws IOException {   
    Query docValues = new ConstantScoreQuery(DocTermOrdsRangeFilter.newBytesRefRange(fieldName, lowerVal, upperVal, includeLower, includeUpper));
    MultiTermQuery inverted = new TermRangeQuery(fieldName, lowerVal, upperVal, includeLower, includeUpper);
    inverted.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_FILTER_REWRITE);
   
    TopDocs invertedDocs = searcher1.search(inverted, 25);
    TopDocs docValuesDocs = searcher2.search(docValues, 25);

    CheckHits.checkEqual(inverted, invertedDocs.scoreDocs, docValuesDocs.scoreDocs);
  }
}
