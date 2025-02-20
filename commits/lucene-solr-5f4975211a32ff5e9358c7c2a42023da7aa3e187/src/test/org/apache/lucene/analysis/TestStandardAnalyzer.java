package org.apache.lucene.analysis;

import junit.framework.TestCase;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.io.StringReader;

/**
 * Copyright 2004 The Apache Software Foundation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class TestStandardAnalyzer extends TestCase {

    private Analyzer a = new StandardAnalyzer();

    public void assertAnalyzesTo(Analyzer a, String input, String[] expected) throws Exception {
	assertAnalyzesTo(a, input, expected, null);
    }

    public void assertAnalyzesTo(Analyzer a, String input, String[] expectedImages, String[] expectedTypes) throws Exception {
	TokenStream ts = a.tokenStream("dummy", new StringReader(input));
	for (int i = 0; i < expectedImages.length; i++) {
	    Token t = ts.next();
	    assertNotNull(t);
	    assertEquals(expectedImages[i], t.termText());
	    if (expectedTypes != null)
	    {
		assertEquals(expectedTypes[i], t.type());
	    }
	}
	assertNull(ts.next());
	ts.close();
    }


    public void testAlphanumeric() throws Exception {
	// alphanumeric tokens
	assertAnalyzesTo(a, "B2B", new String[]{"b2b"});
	assertAnalyzesTo(a, "2B", new String[]{"2b"});
    }

    public void testUnderscores() throws Exception {
	// underscores are delimiters, but not in email addresses (below)
	assertAnalyzesTo(a, "word_having_underscore", new String[]{"word", "having", "underscore"});
	assertAnalyzesTo(a, "word_with_underscore_and_stopwords", new String[]{"word", "underscore", "stopwords"});
    }

    public void testDelimiters() throws Exception {
	// other delimiters: "-", "/", ","
	assertAnalyzesTo(a, "some-dashed-phrase",   new String[]{"some", "dashed", "phrase" });
	assertAnalyzesTo(a, "dogs,chase,cats", new String[]{"dogs", "chase", "cats"});
	assertAnalyzesTo(a, "ac/dc", new String[]{"ac", "dc"});
    }

    public void testApostrophes() throws Exception {
	// internal apostrophes: O'Reilly, you're, O'Reilly's
	// possessives are actually removed by StardardFilter, not the tokenizer
	assertAnalyzesTo(a, "O'Reilly", new String[]{"o'reilly"});
	assertAnalyzesTo(a, "you're", new String[]{"you're"});
	assertAnalyzesTo(a, "she's", new String[]{"she"});
	assertAnalyzesTo(a, "Jim's", new String[]{"jim"});
	assertAnalyzesTo(a, "don't", new String[]{"don't"});
	assertAnalyzesTo(a, "O'Reilly's", new String[]{"o'reilly"});
    }

    public void testTSADash() throws Exception {
	// t and s had been stopwords in Lucene <= 2.0, which made it impossible
	// to correctly search for these terms:
	assertAnalyzesTo(a, "s-class", new String[]{"s", "class"});
	assertAnalyzesTo(a, "t-com", new String[]{"t", "com"});
	// 'a' is still a stopword:
	assertAnalyzesTo(a, "a-class", new String[]{"class"});
    }

    public void testCompanyNames() throws Exception {
	// company names
	assertAnalyzesTo(a, "AT&T", new String[]{"at&t"});
	assertAnalyzesTo(a, "Excite@Home", new String[]{"excite@home"});
    }

    public void testDomainNames() throws Exception {
	// domain names
	assertAnalyzesTo(a, "www.nutch.org",   new String[]{"www.nutch.org" });
    }

    public void testEMailAddresses() throws Exception {
	// email addresses, possibly with underscores, periods, etc
	assertAnalyzesTo(a, "test@example.com", new String[]{"test@example.com"});
	assertAnalyzesTo(a, "first.lastname@example.com", new String[]{"first.lastname@example.com"});
	assertAnalyzesTo(a, "first_lastname@example.com", new String[]{"first_lastname@example.com"});
    }

    public void testNumeric() throws Exception {
	// floating point, serial, model numbers, ip addresses, etc.
	// every other segment must have at least one digit
	assertAnalyzesTo(a, "21.35", new String[]{"21.35"});
	assertAnalyzesTo(a, "R2D2 C3PO", new String[]{"r2d2", "c3po"});
	assertAnalyzesTo(a, "216.239.63.104",   new String[]{"216.239.63.104"});
	assertAnalyzesTo(a, "1-2-3",   new String[]{"1-2-3"});
	assertAnalyzesTo(a, "a1-b2-c3",   new String[]{"a1-b2-c3"});
	assertAnalyzesTo(a, "a1-b-c3",   new String[]{"a1-b-c3"});
    }

    public void testTextWithNumbers() throws Exception {
	// numbers
	assertAnalyzesTo(a, "David has 5000 bones", new String[]{"david", "has", "5000", "bones"});
    }

    public void testVariousText() throws Exception {
	// various
	assertAnalyzesTo(a, "C embedded developers wanted", new String[]{"c", "embedded", "developers", "wanted" });
	assertAnalyzesTo(a, "foo bar FOO BAR", new String[]{"foo", "bar", "foo", "bar"});
	assertAnalyzesTo(a, "foo      bar .  FOO <> BAR", new String[]{"foo", "bar", "foo", "bar"});
	assertAnalyzesTo(a, "\"QUOTED\" word", new String[]{"quoted", "word"});
    }

    public void testAcronyms() throws Exception {
	// acronyms have their dots stripped
	assertAnalyzesTo(a, "U.S.A.", new String[]{ "usa" });
    }

    public void testCPlusPlusHash() throws Exception {
	// It would be nice to change the grammar in StandardTokenizer.jj to make "C#" and "C++" end up as tokens.
	assertAnalyzesTo(a, "C++", new String[]{"c"});
	assertAnalyzesTo(a, "C#", new String[]{"c"});
    }

    public void testKorean() throws Exception {
	// Korean words
	assertAnalyzesTo(a, "안녕하세요 한글입니다", new String[]{"안녕하세요", "한글입니다"});
    }

    // Compliance with the "old" JavaCC-based analyzer, see:
    // https://issues.apache.org/jira/browse/LUCENE-966#action_12516752

    public void testComplianceFileName() throws Exception {
	assertAnalyzesTo(a, "2004.jpg",
		new String[] { "2004.jpg" },
		new String[] { "<HOST>" });
    }

    public void testComplianceNumericIncorrect() throws Exception {
	assertAnalyzesTo(a, "62.46",
		new String[] { "62.46" },
		new String[] { "<HOST>" });
    }

    public void testComplianceNumericLong() throws Exception {
	assertAnalyzesTo(a, "978-0-94045043-1",
		new String[] { "978-0-94045043-1" },
		new String[] { "<NUM>" });
    }

    public void testComplianceNumericFile() throws Exception {
	assertAnalyzesTo(
		a,
		"78academyawards/rules/rule02.html",
		new String[] { "78academyawards/rules/rule02.html" },
		new String[] { "<NUM>" });
    }

    public void testComplianceNumericWithUnderscores() throws Exception {
	assertAnalyzesTo(
		a,
		"2006-03-11t082958z_01_ban130523_rtridst_0_ozabs",
		new String[] { "2006-03-11t082958z_01_ban130523_rtridst_0_ozabs" },
		new String[] { "<NUM>" });
    }

    public void testComplianceNumericWithDash() throws Exception {
	assertAnalyzesTo(a, "mid-20th", new String[] { "mid-20th" },
		new String[] { "<NUM>" });
    }

    public void testComplianceManyTokens() throws Exception {
	assertAnalyzesTo(
		a,
		"/money.cnn.com/magazines/fortune/fortune_archive/2007/03/19/8402357/index.htm "
			+ "safari-0-sheikh-zayed-grand-mosque.jpg",
		new String[] { "money.cnn.com", "magazines", "fortune",
			"fortune", "archive/2007/03/19/8402357", "index.htm",
			"safari-0-sheikh", "zayed", "grand", "mosque.jpg" },
		new String[] { "<HOST>", "<ALPHANUM>", "<ALPHANUM>",
			"<ALPHANUM>", "<NUM>", "<HOST>", "<NUM>", "<ALPHANUM>",
			"<ALPHANUM>", "<HOST>" });
    }
}
