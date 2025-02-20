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
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.FieldInfo.DocValuesType;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.Version;

/**
 * Abstract class to do basic tests for fis format.
 * NOTE: This test focuses on the fis impl, nothing else.
 * The [stretch] goal is for this test to be
 * so thorough in testing a new fis format that if this
 * test passes, then all Lucene/Solr tests should also pass.  Ie,
 * if there is some bug in a given fis Format that this
 * test fails to catch then this test needs to be improved! */
public abstract class BaseFieldInfoFormatTestCase extends BaseIndexFileFormatTestCase {
  
  /** Test field infos read/write with a single field */
  public void testOneField() throws Exception {
    Directory dir = newDirectory();
    Codec codec = getCodec();
    SegmentInfo segmentInfo = newSegmentInfo(dir, "_123");
    FieldInfos.Builder builder = new FieldInfos.Builder();
    FieldInfo fi = builder.addOrUpdate("field", TextField.TYPE_STORED);
    addAttributes(fi);
    FieldInfos infos = builder.finish();
    codec.fieldInfosFormat().write(dir, segmentInfo, "", infos, IOContext.DEFAULT);
    FieldInfos infos2 = codec.fieldInfosFormat().read(dir, segmentInfo, "", IOContext.DEFAULT);
    assertEquals(1, infos2.size());
    assertNotNull(infos2.fieldInfo("field"));
    assertTrue(infos2.fieldInfo("field").isIndexed());
    assertFalse(infos2.fieldInfo("field").hasDocValues());
    assertFalse(infos2.fieldInfo("field").omitsNorms());
    assertFalse(infos2.fieldInfo("field").hasPayloads());
    assertFalse(infos2.fieldInfo("field").hasVectors());
    dir.close();
  }
  
  // TODO: more tests
  
  /** Test field infos read/write with random fields, with different values. */
  public void testRandom() throws Exception {
    Directory dir = newDirectory();
    Codec codec = getCodec();
    SegmentInfo segmentInfo = newSegmentInfo(dir, "_123");
    
    // generate a bunch of fields
    int numFields = atLeast(2000);
    Set<String> fieldNames = new HashSet<>();
    for (int i = 0; i < numFields; i++) {
      fieldNames.add(TestUtil.randomUnicodeString(random()));
    }
    FieldInfos.Builder builder = new FieldInfos.Builder();
    for (String field : fieldNames) {
      IndexableFieldType fieldType = randomFieldType(random());
      FieldInfo fi = builder.addOrUpdate(field, fieldType);
      if (fieldType.indexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0) {
        if (random().nextBoolean()) {
          fi.setStorePayloads();
        }
      }
      addAttributes(fi);
    }
    FieldInfos infos = builder.finish();
    codec.fieldInfosFormat().write(dir, segmentInfo, "", infos, IOContext.DEFAULT);
    FieldInfos infos2 = codec.fieldInfosFormat().read(dir, segmentInfo, "", IOContext.DEFAULT);
    assertEquals(infos, infos2);
    dir.close();
  }
  
  private final IndexableFieldType randomFieldType(Random r) {
    FieldType type = new FieldType();
    
    type.setIndexed(r.nextBoolean());
    if (type.indexed()) {
      IndexOptions values[] = IndexOptions.values();
      type.setIndexOptions(values[r.nextInt(values.length)]);
      type.setOmitNorms(r.nextBoolean());
      
      if (r.nextBoolean()) {
        type.setStoreTermVectors(true);
        if (type.indexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0) {
          type.setStoreTermVectorPositions(r.nextBoolean());
          type.setStoreTermVectorOffsets(r.nextBoolean());
          if (type.storeTermVectorPositions()) {
            type.setStoreTermVectorPayloads(r.nextBoolean());
          }
        }
      }
    }
    
    if (r.nextBoolean()) {
      DocValuesType values[] = getDocValuesTypes();
      type.setDocValueType(values[r.nextInt(values.length)]);
    }
        
    return type;
  }
  
  /** 
   * Hook to add any codec attributes to fieldinfo
   * instances added in this test.
   */
  protected void addAttributes(FieldInfo fi) {
  }
  
  /** 
   * Docvalues types to test. 
   * @deprecated only for Only available to ancient codecs can 
   * limit this to the subset of types they support.
   */
  @Deprecated
  protected DocValuesType[] getDocValuesTypes() {
    return DocValuesType.values();
  }
  
  /** equality for entirety of fieldinfos */
  protected void assertEquals(FieldInfos expected, FieldInfos actual) {
    assertEquals(expected.size(), actual.size());
    for (FieldInfo expectedField : expected) {
      FieldInfo actualField = actual.fieldInfo(expectedField.number);
      assertNotNull(actualField);
      assertEquals(expectedField, actualField);
    }
  }
  
  /** equality for two individual fieldinfo objects */
  protected void assertEquals(FieldInfo expected, FieldInfo actual) {
    assertEquals(expected.number, actual.number);
    assertEquals(expected.name, actual.name);
    assertEquals(expected.getDocValuesType(), actual.getDocValuesType());
    assertEquals(expected.getIndexOptions(), actual.getIndexOptions());
    assertEquals(expected.hasDocValues(), actual.hasDocValues());
    assertEquals(expected.hasNorms(), actual.hasNorms());
    assertEquals(expected.hasPayloads(), actual.hasPayloads());
    assertEquals(expected.hasVectors(), actual.hasVectors());
    assertEquals(expected.isIndexed(), actual.isIndexed());
    assertEquals(expected.omitsNorms(), actual.omitsNorms());
    assertEquals(expected.getDocValuesGen(), actual.getDocValuesGen());
  }
  
  /** Returns a new fake segment */
  protected static SegmentInfo newSegmentInfo(Directory dir, String name) {
    return new SegmentInfo(dir, Version.LATEST, name, 10000, false, Codec.getDefault(), null, StringHelper.randomId());
  }
  
  @Override
  protected void addRandomFields(Document doc) {
    doc.add(new StoredField("foobar", TestUtil.randomSimpleString(random())));
  }

  @Override
  public void testRamBytesUsed() throws IOException {
    assumeTrue("not applicable for this format", true);
  }
}
