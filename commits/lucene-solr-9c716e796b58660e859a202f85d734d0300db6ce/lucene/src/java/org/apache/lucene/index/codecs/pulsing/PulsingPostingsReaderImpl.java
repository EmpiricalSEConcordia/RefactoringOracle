package org.apache.lucene.index.codecs.pulsing;

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

import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.codecs.TermState;
import org.apache.lucene.index.codecs.PostingsReaderBase;
import org.apache.lucene.index.codecs.pulsing.PulsingPostingsWriterImpl.Document;
import org.apache.lucene.index.codecs.pulsing.PulsingPostingsWriterImpl.Position;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CodecUtil;

/** Concrete class that reads the current doc/freq/skip
 *  postings format 
 *  @lucene.experimental */

// TODO: -- should we switch "hasProx" higher up?  and
// create two separate docs readers, one that also reads
// prox and one that doesn't?

public class PulsingPostingsReaderImpl extends PostingsReaderBase {

  // Fallback reader for non-pulsed terms:
  final PostingsReaderBase wrappedPostingsReader;
  int maxPulsingDocFreq;

  public PulsingPostingsReaderImpl(PostingsReaderBase wrappedPostingsReader) throws IOException {
    this.wrappedPostingsReader = wrappedPostingsReader;
  }

  @Override
  public void init(IndexInput termsIn) throws IOException {
    CodecUtil.checkHeader(termsIn, PulsingPostingsWriterImpl.CODEC,
      PulsingPostingsWriterImpl.VERSION_START, PulsingPostingsWriterImpl.VERSION_START);
    maxPulsingDocFreq = termsIn.readVInt();
    wrappedPostingsReader.init(termsIn);
  }

  private static class PulsingTermState extends TermState {
    private Document docs[];
    private TermState wrappedTermState;
    private boolean pendingIndexTerm;

    public Object clone() {
      PulsingTermState clone;
      clone = (PulsingTermState) super.clone();
      clone.docs = (Document[]) docs.clone();
      for(int i=0;i<clone.docs.length;i++) {
        final Document doc = clone.docs[i];
        if (doc != null) {
          clone.docs[i] = (Document) doc.clone();
        }
      }
      clone.wrappedTermState = (TermState) wrappedTermState.clone();
      return clone;
    }

    public void copy(TermState _other) {
      super.copy(_other);
      PulsingTermState other = (PulsingTermState) _other;
      pendingIndexTerm = other.pendingIndexTerm;
      wrappedTermState.copy(other.wrappedTermState);
      for(int i=0;i<docs.length;i++) {
        if (other.docs[i] != null) {
          docs[i] = (Document) other.docs[i].clone();
        }
      }
    }
  }

  @Override
  public TermState newTermState() throws IOException {
    PulsingTermState state = new PulsingTermState();
    state.wrappedTermState = wrappedPostingsReader.newTermState();
    state.docs = new Document[maxPulsingDocFreq];
    return state;
  }

  @Override
  public void readTerm(IndexInput termsIn, FieldInfo fieldInfo, TermState _termState, boolean isIndexTerm) throws IOException {

    PulsingTermState termState = (PulsingTermState) _termState;

    termState.pendingIndexTerm |= isIndexTerm;

    if (termState.docFreq <= maxPulsingDocFreq) {

      // Inlined into terms dict -- read everything in

      // TODO: maybe only read everything in lazily?  But
      // then we'd need to store length so we could seek
      // over it when docs/pos enum was not requested

      // TODO: it'd be better to share this encoding logic
      // in some inner codec that knows how to write a
      // single doc / single position, etc.  This way if a
      // given codec wants to store other interesting
      // stuff, it could use this pulsing codec to do so

      int docID = 0;
      for(int i=0;i<termState.docFreq;i++) {
        Document doc = termState.docs[i];
        if (doc == null) {
          doc = termState.docs[i] = new Document();
        }
        final int code = termsIn.readVInt();
        if (fieldInfo.omitTermFreqAndPositions) {
          docID += code;
          doc.numPositions = 1;
        } else {
          docID += code>>>1;
          if ((code & 1) != 0) {
            doc.numPositions = 1;
          } else {
            doc.numPositions = termsIn.readVInt();
          }
            
          if (doc.numPositions > doc.positions.length) {
            doc.reallocPositions(doc.numPositions);
          }

          int position = 0;
          int payloadLength = -1;

          for(int j=0;j<doc.numPositions;j++) {
            final Position pos = doc.positions[j];
            final int code2 = termsIn.readVInt();
            if (fieldInfo.storePayloads) {
              position += code2 >>> 1;
              if ((code2 & 1) != 0) {
                payloadLength = termsIn.readVInt();
              }

              if (payloadLength > 0) {
                if (pos.payload == null) {
                  pos.payload = new BytesRef();
                  pos.payload.bytes = new byte[payloadLength];
                } else if (payloadLength > pos.payload.bytes.length) {
                  pos.payload.grow(payloadLength);
                }
                pos.payload.length = payloadLength;
                termsIn.readBytes(pos.payload.bytes, 0, payloadLength);
              } else if (pos.payload != null) {
                pos.payload.length = 0;
              }
            } else {
              position += code2;
            }
            pos.pos = position;
          }
        }
        doc.docID = docID;
      }
    } else {
      termState.wrappedTermState.docFreq = termState.docFreq;
      wrappedPostingsReader.readTerm(termsIn, fieldInfo, termState.wrappedTermState, termState.pendingIndexTerm);
      termState.pendingIndexTerm = false;
    }
  }

  // TODO: we could actually reuse, by having TL that
  // holds the last wrapped reuse, and vice-versa
  @Override
  public DocsEnum docs(FieldInfo field, TermState _termState, Bits skipDocs, DocsEnum reuse) throws IOException {
    PulsingTermState termState = (PulsingTermState) _termState;
    if (termState.docFreq <= maxPulsingDocFreq) {
      if (reuse instanceof PulsingDocsEnum) {
        return ((PulsingDocsEnum) reuse).reset(skipDocs, termState);
      } else {
        PulsingDocsEnum docsEnum = new PulsingDocsEnum();
        return docsEnum.reset(skipDocs, termState);
      }
    } else {
      if (reuse instanceof PulsingDocsEnum) {
        return wrappedPostingsReader.docs(field, termState.wrappedTermState, skipDocs, null);
      } else {
        return wrappedPostingsReader.docs(field, termState.wrappedTermState, skipDocs, reuse);
      }
    }
  }

  // TODO: -- not great that we can't always reuse
  @Override
  public DocsAndPositionsEnum docsAndPositions(FieldInfo field, TermState _termState, Bits skipDocs, DocsAndPositionsEnum reuse) throws IOException {
    PulsingTermState termState = (PulsingTermState) _termState;
    if (termState.docFreq <= maxPulsingDocFreq) {
      if (reuse instanceof PulsingDocsAndPositionsEnum) {
        return ((PulsingDocsAndPositionsEnum) reuse).reset(skipDocs, termState);
      } else {
        PulsingDocsAndPositionsEnum postingsEnum = new PulsingDocsAndPositionsEnum();
        return postingsEnum.reset(skipDocs, termState);
      }
    } else {
      if (reuse instanceof PulsingDocsAndPositionsEnum) {
        return wrappedPostingsReader.docsAndPositions(field, termState.wrappedTermState, skipDocs, null);
      } else {
        return wrappedPostingsReader.docsAndPositions(field, termState.wrappedTermState, skipDocs, reuse);
      }
    }
  }

  static class PulsingDocsEnum extends DocsEnum {
    private int nextRead;
    private Bits skipDocs;
    private Document doc;
    private PulsingTermState state;

    public void close() {}

    PulsingDocsEnum reset(Bits skipDocs, PulsingTermState termState) {
      // TODO: -- not great we have to clone here --
      // merging is wasteful; TermRangeQuery too
      state = (PulsingTermState) termState.clone();
      this.skipDocs = skipDocs;
      nextRead = 0;
      return this;
    }

    @Override
    public int nextDoc() {
      while(true) {
        if (nextRead >= state.docFreq) {
          return NO_MORE_DOCS;
        } else {
          doc = state.docs[nextRead++];
          if (skipDocs == null || !skipDocs.get(doc.docID)) {
            return doc.docID;
          }
        }
      }
    }

    @Override
    public int read() {
      int i=0;
      // TODO: -- ob1?
      initBulkResult();
      final int[] docs = bulkResult.docs.ints;
      final int[] freqs = bulkResult.freqs.ints;
      while(nextRead < state.docFreq) {
        doc = state.docs[nextRead++];
        if (skipDocs == null || !skipDocs.get(doc.docID)) {
          docs[i] = doc.docID;
          freqs[i] = doc.numPositions;
          i++;
        }
      }
      return i;
    }

    @Override
    public int freq() {
      return doc.numPositions;
    }

    @Override
    public int docID() {
      return doc.docID;
    }

    @Override
    public int advance(int target) throws IOException {
      int doc;
      while((doc=nextDoc()) != NO_MORE_DOCS) {
        if (doc >= target)
          return doc;
      }
      return NO_MORE_DOCS;
    }
  }

  static class PulsingDocsAndPositionsEnum extends DocsAndPositionsEnum {
    private int nextRead;
    private int nextPosRead;
    private Bits skipDocs;
    private Document doc;
    private Position pos;
    private PulsingTermState state;

    // Only here to emulate limitation of standard codec,
    // which only allows retrieving payload more than once
    private boolean payloadRetrieved;

    public void close() {}

    PulsingDocsAndPositionsEnum reset(Bits skipDocs, PulsingTermState termState) {
      // TODO: -- not great we have to clone here --
      // merging is wasteful; TermRangeQuery too
      state = (PulsingTermState) termState.clone();
      this.skipDocs = skipDocs;
      nextRead = 0;
      nextPosRead = 0;
      return this;
    }

    @Override
    public int nextDoc() {
      while(true) {
        if (nextRead >= state.docFreq) {
          return NO_MORE_DOCS;
        } else {
          doc = state.docs[nextRead++];
          if (skipDocs == null || !skipDocs.get(doc.docID)) {
            nextPosRead = 0;
            return doc.docID;
          }
        }
      }
    }

    @Override
    public int freq() {
      return doc.numPositions;
    }

    @Override
    public int docID() {
      return doc.docID;
    }

    @Override
    public int advance(int target) throws IOException {
      int doc;
      while((doc=nextDoc()) != NO_MORE_DOCS) {
        if (doc >= target) {
          return doc;
        }
      }
      return NO_MORE_DOCS;
    }

    @Override
    public int nextPosition() {
      assert nextPosRead < doc.numPositions;
      pos = doc.positions[nextPosRead++];
      payloadRetrieved = false;
      return pos.pos;
    }

    @Override
    public boolean hasPayload() {
      return !payloadRetrieved && pos.payload != null && pos.payload.length > 0;
    }

    @Override
    public BytesRef getPayload() {
      payloadRetrieved = true;
      return pos.payload;
    }
  }

  @Override
  public void close() throws IOException {
    wrappedPostingsReader.close();
  }
}
