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

package org.apache.solr.highlight;

import java.util.HashMap;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.util.TestHarness;
import org.junit.BeforeClass;
import org.junit.Test;

public class FastVectorHighlighterTest extends SolrTestCaseJ4 {

  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig.xml","schema.xml");
  }
  
  @Test
  public void testConfig(){
    SolrHighlighter highlighter = h.getCore().getHighlighter();

    // Make sure we loaded the one fragListBuilder
    SolrFragListBuilder solrFlbNull = highlighter.fragListBuilders.get( null );
    SolrFragListBuilder solrFlbEmpty = highlighter.fragListBuilders.get( "" );
    SolrFragListBuilder solrFlbSimple = highlighter.fragListBuilders.get( "simple" );
    assertSame( solrFlbNull, solrFlbEmpty );
    assertTrue( solrFlbNull instanceof SimpleFragListBuilder );
    assertTrue( solrFlbSimple instanceof SimpleFragListBuilder );
        
    // Make sure we loaded the one fragmentsBuilder
    SolrFragmentsBuilder solrFbNull = highlighter.fragmentsBuilders.get( null );
    SolrFragmentsBuilder solrFbEmpty = highlighter.fragmentsBuilders.get( "" );
    SolrFragmentsBuilder solrFbSimple = highlighter.fragmentsBuilders.get( "simple" );
    SolrFragmentsBuilder solrFbSO = highlighter.fragmentsBuilders.get( "scoreOrder" );
    assertSame( solrFbNull, solrFbEmpty );
    assertTrue( solrFbNull instanceof ScoreOrderFragmentsBuilder );
    assertTrue( solrFbSimple instanceof SimpleFragmentsBuilder );
    assertTrue( solrFbSO instanceof ScoreOrderFragmentsBuilder );
  }

  @Test
  public void test() {
    HashMap<String,String> args = new HashMap<String,String>();
    args.put("hl", "true");
    args.put("hl.fl", "tv_text");
    args.put("hl.snippets", "2");
    args.put("hl.useFastVectorHighlighter", "true");
    TestHarness.LocalRequestFactory sumLRF = h.getRequestFactory(
      "standard",0,200,args);
    
    assertU(adoc("tv_text", "basic fast vector highlighter test", 
                 "id", "1"));
    assertU(commit());
    assertU(optimize());
    assertQ("Basic summarization",
            sumLRF.makeRequest("tv_text:vector"),
            "//lst[@name='highlighting']/lst[@name='1']",
            "//lst[@name='1']/arr[@name='tv_text']/str[.=' fast <em>vector</em> highlighter test ']"
            );
  }
}
