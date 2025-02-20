package org.apache.lucene.index;

/**
 * Copyright 2006 The Apache Software Foundation
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

import java.io.IOException;

import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.RAMDirectory;

import junit.framework.TestCase;

/**
 * Tests lazy skipping on the proximity file.
 *
 */
public class TestLazyProxSkipping extends TestCase {
    private Searcher searcher;
    private int seeksCounter = 0;
    
    private String field = "tokens";
    private String term1 = "xx";
    private String term2 = "yy";
    private String term3 = "zz";
    
    private void createIndex(int numHits) throws IOException {
        int numDocs = 500;
        
        Directory directory = new RAMDirectory();
        IndexWriter writer = new IndexWriter(directory, new WhitespaceAnalyzer(), true);
        
        for (int i = 0; i < numDocs; i++) {
            Document doc = new Document();
            String content;
            if (i % (numDocs / numHits) == 0) {
                // add a document that matches the query "term1 term2"
                content = this.term1 + " " + this.term2;
            } else if (i % 15 == 0) {
                // add a document that only contains term1
                content = this.term1 + " " + this.term1;
            } else {
                // add a document that contains term2 but not term 1
                content = this.term3 + " " + this.term2;
            }

            doc.add(new Field(this.field, content, Field.Store.YES, Field.Index.TOKENIZED));
            writer.addDocument(doc);
        }
        
        // make sure the index has only a single segment
        writer.optimize();
        writer.close();
        
        // the index is a single segment, thus IndexReader.open() returns an instance of SegmentReader
        SegmentReader reader = (SegmentReader) IndexReader.open(directory);

        // we decorate the proxStream with a wrapper class that allows to count the number of calls of seek()
        reader.proxStream = new SeeksCountingStream(reader.proxStream);
        
        this.searcher = new IndexSearcher(reader);        
    }
    
    private Hits search() throws IOException {
        // create PhraseQuery "term1 term2" and search
        PhraseQuery pq = new PhraseQuery();
        pq.add(new Term(this.field, this.term1));
        pq.add(new Term(this.field, this.term2));
        return this.searcher.search(pq);        
    }
    
    private void performTest(int numHits) throws IOException {
        createIndex(numHits);
        this.seeksCounter = 0;
        Hits hits = search();
        // verify that the right number of docs was found
        assertEquals(numHits, hits.length());
        
        // check if the number of calls of seek() does not exceed the number of hits
        assertEquals(numHits, this.seeksCounter);
    }
    
    public void testLazySkipping() throws IOException {
        // test whether only the minimum amount of seeks() are performed
        performTest(5);
        performTest(10);
    }
    

    // Simply extends IndexInput in a way that we are able to count the number
    // of invocations of seek()
    class SeeksCountingStream extends IndexInput {
          private IndexInput input;      
          
          
          SeeksCountingStream(IndexInput input) {
              this.input = input;
          }      
                
          public byte readByte() throws IOException {
              return this.input.readByte();
          }
    
          public void readBytes(byte[] b, int offset, int len) throws IOException {
              this.input.readBytes(b, offset, len);        
          }
    
          public void close() throws IOException {
              this.input.close();
          }
    
          public long getFilePointer() {
              return this.input.getFilePointer();
          }
    
          public void seek(long pos) throws IOException {
              TestLazyProxSkipping.this.seeksCounter++;
              this.input.seek(pos);
          }
    
          public long length() {
              return this.input.length();
          }
          
          public Object clone() {
              return new SeeksCountingStream((IndexInput) this.input.clone());
          }
      
    }
}
