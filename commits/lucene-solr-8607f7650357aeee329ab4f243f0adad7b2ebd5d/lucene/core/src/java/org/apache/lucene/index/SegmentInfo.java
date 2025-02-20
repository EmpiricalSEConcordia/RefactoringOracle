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


import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.TrackingDirectoryWrapper;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

/**
 * Information about a segment such as its name, directory, and files related
 * to the segment.
 *
 * @lucene.experimental
 */
public final class SegmentInfo {
  
  // TODO: remove these from this class, for now this is the representation
  /** Used by some member fields to mean not present (e.g.,
   *  norms, deletions). */
  public static final int NO = -1;          // e.g. no norms; no deletes;

  /** Used by some member fields to mean present (e.g.,
   *  norms, deletions). */
  public static final int YES = 1;          // e.g. have norms; have deletes;

  /** Unique segment name in the directory. */
  public final String name;

  private int docCount;         // number of docs in seg

  /** Where this segment resides. */
  public final Directory dir;

  private boolean isCompoundFile;

  /** Id that uniquely identifies this segment. */
  private final byte[] id;

  private Codec codec;

  private Map<String,String> diagnostics;
  
  private Map<String,String> attributes;

  // Tracks the Lucene version this segment was created with, since 3.1. Null
  // indicates an older than 3.0 index, and it's used to detect a too old index.
  // The format expected is "x.y" - "2.x" for pre-3.0 indexes (or null), and
  // specific versions afterwards ("3.0.0", "3.1.0" etc.).
  // see o.a.l.util.Version.
  private Version version;

  void setDiagnostics(Map<String, String> diagnostics) {
    this.diagnostics = diagnostics;
  }

  /** Returns diagnostics saved into the segment when it was
   *  written. */
  public Map<String, String> getDiagnostics() {
    return diagnostics;
  }

  /**
   * Construct a new complete SegmentInfo instance from input.
   * <p>Note: this is public only to allow access from
   * the codecs package.</p>
   */
  public SegmentInfo(Directory dir, Version version, String name, int docCount,
                     boolean isCompoundFile, Codec codec, Map<String,String> diagnostics,
                     byte[] id, Map<String,String> attributes) {
    assert !(dir instanceof TrackingDirectoryWrapper);
    this.dir = dir;
    this.version = version;
    this.name = name;
    this.docCount = docCount;
    this.isCompoundFile = isCompoundFile;
    this.codec = codec;
    this.diagnostics = diagnostics;
    this.id = id;
    if (id.length != StringHelper.ID_LENGTH) {
      throw new IllegalArgumentException("invalid id: " + Arrays.toString(id));
    }
    this.attributes = Objects.requireNonNull(attributes);
  }

  /**
   * Mark whether this segment is stored as a compound file.
   *
   * @param isCompoundFile true if this is a compound file;
   * else, false
   */
  void setUseCompoundFile(boolean isCompoundFile) {
    this.isCompoundFile = isCompoundFile;
  }
  
  /**
   * Returns true if this segment is stored as a compound
   * file; else, false.
   */
  public boolean getUseCompoundFile() {
    return isCompoundFile;
  }

  /** Can only be called once. */
  public void setCodec(Codec codec) {
    assert this.codec == null;
    if (codec == null) {
      throw new IllegalArgumentException("codec must be non-null");
    }
    this.codec = codec;
  }

  /** Return {@link Codec} that wrote this segment. */
  public Codec getCodec() {
    return codec;
  }

  /** Returns number of documents in this segment (deletions
   *  are not taken into account). */
  public int getDocCount() {
    if (this.docCount == -1) {
      throw new IllegalStateException("docCount isn't set yet");
    }
    return docCount;
  }

  // NOTE: leave package private
  void setDocCount(int docCount) {
    if (this.docCount != -1) {
      throw new IllegalStateException("docCount was already set: this.docCount=" + this.docCount + " vs docCount=" + docCount);
    }
    this.docCount = docCount;
  }

  /** Return all files referenced by this SegmentInfo. */
  public Set<String> files() {
    if (setFiles == null) {
      throw new IllegalStateException("files were not computed yet");
    }
    return Collections.unmodifiableSet(setFiles);
  }

  @Override
  public String toString() {
    return toString(0);
  }
  
  
  /**
   * Used for debugging.
   * 
   * @deprecated Use {@link #toString(int)} instead.
   */
  @Deprecated
  public String toString(Directory dir, int delCount) {
    return toString(delCount);
  }

  /** Used for debugging.  Format may suddenly change.
   *
   *  <p>Current format looks like
   *  <code>_a(3.1):c45/4</code>, which means the segment's
   *  name is <code>_a</code>; it was created with Lucene 3.1 (or
   *  '?' if it's unknown); it's using compound file
   *  format (would be <code>C</code> if not compound); it
   *  has 45 documents; it has 4 deletions (this part is
   *  left off when there are no deletions).</p>
   */
  public String toString(int delCount) {
    StringBuilder s = new StringBuilder();
    s.append(name).append('(').append(version == null ? "?" : version).append(')').append(':');
    char cfs = getUseCompoundFile() ? 'c' : 'C';
    s.append(cfs);

    s.append(docCount);

    if (delCount != 0) {
      s.append('/').append(delCount);
    }

    // TODO: we could append toString of attributes() here?

    return s.toString();
  }

  /** We consider another SegmentInfo instance equal if it
   *  has the same dir and same name. */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj instanceof SegmentInfo) {
      final SegmentInfo other = (SegmentInfo) obj;
      return other.dir == dir && other.name.equals(name);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return dir.hashCode() + name.hashCode();
  }

  /** Returns the version of the code which wrote the segment.
   */
  public Version getVersion() {
    return version;
  }

  /** Return the id that uniquely identifies this segment. */
  public byte[] getId() {
    return id.clone();
  }

  private Set<String> setFiles;

  /** Sets the files written for this segment. */
  public void setFiles(Collection<String> files) {
    setFiles = new HashSet<>();
    addFiles(files);
  }

  /** Add these files to the set of files written for this
   *  segment. */
  public void addFiles(Collection<String> files) {
    checkFileNames(files);
    for (String f : files) {
      setFiles.add(namedForThisSegment(f));
    }
  }

  /** Add this file to the set of files written for this
   *  segment. */
  public void addFile(String file) {
    checkFileNames(Collections.singleton(file));
    setFiles.add(namedForThisSegment(file));
  }
  
  private void checkFileNames(Collection<String> files) {
    Matcher m = IndexFileNames.CODEC_FILE_PATTERN.matcher("");
    for (String file : files) {
      m.reset(file);
      if (!m.matches()) {
        throw new IllegalArgumentException("invalid codec filename '" + file + "', must match: " + IndexFileNames.CODEC_FILE_PATTERN.pattern());
      }
    }
  }
  
  /** 
   * strips any segment name from the file, naming it with this segment
   * this is because "segment names" can change, e.g. by addIndexes(Dir)
   */
  String namedForThisSegment(String file) {
    return name + IndexFileNames.stripSegmentName(file);
  }
  
  /**
   * Get a codec attribute value, or null if it does not exist
   */
  public String getAttribute(String key) {
    return attributes.get(key);
  }
  
  /**
   * Puts a codec attribute value.
   * <p>
   * This is a key-value mapping for the field that the codec can use to store
   * additional metadata, and will be available to the codec when reading the
   * segment via {@link #getAttribute(String)}
   * <p>
   * If a value already exists for the field, it will be replaced with the new
   * value.
   */
  public String putAttribute(String key, String value) {
    return attributes.put(key, value);
  }
  
  /**
   * Returns the internal codec attributes map.
   * @return internal codec attributes map.
   */
  public Map<String,String> getAttributes() {
    return attributes;
  }
}

