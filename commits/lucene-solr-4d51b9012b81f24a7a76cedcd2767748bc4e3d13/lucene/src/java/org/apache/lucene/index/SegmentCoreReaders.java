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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.NormsReader;
import org.apache.lucene.codecs.PerDocProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.codecs.TermVectorsReader;
import org.apache.lucene.index.SegmentReader.CoreClosedListener;
import org.apache.lucene.store.CompoundFileDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.IOUtils;

/** Holds core readers that are shared (unchanged) when
 * SegmentReader is cloned or reopened */
final class SegmentCoreReaders {
  
  // Counts how many other reader share the core objects
  // (freqStream, proxStream, tis, etc.) of this reader;
  // when coreRef drops to 0, these core objects may be
  // closed.  A given instance of SegmentReader may be
  // closed, even those it shares core objects with other
  // SegmentReaders:
  private final AtomicInteger ref = new AtomicInteger(1);
  
  final String segment;
  final FieldInfos fieldInfos;
  
  final FieldsProducer fields;
  final PerDocProducer perDocProducer;
  final NormsReader norms;

  final Directory dir;
  final Directory cfsDir;
  final IOContext context;
  final int termsIndexDivisor;
  
  private final SegmentReader owner;
  
  final StoredFieldsReader fieldsReaderOrig;
  final TermVectorsReader termVectorsReaderOrig;
  final CompoundFileDirectory cfsReader;
  final CompoundFileDirectory storeCFSReader;

  private final Set<CoreClosedListener> coreClosedListeners = 
      Collections.synchronizedSet(new LinkedHashSet<CoreClosedListener>());
  
  SegmentCoreReaders(SegmentReader owner, Directory dir, SegmentInfo si, IOContext context, int termsIndexDivisor) throws IOException {
    
    if (termsIndexDivisor == 0) {
      throw new IllegalArgumentException("indexDivisor must be < 0 (don't load terms index) or greater than 0 (got 0)");
    }
    
    segment = si.name;
    final Codec codec = si.getCodec();
    this.context = context;
    this.dir = dir;
    
    boolean success = false;
    
    try {
      Directory dir0 = dir;
      if (si.getUseCompoundFile()) {
        cfsReader = new CompoundFileDirectory(dir, IndexFileNames.segmentFileName(segment, "", IndexFileNames.COMPOUND_FILE_EXTENSION), context, false);
        dir0 = cfsReader;
      } else {
        cfsReader = null;
      }
      cfsDir = dir0;
      si.loadFieldInfos(cfsDir, false); // prevent opening the CFS to load fieldInfos
      fieldInfos = si.getFieldInfos();
      
      this.termsIndexDivisor = termsIndexDivisor;
      final PostingsFormat format = codec.postingsFormat();
      final SegmentReadState segmentReadState = new SegmentReadState(cfsDir, si, fieldInfos, context, termsIndexDivisor);
      // Ask codec for its Fields
      fields = format.fieldsProducer(segmentReadState);
      assert fields != null;
      // ask codec for its Norms: 
      // TODO: since we don't write any norms file if there are no norms,
      // kinda jaky to assume the codec handles the case of no norms file at all gracefully?!
      norms = codec.normsFormat().normsReader(cfsDir, si, fieldInfos, context, dir);
      perDocProducer = codec.docValuesFormat().docsProducer(segmentReadState);

      final Directory storeDir;
      if (si.getDocStoreOffset() != -1) {
        if (si.getDocStoreIsCompoundFile()) {
          storeCFSReader = new CompoundFileDirectory(dir,
              IndexFileNames.segmentFileName(si.getDocStoreSegment(), "", IndexFileNames.COMPOUND_FILE_STORE_EXTENSION),
              context, false);
          storeDir = storeCFSReader;
          assert storeDir != null;
        } else {
          storeCFSReader = null;
          storeDir = dir;
          assert storeDir != null;
        }
      } else if (si.getUseCompoundFile()) {
        storeDir = cfsReader;
        storeCFSReader = null;
        assert storeDir != null;
      } else {
        storeDir = dir;
        storeCFSReader = null;
        assert storeDir != null;
      }
      
      fieldsReaderOrig = si.getCodec().storedFieldsFormat().fieldsReader(storeDir, si, fieldInfos, context);
 
      if (si.getHasVectors()) { // open term vector files only as needed
        termVectorsReaderOrig = si.getCodec().termVectorsFormat().vectorsReader(storeDir, si, fieldInfos, context);
      } else {
        termVectorsReaderOrig = null;
      }

      success = true;
    } finally {
      if (!success) {
        decRef();
      }
    }
    
    // Must assign this at the end -- if we hit an
    // exception above core, we don't want to attempt to
    // purge the FieldCache (will hit NPE because core is
    // not assigned yet).
    this.owner = owner;
  }
  
  TermVectorsReader getTermVectorsReaderOrig() {
    return termVectorsReaderOrig;
  }
  
  StoredFieldsReader getFieldsReaderOrig() {
    return fieldsReaderOrig;
  }
  
  void incRef() {
    ref.incrementAndGet();
  }
  
  Directory getCFSReader() {
    return cfsReader;
  }
  
  void decRef() throws IOException {
    //System.out.println("core.decRef seg=" + owner.getSegmentInfo() + " rc=" + ref);
    if (ref.decrementAndGet() == 0) {
      IOUtils.close(fields, perDocProducer, termVectorsReaderOrig,
          fieldsReaderOrig, cfsReader, storeCFSReader, norms);
      notifyCoreClosedListeners();
    }
  }
  
  private final void notifyCoreClosedListeners() {
    synchronized(coreClosedListeners) {
      for (CoreClosedListener listener : coreClosedListeners) {
        listener.onClose(owner);
      }
    }
  }

  void addCoreClosedListener(CoreClosedListener listener) {
    coreClosedListeners.add(listener);
  }
  
  void removeCoreClosedListener(CoreClosedListener listener) {
    coreClosedListeners.remove(listener);
  }

  @Override
  public String toString() {
    return "SegmentCoreReader(owner=" + owner + ")";
  }
}
