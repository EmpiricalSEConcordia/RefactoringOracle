package org.apache.lucene.index;

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
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.lucene.codecs.DocValuesConsumer;
import org.apache.lucene.util.ByteBlockPool.DirectTrackingAllocator;
import org.apache.lucene.util.ByteBlockPool;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Counter;
import org.apache.lucene.util.OpenBitSet;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.packed.AppendingDeltaPackedLongBuffer;
import org.apache.lucene.util.packed.PackedInts;

import static org.apache.lucene.util.ByteBlockPool.BYTE_BLOCK_SIZE;


/** Buffers up pending byte[] per doc, then flushes when
 *  segment flushes. */
class BinaryDocValuesWriter extends DocValuesWriter {

  private final ByteBlockPool pool;
  private final AppendingDeltaPackedLongBuffer lengths;
  private final OpenBitSet docsWithField;
  private final Counter iwBytesUsed;
  private long bytesUsed;
  private final FieldInfo fieldInfo;
  private int addedValues = 0;

  public BinaryDocValuesWriter(FieldInfo fieldInfo, Counter iwBytesUsed) {
    this.fieldInfo = fieldInfo;
    this.pool = new ByteBlockPool(new DirectTrackingAllocator(iwBytesUsed));
    this.lengths = new AppendingDeltaPackedLongBuffer(PackedInts.COMPACT);
    this.iwBytesUsed = iwBytesUsed;
    this.docsWithField = new OpenBitSet();
    this.bytesUsed = docsWithFieldBytesUsed();
    iwBytesUsed.addAndGet(bytesUsed);
  }

  public void addValue(int docID, BytesRef value) {
    if (docID < addedValues) {
      throw new IllegalArgumentException("DocValuesField \"" + fieldInfo.name + "\" appears more than once in this document (only one value is allowed per field)");
    }
    if (value == null) {
      throw new IllegalArgumentException("field=\"" + fieldInfo.name + "\": null value not allowed");
    }
    if (value.length > (BYTE_BLOCK_SIZE - 2)) {
      throw new IllegalArgumentException("DocValuesField \"" + fieldInfo.name + "\" is too large, must be <= " + (BYTE_BLOCK_SIZE - 2));
    }
    
    // Fill in any holes:
    while(addedValues < docID) {
      addedValues++;
      lengths.add(0);
    }
    addedValues++;
    lengths.add(value.length);
    pool.append(value);
    docsWithField.set(docID);
    updateBytesUsed();
  }
  
  private long docsWithFieldBytesUsed() {
    // nocommit: this is not correct
    return docsWithField.getBits().length*RamUsageEstimator.NUM_BYTES_LONG;
  }
  
  private void updateBytesUsed() {
    final long newBytesUsed = docsWithFieldBytesUsed();
    iwBytesUsed.addAndGet(newBytesUsed - bytesUsed);
    bytesUsed = newBytesUsed;
  }

  @Override
  public void finish(int maxDoc) {
  }

  @Override
  public void flush(SegmentWriteState state, DocValuesConsumer dvConsumer) throws IOException {
    final int maxDoc = state.segmentInfo.getDocCount();
    dvConsumer.addBinaryField(fieldInfo,
                              new Iterable<BytesRef>() {
                                @Override
                                public Iterator<BytesRef> iterator() {
                                   return new BytesIterator(maxDoc);                                 
                                }
                              });
  }

  @Override
  public void abort() {
  }
  
  // iterates over the values we have in ram
  private class BytesIterator implements Iterator<BytesRef> {
    final BytesRef value = new BytesRef();
    final AppendingDeltaPackedLongBuffer.Iterator lengthsIterator = lengths.iterator();
    final int size = (int) lengths.size();
    final int maxDoc;
    int upto;
    long byteOffset;
    
    BytesIterator(int maxDoc) {
      this.maxDoc = maxDoc;
    }
    
    @Override
    public boolean hasNext() {
      return upto < maxDoc;
    }

    @Override
    public BytesRef next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      final BytesRef v;
      if (upto < size) {
        int length = (int) lengthsIterator.next();
        value.grow(length);
        value.length = length;
        pool.readBytes(byteOffset, value.bytes, value.offset, value.length);
        byteOffset += length;
        if (docsWithField.get(upto)) {
          v = value;
        } else {
          v = null;
        }
      } else {
        v = null;
      }
      upto++;
      return v;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
