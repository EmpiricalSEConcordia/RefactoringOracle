package org.apache.lucene.search.spans;

/**
 * Copyright 2006 Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import org.apache.lucene.search.Query;
import org.apache.lucene.search.CheckHits;


/**
 * subclass of TestSimpleExplanations that verifies non matches.
 */
public class TestSpanExplanationsOfNonMatches
  extends TestSpanExplanations {

  /**
   * Overrides superclass to ignore matches and focus on non-matches
   *
   * @see CheckHits#checkNoMatchExplanations
   */
  public void qtest(Query q, int[] expDocNrs) throws Exception {
    CheckHits.checkNoMatchExplanations(q, FIELD, searcher, expDocNrs);
  }
    
}
