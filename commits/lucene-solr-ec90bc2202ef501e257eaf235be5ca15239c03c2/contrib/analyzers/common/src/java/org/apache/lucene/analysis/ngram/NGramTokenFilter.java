package org.apache.lucene.analysis.ngram;

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

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

/**
 * Tokenizes the input into n-grams of the given size(s).
 */
public class NGramTokenFilter extends TokenFilter {
  public static final int DEFAULT_MIN_NGRAM_SIZE = 1;
  public static final int DEFAULT_MAX_NGRAM_SIZE = 2;

  private int minGram, maxGram;
  
  private char[] curTermBuffer;
  private int curTermLength;
  private int curGramSize;
  private int curPos;
  
  private TermAttribute termAtt;
  private OffsetAttribute offsetAtt;

  /**
   * Creates NGramTokenFilter with given min and max n-grams.
   * @param input {@link TokenStream} holding the input to be tokenized
   * @param minGram the smallest n-gram to generate
   * @param maxGram the largest n-gram to generate
   */
  public NGramTokenFilter(TokenStream input, int minGram, int maxGram) {
    super(input);
    if (minGram < 1) {
      throw new IllegalArgumentException("minGram must be greater than zero");
    }
    if (minGram > maxGram) {
      throw new IllegalArgumentException("minGram must not be greater than maxGram");
    }
    this.minGram = minGram;
    this.maxGram = maxGram;
    
    this.termAtt = addAttribute(TermAttribute.class);
    this.offsetAtt = addAttribute(OffsetAttribute.class);
  }

  /**
   * Creates NGramTokenFilter with default min and max n-grams.
   * @param input {@link TokenStream} holding the input to be tokenized
   */
  public NGramTokenFilter(TokenStream input) {
    this(input, DEFAULT_MIN_NGRAM_SIZE, DEFAULT_MAX_NGRAM_SIZE);
  }

  /** Returns the next token in the stream, or null at EOS. */
  public final boolean incrementToken() throws IOException {
    while (true) {
      if (curTermBuffer == null) {
        if (!input.incrementToken()) {
          return false;
        } else {
          curTermBuffer = (char[]) termAtt.termBuffer().clone();
          curTermLength = termAtt.termLength();
          curGramSize = minGram;
          curPos = 0;
        }
      }
      while (curGramSize <= maxGram) {
        while (curPos+curGramSize <= curTermLength) {     // while there is input
          termAtt.setTermBuffer(curTermBuffer, curPos, curGramSize);
          offsetAtt.setOffset(curPos, curPos+curGramSize);
          curPos++;
          return true;
        }
        curGramSize++;                         // increase n-gram size
        curPos = 0;
      }
      curTermBuffer = null;
    }
  }
  
  /** @deprecated Will be removed in Lucene 3.0. This method is final, as it should
   * not be overridden. Delegates to the backwards compatibility layer. */
  public final Token next(final Token reusableToken) throws java.io.IOException {
    return super.next(reusableToken);
  }

  /** @deprecated Will be removed in Lucene 3.0. This method is final, as it should
   * not be overridden. Delegates to the backwards compatibility layer. */
  public final Token next() throws java.io.IOException {
    return super.next();
  }

  public void reset() throws IOException {
    super.reset();
    curTermBuffer = null;
  }
}
