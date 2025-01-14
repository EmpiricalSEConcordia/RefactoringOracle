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

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

import java.io.IOException;

/**
 * Tokenizes the given token into n-grams of given size(s).
 *
 * This filter create n-grams from the beginning edge or ending edge of a input token.
 * 
 */
public class EdgeNGramTokenFilter extends TokenFilter {
  public static final Side DEFAULT_SIDE = Side.FRONT;
  public static final int DEFAULT_MAX_GRAM_SIZE = 1;
  public static final int DEFAULT_MIN_GRAM_SIZE = 1;

  // Replace this with an enum when the Java 1.5 upgrade is made, the impl will be simplified
  /** Specifies which side of the input the n-gram should be generated from */
  public static class Side {
    private String label;

    /** Get the n-gram from the front of the input */
    public static Side FRONT = new Side("front");

    /** Get the n-gram from the end of the input */
    public static Side BACK = new Side("back");

    // Private ctor
    private Side(String label) { this.label = label; }

    public String getLabel() { return label; }

    // Get the appropriate Side from a string
    public static Side getSide(String sideName) {
      if (FRONT.getLabel().equals(sideName)) {
        return FRONT;
      }
      else if (BACK.getLabel().equals(sideName)) {
        return BACK;
      }
      return null;
    }
  }

  private int minGram;
  private int maxGram;
  private Side side;
  private char[] curTermBuffer;
  private int curTermLength;
  private int curGramSize;
  
  private TermAttribute termAtt;
  private OffsetAttribute offsetAtt;


  protected EdgeNGramTokenFilter(TokenStream input) {
    super(input);
    this.termAtt = (TermAttribute) addAttribute(TermAttribute.class);
    this.offsetAtt = (OffsetAttribute) addAttribute(OffsetAttribute.class);
  }

  /**
   * Creates EdgeNGramTokenFilter that can generate n-grams in the sizes of the given range
   *
   * @param input TokenStream holding the input to be tokenized
   * @param side the {@link Side} from which to chop off an n-gram
   * @param minGram the smallest n-gram to generate
   * @param maxGram the largest n-gram to generate
   */
  public EdgeNGramTokenFilter(TokenStream input, Side side, int minGram, int maxGram) {
    super(input);

    if (side == null) {
      throw new IllegalArgumentException("sideLabel must be either front or back");
    }

    if (minGram < 1) {
      throw new IllegalArgumentException("minGram must be greater than zero");
    }

    if (minGram > maxGram) {
      throw new IllegalArgumentException("minGram must not be greater than maxGram");
    }

    this.minGram = minGram;
    this.maxGram = maxGram;
    this.side = side;
    this.termAtt = (TermAttribute) addAttribute(TermAttribute.class);
    this.offsetAtt = (OffsetAttribute) addAttribute(OffsetAttribute.class);
  }

  /**
   * Creates EdgeNGramTokenFilter that can generate n-grams in the sizes of the given range
   *
   * @param input TokenStream holding the input to be tokenized
   * @param sideLabel the name of the {@link Side} from which to chop off an n-gram
   * @param minGram the smallest n-gram to generate
   * @param maxGram the largest n-gram to generate
   */
  public EdgeNGramTokenFilter(TokenStream input, String sideLabel, int minGram, int maxGram) {
    this(input, Side.getSide(sideLabel), minGram, maxGram);
  }

  public final boolean incrementToken() throws IOException {
    while (true) {
      if (curTermBuffer == null) {
        if (!input.incrementToken()) {
          return false;
        } else {
          curTermBuffer = (char[]) termAtt.termBuffer().clone();
          curTermLength = termAtt.termLength();
          curGramSize = minGram;
        }
      }
      if (curGramSize <= maxGram) {
        if (! (curGramSize > curTermLength         // if the remaining input is too short, we can't generate any n-grams
            || curGramSize > maxGram)) {       // if we have hit the end of our n-gram size range, quit
          // grab gramSize chars from front or back
          int start = side == Side.FRONT ? 0 : curTermLength - curGramSize;
          int end = start + curGramSize;
          offsetAtt.setOffset(start, end);
          termAtt.setTermBuffer(curTermBuffer, start, curGramSize);
          curGramSize++;
          return true;
        }
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
}
