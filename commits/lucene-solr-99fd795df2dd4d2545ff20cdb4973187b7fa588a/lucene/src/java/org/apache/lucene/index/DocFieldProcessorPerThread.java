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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.values.ValuesAttribute;
import org.apache.lucene.index.values.codec.DocValuesConsumer;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.RamUsageEstimator;

/**
 * Gathers all Fieldables for a document under the same
 * name, updates FieldInfos, and calls per-field consumers
 * to process field by field.
 *
 * Currently, only a single thread visits the fields,
 * sequentially, for processing.
 */

final class DocFieldProcessorPerThread extends DocConsumerPerThread {

  float docBoost;
  int fieldGen;
  final DocFieldProcessor docFieldProcessor;
  final FieldInfos fieldInfos;
  final DocFieldConsumerPerThread consumer;

  // Holds all fields seen in current doc
  DocFieldProcessorPerField[] fields = new DocFieldProcessorPerField[1];
  int fieldCount;

  // Hash table for all fields ever seen
  DocFieldProcessorPerField[] fieldHash = new DocFieldProcessorPerField[2];
  int hashMask = 1;
  int totalFieldCount;

  final StoredFieldsWriterPerThread fieldsWriter;

  final DocumentsWriter.DocState docState;

  public DocFieldProcessorPerThread(DocumentsWriterThreadState threadState, DocFieldProcessor docFieldProcessor) throws IOException {
    this.docState = threadState.docState;
    this.docFieldProcessor = docFieldProcessor;
    this.fieldInfos = docFieldProcessor.fieldInfos;
    this.consumer = docFieldProcessor.consumer.addThread(this);
    fieldsWriter = docFieldProcessor.fieldsWriter.addThread(docState);
  }

  @Override
  public void abort() {
    for(int i=0;i<fieldHash.length;i++) {
      DocFieldProcessorPerField field = fieldHash[i];
      while(field != null) {
        final DocFieldProcessorPerField next = field.next;
        field.abort();
        field = next;
      }
    }
    fieldsWriter.abort();
    consumer.abort();
  }

  public Collection<DocFieldConsumerPerField> fields() {
    Collection<DocFieldConsumerPerField> fields = new HashSet<DocFieldConsumerPerField>();
    for(int i=0;i<fieldHash.length;i++) {
      DocFieldProcessorPerField field = fieldHash[i];
      while(field != null) {
        fields.add(field.consumer);
        field = field.next;
      }
    }
    assert fields.size() == totalFieldCount;
    return fields;
  }

  /** If there are fields we've seen but did not see again
   *  in the last run, then free them up. */

  void trimFields(SegmentWriteState state) {

    for(int i=0;i<fieldHash.length;i++) {
      DocFieldProcessorPerField perField = fieldHash[i];
      DocFieldProcessorPerField lastPerField = null;

      while (perField != null) {

        if (perField.lastGen == -1) {

          // This field was not seen since the previous
          // flush, so, free up its resources now

          // Unhash
          if (lastPerField == null)
            fieldHash[i] = perField.next;
          else
            lastPerField.next = perField.next;

          if (state.infoStream != null) {
            state.infoStream.println("  purge field=" + perField.fieldInfo.name);
          }

          totalFieldCount--;

        } else {
          // Reset
          perField.lastGen = -1;
          lastPerField = perField;
        }

        perField = perField.next;
      }
    }
  }

  private void rehash() {
    final int newHashSize = (fieldHash.length*2);
    assert newHashSize > fieldHash.length;

    final DocFieldProcessorPerField newHashArray[] = new DocFieldProcessorPerField[newHashSize];

    // Rehash
    int newHashMask = newHashSize-1;
    for(int j=0;j<fieldHash.length;j++) {
      DocFieldProcessorPerField fp0 = fieldHash[j];
      while(fp0 != null) {
        final int hashPos2 = fp0.fieldInfo.name.hashCode() & newHashMask;
        DocFieldProcessorPerField nextFP0 = fp0.next;
        fp0.next = newHashArray[hashPos2];
        newHashArray[hashPos2] = fp0;
        fp0 = nextFP0;
      }
    }

    fieldHash = newHashArray;
    hashMask = newHashMask;
  }

  @Override
  public DocumentsWriter.DocWriter processDocument() throws IOException {

    consumer.startDocument();
    fieldsWriter.startDocument();

    final Document doc = docState.doc;

    assert docFieldProcessor.docWriter.writer.testPoint("DocumentsWriter.ThreadState.init start");

    fieldCount = 0;
    
    final int thisFieldGen = fieldGen++;

    final List<Fieldable> docFields = doc.getFields();
    final int numDocFields = docFields.size();

    // Absorb any new fields first seen in this document.
    // Also absorb any changes to fields we had already
    // seen before (eg suddenly turning on norms or
    // vectors, etc.):

    for(int i=0;i<numDocFields;i++) {
      Fieldable field = docFields.get(i);
      final String fieldName = field.name();

      // Make sure we have a PerField allocated
      final int hashPos = fieldName.hashCode() & hashMask;
      DocFieldProcessorPerField fp = fieldHash[hashPos];
      while(fp != null && !fp.fieldInfo.name.equals(fieldName))
        fp = fp.next;

      if (fp == null) {

        // TODO FI: we need to genericize the "flags" that a
        // field holds, and, how these flags are merged; it
        // needs to be more "pluggable" such that if I want
        // to have a new "thing" my Fields can do, I can
        // easily add it
        FieldInfo fi = fieldInfos.add(fieldName, field.isIndexed(), field.isTermVectorStored(),
                                      field.isStorePositionWithTermVector(), field.isStoreOffsetWithTermVector(),
                                      field.getOmitNorms(), false, field.getOmitTermFreqAndPositions());

        fp = new DocFieldProcessorPerField(this, fi);
        fp.next = fieldHash[hashPos];
        fieldHash[hashPos] = fp;
        totalFieldCount++;

        if (totalFieldCount >= fieldHash.length/2)
          rehash();
      } else
        fp.fieldInfo.update(field.isIndexed(), field.isTermVectorStored(),
                            field.isStorePositionWithTermVector(), field.isStoreOffsetWithTermVector(),
                            field.getOmitNorms(), false, field.getOmitTermFreqAndPositions());

      if (thisFieldGen != fp.lastGen) {

        // First time we're seeing this field for this doc
        fp.fieldCount = 0;

        if (fieldCount == fields.length) {
          final int newSize = fields.length*2;
          DocFieldProcessorPerField newArray[] = new DocFieldProcessorPerField[newSize];
          System.arraycopy(fields, 0, newArray, 0, fieldCount);
          fields = newArray;
        }

        fields[fieldCount++] = fp;
        fp.lastGen = thisFieldGen;
      }

      if (fp.fieldCount == fp.fields.length) {
        Fieldable[] newArray = new Fieldable[fp.fields.length*2];
        System.arraycopy(fp.fields, 0, newArray, 0, fp.fieldCount);
        fp.fields = newArray;
      }

      fp.fields[fp.fieldCount++] = field;
      if (field.isStored()) {
        fieldsWriter.addField(field, fp.fieldInfo);
      }
    }

    // If we are writing vectors then we must visit
    // fields in sorted order so they are written in
    // sorted order.  TODO: we actually only need to
    // sort the subset of fields that have vectors
    // enabled; we could save [small amount of] CPU
    // here.
    quickSort(fields, 0, fieldCount-1);
   

    for(int i=0;i<fieldCount;i++) {
      final DocFieldProcessorPerField perField = fields[i];
      final Fieldable fieldable = perField.fields[0];
      perField.consumer.processFields(perField.fields, perField.fieldCount);
     
      if(!fieldable.hasFieldAttribute())
        continue;
      final AttributeSource attrSource = fieldable.getFieldAttributes();
      if(!attrSource.hasAttribute(ValuesAttribute.class))
        continue;
      final ValuesAttribute attribute = attrSource.getAttribute(ValuesAttribute.class);
      final DocValuesConsumer consumer = docFieldProcessor.docValuesConsumer(docState.docWriter.directory,
              docState.docWriter.segment, fieldable.name(), attribute, perField.fieldInfo);
      consumer.add(docState.docID, attribute);
    }
    if (docState.maxTermPrefix != null && docState.infoStream != null) {
      docState.infoStream.println("WARNING: document contains at least one immense term (whose UTF8 encoding is longer than the max length " + DocumentsWriter.MAX_TERM_LENGTH_UTF8 + "), all of which were skipped.  Please correct the analyzer to not produce such terms.  The prefix of the first immense term is: '" + docState.maxTermPrefix + "...'"); 
      docState.maxTermPrefix = null;
    }

    final DocumentsWriter.DocWriter one = fieldsWriter.finishDocument();
    final DocumentsWriter.DocWriter two = consumer.finishDocument();
    if (one == null) {
      return two;
    } else if (two == null) {
      return one;
    } else {
      PerDoc both = getPerDoc();
      both.docID = docState.docID;
      assert one.docID == docState.docID;
      assert two.docID == docState.docID;
      both.one = one;
      both.two = two;
      return both;
    }
  }

  void quickSort(DocFieldProcessorPerField[] array, int lo, int hi) {
    if (lo >= hi)
      return;
    else if (hi == 1+lo) {
      if (array[lo].fieldInfo.name.compareTo(array[hi].fieldInfo.name) > 0) {
        final DocFieldProcessorPerField tmp = array[lo];
        array[lo] = array[hi];
        array[hi] = tmp;
      }
      return;
    }

    int mid = (lo + hi) >>> 1;

    if (array[lo].fieldInfo.name.compareTo(array[mid].fieldInfo.name) > 0) {
      DocFieldProcessorPerField tmp = array[lo];
      array[lo] = array[mid];
      array[mid] = tmp;
    }

    if (array[mid].fieldInfo.name.compareTo(array[hi].fieldInfo.name) > 0) {
      DocFieldProcessorPerField tmp = array[mid];
      array[mid] = array[hi];
      array[hi] = tmp;

      if (array[lo].fieldInfo.name.compareTo(array[mid].fieldInfo.name) > 0) {
        DocFieldProcessorPerField tmp2 = array[lo];
        array[lo] = array[mid];
        array[mid] = tmp2;
      }
    }

    int left = lo + 1;
    int right = hi - 1;

    if (left >= right)
      return;

    DocFieldProcessorPerField partition = array[mid];

    for (; ;) {
      while (array[right].fieldInfo.name.compareTo(partition.fieldInfo.name) > 0)
        --right;

      while (left < right && array[left].fieldInfo.name.compareTo(partition.fieldInfo.name) <= 0)
        ++left;

      if (left < right) {
        DocFieldProcessorPerField tmp = array[left];
        array[left] = array[right];
        array[right] = tmp;
        --right;
      } else {
        break;
      }
    }

    quickSort(array, lo, left);
    quickSort(array, left + 1, hi);
  }

  PerDoc[] docFreeList = new PerDoc[1];
  int freeCount;
  int allocCount;

  synchronized PerDoc getPerDoc() {
    if (freeCount == 0) {
      allocCount++;
      if (allocCount > docFreeList.length) {
        // Grow our free list up front to make sure we have
        // enough space to recycle all outstanding PerDoc
        // instances
        assert allocCount == 1+docFreeList.length;
        docFreeList = new PerDoc[ArrayUtil.oversize(allocCount, RamUsageEstimator.NUM_BYTES_OBJECT_REF)];
      }
      return new PerDoc();
    } else
      return docFreeList[--freeCount];
  }

  synchronized void freePerDoc(PerDoc perDoc) {
    assert freeCount < docFreeList.length;
    docFreeList[freeCount++] = perDoc;
  }

  class PerDoc extends DocumentsWriter.DocWriter {

    DocumentsWriter.DocWriter one;
    DocumentsWriter.DocWriter two;

    @Override
    public long sizeInBytes() {
      return one.sizeInBytes() + two.sizeInBytes();
    }

    @Override
    public void finish() throws IOException {
      try {
        try {
          one.finish();
        } finally {
          two.finish();
        }
      } finally {
        freePerDoc(this);
      }
    }

    @Override
    public void abort() {
      try {
        try {
          one.abort();
        } finally {
          two.abort();
        }
      } finally {
        freePerDoc(this);
      }
    }
  }
}