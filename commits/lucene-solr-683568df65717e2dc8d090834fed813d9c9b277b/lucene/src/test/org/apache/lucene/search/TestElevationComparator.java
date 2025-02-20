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

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.*;
import org.apache.lucene.search.FieldValueHitQueue.Entry;
import org.apache.lucene.store.*;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TestElevationComparator extends LuceneTestCase {

  private final Map<BytesRef,Integer> priority = new HashMap<BytesRef,Integer>();

  //@Test
  public void testSorting() throws Throwable {
    Directory directory = new MockRAMDirectory();
    IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer()).setMaxBufferedDocs(2));
    ((LogMergePolicy) writer.getConfig().getMergePolicy()).setMergeFactor(1000);
    writer.addDocument(adoc(new String[] {"id", "a", "title", "ipod", "str_s", "a"}));
    writer.addDocument(adoc(new String[] {"id", "b", "title", "ipod ipod", "str_s", "b"}));
    writer.addDocument(adoc(new String[] {"id", "c", "title", "ipod ipod ipod", "str_s","c"}));
    writer.addDocument(adoc(new String[] {"id", "x", "title", "boosted", "str_s", "x"}));
    writer.addDocument(adoc(new String[] {"id", "y", "title", "boosted boosted", "str_s","y"}));
    writer.addDocument(adoc(new String[] {"id", "z", "title", "boosted boosted boosted","str_s", "z"}));

    IndexReader r = writer.getReader();
    writer.close();

    IndexSearcher searcher = new IndexSearcher(r);

    runTest(searcher, true);
    runTest(searcher, false);

    searcher.close();
    r.close();
    directory.close();
  }

  private void runTest(IndexSearcher searcher, boolean reversed) throws Throwable {

    BooleanQuery newq = new BooleanQuery(false);
    TermQuery query = new TermQuery(new Term("title", "ipod"));

    newq.add(query, BooleanClause.Occur.SHOULD);
    newq.add(getElevatedQuery(new String[] {"id", "a", "id", "x"}), BooleanClause.Occur.SHOULD);

    Sort sort = new Sort(
        new SortField("id", new ElevationComparatorSource(priority), false),
        new SortField(null, SortField.SCORE, reversed)
      );

    TopDocsCollector<Entry> topCollector = TopFieldCollector.create(sort, 50, false, true, true, true);
    searcher.search(newq, null, topCollector);

    TopDocs topDocs = topCollector.topDocs(0, 10);
    int nDocsReturned = topDocs.scoreDocs.length;

    assertEquals(4, nDocsReturned);

    // 0 & 3 were elevated
    assertEquals(0, topDocs.scoreDocs[0].doc);
    assertEquals(3, topDocs.scoreDocs[1].doc);

    if (reversed) {
      assertEquals(2, topDocs.scoreDocs[2].doc);
      assertEquals(1, topDocs.scoreDocs[3].doc);
    } else {
      assertEquals(1, topDocs.scoreDocs[2].doc);
      assertEquals(2, topDocs.scoreDocs[3].doc);
    }

    /*
    for (int i = 0; i < nDocsReturned; i++) {
     ScoreDoc scoreDoc = topDocs.scoreDocs[i];
     ids[i] = scoreDoc.doc;
     scores[i] = scoreDoc.score;
     documents[i] = searcher.doc(ids[i]);
     System.out.println("ids[i] = " + ids[i]);
     System.out.println("documents[i] = " + documents[i]);
     System.out.println("scores[i] = " + scores[i]);
   }
    */
 }

 private Query getElevatedQuery(String[] vals) {
   BooleanQuery q = new BooleanQuery(false);
   q.setBoost(0);
   int max = (vals.length / 2) + 5;
   for (int i = 0; i < vals.length - 1; i += 2) {
     q.add(new TermQuery(new Term(vals[i], vals[i + 1])), BooleanClause.Occur.SHOULD);
     priority.put(new BytesRef(vals[i + 1]), Integer.valueOf(max--));
     // System.out.println(" pri doc=" + vals[i+1] + " pri=" + (1+max));
   }
   return q;
 }

 private Document adoc(String[] vals) {
   Document doc = new Document();
   for (int i = 0; i < vals.length - 2; i += 2) {
     doc.add(new Field(vals[i], vals[i + 1], Field.Store.YES, Field.Index.ANALYZED));
   }
   return doc;
 }
}

class ElevationComparatorSource extends FieldComparatorSource {
  private final Map<BytesRef,Integer> priority;

  public ElevationComparatorSource(final Map<BytesRef,Integer> boosts) {
   this.priority = boosts;
  }

  @Override
  public FieldComparator newComparator(final String fieldname, final int numHits, int sortPos, boolean reversed) throws IOException {
   return new FieldComparator() {

     FieldCache.DocTermsIndex idIndex;
     private final int[] values = new int[numHits];
     private final BytesRef tempBR = new BytesRef();
     int bottomVal;

     @Override
     public int compare(int slot1, int slot2) {
       return values[slot2] - values[slot1];  // values will be small enough that there is no overflow concern
     }

     @Override
     public void setBottom(int slot) {
       bottomVal = values[slot];
     }

     private int docVal(int doc) throws IOException {
       int ord = idIndex.getOrd(doc);
       if (ord == 0) {
         return 0;
       } else {
         BytesRef id = idIndex.lookup(ord, tempBR);
         Integer prio = priority.get(id);
         return prio == null ? 0 : prio.intValue();
       }
     }

     @Override
     public int compareBottom(int doc) throws IOException {
       return docVal(doc) - bottomVal;
     }

     @Override
     public void copy(int slot, int doc) throws IOException {
       values[slot] = docVal(doc);
     }

     @Override
     public void setNextReader(IndexReader reader, int docBase) throws IOException {
       idIndex = FieldCache.DEFAULT.getTermsIndex(reader, fieldname);
     }

     @Override
     public Comparable<?> value(int slot) {
       return Integer.valueOf(values[slot]);
     }
   };
 }
}
