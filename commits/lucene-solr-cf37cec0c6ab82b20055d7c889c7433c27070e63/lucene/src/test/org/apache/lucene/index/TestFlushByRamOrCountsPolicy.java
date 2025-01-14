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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DocumentsWriterPerThreadPool.ThreadState;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.MockDirectoryWrapper;
import org.apache.lucene.util.LineFileDocs;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.ThrottledIndexOutput;
import org.junit.Before;

public class TestFlushByRamOrCountsPolicy extends LuceneTestCase {

  private LineFileDocs lineDocFile;
  private int numCPUs;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    lineDocFile = new LineFileDocs(random);
    numCPUs = Runtime.getRuntime().availableProcessors();
  }

  public void testFlushByRam() throws CorruptIndexException,
      LockObtainFailedException, IOException, InterruptedException {
    int[] numThreads = new int[] { numCPUs + random.nextInt(numCPUs + 1), 1 };
    for (int i = 0; i < numThreads.length; i++) {
      runFlushByRam(numThreads[i],
          1 + random.nextInt(10) + random.nextDouble(), false);
    }

    for (int i = 0; i < numThreads.length; i++) {
      // with a 250 mb ram buffer we should never stall
      runFlushByRam(numThreads[i], 250.d, true);
    }
  }

  protected void runFlushByRam(int numThreads, double maxRam,
      boolean ensureNotStalled) throws IOException, CorruptIndexException,
      LockObtainFailedException, InterruptedException {
    final int numDocumentsToIndex = 50 + random.nextInt(150);
    AtomicInteger numDocs = new AtomicInteger(numDocumentsToIndex);
    Directory dir = newDirectory();
    MockDefaultFlushPolicy flushPolicy = new MockDefaultFlushPolicy();
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT,
        new MockAnalyzer()).setFlushPolicy(flushPolicy);

    final int numDWPT = 1 + random.nextInt(8);
    DocumentsWriterPerThreadPool threadPool = new ThreadAffinityDocumentsWriterThreadPool(
        numDWPT);
    iwc.setIndexerThreadPool(threadPool);
    iwc.setRAMBufferSizeMB(1 + random.nextInt(10) + random.nextDouble());
    iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    iwc.setMaxBufferedDeleteTerms(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    IndexWriter writer = new IndexWriter(dir, iwc);
    assertFalse(flushPolicy.flushOnDocCount());
    assertFalse(flushPolicy.flushOnDeleteTerms());
    assertTrue(flushPolicy.flushOnRAM());
    DocumentsWriter docsWriter = writer.getDocsWriter();
    assertNotNull(docsWriter);
    DocumentsWriterFlushControl flushControl = docsWriter.flushControl;
    assertEquals(" bytes must be 0 after init", 0, flushControl.flushBytes());

    IndexThread[] threads = new IndexThread[numThreads];
    for (int x = 0; x < threads.length; x++) {
      threads[x] = new IndexThread(numDocs, numThreads, writer, lineDocFile,
          false);
      threads[x].start();
    }

    for (int x = 0; x < threads.length; x++) {
      threads[x].join();
    }
    final long maxRAMBytes = (long) (iwc.getRAMBufferSizeMB() * 1024. * 1024.);
    assertEquals(" all flushes must be due numThreads=" + numThreads, 0,
        flushControl.flushBytes());
    assertEquals(numDocumentsToIndex, writer.numDocs());
    assertEquals(numDocumentsToIndex, writer.maxDoc());
    assertTrue("peak bytes without flush exceeded watermark",
        flushPolicy.peakBytesWithoutFlush <= maxRAMBytes);
    assertActiveBytesAfter(flushControl);
    if (flushPolicy.hasMarkedPending) {
      assertTrue(maxRAMBytes < flushControl.peakActiveBytes);
    }
    if (ensureNotStalled) {
      assertFalse(docsWriter.healthiness.wasStalled);
    }
    writer.close();
    assertEquals(0, flushControl.activeBytes());
    dir.close();
  }

  public void testFlushDocCount() throws CorruptIndexException,
      LockObtainFailedException, IOException, InterruptedException {
    int[] numThreads = new int[] { numCPUs + random.nextInt(numCPUs + 1), 1 };
    for (int i = 0; i < numThreads.length; i++) {

      final int numDocumentsToIndex = 50 + random.nextInt(150);
      AtomicInteger numDocs = new AtomicInteger(numDocumentsToIndex);
      Directory dir = newDirectory();
      MockDefaultFlushPolicy flushPolicy = new MockDefaultFlushPolicy();
      IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT,
          new MockAnalyzer()).setFlushPolicy(flushPolicy);

      final int numDWPT = 1 + random.nextInt(8);
      DocumentsWriterPerThreadPool threadPool = new ThreadAffinityDocumentsWriterThreadPool(
          numDWPT);
      iwc.setIndexerThreadPool(threadPool);
      iwc.setMaxBufferedDocs(2 + random.nextInt(50));
      iwc.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);
      iwc.setMaxBufferedDeleteTerms(IndexWriterConfig.DISABLE_AUTO_FLUSH);
      IndexWriter writer = new IndexWriter(dir, iwc);
      assertTrue(flushPolicy.flushOnDocCount());
      assertFalse(flushPolicy.flushOnDeleteTerms());
      assertFalse(flushPolicy.flushOnRAM());
      DocumentsWriter docsWriter = writer.getDocsWriter();
      assertNotNull(docsWriter);
      DocumentsWriterFlushControl flushControl = docsWriter.flushControl;
      assertEquals(" bytes must be 0 after init", 0, flushControl.flushBytes());

      IndexThread[] threads = new IndexThread[numThreads[i]];
      for (int x = 0; x < threads.length; x++) {
        threads[x] = new IndexThread(numDocs, numThreads[i], writer,
            lineDocFile, false);
        threads[x].start();
      }

      for (int x = 0; x < threads.length; x++) {
        threads[x].join();
      }

      assertEquals(" all flushes must be due numThreads=" + numThreads[i], 0,
          flushControl.flushBytes());
      assertEquals(numDocumentsToIndex, writer.numDocs());
      assertEquals(numDocumentsToIndex, writer.maxDoc());
      assertTrue("peak bytes without flush exceeded watermark",
          flushPolicy.peakDocCountWithoutFlush <= iwc.getMaxBufferedDocs());
      assertActiveBytesAfter(flushControl);
      writer.close();
      assertEquals(0, flushControl.activeBytes());
      dir.close();
    }
  }

  public void testFlushPolicySetup() throws IOException {
    Directory dir = newDirectory();
    FlushByRamOrCountsPolicy flushPolicy = new FlushByRamOrCountsPolicy();
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT,
        new MockAnalyzer()).setFlushPolicy(flushPolicy);

    final int numDWPT = 1 + random.nextInt(10);
    DocumentsWriterPerThreadPool threadPool = new ThreadAffinityDocumentsWriterThreadPool(
        numDWPT);
    iwc.setIndexerThreadPool(threadPool);
    double maxMB = 1.0 + Math.ceil(random.nextDouble());
    iwc.setRAMBufferSizeMB(maxMB);
    iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);

    IndexWriter writer = new IndexWriter(dir, iwc);
    assertEquals((long) (maxMB * 1024. * 1024. * 2.),
        flushPolicy.getMaxNetBytes());

    writer.close();
    dir.close();
  }

  public void testRandom() throws IOException, InterruptedException {
    final int numThreads = 1 + random.nextInt(8);
    final int numDocumentsToIndex = 100 + random.nextInt(300);
    AtomicInteger numDocs = new AtomicInteger(numDocumentsToIndex);
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT,
        new MockAnalyzer());
    MockDefaultFlushPolicy flushPolicy = new MockDefaultFlushPolicy();
    iwc.setFlushPolicy(flushPolicy);

    final int numDWPT = 1 + random.nextInt(8);
    DocumentsWriterPerThreadPool threadPool = new ThreadAffinityDocumentsWriterThreadPool(
        numDWPT);
    iwc.setIndexerThreadPool(threadPool);

    IndexWriter writer = new IndexWriter(dir, iwc);
    DocumentsWriter docsWriter = writer.getDocsWriter();
    assertNotNull(docsWriter);
    DocumentsWriterFlushControl flushControl = docsWriter.flushControl;

    assertEquals(" bytes must be 0 after init", 0, flushControl.flushBytes());

    IndexThread[] threads = new IndexThread[numThreads];
    for (int x = 0; x < threads.length; x++) {
      threads[x] = new IndexThread(numDocs, numThreads, writer, lineDocFile,
          true);
      threads[x].start();
    }

    for (int x = 0; x < threads.length; x++) {
      threads[x].join();
    }

    assertEquals(" all flushes must be due", 0, flushControl.flushBytes());
    assertEquals(numDocumentsToIndex, writer.numDocs());
    assertEquals(numDocumentsToIndex, writer.maxDoc());
    if (flushPolicy.flushOnRAM() && !flushPolicy.flushOnDocCount()
        && !flushPolicy.flushOnDeleteTerms()) {
      final long maxRAMBytes = (long) (iwc.getRAMBufferSizeMB() * 1024. * 1024.);
      assertTrue("peak bytes without flush exceeded watermark",
          flushPolicy.peakBytesWithoutFlush <= maxRAMBytes);
      if (flushPolicy.hasMarkedPending) {
        assertTrue("max: " + maxRAMBytes + " " + flushControl.peakActiveBytes,
            maxRAMBytes <= flushControl.peakActiveBytes);
      }
    }
    assertActiveBytesAfter(flushControl);
    writer.commit();
    assertEquals(0, flushControl.activeBytes());
    IndexReader r = IndexReader.open(dir);
    assertEquals(numDocumentsToIndex, r.numDocs());
    assertEquals(numDocumentsToIndex, r.maxDoc());
    if (!flushPolicy.flushOnRAM()) {
      assertFalse("never stall if we don't flush on RAM", docsWriter.healthiness.wasStalled);
      assertFalse("never block if we don't flush on RAM", docsWriter.healthiness.hasBlocked());
    }
    r.close();
    writer.close();
    dir.close();
  }

  public void testHealthyness() throws InterruptedException,
      CorruptIndexException, LockObtainFailedException, IOException {

    int[] numThreads = new int[] { 3 + random.nextInt(8), 1 };
    final int numDocumentsToIndex = 50 + random.nextInt(50);
    for (int i = 0; i < numThreads.length; i++) {
      AtomicInteger numDocs = new AtomicInteger(numDocumentsToIndex);
      MockDirectoryWrapper dir = newDirectory();
      // mock a very slow harddisk here so that flushing is very slow
      dir.setThrottledIndexOutput(new ThrottledIndexOutput(ThrottledIndexOutput
          .mBitsToBytes(50 + random.nextInt(10)), 5 + random.nextInt(5), null));
      IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT,
          new MockAnalyzer());
      iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
      iwc.setMaxBufferedDeleteTerms(IndexWriterConfig.DISABLE_AUTO_FLUSH);
      FlushPolicy flushPolicy = new FlushByRamOrCountsPolicy();
      iwc.setFlushPolicy(flushPolicy);

      DocumentsWriterPerThreadPool threadPool = new ThreadAffinityDocumentsWriterThreadPool(
          numThreads[i]== 1 ? 1 : 2);
      iwc.setIndexerThreadPool(threadPool);
      // with such a small ram buffer we should be stalled quiet quickly
      iwc.setRAMBufferSizeMB(0.25);
      IndexWriter writer = new IndexWriter(dir, iwc);
      IndexThread[] threads = new IndexThread[numThreads[i]];
      for (int x = 0; x < threads.length; x++) {
        threads[x] = new IndexThread(numDocs, numThreads[i], writer,
            lineDocFile, false);
        threads[x].start();
      }

      for (int x = 0; x < threads.length; x++) {
        threads[x].join();
      }
      DocumentsWriter docsWriter = writer.getDocsWriter();
      assertNotNull(docsWriter);
      DocumentsWriterFlushControl flushControl = docsWriter.flushControl;
      assertEquals(" all flushes must be due", 0, flushControl.flushBytes());
      assertEquals(numDocumentsToIndex, writer.numDocs());
      assertEquals(numDocumentsToIndex, writer.maxDoc());
      if (flushControl.peakNetBytes > (long)(iwc.getRAMBufferSizeMB() * 1024d * 1024d * 2d)) {
        assertTrue("should be unhealthy here numThreads: " + numThreads[i],
            docsWriter.healthiness.wasStalled);
      }

      if (numThreads[i] == 1) { // single thread could be unhealthy is a single
                                // doc is very large?!
        assertFalse(
            "single thread must not block numThreads: " + numThreads[i],
            docsWriter.healthiness.hasBlocked());
      } else {
        if (docsWriter.healthiness.wasStalled) {
          // TODO maybe this assumtion is too strickt
          assertTrue(" we should have blocked here numThreads: "
              + numThreads[i], docsWriter.healthiness.hasBlocked());
        }
      }
      assertActiveBytesAfter(flushControl);
      writer.close(true);
      dir.close();
    }
  }

  protected void assertActiveBytesAfter(DocumentsWriterFlushControl flushControl) {
    Iterator<ThreadState> allActiveThreads = flushControl.allActiveThreads();
    long bytesUsed = 0;
    while (allActiveThreads.hasNext()) {
      bytesUsed += allActiveThreads.next().perThread.bytesUsed();
    }
    assertEquals(bytesUsed, flushControl.activeBytes());
  }

  public class IndexThread extends Thread {
    IndexWriter writer;
    IndexWriterConfig iwc;
    LineFileDocs docs;
    private AtomicInteger pendingDocs;
    private final boolean doRandomCommit;

    public IndexThread(AtomicInteger pendingDocs, int numThreads,
        IndexWriter writer, LineFileDocs docs, boolean doRandomCommit) {
      this.pendingDocs = pendingDocs;
      this.writer = writer;
      iwc = writer.getConfig();
      this.docs = docs;
      this.doRandomCommit = doRandomCommit;
    }

    public void run() {
      try {
        long ramSize = 0;
        while (pendingDocs.decrementAndGet() > -1) {
          Document doc = docs.nextDoc();
          writer.addDocument(doc);
          long newRamSize = writer.ramSizeInBytes();
          if (newRamSize != ramSize) {
            ramSize = newRamSize;
          }
          if (doRandomCommit) {
            int commit;
            synchronized (random) {
              commit = random.nextInt(20);
            }
            if (commit == 0) {
              writer.commit();
            }
          }
        }
      } catch (Throwable ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  private static class MockDefaultFlushPolicy extends FlushByRamOrCountsPolicy {
    long peakBytesWithoutFlush = Integer.MIN_VALUE;
    long peakDocCountWithoutFlush = Integer.MIN_VALUE;
    boolean hasMarkedPending = false;

    @Override
    public void onDelete(DocumentsWriterFlushControl control, ThreadState state) {
      final ArrayList<ThreadState> pending = new ArrayList<DocumentsWriterPerThreadPool.ThreadState>();
      final ArrayList<ThreadState> notPending = new ArrayList<DocumentsWriterPerThreadPool.ThreadState>();
      findPending(control, pending, notPending);
      final boolean flushCurrent = state.flushPending;
      final ThreadState toFlush;
      if (state.flushPending) {
        toFlush = state;
      } else if (flushOnDeleteTerms()
          && state.perThread.pendingDeletes.numTermDeletes.get() >= indexWriterConfig
              .getMaxBufferedDeleteTerms()) {
        toFlush = state;
      } else {
        toFlush = null;
      }
      super.onDelete(control, state);
      if (toFlush != null) {
        if (flushCurrent) {
          assertTrue(pending.remove(toFlush));
        } else {
          assertTrue(notPending.remove(toFlush));
        }
        assertTrue(toFlush.flushPending);
        hasMarkedPending = true;
      }

      for (ThreadState threadState : notPending) {
        assertFalse(threadState.flushPending);
      }
    }

    @Override
    public void onInsert(DocumentsWriterFlushControl control, ThreadState state) {
      final ArrayList<ThreadState> pending = new ArrayList<DocumentsWriterPerThreadPool.ThreadState>();
      final ArrayList<ThreadState> notPending = new ArrayList<DocumentsWriterPerThreadPool.ThreadState>();
      findPending(control, pending, notPending);
      final boolean flushCurrent = state.flushPending;
      long activeBytes = control.activeBytes();
      final ThreadState toFlush;
      if (state.flushPending) {
        toFlush = state;
      } else if (flushOnDocCount()
          && state.perThread.getNumDocsInRAM() >= indexWriterConfig
              .getMaxBufferedDocs()) {
        toFlush = state;
      } else if (flushOnRAM()
          && activeBytes >= (long) (indexWriterConfig.getRAMBufferSizeMB() * 1024. * 1024.)) {
        toFlush = findLargestNonPendingWriter(control, state);
        assertFalse(toFlush.flushPending);
      } else {
        toFlush = null;
      }
      super.onInsert(control, state);
      if (toFlush != null) {
        if (flushCurrent) {
          assertTrue(pending.remove(toFlush));
        } else {
          assertTrue(notPending.remove(toFlush));
        }
        assertTrue(toFlush.flushPending);
        hasMarkedPending = true;
      } else {
        peakBytesWithoutFlush = Math.max(activeBytes, peakBytesWithoutFlush);
        peakDocCountWithoutFlush = Math.max(state.perThread.getNumDocsInRAM(),
            peakDocCountWithoutFlush);
      }

      for (ThreadState threadState : notPending) {
        assertFalse(threadState.flushPending);
      }
    }
  }

  static void findPending(DocumentsWriterFlushControl flushControl,
      ArrayList<ThreadState> pending, ArrayList<ThreadState> notPending) {
    Iterator<ThreadState> allActiveThreads = flushControl.allActiveThreads();
    while (allActiveThreads.hasNext()) {
      ThreadState next = allActiveThreads.next();
      if (next.flushPending) {
        pending.add(next);
      } else {
        notPending.add(next);
      }
    }
  }
}
