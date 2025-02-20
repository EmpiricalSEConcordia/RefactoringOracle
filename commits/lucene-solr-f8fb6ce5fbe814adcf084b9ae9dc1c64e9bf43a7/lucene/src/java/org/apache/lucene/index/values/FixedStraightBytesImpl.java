package org.apache.lucene.index.values;

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

import org.apache.lucene.index.values.Bytes.BytesBaseSource;
import org.apache.lucene.index.values.Bytes.BytesReaderBase;
import org.apache.lucene.index.values.Bytes.BytesWriterBase;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.RamUsageEstimator;

// Simplest storage: stores fixed length byte[] per
// document, with no dedup and no sorting.

class FixedStraightBytesImpl {

  static final String CODEC_NAME = "FixedStraightBytes";
  static final int VERSION_START = 0;
  static final int VERSION_CURRENT = VERSION_START;

  static class Writer extends BytesWriterBase {
    private int size = -1;
    // start at -1 if the first added value is > 0
    private int lastDocID = -1;
    private byte[] oneRecord;

    protected Writer(Directory dir, String id) throws IOException {
      super(dir, id, CODEC_NAME, VERSION_CURRENT, false, false, null, null);
    }
    
    // nocommit - impl bulk copy here!

    @Override
    synchronized public void add(int docID, BytesRef bytes) throws IOException {
      if (size == -1) {
        size = bytes.length;
        initDataOut();
        datOut.writeInt(size);
        oneRecord = new byte[size];
      } else if (bytes.length != size) {
        throw new IllegalArgumentException("expected bytes size=" + size + " but got " + bytes.length);
      }
      fill(docID);
      assert bytes.bytes.length >= bytes.length;
      datOut.writeBytes(bytes.bytes, bytes.offset, bytes.length);
    }

    /* (non-Javadoc)
     * @see org.apache.lucene.index.values.Writer#merge(org.apache.lucene.index.values.Writer.MergeState)
     */
    @Override
    protected void merge(MergeState state) throws IOException {
      if(state.bits == null && state.reader instanceof Reader){
        Reader reader = (Reader) state.reader;
        final int maxDocs = reader.maxDoc;
        if(maxDocs == 0)
          return;
        if(size == -1) {
          size = reader.size;
          initDataOut();
          datOut.writeInt(size);
          oneRecord = new byte[size];
        }
       fill(state.docBase);
       // nocommit should we add a transfer to API to each reader?
       datOut.copyBytes(reader.cloneData(), size * maxDocs);
       lastDocID += maxDocs-1;
      } else
        super.merge(state);
    }

    // Fills up to but not including this docID
    private void fill(int docID) throws IOException {
      assert size >= 0;
      for(int i=lastDocID+1;i<docID;i++) {
        datOut.writeBytes(oneRecord, size);
      }
      lastDocID = docID;
    }

    @Override
    synchronized public void finish(int docCount) throws IOException {
      if(datOut == null) // no data added
        return;
      fill(docCount);
      super.finish(docCount);
    }

    public long ramBytesUsed() {
      return 0;
    }
    
  }

  public static class Reader extends BytesReaderBase {
    private final int size;
    private final int maxDoc;

    Reader(Directory dir, String id, int maxDoc)
      throws IOException {
      super(dir, id, CODEC_NAME, VERSION_START, false);
      size = datIn.readInt();
      this.maxDoc = maxDoc;
    }

    @Override
    public Source load() throws IOException {
      return new Source(cloneData(), cloneIndex(), size, maxDoc);
    }

    @Override
    public void close() throws IOException {
      datIn.close();
    }

    private static class Source extends BytesBaseSource {
      // TODO: paged data
      private final byte[] data;
      private final BytesRef bytesRef = new BytesRef();
      private final int size;

      public Source(IndexInput datIn, IndexInput idxIn, int size, int maxDoc) throws IOException {
        super(datIn, idxIn);
        this.size = size;
        final int sizeInBytes = size*maxDoc;
        data = new byte[sizeInBytes];
        assert data.length <= datIn.length() : " file size is less than the expected size diff: " + (data.length - datIn.length()) + " size: " + size + " maxDoc " + maxDoc + " pos: " + datIn.getFilePointer();
        datIn.readBytes(data, 0, sizeInBytes);
        bytesRef.bytes = data;
        bytesRef.length = size;
      }

      @Override
      public BytesRef bytes(int docID) {
        bytesRef.offset = docID * size;
        return bytesRef;
      }

      public long ramBytesUsed() {
        return RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + data.length;
      }

      @Override
      public int getValueCount() {
        throw new UnsupportedOperationException();
      }
    }

    @Override
    public ValuesEnum getEnum(AttributeSource source) throws IOException {
      return new FixedStraightBytesEnum(source, cloneData(), size, maxDoc);
    }
    
    private static final class FixedStraightBytesEnum extends ValuesEnum {
      private final IndexInput datIn;
      private final int size;
      private final int maxDoc;
      private int pos = -1;
      private final long fp;
      private final BytesRef ref;

      public FixedStraightBytesEnum(AttributeSource source, IndexInput datIn, int size, int maxDoc) throws IOException{
        super(source, Values.BYTES_FIXED_STRAIGHT);
        this.datIn = datIn;
        this.size = size;
        this.maxDoc = maxDoc;
        ref = attr.bytes();
        ref.grow(size);
        ref.length = size;
        ref.offset = 0;
        fp = datIn.getFilePointer();
      }
     
      public void close() throws IOException {
        datIn.close();
      }
  
      @Override
      public int advance(int target) throws IOException {
        if(target >= maxDoc){
          ref.length = 0;
          ref.offset = 0;
          return pos = NO_MORE_DOCS;
        }
        if((target-1) != pos) // pos inc == 1
          datIn.seek(fp + target * size);
        datIn.readBytes(ref.bytes, 0, size);
        return pos = target;
      }
      
      @Override
      public int docID() {
        return pos;
      }
      
      @Override
      public int nextDoc() throws IOException {
        return advance(pos+1);
      }
    }
  }
}
