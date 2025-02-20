package org.apache.lucene.index;

/**
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Vector;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.IOException;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.OutputStream;
import org.apache.lucene.store.RAMOutputStream;

/**
 * The SegmentMerger class combines two or more Segments, represented by an IndexReader ({@link #add},
 * into a single Segment.  After adding the appropriate readers, call the merge method to combine the 
 * segments.
 *<P> 
 * If the compoundFile flag is set, then the segments will be merged into a compound file.
 *   
 * 
 * @see #merge
 * @see #add
 */
final class SegmentMerger {
  private Directory directory;
  private String segment;

  private Vector readers = new Vector();
  private FieldInfos fieldInfos;

  // File extensions of old-style index files
  private static final String COMPOUND_EXTENSIONS[] = new String[] {
    "fnm", "frq", "prx", "fdx", "fdt", "tii", "tis"
  };
  private static final String VECTOR_EXTENSIONS[] = new String[] {
    "tvx", "tvd", "tvf"
  };

  /**
   * 
   * @param dir The Directory to merge the other segments into
   * @param name The name of the new segment
   */
  SegmentMerger(Directory dir, String name) {
    directory = dir;
    segment = name;
  }

  /**
   * Add an IndexReader to the collection of readers that are to be merged
   * @param reader
   */
  final void add(IndexReader reader) {
    readers.addElement(reader);
  }

  /**
   * 
   * @param i The index of the reader to return
   * @return The ith reader to be merged
   */
  final IndexReader segmentReader(int i) {
    return (IndexReader) readers.elementAt(i);
  }

  /**
   * Merges the readers specified by the {@link #add} method into the directory passed to the constructor
   * @return The number of documents that were merged
   * @throws IOException
   */
  final int merge() throws IOException {
    int value;
    
    value = mergeFields();
    mergeTerms();
    mergeNorms();

    if (fieldInfos.hasVectors())
      mergeVectors();

    return value;
  }
  
  /**
   * close all IndexReaders that have been added.
   * Should not be called before merge().
   * @throws IOException
   */
  final void closeReaders() throws IOException {
    for (int i = 0; i < readers.size(); i++) {  // close readers
      IndexReader reader = (IndexReader) readers.elementAt(i);
      reader.close();
    }
  }

  final Vector createCompoundFile(String fileName)
          throws IOException {
    CompoundFileWriter cfsWriter =
            new CompoundFileWriter(directory, fileName);

    Vector files =
      new Vector(COMPOUND_EXTENSIONS.length + fieldInfos.size());    
    
    // Basic files
    for (int i = 0; i < COMPOUND_EXTENSIONS.length; i++) {
      files.add(segment + "." + COMPOUND_EXTENSIONS[i]);
    }

    // Field norm files
    for (int i = 0; i < fieldInfos.size(); i++) {
      FieldInfo fi = fieldInfos.fieldInfo(i);
      if (fi.isIndexed) {
        files.add(segment + ".f" + i);
      }
    }

    // Vector files
    if (fieldInfos.hasVectors()) {
      for (int i = 0; i < VECTOR_EXTENSIONS.length; i++) {
        files.add(segment + "." + VECTOR_EXTENSIONS[i]);
      }
    }

    // Now merge all added files
    Iterator it = files.iterator();
    while (it.hasNext()) {
      cfsWriter.addFile((String) it.next());
    }
    
    // Perform the merge
    cfsWriter.close();
   
    return files;
  }

  /**
   * 
   * @return The number of documents in all of the readers
   * @throws IOException
   */
  private final int mergeFields() throws IOException {
    fieldInfos = new FieldInfos();		  // merge field names
    int docCount = 0;
    for (int i = 0; i < readers.size(); i++) {
      IndexReader reader = (IndexReader) readers.elementAt(i);
      fieldInfos.addIndexed(reader.getIndexedFieldNames(true), true);
      fieldInfos.addIndexed(reader.getIndexedFieldNames(false), false);
      fieldInfos.add(reader.getFieldNames(false), false);
    }
    fieldInfos.write(directory, segment + ".fnm");

    FieldsWriter fieldsWriter = // merge field values
            new FieldsWriter(directory, segment, fieldInfos);
    try {
      for (int i = 0; i < readers.size(); i++) {
        IndexReader reader = (IndexReader) readers.elementAt(i);
        int maxDoc = reader.maxDoc();
        for (int j = 0; j < maxDoc; j++)
          if (!reader.isDeleted(j)) {               // skip deleted docs
            fieldsWriter.addDocument(reader.document(j));
            docCount++;
          }
      }
    } finally {
      fieldsWriter.close();
    }
    return docCount;
  }

  /**
   * Merge the TermVectors from each of the segments into the new one.
   * @throws IOException
   */
  private final void mergeVectors() throws IOException {
    TermVectorsWriter termVectorsWriter = 
      new TermVectorsWriter(directory, segment, fieldInfos);

    try {
      for (int r = 0; r < readers.size(); r++) {
        IndexReader reader = (IndexReader) readers.elementAt(r);
        int maxDoc = reader.maxDoc();
        for (int docNum = 0; docNum < maxDoc; docNum++) {
          // skip deleted docs
          if (reader.isDeleted(docNum)) {
            continue;
          }
          termVectorsWriter.openDocument();

          // get all term vectors
          TermFreqVector[] sourceTermVector =
            reader.getTermFreqVectors(docNum);

          if (sourceTermVector != null) {
            for (int f = 0; f < sourceTermVector.length; f++) {
              // translate field numbers
              TermFreqVector termVector = sourceTermVector[f];
              termVectorsWriter.openField(termVector.getField());
              String [] terms = termVector.getTerms();
              int [] freqs = termVector.getTermFrequencies();
              
              for (int t = 0; t < terms.length; t++) {
                termVectorsWriter.addTerm(terms[t], freqs[t]);
              }
            }
            termVectorsWriter.closeDocument();
          }
        }
      }
    } finally {
      termVectorsWriter.close();
    }
  }

  private OutputStream freqOutput = null;
  private OutputStream proxOutput = null;
  private TermInfosWriter termInfosWriter = null;
  private int skipInterval;
  private SegmentMergeQueue queue = null;

  private final void mergeTerms() throws IOException {
    try {
      freqOutput = directory.createFile(segment + ".frq");
      proxOutput = directory.createFile(segment + ".prx");
      termInfosWriter =
              new TermInfosWriter(directory, segment, fieldInfos);
      skipInterval = termInfosWriter.skipInterval;
      queue = new SegmentMergeQueue(readers.size());

      mergeTermInfos();

    } finally {
      if (freqOutput != null) freqOutput.close();
      if (proxOutput != null) proxOutput.close();
      if (termInfosWriter != null) termInfosWriter.close();
      if (queue != null) queue.close();
    }
  }

  private final void mergeTermInfos() throws IOException {
    int base = 0;
    for (int i = 0; i < readers.size(); i++) {
      IndexReader reader = (IndexReader) readers.elementAt(i);
      TermEnum termEnum = reader.terms();
      SegmentMergeInfo smi = new SegmentMergeInfo(base, termEnum, reader);
      base += reader.numDocs();
      if (smi.next())
        queue.put(smi);				  // initialize queue
      else
        smi.close();
    }

    SegmentMergeInfo[] match = new SegmentMergeInfo[readers.size()];

    while (queue.size() > 0) {
      int matchSize = 0;			  // pop matching terms
      match[matchSize++] = (SegmentMergeInfo) queue.pop();
      Term term = match[0].term;
      SegmentMergeInfo top = (SegmentMergeInfo) queue.top();

      while (top != null && term.compareTo(top.term) == 0) {
        match[matchSize++] = (SegmentMergeInfo) queue.pop();
        top = (SegmentMergeInfo) queue.top();
      }

      mergeTermInfo(match, matchSize);		  // add new TermInfo

      while (matchSize > 0) {
        SegmentMergeInfo smi = match[--matchSize];
        if (smi.next())
          queue.put(smi);			  // restore queue
        else
          smi.close();				  // done with a segment
      }
    }
  }

  private final TermInfo termInfo = new TermInfo(); // minimize consing

  /** Merge one term found in one or more segments. The array <code>smis</code>
   *  contains segments that are positioned at the same term. <code>N</code>
   *  is the number of cells in the array actually occupied.
   *
   * @param smis array of segments
   * @param n number of cells in the array actually occupied
   */
  private final void mergeTermInfo(SegmentMergeInfo[] smis, int n)
          throws IOException {
    long freqPointer = freqOutput.getFilePointer();
    long proxPointer = proxOutput.getFilePointer();

    int df = appendPostings(smis, n);		  // append posting data

    long skipPointer = writeSkip();

    if (df > 0) {
      // add an entry to the dictionary with pointers to prox and freq files
      termInfo.set(df, freqPointer, proxPointer, (int) (skipPointer - freqPointer));
      termInfosWriter.add(smis[0].term, termInfo);
    }
  }

  /** Process postings from multiple segments all positioned on the
   *  same term. Writes out merged entries into freqOutput and
   *  the proxOutput streams.
   *
   * @param smis array of segments
   * @param n number of cells in the array actually occupied
   * @return number of documents across all segments where this term was found
   */
  private final int appendPostings(SegmentMergeInfo[] smis, int n)
          throws IOException {
    int lastDoc = 0;
    int df = 0;					  // number of docs w/ term
    resetSkip();
    for (int i = 0; i < n; i++) {
      SegmentMergeInfo smi = smis[i];
      TermPositions postings = smi.postings;
      int base = smi.base;
      int[] docMap = smi.docMap;
      postings.seek(smi.termEnum);
      while (postings.next()) {
        int doc = postings.doc();
        if (docMap != null)
          doc = docMap[doc];                      // map around deletions
        doc += base;                              // convert to merged space

        if (doc < lastDoc)
          throw new IllegalStateException("docs out of order");

        df++;

        if ((df % skipInterval) == 0) {
          bufferSkip(lastDoc);
        }

        int docCode = (doc - lastDoc) << 1;	  // use low bit to flag freq=1
        lastDoc = doc;

        int freq = postings.freq();
        if (freq == 1) {
          freqOutput.writeVInt(docCode | 1);	  // write doc & freq=1
        } else {
          freqOutput.writeVInt(docCode);	  // write doc
          freqOutput.writeVInt(freq);		  // write frequency in doc
        }

        int lastPosition = 0;			  // write position deltas
        for (int j = 0; j < freq; j++) {
          int position = postings.nextPosition();
          proxOutput.writeVInt(position - lastPosition);
          lastPosition = position;
        }
      }
    }
    return df;
  }

  private RAMOutputStream skipBuffer = new RAMOutputStream();
  private int lastSkipDoc;
  private long lastSkipFreqPointer;
  private long lastSkipProxPointer;

  private void resetSkip() throws IOException {
    skipBuffer.reset();
    lastSkipDoc = 0;
    lastSkipFreqPointer = freqOutput.getFilePointer();
    lastSkipProxPointer = proxOutput.getFilePointer();
  }

  private void bufferSkip(int doc) throws IOException {
    long freqPointer = freqOutput.getFilePointer();
    long proxPointer = proxOutput.getFilePointer();

    skipBuffer.writeVInt(doc - lastSkipDoc);
    skipBuffer.writeVInt((int) (freqPointer - lastSkipFreqPointer));
    skipBuffer.writeVInt((int) (proxPointer - lastSkipProxPointer));

    lastSkipDoc = doc;
    lastSkipFreqPointer = freqPointer;
    lastSkipProxPointer = proxPointer;
  }

  private long writeSkip() throws IOException {
    long skipPointer = freqOutput.getFilePointer();
    skipBuffer.writeTo(freqOutput);
    return skipPointer;
  }

  private void mergeNorms() throws IOException {
    for (int i = 0; i < fieldInfos.size(); i++) {
      FieldInfo fi = fieldInfos.fieldInfo(i);
      if (fi.isIndexed) {
        OutputStream output = directory.createFile(segment + ".f" + i);
        try {
          for (int j = 0; j < readers.size(); j++) {
            IndexReader reader = (IndexReader) readers.elementAt(j);
            byte[] input = reader.norms(fi.name);
            int maxDoc = reader.maxDoc();
            for (int k = 0; k < maxDoc; k++) {
              byte norm = input != null ? input[k] : (byte) 0;
              if (!reader.isDeleted(k)) {
                output.writeByte(norm);
              }
            }
          }
        } finally {
          output.close();
        }
      }
    }
  }

}
