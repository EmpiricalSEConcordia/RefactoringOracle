package org.apache.lucene.analysis.bg;

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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.ReusableAnalyzerBase.TokenStreamComponents; // javadoc @link
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.KeywordMarkerTokenFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.StopwordAnalyzerBase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.util.Version;

/**
 * {@link Analyzer} for Bulgarian.
 * <p>
 * This analyzer implements light-stemming as specified by: <i> Searching
 * Strategies for the Bulgarian Language </i>
 * http://members.unine.ch/jacques.savoy/Papers/BUIR.pdf
 * <p>
 */
public final class BulgarianAnalyzer extends StopwordAnalyzerBase {
  
  /**
   * File containing default Bulgarian stopwords.
   * 
   * Default stopword list is from
   * http://members.unine.ch/jacques.savoy/clef/index.html The stopword list is
   * BSD-Licensed.
   */
  public final static String DEFAULT_STOPWORD_FILE = "stopwords.txt";
  
  /**
   * The comment character in the stopwords file. All lines prefixed with this
   * will be ignored
   * @deprecated use {@link WordlistLoader#getWordSet(File, String)} directly
   */
  //TODO make this private
  @Deprecated
  public static final String STOPWORDS_COMMENT = "#";
  
  /**
   * Returns an unmodifiable instance of the default stop-words set.
   * 
   * @return an unmodifiable instance of the default stop-words set.
   */
  public static Set<?> getDefaultStopSet() {
    return DefaultSetHolder.DEFAULT_STOP_SET;
  }
  
  /**
   * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer
   * class accesses the static final set the first time.;
   */
  private static class DefaultSetHolder {
    static final Set<?> DEFAULT_STOP_SET;
    
    static {
      try {
        DEFAULT_STOP_SET = loadStopwordSet(false, BulgarianAnalyzer.class, DEFAULT_STOPWORD_FILE, STOPWORDS_COMMENT);
      } catch (IOException ex) {
        // default set should always be present as it is part of the
        // distribution (JAR)
        throw new RuntimeException("Unable to load default stopword set");
      }
    }
  }
  
  private final Set<?> stemExclusionSet;
   
  /**
   * Builds an analyzer with the default stop words:
   * {@link #DEFAULT_STOPWORD_FILE}.
   */
  public BulgarianAnalyzer(Version matchVersion) {
    this(matchVersion, DefaultSetHolder.DEFAULT_STOP_SET);
  }
  
  /**
   * Builds an analyzer with the given stop words.
   */
  public BulgarianAnalyzer(Version matchVersion, Set<?> stopwords) {
    this(matchVersion, stopwords, CharArraySet.EMPTY_SET);
  }
  
  /**
   * Builds an analyzer with the given stop words and a stem exclusion set.
   * If a stem exclusion set is provided this analyzer will add a {@link KeywordMarkerTokenFilter} 
   * before {@link BulgarianStemFilter}.
   */
  public BulgarianAnalyzer(Version matchVersion, Set<?> stopwords, Set<?> stemExclusionSet) {
    super(matchVersion, stopwords);
    this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(
        matchVersion, stemExclusionSet));  }
  
  /**
   * Creates a {@link TokenStreamComponents} which tokenizes all the text in the provided
   * {@link Reader}.
   * 
   * @return A {@link TokenStreamComponents} built from an {@link StandardTokenizer}
   *         filtered with {@link StandardFilter}, {@link LowerCaseFilter},
   *         {@link StopFilter}, {@link KeywordMarkerTokenFilter} if a stem
   *         exclusion set is provided and {@link BulgarianStemFilter}.
   */
  @Override
  public TokenStreamComponents createComponents(String fieldName, Reader reader) {
    final Tokenizer source = new StandardTokenizer(matchVersion, reader);
    TokenStream result = new StandardFilter(source);
    result = new LowerCaseFilter(matchVersion, result);
    result = new StopFilter(matchVersion, result, stopwords);
    if(!stemExclusionSet.isEmpty())
      result = new KeywordMarkerTokenFilter(result, stemExclusionSet);
    result = new BulgarianStemFilter(result);
    return new TokenStreamComponents(source, result);
  }
}
