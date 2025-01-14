package org.apache.lucene.analysis.payloads;
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

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.index.Payload;
import org.apache.lucene.util.LuceneTestCase;

import java.io.StringReader;


/**
 *
 *
 **/
public class DelimitedPayloadTokenFilterTest extends LuceneTestCase {

  public void testPayloads() throws Exception {
    String test = "The quick|JJ red|JJ fox|NN jumped|VB over the lazy|JJ brown|JJ dogs|NN";
    DelimitedPayloadTokenFilter filter = new DelimitedPayloadTokenFilter(new WhitespaceTokenizer(new StringReader(test)));
    TermAttribute termAtt = (TermAttribute) filter.getAttribute(TermAttribute.class);
    PayloadAttribute payAtt = (PayloadAttribute) filter.getAttribute(PayloadAttribute.class);
    assertTermEquals("The", filter, termAtt, payAtt, null);
    assertTermEquals("quick", filter, termAtt, payAtt, "JJ".getBytes("UTF-8"));
    assertTermEquals("red", filter, termAtt, payAtt, "JJ".getBytes("UTF-8"));
    assertTermEquals("fox", filter, termAtt, payAtt, "NN".getBytes("UTF-8"));
    assertTermEquals("jumped", filter, termAtt, payAtt, "VB".getBytes("UTF-8"));
    assertTermEquals("over", filter, termAtt, payAtt, null);
    assertTermEquals("the", filter, termAtt, payAtt, null);
    assertTermEquals("lazy", filter, termAtt, payAtt, "JJ".getBytes("UTF-8"));
    assertTermEquals("brown", filter, termAtt, payAtt, "JJ".getBytes("UTF-8"));
    assertTermEquals("dogs", filter, termAtt, payAtt, "NN".getBytes("UTF-8"));
    assertFalse(filter.incrementToken());
  }

  public void testNext() throws Exception {

    String test = "The quick|JJ red|JJ fox|NN jumped|VB over the lazy|JJ brown|JJ dogs|NN";
    DelimitedPayloadTokenFilter filter = new DelimitedPayloadTokenFilter(new WhitespaceTokenizer(new StringReader(test)));
    assertTermEquals("The", filter, null);
    assertTermEquals("quick", filter, "JJ".getBytes("UTF-8"));
    assertTermEquals("red", filter, "JJ".getBytes("UTF-8"));
    assertTermEquals("fox", filter, "NN".getBytes("UTF-8"));
    assertTermEquals("jumped", filter, "VB".getBytes("UTF-8"));
    assertTermEquals("over", filter, null);
    assertTermEquals("the", filter, null);
    assertTermEquals("lazy", filter, "JJ".getBytes("UTF-8"));
    assertTermEquals("brown", filter, "JJ".getBytes("UTF-8"));
    assertTermEquals("dogs", filter, "NN".getBytes("UTF-8"));
    assertTrue(filter.next(new Token()) == null);
  }


  public void testFloatEncoding() throws Exception {
    String test = "The quick|1.0 red|2.0 fox|3.5 jumped|0.5 over the lazy|5 brown|99.3 dogs|83.7";
    DelimitedPayloadTokenFilter filter = new DelimitedPayloadTokenFilter(new WhitespaceTokenizer(new StringReader(test)), '|', new FloatEncoder());
    TermAttribute termAtt = (TermAttribute) filter.getAttribute(TermAttribute.class);
    PayloadAttribute payAtt = (PayloadAttribute) filter.getAttribute(PayloadAttribute.class);
    assertTermEquals("The", filter, termAtt, payAtt, null);
    assertTermEquals("quick", filter, termAtt, payAtt, PayloadHelper.encodeFloat(1.0f));
    assertTermEquals("red", filter, termAtt, payAtt, PayloadHelper.encodeFloat(2.0f));
    assertTermEquals("fox", filter, termAtt, payAtt, PayloadHelper.encodeFloat(3.5f));
    assertTermEquals("jumped", filter, termAtt, payAtt, PayloadHelper.encodeFloat(0.5f));
    assertTermEquals("over", filter, termAtt, payAtt, null);
    assertTermEquals("the", filter, termAtt, payAtt, null);
    assertTermEquals("lazy", filter, termAtt, payAtt, PayloadHelper.encodeFloat(5.0f));
    assertTermEquals("brown", filter, termAtt, payAtt, PayloadHelper.encodeFloat(99.3f));
    assertTermEquals("dogs", filter, termAtt, payAtt, PayloadHelper.encodeFloat(83.7f));
    assertFalse(filter.incrementToken());
  }

  public void testIntEncoding() throws Exception {
    String test = "The quick|1 red|2 fox|3 jumped over the lazy|5 brown|99 dogs|83";
    DelimitedPayloadTokenFilter filter = new DelimitedPayloadTokenFilter(new WhitespaceTokenizer(new StringReader(test)), '|', new IntegerEncoder());
    TermAttribute termAtt = (TermAttribute) filter.getAttribute(TermAttribute.class);
    PayloadAttribute payAtt = (PayloadAttribute) filter.getAttribute(PayloadAttribute.class);
    assertTermEquals("The", filter, termAtt, payAtt, null);
    assertTermEquals("quick", filter, termAtt, payAtt, PayloadHelper.encodeInt(1));
    assertTermEquals("red", filter, termAtt, payAtt, PayloadHelper.encodeInt(2));
    assertTermEquals("fox", filter, termAtt, payAtt, PayloadHelper.encodeInt(3));
    assertTermEquals("jumped", filter, termAtt, payAtt, null);
    assertTermEquals("over", filter, termAtt, payAtt, null);
    assertTermEquals("the", filter, termAtt, payAtt, null);
    assertTermEquals("lazy", filter, termAtt, payAtt, PayloadHelper.encodeInt(5));
    assertTermEquals("brown", filter, termAtt, payAtt, PayloadHelper.encodeInt(99));
    assertTermEquals("dogs", filter, termAtt, payAtt, PayloadHelper.encodeInt(83));
    assertFalse(filter.incrementToken());
  }

  void assertTermEquals(String expected, TokenStream stream, byte[] expectPay) throws Exception {
    Token tok = new Token();
    assertTrue(stream.next(tok) != null);
    assertEquals(expected, tok.term());
    Payload payload = tok.getPayload();
    if (payload != null) {
      assertTrue(payload.length() + " does not equal: " + expectPay.length, payload.length() == expectPay.length);
      for (int i = 0; i < expectPay.length; i++) {
        assertTrue(expectPay[i] + " does not equal: " + payload.byteAt(i), expectPay[i] == payload.byteAt(i));

      }
    } else {
      assertTrue("expectPay is not null and it should be", expectPay == null);
    }
  }


  void assertTermEquals(String expected, TokenStream stream, TermAttribute termAtt, PayloadAttribute payAtt, byte[] expectPay) throws Exception {
    assertTrue(stream.incrementToken());
    assertEquals(expected, termAtt.term());
    Payload payload = payAtt.getPayload();
    if (payload != null) {
      assertTrue(payload.length() + " does not equal: " + expectPay.length, payload.length() == expectPay.length);
      for (int i = 0; i < expectPay.length; i++) {
        assertTrue(expectPay[i] + " does not equal: " + payload.byteAt(i), expectPay[i] == payload.byteAt(i));

      }
    } else {
      assertTrue("expectPay is not null and it should be", expectPay == null);
    }
  }
}
