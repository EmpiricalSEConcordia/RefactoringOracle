package org.apache.lucene.search.function;

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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.function.DocValues;

import java.io.IOException;

/**
 * Expert: obtains int field values from the 
 * {@link org.apache.lucene.search.FieldCache FieldCache}
 * using <code>getInts()</code> and makes those values 
 * available as other numeric types, casting as needed.
 * 
 * @lucene.experimental
 * 
 * @see org.apache.lucene.search.function.FieldCacheSource for requirements 
 * on the field.
 *
 * <p><b>NOTE</b>: with the switch in 2.9 to segment-based
 * searching, if {@link #getValues} is invoked with a
 * composite (multi-segment) reader, this can easily cause
 * double RAM usage for the values in the FieldCache.  It's
 * best to switch your application to pass only atomic
 * (single segment) readers to this API.  Alternatively, for
 * a short-term fix, you could wrap your ValueSource using
 * {@link MultiValueSource}, which costs more CPU per lookup
 * but will not consume double the FieldCache RAM.</p>
 */
public class IntFieldSource extends FieldCacheSource {
  private FieldCache.IntParser parser;

  /**
   * Create a cached int field source with default string-to-int parser. 
   */
  public IntFieldSource(String field) {
    this(field, null);
  }

  /**
   * Create a cached int field source with a specific string-to-int parser. 
   */
  public IntFieldSource(String field, FieldCache.IntParser parser) {
    super(field);
    this.parser = parser;
  }

  /*(non-Javadoc) @see org.apache.lucene.search.function.ValueSource#description() */
  @Override
  public String description() {
    return "int(" + super.description() + ')';
  }

  /*(non-Javadoc) @see org.apache.lucene.search.function.FieldCacheSource#getCachedValues(org.apache.lucene.search.FieldCache, java.lang.String, org.apache.lucene.index.IndexReader) */
  @Override
  public DocValues getCachedFieldValues (FieldCache cache, String field, IndexReader reader) throws IOException {
    final int[] arr = cache.getInts(reader, field, parser);
    return new DocValues() {
      /*(non-Javadoc) @see org.apache.lucene.search.function.DocValues#floatVal(int) */
      @Override
      public float floatVal(int doc) { 
        return (float) arr[doc]; 
      }
      /*(non-Javadoc) @see org.apache.lucene.search.function.DocValues#intVal(int) */
      @Override
      public  int intVal(int doc) { 
        return arr[doc]; 
      }
      /*(non-Javadoc) @see org.apache.lucene.search.function.DocValues#toString(int) */
      @Override
      public String toString(int doc) { 
        return  description() + '=' + intVal(doc);  
      }
      /*(non-Javadoc) @see org.apache.lucene.search.function.DocValues#getInnerArray() */
      @Override
      Object getInnerArray() {
        return arr;
      }
    };
  }

  /*(non-Javadoc) @see org.apache.lucene.search.function.FieldCacheSource#cachedFieldSourceEquals(org.apache.lucene.search.function.FieldCacheSource) */
  @Override
  public boolean cachedFieldSourceEquals(FieldCacheSource o) {
    if (o.getClass() !=  IntFieldSource.class) {
      return false;
    }
    IntFieldSource other = (IntFieldSource)o;
    return this.parser==null ? 
      other.parser==null :
      this.parser.getClass() == other.parser.getClass();
  }

  /*(non-Javadoc) @see org.apache.lucene.search.function.FieldCacheSource#cachedFieldSourceHashCode() */
  @Override
  public int cachedFieldSourceHashCode() {
    return parser==null ? 
      Integer.class.hashCode() : parser.getClass().hashCode();
  }

}
