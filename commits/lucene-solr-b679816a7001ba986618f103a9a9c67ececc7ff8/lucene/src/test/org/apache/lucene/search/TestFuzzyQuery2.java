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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.LuceneTestCase;

/** 
 * Tests the results of fuzzy against pre-recorded output 
 * The format of the file is the following:
 * 
 * Header Row: # of bits: generate 2^n sequential documents 
 * with a value of Integer.toBinaryString
 * 
 * Entries: an entry is a param spec line, a resultCount line, and
 * then 'resultCount' results lines. The results lines are in the
 * expected order.
 * 
 * param spec line: a comma-separated list of params to FuzzyQuery
 *   (query, prefixLen, pqSize, minScore)
 * query = query text as a number (expand with Integer.toBinaryString)
 * prefixLen = prefix length
 * pqSize = priority queue maximum size for TopTermsBoostOnlyBooleanQueryRewrite
 * minScore = minimum similarity
 * 
 * resultCount line: total number of expected hits.
 * 
 * results line: comma-separated docID, score pair
 **/
public class TestFuzzyQuery2 extends LuceneTestCase {
  /** epsilon for score comparisons */
  static final float epsilon = 0.00001f;

  public void testFromTestData() throws Exception {
    InputStream stream = getClass().getResourceAsStream("fuzzyTestData.txt");
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
    
    int bits = Integer.parseInt(reader.readLine());
    int terms = (int) Math.pow(2, bits);
    
    RAMDirectory dir = new RAMDirectory();
    IndexWriter writer = new IndexWriter(dir, new KeywordAnalyzer(),
        IndexWriter.MaxFieldLength.UNLIMITED);
    
    Document doc = new Document();
    Field field = new Field("field", "", Field.Store.NO, Field.Index.ANALYZED);
    doc.add(field);
    
    for (int i = 0; i < terms; i++) {
      field.setValue(Integer.toBinaryString(i));
      writer.addDocument(doc);
    }
    
    writer.optimize();
    writer.close();   
    
    IndexSearcher searcher = new IndexSearcher(dir);
    String line;
    while ((line = reader.readLine()) != null) {
      String params[] = line.split(",");
      String query = Integer.toBinaryString(Integer.parseInt(params[0]));
      int prefix = Integer.parseInt(params[1]);
      int pqSize = Integer.parseInt(params[2]);
      float minScore = Float.parseFloat(params[3]);
      FuzzyQuery q = new FuzzyQuery(new Term("field", query), minScore, prefix);
      q.setRewriteMethod(new MultiTermQuery.TopTermsBoostOnlyBooleanQueryRewrite(pqSize));
      int expectedResults = Integer.parseInt(reader.readLine());
      TopDocs docs = searcher.search(q, expectedResults);
      assertEquals(expectedResults, docs.totalHits);
      for (int i = 0; i < expectedResults; i++) {
        String scoreDoc[] = reader.readLine().split(",");
        assertEquals(Integer.parseInt(scoreDoc[0]), docs.scoreDocs[i].doc);
        assertEquals(Float.parseFloat(scoreDoc[1]), docs.scoreDocs[i].score, epsilon);
      }
    }
    searcher.close();
    dir.close();
  }
  
  /* Code to generate test data
  public static void main(String args[]) throws Exception {
    int bits = 3;
    System.out.println(bits);
    int terms = (int) Math.pow(2, bits);
    
    RAMDirectory dir = new RAMDirectory();
    IndexWriter writer = new IndexWriter(dir, new KeywordAnalyzer(),
        IndexWriter.MaxFieldLength.UNLIMITED);
    
    Document doc = new Document();
    Field field = new Field("field", "", Field.Store.NO, Field.Index.ANALYZED);
    doc.add(field);

    for (int i = 0; i < terms; i++) {
      field.setValue(Integer.toBinaryString(i));
      writer.addDocument(doc);
    }
    
    writer.optimize();
    writer.close();

    IndexSearcher searcher = new IndexSearcher(dir);
    for (int prefix = 0; prefix < bits; prefix++)
      for (int pqsize = 1; pqsize <= terms; pqsize++)
        for (float minscore = 0.1F; minscore < 1F; minscore += 0.2F)
          for (int query = 0; query < terms; query++) {
            FuzzyQuery q = new FuzzyQuery(
                new Term("field", Integer.toBinaryString(query)), minscore, prefix);
            q.setRewriteMethod(new MultiTermQuery.TopTermsBoostOnlyBooleanQueryRewrite(pqsize));
            System.out.println(query + "," + prefix + "," + pqsize + "," + minscore);
            TopDocs docs = searcher.search(q, terms);
            System.out.println(docs.totalHits);
            for (int i = 0; i < docs.totalHits; i++)
              System.out.println(docs.scoreDocs[i].doc + "," + docs.scoreDocs[i].score);
          }
  }
  */
}
