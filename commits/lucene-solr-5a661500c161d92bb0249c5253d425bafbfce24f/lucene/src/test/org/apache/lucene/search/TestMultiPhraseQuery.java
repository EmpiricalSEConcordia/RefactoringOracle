package org.apache.lucene.search;

/**
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

import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.MockRAMDirectory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Collections;

/**
 * This class tests the MultiPhraseQuery class.
 *
 *
 */
public class TestMultiPhraseQuery extends LuceneTestCase
{
    public TestMultiPhraseQuery(String name) {
        super(name);
    }

    public void testPhrasePrefix() throws IOException {
        MockRAMDirectory indexStore = new MockRAMDirectory();
        IndexWriter writer = new IndexWriter(indexStore, new IndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer()));
        add("blueberry pie", writer);
        add("blueberry strudel", writer);
        add("blueberry pizza", writer);
        add("blueberry chewing gum", writer);
        add("bluebird pizza", writer);
        add("bluebird foobar pizza", writer);
        add("piccadilly circus", writer);
        writer.optimize();
        writer.close();

        IndexSearcher searcher = new IndexSearcher(indexStore, true);

        // search for "blueberry pi*":
        MultiPhraseQuery query1 = new MultiPhraseQuery();
        // search for "strawberry pi*":
        MultiPhraseQuery query2 = new MultiPhraseQuery();
        query1.add(new Term("body", "blueberry"));
        query2.add(new Term("body", "strawberry"));

        LinkedList<Term> termsWithPrefix = new LinkedList<Term>();
        IndexReader ir = IndexReader.open(indexStore, true);

        // this TermEnum gives "piccadilly", "pie" and "pizza".
        String prefix = "pi";
        TermEnum te = ir.terms(new Term("body", prefix));
        do {
            if (te.term().text().startsWith(prefix))
            {
                termsWithPrefix.add(te.term());
            }
        } while (te.next());

        query1.add(termsWithPrefix.toArray(new Term[0]));
        assertEquals("body:\"blueberry (piccadilly pie pizza)\"", query1.toString());
        query2.add(termsWithPrefix.toArray(new Term[0]));
        assertEquals("body:\"strawberry (piccadilly pie pizza)\"", query2.toString());

        ScoreDoc[] result;
        result = searcher.search(query1, null, 1000).scoreDocs;
        assertEquals(2, result.length);
        result = searcher.search(query2, null, 1000).scoreDocs;
        assertEquals(0, result.length);

        // search for "blue* pizza":
        MultiPhraseQuery query3 = new MultiPhraseQuery();
        termsWithPrefix.clear();
        prefix = "blue";
        te = ir.terms(new Term("body", prefix));
        do {
            if (te.term().text().startsWith(prefix))
            {
                termsWithPrefix.add(te.term());
            }
        } while (te.next());
        ir.close();
        query3.add(termsWithPrefix.toArray(new Term[0]));
        query3.add(new Term("body", "pizza"));

        result = searcher.search(query3, null, 1000).scoreDocs;
        assertEquals(2, result.length); // blueberry pizza, bluebird pizza
        assertEquals("body:\"(blueberry bluebird) pizza\"", query3.toString());

        // test slop:
        query3.setSlop(1);
        result = searcher.search(query3, null, 1000).scoreDocs;
        assertEquals(3, result.length); // blueberry pizza, bluebird pizza, bluebird foobar pizza

        MultiPhraseQuery query4 = new MultiPhraseQuery();
        try {
          query4.add(new Term("field1", "foo"));
          query4.add(new Term("field2", "foobar"));
          fail();
        } catch(IllegalArgumentException e) {
          // okay, all terms must belong to the same field
        }
        
        searcher.close();
        indexStore.close();

    }
    
    private void add(String s, IndexWriter writer) throws IOException {
      Document doc = new Document();
      doc.add(new Field("body", s, Field.Store.YES, Field.Index.ANALYZED));
      writer.addDocument(doc);
    }
    
    public void testBooleanQueryContainingSingleTermPrefixQuery() throws IOException {
      // this tests against bug 33161 (now fixed)
      // In order to cause the bug, the outer query must have more than one term 
      // and all terms required.
      // The contained PhraseMultiQuery must contain exactly one term array.

      MockRAMDirectory indexStore = new MockRAMDirectory();
      IndexWriter writer = new IndexWriter(indexStore, new IndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer()));
      add("blueberry pie", writer);
      add("blueberry chewing gum", writer);
      add("blue raspberry pie", writer);
      writer.optimize();
      writer.close();

      IndexSearcher searcher = new IndexSearcher(indexStore, true);
      // This query will be equivalent to +body:pie +body:"blue*"
      BooleanQuery q = new BooleanQuery();
      q.add(new TermQuery(new Term("body", "pie")), BooleanClause.Occur.MUST);

      MultiPhraseQuery trouble = new MultiPhraseQuery();
      trouble.add(new Term[] {
          new Term("body", "blueberry"),
          new Term("body", "blue")
      });
      q.add(trouble, BooleanClause.Occur.MUST);

      // exception will be thrown here without fix
      ScoreDoc[] hits = searcher.search(q, null, 1000).scoreDocs;

      assertEquals("Wrong number of hits", 2, hits.length);
      searcher.close();
      indexStore.close();
  }
    
  public void testPhrasePrefixWithBooleanQuery() throws IOException {
    MockRAMDirectory indexStore = new MockRAMDirectory();
    IndexWriter writer = new IndexWriter(indexStore, new IndexWriterConfig(
        TEST_VERSION_CURRENT, new MockAnalyzer()));
    add("This is a test", "object", writer);
    add("a note", "note", writer);
    writer.close();
    
    IndexSearcher searcher = new IndexSearcher(indexStore, true);

    // This query will be equivalent to +type:note +body:"a t*"
    BooleanQuery q = new BooleanQuery();
    q.add(new TermQuery(new Term("type", "note")), BooleanClause.Occur.MUST);

    MultiPhraseQuery trouble = new MultiPhraseQuery();
    trouble.add(new Term("body", "a"));
    trouble.add(new Term[] { new Term("body", "test"), new Term("body", "this") });
    q.add(trouble, BooleanClause.Occur.MUST);

    // exception will be thrown here without fix for #35626:
    ScoreDoc[] hits = searcher.search(q, null, 1000).scoreDocs;
    assertEquals("Wrong number of hits", 0, hits.length);
    searcher.close();
    indexStore.close();
  }

  public void testNoDocs() throws Exception {
    MockRAMDirectory indexStore = new MockRAMDirectory();
    IndexWriter writer = new IndexWriter(indexStore, new MockAnalyzer(), true, IndexWriter.MaxFieldLength.LIMITED);
    add("a note", "note", writer);
    writer.close();

    IndexSearcher searcher = new IndexSearcher(indexStore, true);

    MultiPhraseQuery q = new MultiPhraseQuery();
    q.add(new Term("body", "a"));
    q.add(new Term[] { new Term("body", "nope"), new Term("body", "nope") });
    assertEquals("Wrong number of hits", 0, searcher.search(q, null, 1).totalHits);
    searcher.close();
    indexStore.close();
  }
  
  public void testHashCodeAndEquals(){
    MultiPhraseQuery query1 = new MultiPhraseQuery();
    MultiPhraseQuery query2 = new MultiPhraseQuery();
    
    assertEquals(query1.hashCode(), query2.hashCode());
    assertEquals(query1,query2);
    
    Term term1= new Term("someField","someText");
    
    query1.add(term1);
    query2.add(term1);
    
    assertEquals(query1.hashCode(), query2.hashCode());
    assertEquals(query1,query2);
    
    Term term2= new Term("someField","someMoreText");
    
    query1.add(term2);
    
    assertFalse(query1.hashCode()==query2.hashCode());
    assertFalse(query1.equals(query2));
    
    query2.add(term2);
    
    assertEquals(query1.hashCode(), query2.hashCode());
    assertEquals(query1,query2);
  }

  
  private void add(String s, String type, IndexWriter writer) throws IOException {
    Document doc = new Document();
    doc.add(new Field("body", s, Field.Store.YES, Field.Index.ANALYZED));
    doc.add(new Field("type", type, Field.Store.YES, Field.Index.NOT_ANALYZED));
    writer.addDocument(doc);
  }

}
