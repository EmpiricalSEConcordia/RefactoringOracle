package org.apache.lucene.store.instantiated;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Payload;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.index.TermPositions;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.LuceneTestCase;

/**
 * Asserts equality of content and behaviour of two index readers.
 */
public class TestIndicesEquals extends LuceneTestCase {

//  public void test2() throws Exception {
//    FSDirectory fsdir = FSDirectory.open(new File("/tmp/fatcorpus"));
//    IndexReader ir = IndexReader.open(fsdir, false);
//    InstantiatedIndex ii = new InstantiatedIndex(ir);
//    ir.close();
//    testEquals(fsdir, ii);
//  }


  public void testLoadIndexReader() throws Exception {
    RAMDirectory dir = new RAMDirectory();

    // create dir data
    IndexWriter indexWriter = new IndexWriter(dir, new IndexWriterConfig(
        TEST_VERSION_CURRENT, new MockAnalyzer()));
    for (int i = 0; i < 20; i++) {
      Document document = new Document();
      assembleDocument(document, i);
      indexWriter.addDocument(document);
    }
    indexWriter.close();

    // test load ii from index reader
    IndexReader ir = IndexReader.open(dir, false);
    InstantiatedIndex ii = new InstantiatedIndex(ir);
    ir.close();

    testEqualBehaviour(dir, ii);
  }


  public void testInstantiatedIndexWriter() throws Exception {


    RAMDirectory dir = new RAMDirectory();
    InstantiatedIndex ii = new InstantiatedIndex();

    // create dir data
    IndexWriter indexWriter = new IndexWriter(dir, new IndexWriterConfig(
        TEST_VERSION_CURRENT, new MockAnalyzer()));
    for (int i = 0; i < 500; i++) {
      Document document = new Document();
      assembleDocument(document, i);
      indexWriter.addDocument(document);
    }
    indexWriter.close();

    // test ii writer
    InstantiatedIndexWriter instantiatedIndexWriter = ii.indexWriterFactory(new MockAnalyzer(), true);
    for (int i = 0; i < 500; i++) {
      Document document = new Document();
      assembleDocument(document, i);
      instantiatedIndexWriter.addDocument(document);
    }
    instantiatedIndexWriter.close();


    testEqualBehaviour(dir, ii);



  }


  private void testTermDocsSomeMore(Directory aprioriIndex, InstantiatedIndex testIndex) throws Exception {

    IndexReader aprioriReader = IndexReader.open(aprioriIndex, false);
    IndexReader testReader = testIndex.indexReaderFactory();

    // test seek

    Term t = new Term("c", "danny");
    TermEnum aprioriTermEnum = aprioriReader.terms(t);
    TermEnum testTermEnum = testReader.terms(t);

    assertEquals(aprioriTermEnum.term(), testTermEnum.term());

    t = aprioriTermEnum.term();

    aprioriTermEnum.close();
    testTermEnum.close();

    TermDocs aprioriTermDocs = aprioriReader.termDocs(t);
    TermDocs testTermDocs = testReader.termDocs(t);

    assertEquals(aprioriTermDocs.next(), testTermDocs.next());
    assertEquals(aprioriTermDocs.freq(), testTermDocs.freq());
    assertEquals(aprioriTermDocs.doc(), testTermDocs.doc());

    if (aprioriTermDocs.skipTo(4)) {
      assertTrue(testTermDocs.skipTo(4));
      assertEquals(aprioriTermDocs.freq(), testTermDocs.freq());
      assertEquals(aprioriTermDocs.doc(), testTermDocs.doc());
    } else {
      assertFalse(testTermDocs.skipTo(4));
    }

    if (aprioriTermDocs.next()) {
      assertTrue(testTermDocs.next());
      assertEquals(aprioriTermDocs.freq(), testTermDocs.freq());
      assertEquals(aprioriTermDocs.doc(), testTermDocs.doc());
    } else {
      assertFalse(testTermDocs.next());
    }


    // beyond this point all next and skipto will return false

    if (aprioriTermDocs.skipTo(100)) {
      assertTrue(testTermDocs.skipTo(100));
      assertEquals(aprioriTermDocs.freq(), testTermDocs.freq());
      assertEquals(aprioriTermDocs.doc(), testTermDocs.doc());
    } else {
      assertFalse(testTermDocs.skipTo(100));
    }


    if (aprioriTermDocs.next()) {
      assertTrue(testTermDocs.next());
      assertEquals(aprioriTermDocs.freq(), testTermDocs.freq());
      assertEquals(aprioriTermDocs.doc(), testTermDocs.doc());
    } else {
      assertFalse(testTermDocs.next());
    }

    if (aprioriTermDocs.skipTo(110)) {
      assertTrue(testTermDocs.skipTo(110));
      assertEquals(aprioriTermDocs.freq(), testTermDocs.freq());
      assertEquals(aprioriTermDocs.doc(), testTermDocs.doc());
    } else {
      assertFalse(testTermDocs.skipTo(110));
    }

    if (aprioriTermDocs.skipTo(10)) {
      assertTrue(testTermDocs.skipTo(10));
      assertEquals(aprioriTermDocs.freq(), testTermDocs.freq());
      assertEquals(aprioriTermDocs.doc(), testTermDocs.doc());
    } else {
      assertFalse(testTermDocs.skipTo(10));
    }


    if (aprioriTermDocs.skipTo(210)) {
      assertTrue(testTermDocs.skipTo(210));
      assertEquals(aprioriTermDocs.freq(), testTermDocs.freq());
      assertEquals(aprioriTermDocs.doc(), testTermDocs.doc());
    } else {
      assertFalse(testTermDocs.skipTo(210));
    }

    aprioriTermDocs.close();
    testTermDocs.close();



    // test seek null (AllTermDocs)
    aprioriTermDocs = aprioriReader.termDocs(null);
    testTermDocs = testReader.termDocs(null);

    while (aprioriTermDocs.next()) {
      assertTrue(testTermDocs.next());
      assertEquals(aprioriTermDocs.freq(), testTermDocs.freq());
      assertEquals(aprioriTermDocs.doc(), testTermDocs.doc());
    }
    assertFalse(testTermDocs.next());


    aprioriTermDocs.close();
    testTermDocs.close();


    // test seek default
    aprioriTermDocs = aprioriReader.termDocs();
    testTermDocs = testReader.termDocs();

    // this is invalid use of the API,
    // but if the response differs then it's an indication that something might have changed.
    // in 2.9 and 3.0 the two TermDocs-implementations returned different values at this point.
//    assertEquals("Descripency during invalid use of the TermDocs API, see comments in test code for details.",
//        aprioriTermDocs.next(), testTermDocs.next());

    // start using the API the way one is supposed to use it

    t = new Term("", "");
    aprioriTermDocs.seek(t);
    testTermDocs.seek(t);

    while (aprioriTermDocs.next()) {
      assertTrue(testTermDocs.next());
      assertEquals(aprioriTermDocs.freq(), testTermDocs.freq());
      assertEquals(aprioriTermDocs.doc(), testTermDocs.doc());
    }
    assertFalse(testTermDocs.next());

    aprioriTermDocs.close();
    testTermDocs.close();


    // clean up
    aprioriReader.close();
    testReader.close();

  }


  private void assembleDocument(Document document, int i) {
    document.add(new Field("a", i + " Do you really want to go and live in that house all winter?", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
    if (i > 0) {
      document.add(new Field("b0", i + " All work and no play makes Jack a dull boy", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
      document.add(new Field("b1", i + " All work and no play makes Jack a dull boy", Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS, Field.TermVector.NO));
      document.add(new Field("b2", i + " All work and no play makes Jack a dull boy", Field.Store.NO, Field.Index.NOT_ANALYZED, Field.TermVector.NO));
      document.add(new Field("b3", i + " All work and no play makes Jack a dull boy", Field.Store.YES, Field.Index.NO, Field.TermVector.NO));
      if (i > 1) {
        document.add(new Field("c", i + " Redrum redrum", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
        if (i > 2) {
          document.add(new Field("d", i + " Hello Danny, come and play with us... forever and ever. and ever.", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
          if (i > 3) {
            Field f = new Field("e", i + " Heres Johnny!", Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS);
            f.setOmitNorms(true);
            document.add(f);
            if (i > 4) {
              final List<Token> tokens = new ArrayList<Token>(2);
              Token t = createToken("the", 0, 2, "text");
              t.setPayload(new Payload(new byte[]{1, 2, 3}));
              tokens.add(t);
              t = createToken("end", 3, 5, "text");
              t.setPayload(new Payload(new byte[]{2}));
              tokens.add(t);
              tokens.add(createToken("fin", 7, 9));
              TokenStream ts = new TokenStream(Token.TOKEN_ATTRIBUTE_FACTORY) {
                final AttributeImpl reusableToken = (AttributeImpl) addAttribute(TermAttribute.class);
                Iterator<Token> it = tokens.iterator();
                
                @Override
                public final boolean incrementToken() throws IOException {
                  if (!it.hasNext()) {
                    return false;
                  }
                  clearAttributes();
                  it.next().copyTo(reusableToken);
                  return true;
                }

                @Override
                public void reset() throws IOException {
                  it = tokens.iterator();
                }
              };
              
              document.add(new Field("f", ts));
            }
          }
        }
      }
    }
  }


  /**
   * Asserts that the content of two index readers equal each other.
   *
   * @param aprioriIndex the index that is known to be correct
   * @param testIndex    the index that is supposed to equals the apriori index.
   * @throws Exception
   */
  protected void testEqualBehaviour(Directory aprioriIndex, InstantiatedIndex testIndex) throws Exception {


    testEquals(aprioriIndex,  testIndex);

    // delete a few documents
    IndexReader air = IndexReader.open(aprioriIndex, false);
    InstantiatedIndexReader tir = testIndex.indexReaderFactory();

    assertEquals(air.isCurrent(), tir.isCurrent());
    assertEquals(air.hasDeletions(), tir.hasDeletions());
    assertEquals(air.maxDoc(), tir.maxDoc());
    assertEquals(air.numDocs(), tir.numDocs());
    assertEquals(air.numDeletedDocs(), tir.numDeletedDocs());

    air.deleteDocument(3);
    tir.deleteDocument(3);

    assertEquals(air.isCurrent(), tir.isCurrent());
    assertEquals(air.hasDeletions(), tir.hasDeletions());
    assertEquals(air.maxDoc(), tir.maxDoc());
    assertEquals(air.numDocs(), tir.numDocs());
    assertEquals(air.numDeletedDocs(), tir.numDeletedDocs());

    air.deleteDocument(8);
    tir.deleteDocument(8);

    assertEquals(air.isCurrent(), tir.isCurrent());
    assertEquals(air.hasDeletions(), tir.hasDeletions());
    assertEquals(air.maxDoc(), tir.maxDoc());
    assertEquals(air.numDocs(), tir.numDocs());
    assertEquals(air.numDeletedDocs(), tir.numDeletedDocs());    

    // this (in 3.0) commits the deletions
    air.close();
    tir.close();

    air = IndexReader.open(aprioriIndex, false);
    tir = testIndex.indexReaderFactory();

    assertEquals(air.isCurrent(), tir.isCurrent());
    assertEquals(air.hasDeletions(), tir.hasDeletions());
    assertEquals(air.maxDoc(), tir.maxDoc());
    assertEquals(air.numDocs(), tir.numDocs());
    assertEquals(air.numDeletedDocs(), tir.numDeletedDocs());

    for (int d =0; d<air.maxDoc(); d++) {
      assertEquals(air.isDeleted(d), tir.isDeleted(d));
    }

    air.close();
    tir.close();


    // make sure they still equal
    testEquals(aprioriIndex,  testIndex);
  }

  protected void testEquals(Directory aprioriIndex, InstantiatedIndex testIndex) throws Exception {

    testTermDocsSomeMore(aprioriIndex, testIndex);

    IndexReader aprioriReader = IndexReader.open(aprioriIndex, false);
    IndexReader testReader = testIndex.indexReaderFactory();

    assertEquals(aprioriReader.numDocs(), testReader.numDocs());

    // assert field options
    assertEquals(aprioriReader.getFieldNames(IndexReader.FieldOption.INDEXED), testReader.getFieldNames(IndexReader.FieldOption.INDEXED));
    assertEquals(aprioriReader.getFieldNames(IndexReader.FieldOption.INDEXED_NO_TERMVECTOR), testReader.getFieldNames(IndexReader.FieldOption.INDEXED_NO_TERMVECTOR));
    assertEquals(aprioriReader.getFieldNames(IndexReader.FieldOption.INDEXED_WITH_TERMVECTOR), testReader.getFieldNames(IndexReader.FieldOption.INDEXED_WITH_TERMVECTOR));
    assertEquals(aprioriReader.getFieldNames(IndexReader.FieldOption.STORES_PAYLOADS), testReader.getFieldNames(IndexReader.FieldOption.STORES_PAYLOADS));
    assertEquals(aprioriReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR), testReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR));
    assertEquals(aprioriReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_OFFSET), testReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_OFFSET));
    assertEquals(aprioriReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_POSITION), testReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_POSITION));
    assertEquals(aprioriReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_POSITION_OFFSET), testReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_POSITION_OFFSET));
    assertEquals(aprioriReader.getFieldNames(IndexReader.FieldOption.UNINDEXED), testReader.getFieldNames(IndexReader.FieldOption.UNINDEXED));

    for (Object field : aprioriReader.getFieldNames(IndexReader.FieldOption.ALL)) {

      // test norms as used by normal use

      byte[] aprioriNorms = aprioriReader.norms((String) field);
      byte[] testNorms = testReader.norms((String) field);

      if (aprioriNorms != null) {
        assertEquals(aprioriNorms.length, testNorms.length);

        for (int i = 0; i < aprioriNorms.length; i++) {
          assertEquals("norms does not equals for field " + field + " in document " + i, aprioriNorms[i], testNorms[i]);
        }

        // test norms as used by multireader

        aprioriNorms = new byte[aprioriReader.maxDoc()];
        aprioriReader.norms((String) field, aprioriNorms, 0);

        testNorms = new byte[testReader.maxDoc()];
        testReader.norms((String) field, testNorms, 0);

        assertEquals(aprioriNorms.length, testNorms.length);

        for (int i = 0; i < aprioriNorms.length; i++) {
          assertEquals("norms does not equals for field " + field + " in document " + i, aprioriNorms[i], testNorms[i]);
        }


        // test norms as used by multireader

        aprioriNorms = new byte[aprioriReader.maxDoc() + 10];
        aprioriReader.norms((String) field, aprioriNorms, 10);

        testNorms = new byte[testReader.maxDoc() + 10];
        testReader.norms((String) field, testNorms, 10);

        assertEquals(aprioriNorms.length, testNorms.length);
        
        for (int i = 0; i < aprioriNorms.length; i++) {
          assertEquals("norms does not equals for field " + field + " in document " + i, aprioriNorms[i], testNorms[i]);
        }
      }

    }

    for (int docIndex = 0; docIndex < aprioriReader.numDocs(); docIndex++) {
      assertEquals(aprioriReader.isDeleted(docIndex), testReader.isDeleted(docIndex));
    }

    // compare term enumeration stepping

    TermEnum aprioriTermEnum = aprioriReader.terms();
    TermEnum testTermEnum = testReader.terms();


    while (true) {

      if (!aprioriTermEnum.next()) {
        assertFalse(testTermEnum.next());
        break;
      }
      assertTrue(testTermEnum.next());

      assertEquals(aprioriTermEnum.term(), testTermEnum.term());
      assertTrue(aprioriTermEnum.docFreq() == testTermEnum.docFreq());

      // compare termDocs seeking

      TermDocs aprioriTermDocsSeeker = aprioriReader.termDocs(aprioriTermEnum.term());
      TermDocs testTermDocsSeeker = testReader.termDocs(testTermEnum.term());

      while (aprioriTermDocsSeeker.next()) {
        assertTrue(testTermDocsSeeker.skipTo(aprioriTermDocsSeeker.doc()));
        assertEquals(aprioriTermDocsSeeker.doc(), testTermDocsSeeker.doc());
      }

      aprioriTermDocsSeeker.close();
      testTermDocsSeeker.close();

      // compare documents per term

      assertEquals(aprioriReader.docFreq(aprioriTermEnum.term()), testReader.docFreq(testTermEnum.term()));

      TermDocs aprioriTermDocs = aprioriReader.termDocs(aprioriTermEnum.term());
      TermDocs testTermDocs = testReader.termDocs(testTermEnum.term());

      while (true) {
        if (!aprioriTermDocs.next()) {
          assertFalse(testTermDocs.next());
          break;
        }
        assertTrue(testTermDocs.next());

        assertEquals(aprioriTermDocs.doc(), testTermDocs.doc());
        assertEquals(aprioriTermDocs.freq(), testTermDocs.freq());
      }

      aprioriTermDocs.close();
      testTermDocs.close();

      // compare term positions

      TermPositions testTermPositions = testReader.termPositions(testTermEnum.term());
      TermPositions aprioriTermPositions = aprioriReader.termPositions(aprioriTermEnum.term());

      if (aprioriTermPositions != null) {

        for (int docIndex = 0; docIndex < aprioriReader.maxDoc(); docIndex++) {
          boolean hasNext = aprioriTermPositions.next();
          if (hasNext) {
            assertTrue(testTermPositions.next());

            assertEquals(aprioriTermPositions.freq(), testTermPositions.freq());


            for (int termPositionIndex = 0; termPositionIndex < aprioriTermPositions.freq(); termPositionIndex++) {
              int aprioriPos = aprioriTermPositions.nextPosition();
              int testPos = testTermPositions.nextPosition();

              if (aprioriPos != testPos) {
                assertEquals(aprioriPos, testPos);
              }


              assertEquals(aprioriTermPositions.isPayloadAvailable(), testTermPositions.isPayloadAvailable());
              if (aprioriTermPositions.isPayloadAvailable()) {
                assertEquals(aprioriTermPositions.getPayloadLength(), testTermPositions.getPayloadLength());
                byte[] aprioriPayloads = aprioriTermPositions.getPayload(new byte[aprioriTermPositions.getPayloadLength()], 0);
                byte[] testPayloads = testTermPositions.getPayload(new byte[testTermPositions.getPayloadLength()], 0);
                for (int i = 0; i < aprioriPayloads.length; i++) {
                  assertEquals(aprioriPayloads[i], testPayloads[i]);
                }
              }

            }
          }
        }

        aprioriTermPositions.close();
        testTermPositions.close();

      }
    }

    // compare term vectors and position vectors

    for (int documentNumber = 0; documentNumber < aprioriReader.numDocs(); documentNumber++) {

      if (documentNumber > 0) {
        assertNotNull(aprioriReader.getTermFreqVector(documentNumber, "b0"));
        assertNull(aprioriReader.getTermFreqVector(documentNumber, "b1"));

        assertNotNull(testReader.getTermFreqVector(documentNumber, "b0"));
        assertNull(testReader.getTermFreqVector(documentNumber, "b1"));

      }

      TermFreqVector[] aprioriFreqVectors = aprioriReader.getTermFreqVectors(documentNumber);
      TermFreqVector[] testFreqVectors = testReader.getTermFreqVectors(documentNumber);

      if (aprioriFreqVectors != null && testFreqVectors != null) {

        Arrays.sort(aprioriFreqVectors, new Comparator<TermFreqVector>() {
          public int compare(TermFreqVector termFreqVector, TermFreqVector termFreqVector1) {
            return termFreqVector.getField().compareTo(termFreqVector1.getField());
          }
        });
        Arrays.sort(testFreqVectors, new Comparator<TermFreqVector>() {
          public int compare(TermFreqVector termFreqVector, TermFreqVector termFreqVector1) {
            return termFreqVector.getField().compareTo(termFreqVector1.getField());
          }
        });

        assertEquals("document " + documentNumber + " vectors does not match", aprioriFreqVectors.length, testFreqVectors.length);

        for (int freqVectorIndex = 0; freqVectorIndex < aprioriFreqVectors.length; freqVectorIndex++) {
          assertTrue(Arrays.equals(aprioriFreqVectors[freqVectorIndex].getTermFrequencies(), testFreqVectors[freqVectorIndex].getTermFrequencies()));
          assertTrue(Arrays.equals(aprioriFreqVectors[freqVectorIndex].getTerms(), testFreqVectors[freqVectorIndex].getTerms()));

          if (aprioriFreqVectors[freqVectorIndex] instanceof TermPositionVector) {
            TermPositionVector aprioriTermPositionVector = (TermPositionVector) aprioriFreqVectors[freqVectorIndex];
            TermPositionVector testTermPositionVector = (TermPositionVector) testFreqVectors[freqVectorIndex];

            for (int positionVectorIndex = 0; positionVectorIndex < aprioriFreqVectors[freqVectorIndex].getTerms().length; positionVectorIndex++)
            {
              if (aprioriTermPositionVector.getOffsets(positionVectorIndex) != null) {
                assertTrue(Arrays.equals(aprioriTermPositionVector.getOffsets(positionVectorIndex), testTermPositionVector.getOffsets(positionVectorIndex)));
              }

              if (aprioriTermPositionVector.getTermPositions(positionVectorIndex) != null) {
                assertTrue(Arrays.equals(aprioriTermPositionVector.getTermPositions(positionVectorIndex), testTermPositionVector.getTermPositions(positionVectorIndex)));
              }
            }
          }

        }
      }

    }

    aprioriTermEnum.close();
    testTermEnum.close();

    aprioriReader.close();
    testReader.close();
  }

  private static Token createToken(String term, int start, int offset)
  {
    Token token = new Token(start, offset);
    token.setTermBuffer(term);
    return token;
  }

  private static Token createToken(String term, int start, int offset, String type)
  {
    Token token = new Token(start, offset, type);
    token.setTermBuffer(term);
    return token;
  }


}
