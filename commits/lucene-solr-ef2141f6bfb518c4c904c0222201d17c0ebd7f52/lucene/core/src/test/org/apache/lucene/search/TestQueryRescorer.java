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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;

public class TestQueryRescorer extends LuceneTestCase {

  private IndexSearcher getSearcher(IndexReader r) {
    IndexSearcher searcher = newSearcher(r);

    // We rely on more tokens = lower score:
    searcher.setSimilarity(new DefaultSimilarity());

    return searcher;
  }

  public void testBasic() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    Document doc = new Document();
    doc.add(newStringField("id", "0", Field.Store.YES));
    doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO));
    w.addDocument(doc);
    doc = new Document();
    doc.add(newStringField("id", "1", Field.Store.YES));
    // 1 extra token, but wizard and oz are close;
    doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO));
    w.addDocument(doc);
    IndexReader r = w.getReader();
    w.close();

    // Do ordinary BooleanQuery:
    BooleanQuery bq = new BooleanQuery();
    bq.add(new TermQuery(new Term("field", "wizard")), Occur.SHOULD);
    bq.add(new TermQuery(new Term("field", "oz")), Occur.SHOULD);
    IndexSearcher searcher = getSearcher(r);
    searcher.setSimilarity(new DefaultSimilarity());

    TopDocs hits = searcher.search(bq, 10);
    assertEquals(2, hits.totalHits);
    assertEquals("0", searcher.doc(hits.scoreDocs[0].doc).get("id"));
    assertEquals("1", searcher.doc(hits.scoreDocs[1].doc).get("id"));

    // Now, resort using PhraseQuery:
    PhraseQuery pq = new PhraseQuery();
    pq.setSlop(5);
    pq.add(new Term("field", "wizard"));
    pq.add(new Term("field", "oz"));

    TopDocs hits2 = QueryRescorer.rescore(searcher, hits, pq, 2.0, 10);

    // Resorting changed the order:
    assertEquals(2, hits2.totalHits);
    assertEquals("1", searcher.doc(hits2.scoreDocs[0].doc).get("id"));
    assertEquals("0", searcher.doc(hits2.scoreDocs[1].doc).get("id"));

    // Resort using SpanNearQuery:
    SpanTermQuery t1 = new SpanTermQuery(new Term("field", "wizard"));
    SpanTermQuery t2 = new SpanTermQuery(new Term("field", "oz"));
    SpanNearQuery snq = new SpanNearQuery(new SpanQuery[] {t1, t2}, 0, true);

    TopDocs hits3 = QueryRescorer.rescore(searcher, hits, snq, 2.0, 10);

    // Resorting changed the order:
    assertEquals(2, hits3.totalHits);
    assertEquals("1", searcher.doc(hits3.scoreDocs[0].doc).get("id"));
    assertEquals("0", searcher.doc(hits3.scoreDocs[1].doc).get("id"));

    r.close();
    dir.close();
  }

  // Test LUCENE-5682
  public void testNullScorerTermQuery() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    Document doc = new Document();
    doc.add(newStringField("id", "0", Field.Store.YES));
    doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO));
    w.addDocument(doc);
    doc = new Document();
    doc.add(newStringField("id", "1", Field.Store.YES));
    // 1 extra token, but wizard and oz are close;
    doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO));
    w.addDocument(doc);
    IndexReader r = w.getReader();
    w.close();

    // Do ordinary BooleanQuery:
    BooleanQuery bq = new BooleanQuery();
    bq.add(new TermQuery(new Term("field", "wizard")), Occur.SHOULD);
    bq.add(new TermQuery(new Term("field", "oz")), Occur.SHOULD);
    IndexSearcher searcher = getSearcher(r);
    searcher.setSimilarity(new DefaultSimilarity());

    TopDocs hits = searcher.search(bq, 10);
    assertEquals(2, hits.totalHits);
    assertEquals("0", searcher.doc(hits.scoreDocs[0].doc).get("id"));
    assertEquals("1", searcher.doc(hits.scoreDocs[1].doc).get("id"));

    // Now, resort using TermQuery on term that does not exist.
    TermQuery tq = new TermQuery(new Term("field", "gold"));
    TopDocs hits2 = QueryRescorer.rescore(searcher, hits, tq, 2.0, 10);

    // Just testing that null scorer is handled.
    assertEquals(2, hits2.totalHits);

    r.close();
    dir.close();
  }

  public void testCustomCombine() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    Document doc = new Document();
    doc.add(newStringField("id", "0", Field.Store.YES));
    doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO));
    w.addDocument(doc);
    doc = new Document();
    doc.add(newStringField("id", "1", Field.Store.YES));
    // 1 extra token, but wizard and oz are close;
    doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO));
    w.addDocument(doc);
    IndexReader r = w.getReader();
    w.close();

    // Do ordinary BooleanQuery:
    BooleanQuery bq = new BooleanQuery();
    bq.add(new TermQuery(new Term("field", "wizard")), Occur.SHOULD);
    bq.add(new TermQuery(new Term("field", "oz")), Occur.SHOULD);
    IndexSearcher searcher = getSearcher(r);

    TopDocs hits = searcher.search(bq, 10);
    assertEquals(2, hits.totalHits);
    assertEquals("0", searcher.doc(hits.scoreDocs[0].doc).get("id"));
    assertEquals("1", searcher.doc(hits.scoreDocs[1].doc).get("id"));

    // Now, resort using PhraseQuery, but with an
    // opposite-world combine:
    PhraseQuery pq = new PhraseQuery();
    pq.setSlop(5);
    pq.add(new Term("field", "wizard"));
    pq.add(new Term("field", "oz"));
    
    TopDocs hits2 = new QueryRescorer(pq) {
        @Override
        protected float combine(float firstPassScore, boolean secondPassMatches, float secondPassScore) {
          float score = firstPassScore;
          if (secondPassMatches) {
            score -= 2.0 * secondPassScore;
          }
          return score;
        }
      }.rescore(searcher, hits, 10);

    // Resorting didn't change the order:
    assertEquals(2, hits2.totalHits);
    assertEquals("0", searcher.doc(hits2.scoreDocs[0].doc).get("id"));
    assertEquals("1", searcher.doc(hits2.scoreDocs[1].doc).get("id"));

    r.close();
    dir.close();
  }

  public void testExplain() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    Document doc = new Document();
    doc.add(newStringField("id", "0", Field.Store.YES));
    doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO));
    w.addDocument(doc);
    doc = new Document();
    doc.add(newStringField("id", "1", Field.Store.YES));
    // 1 extra token, but wizard and oz are close;
    doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO));
    w.addDocument(doc);
    IndexReader r = w.getReader();
    w.close();

    // Do ordinary BooleanQuery:
    BooleanQuery bq = new BooleanQuery();
    bq.add(new TermQuery(new Term("field", "wizard")), Occur.SHOULD);
    bq.add(new TermQuery(new Term("field", "oz")), Occur.SHOULD);
    IndexSearcher searcher = getSearcher(r);

    TopDocs hits = searcher.search(bq, 10);
    assertEquals(2, hits.totalHits);
    assertEquals("0", searcher.doc(hits.scoreDocs[0].doc).get("id"));
    assertEquals("1", searcher.doc(hits.scoreDocs[1].doc).get("id"));

    // Now, resort using PhraseQuery:
    PhraseQuery pq = new PhraseQuery();
    pq.add(new Term("field", "wizard"));
    pq.add(new Term("field", "oz"));

    Rescorer rescorer = new QueryRescorer(pq) {
        @Override
        protected float combine(float firstPassScore, boolean secondPassMatches, float secondPassScore) {
          float score = firstPassScore;
          if (secondPassMatches) {
            score += 2.0 * secondPassScore;
          }
          return score;
        }
      };

    TopDocs hits2 = rescorer.rescore(searcher, hits, 10);

    // Resorting changed the order:
    assertEquals(2, hits2.totalHits);
    assertEquals("1", searcher.doc(hits2.scoreDocs[0].doc).get("id"));
    assertEquals("0", searcher.doc(hits2.scoreDocs[1].doc).get("id"));

    int docID = hits2.scoreDocs[0].doc;
    Explanation explain = rescorer.explain(searcher,
                                           searcher.explain(bq, docID),
                                           docID);
    String s = explain.toString();
    assertTrue(s.contains("TestQueryRescorer$"));
    assertTrue(s.contains("combined first and second pass score"));
    assertTrue(s.contains("first pass score"));
    assertTrue(s.contains("= second pass score"));
    assertEquals(hits2.scoreDocs[0].score, explain.getValue(), 0.0f);

    docID = hits2.scoreDocs[1].doc;
    explain = rescorer.explain(searcher,
                               searcher.explain(bq, docID),
                               docID);
    s = explain.toString();
    assertTrue(s.contains("TestQueryRescorer$"));
    assertTrue(s.contains("combined first and second pass score"));
    assertTrue(s.contains("first pass score"));
    assertTrue(s.contains("no second pass score"));
    assertFalse(s.contains("= second pass score"));
    assertTrue(s.contains("NON-MATCH"));
    assertEquals(hits2.scoreDocs[1].score, explain.getValue(), 0.0f);

    r.close();
    dir.close();
  }

  public void testMissingSecondPassScore() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    Document doc = new Document();
    doc.add(newStringField("id", "0", Field.Store.YES));
    doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO));
    w.addDocument(doc);
    doc = new Document();
    doc.add(newStringField("id", "1", Field.Store.YES));
    // 1 extra token, but wizard and oz are close;
    doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO));
    w.addDocument(doc);
    IndexReader r = w.getReader();
    w.close();

    // Do ordinary BooleanQuery:
    BooleanQuery bq = new BooleanQuery();
    bq.add(new TermQuery(new Term("field", "wizard")), Occur.SHOULD);
    bq.add(new TermQuery(new Term("field", "oz")), Occur.SHOULD);
    IndexSearcher searcher = getSearcher(r);

    TopDocs hits = searcher.search(bq, 10);
    assertEquals(2, hits.totalHits);
    assertEquals("0", searcher.doc(hits.scoreDocs[0].doc).get("id"));
    assertEquals("1", searcher.doc(hits.scoreDocs[1].doc).get("id"));

    // Now, resort using PhraseQuery, no slop:
    PhraseQuery pq = new PhraseQuery();
    pq.add(new Term("field", "wizard"));
    pq.add(new Term("field", "oz"));

    TopDocs hits2 = QueryRescorer.rescore(searcher, hits, pq, 2.0, 10);

    // Resorting changed the order:
    assertEquals(2, hits2.totalHits);
    assertEquals("1", searcher.doc(hits2.scoreDocs[0].doc).get("id"));
    assertEquals("0", searcher.doc(hits2.scoreDocs[1].doc).get("id"));

    // Resort using SpanNearQuery:
    SpanTermQuery t1 = new SpanTermQuery(new Term("field", "wizard"));
    SpanTermQuery t2 = new SpanTermQuery(new Term("field", "oz"));
    SpanNearQuery snq = new SpanNearQuery(new SpanQuery[] {t1, t2}, 0, true);

    TopDocs hits3 = QueryRescorer.rescore(searcher, hits, snq, 2.0, 10);

    // Resorting changed the order:
    assertEquals(2, hits3.totalHits);
    assertEquals("1", searcher.doc(hits3.scoreDocs[0].doc).get("id"));
    assertEquals("0", searcher.doc(hits3.scoreDocs[1].doc).get("id"));

    r.close();
    dir.close();
  }

  public void testRandom() throws Exception {
    Directory dir = newDirectory();
    int numDocs = atLeast(1000);
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    final int[] idToNum = new int[numDocs];
    int maxValue = TestUtil.nextInt(random(), 10, 1000000);
    for(int i=0;i<numDocs;i++) {
      Document doc = new Document();
      doc.add(newStringField("id", ""+i, Field.Store.YES));
      int numTokens = TestUtil.nextInt(random(), 1, 10);
      StringBuilder b = new StringBuilder();
      for(int j=0;j<numTokens;j++) {
        b.append("a ");
      }
      doc.add(newTextField("field", b.toString(), Field.Store.NO));
      idToNum[i] = random().nextInt(maxValue);
      doc.add(new NumericDocValuesField("num", idToNum[i]));
      w.addDocument(doc);
    }
    final IndexReader r = w.getReader();
    w.close();

    IndexSearcher s = newSearcher(r);
    int numHits = TestUtil.nextInt(random(), 1, numDocs);
    boolean reverse = random().nextBoolean();

    //System.out.println("numHits=" + numHits + " reverse=" + reverse);
    TopDocs hits = s.search(new TermQuery(new Term("field", "a")), numHits);

    TopDocs hits2 = new QueryRescorer(new FixedScoreQuery(idToNum, reverse)) {
        @Override
        protected float combine(float firstPassScore, boolean secondPassMatches, float secondPassScore) {
          return secondPassScore;
        }
      }.rescore(s, hits, numHits);

    Integer[] expected = new Integer[numHits];
    for(int i=0;i<numHits;i++) {
      expected[i] = hits.scoreDocs[i].doc;
    }

    final int reverseInt = reverse ? -1 : 1;

    Arrays.sort(expected,
                new Comparator<Integer>() {
                  @Override
                  public int compare(Integer a, Integer b) {
                    try {
                      int av = idToNum[Integer.parseInt(r.document(a).get("id"))];
                      int bv = idToNum[Integer.parseInt(r.document(b).get("id"))];
                      if (av < bv) {
                        return -reverseInt;
                      } else if (bv < av) {
                        return reverseInt;
                      } else {
                        // Tie break by docID, ascending
                        return a - b;
                      }
                    } catch (IOException ioe) {
                      throw new RuntimeException(ioe);
                    }
                  }
                });

    boolean fail = false;
    for(int i=0;i<numHits;i++) {
      //System.out.println("expected=" + expected[i] + " vs " + hits2.scoreDocs[i].doc + " v=" + idToNum[Integer.parseInt(r.document(expected[i]).get("id"))]);
      if (expected[i].intValue() != hits2.scoreDocs[i].doc) {
        //System.out.println("  diff!");
        fail = true;
      }
    }
    assertFalse(fail);

    r.close();
    dir.close();
  }

  /** Just assigns score == idToNum[doc("id")] for each doc. */
  private static class FixedScoreQuery extends Query {
    private final int[] idToNum;
    private final boolean reverse;

    public FixedScoreQuery(int[] idToNum, boolean reverse) {
      this.idToNum = idToNum;
      this.reverse = reverse;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, int postingsFlags) throws IOException {

      return new Weight(FixedScoreQuery.this) {

        @Override
        public float getValueForNormalization() {
          return 1.0f;
        }

        @Override
        public void normalize(float queryNorm, float topLevelBoost) {
        }

        @Override
        public Scorer scorer(final LeafReaderContext context, Bits acceptDocs) throws IOException {

          return new Scorer(null) {
            int docID = -1;

            @Override
            public int docID() {
              return docID;
            }

            @Override
            public int freq() {
              return 1;
            }

            @Override
            public int nextPosition() throws IOException {
              return -1;
            }

            @Override
            public int startOffset() throws IOException {
              return -1;
            }

            @Override
            public int endOffset() throws IOException {
              return -1;
            }

            @Override
            public BytesRef getPayload() throws IOException {
              return null;
            }

            @Override
            public long cost() {
              return 1;
            }

            @Override
            public int nextDoc() {
              docID++;
              if (docID >= context.reader().maxDoc()) {
                return NO_MORE_DOCS;
              }
              return docID;
            }

            @Override
            public int advance(int target) {
              docID = target;
              return docID;
            }

            @Override
            public float score() throws IOException {
              int num = idToNum[Integer.parseInt(context.reader().document(docID).get("id"))];
              if (reverse) {
                //System.out.println("score doc=" + docID + " num=" + num);
                return num;
              } else {
                //System.out.println("score doc=" + docID + " num=" + -num);
                return -num;
              }
            }
          };
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
          return null;
        }
      };
    }

    @Override
    public void extractTerms(Set<Term> terms) {
    }

    @Override
    public String toString(String field) {
      return "FixedScoreQuery " + idToNum.length + " ids; reverse=" + reverse;
    }

    @Override
    public boolean equals(Object o) {
      if ((o instanceof FixedScoreQuery) == false) {
        return false;
      }
      FixedScoreQuery other = (FixedScoreQuery) o;
      return Float.floatToIntBits(getBoost()) == Float.floatToIntBits(other.getBoost()) &&
        reverse == other.reverse &&
        Arrays.equals(idToNum, other.idToNum);
    }

    @Override
    public Query clone() {
      return new FixedScoreQuery(idToNum, reverse);
    }

    @Override
    public int hashCode() {
      int PRIME = 31;
      int hash = super.hashCode();
      if (reverse) {
        hash = PRIME * hash + 3623;
      }
      hash = PRIME * hash + Arrays.hashCode(idToNum);
      return hash;
    }
  }
}
