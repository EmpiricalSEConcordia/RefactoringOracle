package org.apache.lucene.analysis.cz;

import java.io.IOException;

import org.apache.lucene.analysis.miscellaneous.KeywordMarkerFilter; // for javadoc
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

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

/**
 * A {@link TokenFilter} that applies {@link CzechStemmer} to stem Czech words.
 * <p>
 * To prevent terms from being stemmed use an instance of
 * {@link KeywordMarkerFilter} or a custom {@link TokenFilter} that sets
 * the {@link KeywordAttribute} before this {@link TokenStream}.
 * </p>
 * <p><b>NOTE</b>: Input is expected to be in lowercase, 
 * but with diacritical marks</p>
 * @see KeywordMarkerFilter
 */
public final class CzechStemFilter extends TokenFilter {
  private final CzechStemmer stemmer;
  private final TermAttribute termAtt;
  private final KeywordAttribute keywordAttr;
  
  public CzechStemFilter(TokenStream input) {
    super(input);
    stemmer = new CzechStemmer();
    termAtt = addAttribute(TermAttribute.class);
    keywordAttr = addAttribute(KeywordAttribute.class);
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (input.incrementToken()) {
      if(!keywordAttr.isKeyword()) {
        final int newlen = stemmer.stem(termAtt.termBuffer(), termAtt.termLength());
        termAtt.setTermLength(newlen);
      }
      return true;
    } else {
      return false;
    }
  }
}
