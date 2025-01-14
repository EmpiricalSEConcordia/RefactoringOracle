package org.apache.solr.core;

/*
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

import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.solr.SolrTestCaseJ4;
import org.junit.BeforeClass;

public class TestMergePolicyConfig extends SolrTestCaseJ4 {

  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig-mergepolicy.xml","schema.xml");
  }
  
  public void testTieredMergePolicyConfig() throws Exception {
    IndexWriterConfig iwc = solrConfig.indexConfig.toIndexWriterConfig(h.getCore().getLatestSchema());
    MergePolicy mp = iwc.getMergePolicy();
    assertTrue(mp instanceof TieredMergePolicy);
    TieredMergePolicy tieredMP = (TieredMergePolicy) mp;

    // mp-specific setter
    assertEquals(19, tieredMP.getMaxMergeAtOnceExplicit());
    
    // make sure we apply compoundFile and mergeFactor
    assertEquals(0.0, tieredMP.getNoCFSRatio(), 0.0);
    assertEquals(7, tieredMP.getMaxMergeAtOnce());
    
    // make sure we overrode segmentsPerTier (split from maxMergeAtOnce out of mergeFactor)
    assertEquals(9D, tieredMP.getSegmentsPerTier(), 0.001);
    
    // make sure we overrode noCFSRatio (useless because we disabled useCompoundFile,
    // but just to make sure it works)
    assertEquals(1.0D, tieredMP.getNoCFSRatio(), 0.001);
  }
}
