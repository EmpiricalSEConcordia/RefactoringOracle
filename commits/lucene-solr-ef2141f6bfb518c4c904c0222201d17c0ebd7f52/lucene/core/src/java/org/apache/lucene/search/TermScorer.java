package org.apache.lucene.search;

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

import java.io.IOException;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;

/** Expert: A <code>Scorer</code> for documents matching a <code>Term</code>.
 */
final class TermScorer extends Scorer {

  private final PostingsEnum postingsEnum;
  private final Similarity.SimScorer docScorer;

  /**
   * Construct a <code>TermScorer</code>.
   *
   * @param weight
   *          The weight of the <code>Term</code> in the query.
   * @param td
   *          An iterator over the documents matching the <code>Term</code>.
   * @param docScorer
   *          The </code>Similarity.SimScorer</code> implementation
   *          to be used for score computations.
   */
  TermScorer(Weight weight, PostingsEnum td, Similarity.SimScorer docScorer) {
    super(weight);
    this.docScorer = docScorer;
    this.postingsEnum = td;
  }

  @Override
  public int docID() {
    return postingsEnum.docID();
  }

  @Override
  public int freq() throws IOException {
    return postingsEnum.freq();
  }

  @Override
  public int nextPosition() throws IOException {
    return postingsEnum.nextPosition();
  }

  @Override
  public int startOffset() throws IOException {
    return postingsEnum.startOffset();
  }

  @Override
  public int endOffset() throws IOException {
    return postingsEnum.endOffset();
  }

  @Override
  public BytesRef getPayload() throws IOException {
    return postingsEnum.getPayload();
  }

  /**
   * Advances to the next document matching the query. <br>
   *
   * @return the document matching the query or NO_MORE_DOCS if there are no more documents.
   */
  @Override
  public int nextDoc() throws IOException {
    return postingsEnum.nextDoc();
  }

  @Override
  public float score() throws IOException {
    assert docID() != NO_MORE_DOCS;
    return docScorer.score(postingsEnum.docID(), postingsEnum.freq());
  }

  /**
   * Advances to the first match beyond the current whose document number is
   * greater than or equal to a given target. <br>
   * The implementation uses {@link org.apache.lucene.index.PostingsEnum#advance(int)}.
   *
   * @param target
   *          The target document number.
   * @return the matching document or NO_MORE_DOCS if none exist.
   */
  @Override
  public int advance(int target) throws IOException {
    return postingsEnum.advance(target);
  }

  @Override
  public long cost() {
    return postingsEnum.cost();
  }

  /** Returns a string representation of this <code>TermScorer</code>. */
  @Override
  public String toString() { return "scorer(" + weight + ")[" + super.toString() + "]"; }
}
