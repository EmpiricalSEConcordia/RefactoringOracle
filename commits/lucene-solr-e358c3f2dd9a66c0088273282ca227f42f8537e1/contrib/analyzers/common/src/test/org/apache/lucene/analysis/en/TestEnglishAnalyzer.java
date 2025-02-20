package org.apache.lucene.analysis.en;

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
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.Version;

public class TestEnglishAnalyzer extends BaseTokenStreamTestCase {
  /** This test fails with NPE when the 
   * stopwords file is missing in classpath */
  public void testResourcesAvailable() {
    new EnglishAnalyzer(Version.LUCENE_CURRENT);
  }
  
  /** test stopwords and stemming */
  public void testBasics() throws IOException {
    Analyzer a = new EnglishAnalyzer(Version.LUCENE_CURRENT);
    // stemming
    checkOneTermReuse(a, "books", "book");
    checkOneTermReuse(a, "book", "book");
    // stopword
    assertAnalyzesTo(a, "the", new String[] {});
  }
  
  /** test use of exclusion set */
  public void testExclude() throws IOException {
    Set<String> exclusionSet = new HashSet<String>();
    exclusionSet.add("books");
    Analyzer a = new EnglishAnalyzer(Version.LUCENE_CURRENT, 
        EnglishAnalyzer.getDefaultStopSet(), exclusionSet);
    checkOneTermReuse(a, "books", "books");
    checkOneTermReuse(a, "book", "book");
  }
}
