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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util._TestUtil;

public class TestSegmentReader extends LuceneTestCase {
  private Directory dir;
  private Document testDoc = new Document();
  private SegmentReader reader = null;
  
  //TODO: Setup the reader w/ multiple documents
  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    DocHelper.setupDoc(testDoc);
    SegmentInfo info = DocHelper.writeDoc(random, dir, testDoc);
    reader = new SegmentReader(info, IndexReader.DEFAULT_TERMS_INDEX_DIVISOR, IOContext.READ);
  }
  
  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  public void test() {
    assertTrue(dir != null);
    assertTrue(reader != null);
    assertTrue(DocHelper.nameValues.size() > 0);
    assertTrue(DocHelper.numFields(testDoc) == DocHelper.all.size());
  }
  
  public void testDocument() throws IOException {
    assertTrue(reader.numDocs() == 1);
    assertTrue(reader.maxDoc() >= 1);
    Document result = reader.document(0);
    assertTrue(result != null);
    //There are 2 unstored fields on the document that are not preserved across writing
    assertTrue(DocHelper.numFields(result) == DocHelper.numFields(testDoc) - DocHelper.unstored.size());
    
    List<IndexableField> fields = result.getFields();
    for (final IndexableField field : fields ) { 
      assertTrue(field != null);
      assertTrue(DocHelper.nameValues.containsKey(field.name()));
    }
  }
  
  public void testGetFieldNameVariations() {
    Collection<String> result = reader.getFieldNames(IndexReader.FieldOption.ALL);
    assertTrue(result != null);
    assertTrue(result.size() == DocHelper.all.size());
    for (Iterator<String> iter = result.iterator(); iter.hasNext();) {
      String s =  iter.next();
      //System.out.println("Name: " + s);
      assertTrue(DocHelper.nameValues.containsKey(s) == true || s.equals(""));
    }                                                                               
    result = reader.getFieldNames(IndexReader.FieldOption.INDEXED);
    assertTrue(result != null);
    assertTrue(result.size() == DocHelper.indexed.size());
    for (Iterator<String> iter = result.iterator(); iter.hasNext();) {
      String s = iter.next();
      assertTrue(DocHelper.indexed.containsKey(s) == true || s.equals(""));
    }
    
    result = reader.getFieldNames(IndexReader.FieldOption.UNINDEXED);
    assertTrue(result != null);
    assertTrue(result.size() == DocHelper.unindexed.size());
    //Get all indexed fields that are storing term vectors
    result = reader.getFieldNames(IndexReader.FieldOption.INDEXED_WITH_TERMVECTOR);
    assertTrue(result != null);
    assertTrue(result.size() == DocHelper.termvector.size());
    
    result = reader.getFieldNames(IndexReader.FieldOption.INDEXED_NO_TERMVECTOR);
    assertTrue(result != null);
    assertTrue(result.size() == DocHelper.notermvector.size());
  } 
  
  public void testTerms() throws IOException {
    FieldsEnum fields = MultiFields.getFields(reader).iterator();
    String field;
    while((field = fields.next()) != null) {
      Terms terms = fields.terms();
      assertNotNull(terms);
      TermsEnum termsEnum = terms.iterator(null);
      while(termsEnum.next() != null) {
        BytesRef term = termsEnum.term();
        assertTrue(term != null);
        String fieldValue = (String) DocHelper.nameValues.get(field);
        assertTrue(fieldValue.indexOf(term.utf8ToString()) != -1);
      }
    }
    
    DocsEnum termDocs = _TestUtil.docs(random, reader,
                                       DocHelper.TEXT_FIELD_1_KEY,
                                       new BytesRef("field"),
                                       MultiFields.getLiveDocs(reader),
                                       null,
                                       false);
    assertTrue(termDocs.nextDoc() != DocsEnum.NO_MORE_DOCS);

    termDocs = _TestUtil.docs(random, reader,
                              DocHelper.NO_NORMS_KEY,
                              new BytesRef(DocHelper.NO_NORMS_TEXT),
                              MultiFields.getLiveDocs(reader),
                              null,
                              false);

    assertTrue(termDocs.nextDoc() != DocsEnum.NO_MORE_DOCS);

    
    DocsAndPositionsEnum positions = MultiFields.getTermPositionsEnum(reader,
                                                                      MultiFields.getLiveDocs(reader),
                                                                      DocHelper.TEXT_FIELD_1_KEY,
                                                                      new BytesRef("field"));
    // NOTE: prior rev of this test was failing to first
    // call next here:
    assertTrue(positions.nextDoc() != DocsEnum.NO_MORE_DOCS);
    assertTrue(positions.docID() == 0);
    assertTrue(positions.nextPosition() >= 0);
  }    
  
  public void testNorms() throws IOException {
    //TODO: Not sure how these work/should be tested
/*
    try {
      byte [] norms = reader.norms(DocHelper.TEXT_FIELD_1_KEY);
      System.out.println("Norms: " + norms);
      assertTrue(norms != null);
    } catch (IOException e) {
      e.printStackTrace();
      assertTrue(false);
    }
*/

    checkNorms(reader);
  }

  public static void checkNorms(IndexReader reader) throws IOException {
        // test omit norms
    for (int i=0; i<DocHelper.fields.length; i++) {
      IndexableField f = DocHelper.fields[i];
      if (f.fieldType().indexed()) {
        assertEquals(reader.hasNorms(f.name()), !f.fieldType().omitNorms());
        assertEquals(reader.hasNorms(f.name()), !DocHelper.noNorms.containsKey(f.name()));
        if (!reader.hasNorms(f.name())) {
          // test for norms of null
          byte [] norms = MultiNorms.norms(reader, f.name());
          assertNull(norms);
        }
      }
    }
  }
  
  public void testTermVectors() throws IOException {
    Terms result = reader.getTermVectors(0).terms(DocHelper.TEXT_FIELD_2_KEY);
    assertNotNull(result);
    assertEquals(3, result.getUniqueTermCount());
    TermsEnum termsEnum = result.iterator(null);
    while(termsEnum.next() != null) {
      String term = termsEnum.term().utf8ToString();
      int freq = (int) termsEnum.totalTermFreq();
      assertTrue(DocHelper.FIELD_2_TEXT.indexOf(term) != -1);
      assertTrue(freq > 0);
    }

    Fields results = reader.getTermVectors(0);
    assertTrue(results != null);
    assertEquals("We do not have 3 term freq vectors", 3, results.getUniqueFieldCount());      
  }    
}
