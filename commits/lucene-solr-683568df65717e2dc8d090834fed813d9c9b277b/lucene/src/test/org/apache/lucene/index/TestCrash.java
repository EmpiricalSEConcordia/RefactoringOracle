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

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.store.MockRAMDirectory;
import org.apache.lucene.store.NoLockFactory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

public class TestCrash extends LuceneTestCase {

  private IndexWriter initIndex(boolean initialCommit) throws IOException {
    return initIndex(new MockRAMDirectory(), initialCommit);
  }

  private IndexWriter initIndex(MockRAMDirectory dir, boolean initialCommit) throws IOException {
    dir.setLockFactory(NoLockFactory.getNoLockFactory());

    IndexWriter writer  = new IndexWriter(dir, new IndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer()).setMaxBufferedDocs(10));
    ((ConcurrentMergeScheduler) writer.getConfig().getMergeScheduler()).setSuppressExceptions();
    if (initialCommit) {
      writer.commit();
    }
    
    Document doc = new Document();
    doc.add(new Field("content", "aaa", Field.Store.YES, Field.Index.ANALYZED));
    doc.add(new Field("id", "0", Field.Store.YES, Field.Index.ANALYZED));
    for(int i=0;i<157;i++)
      writer.addDocument(doc);

    return writer;
  }

  private void crash(final IndexWriter writer) throws IOException {
    final MockRAMDirectory dir = (MockRAMDirectory) writer.getDirectory();
    ConcurrentMergeScheduler cms = (ConcurrentMergeScheduler) writer.getConfig().getMergeScheduler();
    dir.crash();
    cms.sync();
    dir.clearCrash();
  }

  public void testCrashWhileIndexing() throws IOException {
    // This test relies on being able to open a reader before any commit
    // happened, so we must create an initial commit just to allow that, but
    // before any documents were added.
    IndexWriter writer = initIndex(true);
    MockRAMDirectory dir = (MockRAMDirectory) writer.getDirectory();
    crash(writer);
    IndexReader reader = IndexReader.open(dir, false);
    assertTrue(reader.numDocs() < 157);
  }

  public void testWriterAfterCrash() throws IOException {
    // This test relies on being able to open a reader before any commit
    // happened, so we must create an initial commit just to allow that, but
    // before any documents were added.
    IndexWriter writer = initIndex(true);
    MockRAMDirectory dir = (MockRAMDirectory) writer.getDirectory();
    dir.setPreventDoubleWrite(false);
    crash(writer);
    writer = initIndex(dir, false);
    writer.close();

    IndexReader reader = IndexReader.open(dir, false);
    assertTrue(reader.numDocs() < 314);
  }

  public void testCrashAfterReopen() throws IOException {
    IndexWriter writer = initIndex(false);
    MockRAMDirectory dir = (MockRAMDirectory) writer.getDirectory();
    writer.close();
    writer = initIndex(dir, false);
    assertEquals(314, writer.maxDoc());
    crash(writer);

    /*
    System.out.println("\n\nTEST: open reader");
    String[] l = dir.list();
    Arrays.sort(l);
    for(int i=0;i<l.length;i++)
      System.out.println("file " + i + " = " + l[i] + " " +
    dir.fileLength(l[i]) + " bytes");
    */

    IndexReader reader = IndexReader.open(dir, false);
    assertTrue(reader.numDocs() >= 157);
  }

  public void testCrashAfterClose() throws IOException {
    
    IndexWriter writer = initIndex(false);
    MockRAMDirectory dir = (MockRAMDirectory) writer.getDirectory();

    writer.close();
    dir.crash();

    /*
    String[] l = dir.list();
    Arrays.sort(l);
    for(int i=0;i<l.length;i++)
      System.out.println("file " + i + " = " + l[i] + " " + dir.fileLength(l[i]) + " bytes");
    */

    IndexReader reader = IndexReader.open(dir, false);
    assertEquals(157, reader.numDocs());
  }

  public void testCrashAfterCloseNoWait() throws IOException {
    
    IndexWriter writer = initIndex(false);
    MockRAMDirectory dir = (MockRAMDirectory) writer.getDirectory();

    writer.close(false);

    dir.crash();

    /*
    String[] l = dir.list();
    Arrays.sort(l);
    for(int i=0;i<l.length;i++)
      System.out.println("file " + i + " = " + l[i] + " " + dir.fileLength(l[i]) + " bytes");
    */
    IndexReader reader = IndexReader.open(dir, false);
    assertEquals(157, reader.numDocs());
  }

  public void testCrashReaderDeletes() throws IOException {
    
    IndexWriter writer = initIndex(false);
    MockRAMDirectory dir = (MockRAMDirectory) writer.getDirectory();

    writer.close(false);
    IndexReader reader = IndexReader.open(dir, false);
    reader.deleteDocument(3);

    dir.crash();

    /*
    String[] l = dir.list();
    Arrays.sort(l);
    for(int i=0;i<l.length;i++)
      System.out.println("file " + i + " = " + l[i] + " " + dir.fileLength(l[i]) + " bytes");
    */
    reader = IndexReader.open(dir, false);
    assertEquals(157, reader.numDocs());
  }

  public void testCrashReaderDeletesAfterClose() throws IOException {
    
    IndexWriter writer = initIndex(false);
    MockRAMDirectory dir = (MockRAMDirectory) writer.getDirectory();

    writer.close(false);
    IndexReader reader = IndexReader.open(dir, false);
    reader.deleteDocument(3);
    reader.close();

    dir.crash();

    /*
    String[] l = dir.list();
    Arrays.sort(l);
    for(int i=0;i<l.length;i++)
      System.out.println("file " + i + " = " + l[i] + " " + dir.fileLength(l[i]) + " bytes");
    */
    reader = IndexReader.open(dir, false);
    assertEquals(156, reader.numDocs());
  }
}
