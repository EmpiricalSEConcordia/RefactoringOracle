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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexDocument;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.ThreadedIndexingAndSearchingTestCase;
import org.apache.lucene.index.TrackingIndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NRTCachingDirectory;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.LuceneTestCase.SuppressCodecs;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.ThreadInterruptedException;
import org.apache.lucene.util.Version;

@SuppressCodecs({ "SimpleText", "Memory", "Direct" })
public class TestControlledRealTimeReopenThread extends ThreadedIndexingAndSearchingTestCase {

  // Not guaranteed to reflect deletes:
  private SearcherManager nrtNoDeletes;

  // Is guaranteed to reflect deletes:
  private SearcherManager nrtDeletes;

  private TrackingIndexWriter genWriter;

  private ControlledRealTimeReopenThread<IndexSearcher> nrtDeletesThread;
  private ControlledRealTimeReopenThread<IndexSearcher> nrtNoDeletesThread;

  private final ThreadLocal<Long> lastGens = new ThreadLocal<>();
  private boolean warmCalled;

  public void testControlledRealTimeReopenThread() throws Exception {
    runTest("TestControlledRealTimeReopenThread");
  }

  @Override
  protected IndexSearcher getFinalSearcher() throws Exception  {
    if (VERBOSE) {
      System.out.println("TEST: finalSearcher maxGen=" + maxGen);
    }
    nrtDeletesThread.waitForGeneration(maxGen);
    return nrtDeletes.acquire();
  }

  @Override
  protected Directory getDirectory(Directory in) {
    // Randomly swap in NRTCachingDir
    if (random().nextBoolean()) {
      if (VERBOSE) {
        System.out.println("TEST: wrap NRTCachingDir");
      }

      return new NRTCachingDirectory(in, 5.0, 60.0);
    } else {
      return in;
    }
  }

  @Override
  protected void updateDocuments(Term id, List<? extends IndexDocument> docs) throws Exception {
    final long gen = genWriter.updateDocuments(id, docs);

    // Randomly verify the update "took":
    if (random().nextInt(20) == 2) {
      if (VERBOSE) {
        System.out.println(Thread.currentThread().getName() + ": nrt: verify " + id);
      }
      nrtDeletesThread.waitForGeneration(gen);
      final IndexSearcher s = nrtDeletes.acquire();
      if (VERBOSE) {
        System.out.println(Thread.currentThread().getName() + ": nrt: got searcher=" + s);
      }
      try {
        assertEquals(docs.size(), s.search(new TermQuery(id), 10).totalHits);
      } finally {
        nrtDeletes.release(s);
      }
    }
    
    lastGens.set(gen);
  }

  @Override
  protected void addDocuments(Term id, List<? extends IndexDocument> docs) throws Exception {
    final long gen = genWriter.addDocuments(docs);
    // Randomly verify the add "took":
    if (random().nextInt(20) == 2) {
      if (VERBOSE) {
        System.out.println(Thread.currentThread().getName() + ": nrt: verify " + id);
      }
      nrtNoDeletesThread.waitForGeneration(gen);
      final IndexSearcher s = nrtNoDeletes.acquire();
      if (VERBOSE) {
        System.out.println(Thread.currentThread().getName() + ": nrt: got searcher=" + s);
      }
      try {
        assertEquals(docs.size(), s.search(new TermQuery(id), 10).totalHits);
      } finally {
        nrtNoDeletes.release(s);
      }
    }
    lastGens.set(gen);
  }

  @Override
  protected void addDocument(Term id, IndexDocument doc) throws Exception {
    final long gen = genWriter.addDocument(doc);

    // Randomly verify the add "took":
    if (random().nextInt(20) == 2) {
      if (VERBOSE) {
        System.out.println(Thread.currentThread().getName() + ": nrt: verify " + id);
      }
      nrtNoDeletesThread.waitForGeneration(gen);
      final IndexSearcher s = nrtNoDeletes.acquire();
      if (VERBOSE) {
        System.out.println(Thread.currentThread().getName() + ": nrt: got searcher=" + s);
      }
      try {
        assertEquals(1, s.search(new TermQuery(id), 10).totalHits);
      } finally {
        nrtNoDeletes.release(s);
      }
    }
    lastGens.set(gen);
  }

  @Override
  protected void updateDocument(Term id, IndexDocument doc) throws Exception {
    final long gen = genWriter.updateDocument(id, doc);
    // Randomly verify the udpate "took":
    if (random().nextInt(20) == 2) {
      if (VERBOSE) {
        System.out.println(Thread.currentThread().getName() + ": nrt: verify " + id);
      }
      nrtDeletesThread.waitForGeneration(gen);
      final IndexSearcher s = nrtDeletes.acquire();
      if (VERBOSE) {
        System.out.println(Thread.currentThread().getName() + ": nrt: got searcher=" + s);
      }
      try {
        assertEquals(1, s.search(new TermQuery(id), 10).totalHits);
      } finally {
        nrtDeletes.release(s);
      }
    }
    lastGens.set(gen);
  }

  @Override
  protected void deleteDocuments(Term id) throws Exception {
    final long gen = genWriter.deleteDocuments(id);
    // randomly verify the delete "took":
    if (random().nextInt(20) == 7) {
      if (VERBOSE) {
        System.out.println(Thread.currentThread().getName() + ": nrt: verify del " + id);
      }
      nrtDeletesThread.waitForGeneration(gen);
      final IndexSearcher s = nrtDeletes.acquire();
      if (VERBOSE) {
        System.out.println(Thread.currentThread().getName() + ": nrt: got searcher=" + s);
      }
      try {
        assertEquals(0, s.search(new TermQuery(id), 10).totalHits);
      } finally {
        nrtDeletes.release(s);
      }
    }
    lastGens.set(gen);
  }

  @Override
  protected void doAfterWriter(final ExecutorService es) throws Exception {
    final double minReopenSec = 0.01 + 0.05 * random().nextDouble();
    final double maxReopenSec = minReopenSec * (1.0 + 10 * random().nextDouble());

    if (VERBOSE) {
      System.out.println("TEST: make SearcherManager maxReopenSec=" + maxReopenSec + " minReopenSec=" + minReopenSec);
    }

    genWriter = new TrackingIndexWriter(writer);

    final SearcherFactory sf = new SearcherFactory() {
        @Override
        public IndexSearcher newSearcher(IndexReader r) throws IOException {
          TestControlledRealTimeReopenThread.this.warmCalled = true;
          IndexSearcher s = new IndexSearcher(r, es);
          s.search(new TermQuery(new Term("body", "united")), 10);
          return s;
        }
      };

    nrtNoDeletes = new SearcherManager(writer, false, sf);
    nrtDeletes = new SearcherManager(writer, true, sf);
                         
    nrtDeletesThread = new ControlledRealTimeReopenThread<>(genWriter, nrtDeletes, maxReopenSec, minReopenSec);
    nrtDeletesThread.setName("NRTDeletes Reopen Thread");
    nrtDeletesThread.setPriority(Math.min(Thread.currentThread().getPriority()+2, Thread.MAX_PRIORITY));
    nrtDeletesThread.setDaemon(true);
    nrtDeletesThread.start();

    nrtNoDeletesThread = new ControlledRealTimeReopenThread<>(genWriter, nrtNoDeletes, maxReopenSec, minReopenSec);
    nrtNoDeletesThread.setName("NRTNoDeletes Reopen Thread");
    nrtNoDeletesThread.setPriority(Math.min(Thread.currentThread().getPriority()+2, Thread.MAX_PRIORITY));
    nrtNoDeletesThread.setDaemon(true);
    nrtNoDeletesThread.start();
  }

  @Override
  protected void doAfterIndexingThreadDone() {
    Long gen = lastGens.get();
    if (gen != null) {
      addMaxGen(gen);
    }
  }

  private long maxGen = -1;

  private synchronized void addMaxGen(long gen) {
    maxGen = Math.max(gen, maxGen);
  }

  @Override
  protected void doSearching(ExecutorService es, long stopTime) throws Exception {
    runSearchThreads(stopTime);
  }

  @Override
  protected IndexSearcher getCurrentSearcher() throws Exception {
    // Test doesn't assert deletions until the end, so we
    // can randomize whether dels must be applied
    final SearcherManager nrt;
    if (random().nextBoolean()) {
      nrt = nrtDeletes;
    } else {
      nrt = nrtNoDeletes;
    }

    return nrt.acquire();
  }

  @Override
  protected void releaseSearcher(IndexSearcher s) throws Exception {
    // NOTE: a bit iffy... technically you should release
    // against the same SearcherManager you acquired from... but
    // both impls just decRef the underlying reader so we
    // can get away w/ cheating:
    nrtNoDeletes.release(s);
  }

  @Override
  protected void doClose() throws Exception {
    assertTrue(warmCalled);
    if (VERBOSE) {
      System.out.println("TEST: now close SearcherManagers");
    }
    nrtDeletesThread.close();
    nrtDeletes.close();
    nrtNoDeletesThread.close();
    nrtNoDeletes.close();
  }
  
  /*
   * LUCENE-3528 - NRTManager hangs in certain situations 
   */
  public void testThreadStarvationNoDeleteNRTReader() throws IOException, InterruptedException {
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    conf.setMergePolicy(random().nextBoolean() ? NoMergePolicy.COMPOUND_FILES : NoMergePolicy.NO_COMPOUND_FILES);
    Directory d = newDirectory();
    final CountDownLatch latch = new CountDownLatch(1);
    final CountDownLatch signal = new CountDownLatch(1);

    LatchedIndexWriter _writer = new LatchedIndexWriter(d, conf, latch, signal);
    final TrackingIndexWriter writer = new TrackingIndexWriter(_writer);
    final SearcherManager manager = new SearcherManager(_writer, false, null);
    Document doc = new Document();
    doc.add(newTextField("test", "test", Field.Store.YES));
    writer.addDocument(doc);
    manager.maybeRefresh();
    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          signal.await();
          manager.maybeRefresh();
          writer.deleteDocuments(new TermQuery(new Term("foo", "barista")));
          manager.maybeRefresh(); // kick off another reopen so we inc. the internal gen
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          latch.countDown(); // let the add below finish
        }
      }
    };
    t.start();
    _writer.waitAfterUpdate = true; // wait in addDocument to let some reopens go through
    final long lastGen = writer.updateDocument(new Term("foo", "bar"), doc); // once this returns the doc is already reflected in the last reopen

    assertFalse(manager.isSearcherCurrent()); // false since there is a delete in the queue
    
    IndexSearcher searcher = manager.acquire();
    try {
      assertEquals(2, searcher.getIndexReader().numDocs());
    } finally {
      manager.release(searcher);
    }
    final ControlledRealTimeReopenThread<IndexSearcher> thread = new ControlledRealTimeReopenThread<>(writer, manager, 0.01, 0.01);
    thread.start(); // start reopening
    if (VERBOSE) {
      System.out.println("waiting now for generation " + lastGen);
    }
    
    final AtomicBoolean finished = new AtomicBoolean(false);
    Thread waiter = new Thread() {
      @Override
      public void run() {
        try {
          thread.waitForGeneration(lastGen);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(ie);
        }
        finished.set(true);
      }
    };
    waiter.start();
    manager.maybeRefresh();
    waiter.join(1000);
    if (!finished.get()) {
      waiter.interrupt();
      fail("thread deadlocked on waitForGeneration");
    }
    thread.close();
    thread.join();
    _writer.shutdown();
    IOUtils.close(manager, d);
  }
  
  public static class LatchedIndexWriter extends IndexWriter {

    private CountDownLatch latch;
    boolean waitAfterUpdate = false;
    private CountDownLatch signal;

    public LatchedIndexWriter(Directory d, IndexWriterConfig conf,
        CountDownLatch latch, CountDownLatch signal)
        throws IOException {
      super(d, conf);
      this.latch = latch;
      this.signal = signal;

    }

    @Override
    public void updateDocument(Term term,
        IndexDocument doc, Analyzer analyzer)
        throws IOException {
      super.updateDocument(term, doc, analyzer);
      try {
        if (waitAfterUpdate) {
          signal.countDown();
          latch.await();
        }
      } catch (InterruptedException e) {
        throw new ThreadInterruptedException(e);
      }
    }
  }

  public void testEvilSearcherFactory() throws Exception {
    final Directory dir = newDirectory();
    final RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    w.commit();

    final IndexReader other = DirectoryReader.open(dir);

    final SearcherFactory theEvilOne = new SearcherFactory() {
      @Override
      public IndexSearcher newSearcher(IndexReader ignored) {
        return LuceneTestCase.newSearcher(other);
      }
      };

    try {
      new SearcherManager(w.w, false, theEvilOne);
      fail("didn't hit expected exception");
    } catch (IllegalStateException ise) {
      // expected
    }
    w.shutdown();
    other.close();
    dir.close();
  }

  public void testListenerCalled() throws Exception {
    Directory dir = newDirectory();
    IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig(TEST_VERSION_CURRENT, null));
    final AtomicBoolean afterRefreshCalled = new AtomicBoolean(false);
    SearcherManager sm = new SearcherManager(iw, true, new SearcherFactory());
    sm.addListener(new ReferenceManager.RefreshListener() {
      @Override
      public void beforeRefresh() {
      }
      @Override
      public void afterRefresh(boolean didRefresh) {
        if (didRefresh) {
          afterRefreshCalled.set(true);
        }
      }
    });
    iw.addDocument(new Document());
    iw.commit();
    assertFalse(afterRefreshCalled.get());
    sm.maybeRefreshBlocking();
    assertTrue(afterRefreshCalled.get());
    sm.close();
    iw.shutdown();
    dir.close();
  }

  // LUCENE-5461
  public void testCRTReopen() throws Exception {
    //test behaving badly

    //should be high enough
    int maxStaleSecs = 20;

    //build crap data just to store it.
    String s = "        abcdefghijklmnopqrstuvwxyz     ";
    char[] chars = s.toCharArray();
    StringBuilder builder = new StringBuilder(2048);
    for (int i = 0; i < 2048; i++) {
      builder.append(chars[random().nextInt(chars.length)]);
    }
    String content = builder.toString();

    final SnapshotDeletionPolicy sdp = new SnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy());
    final Directory dir = new NRTCachingDirectory(newFSDirectory(createTempDir("nrt")), 5, 128);
    IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_4_6,
                                                     new MockAnalyzer(random()));
    config.setIndexDeletionPolicy(sdp);
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
    final IndexWriter iw = new IndexWriter(dir, config);
    SearcherManager sm = new SearcherManager(iw, true, new SearcherFactory());
    final TrackingIndexWriter tiw = new TrackingIndexWriter(iw);
    ControlledRealTimeReopenThread<IndexSearcher> controlledRealTimeReopenThread =
      new ControlledRealTimeReopenThread<>(tiw, sm, maxStaleSecs, 0);

    controlledRealTimeReopenThread.setDaemon(true);
    controlledRealTimeReopenThread.start();

    List<Thread> commitThreads = new ArrayList<>();

    for (int i = 0; i < 500; i++) {
      if (i > 0 && i % 50 == 0) {
        Thread commitThread =  new Thread(new Runnable() {
            @Override
            public void run() {
              try {
                iw.commit();
                IndexCommit ic = sdp.snapshot();
                for (String name : ic.getFileNames()) {
                  //distribute, and backup
                  //System.out.println(names);
                  assertTrue(slowFileExists(dir, name));
                }
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
          });
        commitThread.start();
        commitThreads.add(commitThread);
      }
      Document d = new Document();
      d.add(new TextField("count", i + "", Field.Store.NO));
      d.add(new TextField("content", content, Field.Store.YES));
      long start = System.currentTimeMillis();
      long l = tiw.addDocument(d);
      controlledRealTimeReopenThread.waitForGeneration(l);
      long wait = System.currentTimeMillis() - start;
      assertTrue("waited too long for generation " + wait,
                 wait < (maxStaleSecs *1000));
      IndexSearcher searcher = sm.acquire();
      TopDocs td = searcher.search(new TermQuery(new Term("count", i + "")), 10);
      sm.release(searcher);
      assertEquals(1, td.totalHits);
    }

    for(Thread commitThread : commitThreads) {
      commitThread.join();
    }

    controlledRealTimeReopenThread.close();
    sm.close();
    iw.shutdown();
    dir.close();
  }
}
