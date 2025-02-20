package org.apache.lucene.util.packed;

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
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.LongsRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.LuceneTestCase.Slow;
import org.apache.lucene.util._TestUtil;
import org.apache.lucene.util.packed.PackedInts.Reader;

import org.junit.Ignore;

import com.carrotsearch.randomizedtesting.generators.RandomInts;

@Slow
public class TestPackedInts extends LuceneTestCase {

  public void testByteCount() {
    final int iters = atLeast(3);
    for (int i = 0; i < iters; ++i) {
      final int valueCount = RandomInts.randomIntBetween(random(), 1, Integer.MAX_VALUE);
      for (PackedInts.Format format : PackedInts.Format.values()) {
        for (int bpv = 1; bpv <= 64; ++bpv) {
          final long byteCount = format.byteCount(PackedInts.VERSION_CURRENT, valueCount, bpv);
          String msg = "format=" + format + ", byteCount=" + byteCount + ", valueCount=" + valueCount + ", bpv=" + bpv;
          assertTrue(msg, byteCount * 8 >= (long) valueCount * bpv);
          if (format == PackedInts.Format.PACKED) {
            assertTrue(msg, (byteCount - 1) * 8 < (long) valueCount * bpv);
          }
        }
      }
    }
  }

  public void testBitsRequired() {
    assertEquals(61, PackedInts.bitsRequired((long)Math.pow(2, 61)-1));
    assertEquals(61, PackedInts.bitsRequired(0x1FFFFFFFFFFFFFFFL));
    assertEquals(62, PackedInts.bitsRequired(0x3FFFFFFFFFFFFFFFL));
    assertEquals(63, PackedInts.bitsRequired(0x7FFFFFFFFFFFFFFFL));
  }

  public void testMaxValues() {
    assertEquals("1 bit -> max == 1",
            1, PackedInts.maxValue(1));
    assertEquals("2 bit -> max == 3",
            3, PackedInts.maxValue(2));
    assertEquals("8 bit -> max == 255",
            255, PackedInts.maxValue(8));
    assertEquals("63 bit -> max == Long.MAX_VALUE",
            Long.MAX_VALUE, PackedInts.maxValue(63));
    assertEquals("64 bit -> max == Long.MAX_VALUE (same as for 63 bit)", 
            Long.MAX_VALUE, PackedInts.maxValue(64));
  }

  public void testPackedInts() throws IOException {
    int num = atLeast(3);
    for (int iter = 0; iter < num; iter++) {
      for(int nbits=1;nbits<=64;nbits++) {
        final long maxValue = PackedInts.maxValue(nbits);
        final int valueCount = _TestUtil.nextInt(random(), 1, 600);
        final int bufferSize = random().nextBoolean()
            ? _TestUtil.nextInt(random(), 0, 48)
            : _TestUtil.nextInt(random(), 0, 4096);
        final Directory d = newDirectory();
        
        IndexOutput out = d.createOutput("out.bin", newIOContext(random()));
        PackedInts.Writer w = PackedInts.getWriter(
                                out, valueCount, nbits, random().nextFloat());
        final long startFp = out.getFilePointer();

        final int actualValueCount = random().nextBoolean() ? valueCount : _TestUtil.nextInt(random(), 0, valueCount);
        final long[] values = new long[valueCount];
        for(int i=0;i<actualValueCount;i++) {
          if (nbits == 64) {
            values[i] = random().nextLong();
          } else {
            values[i] = _TestUtil.nextLong(random(), 0, maxValue);
          }
          w.add(values[i]);
        }
        w.finish();
        final long fp = out.getFilePointer();
        out.close();

        // ensure that finish() added the (valueCount-actualValueCount) missing values
        final long bytes = w.getFormat().byteCount(PackedInts.VERSION_CURRENT, valueCount, w.bitsPerValue);
        assertEquals(bytes, fp - startFp);

        {// test header
          IndexInput in = d.openInput("out.bin", newIOContext(random()));
          // header = codec header | bitsPerValue | valueCount | format
          CodecUtil.checkHeader(in, PackedInts.CODEC_NAME, PackedInts.VERSION_START, PackedInts.VERSION_CURRENT); // codec header
          assertEquals(w.bitsPerValue, in.readVInt());
          assertEquals(valueCount, in.readVInt());
          assertEquals(w.getFormat().getId(), in.readVInt());
          assertEquals(startFp, in.getFilePointer());
          in.close();
        }

        {// test reader
          IndexInput in = d.openInput("out.bin", newIOContext(random()));
          PackedInts.Reader r = PackedInts.getReader(in);
          assertEquals(fp, in.getFilePointer());
          for(int i=0;i<valueCount;i++) {
            assertEquals("index=" + i + " valueCount="
                    + valueCount + " nbits=" + nbits + " for "
                    + r.getClass().getSimpleName(), values[i], r.get(i));
          }
          in.close();

          final long expectedBytesUsed = RamUsageEstimator.sizeOf(r);
          final long computedBytesUsed = r.ramBytesUsed();
          assertEquals(r.getClass() + "expected " + expectedBytesUsed + ", got: " + computedBytesUsed,
              expectedBytesUsed, computedBytesUsed);
        }

        { // test reader iterator next
          IndexInput in = d.openInput("out.bin", newIOContext(random()));
          PackedInts.ReaderIterator r = PackedInts.getReaderIterator(in, bufferSize);
          for(int i=0;i<valueCount;i++) {
            assertEquals("index=" + i + " valueCount="
                    + valueCount + " nbits=" + nbits + " for "
                    + r.getClass().getSimpleName(), values[i], r.next());
            assertEquals(i, r.ord());
          }
          assertEquals(fp, in.getFilePointer());
          in.close();
        }

        { // test reader iterator bulk next
          IndexInput in = d.openInput("out.bin", newIOContext(random()));
          PackedInts.ReaderIterator r = PackedInts.getReaderIterator(in, bufferSize);
          int i = 0;
          while (i < valueCount) {
            final int count = _TestUtil.nextInt(random(), 1, 95);
            final LongsRef next = r.next(count);
            for (int k = 0; k < next.length; ++k) {
              assertEquals("index=" + i + " valueCount="
                  + valueCount + " nbits=" + nbits + " for "
                  + r.getClass().getSimpleName(), values[i + k], next.longs[next.offset + k]);
            }
            i += next.length;
          }
          assertEquals(fp, in.getFilePointer());
          in.close();
        }
        
        { // test direct reader get
          IndexInput in = d.openInput("out.bin", newIOContext(random()));
          PackedInts.Reader intsEnum = PackedInts.getDirectReader(in);
          for (int i = 0; i < valueCount; i++) {
            final String msg = "index=" + i + " valueCount="
                + valueCount + " nbits=" + nbits + " for "
                + intsEnum.getClass().getSimpleName();
            final int index = random().nextInt(valueCount);
            long value = intsEnum.get(index);
            assertEquals(msg, value, values[index]);
          }
          intsEnum.get(intsEnum.size() - 1);
          assertEquals(fp, in.getFilePointer());
          in.close();
        }
        d.close();
      }
    }
  }

  public void testEndPointer() throws IOException {
    final Directory dir = newDirectory();
    final int valueCount = RandomInts.randomIntBetween(random(), 1, 1000);
    final IndexOutput out = dir.createOutput("tests.bin", newIOContext(random()));
    for (int i = 0; i < valueCount; ++i) {
      out.writeLong(0);
    }
    out.close();
    final IndexInput in = dir.openInput("tests.bin", newIOContext(random()));
    for (int version = PackedInts.VERSION_START; version <= PackedInts.VERSION_CURRENT; ++version) {
      for (int bpv = 1; bpv <= 64; ++bpv) {
        for (PackedInts.Format format : PackedInts.Format.values()) {
          if (!format.isSupported(bpv)) {
            continue;
          }
          final long byteCount = format.byteCount(version, valueCount, bpv); 
          String msg = "format=" + format + ",version=" + version + ",valueCount=" + valueCount + ",bpv=" + bpv;

          // test iterator
          in.seek(0L);
          final PackedInts.ReaderIterator it = PackedInts.getReaderIteratorNoHeader(in, format, version, valueCount, bpv, RandomInts.randomIntBetween(random(), 1, 1<<16));
          for (int i = 0; i < valueCount; ++i) {
            it.next();
          }
          assertEquals(msg, byteCount, in.getFilePointer());

          // test direct reader
          in.seek(0L);
          final PackedInts.Reader directReader = PackedInts.getDirectReaderNoHeader(in, format, version, valueCount, bpv);
          directReader.get(valueCount - 1);
          assertEquals(msg, byteCount, in.getFilePointer());

          // test reader
          in.seek(0L);
          PackedInts.getReaderNoHeader(in, format, version, valueCount, bpv);
          assertEquals(msg, byteCount, in.getFilePointer());
         }
      }
    }
    in.close();
    dir.close();
  }

  public void testControlledEquality() {
    final int VALUE_COUNT = 255;
    final int BITS_PER_VALUE = 8;

    List<PackedInts.Mutable> packedInts =
            createPackedInts(VALUE_COUNT, BITS_PER_VALUE);
    for (PackedInts.Mutable packedInt: packedInts) {
      for (int i = 0 ; i < packedInt.size() ; i++) {
        packedInt.set(i, i+1);
      }
    }
    assertListEquality(packedInts);
  }

  public void testRandomBulkCopy() {
    final int numIters = atLeast(3);
    for(int iter=0;iter<numIters;iter++) {
      if (VERBOSE) {
        System.out.println("\nTEST: iter=" + iter);
      }
      final int valueCount = atLeast(100000);
      int bits1 = _TestUtil.nextInt(random(), 1, 64);
      int bits2 = _TestUtil.nextInt(random(), 1, 64);
      if (bits1 > bits2) {
        int tmp = bits1;
        bits1 = bits2;
        bits2 = tmp;
      }
      if (VERBOSE) {
        System.out.println("  valueCount=" + valueCount + " bits1=" + bits1 + " bits2=" + bits2);
      }

      final PackedInts.Mutable packed1 = PackedInts.getMutable(valueCount, bits1, PackedInts.COMPACT);
      final PackedInts.Mutable packed2 = PackedInts.getMutable(valueCount, bits2, PackedInts.COMPACT);

      final long maxValue = PackedInts.maxValue(bits1);
      for(int i=0;i<valueCount;i++) {
        final long val = _TestUtil.nextLong(random(), 0, maxValue);
        packed1.set(i, val);
        packed2.set(i, val);
      }

      final long[] buffer = new long[valueCount];

      // Copy random slice over, 20 times:
      for(int iter2=0;iter2<20;iter2++) {
        int start = random().nextInt(valueCount-1);
        int len = _TestUtil.nextInt(random(), 1, valueCount-start);
        int offset;
        if (VERBOSE) {
          System.out.println("  copy " + len + " values @ " + start);
        }
        if (len == valueCount) {
          offset = 0;
        } else {
          offset = random().nextInt(valueCount - len);
        }
        if (random().nextBoolean()) {
          int got = packed1.get(start, buffer, offset, len);
          assertTrue(got <= len);
          int sot = packed2.set(start, buffer, offset, got);
          assertTrue(sot <= got);
        } else {
          PackedInts.copy(packed1, offset, packed2, offset, len, random().nextInt(10 * len));
        }

        /*
        for(int i=0;i<valueCount;i++) {
          assertEquals("value " + i, packed1.get(i), packed2.get(i));
        }
        */
      }

      for(int i=0;i<valueCount;i++) {
        assertEquals("value " + i, packed1.get(i), packed2.get(i));
      }
    }
  }

  public void testRandomEquality() {
    final int numIters = atLeast(2);
    for (int i = 0; i < numIters; ++i) {
      final int valueCount = _TestUtil.nextInt(random(), 1, 300);

      for (int bitsPerValue = 1 ;
           bitsPerValue <= 64 ;
           bitsPerValue++) {
        assertRandomEquality(valueCount, bitsPerValue, random().nextLong());
      }
    }
  }

  private static void assertRandomEquality(int valueCount, int bitsPerValue, long randomSeed) {
    List<PackedInts.Mutable> packedInts = createPackedInts(valueCount, bitsPerValue);
    for (PackedInts.Mutable packedInt: packedInts) {
      try {
        fill(packedInt, PackedInts.maxValue(bitsPerValue), randomSeed);
      } catch (Exception e) {
        e.printStackTrace(System.err);
        fail(String.format(Locale.ROOT,
                "Exception while filling %s: valueCount=%d, bitsPerValue=%s",
                packedInt.getClass().getSimpleName(),
                valueCount, bitsPerValue));
      }
    }
    assertListEquality(packedInts);
  }

  private static List<PackedInts.Mutable> createPackedInts(
          int valueCount, int bitsPerValue) {
    List<PackedInts.Mutable> packedInts = new ArrayList<PackedInts.Mutable>();
    if (bitsPerValue <= 8) {
      packedInts.add(new Direct8(valueCount));
    }
    if (bitsPerValue <= 16) {
      packedInts.add(new Direct16(valueCount));
    }
    if (bitsPerValue <= 24 && valueCount <= Packed8ThreeBlocks.MAX_SIZE) {
      packedInts.add(new Packed8ThreeBlocks(valueCount));
    }
    if (bitsPerValue <= 32) {
      packedInts.add(new Direct32(valueCount));
    }
    if (bitsPerValue <= 48 && valueCount <= Packed16ThreeBlocks.MAX_SIZE) {
      packedInts.add(new Packed16ThreeBlocks(valueCount));
    }
    if (bitsPerValue <= 63) {
      packedInts.add(new Packed64(valueCount, bitsPerValue));
    }
    packedInts.add(new Direct64(valueCount));
    for (int bpv = bitsPerValue; bpv <= Packed64SingleBlock.MAX_SUPPORTED_BITS_PER_VALUE; ++bpv) {
      if (Packed64SingleBlock.isSupported(bpv)) {
        packedInts.add(Packed64SingleBlock.create(valueCount, bpv));
      }
    }
    return packedInts;
  }

  private static void fill(PackedInts.Mutable packedInt, long maxValue, long randomSeed) {
    Random rnd2 = new Random(randomSeed);
    for (int i = 0 ; i < packedInt.size() ; i++) {
      long value = _TestUtil.nextLong(rnd2, 0, maxValue);
      packedInt.set(i, value);
      assertEquals(String.format(Locale.ROOT,
              "The set/get of the value at index %d should match for %s",
              i, packedInt.getClass().getSimpleName()),
              value, packedInt.get(i));
    }
  }

  private static void assertListEquality(
          List<? extends PackedInts.Reader> packedInts) {
    assertListEquality("", packedInts);
  }

  private static void assertListEquality(
            String message, List<? extends PackedInts.Reader> packedInts) {
    if (packedInts.size() == 0) {
      return;
    }
    PackedInts.Reader base = packedInts.get(0);
    int valueCount = base.size();
    for (PackedInts.Reader packedInt: packedInts) {
      assertEquals(message + ". The number of values should be the same ",
              valueCount, packedInt.size());
    }
    for (int i = 0 ; i < valueCount ; i++) {
      for (int j = 1 ; j < packedInts.size() ; j++) {
        assertEquals(String.format(Locale.ROOT,
                "%s. The value at index %d should be the same for %s and %s",
                message, i, base.getClass().getSimpleName(),
                packedInts.get(j).getClass().getSimpleName()),
                base.get(i), packedInts.get(j).get(i));
      }
    }
  }

  public void testSingleValue() throws Exception {
    for (int bitsPerValue = 1; bitsPerValue <= 64; ++bitsPerValue) {
      Directory dir = newDirectory();
      IndexOutput out = dir.createOutput("out", newIOContext(random()));
      PackedInts.Writer w = PackedInts.getWriter(out, 1, bitsPerValue, PackedInts.DEFAULT);
      long value = 17L & PackedInts.maxValue(bitsPerValue);
      w.add(value);
      w.finish();
      final long end = out.getFilePointer();
      out.close();

      IndexInput in = dir.openInput("out", newIOContext(random()));
      Reader reader = PackedInts.getReader(in);
      String msg = "Impl=" + w.getClass().getSimpleName() + ", bitsPerValue=" + bitsPerValue;
      assertEquals(msg, 1, reader.size());
      assertEquals(msg, value, reader.get(0));
      assertEquals(msg, end, in.getFilePointer());
      in.close();

      dir.close();
    }
  }

  public void testSecondaryBlockChange() {
    PackedInts.Mutable mutable = new Packed64(26, 5);
    mutable.set(24, 31);
    assertEquals("The value #24 should be correct", 31, mutable.get(24));
    mutable.set(4, 16);
    assertEquals("The value #24 should remain unchanged", 31, mutable.get(24));
  }

  /*
    Check if the structures properly handle the case where
    index * bitsPerValue > Integer.MAX_VALUE
    
    NOTE: this test allocates 256 MB
   */
  @Ignore("See LUCENE-4488")
  public void testIntOverflow() {
    int INDEX = (int)Math.pow(2, 30)+1;
    int BITS = 2;

    Packed64 p64 = null;
    try {
      p64 = new Packed64(INDEX, BITS);
    } catch (OutOfMemoryError oome) {
      // This can easily happen: we're allocating a
      // long[] that needs 256-273 MB.  Heap is 512 MB,
      // but not all of that is available for large
      // objects ... empirical testing shows we only
      // have ~ 67 MB free.
    }
    if (p64 != null) {
      p64.set(INDEX-1, 1);
      assertEquals("The value at position " + (INDEX-1)
                   + " should be correct for Packed64", 1, p64.get(INDEX-1));
      p64 = null;
    }

    Packed64SingleBlock p64sb = null;
    try {
      p64sb = Packed64SingleBlock.create(INDEX, BITS);
    } catch (OutOfMemoryError oome) {
      // Ignore: see comment above
    }
    if (p64sb != null) {
      p64sb.set(INDEX - 1, 1);
      assertEquals("The value at position " + (INDEX-1)
          + " should be correct for " + p64sb.getClass().getSimpleName(),
          1, p64sb.get(INDEX-1));
    }

    int index = Integer.MAX_VALUE / 24 + 1;
    Packed8ThreeBlocks p8 = null;
    try {
      p8 = new Packed8ThreeBlocks(index);
    } catch (OutOfMemoryError oome) {
      // Ignore: see comment above
    }
    if (p8 != null) {
      p8.set(index - 1, 1);
      assertEquals("The value at position " + (index-1)
                   + " should be correct for Packed8ThreeBlocks", 1, p8.get(index-1));
      p8 = null;
    }

    index = Integer.MAX_VALUE / 48 + 1;
    Packed16ThreeBlocks p16 = null;
    try {
      p16 = new Packed16ThreeBlocks(index);
    } catch (OutOfMemoryError oome) {
      // Ignore: see comment above
    }
    if (p16 != null) {
      p16.set(index - 1, 1);
      assertEquals("The value at position " + (index-1)
                   + " should be correct for Packed16ThreeBlocks", 1, p16.get(index-1));
      p16 = null;
    }
  }

  public void testFill() {
    final int valueCount = 1111;
    final int from = random().nextInt(valueCount + 1);
    final int to = from + random().nextInt(valueCount + 1 - from);
    for (int bpv = 1; bpv <= 64; ++bpv) {
      final long val = _TestUtil.nextLong(random(), 0, PackedInts.maxValue(bpv));
      List<PackedInts.Mutable> packedInts = createPackedInts(valueCount, bpv);
      for (PackedInts.Mutable ints : packedInts) {
        String msg = ints.getClass().getSimpleName() + " bpv=" + bpv + ", from=" + from + ", to=" + to + ", val=" + val;
        ints.fill(0, ints.size(), 1);
        ints.fill(from, to, val);
        for (int i = 0; i < ints.size(); ++i) {
          if (i >= from && i < to) {
            assertEquals(msg + ", i=" + i, val, ints.get(i));
          } else {
            assertEquals(msg + ", i=" + i, 1, ints.get(i));
          }
        }
      }
    }
  }

  public void testBulkGet() {
    final int valueCount = 1111;
    final int index = random().nextInt(valueCount);
    final int len = _TestUtil.nextInt(random(), 1, valueCount * 2);
    final int off = random().nextInt(77);

    for (int bpv = 1; bpv <= 64; ++bpv) {
      long mask = PackedInts.maxValue(bpv);
      List<PackedInts.Mutable> packedInts = createPackedInts(valueCount, bpv);

      for (PackedInts.Mutable ints : packedInts) {
        for (int i = 0; i < ints.size(); ++i) {
          ints.set(i, (31L * i - 1099) & mask);
        }
        long[] arr = new long[off+len];

        String msg = ints.getClass().getSimpleName() + " valueCount=" + valueCount
            + ", index=" + index + ", len=" + len + ", off=" + off;
        final int gets = ints.get(index, arr, off, len);
        assertTrue(msg, gets > 0);
        assertTrue(msg, gets <= len);
        assertTrue(msg, gets <= ints.size() - index);

        for (int i = 0; i < arr.length; ++i) {
          String m = msg + ", i=" + i;
          if (i >= off && i < off + gets) {
            assertEquals(m, ints.get(i - off + index), arr[i]);
          } else {
            assertEquals(m, 0, arr[i]);
          }
        }
      }
    }
  }

  public void testBulkSet() {
    final int valueCount = 1111;
    final int index = random().nextInt(valueCount);
    final int len = _TestUtil.nextInt(random(), 1, valueCount * 2);
    final int off = random().nextInt(77);
    long[] arr = new long[off+len];

    for (int bpv = 1; bpv <= 64; ++bpv) {
      long mask = PackedInts.maxValue(bpv);
      List<PackedInts.Mutable> packedInts = createPackedInts(valueCount, bpv);
      for (int i = 0; i < arr.length; ++i) {
        arr[i] = (31L * i + 19) & mask;
      }

      for (PackedInts.Mutable ints : packedInts) {
        String msg = ints.getClass().getSimpleName() + " valueCount=" + valueCount
            + ", index=" + index + ", len=" + len + ", off=" + off;
        final int sets = ints.set(index, arr, off, len);
        assertTrue(msg, sets > 0);
        assertTrue(msg, sets <= len);

        for (int i = 0; i < ints.size(); ++i) {
          String m = msg + ", i=" + i;
          if (i >= index && i < index + sets) {
            assertEquals(m, arr[off - index + i], ints.get(i));
          } else {
            assertEquals(m, 0, ints.get(i));
          }
        }
      }
    }
  }

  public void testCopy() {
    final int valueCount = _TestUtil.nextInt(random(), 5, 600);
    final int off1 = random().nextInt(valueCount);
    final int off2 = random().nextInt(valueCount);
    final int len = random().nextInt(Math.min(valueCount - off1, valueCount - off2));
    final int mem = random().nextInt(1024);

    for (int bpv = 1; bpv <= 64; ++bpv) {
      long mask = PackedInts.maxValue(bpv);
      for (PackedInts.Mutable r1 : createPackedInts(valueCount, bpv)) {
        for (int i = 0; i < r1.size(); ++i) {
          r1.set(i, (31L * i - 1023) & mask);
        }
        for (PackedInts.Mutable r2 : createPackedInts(valueCount, bpv)) {
          String msg = "src=" + r1 + ", dest=" + r2 + ", srcPos=" + off1
              + ", destPos=" + off2 + ", len=" + len + ", mem=" + mem;
          PackedInts.copy(r1, off1, r2, off2, len, mem);
          for (int i = 0; i < r2.size(); ++i) {
            String m = msg + ", i=" + i;
            if (i >= off2 && i < off2 + len) {
              assertEquals(m, r1.get(i - off2 + off1), r2.get(i));
            } else {
              assertEquals(m, 0, r2.get(i));
            }
          }
        }
      }
    }
  }

  public void testGrowableWriter() {
    final int valueCount = 113 + random().nextInt(1111);
    GrowableWriter wrt = new GrowableWriter(1, valueCount, PackedInts.DEFAULT);
    wrt.set(4, 2);
    wrt.set(7, 10);
    wrt.set(valueCount - 10, 99);
    wrt.set(99, 999);
    wrt.set(valueCount - 1, 1 << 10);
    assertEquals(1 << 10, wrt.get(valueCount - 1));
    wrt.set(99, (1 << 23) - 1);
    assertEquals(1 << 10, wrt.get(valueCount - 1));
    wrt.set(1, Long.MAX_VALUE);
    assertEquals(1 << 10, wrt.get(valueCount - 1));
    assertEquals(Long.MAX_VALUE, wrt.get(1));
    assertEquals(2, wrt.get(4));
    assertEquals((1 << 23) - 1, wrt.get(99));
    assertEquals(10, wrt.get(7));
    assertEquals(99, wrt.get(valueCount - 10));
    assertEquals(1 << 10, wrt.get(valueCount - 1));
  }

  public void testSave() throws IOException {
    final int valueCount = _TestUtil.nextInt(random(), 1, 2048);
    for (int bpv = 1; bpv <= 64; ++bpv) {
      final int maxValue = (int) Math.min(PackedInts.maxValue(31), PackedInts.maxValue(bpv));
      final RAMDirectory directory = new RAMDirectory();
      List<PackedInts.Mutable> packedInts = createPackedInts(valueCount, bpv);
      for (PackedInts.Mutable mutable : packedInts) {
        for (int i = 0; i < mutable.size(); ++i) {
          mutable.set(i, random().nextInt(maxValue));
        }

        IndexOutput out = directory.createOutput("packed-ints.bin", IOContext.DEFAULT);
        mutable.save(out);
        out.close();

        IndexInput in = directory.openInput("packed-ints.bin", IOContext.DEFAULT);
        PackedInts.Reader reader = PackedInts.getReader(in);
        assertEquals(mutable.getBitsPerValue(), reader.getBitsPerValue());
        assertEquals(valueCount, reader.size());
        if (mutable instanceof Packed64SingleBlock) {
          // make sure that we used the right format so that the reader has
          // the same performance characteristics as the mutable that has been
          // serialized
          assertTrue(reader instanceof Packed64SingleBlock);
        } else {
          assertFalse(reader instanceof Packed64SingleBlock);
        }
        for (int i = 0; i < valueCount; ++i) {
          assertEquals(mutable.get(i), reader.get(i));
        }
        in.close();
        directory.deleteFile("packed-ints.bin");
      }
      directory.close();
    }
  }

  public void testEncodeDecode() {
    for (PackedInts.Format format : PackedInts.Format.values()) {
      for (int bpv = 1; bpv <= 64; ++bpv) {
        if (!format.isSupported(bpv)) {
          continue;
        }
        String msg = format + " " + bpv;

        final PackedInts.Encoder encoder = PackedInts.getEncoder(format, PackedInts.VERSION_CURRENT, bpv);
        final PackedInts.Decoder decoder = PackedInts.getDecoder(format, PackedInts.VERSION_CURRENT, bpv);
        final int blockCount = encoder.blockCount();
        final int valueCount = encoder.valueCount();
        assertEquals(blockCount, decoder.blockCount());
        assertEquals(valueCount, decoder.valueCount());

        final int iterations = random().nextInt(100);
        final int blocksOffset = random().nextInt(100);
        final int valuesOffset = random().nextInt(100);
        final int blocksOffset2 = random().nextInt(100);
        final int blocksLen = iterations * blockCount;

        // 1. generate random inputs
        final long[] blocks = new long[blocksOffset + blocksLen];
        for (int i = 0; i < blocks.length; ++i) {
          blocks[i] = random().nextLong();
          if (format == PackedInts.Format.PACKED_SINGLE_BLOCK && 64 % bpv != 0) {
            // clear highest bits for packed
            final int toClear = 64 % bpv;
            blocks[i] = (blocks[i] << toClear) >>> toClear;
          }
        }

        // 2. decode
        final long[] values = new long[valuesOffset + iterations * valueCount];
        decoder.decode(blocks, blocksOffset, values, valuesOffset, iterations);
        for (long value : values) {
          assertTrue(value <= PackedInts.maxValue(bpv));
        }
        // test decoding to int[]
        final int[] intValues;
        if (bpv <= 32) {
          intValues = new int[values.length];
          decoder.decode(blocks, blocksOffset, intValues, valuesOffset, iterations);
          assertTrue(equals(intValues, values));
        } else {
          intValues = null;
        }

        // 3. re-encode
        final long[] blocks2 = new long[blocksOffset2 + blocksLen];
        encoder.encode(values, valuesOffset, blocks2, blocksOffset2, iterations);
        assertArrayEquals(msg, Arrays.copyOfRange(blocks, blocksOffset, blocks.length),
            Arrays.copyOfRange(blocks2, blocksOffset2, blocks2.length));
        // test encoding from int[]
        if (bpv <= 32) {
          final long[] blocks3 = new long[blocks2.length];
          encoder.encode(intValues, valuesOffset, blocks3, blocksOffset2, iterations);
          assertArrayEquals(msg, blocks2, blocks3);
        }

        // 4. byte[] decoding
        final byte[] byteBlocks = new byte[8 * blocks.length];
        ByteBuffer.wrap(byteBlocks).asLongBuffer().put(blocks);
        final long[] values2 = new long[valuesOffset + iterations * valueCount];
        decoder.decode(byteBlocks, blocksOffset * 8, values2, valuesOffset, iterations);
        for (long value : values2) {
          assertTrue(msg, value <= PackedInts.maxValue(bpv));
        }
        assertArrayEquals(msg, values, values2);
        // test decoding to int[]
        if (bpv <= 32) {
          final int[] intValues2 = new int[values2.length];
          decoder.decode(byteBlocks, blocksOffset * 8, intValues2, valuesOffset, iterations);
          assertTrue(msg, equals(intValues2, values2));
        }

        // 5. byte[] encoding
        final byte[] blocks3 = new byte[8 * (blocksOffset2 + blocksLen)];
        encoder.encode(values, valuesOffset, blocks3, 8 * blocksOffset2, iterations);
        assertEquals(msg, LongBuffer.wrap(blocks2), ByteBuffer.wrap(blocks3).asLongBuffer());
        // test encoding from int[]
        if (bpv <= 32) {
          final byte[] blocks4 = new byte[blocks3.length];
          encoder.encode(intValues, valuesOffset, blocks4, 8 * blocksOffset2, iterations);
          assertArrayEquals(msg, blocks3, blocks4);
        }
      }
    }
  }

  private static boolean equals(int[] ints, long[] longs) {
    if (ints.length != longs.length) {
      return false;
    }
    for (int i = 0; i < ints.length; ++i) {
      if ((ints[i] & 0xFFFFFFFFL) != longs[i]) {
        return false;
      }
    }
    return true;
  }

  public void testAppendingLongBuffer() {
    final long[] arr = new long[RandomInts.randomIntBetween(random(), 1, 2000000)];
    for (int bpv : new int[] {0, 1, 63, 64, RandomInts.randomIntBetween(random(), 2, 61)}) {
      if (bpv == 0) {
        Arrays.fill(arr, random().nextLong());
      } else if (bpv == 64) {
        for (int i = 0; i < arr.length; ++i) {
          arr[i] = random().nextLong();
        }
      } else {
        final long minValue = _TestUtil.nextLong(random(), Long.MIN_VALUE, Long.MAX_VALUE - PackedInts.maxValue(bpv));
        for (int i = 0; i < arr.length; ++i) {
          arr[i] = minValue + random().nextLong() & PackedInts.maxValue(bpv); // _TestUtil.nextLong is too slow
        }
      }
      AppendingLongBuffer buf = new AppendingLongBuffer();
      for (int i = 0; i < arr.length; ++i) {
        buf.add(arr[i]);
      }
      assertEquals(arr.length, buf.size());
      final AppendingLongBuffer.Iterator it = buf.iterator();
      for (int i = 0; i < arr.length; ++i) {
        if (random().nextBoolean()) {
          assertTrue(it.hasNext());
        }
        assertEquals(arr[i], it.next());
      }
      assertFalse(it.hasNext());

      final long expectedBytesUsed = RamUsageEstimator.sizeOf(buf);
      final long computedBytesUsed = buf.ramBytesUsed();
      assertEquals("got " + computedBytesUsed + ", expected: " + expectedBytesUsed,
          expectedBytesUsed, computedBytesUsed);
    }
  }

}
