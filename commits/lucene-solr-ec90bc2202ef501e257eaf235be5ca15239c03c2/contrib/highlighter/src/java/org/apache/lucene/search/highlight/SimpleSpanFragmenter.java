package org.apache.lucene.search.highlight;


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
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.search.spans.Spans;


/**
 * {@link Fragmenter} implementation which breaks text up into same-size
 * fragments but does not split up {@link Spans}. This is a simple sample class.
 */
public class SimpleSpanFragmenter implements Fragmenter {
  private static final int DEFAULT_FRAGMENT_SIZE = 100;
  private int fragmentSize;
  private int currentNumFrags;
  private int position = -1;
  private QueryScorer queryScorer;
  private int waitForPos = -1;
  private int textSize;
  private TermAttribute termAtt;
  private PositionIncrementAttribute posIncAtt;
  private OffsetAttribute offsetAtt;

  /**
   * @param queryScorer QueryScorer that was used to score hits
   */
  public SimpleSpanFragmenter(QueryScorer queryScorer) {
    this(queryScorer, DEFAULT_FRAGMENT_SIZE);
  }

  /**
   * @param queryScorer QueryScorer that was used to score hits
   * @param fragmentSize size in bytes of each fragment
   */
  public SimpleSpanFragmenter(QueryScorer queryScorer, int fragmentSize) {
    this.fragmentSize = fragmentSize;
    this.queryScorer = queryScorer;
  }
  
  /* (non-Javadoc)
   * @see org.apache.lucene.search.highlight.Fragmenter#isNewFragment()
   */
  public boolean isNewFragment() {
    position += posIncAtt.getPositionIncrement();

    if (waitForPos == position) {
      waitForPos = -1;
    } else if (waitForPos != -1) {
      return false;
    }

    WeightedSpanTerm wSpanTerm = queryScorer.getWeightedSpanTerm(termAtt.term());

    if (wSpanTerm != null) {
      List positionSpans = wSpanTerm.getPositionSpans();

      for (int i = 0; i < positionSpans.size(); i++) {
        if (((PositionSpan) positionSpans.get(i)).start == position) {
          waitForPos = ((PositionSpan) positionSpans.get(i)).end + 1;
          break;
        }
      }
    }

    boolean isNewFrag = offsetAtt.endOffset() >= (fragmentSize * currentNumFrags)
        && (textSize - offsetAtt.endOffset()) >= (fragmentSize >>> 1);
    
    if (isNewFrag) {
      currentNumFrags++;
    }

    return isNewFrag;
  }


  /* (non-Javadoc)
   * @see org.apache.lucene.search.highlight.Fragmenter#start(java.lang.String, org.apache.lucene.analysis.TokenStream)
   */
  public void start(String originalText, TokenStream tokenStream) {
    position = -1;
    currentNumFrags = 1;
    textSize = originalText.length();
    termAtt = tokenStream.addAttribute(TermAttribute.class);
    posIncAtt = tokenStream.addAttribute(PositionIncrementAttribute.class);
    offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
  }
}
