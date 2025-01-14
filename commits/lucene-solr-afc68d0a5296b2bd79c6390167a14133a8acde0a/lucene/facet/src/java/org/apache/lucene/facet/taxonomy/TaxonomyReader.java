package org.apache.lucene.facet.taxonomy;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.store.AlreadyClosedException;

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

/**
 * TaxonomyReader is the read-only interface with which the faceted-search
 * library uses the taxonomy during search time.
 * <P>
 * A TaxonomyReader holds a list of categories. Each category has a serial
 * number which we call an "ordinal", and a hierarchical "path" name:
 * <UL>
 * <LI>
 * The ordinal is an integer that starts at 0 for the first category (which is
 * always the root category), and grows contiguously as more categories are
 * added; Note that once a category is added, it can never be deleted.
 * <LI>
 * The path is a CategoryPath object specifying the category's position in the
 * hierarchy.
 * </UL>
 * <B>Notes about concurrent access to the taxonomy:</B>
 * <P>
 * An implementation must allow multiple readers to be active concurrently
 * with a single writer. Readers follow so-called "point in time" semantics,
 * i.e., a TaxonomyReader object will only see taxonomy entries which were
 * available at the time it was created. What the writer writes is only
 * available to (new) readers after the writer's commit() is called.
 * <P>
 * In faceted search, two separate indices are used: the main Lucene index,
 * and the taxonomy. Because the main index refers to the categories listed
 * in the taxonomy, it is important to open the taxonomy *after* opening the
 * main index, and it is also necessary to reopen() the taxonomy after
 * reopen()ing the main index.
 * <P>
 * This order is important, otherwise it would be possible for the main index
 * to refer to a category which is not yet visible in the old snapshot of
 * the taxonomy. Note that it is indeed fine for the the taxonomy to be opened
 * after the main index - even a long time after. The reason is that once
 * a category is added to the taxonomy, it can never be changed or deleted,
 * so there is no danger that a "too new" taxonomy not being consistent with
 * an older index.
 * 
 * @lucene.experimental
 */
public abstract class TaxonomyReader implements Closeable {
  
  /**
   * The root category (the category with the empty path) always has the ordinal
   * 0, to which we give a name ROOT_ORDINAL. {@link #getOrdinal(CategoryPath)}
   * of an empty path will always return {@code ROOT_ORDINAL}, and
   * {@link #getPath(int)} with {@code ROOT_ORDINAL} will return the empty path.
   */
  public final static int ROOT_ORDINAL = 0;
  
  /**
   * Ordinals are always non-negative, so a negative ordinal can be used to
   * signify an error. Methods here return INVALID_ORDINAL (-1) in this case.
   */
  public final static int INVALID_ORDINAL = -1;
  
  /**
   * If the taxonomy has changed since the provided reader was opened, open and
   * return a new {@link TaxonomyReader}; else, return {@code null}. The new
   * reader, if not {@code null}, will be the same type of reader as the one
   * given to this method.
   * 
   * <p>
   * This method is typically far less costly than opening a fully new
   * {@link TaxonomyReader} as it shares resources with the provided
   * {@link TaxonomyReader}, when possible.
   */
  public static <T extends TaxonomyReader> T openIfChanged(T oldTaxoReader) throws IOException {
    @SuppressWarnings("unchecked")
    final T newTaxoReader = (T) oldTaxoReader.doOpenIfChanged();
    assert newTaxoReader != oldTaxoReader;
    return newTaxoReader;
  }

  private volatile boolean closed = false;

  // set refCount to 1 at start
  private final AtomicInteger refCount = new AtomicInteger(1);
  
  /**
   * performs the actual task of closing the resources that are used by the
   * taxonomy reader.
   */
  protected abstract void doClose() throws IOException;
  
  /**
   * Implements the actual opening of a new {@link TaxonomyReader} instance if
   * the taxonomy has changed.
   * 
   * @see #openIfChanged(TaxonomyReader)
   */
  protected abstract TaxonomyReader doOpenIfChanged() throws IOException;
  
  /**
   * @throws AlreadyClosedException if this IndexReader is closed
   */
  protected final void ensureOpen() throws AlreadyClosedException {
    if (getRefCount() <= 0) {
      throw new AlreadyClosedException("this TaxonomyReader is closed");
    }
  }

  @Override
  public final void close() throws IOException {
    if (!closed) {
      synchronized (this) {
        if (!closed) {
          decRef();
          closed = true;
        }
      }
    }
  }
  
  /**
   * Expert: decreases the refCount of this TaxonomyReader instance. If the
   * refCount drops to 0 this taxonomy reader is closed.
   */
  public final void decRef() throws IOException {
    ensureOpen();
    final int rc = refCount.decrementAndGet();
    if (rc == 0) {
      boolean success = false;
      try {
        doClose();
        closed = true;
        success = true;
      } finally {
        if (!success) {
          // Put reference back on failure
          refCount.incrementAndGet();
        }
      }
    } else if (rc < 0) {
      throw new IllegalStateException("too many decRef calls: refCount is " + rc + " after decrement");
    }
  }
  
  /**
   * Returns a {@link ChildrenArrays} object which can be used together to
   * efficiently enumerate the children of any category.
   * <p>
   * The caller can hold on to the object it got indefinitely - it is guaranteed
   * that no-one else will modify it. The other side of the same coin is that
   * the caller must treat the object which it got (and the arrays it contains)
   * as read-only and <b>not modify it</b>, because other callers might have
   * gotten the same object too.
   */
  public abstract ChildrenArrays getChildrenArrays() throws IOException;
  
  /**
   * Retrieve user committed data.
   * 
   * @see TaxonomyWriter#setCommitData(Map)
   */
  public abstract Map<String, String> getCommitUserData() throws IOException;
  
  /**
   * Returns the ordinal of the category given as a path. The ordinal is the
   * category's serial number, an integer which starts with 0 and grows as more
   * categories are added (note that once a category is added, it can never be
   * deleted).
   * 
   * @return the category's ordinal or {@link #INVALID_ORDINAL} if the category
   *         wasn't foun.
   */
  public abstract int getOrdinal(CategoryPath categoryPath) throws IOException;
  
  /**
   * Returns the ordinal of the parent category of the category with the given
   * ordinal, according to the following rules:
   * 
   * 
   * <ul>
   * <li>If the given ordinal is the {@link #ROOT_ORDINAL}, an
   * {@link #INVALID_ORDINAL} is returned.
   * <li>If the given ordinal is a top-level category, the {@link #ROOT_ORDINAL}
   * is returned.
   * <li>If the given ordinal is an existing category, returns the ordinal of
   * its parent
   * </ul>
   * 
   * @throws ArrayIndexOutOfBoundsException
   *           if an invalid ordinal is given (negative or beyond the last
   *           available ordinal)
   */
  public abstract int getParent(int ordinal) throws IOException;
  
  /**
   * Returns an {@code int[]} the size of the taxonomy listing the ordinal of
   * the parent category of each category in the taxonomy.
   * <p>
   * The caller can hold on to the array it got indefinitely - it is guaranteed
   * that no-one else will modify it. The other side of the same coin is that
   * the caller must treat the array it got as read-only and <b>not modify
   * it</b>, because other callers might have gotten the same array too (and
   * getParent() calls might be answered from the same array).
   */
  public abstract int[] getParentArray() throws IOException;
  
  /**
   * Returns the path name of the category with the given ordinal. The path is
   * returned as a new CategoryPath object - to reuse an existing object, use
   * {@link #getPath(int, CategoryPath)}.
   * 
   * @return a {@link CategoryPath} with the required path, or {@code null} if
   *         the given ordinal is unknown to the taxonomy.
   */
  public abstract CategoryPath getPath(int ordinal) throws IOException;
  
  /**
   * Same as {@link #getPath(int)}, only reuses the given {@link CategoryPath}
   * instances.
   */
  public abstract boolean getPath(int ordinal, CategoryPath result) throws IOException;

  /** Returns the current refCount for this taxonomy reader. */
  public final int getRefCount() {
    return refCount.get();
  }
  
  /**
   * Returns the number of categories in the taxonomy. Note that the number of
   * categories returned is often slightly higher than the number of categories
   * inserted into the taxonomy; This is because when a category is added to the
   * taxonomy, its ancestors are also added automatically (including the root,
   * which always get ordinal 0).
   */
  public abstract int getSize();
  
  /**
   * Expert: increments the refCount of this TaxonomyReader instance. RefCounts
   * can be used to determine when a taxonomy reader can be closed safely, i.e.
   * as soon as there are no more references. Be sure to always call a
   * corresponding decRef(), in a finally clause; otherwise the reader may never
   * be closed.
   */
  public final void incRef() {
    ensureOpen();
    refCount.incrementAndGet();
  }

}
