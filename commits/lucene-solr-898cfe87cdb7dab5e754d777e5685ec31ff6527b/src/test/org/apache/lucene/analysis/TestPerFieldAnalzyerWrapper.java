package org.apache.lucene.analysis;

import java.io.StringReader;

import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.util.LuceneTestCase;

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

public class TestPerFieldAnalzyerWrapper extends LuceneTestCase {
  public void testPerField() throws Exception {
    String text = "Qwerty";
    PerFieldAnalyzerWrapper analyzer =
              new PerFieldAnalyzerWrapper(new WhitespaceAnalyzer());
    analyzer.addAnalyzer("special", new SimpleAnalyzer());

    TokenStream tokenStream = analyzer.tokenStream("field",
                                            new StringReader(text));
    TermAttribute termAtt = (TermAttribute) tokenStream.getAttribute(TermAttribute.class);

    assertTrue(tokenStream.incrementToken());
    assertEquals("WhitespaceAnalyzer does not lowercase",
                 "Qwerty",
                 termAtt.term());

    tokenStream = analyzer.tokenStream("special",
                                            new StringReader(text));
    termAtt = (TermAttribute) tokenStream.getAttribute(TermAttribute.class);
    assertTrue(tokenStream.incrementToken());
    assertEquals("SimpleAnalyzer lowercases",
                 "qwerty",
                 termAtt.term());
  }
}
