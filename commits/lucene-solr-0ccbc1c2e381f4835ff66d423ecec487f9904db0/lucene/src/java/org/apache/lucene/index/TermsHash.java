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
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.index.DocumentsWriterPerThread.DocWriter;
import org.apache.lucene.util.BytesRef;

/** This class implements {@link InvertedDocConsumer}, which
 *  is passed each token produced by the analyzer on each
 *  field.  It stores these tokens in a hash table, and
 *  allocates separate byte streams per token.  Consumers of
 *  this class, eg {@link FreqProxTermsWriter} and {@link
 *  TermVectorsTermsWriter}, write their own byte streams
 *  under each term.
 */
final class TermsHash extends InvertedDocConsumer {

  final TermsHashConsumer consumer;
  final TermsHash nextTermsHash;
  final DocumentsWriterPerThread docWriter;
  
  final IntBlockPool intPool;
  final ByteBlockPool bytePool;
  ByteBlockPool termBytePool;

  final boolean primary;
  final DocumentsWriterPerThread.DocState docState;

  // Used when comparing postings via termRefComp, in TermsHashPerField
  final BytesRef tr1 = new BytesRef();
  final BytesRef tr2 = new BytesRef();

  // Used by perField:
  final BytesRef utf8 = new BytesRef(10);
  
  boolean trackAllocations;

  
  public TermsHash(final DocumentsWriterPerThread docWriter, final TermsHashConsumer consumer, final TermsHash nextTermsHash) {
    this.docState = docWriter.docState;
    this.docWriter = docWriter;
    this.consumer = consumer;
    this.nextTermsHash = nextTermsHash;    
    intPool = new IntBlockPool(docWriter);
    bytePool = new ByteBlockPool(docWriter.ramAllocator.byteBlockAllocator);
    
    if (nextTermsHash != null) {
      // We are primary
      primary = true;
      termBytePool = bytePool;
      nextTermsHash.termBytePool = bytePool;
    } else {
      primary = false;
    }

  }

  @Override
  void setFieldInfos(FieldInfos fieldInfos) {
    this.fieldInfos = fieldInfos;
    consumer.setFieldInfos(fieldInfos);
  }

  @Override
  public void abort() {
    reset();
    consumer.abort();
    if (nextTermsHash != null) {
      nextTermsHash.abort();
    }
  }
  
  // Clear all state
  void reset() {
    intPool.reset();
    bytePool.reset();

    if (primary) {
      bytePool.reset();
    }
  }


  @Override
  void closeDocStore(SegmentWriteState state) throws IOException {
    consumer.closeDocStore(state);
    if (nextTermsHash != null)
      nextTermsHash.closeDocStore(state);
  }

  @Override
  void flush(Map<FieldInfo,InvertedDocConsumerPerField> fieldsToFlush, final SegmentWriteState state) throws IOException {
    Map<FieldInfo,TermsHashConsumerPerField> childFields = new HashMap<FieldInfo,TermsHashConsumerPerField>();
    Map<FieldInfo,InvertedDocConsumerPerField> nextChildFields;

    if (nextTermsHash != null) {
      nextChildFields = new HashMap<FieldInfo,InvertedDocConsumerPerField>();
    } else {
      nextChildFields = null;
    }

    for (final Map.Entry<FieldInfo,InvertedDocConsumerPerField> entry : fieldsToFlush.entrySet()) {
        TermsHashPerField perField = (TermsHashPerField) entry.getValue();
        childFields.put(entry.getKey(), perField.consumer);
        if (nextTermsHash != null) {
          nextChildFields.put(entry.getKey(), perField.nextPerField);
        }
    }
    
    consumer.flush(childFields, state);

    if (nextTermsHash != null) {
      nextTermsHash.flush(nextChildFields, state);
    }
  }
  
  @Override
  InvertedDocConsumerPerField addField(DocInverterPerField docInverterPerField, final FieldInfo fieldInfo) {
    return new TermsHashPerField(docInverterPerField, this, nextTermsHash, fieldInfo);
  }

  @Override
  public boolean freeRAM() {
    return false;
  }

  @Override
  DocWriter finishDocument() throws IOException {
    final DocumentsWriterPerThread.DocWriter doc = consumer.finishDocument();

    final DocumentsWriterPerThread.DocWriter doc2;
    if (nextTermsHash != null) {
      doc2 = nextTermsHash.consumer.finishDocument();
    } else {
      doc2 = null;
    }
    if (doc == null) {
      return doc2;
    } else {
      doc.setNext(doc2);
      return doc;
    }
  }

  @Override
  void startDocument() throws IOException {
    consumer.startDocument();
    if (nextTermsHash != null) {
      nextTermsHash.consumer.startDocument();
    }
  }
}
