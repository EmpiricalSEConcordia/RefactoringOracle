package org.apache.lucene.analysis.nl;

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

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.util.Version;

/**
 * Test the Dutch Stem Filter, which only modifies the term text.
 * 
 * The code states that it uses the snowball algorithm, but tests reveal some differences.
 * 
 */
public class TestDutchStemmer extends BaseTokenStreamTestCase {
  File dataDir = new File(System.getProperty("dataDir", "./bin"));
  File customDictFile = new File(dataDir, "org/apache/lucene/analysis/nl/customStemDict.txt");
  
  public void testWithSnowballExamples() throws Exception {
	 check("lichaamsziek", "lichaamsziek");
	 check("lichamelijk", "licham");
	 check("lichamelijke", "licham");
	 check("lichamelijkheden", "licham");
	 check("lichamen", "licham");
	 check("lichere", "licher");
	 check("licht", "licht");
	 check("lichtbeeld", "lichtbeeld");
	 check("lichtbruin", "lichtbruin");
	 check("lichtdoorlatende", "lichtdoorlat");
	 check("lichte", "licht");
	 check("lichten", "licht");
	 check("lichtende", "lichtend");
	 check("lichtenvoorde", "lichtenvoord");
	 check("lichter", "lichter");
	 check("lichtere", "lichter");
	 check("lichters", "lichter");
	 check("lichtgevoeligheid", "lichtgevoel");
	 check("lichtgewicht", "lichtgewicht");
	 check("lichtgrijs", "lichtgrijs");
	 check("lichthoeveelheid", "lichthoevel");
	 check("lichtintensiteit", "lichtintensiteit");
	 check("lichtje", "lichtj");
	 check("lichtjes", "lichtjes");
	 check("lichtkranten", "lichtkrant");
	 check("lichtkring", "lichtkring");
	 check("lichtkringen", "lichtkring");
	 check("lichtregelsystemen", "lichtregelsystem");
	 check("lichtste", "lichtst");
	 check("lichtstromende", "lichtstrom");
	 check("lichtte", "licht");
	 check("lichtten", "licht");
	 check("lichttoetreding", "lichttoetred");
	 check("lichtverontreinigde", "lichtverontreinigd");
	 check("lichtzinnige", "lichtzinn");
	 check("lid", "lid");
	 check("lidia", "lidia");
	 check("lidmaatschap", "lidmaatschap");
	 check("lidstaten", "lidstat");
	 check("lidvereniging", "lidveren");
	 check("opgingen", "opging");
	 check("opglanzing", "opglanz");
	 check("opglanzingen", "opglanz");
	 check("opglimlachten", "opglimlacht");
	 check("opglimpen", "opglimp");
	 check("opglimpende", "opglimp");
	 check("opglimping", "opglimp");
	 check("opglimpingen", "opglimp");
	 check("opgraven", "opgrav");
	 check("opgrijnzen", "opgrijnz");
	 check("opgrijzende", "opgrijz");
	 check("opgroeien", "opgroei");
	 check("opgroeiende", "opgroei");
	 check("opgroeiplaats", "opgroeiplat");
	 check("ophaal", "ophal");
	 check("ophaaldienst", "ophaaldienst");
	 check("ophaalkosten", "ophaalkost");
	 check("ophaalsystemen", "ophaalsystem");
	 check("ophaalt", "ophaalt");
	 check("ophaaltruck", "ophaaltruck");
	 check("ophalen", "ophal");
	 check("ophalend", "ophal");
	 check("ophalers", "ophaler");
	 check("ophef", "ophef");
	 check("opheldering", "ophelder");
	 check("ophemelde", "ophemeld");
	 check("ophemelen", "ophemel");
	 check("opheusden", "opheusd");
	 check("ophief", "ophief");
	 check("ophield", "ophield");
	 check("ophieven", "ophiev");
	 check("ophoepelt", "ophoepelt");
	 check("ophoog", "ophog");
	 check("ophoogzand", "ophoogzand");
	 check("ophopen", "ophop");
	 check("ophoping", "ophop");
	 check("ophouden", "ophoud");
  }
  
  /**
   * @deprecated remove this test in Lucene 4.0
   */
  @Deprecated
  public void testOldBuggyStemmer() throws Exception {
    Analyzer a = new DutchAnalyzer(Version.LUCENE_30);
    checkOneTermReuse(a, "opheffen", "ophef"); // versus snowball 'opheff'
    checkOneTermReuse(a, "opheffende", "ophef"); // versus snowball 'opheff'
    checkOneTermReuse(a, "opheffing", "ophef"); // versus snowball 'opheff'
  }
  
  public void testSnowballCorrectness() throws Exception {
    Analyzer a = new DutchAnalyzer(Version.LUCENE_CURRENT);
    checkOneTermReuse(a, "opheffen", "opheff");
    checkOneTermReuse(a, "opheffende", "opheff");
    checkOneTermReuse(a, "opheffing", "opheff");
  }
  
  public void testReusableTokenStream() throws Exception {
    Analyzer a = new DutchAnalyzer(Version.LUCENE_CURRENT); 
    checkOneTermReuse(a, "lichaamsziek", "lichaamsziek");
    checkOneTermReuse(a, "lichamelijk", "licham");
    checkOneTermReuse(a, "lichamelijke", "licham");
    checkOneTermReuse(a, "lichamelijkheden", "licham");
  }
  
  /* 
   * Test that changes to the exclusion table are applied immediately
   * when using reusable token streams.
   */
  public void testExclusionTableReuse() throws Exception {
    DutchAnalyzer a = new DutchAnalyzer(Version.LUCENE_CURRENT);
    checkOneTermReuse(a, "lichamelijk", "licham");
    a.setStemExclusionTable(new String[] { "lichamelijk" });
    checkOneTermReuse(a, "lichamelijk", "lichamelijk");

    
  }
  
  public void testExclusionTableViaCtor() throws IOException {
    CharArraySet set = new CharArraySet(Version.LUCENE_30, 1, true);
    set.add("lichamelijk");
    DutchAnalyzer a = new DutchAnalyzer(Version.LUCENE_CURRENT, CharArraySet.EMPTY_SET, set);
    assertAnalyzesToReuse(a, "lichamelijk lichamelijke", new String[] { "lichamelijk", "licham" });
    
    a = new DutchAnalyzer(Version.LUCENE_CURRENT, CharArraySet.EMPTY_SET, set);
    assertAnalyzesTo(a, "lichamelijk lichamelijke", new String[] { "lichamelijk", "licham" });

  }
  
  /* 
   * Test that changes to the dictionary stemming table are applied immediately
   * when using reusable token streams.
   */
  public void testStemDictionaryReuse() throws Exception {
    DutchAnalyzer a = new DutchAnalyzer(Version.LUCENE_CURRENT);
    checkOneTermReuse(a, "lichamelijk", "licham");
    a.setStemDictionary(customDictFile);
    checkOneTermReuse(a, "lichamelijk", "somethingentirelydifferent");
  }
  
  /**
   * Prior to 3.1, this analyzer had no lowercase filter.
   * stopwords were case sensitive. Preserve this for back compat.
   * @deprecated Remove this test in Lucene 4.0
   */
  @Deprecated
  public void testBuggyStopwordsCasing() throws IOException {
    DutchAnalyzer a = new DutchAnalyzer(Version.LUCENE_30);
    assertAnalyzesTo(a, "Zelf", new String[] { "zelf" });
  }
  
  /**
   * Test that stopwords are not case sensitive
   */
  public void testStopwordsCasing() throws IOException {
    DutchAnalyzer a = new DutchAnalyzer(Version.LUCENE_31);
    assertAnalyzesTo(a, "Zelf", new String[] { });
  }
  
  private void check(final String input, final String expected) throws Exception {
    checkOneTerm(new DutchAnalyzer(Version.LUCENE_CURRENT), input, expected); 
  }
  
}