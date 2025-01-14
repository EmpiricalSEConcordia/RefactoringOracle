package org.apache.lucene.codecs.compressing;

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

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntField;
import org.apache.lucene.index.BaseStoredFieldsFormatTestCase;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MockDirectoryWrapper;
import org.junit.Test;
import com.carrotsearch.randomizedtesting.generators.RandomInts;

public class TestCompressingStoredFieldsFormat extends BaseStoredFieldsFormatTestCase {

  @Override
  protected Codec getCodec() {
    return CompressingCodec.randomInstance(random());
  }

  @Test(expected=IllegalArgumentException.class)
  public void testDeletePartiallyWrittenFilesIfAbort() throws IOException {
    Directory dir = newDirectory();
    // test explicitly needs files to always be actually deleted
    if (dir instanceof MockDirectoryWrapper) {
      ((MockDirectoryWrapper)dir).setEnableVirusScanner(false);
    }
    IndexWriterConfig iwConf = newIndexWriterConfig(new MockAnalyzer(random()));
    iwConf.setMaxBufferedDocs(RandomInts.randomIntBetween(random(), 2, 30));
    iwConf.setCodec(CompressingCodec.randomInstance(random()));
    // disable CFS because this test checks file names
    iwConf.setMergePolicy(newLogMergePolicy(false));
    iwConf.setUseCompoundFile(false);

    // Cannot use RIW because this test wants CFS to stay off:
    IndexWriter iw = new IndexWriter(dir, iwConf);

    final Document validDoc = new Document();
    validDoc.add(new IntField("id", 0, Store.YES));
    iw.addDocument(validDoc);
    iw.commit();
    
    // make sure that #writeField will fail to trigger an abort
    final Document invalidDoc = new Document();
    FieldType fieldType = new FieldType();
    fieldType.setStored(true);
    invalidDoc.add(new Field("invalid", fieldType) {
      
      @Override
      public String stringValue() {
        // TODO: really bad & scary that this causes IW to
        // abort the segment!!  We should fix this.
        return null;
      }
      
    });
    
    try {
      iw.addDocument(invalidDoc);
      iw.commit();
    }
    finally {
      // Abort should have closed the deleter:
      dir.close();
    }
  }
}
