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

import java.io.IOException;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Constants;
import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util._TestUtil;


public class TestSegmentMerger extends LuceneTestCase {
  //The variables for the new merged segment
  private Directory mergedDir;
  private String mergedSegment = "test";
  //First segment to be merged
  private Directory merge1Dir;
  private Document doc1 = new Document();
  private SegmentReader reader1 = null;
  //Second Segment to be merged
  private Directory merge2Dir;
  private Document doc2 = new Document();
  private SegmentReader reader2 = null;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mergedDir = newDirectory();
    merge1Dir = newDirectory();
    merge2Dir = newDirectory();
    DocHelper.setupDoc(doc1);
    SegmentInfo info1 = DocHelper.writeDoc(random(), merge1Dir, doc1);
    DocHelper.setupDoc(doc2);
    SegmentInfo info2 = DocHelper.writeDoc(random(), merge2Dir, doc2);
    reader1 = new SegmentReader(info1, DirectoryReader.DEFAULT_TERMS_INDEX_DIVISOR, newIOContext(random()));
    reader2 = new SegmentReader(info2, DirectoryReader.DEFAULT_TERMS_INDEX_DIVISOR, newIOContext(random()));
  }

  @Override
  public void tearDown() throws Exception {
    reader1.close();
    reader2.close();
    mergedDir.close();
    merge1Dir.close();
    merge2Dir.close();
    super.tearDown();
  }

  public void test() {
    assertTrue(mergedDir != null);
    assertTrue(merge1Dir != null);
    assertTrue(merge2Dir != null);
    assertTrue(reader1 != null);
    assertTrue(reader2 != null);
  }

  public void testMerge() throws IOException {
    final Codec codec = Codec.getDefault();
    SegmentMerger merger = new SegmentMerger(InfoStream.getDefault(), mergedDir, IndexWriterConfig.DEFAULT_TERM_INDEX_INTERVAL, mergedSegment, MergeState.CheckAbort.NONE, null, new MutableFieldInfos(new MutableFieldInfos.FieldNumberBiMap()), codec, newIOContext(random()));
    merger.add(reader1);
    merger.add(reader2);
    MergeState mergeState = merger.merge();
    int docsMerged = mergeState.mergedDocCount;
    assertTrue(docsMerged == 2);
    //Should be able to open a new SegmentReader against the new directory
    SegmentReader mergedReader = new SegmentReader(new SegmentInfo(mergedDir, Constants.LUCENE_MAIN_VERSION, mergedSegment, docsMerged, -1, -1, mergedSegment,
                                                                   false, null, false, 0, mergeState.fieldInfos.hasProx(), codec, null),
                                                   DirectoryReader.DEFAULT_TERMS_INDEX_DIVISOR, newIOContext(random()));
    assertTrue(mergedReader != null);
    assertTrue(mergedReader.numDocs() == 2);
    Document newDoc1 = mergedReader.document(0);
    assertTrue(newDoc1 != null);
    //There are 2 unstored fields on the document
    assertTrue(DocHelper.numFields(newDoc1) == DocHelper.numFields(doc1) - DocHelper.unstored.size());
    Document newDoc2 = mergedReader.document(1);
    assertTrue(newDoc2 != null);
    assertTrue(DocHelper.numFields(newDoc2) == DocHelper.numFields(doc2) - DocHelper.unstored.size());

    DocsEnum termDocs = _TestUtil.docs(random(), mergedReader,
                                       DocHelper.TEXT_FIELD_2_KEY,
                                       new BytesRef("field"),
                                       MultiFields.getLiveDocs(mergedReader),
                                       null,
                                       false);
    assertTrue(termDocs != null);
    assertTrue(termDocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);

    int tvCount = 0;
    for(FieldInfo fieldInfo : mergedReader.getFieldInfos()) {
      if (fieldInfo.hasVectors()) {
        tvCount++;
      }
    }
    
    //System.out.println("stored size: " + stored.size());
    assertEquals("We do not have 3 fields that were indexed with term vector", 3, tvCount);

    Terms vector = mergedReader.getTermVectors(0).terms(DocHelper.TEXT_FIELD_2_KEY);
    assertNotNull(vector);
    assertEquals(3, vector.size());
    TermsEnum termsEnum = vector.iterator(null);

    int i = 0;
    while (termsEnum.next() != null) {
      String term = termsEnum.term().utf8ToString();
      int freq = (int) termsEnum.totalTermFreq();
      //System.out.println("Term: " + term + " Freq: " + freq);
      assertTrue(DocHelper.FIELD_2_TEXT.indexOf(term) != -1);
      assertTrue(DocHelper.FIELD_2_FREQS[i] == freq);
      i++;
    }

    TestSegmentReader.checkNorms(mergedReader);
    mergedReader.close();
  }
}
