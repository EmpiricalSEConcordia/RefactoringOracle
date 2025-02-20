package org.apache.lucene.codecs.lucene41;

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

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;

/** 
 * Tests special cases of BlockPostingsFormat 
 */
public class TestBlockPostingsFormat2 extends LuceneTestCase {
  Directory dir;
  RandomIndexWriter iw;
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newFSDirectory(createTempDir("testDFBlockSize"));
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.alwaysPostingsFormat(new Lucene41PostingsFormat()));
    iw = new RandomIndexWriter(random(), dir, iwc);
    iw.setDoRandomForceMerge(false); // we will ourselves
  }
  
  @Override
  public void tearDown() throws Exception {
    iw.shutdown();
    TestUtil.checkIndex(dir); // for some extra coverage, checkIndex before we forceMerge
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    iwc.setOpenMode(OpenMode.APPEND);
    IndexWriter iw = new IndexWriter(dir, iwc);
    iw.forceMerge(1);
    iw.shutdown();
    dir.close(); // just force a checkindex for now
    super.tearDown();
  }
  
  private Document newDocument() {
    Document doc = new Document();
    for (IndexOptions option : FieldInfo.IndexOptions.values()) {
      FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
      // turn on tvs for a cross-check, since we rely upon checkindex in this test (for now)
      ft.setStoreTermVectors(true);
      ft.setStoreTermVectorOffsets(true);
      ft.setStoreTermVectorPositions(true);
      ft.setStoreTermVectorPayloads(true);
      ft.setIndexOptions(option);
      doc.add(new Field(option.toString(), "", ft));
    }
    return doc;
  }

  /** tests terms with df = blocksize */
  public void testDFBlockSize() throws Exception {
    Document doc = newDocument();
    for (int i = 0; i < Lucene41PostingsFormat.BLOCK_SIZE; i++) {
      for (Field f : doc.getFields()) {
        f.setStringValue(f.name() + " " + f.name() + "_2");
      }
      iw.addDocument(doc);
    }
  }

  /** tests terms with df % blocksize = 0 */
  public void testDFBlockSizeMultiple() throws Exception {
    Document doc = newDocument();
    for (int i = 0; i < Lucene41PostingsFormat.BLOCK_SIZE * 16; i++) {
      for (Field f : doc.getFields()) {
        f.setStringValue(f.name() + " " + f.name() + "_2");
      }
      iw.addDocument(doc);
    }
  }
  
  /** tests terms with ttf = blocksize */
  public void testTTFBlockSize() throws Exception {
    Document doc = newDocument();
    for (int i = 0; i < Lucene41PostingsFormat.BLOCK_SIZE/2; i++) {
      for (Field f : doc.getFields()) {
        f.setStringValue(f.name() + " " + f.name() + " " + f.name() + "_2 " + f.name() + "_2");
      }
      iw.addDocument(doc);
    }
  }
  
  /** tests terms with ttf % blocksize = 0 */
  public void testTTFBlockSizeMultiple() throws Exception {
    Document doc = newDocument();
    for (int i = 0; i < Lucene41PostingsFormat.BLOCK_SIZE/2; i++) {
      for (Field f : doc.getFields()) {
        String proto = (f.name() + " " + f.name() + " " + f.name() + " " + f.name() + " " 
                       + f.name() + "_2 " + f.name() + "_2 " + f.name() + "_2 " + f.name() + "_2");
        StringBuilder val = new StringBuilder();
        for (int j = 0; j < 16; j++) {
          val.append(proto);
          val.append(" ");
        }
        f.setStringValue(val.toString());
      }
      iw.addDocument(doc);
    }
  }
}
