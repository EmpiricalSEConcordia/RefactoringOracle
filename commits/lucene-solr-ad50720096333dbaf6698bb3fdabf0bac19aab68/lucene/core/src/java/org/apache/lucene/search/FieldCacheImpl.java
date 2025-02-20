package org.apache.lucene.search;

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
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DocTermOrds;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.OrdTermState;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FieldCacheSanityChecker;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.PagedBytes;
import org.apache.lucene.util.packed.GrowableWriter;
import org.apache.lucene.util.packed.PackedInts;

// nocommit rename to UninvertFieldCacheImpl or something ...

/**
 * Expert: The default cache implementation, storing all values in memory.
 * A WeakHashMap is used for storage.
 *
 * @since   lucene 1.4
 */
class FieldCacheImpl implements FieldCache {

  private Map<Class<?>,Cache> caches;
  FieldCacheImpl() {
    init();
  }

  private synchronized void init() {
    caches = new HashMap<Class<?>,Cache>(9);
    caches.put(Byte.TYPE, new ByteCache(this));
    caches.put(Short.TYPE, new ShortCache(this));
    caches.put(Integer.TYPE, new IntCache(this));
    caches.put(Float.TYPE, new FloatCache(this));
    caches.put(Long.TYPE, new LongCache(this));
    caches.put(Double.TYPE, new DoubleCache(this));
    caches.put(BinaryDocValues.class, new BinaryDocValuesCache(this));
    caches.put(SortedDocValues.class, new SortedDocValuesCache(this));
    caches.put(DocTermOrds.class, new DocTermOrdsCache(this));
    caches.put(DocsWithFieldCache.class, new DocsWithFieldCache(this));
  }

  @Override
  public synchronized void purgeAllCaches() {
    init();
  }

  @Override
  public synchronized void purge(AtomicReader r) {
    for(Cache c : caches.values()) {
      c.purge(r);
    }
  }

  @Override
  public synchronized CacheEntry[] getCacheEntries() {
    List<CacheEntry> result = new ArrayList<CacheEntry>(17);
    for(final Map.Entry<Class<?>,Cache> cacheEntry: caches.entrySet()) {
      final Cache cache = cacheEntry.getValue();
      final Class<?> cacheType = cacheEntry.getKey();
      synchronized(cache.readerCache) {
        for (final Map.Entry<Object,Map<CacheKey, Object>> readerCacheEntry : cache.readerCache.entrySet()) {
          final Object readerKey = readerCacheEntry.getKey();
          if (readerKey == null) continue;
          final Map<CacheKey, Object> innerCache = readerCacheEntry.getValue();
          for (final Map.Entry<CacheKey, Object> mapEntry : innerCache.entrySet()) {
            CacheKey entry = mapEntry.getKey();
            result.add(new CacheEntry(readerKey, entry.field,
                                      cacheType, entry.custom,
                                      mapEntry.getValue()));
          }
        }
      }
    }
    return result.toArray(new CacheEntry[result.size()]);
  }

  // per-segment fieldcaches don't purge until the shared core closes.
  final SegmentReader.CoreClosedListener purgeCore = new SegmentReader.CoreClosedListener() {
    @Override
    public void onClose(SegmentReader owner) {
      FieldCacheImpl.this.purge(owner);
    }
  };

  // composite/SlowMultiReaderWrapper fieldcaches don't purge until composite reader is closed.
  final IndexReader.ReaderClosedListener purgeReader = new IndexReader.ReaderClosedListener() {
    @Override
    public void onClose(IndexReader owner) {
      assert owner instanceof AtomicReader;
      FieldCacheImpl.this.purge((AtomicReader) owner);
    }
  };
  
  private void initReader(AtomicReader reader) {
    if (reader instanceof SegmentReader) {
      ((SegmentReader) reader).addCoreClosedListener(purgeCore);
    } else {
      // we have a slow reader of some sort, try to register a purge event
      // rather than relying on gc:
      Object key = reader.getCoreCacheKey();
      if (key instanceof AtomicReader) {
        ((AtomicReader)key).addReaderClosedListener(purgeReader); 
      } else {
        // last chance
        reader.addReaderClosedListener(purgeReader);
      }
    }
  }

  /** Expert: Internal cache. */
  abstract static class Cache {

    // nocommit why wrapper vs non-static class...?
    Cache(FieldCacheImpl wrapper) {
      this.wrapper = wrapper;
    }

    final FieldCacheImpl wrapper;

    final Map<Object,Map<CacheKey,Object>> readerCache = new WeakHashMap<Object,Map<CacheKey,Object>>();
    
    protected abstract Object createValue(AtomicReader reader, CacheKey key, boolean setDocsWithField)
        throws IOException;

    /** Remove this reader from the cache, if present. */
    public void purge(AtomicReader r) {
      Object readerKey = r.getCoreCacheKey();
      synchronized(readerCache) {
        readerCache.remove(readerKey);
      }
    }

    /** Sets the key to the value for the provided reader;
     *  if the key is already set then this doesn't change it. */
    public void put(AtomicReader reader, CacheKey key, Object value) {
      final Object readerKey = reader.getCoreCacheKey();
      synchronized (readerCache) {
        Map<CacheKey,Object> innerCache = readerCache.get(readerKey);
        if (innerCache == null) {
          // First time this reader is using FieldCache
          innerCache = new HashMap<CacheKey,Object>();
          readerCache.put(readerKey, innerCache);
          wrapper.initReader(reader);
        }
        if (innerCache.get(key) == null) {
          innerCache.put(key, value);
        } else {
          // Another thread beat us to it; leave the current
          // value
        }
      }
    }

    public Object get(AtomicReader reader, CacheKey key, boolean setDocsWithField) throws IOException {
      Map<CacheKey,Object> innerCache;
      Object value;
      final Object readerKey = reader.getCoreCacheKey();
      synchronized (readerCache) {
        innerCache = readerCache.get(readerKey);
        if (innerCache == null) {
          // First time this reader is using FieldCache
          innerCache = new HashMap<CacheKey,Object>();
          readerCache.put(readerKey, innerCache);
          wrapper.initReader(reader);
          value = null;
        } else {
          value = innerCache.get(key);
        }
        if (value == null) {
          value = new CreationPlaceholder();
          innerCache.put(key, value);
        }
      }
      if (value instanceof CreationPlaceholder) {
        synchronized (value) {
          CreationPlaceholder progress = (CreationPlaceholder) value;
          if (progress.value == null) {
            progress.value = createValue(reader, key, setDocsWithField);
            synchronized (readerCache) {
              innerCache.put(key, progress.value);
            }

            // Only check if key.custom (the parser) is
            // non-null; else, we check twice for a single
            // call to FieldCache.getXXX
            if (key.custom != null && wrapper != null) {
              final PrintStream infoStream = wrapper.getInfoStream();
              if (infoStream != null) {
                printNewInsanity(infoStream, progress.value);
              }
            }
          }
          return progress.value;
        }
      }
      return value;
    }

    private void printNewInsanity(PrintStream infoStream, Object value) {
      final FieldCacheSanityChecker.Insanity[] insanities = FieldCacheSanityChecker.checkSanity(wrapper);
      for(int i=0;i<insanities.length;i++) {
        final FieldCacheSanityChecker.Insanity insanity = insanities[i];
        final CacheEntry[] entries = insanity.getCacheEntries();
        for(int j=0;j<entries.length;j++) {
          if (entries[j].getValue() == value) {
            // OK this insanity involves our entry
            infoStream.println("WARNING: new FieldCache insanity created\nDetails: " + insanity.toString());
            infoStream.println("\nStack:\n");
            new Throwable().printStackTrace(infoStream);
            break;
          }
        }
      }
    }
  }

  /** Expert: Every composite-key in the internal cache is of this type. */
  static class CacheKey {
    final String field;        // which Field
    final Object custom;       // which custom comparator or parser

    /** Creates one of these objects for a custom comparator/parser. */
    CacheKey(String field, Object custom) {
      this.field = field;
      this.custom = custom;
    }

    /** Two of these are equal iff they reference the same field and type. */
    @Override
    public boolean equals (Object o) {
      if (o instanceof CacheKey) {
        CacheKey other = (CacheKey) o;
        if (other.field.equals(field)) {
          if (other.custom == null) {
            if (custom == null) return true;
          } else if (other.custom.equals (custom)) {
            return true;
          }
        }
      }
      return false;
    }

    /** Composes a hashcode based on the field and type. */
    @Override
    public int hashCode() {
      return field.hashCode() ^ (custom==null ? 0 : custom.hashCode());
    }
  }

  private static abstract class Uninvert {

    public Bits docsWithField;

    public void uninvert(AtomicReader reader, String field, boolean setDocsWithField) throws IOException {
      final int maxDoc = reader.maxDoc();
      Terms terms = reader.terms(field);
      if (terms != null) {
        if (setDocsWithField) {
          final int termsDocCount = terms.getDocCount();
          assert termsDocCount <= maxDoc;
          if (termsDocCount == maxDoc) {
            // Fast case: all docs have this field:
            docsWithField = new Bits.MatchAllBits(maxDoc);
            setDocsWithField = false;
          }
        }

        final TermsEnum termsEnum = terms.iterator(null);

        DocsEnum docs = null;
        FixedBitSet docsWithField = null;
        while(true) {
          final BytesRef term = termsEnum.next();
          if (term == null) {
            break;
          }
          try {
            visitTerm(term);
          } catch (StopFillCacheException stop) {
            break;
          }
          docs = termsEnum.docs(null, docs, 0);
          while (true) {
            final int docID = docs.nextDoc();
            if (docID == DocIdSetIterator.NO_MORE_DOCS) {
              break;
            }
            visitDoc(docID);
            if (setDocsWithField) {
              if (docsWithField == null) {
                // Lazy init
                this.docsWithField = docsWithField = new FixedBitSet(maxDoc);
              }
              docsWithField.set(docID);
            }
          }
        }
      }
    }

    protected abstract void visitTerm(BytesRef term);
    protected abstract void visitDoc(int docID);
  }

  // null Bits means no docs matched
  void setDocsWithField(AtomicReader reader, String field, Bits docsWithField) {
    final int maxDoc = reader.maxDoc();
    final Bits bits;
    if (docsWithField == null) {
      bits = new Bits.MatchNoBits(maxDoc);
    } else if (docsWithField instanceof FixedBitSet) {
      final int numSet = ((FixedBitSet) docsWithField).cardinality();
      if (numSet >= maxDoc) {
        // The cardinality of the BitSet is maxDoc if all documents have a value.
        assert numSet == maxDoc;
        bits = new Bits.MatchAllBits(maxDoc);
      } else {
        bits = docsWithField;
      }
    } else {
      bits = docsWithField;
    }
    caches.get(DocsWithFieldCache.class).put(reader, new CacheKey(field, null), bits);
  }
  
  // inherit javadocs
  public Bytes getBytes (AtomicReader reader, String field, boolean setDocsWithField) throws IOException {
    return getBytes(reader, field, null, setDocsWithField);
  }

  // inherit javadocs
  public Bytes getBytes(AtomicReader reader, String field, ByteParser parser, boolean setDocsWithField)
      throws IOException {
    return (Bytes) caches.get(Byte.TYPE).get(reader, new CacheKey(field, parser), setDocsWithField);
  }

  // nocommit move up?
  static class BytesFromArray extends Bytes {
    private final byte[] values;

    public BytesFromArray(byte[] values) {
      this.values = values;
    }
    
    @Override
    public byte get(int docID) {
      return values[docID];
    }
  }

  static final class ByteCache extends Cache {
    ByteCache(FieldCacheImpl wrapper) {
      super(wrapper);
    }

    @Override
    protected Object createValue(AtomicReader reader, CacheKey key, boolean setDocsWithField)
        throws IOException {

      NumericDocValues valuesIn = reader.getNumericDocValues(key.field);
      if (valuesIn != null) {
        final NumericDocValues ramInstance = valuesIn.newRAMInstance();
        return new Bytes() {
          @Override
          public byte get(int docID) {
            return (byte) ramInstance.get(docID);
          }
        };
        // nocommit should we throw exc if parser isn't
        // null?  if setDocsWithField is true?
      } else {

        int maxDoc = reader.maxDoc();
        final byte[] values;
        final ByteParser parser = (ByteParser) key.custom;
        if (parser == null) {
          // Confusing: must delegate to wrapper (vs simply
          // setting parser = DEFAULT_SHORT_PARSER) so cache
          // key includes DEFAULT_SHORT_PARSER:
          return wrapper.getBytes(reader, key.field, DEFAULT_BYTE_PARSER, setDocsWithField);
        }

        values = new byte[maxDoc];

        Uninvert u = new Uninvert() {
            private byte currentValue;

            @Override
            public void visitTerm(BytesRef term) {
              currentValue = parser.parseByte(term);
            }

            @Override
            public void visitDoc(int docID) {
              values[docID] = currentValue;
            }
          };

        u.uninvert(reader, key.field, setDocsWithField);

        if (setDocsWithField) {
          wrapper.setDocsWithField(reader, key.field, u.docsWithField);
        }

        return new BytesFromArray(values);
      }
    }
  }
  
  // inherit javadocs
  public Shorts getShorts (AtomicReader reader, String field, boolean setDocsWithField) throws IOException {
    return getShorts(reader, field, null, setDocsWithField);
  }

  // inherit javadocs
  public Shorts getShorts(AtomicReader reader, String field, ShortParser parser, boolean setDocsWithField)
      throws IOException {
    return (Shorts) caches.get(Short.TYPE).get(reader, new CacheKey(field, parser), setDocsWithField);
  }

  // nocommit move up?
  static class ShortsFromArray extends Shorts {
    private final short[] values;

    public ShortsFromArray(short[] values) {
      this.values = values;
    }
    
    @Override
    public short get(int docID) {
      return values[docID];
    }
  }

  static final class ShortCache extends Cache {
    ShortCache(FieldCacheImpl wrapper) {
      super(wrapper);
    }

    @Override
    protected Object createValue(AtomicReader reader, CacheKey key, boolean setDocsWithField)
        throws IOException {

      NumericDocValues valuesIn = reader.getNumericDocValues(key.field);
      if (valuesIn != null) {
        final NumericDocValues ramInstance = valuesIn.newRAMInstance();
        return new Shorts() {
          @Override
          public short get(int docID) {
            return (short) ramInstance.get(docID);
          }
        };
        // nocommit should we throw exc if parser isn't
        // null?  if setDocsWithField is true?
      } else {
        int maxDoc = reader.maxDoc();
        final short[] values;
        final ShortParser parser = (ShortParser) key.custom;
        if (parser == null) {
          // Confusing: must delegate to wrapper (vs simply
          // setting parser = DEFAULT_SHORT_PARSER) so cache
          // key includes DEFAULT_SHORT_PARSER:
          return wrapper.getShorts(reader, key.field, DEFAULT_SHORT_PARSER, setDocsWithField);
        }

        values = new short[maxDoc];
        Uninvert u = new Uninvert() {
            private short currentValue;

            @Override
            public void visitTerm(BytesRef term) {
              currentValue = parser.parseShort(term);
            }

            @Override
            public void visitDoc(int docID) {
              values[docID] = currentValue;
            }
          };

        u.uninvert(reader, key.field, setDocsWithField);

        if (setDocsWithField) {
          wrapper.setDocsWithField(reader, key.field, u.docsWithField);
        }
        return new ShortsFromArray(values);
      }
    }
  }

  // inherit javadocs
  public Ints getInts (AtomicReader reader, String field, boolean setDocsWithField) throws IOException {
    return getInts(reader, field, null, setDocsWithField);
  }

  // inherit javadocs
  public Ints getInts(AtomicReader reader, String field, IntParser parser, boolean setDocsWithField)
      throws IOException {
    return (Ints) caches.get(Integer.TYPE).get(reader, new CacheKey(field, parser), setDocsWithField);
  }

  // nocommit move up?
  static class IntsFromArray extends Ints {
    private final int[] values;

    public IntsFromArray(int[] values) {
      this.values = values;
    }
    
    @Override
    public int get(int docID) {
      return values[docID];
    }
  }

  static final class IntCache extends Cache {
    IntCache(FieldCacheImpl wrapper) {
      super(wrapper);
    }

    @Override
    protected Object createValue(final AtomicReader reader, CacheKey key, boolean setDocsWithField)
        throws IOException {

      NumericDocValues valuesIn = reader.getNumericDocValues(key.field);
      if (valuesIn != null) {
        final NumericDocValues ramInstance = valuesIn.newRAMInstance();
        return new Ints() {
          @Override
          public int get(int docID) {
            return (int) ramInstance.get(docID);
          }
        };
        // nocommit should we throw exc if parser isn't
        // null?  if setDocsWithField is true?
      } else {
        final int[] values;
        final IntParser parser = (IntParser) key.custom;
        if (parser == null) {
          // Confusing: must delegate to wrapper (vs simply
          // setting parser =
          // DEFAULT_INT_PARSER/NUMERIC_UTILS_INT_PARSER) so
          // cache key includes
          // DEFAULT_INT_PARSER/NUMERIC_UTILS_INT_PARSER:
          try {
            return wrapper.getInts(reader, key.field, DEFAULT_INT_PARSER, setDocsWithField);
          } catch (NumberFormatException ne) {
            return wrapper.getInts(reader, key.field, NUMERIC_UTILS_INT_PARSER, setDocsWithField);
          }
        }

        // nocommit how to avoid double alloc in numeric field
        // case ...
        values = new int[reader.maxDoc()];

        Uninvert u = new Uninvert() {
            private int currentValue;

            @Override
            public void visitTerm(BytesRef term) {
              currentValue = parser.parseInt(term);
            }

            @Override
            public void visitDoc(int docID) {
              values[docID] = currentValue;
            }
          };

        u.uninvert(reader, key.field, setDocsWithField);

        if (setDocsWithField) {
          wrapper.setDocsWithField(reader, key.field, u.docsWithField);
        }
        return new IntsFromArray(values);
      }
    }
  }

  // nocommit must advertise that this does NOT work if you
  // index only doc values for the field ... it will say no
  // doc exists...
  public Bits getDocsWithField(AtomicReader reader, String field)
      throws IOException {
    return (Bits) caches.get(DocsWithFieldCache.class).get(reader, new CacheKey(field, null), false);
  }

  static final class DocsWithFieldCache extends Cache {
    DocsWithFieldCache(FieldCacheImpl wrapper) {
      super(wrapper);
    }
    
    @Override
    protected Object createValue(AtomicReader reader, CacheKey key, boolean setDocsWithField /* ignored */)
    throws IOException {
      final String field = key.field;      
      FixedBitSet res = null;
      Terms terms = reader.terms(field);
      final int maxDoc = reader.maxDoc();
      if (terms != null) {
        final int termsDocCount = terms.getDocCount();
        assert termsDocCount <= maxDoc;
        if (termsDocCount == maxDoc) {
          // Fast case: all docs have this field:
          return new Bits.MatchAllBits(maxDoc);
        }
        final TermsEnum termsEnum = terms.iterator(null);
        DocsEnum docs = null;
        while(true) {
          final BytesRef term = termsEnum.next();
          if (term == null) {
            break;
          }
          if (res == null) {
            // lazy init
            res = new FixedBitSet(maxDoc);
          }

          docs = termsEnum.docs(null, docs, 0);
          // TODO: use bulk API
          while (true) {
            final int docID = docs.nextDoc();
            if (docID == DocIdSetIterator.NO_MORE_DOCS) {
              break;
            }
            res.set(docID);
          }
        }
      }
      if (res == null) {
        return new Bits.MatchNoBits(maxDoc);
      }
      final int numSet = res.cardinality();
      if (numSet >= maxDoc) {
        // The cardinality of the BitSet is maxDoc if all documents have a value.
        assert numSet == maxDoc;
        return new Bits.MatchAllBits(maxDoc);
      }
      return res;
    }
  }

  // inherit javadocs
  public Floats getFloats (AtomicReader reader, String field, boolean setDocsWithField)
    throws IOException {
    return getFloats(reader, field, null, setDocsWithField);
  }

  // inherit javadocs
  public Floats getFloats(AtomicReader reader, String field, FloatParser parser, boolean setDocsWithField)
    throws IOException {
    return (Floats) caches.get(Float.TYPE).get(reader, new CacheKey(field, parser), setDocsWithField);
  }

  // nocommit move up?
  static class FloatsFromArray extends Floats {
    private final float[] values;

    public FloatsFromArray(float[] values) {
      this.values = values;
    }
    
    @Override
    public float get(int docID) {
      return values[docID];
    }
  }

  static final class FloatCache extends Cache {
    FloatCache(FieldCacheImpl wrapper) {
      super(wrapper);
    }

    @Override
    protected Object createValue(AtomicReader reader, CacheKey key, boolean setDocsWithField)
        throws IOException {

      NumericDocValues valuesIn = reader.getNumericDocValues(key.field);
      if (valuesIn != null) {
        final NumericDocValues ramInstance = valuesIn.newRAMInstance();
        return new Floats() {
          @Override
          public float get(int docID) {
            return Float.intBitsToFloat((int) ramInstance.get(docID));
          }
        };
        // nocommit should we throw exc if parser isn't
        // null?  if setDocsWithField is true?
      } else {
        final float[] values;
        final FloatParser parser = (FloatParser) key.custom;
        if (parser == null) {
          // Confusing: must delegate to wrapper (vs simply
          // setting parser =
          // DEFAULT_FLOAT_PARSER/NUMERIC_UTILS_FLOAT_PARSER) so
          // cache key includes
          // DEFAULT_FLOAT_PARSER/NUMERIC_UTILS_FLOAT_PARSER:
          try {
            return wrapper.getFloats(reader, key.field, DEFAULT_FLOAT_PARSER, setDocsWithField);
          } catch (NumberFormatException ne) {
            return wrapper.getFloats(reader, key.field, NUMERIC_UTILS_FLOAT_PARSER, setDocsWithField);
          }
        }

        // nocommit how to avoid double alloc in numeric field
        // case ...
        values = new float[reader.maxDoc()];

        Uninvert u = new Uninvert() {
            private float currentValue;

            @Override
            public void visitTerm(BytesRef term) {
              currentValue = parser.parseFloat(term);
            }

            @Override
            public void visitDoc(int docID) {
              values[docID] = currentValue;
            }
          };

        u.uninvert(reader, key.field, setDocsWithField);

        if (setDocsWithField) {
          wrapper.setDocsWithField(reader, key.field, u.docsWithField);
        }

        return new FloatsFromArray(values);
      }
    }
  }

  // inherit javadocs
  public Longs getLongs(AtomicReader reader, String field, boolean setDocsWithField) throws IOException {
    return getLongs(reader, field, null, setDocsWithField);
  }
  
  // inherit javadocs
  public Longs getLongs(AtomicReader reader, String field, FieldCache.LongParser parser, boolean setDocsWithField)
      throws IOException {
    return (Longs) caches.get(Long.TYPE).get(reader, new CacheKey(field, parser), setDocsWithField);
  }

  // nocommit move up?
  static class LongsFromArray extends Longs {
    private final long[] values;

    public LongsFromArray(long[] values) {
      this.values = values;
    }
    
    @Override
    public long get(int docID) {
      return values[docID];
    }
  }

  static final class LongCache extends Cache {
    LongCache(FieldCacheImpl wrapper) {
      super(wrapper);
    }

    @Override
    protected Object createValue(AtomicReader reader, CacheKey key, boolean setDocsWithField)
        throws IOException {

      NumericDocValues valuesIn = reader.getNumericDocValues(key.field);
      if (valuesIn != null) {
        final NumericDocValues ramInstance = valuesIn.newRAMInstance();
        return new Longs() {
          @Override
          public long get(int docID) {
            return ramInstance.get(docID);
          }
        };
        // nocommit should we throw exc if parser isn't
        // null?  if setDocsWithField is true?
      } else {
        final long[] values;
        final LongParser parser = (LongParser) key.custom;
        if (parser == null) {
          // Confusing: must delegate to wrapper (vs simply
          // setting parser =
          // DEFAULT_LONG_PARSER/NUMERIC_UTILS_LONG_PARSER) so
          // cache key includes
          // DEFAULT_LONG_PARSER/NUMERIC_UTILS_LONG_PARSER:
          try {
            return wrapper.getLongs(reader, key.field, DEFAULT_LONG_PARSER, setDocsWithField);
          } catch (NumberFormatException ne) {
            return wrapper.getLongs(reader, key.field, NUMERIC_UTILS_LONG_PARSER, setDocsWithField);
          }
        }

        // nocommit how to avoid double alloc in numeric field
        // case ...
        values = new long[reader.maxDoc()];

        Uninvert u = new Uninvert() {
            private long currentValue;

            @Override
            public void visitTerm(BytesRef term) {
              currentValue = parser.parseLong(term);
            }

            @Override
            public void visitDoc(int docID) {
              values[docID] = currentValue;
            }
          };

        u.uninvert(reader, key.field, setDocsWithField);

        if (setDocsWithField) {
          wrapper.setDocsWithField(reader, key.field, u.docsWithField);
        }
        return new LongsFromArray(values);
      }
    }
  }

  // inherit javadocs
  public Doubles getDoubles(AtomicReader reader, String field, boolean setDocsWithField)
    throws IOException {
    return getDoubles(reader, field, null, setDocsWithField);
  }

  // inherit javadocs
  public Doubles getDoubles(AtomicReader reader, String field, FieldCache.DoubleParser parser, boolean setDocsWithField)
      throws IOException {
    return (Doubles) caches.get(Double.TYPE).get(reader, new CacheKey(field, parser), setDocsWithField);
  }

  // nocommit move up?
  static class DoublesFromArray extends Doubles {
    private final double[] values;

    public DoublesFromArray(double[] values) {
      this.values = values;
    }
    
    @Override
    public double get(int docID) {
      return values[docID];
    }
  }

  static final class DoubleCache extends Cache {
    DoubleCache(FieldCacheImpl wrapper) {
      super(wrapper);
    }

    @Override
    protected Object createValue(AtomicReader reader, CacheKey key, boolean setDocsWithField)
        throws IOException {

      NumericDocValues valuesIn = reader.getNumericDocValues(key.field);
      if (valuesIn != null) {
        final NumericDocValues ramInstance = valuesIn.newRAMInstance();
        return new Doubles() {
          @Override
          public double get(int docID) {
            return Double.longBitsToDouble(ramInstance.get(docID));
          }
        };
        // nocommit should we throw exc if parser isn't
        // null?  if setDocsWithField is true?
      } else {
        final double[] values;
        final DoubleParser parser = (DoubleParser) key.custom;
        if (parser == null) {
          // Confusing: must delegate to wrapper (vs simply
          // setting parser =
          // DEFAULT_DOUBLE_PARSER/NUMERIC_UTILS_DOUBLE_PARSER) so
          // cache key includes
          // DEFAULT_DOUBLE_PARSER/NUMERIC_UTILS_DOUBLE_PARSER:
          try {
            return wrapper.getDoubles(reader, key.field, DEFAULT_DOUBLE_PARSER, setDocsWithField);
          } catch (NumberFormatException ne) {
            return wrapper.getDoubles(reader, key.field, NUMERIC_UTILS_DOUBLE_PARSER, setDocsWithField);
          }
        }

        // nocommit how to avoid double alloc in numeric field
        // case ...
        values = new double[reader.maxDoc()];

        Uninvert u = new Uninvert() {
            private double currentValue;

            @Override
            public void visitTerm(BytesRef term) {
              currentValue = parser.parseDouble(term);
            }

            @Override
            public void visitDoc(int docID) {
              values[docID] = currentValue;
            }
          };

        u.uninvert(reader, key.field, setDocsWithField);

        if (setDocsWithField) {
          wrapper.setDocsWithField(reader, key.field, u.docsWithField);
        }
        return new DoublesFromArray(values);
      }
    }
  }

  public static class SortedDocValuesImpl extends SortedDocValues {
    private final PagedBytes.Reader bytes;
    private final PackedInts.Reader termOrdToBytesOffset;
    private final PackedInts.Reader docToTermOrd;
    private final int numOrd;

    public SortedDocValuesImpl(PagedBytes.Reader bytes, PackedInts.Reader termOrdToBytesOffset, PackedInts.Reader docToTermOrd, int numOrd) {
      this.bytes = bytes;
      this.docToTermOrd = docToTermOrd;
      this.termOrdToBytesOffset = termOrdToBytesOffset;
      this.numOrd = numOrd;
    }

    @Override
    public int getValueCount() {
      return numOrd;
    }

    @Override
    public int getOrd(int docID) {
      // Subtract 1, matching the 1+ord we did when
      // storing, so that missing values, which are 0 in the
      // packed ints, are returned as -1 ord:
      return (int) docToTermOrd.get(docID)-1;
    }

    @Override
    public int size() {
      return docToTermOrd.size();
    }

    @Override
    public void lookupOrd(int ord, BytesRef ret) {
      if (ord < 0) {
        throw new IllegalArgumentException("ord must be >=0 (got ord=" + ord + ")");
      }
      bytes.fill(ret, termOrdToBytesOffset.get(ord));
    }

    @Override
    public int maxLength() {
      // nocommit hmm
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFixedLength() {
      // nocommit hmm
      throw new UnsupportedOperationException();
    }

    @Override
    public TermsEnum getTermsEnum() {
      return this.new SortedDocValuesEnum();
    }

    class SortedDocValuesEnum extends TermsEnum {
      int currentOrd;
      int currentBlockNumber;
      int end;  // end position in the current block
      final byte[][] blocks;
      final int[] blockEnds;

      final BytesRef term = new BytesRef();

      public SortedDocValuesEnum() {
        currentOrd = -1;
        currentBlockNumber = 0;
        blocks = bytes.getBlocks();
        blockEnds = bytes.getBlockEnds();
        term.bytes = blocks[0];
        end = blockEnds[currentBlockNumber];
      }

      @Override
      public SeekStatus seekCeil(BytesRef text, boolean useCache /* ignored */) throws IOException {
        int low = 0;
        int high = numOrd-1;
        
        while (low <= high) {
          int mid = (low + high) >>> 1;
          seekExact(mid);
          int cmp = term.compareTo(text);

          if (cmp < 0)
            low = mid + 1;
          else if (cmp > 0)
            high = mid - 1;
          else {
            return SeekStatus.FOUND; // key found
          }
        }
        
        if (low == numOrd) {
          return SeekStatus.END;
        } else {
          seekExact(low);
          return SeekStatus.NOT_FOUND;
        }
      }

      @Override
      public void seekExact(long ord) throws IOException {
        assert ord >= 0 && ord < numOrd;
        // TODO: if gap is small, could iterate from current position?  Or let user decide that?
        currentBlockNumber = bytes.fillAndGetIndex(term, termOrdToBytesOffset.get((int)ord));
        end = blockEnds[currentBlockNumber];
        currentOrd = (int)ord;
      }

      @Override
      public BytesRef next() throws IOException {
        int start = term.offset + term.length;
        if (start >= end) {
          // switch byte blocks
          if (currentBlockNumber+1 >= blocks.length) {
            assert currentOrd+1 == numOrd: "currentOrd=" + currentOrd + " numOrd=" + numOrd;
            return null;
          }
          currentBlockNumber++;
          term.bytes = blocks[currentBlockNumber];
          end = blockEnds[currentBlockNumber];
          start = 0;
          if (end<=0) {
            assert currentOrd+1 == numOrd;
            return null;  // special case of empty last array
          }
        }

        currentOrd++;

        byte[] block = term.bytes;
        if ((block[start] & 128) == 0) {
          term.length = block[start];
          term.offset = start+1;
        } else {
          term.length = (((block[start] & 0x7f)) << 8) | (block[1+start] & 0xff);
          term.offset = start+2;
        }

        return term;
      }

      @Override
      public BytesRef term() throws IOException {
        return term;
      }

      @Override
      public long ord() throws IOException {
        return currentOrd;
      }

      @Override
      public int docFreq() {
        throw new UnsupportedOperationException();
      }

      @Override
      public long totalTermFreq() {
        return -1;
      }

      @Override
      public DocsEnum docs(Bits liveDocs, DocsEnum reuse, int flags) throws IOException {
        throw new UnsupportedOperationException();
      }

      @Override
      public DocsAndPositionsEnum docsAndPositions(Bits liveDocs, DocsAndPositionsEnum reuse, int flags) throws IOException {
        throw new UnsupportedOperationException();
      }

      @Override
      public Comparator<BytesRef> getComparator() {
        return BytesRef.getUTF8SortedAsUnicodeComparator();
      }

      @Override
      public void seekExact(BytesRef term, TermState state) throws IOException {
        assert state != null && state instanceof OrdTermState;
        this.seekExact(((OrdTermState)state).ord);
      }

      @Override
      public TermState termState() throws IOException {
        OrdTermState state = new OrdTermState();
        state.ord = currentOrd;
        return state;
      }
    }
  }

  // nocommit for DV if you ask for sorted or binary we
  // should check sorted first?

  // nocommit woudl be nice if .getTErms would return a
  // DocTermsIndex if one already existed

  public SortedDocValues getTermsIndex(AtomicReader reader, String field) throws IOException {
    return getTermsIndex(reader, field, PackedInts.FAST);
  }

  public SortedDocValues getTermsIndex(AtomicReader reader, String field, float acceptableOverheadRatio) throws IOException {
    return (SortedDocValues) caches.get(SortedDocValues.class).get(reader, new CacheKey(field, acceptableOverheadRatio), false);
  }

  static class SortedDocValuesCache extends Cache {
    SortedDocValuesCache(FieldCacheImpl wrapper) {
      super(wrapper);
    }

    @Override
    protected Object createValue(AtomicReader reader, CacheKey key, boolean setDocsWithField /* ignored */)
        throws IOException {

      final int maxDoc = reader.maxDoc();
      SortedDocValues valuesIn = reader.getSortedDocValues(key.field);
      if (valuesIn != null) {
        return valuesIn.newRAMInstance();
      } else {

        Terms terms = reader.terms(key.field);

        final float acceptableOverheadRatio = ((Float) key.custom).floatValue();

        final PagedBytes bytes = new PagedBytes(15);

        int startBytesBPV;
        int startTermsBPV;
        int startNumUniqueTerms;

        final int termCountHardLimit;
        if (maxDoc == Integer.MAX_VALUE) {
          termCountHardLimit = Integer.MAX_VALUE;
        } else {
          termCountHardLimit = maxDoc+1;
        }

        // nocommit use Uninvert?
        if (terms != null) {
          // Try for coarse estimate for number of bits; this
          // should be an underestimate most of the time, which
          // is fine -- GrowableWriter will reallocate as needed
          long numUniqueTerms = terms.size();
          if (numUniqueTerms != -1L) {
            if (numUniqueTerms > termCountHardLimit) {
              // app is misusing the API (there is more than
              // one term per doc); in this case we make best
              // effort to load what we can (see LUCENE-2142)
              numUniqueTerms = termCountHardLimit;
            }

            startBytesBPV = PackedInts.bitsRequired(numUniqueTerms*4);
            startTermsBPV = PackedInts.bitsRequired(numUniqueTerms);

            startNumUniqueTerms = (int) numUniqueTerms;
          } else {
            startBytesBPV = 1;
            startTermsBPV = 1;
            startNumUniqueTerms = 1;
          }
        } else {
          startBytesBPV = 1;
          startTermsBPV = 1;
          startNumUniqueTerms = 1;
        }

        GrowableWriter termOrdToBytesOffset = new GrowableWriter(startBytesBPV, 1+startNumUniqueTerms, acceptableOverheadRatio);
        final GrowableWriter docToTermOrd = new GrowableWriter(startTermsBPV, maxDoc, acceptableOverheadRatio);

        int termOrd = 0;

        // nocommit use Uninvert?

        if (terms != null) {
          final TermsEnum termsEnum = terms.iterator(null);
          DocsEnum docs = null;

          while(true) {
            final BytesRef term = termsEnum.next();
            if (term == null) {
              break;
            }
            if (termOrd >= termCountHardLimit) {
              break;
            }

            if (termOrd == termOrdToBytesOffset.size()) {
              // NOTE: this code only runs if the incoming
              // reader impl doesn't implement
              // size (which should be uncommon)
              termOrdToBytesOffset = termOrdToBytesOffset.resize(ArrayUtil.oversize(1+termOrd, 1));
            }
            termOrdToBytesOffset.set(termOrd, bytes.copyUsingLengthPrefix(term));
            docs = termsEnum.docs(null, docs, 0);
            while (true) {
              final int docID = docs.nextDoc();
              if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                break;
              }
              // Store 1+ ord into packed bits
              docToTermOrd.set(docID, 1+termOrd);
            }
            termOrd++;
          }

          if (termOrdToBytesOffset.size() > termOrd) {
            termOrdToBytesOffset = termOrdToBytesOffset.resize(termOrd);
          }
        }

        // maybe an int-only impl?
        return new SortedDocValuesImpl(bytes.freeze(true), termOrdToBytesOffset.getMutable(), docToTermOrd.getMutable(), termOrd);
      }
    }
  }

  private static class BinaryDocValuesImpl extends BinaryDocValues {
    private final PagedBytes.Reader bytes;
    private final PackedInts.Reader docToOffset;

    public BinaryDocValuesImpl(PagedBytes.Reader bytes, PackedInts.Reader docToOffset) {
      this.bytes = bytes;
      this.docToOffset = docToOffset;
    }

    @Override
    public int size() {
      return docToOffset.size();
    }

    @Override
    public void get(int docID, BytesRef ret) {
      final int pointer = (int) docToOffset.get(docID);
      if (pointer == 0) {
        ret.bytes = MISSING;
        ret.offset = 0;
        ret.length = 0;
      } else {
        bytes.fill(ret, pointer);
      }
    }

    @Override
    public boolean isFixedLength() {
      // nocommit hmm
      throw new UnsupportedOperationException();
    }

    @Override
    public int maxLength() {
      // nocommit hmm
      throw new UnsupportedOperationException();
    }
  }

  // TODO: this if DocTermsIndex was already created, we
  // should share it...
  public BinaryDocValues getTerms(AtomicReader reader, String field) throws IOException {
    return getTerms(reader, field, PackedInts.FAST);
  }

  public BinaryDocValues getTerms(AtomicReader reader, String field, float acceptableOverheadRatio) throws IOException {
    return (BinaryDocValues) caches.get(BinaryDocValues.class).get(reader, new CacheKey(field, acceptableOverheadRatio), false);
  }

  static final class BinaryDocValuesCache extends Cache {
    BinaryDocValuesCache(FieldCacheImpl wrapper) {
      super(wrapper);
    }

    @Override
    protected Object createValue(AtomicReader reader, CacheKey key, boolean setDocsWithField /* ignored */)
        throws IOException {

      BinaryDocValues valuesIn = reader.getBinaryDocValues(key.field);
      if (valuesIn != null) {
        return valuesIn;
      } else {
        final int maxDoc = reader.maxDoc();
        Terms terms = reader.terms(key.field);

        final float acceptableOverheadRatio = ((Float) key.custom).floatValue();

        final int termCountHardLimit = maxDoc;

        // Holds the actual term data, expanded.
        final PagedBytes bytes = new PagedBytes(15);

        int startBPV;

        if (terms != null) {
          // Try for coarse estimate for number of bits; this
          // should be an underestimate most of the time, which
          // is fine -- GrowableWriter will reallocate as needed
          long numUniqueTerms = terms.size();
          if (numUniqueTerms != -1L) {
            if (numUniqueTerms > termCountHardLimit) {
              numUniqueTerms = termCountHardLimit;
            }
            startBPV = PackedInts.bitsRequired(numUniqueTerms*4);
          } else {
            startBPV = 1;
          }
        } else {
          startBPV = 1;
        }

        final GrowableWriter docToOffset = new GrowableWriter(startBPV, maxDoc, acceptableOverheadRatio);
      
        // pointer==0 means not set
        bytes.copyUsingLengthPrefix(new BytesRef());

        if (terms != null) {
          int termCount = 0;
          final TermsEnum termsEnum = terms.iterator(null);
          DocsEnum docs = null;
          while(true) {
            if (termCount++ == termCountHardLimit) {
              // app is misusing the API (there is more than
              // one term per doc); in this case we make best
              // effort to load what we can (see LUCENE-2142)
              break;
            }

            final BytesRef term = termsEnum.next();
            if (term == null) {
              break;
            }
            final long pointer = bytes.copyUsingLengthPrefix(term);
            docs = termsEnum.docs(null, docs, 0);
            while (true) {
              final int docID = docs.nextDoc();
              if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                break;
              }
              docToOffset.set(docID, pointer);
            }
          }
        }

        // maybe an int-only impl?
        return new BinaryDocValuesImpl(bytes.freeze(true), docToOffset.getMutable());
      }
    }
  }

  public DocTermOrds getDocTermOrds(AtomicReader reader, String field) throws IOException {
    return (DocTermOrds) caches.get(DocTermOrds.class).get(reader, new CacheKey(field, null), false);
  }

  static final class DocTermOrdsCache extends Cache {
    DocTermOrdsCache(FieldCacheImpl wrapper) {
      super(wrapper);
    }

    @Override
    protected Object createValue(AtomicReader reader, CacheKey key, boolean setDocsWithField /* ignored */)
        throws IOException {
      // No DocValues impl yet (DocValues are single valued...):
      return new DocTermOrds(reader, key.field);
    }
  }

  private volatile PrintStream infoStream;

  public void setInfoStream(PrintStream stream) {
    infoStream = stream;
  }

  public PrintStream getInfoStream() {
    return infoStream;
  }
}

