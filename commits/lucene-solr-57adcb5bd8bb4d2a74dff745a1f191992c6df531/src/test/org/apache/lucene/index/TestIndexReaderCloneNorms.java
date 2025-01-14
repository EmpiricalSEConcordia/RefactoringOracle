package org.apache.lucene.index;

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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.SegmentReader.Norm;
import org.apache.lucene.search.DefaultSimilarity;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MockRAMDirectory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * Tests cloning IndexReader norms
 */
public class TestIndexReaderCloneNorms extends LuceneTestCase {

  private class SimilarityOne extends DefaultSimilarity {
    public float lengthNorm(String fieldName, int numTerms) {
      return 1;
    }
  }

  private static final int NUM_FIELDS = 10;

  private Similarity similarityOne;

  private Analyzer anlzr;

  private int numDocNorms;

  private ArrayList norms;

  private ArrayList modifiedNorms;

  private float lastNorm = 0;

  private float normDelta = (float) 0.001;

  public TestIndexReaderCloneNorms(String s) {
    super(s);
  }

  protected void setUp() throws Exception {
    super.setUp();
    similarityOne = new SimilarityOne();
    anlzr = new StandardAnalyzer();
  }
  
  /**
   * Test that norms values are preserved as the index is maintained. Including
   * separate norms. Including merging indexes with seprate norms. Including
   * optimize.
   */
  public void testNorms() throws IOException {
    // tmp dir
    String tempDir = System.getProperty("java.io.tmpdir");
    if (tempDir == null) {
      throw new IOException("java.io.tmpdir undefined, cannot run test");
    }

    // test with a single index: index1
    File indexDir1 = new File(tempDir, "lucenetestindex1");
    Directory dir1 = FSDirectory.open(indexDir1);
    IndexWriter.unlock(dir1);

    norms = new ArrayList();
    modifiedNorms = new ArrayList();

    createIndex(dir1);
    doTestNorms(dir1);

    // test with a single index: index2
    ArrayList norms1 = norms;
    ArrayList modifiedNorms1 = modifiedNorms;
    int numDocNorms1 = numDocNorms;

    norms = new ArrayList();
    modifiedNorms = new ArrayList();
    numDocNorms = 0;

    File indexDir2 = new File(tempDir, "lucenetestindex2");
    Directory dir2 = FSDirectory.open(indexDir2);

    createIndex(dir2);
    doTestNorms(dir2);

    // add index1 and index2 to a third index: index3
    File indexDir3 = new File(tempDir, "lucenetestindex3");
    Directory dir3 = FSDirectory.open(indexDir3);

    createIndex(dir3);
    IndexWriter iw = new IndexWriter(dir3, anlzr, false,
        IndexWriter.MaxFieldLength.LIMITED);
    iw.setMaxBufferedDocs(5);
    iw.setMergeFactor(3);
    iw.addIndexes(new Directory[] { dir1, dir2 });
    iw.close();

    norms1.addAll(norms);
    norms = norms1;
    modifiedNorms1.addAll(modifiedNorms);
    modifiedNorms = modifiedNorms1;
    numDocNorms += numDocNorms1;

    // test with index3
    verifyIndex(dir3);
    doTestNorms(dir3);

    // now with optimize
    iw = new IndexWriter(dir3, anlzr, false, IndexWriter.MaxFieldLength.LIMITED);
    iw.setMaxBufferedDocs(5);
    iw.setMergeFactor(3);
    iw.optimize();
    iw.close();
    verifyIndex(dir3);

    dir1.close();
    dir2.close();
    dir3.close();
  }

  // try cloning and reopening the norms
  private void doTestNorms(Directory dir) throws IOException {
    addDocs(dir, 12, true);
    IndexReader ir = IndexReader.open(dir);
    verifyIndex(ir);
    modifyNormsForF1(ir);
    IndexReader irc = (IndexReader) ir.clone();// IndexReader.open(dir);//ir.clone();
    verifyIndex(irc);

    modifyNormsForF1(irc);

    IndexReader irc3 = (IndexReader) irc.clone();
    verifyIndex(irc3);
    modifyNormsForF1(irc3);
    verifyIndex(irc3);
    irc3.flush();
    irc3.close();
  }
  
  public void testNormsClose() throws IOException { 
    Directory dir1 = new MockRAMDirectory(); 
    TestIndexReaderReopen.createIndex(dir1, false);
    SegmentReader reader1 = (SegmentReader) IndexReader.open(dir1);
    reader1.norms("field1");
    Norm r1norm = (Norm)reader1.norms.get("field1");
    SegmentReader.Ref r1BytesRef = r1norm.bytesRef();
    SegmentReader reader2 = (SegmentReader)reader1.clone();
    assertEquals(2, r1norm.bytesRef().refCount());
    reader1.close();
    assertEquals(1, r1BytesRef.refCount());
    reader2.norms("field1");
    reader2.close();
    dir1.close();
  }
  
  public void testNormsRefCounting() throws IOException { 
    Directory dir1 = new MockRAMDirectory(); 
    TestIndexReaderReopen.createIndex(dir1, false);
    SegmentReader reader1 = (SegmentReader) IndexReader.open(dir1);
        
    SegmentReader reader2C = (SegmentReader)reader1.clone();
    reader2C.norms("field1"); // load the norms for the field
    Norm reader2CNorm = (Norm)reader2C.norms.get("field1");
    assertTrue("reader2CNorm.bytesRef()=" + reader2CNorm.bytesRef(), reader2CNorm.bytesRef().refCount() == 2);
    
    
    
    SegmentReader reader3C = (SegmentReader)reader2C.clone();
    Norm reader3CCNorm = (Norm)reader3C.norms.get("field1");
    assertEquals(3, reader3CCNorm.bytesRef().refCount());
    
    // edit a norm and the refcount should be 1
    SegmentReader reader4C = (SegmentReader)reader3C.clone();
    assertEquals(4, reader3CCNorm.bytesRef().refCount());
    reader4C.setNorm(5, "field1", 0.33f);
    
    // generate a cannot update exception in reader1
    try {
      reader3C.setNorm(1, "field1", 0.99f);
      fail("did not hit expected exception");
    } catch (Exception ex) {
      // expected
    }
    
    // norm values should be different 
    assertTrue(Similarity.decodeNorm(reader3C.norms("field1")[5]) != Similarity.decodeNorm(reader4C.norms("field1")[5]));
    Norm reader4CCNorm = (Norm)reader4C.norms.get("field1");
    assertEquals(3, reader3CCNorm.bytesRef().refCount());
    assertEquals(1, reader4CCNorm.bytesRef().refCount());
        
    SegmentReader reader5C = (SegmentReader)reader4C.clone();
    Norm reader5CCNorm = (Norm)reader5C.norms.get("field1");
    reader5C.setNorm(5, "field1", 0.7f);
    assertEquals(1, reader5CCNorm.bytesRef().refCount());    

    reader5C.close();
    reader4C.close();
    reader3C.close();
    reader2C.close();
    reader1.close();
    dir1.close();
  }
  
  private void createIndex(Directory dir) throws IOException {
    IndexWriter iw = new IndexWriter(dir, anlzr, true,
        IndexWriter.MaxFieldLength.LIMITED);
    iw.setMaxBufferedDocs(5);
    iw.setMergeFactor(3);
    iw.setSimilarity(similarityOne);
    iw.setUseCompoundFile(true);
    iw.close();
  }

  private void modifyNormsForF1(Directory dir) throws IOException {
    IndexReader ir = IndexReader.open(dir);
    modifyNormsForF1(ir);
  }

  private void modifyNormsForF1(IndexReader ir) throws IOException {
    int n = ir.maxDoc();
    // System.out.println("modifyNormsForF1 maxDoc: "+n);
    for (int i = 0; i < n; i += 3) { // modify for every third doc
      int k = (i * 3) % modifiedNorms.size();
      float origNorm = ((Float) modifiedNorms.get(i)).floatValue();
      float newNorm = ((Float) modifiedNorms.get(k)).floatValue();
      // System.out.println("Modifying: for "+i+" from "+origNorm+" to
      // "+newNorm);
      // System.out.println(" and: for "+k+" from "+newNorm+" to "+origNorm);
      modifiedNorms.set(i, new Float(newNorm));
      modifiedNorms.set(k, new Float(origNorm));
      ir.setNorm(i, "f" + 1, newNorm);
      ir.setNorm(k, "f" + 1, origNorm);
      // System.out.println("setNorm i: "+i);
      // break;
    }
    // ir.close();
  }

  private void verifyIndex(Directory dir) throws IOException {
    IndexReader ir = IndexReader.open(dir);
    verifyIndex(ir);
    ir.close();
  }

  private void verifyIndex(IndexReader ir) throws IOException {
    for (int i = 0; i < NUM_FIELDS; i++) {
      String field = "f" + i;
      byte b[] = ir.norms(field);
      assertEquals("number of norms mismatches", numDocNorms, b.length);
      ArrayList storedNorms = (i == 1 ? modifiedNorms : norms);
      for (int j = 0; j < b.length; j++) {
        float norm = Similarity.decodeNorm(b[j]);
        float norm1 = ((Float) storedNorms.get(j)).floatValue();
        assertEquals("stored norm value of " + field + " for doc " + j + " is "
            + norm + " - a mismatch!", norm, norm1, 0.000001);
      }
    }
  }

  private void addDocs(Directory dir, int ndocs, boolean compound)
      throws IOException {
    IndexWriter iw = new IndexWriter(dir, anlzr, false,
        IndexWriter.MaxFieldLength.LIMITED);
    iw.setMaxBufferedDocs(5);
    iw.setMergeFactor(3);
    iw.setSimilarity(similarityOne);
    iw.setUseCompoundFile(compound);
    for (int i = 0; i < ndocs; i++) {
      iw.addDocument(newDoc());
    }
    iw.close();
  }

  // create the next document
  private Document newDoc() {
    Document d = new Document();
    float boost = nextNorm();
    for (int i = 0; i < 10; i++) {
      Field f = new Field("f" + i, "v" + i, Store.NO, Index.NOT_ANALYZED);
      f.setBoost(boost);
      d.add(f);
    }
    return d;
  }

  // return unique norm values that are unchanged by encoding/decoding
  private float nextNorm() {
    float norm = lastNorm + normDelta;
    do {
      float norm1 = Similarity.decodeNorm(Similarity.encodeNorm(norm));
      if (norm1 > lastNorm) {
        // System.out.println(norm1+" > "+lastNorm);
        norm = norm1;
        break;
      }
      norm += normDelta;
    } while (true);
    norms.add(numDocNorms, new Float(norm));
    modifiedNorms.add(numDocNorms, new Float(norm));
    // System.out.println("creating norm("+numDocNorms+"): "+norm);
    numDocNorms++;
    lastNorm = (norm > 10 ? 0 : norm); // there's a limit to how many distinct
                                        // values can be stored in a ingle byte
    return norm;
  }
}
