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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.CodecUtil;
import org.apache.lucene.util.StringHelper;

/** Access to the Fieldable Info file that describes document fields and whether or
 *  not they are indexed. Each segment has a separate Fieldable Info file. Objects
 *  of this class are thread-safe for multiple readers, but only one thread can
 *  be adding documents at a time, with no other reader or writer threads
 *  accessing this object.
 *  @lucene.experimental
 */
public final class FieldInfos implements Iterable<FieldInfo> {
  static final class FieldNumberBiMap {
    
    final static String CODEC_NAME = "GLOBAL_FIELD_MAP";
    
    // Initial format
    private static final int VERSION_START = 0;

    private static final int VERSION_CURRENT = VERSION_START;

    private final Map<Integer,String> numberToName;
    private final Map<String,Integer> nameToNumber;
    private int lowestUnassignedFieldNumber = -1;
    private long lastVersion = 0;
    private long version = 0;
    
    FieldNumberBiMap() {
      this.nameToNumber = new HashMap<String, Integer>();
      this.numberToName = new HashMap<Integer, String>();
    }
    
    /**
     * Returns the global field number for the given field name. If the name
     * does not exist yet it tries to add it with the given preferred field
     * number assigned if possible otherwise the first unassigned field number
     * is used as the field number.
     */
    synchronized int addOrGet(String fieldName, int preferredFieldNumber) {
      Integer fieldNumber = nameToNumber.get(fieldName);
      if (fieldNumber == null) {
        final Integer preferredBoxed = Integer.valueOf(preferredFieldNumber);

        if (preferredFieldNumber != -1 && !numberToName.containsKey(preferredBoxed)) {
            // cool - we can use this number globally
            fieldNumber = preferredBoxed;
        } else {
          // find a new FieldNumber
          while (numberToName.containsKey(++lowestUnassignedFieldNumber)) {
            // might not be up to date - lets do the work once needed
          }
          fieldNumber = lowestUnassignedFieldNumber;
        }
        
        version++;
        numberToName.put(fieldNumber, fieldName);
        nameToNumber.put(fieldName, fieldNumber);
        
      }

      return fieldNumber.intValue();
    }

    /**
     * Sets the given field number and name if not yet set. 
     */
    synchronized void setIfNotSet(int fieldNumber, String fieldName) {
      final Integer boxedFieldNumber = Integer.valueOf(fieldNumber);
      if (!numberToName.containsKey(boxedFieldNumber)
          && !nameToNumber.containsKey(fieldName)) {
        version++;
        numberToName.put(boxedFieldNumber, fieldName);
        nameToNumber.put(fieldName, boxedFieldNumber);
      } else {
        assert containsConsistent(boxedFieldNumber, fieldName);
      }
    }
    
    /**
     * Writes this {@link FieldNumberBiMap} to the given output and returns its
     * version.
     */
    public synchronized long write(IndexOutput output) throws IOException{
      Set<Entry<String, Integer>> entrySet = nameToNumber.entrySet();
      CodecUtil.writeHeader(output, CODEC_NAME, VERSION_CURRENT); 
      output.writeVInt(entrySet.size());
      for (Entry<String, Integer> entry : entrySet) {
        output.writeVInt(entry.getValue().intValue());
        output.writeString(entry.getKey());
      }
      return version;
    }

    /**
     * Reads the {@link FieldNumberBiMap} from the given input and resets the
     * version to 0.
     */
    public synchronized void read(IndexInput input) throws IOException{
      CodecUtil.checkHeader(input, CODEC_NAME,
          VERSION_START,
          VERSION_CURRENT);
      final int size = input.readVInt();
      for (int i = 0; i < size; i++) {
        final int num = input.readVInt();
        final String name = input.readString();
        setIfNotSet(num, name);
      }
      version = lastVersion = 0;
    }
    
    /**
     * Returns a new {@link FieldInfos} instance with this as the global field
     * map
     * 
     * @return a new {@link FieldInfos} instance with this as the global field
     *         map
     */
    public FieldInfos newFieldInfos() {
      return new FieldInfos(this);
    }

    /**
     * Returns <code>true</code> iff the last committed version differs from the
     * current version, otherwise <code>false</code>
     * 
     * @return <code>true</code> iff the last committed version differs from the
     *         current version, otherwise <code>false</code>
     */
    public synchronized boolean isDirty() {
      return lastVersion != version;
    }
    
    /**
     * commits the given version if the given version is greater than the previous committed version
     * 
     * @param version
     *          the version to commit
     * @return <code>true</code> iff the version was successfully committed otherwise <code>false</code>
     * @see #write(IndexOutput)
     */
    public synchronized boolean commitLastVersion(long version) {
      if (version > lastVersion) {
        lastVersion = version;
        return true;
      }
      return false;
    }
    
    // just for testing
    Set<Entry<String, Integer>> entries() {
      return new HashSet<Entry<String, Integer>>(nameToNumber.entrySet());
    }
    
    // used by assert
    boolean containsConsistent(Integer number, String name) {
      return name.equals(numberToName.get(number))
          && number.equals(nameToNumber.get(name));
    }
  }
  
  private final SortedMap<Integer,FieldInfo> byNumber = new TreeMap<Integer,FieldInfo>();
  private final HashMap<String,FieldInfo> byName = new HashMap<String,FieldInfo>();
  private final FieldNumberBiMap globalFieldNumbers;
  
  // First used in 2.9; prior to 2.9 there was no format header
  public static final int FORMAT_START = -2;
  public static final int FORMAT_PER_FIELD_CODEC = -3;

  // whenever you add a new format, make it 1 smaller (negative version logic)!
  static final int FORMAT_CURRENT = FORMAT_PER_FIELD_CODEC;
  
  static final int FORMAT_MINIMUM = FORMAT_START;
  
  static final byte IS_INDEXED = 0x1;
  static final byte STORE_TERMVECTOR = 0x2;
  static final byte STORE_POSITIONS_WITH_TERMVECTOR = 0x4;
  static final byte STORE_OFFSET_WITH_TERMVECTOR = 0x8;
  static final byte OMIT_NORMS = 0x10;
  static final byte STORE_PAYLOADS = 0x20;
  static final byte OMIT_TERM_FREQ_AND_POSITIONS = 0x40;

  private int format;

  public FieldInfos() {
    this(new FieldNumberBiMap());
  }
  
  FieldInfos(FieldInfos other) {
    this(other.globalFieldNumbers);
  }

  FieldInfos(FieldNumberBiMap globalFieldNumbers) {
    this.globalFieldNumbers = globalFieldNumbers;
  }

  /**
   * Construct a FieldInfos object using the directory and the name of the file
   * IndexInput
   * @param d The directory to open the IndexInput from
   * @param name The name of the file to open the IndexInput from in the Directory
   * @throws IOException
   */
  public FieldInfos(Directory d, String name) throws IOException {
    this(new FieldNumberBiMap());
    /*
     * TODO: in the read case we create a FNBM for each FIs which is a wast of resources.
     * Yet, we must not seed this with a global map since due to addIndexes(Dir) we could
     * have non-matching field numbers. we should use a null FNBM here and set the FIs 
     * to READ-ONLY once this ctor is done. Each modificator should check if we are readonly
     * with an assert
     */
    IndexInput input = d.openInput(name);
    try {
      read(input, name);
    } finally {
      input.close();
    }
  }
  
  /**
   * adds the given field to this FieldInfos name / number mapping. The given FI
   * must be present in the global field number mapping before this method it
   * called
   */
  private void putInternal(FieldInfo fi) {
    assert !byNumber.containsKey(fi.number);
    assert !byName.containsKey(fi.name);
    assert globalFieldNumbers.containsConsistent(Integer.valueOf(fi.number), fi.name);
    byNumber.put(fi.number, fi);
    byName.put(fi.name, fi);
  }
  
  private int nextFieldNumber(String name, int preferredFieldNumber) {
    // get a global number for this field
    final int fieldNumber = globalFieldNumbers.addOrGet(name,
        preferredFieldNumber);
    assert byNumber.get(fieldNumber) == null : "field number " + fieldNumber
        + " already taken";
    return fieldNumber;
  }

  /**
   * Returns a deep clone of this FieldInfos instance.
   */
  @Override
  synchronized public Object clone() {
    FieldInfos fis = new FieldInfos(globalFieldNumbers);
    for (FieldInfo fi : this) {
      FieldInfo clone = (FieldInfo) (fi).clone();
      fis.putInternal(clone);
    }
    return fis;
  }

  /** Returns true if any fields do not omitTermFreqAndPositions */
  public boolean hasProx() {
    for (FieldInfo fi : this) {
      if (fi.isIndexed && !fi.omitTermFreqAndPositions) {
        return true;
      }
    }
    return false;
  }
  
  /**
   * Add fields that are indexed. Whether they have termvectors has to be specified.
   * 
   * @param names The names of the fields
   * @param storeTermVectors Whether the fields store term vectors or not
   * @param storePositionWithTermVector true if positions should be stored.
   * @param storeOffsetWithTermVector true if offsets should be stored
   */
  synchronized public void addIndexed(Collection<String> names, boolean storeTermVectors, boolean storePositionWithTermVector, 
                         boolean storeOffsetWithTermVector) {
    for (String name : names) {
      add(name, true, storeTermVectors, storePositionWithTermVector, storeOffsetWithTermVector);
    }
  }

  /**
   * Assumes the fields are not storing term vectors.
   * 
   * @param names The names of the fields
   * @param isIndexed Whether the fields are indexed or not
   * 
   * @see #add(String, boolean)
   */
  synchronized public void add(Collection<String> names, boolean isIndexed) {
    for (String name : names) {
      add(name, isIndexed);
    }
  }

  /**
   * Calls 5 parameter add with false for all TermVector parameters.
   * 
   * @param name The name of the Fieldable
   * @param isIndexed true if the field is indexed
   * @see #add(String, boolean, boolean, boolean, boolean)
   */
  synchronized public void add(String name, boolean isIndexed) {
    add(name, isIndexed, false, false, false, false);
  }

  /**
   * Calls 5 parameter add with false for term vector positions and offsets.
   * 
   * @param name The name of the field
   * @param isIndexed  true if the field is indexed
   * @param storeTermVector true if the term vector should be stored
   */
  synchronized public void add(String name, boolean isIndexed, boolean storeTermVector){
    add(name, isIndexed, storeTermVector, false, false, false);
  }
  
  /** If the field is not yet known, adds it. If it is known, checks to make
   *  sure that the isIndexed flag is the same as was given previously for this
   *  field. If not - marks it as being indexed.  Same goes for the TermVector
   * parameters.
   * 
   * @param name The name of the field
   * @param isIndexed true if the field is indexed
   * @param storeTermVector true if the term vector should be stored
   * @param storePositionWithTermVector true if the term vector with positions should be stored
   * @param storeOffsetWithTermVector true if the term vector with offsets should be stored
   */
  synchronized public void add(String name, boolean isIndexed, boolean storeTermVector,
                  boolean storePositionWithTermVector, boolean storeOffsetWithTermVector) {

    add(name, isIndexed, storeTermVector, storePositionWithTermVector, storeOffsetWithTermVector, false);
  }

    /** If the field is not yet known, adds it. If it is known, checks to make
   *  sure that the isIndexed flag is the same as was given previously for this
   *  field. If not - marks it as being indexed.  Same goes for the TermVector
   * parameters.
   *
   * @param name The name of the field
   * @param isIndexed true if the field is indexed
   * @param storeTermVector true if the term vector should be stored
   * @param storePositionWithTermVector true if the term vector with positions should be stored
   * @param storeOffsetWithTermVector true if the term vector with offsets should be stored
   * @param omitNorms true if the norms for the indexed field should be omitted
   */
  synchronized public void add(String name, boolean isIndexed, boolean storeTermVector,
                  boolean storePositionWithTermVector, boolean storeOffsetWithTermVector, boolean omitNorms) {
    add(name, isIndexed, storeTermVector, storePositionWithTermVector,
        storeOffsetWithTermVector, omitNorms, false, false);
  }
  
  /** If the field is not yet known, adds it. If it is known, checks to make
   *  sure that the isIndexed flag is the same as was given previously for this
   *  field. If not - marks it as being indexed.  Same goes for the TermVector
   * parameters.
   *
   * @param name The name of the field
   * @param isIndexed true if the field is indexed
   * @param storeTermVector true if the term vector should be stored
   * @param storePositionWithTermVector true if the term vector with positions should be stored
   * @param storeOffsetWithTermVector true if the term vector with offsets should be stored
   * @param omitNorms true if the norms for the indexed field should be omitted
   * @param storePayloads true if payloads should be stored for this field
   * @param omitTermFreqAndPositions true if term freqs should be omitted for this field
   */
  synchronized public FieldInfo add(String name, boolean isIndexed, boolean storeTermVector,
                       boolean storePositionWithTermVector, boolean storeOffsetWithTermVector,
                       boolean omitNorms, boolean storePayloads, boolean omitTermFreqAndPositions) {
    return addOrUpdateInternal(name, -1, isIndexed, storeTermVector, storePositionWithTermVector,
                               storeOffsetWithTermVector, omitNorms, storePayloads, omitTermFreqAndPositions);
  }

  synchronized private FieldInfo addOrUpdateInternal(String name, int preferredFieldNumber, boolean isIndexed,
      boolean storeTermVector, boolean storePositionWithTermVector, boolean storeOffsetWithTermVector,
      boolean omitNorms, boolean storePayloads, boolean omitTermFreqAndPositions) {

    FieldInfo fi = fieldInfo(name);
    if (fi == null) {
      int fieldNumber = nextFieldNumber(name, preferredFieldNumber);
      return addInternal(name, fieldNumber, isIndexed, storeTermVector, storePositionWithTermVector, storeOffsetWithTermVector, omitNorms, storePayloads, omitTermFreqAndPositions);
    } else {
      fi.update(isIndexed, storeTermVector, storePositionWithTermVector, storeOffsetWithTermVector, omitNorms, storePayloads, omitTermFreqAndPositions);
    }
    return fi;
  }

  synchronized public FieldInfo add(FieldInfo fi) {
    // IMPORTANT - reuse the field number if possible for consistent field numbers across segments
    return addOrUpdateInternal(fi.name, fi.number, fi.isIndexed, fi.storeTermVector,
               fi.storePositionWithTermVector, fi.storeOffsetWithTermVector,
               fi.omitNorms, fi.storePayloads,
               fi.omitTermFreqAndPositions);
  }

  private FieldInfo addInternal(String name, int fieldNumber, boolean isIndexed,
                                boolean storeTermVector, boolean storePositionWithTermVector, 
                                boolean storeOffsetWithTermVector, boolean omitNorms, boolean storePayloads, boolean omitTermFreqAndPositions) {
    name = StringHelper.intern(name);
    globalFieldNumbers.setIfNotSet(fieldNumber, name);
    FieldInfo fi = new FieldInfo(name, isIndexed, fieldNumber, storeTermVector, storePositionWithTermVector,
                                 storeOffsetWithTermVector, omitNorms, storePayloads, omitTermFreqAndPositions);

    assert byNumber.get(fi.number) == null;
    putInternal(fi);
    return fi;
  }

  public int fieldNumber(String fieldName) {
    FieldInfo fi = fieldInfo(fieldName);
    return (fi != null) ? fi.number : -1;
  }

  public FieldInfo fieldInfo(String fieldName) {
    return byName.get(fieldName);
  }

  /**
   * Return the fieldName identified by its number.
   * 
   * @param fieldNumber
   * @return the fieldName or an empty string when the field
   * with the given number doesn't exist.
   */  
  public String fieldName(int fieldNumber) {
	FieldInfo fi = fieldInfo(fieldNumber);
	return (fi != null) ? fi.name : "";
  }

  /**
   * Return the fieldinfo object referenced by the fieldNumber.
   * @param fieldNumber
   * @return the FieldInfo object or null when the given fieldNumber
   * doesn't exist.
   */  
  public FieldInfo fieldInfo(int fieldNumber) {
	return (fieldNumber >= 0) ? byNumber.get(fieldNumber) : null;
  }

  public Iterator<FieldInfo> iterator() {
    return byNumber.values().iterator();
  }

  public int size() {
    assert byNumber.size() == byName.size();
    return byNumber.size();
  }

  public boolean hasVectors() {
    for (FieldInfo fi : this) {
      if (fi.storeTermVector) {
        return true;
      }
    }
    return false;
  }

  public boolean hasNorms() {
    for (FieldInfo fi : this) {
      if (!fi.omitNorms) {
        return true;
      }
    }
    return false;
  }

  public void write(Directory d, String name) throws IOException {
    IndexOutput output = d.createOutput(name);
    try {
      write(output);
    } finally {
      output.close();
    }
  }

  public void write(IndexOutput output) throws IOException {
    output.writeVInt(FORMAT_CURRENT);
    output.writeVInt(size());
    for (FieldInfo fi : this) {
      byte bits = 0x0;
      if (fi.isIndexed) bits |= IS_INDEXED;
      if (fi.storeTermVector) bits |= STORE_TERMVECTOR;
      if (fi.storePositionWithTermVector) bits |= STORE_POSITIONS_WITH_TERMVECTOR;
      if (fi.storeOffsetWithTermVector) bits |= STORE_OFFSET_WITH_TERMVECTOR;
      if (fi.omitNorms) bits |= OMIT_NORMS;
      if (fi.storePayloads) bits |= STORE_PAYLOADS;
      if (fi.omitTermFreqAndPositions) bits |= OMIT_TERM_FREQ_AND_POSITIONS;
      output.writeString(fi.name);
      output.writeInt(fi.number);
      output.writeInt(fi.getCodecId());
      output.writeByte(bits);
    }
  }

  private void read(IndexInput input, String fileName) throws IOException {
    format = input.readVInt();

    if (format > FORMAT_MINIMUM) {
      throw new IndexFormatTooOldException(fileName, format, FORMAT_MINIMUM, FORMAT_CURRENT);
    }
    if (format < FORMAT_CURRENT) {
      throw new IndexFormatTooNewException(fileName, format, FORMAT_MINIMUM, FORMAT_CURRENT);
    }

    final int size = input.readVInt(); //read in the size

    for (int i = 0; i < size; i++) {
      String name = StringHelper.intern(input.readString());
      // if this is a previous format codec 0 will be preflex!
      final int fieldNumber = format <= FORMAT_PER_FIELD_CODEC? input.readInt():i;
      final int codecId = format <= FORMAT_PER_FIELD_CODEC? input.readInt():0;
      byte bits = input.readByte();
      boolean isIndexed = (bits & IS_INDEXED) != 0;
      boolean storeTermVector = (bits & STORE_TERMVECTOR) != 0;
      boolean storePositionsWithTermVector = (bits & STORE_POSITIONS_WITH_TERMVECTOR) != 0;
      boolean storeOffsetWithTermVector = (bits & STORE_OFFSET_WITH_TERMVECTOR) != 0;
      boolean omitNorms = (bits & OMIT_NORMS) != 0;
      boolean storePayloads = (bits & STORE_PAYLOADS) != 0;
      boolean omitTermFreqAndPositions = (bits & OMIT_TERM_FREQ_AND_POSITIONS) != 0;
      final FieldInfo addInternal = addInternal(name, fieldNumber, isIndexed, storeTermVector, storePositionsWithTermVector, storeOffsetWithTermVector, omitNorms, storePayloads, omitTermFreqAndPositions);
      addInternal.setCodecId(codecId);
    }

    if (input.getFilePointer() != input.length()) {
      throw new CorruptIndexException("did not read all bytes from file \"" + fileName + "\": read " + input.getFilePointer() + " vs size " + input.length());
    }    
  }

}
