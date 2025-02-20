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
import java.io.PrintStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DocumentsWriterPerThread.FlushedSegment;
import org.apache.lucene.index.DocumentsWriterPerThread.IndexingChain;
import org.apache.lucene.index.DocumentsWriterPerThreadPool.ThreadState;
import org.apache.lucene.index.FieldInfos.FieldNumberBiMap;
import org.apache.lucene.index.codecs.Codec;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.similarities.SimilarityProvider;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.InfoStream;

/**
 * This class accepts multiple added documents and directly
 * writes segment files.
 *
 * Each added document is passed to the {@link DocConsumer},
 * which in turn processes the document and interacts with
 * other consumers in the indexing chain.  Certain
 * consumers, like {@link StoredFieldsConsumer} and {@link
 * TermVectorsConsumer}, digest a document and
 * immediately write bytes to the "doc store" files (ie,
 * they do not consume RAM per document, except while they
 * are processing the document).
 *
 * Other consumers, eg {@link FreqProxTermsWriter} and
 * {@link NormsConsumer}, buffer bytes in RAM and flush only
 * when a new segment is produced.

 * Once we have used our allowed RAM buffer, or the number
 * of added docs is large enough (in the case we are
 * flushing by doc count instead of RAM usage), we create a
 * real segment and flush it to the Directory.
 *
 * Threads:
 *
 * Multiple threads are allowed into addDocument at once.
 * There is an initial synchronized call to getThreadState
 * which allocates a ThreadState for this thread.  The same
 * thread will get the same ThreadState over time (thread
 * affinity) so that if there are consistent patterns (for
 * example each thread is indexing a different content
 * source) then we make better use of RAM.  Then
 * processDocument is called on that ThreadState without
 * synchronization (most of the "heavy lifting" is in this
 * call).  Finally the synchronized "finishDocument" is
 * called to flush changes to the directory.
 *
 * When flush is called by IndexWriter we forcefully idle
 * all threads and flush only once they are all idle.  This
 * means you can call flush with a given thread even while
 * other threads are actively adding/deleting documents.
 *
 *
 * Exceptions:
 *
 * Because this class directly updates in-memory posting
 * lists, and flushes stored fields and term vectors
 * directly to files in the directory, there are certain
 * limited times when an exception can corrupt this state.
 * For example, a disk full while flushing stored fields
 * leaves this file in a corrupt state.  Or, an OOM
 * exception while appending to the in-memory posting lists
 * can corrupt that posting list.  We call such exceptions
 * "aborting exceptions".  In these cases we must call
 * abort() to discard all docs added since the last flush.
 *
 * All other exceptions ("non-aborting exceptions") can
 * still partially update the index structures.  These
 * updates are consistent, but, they represent only a part
 * of the document seen up until the exception was hit.
 * When this happens, we immediately mark the document as
 * deleted so that the document is always atomically ("all
 * or none") added to the index.
 */

final class DocumentsWriter {
  Directory directory;

  private volatile boolean closed;

  final InfoStream infoStream;
  SimilarityProvider similarityProvider;

  List<String> newFiles;

  final IndexWriter indexWriter;

  private AtomicInteger numDocsInRAM = new AtomicInteger(0);

  // TODO: cut over to BytesRefHash in BufferedDeletes
  volatile DocumentsWriterDeleteQueue deleteQueue = new DocumentsWriterDeleteQueue();
  private final TicketQueue ticketQueue = new TicketQueue();
  /*
   * we preserve changes during a full flush since IW might not checkout before
   * we release all changes. NRT Readers otherwise suddenly return true from
   * isCurrent while there are actually changes currently committed. See also
   * #anyChanges() & #flushAllThreads
   */
  private volatile boolean pendingChangesInCurrentFullFlush;

  private Collection<String> abortedFiles;               // List of files that were written before last abort()

  final IndexingChain chain;

  final DocumentsWriterPerThreadPool perThreadPool;
  final FlushPolicy flushPolicy;
  final DocumentsWriterFlushControl flushControl;
  
  final Codec codec;
  DocumentsWriter(Codec codec, IndexWriterConfig config, Directory directory, IndexWriter writer, FieldNumberBiMap globalFieldNumbers,
      BufferedDeletesStream bufferedDeletesStream) throws IOException {
    this.codec = codec;
    this.directory = directory;
    this.indexWriter = writer;
    this.infoStream = config.getInfoStream();
    this.similarityProvider = config.getSimilarityProvider();
    this.perThreadPool = config.getIndexerThreadPool();
    this.chain = config.getIndexingChain();
    this.perThreadPool.initialize(this, globalFieldNumbers, config);
    flushPolicy = config.getFlushPolicy();
    assert flushPolicy != null;
    flushPolicy.init(this);
    flushControl = new DocumentsWriterFlushControl(this, config);
  }

  synchronized void deleteQueries(final Query... queries) throws IOException {
    deleteQueue.addDelete(queries);
    flushControl.doOnDelete();
    if (flushControl.doApplyAllDeletes()) {
      applyAllDeletes(deleteQueue);
    }
  }

  // TODO: we could check w/ FreqProxTermsWriter: if the
  // term doesn't exist, don't bother buffering into the
  // per-DWPT map (but still must go into the global map)
  synchronized void deleteTerms(final Term... terms) throws IOException {
    final DocumentsWriterDeleteQueue deleteQueue = this.deleteQueue;
    deleteQueue.addDelete(terms);
    flushControl.doOnDelete();
    if (flushControl.doApplyAllDeletes()) {
      applyAllDeletes(deleteQueue);
    }
  }

  DocumentsWriterDeleteQueue currentDeleteSession() {
    return deleteQueue;
  }
  
  private void applyAllDeletes(DocumentsWriterDeleteQueue deleteQueue) throws IOException {
    if (deleteQueue != null && !flushControl.isFullFlush()) {
      synchronized (ticketQueue) {
        ticketQueue.incTicketCount();// first inc the ticket count - freeze opens a window for #anyChanges to fail
        // Freeze and insert the delete flush ticket in the queue
        ticketQueue.add(new FlushTicket(deleteQueue.freezeGlobalBuffer(null), false));
        applyFlushTickets();
      }
    }
    indexWriter.applyAllDeletes();
    indexWriter.flushCount.incrementAndGet();
  }

  /** Returns how many docs are currently buffered in RAM. */
  int getNumDocs() {
    return numDocsInRAM.get();
  }

  Collection<String> abortedFiles() {
    return abortedFiles;
  }

  private void ensureOpen() throws AlreadyClosedException {
    if (closed) {
      throw new AlreadyClosedException("this IndexWriter is closed");
    }
  }

  /** Called if we hit an exception at a bad time (when
   *  updating the index files) and must discard all
   *  currently buffered docs.  This resets our state,
   *  discarding any docs added since last flush. */
  synchronized void abort() throws IOException {
    boolean success = false;

    synchronized (this) {
      deleteQueue.clear();
    }

    try {
      infoStream.message("DW", "abort");

      final Iterator<ThreadState> threadsIterator = perThreadPool.getActivePerThreadsIterator();
      while (threadsIterator.hasNext()) {
        final ThreadState perThread = threadsIterator.next();
        perThread.lock();
        try {
          if (perThread.isActive()) { // we might be closed
            try {
              perThread.perThread.abort();
            } catch (IOException ex) {
              // continue
            } finally {
              perThread.perThread.checkAndResetHasAborted();
              flushControl.doOnAbort(perThread);
            }
          } else {
            assert closed;
          }
        } finally {
          perThread.unlock();
        }
      }
      success = true;
    } finally {
      if (infoStream.isEnabled("DW")) {
        infoStream.message("DW", "done abort; abortedFiles=" + abortedFiles + " success=" + success);
      }
    }
  }

  boolean anyChanges() {
    if (infoStream.isEnabled("DW")) {
      infoStream.message("DW", "anyChanges? numDocsInRam=" + numDocsInRAM.get()
          + " deletes=" + anyDeletions() + " hasTickets:"
          + ticketQueue.hasTickets() + " pendingChangesInFullFlush: "
          + pendingChangesInCurrentFullFlush);
    }
    /*
     * changes are either in a DWPT or in the deleteQueue.
     * yet if we currently flush deletes and / or dwpt there
     * could be a window where all changes are in the ticket queue
     * before they are published to the IW. ie we need to check if the 
     * ticket queue has any tickets.
     */
    return numDocsInRAM.get() != 0 || anyDeletions() || ticketQueue.hasTickets() || pendingChangesInCurrentFullFlush;
  }
  
  public int getBufferedDeleteTermsSize() {
    return deleteQueue.getBufferedDeleteTermsSize();
  }

  //for testing
  public int getNumBufferedDeleteTerms() {
    return deleteQueue.numGlobalTermDeletes();
  }

  public boolean anyDeletions() {
    return deleteQueue.anyChanges();
  }

  void close() {
    closed = true;
    flushControl.setClosed();
  }

  private boolean preUpdate() throws CorruptIndexException, IOException {
    ensureOpen();
    boolean maybeMerge = false;
    if (flushControl.anyStalledThreads() || flushControl.numQueuedFlushes() > 0) {
      // Help out flushing any queued DWPTs so we can un-stall:
      if (infoStream.isEnabled("DW")) {
        infoStream.message("DW", "DocumentsWriter has queued dwpt; will hijack this thread to flush pending segment(s)");
      }
      do {
        // Try pick up pending threads here if possible
        DocumentsWriterPerThread flushingDWPT;
        while ((flushingDWPT = flushControl.nextPendingFlush()) != null) {
          // Don't push the delete here since the update could fail!
          maybeMerge |= doFlush(flushingDWPT);
        }
  
        if (infoStream.isEnabled("DW") && flushControl.anyStalledThreads()) {
          infoStream.message("DW", "WARNING DocumentsWriter has stalled threads; waiting");
        }
        
        flushControl.waitIfStalled(); // block if stalled
      } while (flushControl.numQueuedFlushes() != 0); // still queued DWPTs try help flushing

      if (infoStream.isEnabled("DW")) {
        infoStream.message("DW", "continue indexing after helping out flushing DocumentsWriter is healthy");
      }
    }
    return maybeMerge;
  }

  private boolean postUpdate(DocumentsWriterPerThread flushingDWPT, boolean maybeMerge) throws IOException {
    if (flushControl.doApplyAllDeletes()) {
      applyAllDeletes(deleteQueue);
    }
    if (flushingDWPT != null) {
      maybeMerge |= doFlush(flushingDWPT);
    } else {
      final DocumentsWriterPerThread nextPendingFlush = flushControl.nextPendingFlush();
      if (nextPendingFlush != null) {
        maybeMerge |= doFlush(nextPendingFlush);
      }
    }

    return maybeMerge;
  }

  boolean updateDocuments(final Iterable<? extends Iterable<? extends IndexableField>> docs, final Analyzer analyzer,
                          final Term delTerm) throws CorruptIndexException, IOException {
    boolean maybeMerge = preUpdate();

    final ThreadState perThread = flushControl.obtainAndLock();
    final DocumentsWriterPerThread flushingDWPT;
    
    try {
      if (!perThread.isActive()) {
        ensureOpen();
        assert false: "perThread is not active but we are still open";
      }
       
      final DocumentsWriterPerThread dwpt = perThread.perThread;
      try {
        final int docCount = dwpt.updateDocuments(docs, analyzer, delTerm);
        numDocsInRAM.addAndGet(docCount);
      } finally {
        if (dwpt.checkAndResetHasAborted()) {
          flushControl.doOnAbort(perThread);
        }
      }
      final boolean isUpdate = delTerm != null;
      flushingDWPT = flushControl.doAfterDocument(perThread, isUpdate);
    } finally {
      perThread.unlock();
    }

    return postUpdate(flushingDWPT, maybeMerge);
  }

  boolean updateDocument(final Iterable<? extends IndexableField> doc, final Analyzer analyzer,
      final Term delTerm) throws CorruptIndexException, IOException {

    boolean maybeMerge = preUpdate();

    final ThreadState perThread = flushControl.obtainAndLock();

    final DocumentsWriterPerThread flushingDWPT;
    
    try {

      if (!perThread.isActive()) {
        ensureOpen();
        assert false: "perThread is not active but we are still open";
      }
       
      final DocumentsWriterPerThread dwpt = perThread.perThread;
      try {
        dwpt.updateDocument(doc, analyzer, delTerm); 
        numDocsInRAM.incrementAndGet();
      } finally {
        if (dwpt.checkAndResetHasAborted()) {
          flushControl.doOnAbort(perThread);
        }
      }
      final boolean isUpdate = delTerm != null;
      flushingDWPT = flushControl.doAfterDocument(perThread, isUpdate);
    } finally {
      perThread.unlock();
    }

    return postUpdate(flushingDWPT, maybeMerge);
  }

  private  boolean doFlush(DocumentsWriterPerThread flushingDWPT) throws IOException {
    boolean maybeMerge = false;
    while (flushingDWPT != null) {
      maybeMerge = true;
      boolean success = false;
      FlushTicket ticket = null;
      try {
        assert currentFullFlushDelQueue == null
            || flushingDWPT.deleteQueue == currentFullFlushDelQueue : "expected: "
            + currentFullFlushDelQueue + "but was: " + flushingDWPT.deleteQueue
            + " " + flushControl.isFullFlush();
        /*
         * Since with DWPT the flush process is concurrent and several DWPT
         * could flush at the same time we must maintain the order of the
         * flushes before we can apply the flushed segment and the frozen global
         * deletes it is buffering. The reason for this is that the global
         * deletes mark a certain point in time where we took a DWPT out of
         * rotation and freeze the global deletes.
         * 
         * Example: A flush 'A' starts and freezes the global deletes, then
         * flush 'B' starts and freezes all deletes occurred since 'A' has
         * started. if 'B' finishes before 'A' we need to wait until 'A' is done
         * otherwise the deletes frozen by 'B' are not applied to 'A' and we
         * might miss to deletes documents in 'A'.
         */
        try {
          synchronized (ticketQueue) {
            // Each flush is assigned a ticket in the order they acquire the ticketQueue lock
            ticket =  new FlushTicket(flushingDWPT.prepareFlush(), true);
            ticketQueue.incrementAndAdd(ticket);
          }
  
          // flush concurrently without locking
          final FlushedSegment newSegment = flushingDWPT.flush();
          synchronized (ticketQueue) {
            ticket.segment = newSegment;
          }
          // flush was successful once we reached this point - new seg. has been assigned to the ticket!
          success = true;
        } finally {
          if (!success && ticket != null) {
            synchronized (ticketQueue) {
              // In the case of a failure make sure we are making progress and
              // apply all the deletes since the segment flush failed since the flush
              // ticket could hold global deletes see FlushTicket#canPublish()
              ticket.isSegmentFlush = false;
            }
          }
        }
        /*
         * Now we are done and try to flush the ticket queue if the head of the
         * queue has already finished the flush.
         */
        applyFlushTickets();
      } finally {
        flushControl.doAfterFlush(flushingDWPT);
        flushingDWPT.checkAndResetHasAborted();
        indexWriter.flushCount.incrementAndGet();
        indexWriter.doAfterFlush();
      }
     
      flushingDWPT = flushControl.nextPendingFlush();
    }

    // If deletes alone are consuming > 1/2 our RAM
    // buffer, force them all to apply now. This is to
    // prevent too-frequent flushing of a long tail of
    // tiny segments:
    final double ramBufferSizeMB = indexWriter.getConfig().getRAMBufferSizeMB();
    if (ramBufferSizeMB != IndexWriterConfig.DISABLE_AUTO_FLUSH &&
        flushControl.getDeleteBytesUsed() > (1024*1024*ramBufferSizeMB/2)) {
      if (infoStream.isEnabled("DW")) {
        infoStream.message("DW", "force apply deletes bytesUsed=" + flushControl.getDeleteBytesUsed() + " vs ramBuffer=" + (1024*1024*ramBufferSizeMB));
      }
      applyAllDeletes(deleteQueue);
    }

    return maybeMerge;
  }

  private void applyFlushTickets() throws IOException {
    synchronized (ticketQueue) {
      while (true) {
        // Keep publishing eligible flushed segments:
        final FlushTicket head = ticketQueue.peek();
        if (head != null && head.canPublish()) {
          try {
            finishFlush(head.segment, head.frozenDeletes);
          } finally {
            ticketQueue.poll();
          }
        } else {
          break;
        }
      }
    }
  }

  private void finishFlush(FlushedSegment newSegment, FrozenBufferedDeletes bufferedDeletes)
      throws IOException {
    // Finish the flushed segment and publish it to IndexWriter
    if (newSegment == null) {
      assert bufferedDeletes != null;
      if (bufferedDeletes != null && bufferedDeletes.any()) {
        indexWriter.publishFrozenDeletes(bufferedDeletes);
        if (infoStream.isEnabled("DW")) {
          infoStream.message("DW", "flush: push buffered deletes: " + bufferedDeletes);
        }
      }
    } else {
      publishFlushedSegment(newSegment, bufferedDeletes);  
    }
  }

  final void subtractFlushedNumDocs(int numFlushed) {
    int oldValue = numDocsInRAM.get();
    while (!numDocsInRAM.compareAndSet(oldValue, oldValue - numFlushed)) {
      oldValue = numDocsInRAM.get();
    }
  }
  
  /**
   * Publishes the flushed segment, segment private deletes (if any) and its
   * associated global delete (if present) to IndexWriter.  The actual
   * publishing operation is synced on IW -> BDS so that the {@link SegmentInfo}'s
   * delete generation is always GlobalPacket_deleteGeneration + 1
   */
  private void publishFlushedSegment(FlushedSegment newSegment, FrozenBufferedDeletes globalPacket)
      throws IOException {
    assert newSegment != null;
    final SegmentInfo segInfo = indexWriter.prepareFlushedSegment(newSegment);
    final BufferedDeletes deletes = newSegment.segmentDeletes;
    if (infoStream.isEnabled("DW")) {
      infoStream.message("DW", Thread.currentThread().getName() + ": publishFlushedSegment seg-private deletes=" + deletes);  
    }
    FrozenBufferedDeletes packet = null;
    if (deletes != null && deletes.any()) {
      // Segment private delete
      packet = new FrozenBufferedDeletes(deletes, true);
      if (infoStream.isEnabled("DW")) {
        infoStream.message("DW", "flush: push buffered seg private deletes: " + packet);
      }
    }

    // now publish!
    indexWriter.publishFlushedSegment(segInfo, packet, globalPacket);
  }
  
  // for asserts
  private volatile DocumentsWriterDeleteQueue currentFullFlushDelQueue = null;

  // for asserts
  private synchronized boolean setFlushingDeleteQueue(DocumentsWriterDeleteQueue session) {
    currentFullFlushDelQueue = session;
    return true;
  }
  
  /*
   * FlushAllThreads is synced by IW fullFlushLock. Flushing all threads is a
   * two stage operation; the caller must ensure (in try/finally) that finishFlush
   * is called after this method, to release the flush lock in DWFlushControl
   */
  final boolean flushAllThreads()
    throws IOException {
    final DocumentsWriterDeleteQueue flushingDeleteQueue;
    if (infoStream.isEnabled("DW")) {
      infoStream.message("DW", Thread.currentThread().getName() + " startFullFlush");
    }
    
    synchronized (this) {
      pendingChangesInCurrentFullFlush = anyChanges();
      flushingDeleteQueue = deleteQueue;
      /* Cutover to a new delete queue.  This must be synced on the flush control
       * otherwise a new DWPT could sneak into the loop with an already flushing
       * delete queue */
      flushControl.markForFullFlush(); // swaps the delQueue synced on FlushControl
      assert setFlushingDeleteQueue(flushingDeleteQueue);
    }
    assert currentFullFlushDelQueue != null;
    assert currentFullFlushDelQueue != deleteQueue;
    
    boolean anythingFlushed = false;
    try {
      DocumentsWriterPerThread flushingDWPT;
      // Help out with flushing:
      while ((flushingDWPT = flushControl.nextPendingFlush()) != null) {
        anythingFlushed |= doFlush(flushingDWPT);
      }
      // If a concurrent flush is still in flight wait for it
      flushControl.waitForFlush();  
      if (!anythingFlushed && flushingDeleteQueue.anyChanges()) { // apply deletes if we did not flush any document
        if (infoStream.isEnabled("DW")) {
          infoStream.message("DW", Thread.currentThread().getName() + ": flush naked frozen global deletes");
        }
        synchronized (ticketQueue) {
          ticketQueue.incTicketCount(); // first inc the ticket count - freeze opens a window for #anyChanges to fail
          ticketQueue.add(new FlushTicket(flushingDeleteQueue.freezeGlobalBuffer(null), false));
        }
        applyFlushTickets();
      }
      assert !flushingDeleteQueue.anyChanges();
    } finally {
      assert flushingDeleteQueue == currentFullFlushDelQueue;
    }
    return anythingFlushed;
  }
  
  final void finishFullFlush(boolean success) {
    try {
      if (infoStream.isEnabled("DW")) {
        infoStream.message("DW", Thread.currentThread().getName() + " finishFullFlush success=" + success);
      }
      assert setFlushingDeleteQueue(null);
      if (success) {
        // Release the flush lock
        flushControl.finishFullFlush();
      } else {
        flushControl.abortFullFlushes();
      }
    } finally {
      pendingChangesInCurrentFullFlush = false;
    }
    
  }

  static final class FlushTicket {
    final FrozenBufferedDeletes frozenDeletes;
    /* access to non-final members must be synchronized on DW#ticketQueue */
    FlushedSegment segment;
    boolean isSegmentFlush;
    
    FlushTicket(FrozenBufferedDeletes frozenDeletes, boolean isSegmentFlush) {
      this.frozenDeletes = frozenDeletes;
      this.isSegmentFlush = isSegmentFlush;
    }
    
    boolean canPublish() {
      return (!isSegmentFlush || segment != null);  
    }
  }
  
  static final class TicketQueue {
    private final Queue<FlushTicket> queue = new LinkedList<FlushTicket>();
    final AtomicInteger ticketCount = new AtomicInteger();
    
    void incTicketCount() {
      ticketCount.incrementAndGet();
    }
    
    public boolean hasTickets() {
      assert ticketCount.get() >= 0;
      return ticketCount.get() != 0;
    }

    void incrementAndAdd(FlushTicket ticket) {
      incTicketCount();
      add(ticket);
    }
    
    void add(FlushTicket ticket) {
      queue.add(ticket);
    }
    
    FlushTicket peek() {
      return queue.peek();
    }
    
    FlushTicket poll() {
      try {
        return queue.poll();
      } finally {
        ticketCount.decrementAndGet();
      }
    }
    
    void clear() {
      queue.clear();
      ticketCount.set(0);
    }
  }
  
  // use by IW during close to assert all DWPT are inactive after final flush
  boolean assertNoActiveDWPT() {
    Iterator<ThreadState> activePerThreadsIterator = perThreadPool.getAllPerThreadsIterator();
    while(activePerThreadsIterator.hasNext()) {
      ThreadState next = activePerThreadsIterator.next();
      next.lock();
      try {
        assert !next.isActive();
      } finally {
        next.unlock();
      }
    }
    return true;
  }
}
