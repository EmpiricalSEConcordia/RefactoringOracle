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
package org.apache.lucene.analysis.commongrams;

import java.io.Reader;
import java.io.StringReader;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Tests CommonGrams(Query)Filter
 */
public class CommonGramsFilterTest extends BaseTokenStreamTestCase {
  private static final String[] commonWords = { "s", "a", "b", "c", "d", "the",
      "of" };
  
  public void testReset() throws Exception {
    final String input = "How the s a brown s cow d like A B thing?";
    WhitespaceTokenizer wt = new WhitespaceTokenizer(TEST_VERSION_CURRENT, new StringReader(input));
    CommonGramsFilter cgf = new CommonGramsFilter(wt, commonWords);
    
    CharTermAttribute term = cgf.addAttribute(CharTermAttribute.class);
    assertTrue(cgf.incrementToken());
    assertEquals("How", term.toString());
    assertTrue(cgf.incrementToken());
    assertEquals("How_the", term.toString());
    assertTrue(cgf.incrementToken());
    assertEquals("the", term.toString());
    assertTrue(cgf.incrementToken());
    assertEquals("the_s", term.toString());
    
    wt.reset(new StringReader(input));
    cgf.reset();
    assertTrue(cgf.incrementToken());
    assertEquals("How", term.toString());
  }
  
  public void testQueryReset() throws Exception {
    final String input = "How the s a brown s cow d like A B thing?";
    WhitespaceTokenizer wt = new WhitespaceTokenizer(TEST_VERSION_CURRENT, new StringReader(input));
    CommonGramsFilter cgf = new CommonGramsFilter(wt, commonWords);
    CommonGramsQueryFilter nsf = new CommonGramsQueryFilter(cgf);
    
    CharTermAttribute term = wt.addAttribute(CharTermAttribute.class);
    assertTrue(nsf.incrementToken());
    assertEquals("How_the", term.toString());
    assertTrue(nsf.incrementToken());
    assertEquals("the_s", term.toString());
    
    wt.reset(new StringReader(input));
    nsf.reset();
    assertTrue(nsf.incrementToken());
    assertEquals("How_the", term.toString());
  }
  
  /**
   * This is for testing CommonGramsQueryFilter which outputs a set of tokens
   * optimized for querying with only one token at each position, either a
   * unigram or a bigram It also will not return a token for the final position
   * if the final word is already in the preceding bigram Example:(three
   * tokens/positions in)
   * "foo bar the"=>"foo:1|bar:2,bar-the:2|the:3=> "foo" "bar-the" (2 tokens
   * out)
   * 
   * @return Map<String,String>
   */
  public void testCommonGramsQueryFilter() throws Exception {
    Analyzer a = new Analyzer() {    
      @Override
      public TokenStream tokenStream(String field, Reader in) {
        return new CommonGramsQueryFilter(new CommonGramsFilter(
            new WhitespaceTokenizer(TEST_VERSION_CURRENT, in), commonWords));
      } 
    };

    // Stop words used below are "of" "the" and "s"
    
    // two word queries
    assertAnalyzesTo(a, "brown fox", 
        new String[] { "brown", "fox" });
    assertAnalyzesTo(a, "the fox", 
        new String[] { "the_fox" });
    assertAnalyzesTo(a, "fox of", 
        new String[] { "fox_of" });
    assertAnalyzesTo(a, "of the", 
        new String[] { "of_the" });
    
    // one word queries
    assertAnalyzesTo(a, "the", 
        new String[] { "the" });
    assertAnalyzesTo(a, "foo", 
        new String[] { "foo" });

    // 3 word combinations s=stopword/common word n=not a stop word
    assertAnalyzesTo(a, "n n n", 
        new String[] { "n", "n", "n" });
    assertAnalyzesTo(a, "quick brown fox", 
        new String[] { "quick", "brown", "fox" });

    assertAnalyzesTo(a, "n n s", 
        new String[] { "n", "n_s" });
    assertAnalyzesTo(a, "quick brown the", 
        new String[] { "quick", "brown_the" });

    assertAnalyzesTo(a, "n s n", 
        new String[] { "n_s", "s_n" });
    assertAnalyzesTo(a, "quick the brown", 
        new String[] { "quick_the", "the_brown" });

    assertAnalyzesTo(a, "n s s", 
        new String[] { "n_s", "s_s" });
    assertAnalyzesTo(a, "fox of the", 
        new String[] { "fox_of", "of_the" });

    assertAnalyzesTo(a, "s n n", 
        new String[] { "s_n", "n", "n" });
    assertAnalyzesTo(a, "the quick brown", 
        new String[] { "the_quick", "quick", "brown" });

    assertAnalyzesTo(a, "s n s", 
        new String[] { "s_n", "n_s" });
    assertAnalyzesTo(a, "the fox of", 
        new String[] { "the_fox", "fox_of" });

    assertAnalyzesTo(a, "s s n", 
        new String[] { "s_s", "s_n" });
    assertAnalyzesTo(a, "of the fox", 
        new String[] { "of_the", "the_fox" });

    assertAnalyzesTo(a, "s s s", 
        new String[] { "s_s", "s_s" });
    assertAnalyzesTo(a, "of the of", 
        new String[] { "of_the", "the_of" });
  }
  
  public void testCommonGramsFilter() throws Exception {
    Analyzer a = new Analyzer() {    
      @Override
      public TokenStream tokenStream(String field, Reader in) {
        return new CommonGramsFilter(
            new WhitespaceTokenizer(TEST_VERSION_CURRENT, in), commonWords);
      } 
    };

    // Stop words used below are "of" "the" and "s"
    // one word queries
    assertAnalyzesTo(a, "the", new String[] { "the" });
    assertAnalyzesTo(a, "foo", new String[] { "foo" });

    // two word queries
    assertAnalyzesTo(a, "brown fox", 
        new String[] { "brown", "fox" }, 
        new int[] { 1, 1 });
    assertAnalyzesTo(a, "the fox", 
        new String[] { "the", "the_fox", "fox" }, 
        new int[] { 1, 0, 1 });
    assertAnalyzesTo(a, "fox of", 
        new String[] { "fox", "fox_of", "of" }, 
        new int[] { 1, 0, 1 });
    assertAnalyzesTo(a, "of the", 
        new String[] { "of", "of_the", "the" }, 
        new int[] { 1, 0, 1 });

    // 3 word combinations s=stopword/common word n=not a stop word
    assertAnalyzesTo(a, "n n n", 
        new String[] { "n", "n", "n" }, 
        new int[] { 1, 1, 1 });
    assertAnalyzesTo(a, "quick brown fox", 
        new String[] { "quick", "brown", "fox" }, 
        new int[] { 1, 1, 1 });

    assertAnalyzesTo(a, "n n s", 
        new String[] { "n", "n", "n_s", "s" }, 
        new int[] { 1, 1, 0, 1 });
    assertAnalyzesTo(a, "quick brown the", 
        new String[] { "quick", "brown", "brown_the", "the" }, 
        new int[] { 1, 1, 0, 1 });

    assertAnalyzesTo(a, "n s n", 
        new String[] { "n", "n_s", "s", "s_n", "n" }, 
        new int[] { 1, 0, 1, 0, 1 });
    assertAnalyzesTo(a, "quick the fox", 
        new String[] { "quick", "quick_the", "the", "the_fox", "fox" }, 
        new int[] { 1, 0, 1, 0, 1 });

    assertAnalyzesTo(a, "n s s", 
        new String[] { "n", "n_s", "s", "s_s", "s" }, 
        new int[] { 1, 0, 1, 0, 1 });
    assertAnalyzesTo(a, "fox of the", 
        new String[] { "fox", "fox_of", "of", "of_the", "the" }, 
        new int[] { 1, 0, 1, 0, 1 });

    assertAnalyzesTo(a, "s n n", 
        new String[] { "s", "s_n", "n", "n" }, 
        new int[] { 1, 0, 1, 1 });
    assertAnalyzesTo(a, "the quick brown", 
        new String[] { "the", "the_quick", "quick", "brown" }, 
        new int[] { 1, 0, 1, 1 });

    assertAnalyzesTo(a, "s n s", 
        new String[] { "s", "s_n", "n", "n_s", "s" }, 
        new int[] { 1, 0, 1, 0, 1 });
    assertAnalyzesTo(a, "the fox of", 
        new String[] { "the", "the_fox", "fox", "fox_of", "of" }, 
        new int[] { 1, 0, 1, 0, 1 });

    assertAnalyzesTo(a, "s s n", 
        new String[] { "s", "s_s", "s", "s_n", "n" }, 
        new int[] { 1, 0, 1, 0, 1 });
    assertAnalyzesTo(a, "of the fox", 
        new String[] { "of", "of_the", "the", "the_fox", "fox" }, 
        new int[] { 1, 0, 1, 0, 1 });

    assertAnalyzesTo(a, "s s s", 
        new String[] { "s", "s_s", "s", "s_s", "s" }, 
        new int[] { 1, 0, 1, 0, 1 });
    assertAnalyzesTo(a, "of the of", 
        new String[] { "of", "of_the", "the", "the_of", "of" }, 
        new int[] { 1, 0, 1, 0, 1 });
  }
  
  /**
   * Test that CommonGramsFilter works correctly in case-insensitive mode
   */
  public void testCaseSensitive() throws Exception {
    final String input = "How The s a brown s cow d like A B thing?";
    WhitespaceTokenizer wt = new WhitespaceTokenizer(TEST_VERSION_CURRENT, new StringReader(input));
    Set common = CommonGramsFilter.makeCommonSet(commonWords);
    TokenFilter cgf = new CommonGramsFilter(wt, common, false);
    assertTokenStreamContents(cgf, new String[] {"How", "The", "The_s", "s",
        "s_a", "a", "a_brown", "brown", "brown_s", "s", "s_cow", "cow",
        "cow_d", "d", "d_like", "like", "A", "B", "thing?"});
  }
  
  /**
   * Test CommonGramsQueryFilter in the case that the last word is a stopword
   */
  public void testLastWordisStopWord() throws Exception {
    final String input = "dog the";
    WhitespaceTokenizer wt = new WhitespaceTokenizer(TEST_VERSION_CURRENT, new StringReader(input));
    CommonGramsFilter cgf = new CommonGramsFilter(wt, commonWords);
    TokenFilter nsf = new CommonGramsQueryFilter(cgf);
    assertTokenStreamContents(nsf, new String[] { "dog_the" });
  }
  
  /**
   * Test CommonGramsQueryFilter in the case that the first word is a stopword
   */
  public void testFirstWordisStopWord() throws Exception {
    final String input = "the dog";
    WhitespaceTokenizer wt = new WhitespaceTokenizer(TEST_VERSION_CURRENT, new StringReader(input));
    CommonGramsFilter cgf = new CommonGramsFilter(wt, commonWords);
    TokenFilter nsf = new CommonGramsQueryFilter(cgf);
    assertTokenStreamContents(nsf, new String[] { "the_dog" });
  }
  
  /**
   * Test CommonGramsQueryFilter in the case of a single (stop)word query
   */
  public void testOneWordQueryStopWord() throws Exception {
    final String input = "the";
    WhitespaceTokenizer wt = new WhitespaceTokenizer(TEST_VERSION_CURRENT, new StringReader(input));
    CommonGramsFilter cgf = new CommonGramsFilter(wt, commonWords);
    TokenFilter nsf = new CommonGramsQueryFilter(cgf);
    assertTokenStreamContents(nsf, new String[] { "the" });
  }
  
  /**
   * Test CommonGramsQueryFilter in the case of a single word query
   */
  public void testOneWordQuery() throws Exception {
    final String input = "monster";
    WhitespaceTokenizer wt = new WhitespaceTokenizer(TEST_VERSION_CURRENT, new StringReader(input));
    CommonGramsFilter cgf = new CommonGramsFilter(wt, commonWords);
    TokenFilter nsf = new CommonGramsQueryFilter(cgf);
    assertTokenStreamContents(nsf, new String[] { "monster" });
  }
  
  /**
   * Test CommonGramsQueryFilter when first and last words are stopwords.
   */
  public void TestFirstAndLastStopWord() throws Exception {
    final String input = "the of";
    WhitespaceTokenizer wt = new WhitespaceTokenizer(TEST_VERSION_CURRENT, new StringReader(input));
    CommonGramsFilter cgf = new CommonGramsFilter(wt, commonWords);
    TokenFilter nsf = new CommonGramsQueryFilter(cgf);
    assertTokenStreamContents(nsf, new String[] { "the_of" });
  }
}
