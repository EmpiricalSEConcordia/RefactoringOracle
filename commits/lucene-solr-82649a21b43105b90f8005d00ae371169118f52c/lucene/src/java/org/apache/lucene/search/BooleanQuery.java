package org.apache.lucene.search;

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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.IndexReader.AtomicReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.ToStringUtils;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.ConjunctionTermScorer.DocsAndFreqs;
import org.apache.lucene.search.Similarity.ExactDocScorer;
import org.apache.lucene.search.TermQuery.TermWeight;

import java.io.IOException;
import java.util.*;

/** A Query that matches documents matching boolean combinations of other
  * queries, e.g. {@link TermQuery}s, {@link PhraseQuery}s or other
  * BooleanQuerys.
  */
public class BooleanQuery extends Query implements Iterable<BooleanClause> {

  private static int maxClauseCount = 1024;

  /** Thrown when an attempt is made to add more than {@link
   * #getMaxClauseCount()} clauses. This typically happens if
   * a PrefixQuery, FuzzyQuery, WildcardQuery, or TermRangeQuery 
   * is expanded to many terms during search. 
   */
  public static class TooManyClauses extends RuntimeException {
    public TooManyClauses() {
      super("maxClauseCount is set to " + maxClauseCount);
    }
  }

  /** Return the maximum number of clauses permitted, 1024 by default.
   * Attempts to add more than the permitted number of clauses cause {@link
   * TooManyClauses} to be thrown.
   * @see #setMaxClauseCount(int)
   */
  public static int getMaxClauseCount() { return maxClauseCount; }

  /** 
   * Set the maximum number of clauses permitted per BooleanQuery.
   * Default value is 1024.
   */
  public static void setMaxClauseCount(int maxClauseCount) {
    if (maxClauseCount < 1)
      throw new IllegalArgumentException("maxClauseCount must be >= 1");
    BooleanQuery.maxClauseCount = maxClauseCount;
  }

  private ArrayList<BooleanClause> clauses = new ArrayList<BooleanClause>();
  private final boolean disableCoord;

  /** Constructs an empty boolean query. */
  public BooleanQuery() {
    disableCoord = false;
  }

  /** Constructs an empty boolean query.
   *
   * {@link SimilarityProvider#coord(int,int)} may be disabled in scoring, as
   * appropriate. For example, this score factor does not make sense for most
   * automatically generated queries, like {@link WildcardQuery} and {@link
   * FuzzyQuery}.
   *
   * @param disableCoord disables {@link SimilarityProvider#coord(int,int)} in scoring.
   */
  public BooleanQuery(boolean disableCoord) {
    this.disableCoord = disableCoord;
  }

  /** Returns true iff {@link SimilarityProvider#coord(int,int)} is disabled in
   * scoring for this query instance.
   * @see #BooleanQuery(boolean)
   */
  public boolean isCoordDisabled() { return disableCoord; }

  /**
   * Specifies a minimum number of the optional BooleanClauses
   * which must be satisfied.
   *
   * <p>
   * By default no optional clauses are necessary for a match
   * (unless there are no required clauses).  If this method is used,
   * then the specified number of clauses is required.
   * </p>
   * <p>
   * Use of this method is totally independent of specifying that
   * any specific clauses are required (or prohibited).  This number will
   * only be compared against the number of matching optional clauses.
   * </p>
   *
   * @param min the number of optional clauses that must match
   */
  public void setMinimumNumberShouldMatch(int min) {
    this.minNrShouldMatch = min;
  }
  protected int minNrShouldMatch = 0;

  /**
   * Gets the minimum number of the optional BooleanClauses
   * which must be satisfied.
   */
  public int getMinimumNumberShouldMatch() {
    return minNrShouldMatch;
  }

  /** Adds a clause to a boolean query.
   *
   * @throws TooManyClauses if the new number of clauses exceeds the maximum clause number
   * @see #getMaxClauseCount()
   */
  public void add(Query query, BooleanClause.Occur occur) {
    add(new BooleanClause(query, occur));
  }

  /** Adds a clause to a boolean query.
   * @throws TooManyClauses if the new number of clauses exceeds the maximum clause number
   * @see #getMaxClauseCount()
   */
  public void add(BooleanClause clause) {
    if (clauses.size() >= maxClauseCount)
      throw new TooManyClauses();

    clauses.add(clause);
  }

  /** Returns the set of clauses in this query. */
  public BooleanClause[] getClauses() {
    return clauses.toArray(new BooleanClause[clauses.size()]);
  }

  /** Returns the list of clauses in this query. */
  public List<BooleanClause> clauses() { return clauses; }

  /** Returns an iterator on the clauses in this query. It implements the {@link Iterable} interface to
   * make it possible to do:
   * <pre>for (BooleanClause clause : booleanQuery) {}</pre>
   */
  public final Iterator<BooleanClause> iterator() { return clauses().iterator(); }

  /**
   * Expert: the Weight for BooleanQuery, used to
   * normalize, score and explain these queries.
   *
   * <p>NOTE: this API and implementation is subject to
   * change suddenly in the next release.</p>
   */
  protected class BooleanWeight extends Weight {
    /** The Similarity implementation. */
    protected SimilarityProvider similarityProvider;
    protected ArrayList<Weight> weights;
    protected int maxCoord;  // num optional + num required
    private final boolean disableCoord;
    private final boolean termConjunction;

    public BooleanWeight(IndexSearcher searcher, boolean disableCoord)
      throws IOException {
      this.similarityProvider = searcher.getSimilarityProvider();
      this.disableCoord = disableCoord;
      weights = new ArrayList<Weight>(clauses.size());
      boolean termConjunction = clauses.isEmpty() || minNrShouldMatch != 0 ? false : true;
      for (int i = 0 ; i < clauses.size(); i++) {
        BooleanClause c = clauses.get(i);
        Weight w = c.getQuery().createWeight(searcher);
        if (!(c.isRequired() && (w instanceof TermWeight))) {
          termConjunction = false;
        }
        weights.add(w);
        if (!c.isProhibited()) maxCoord++;
      }
      this.termConjunction = termConjunction;
    }

    @Override
    public Query getQuery() { return BooleanQuery.this; }

    @Override
    public float getValueForNormalization() throws IOException {
      float sum = 0.0f;
      for (int i = 0 ; i < weights.size(); i++) {
        // call sumOfSquaredWeights for all clauses in case of side effects
        float s = weights.get(i).getValueForNormalization();         // sum sub weights
        if (!clauses.get(i).isProhibited())
          // only add to sum for non-prohibited clauses
          sum += s;
      }

      sum *= getBoost() * getBoost();             // boost each sub-weight

      return sum ;
    }

    public float coord(int overlap, int maxOverlap) {
      return similarityProvider.coord(overlap, maxOverlap);
    }

    @Override
    public void normalize(float norm, float topLevelBoost) {
      topLevelBoost *= getBoost();                         // incorporate boost
      for (Weight w : weights) {
        // normalize all clauses, (even if prohibited in case of side affects)
        w.normalize(norm, topLevelBoost);
      }
    }

    @Override
    public Explanation explain(AtomicReaderContext context, int doc)
      throws IOException {
      final int minShouldMatch =
        BooleanQuery.this.getMinimumNumberShouldMatch();
      ComplexExplanation sumExpl = new ComplexExplanation();
      sumExpl.setDescription("sum of:");
      int coord = 0;
      float sum = 0.0f;
      boolean fail = false;
      int shouldMatchCount = 0;
      Iterator<BooleanClause> cIter = clauses.iterator();
      for (Iterator<Weight> wIter = weights.iterator(); wIter.hasNext();) {
        Weight w = wIter.next();
        BooleanClause c = cIter.next();
        if (w.scorer(context, ScorerContext.def().scoreDocsInOrder(true).topScorer(true)) == null) {
          if (c.isRequired()) {
            fail = true;
            Explanation r = new Explanation(0.0f, "no match on required clause (" + c.getQuery().toString() + ")");
            sumExpl.addDetail(r);
          }
          continue;
        }
        Explanation e = w.explain(context, doc);
        if (e.isMatch()) {
          if (!c.isProhibited()) {
            sumExpl.addDetail(e);
            sum += e.getValue();
            coord++;
          } else {
            Explanation r =
              new Explanation(0.0f, "match on prohibited clause (" + c.getQuery().toString() + ")");
            r.addDetail(e);
            sumExpl.addDetail(r);
            fail = true;
          }
          if (c.getOccur() == Occur.SHOULD)
            shouldMatchCount++;
        } else if (c.isRequired()) {
          Explanation r = new Explanation(0.0f, "no match on required clause (" + c.getQuery().toString() + ")");
          r.addDetail(e);
          sumExpl.addDetail(r);
          fail = true;
        }
      }
      if (fail) {
        sumExpl.setMatch(Boolean.FALSE);
        sumExpl.setValue(0.0f);
        sumExpl.setDescription
          ("Failure to meet condition(s) of required/prohibited clause(s)");
        return sumExpl;
      } else if (shouldMatchCount < minShouldMatch) {
        sumExpl.setMatch(Boolean.FALSE);
        sumExpl.setValue(0.0f);
        sumExpl.setDescription("Failure to match minimum number "+
                               "of optional clauses: " + minShouldMatch);
        return sumExpl;
      }
      
      sumExpl.setMatch(0 < coord ? Boolean.TRUE : Boolean.FALSE);
      sumExpl.setValue(sum);
      
      final float coordFactor = disableCoord ? 1.0f : coord(coord, maxCoord);
      if (coordFactor == 1.0f) {
        return sumExpl;                             // eliminate wrapper
      } else {
        ComplexExplanation result = new ComplexExplanation(sumExpl.isMatch(),
                                                           sum*coordFactor,
                                                           "product of:");
        result.addDetail(sumExpl);
        result.addDetail(new Explanation(coordFactor,
                                         "coord("+coord+"/"+maxCoord+")"));
        return result;
      }
    }

    @Override
    public Scorer scorer(AtomicReaderContext context, ScorerContext scorerContext)
        throws IOException {
      if (termConjunction) {
        // specialized scorer for term conjunctions
        return createConjunctionTermScorer(context);
      }
      List<Scorer> required = new ArrayList<Scorer>();
      List<Scorer> prohibited = new ArrayList<Scorer>();
      List<Scorer> optional = new ArrayList<Scorer>();
      Iterator<BooleanClause> cIter = clauses.iterator();
      for (Weight w  : weights) {
        BooleanClause c =  cIter.next();
        Scorer subScorer = w.scorer(context, ScorerContext.def());
        if (subScorer == null) {
          if (c.isRequired()) {
            return null;
          }
        } else if (c.isRequired()) {
          required.add(subScorer);
        } else if (c.isProhibited()) {
          prohibited.add(subScorer);
        } else {
          optional.add(subScorer);
        }
      }
      
      // Check if we can return a BooleanScorer
      if (!scorerContext.scoreDocsInOrder && scorerContext.topScorer && required.size() == 0 && prohibited.size() < 32) {
        return new BooleanScorer(this, disableCoord, minNrShouldMatch, optional, prohibited, maxCoord);
      }
      
      if (required.size() == 0 && optional.size() == 0) {
        // no required and optional clauses.
        return null;
      } else if (optional.size() < minNrShouldMatch) {
        // either >1 req scorer, or there are 0 req scorers and at least 1
        // optional scorer. Therefore if there are not enough optional scorers
        // no documents will be matched by the query
        return null;
      }
      
      // Return a BooleanScorer2
      return new BooleanScorer2(this, disableCoord, minNrShouldMatch, required, prohibited, optional, maxCoord);
    }

    private Scorer createConjunctionTermScorer(AtomicReaderContext context)
        throws IOException {
      final DocsAndFreqs[] docsAndFreqs = new DocsAndFreqs[weights.size()];
      for (int i = 0; i < docsAndFreqs.length; i++) {
        final TermWeight weight = (TermWeight) weights.get(i);
        final TermsEnum termsEnum = weight.getTermsEnum(context);
        if (termsEnum == null) {
          return null;
        }
        final ExactDocScorer docScorer = weight.createDocScorer(context);
        docsAndFreqs[i] = new DocsAndFreqs(termsEnum.docs(
            context.reader.getLiveDocs(), null), termsEnum.docFreq(), docScorer);
      }
      return new ConjunctionTermScorer(this, disableCoord ? 1.0f : coord(
          docsAndFreqs.length, docsAndFreqs.length), docsAndFreqs);
    }
    
    @Override
    public boolean scoresDocsOutOfOrder() {
      int numProhibited = 0;
      for (BooleanClause c : clauses) {
        if (c.isRequired()) {
          return false; // BS2 (in-order) will be used by scorer()
        } else if (c.isProhibited()) {
          ++numProhibited;
        }
      }
      
      if (numProhibited > 32) { // cannot use BS
        return false;
      }
      
      // scorer() will return an out-of-order scorer if requested.
      return true;
    }
    
  }

  @Override
  public Weight createWeight(IndexSearcher searcher) throws IOException {
    return new BooleanWeight(searcher, disableCoord);
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    if (minNrShouldMatch == 0 && clauses.size() == 1) {                    // optimize 1-clause queries
      BooleanClause c = clauses.get(0);
      if (!c.isProhibited()) {			  // just return clause

        Query query = c.getQuery().rewrite(reader);    // rewrite first

        if (getBoost() != 1.0f) {                 // incorporate boost
          if (query == c.getQuery()) {                   // if rewrite was no-op
            query = (Query)query.clone();         // then clone before boost
          }
          // Since the BooleanQuery only has 1 clause, the BooleanQuery will be
          // written out. Therefore the rewritten Query's boost must incorporate both
          // the clause's boost, and the boost of the BooleanQuery itself
          query.setBoost(getBoost() * query.getBoost());
        }

        return query;
      }
    }

    BooleanQuery clone = null;                    // recursively rewrite
    for (int i = 0 ; i < clauses.size(); i++) {
      BooleanClause c = clauses.get(i);
      Query query = c.getQuery().rewrite(reader);
      if (query != c.getQuery()) {                     // clause rewrote: must clone
        if (clone == null) {
          // The BooleanQuery clone is lazily initialized so only initialize
          // it if a rewritten clause differs from the original clause (and hasn't been
          // initialized already).  If nothing differs, the clone isn't needlessly created
          clone = (BooleanQuery)this.clone();
        }
        clone.clauses.set(i, new BooleanClause(query, c.getOccur()));
      }
    }
    if (clone != null) {
      return clone;                               // some clauses rewrote
    } else
      return this;                                // no clauses rewrote
  }

  // inherit javadoc
  @Override
  public void extractTerms(Set<Term> terms) {
      for (BooleanClause clause : clauses) {
          clause.getQuery().extractTerms(terms);
        }
  }

  @Override @SuppressWarnings("unchecked")
  public Object clone() {
    BooleanQuery clone = (BooleanQuery)super.clone();
    clone.clauses = (ArrayList<BooleanClause>) this.clauses.clone();
    return clone;
  }

  /** Prints a user-readable version of this query. */
  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    boolean needParens=(getBoost() != 1.0) || (getMinimumNumberShouldMatch()>0) ;
    if (needParens) {
      buffer.append("(");
    }

    for (int i = 0 ; i < clauses.size(); i++) {
      BooleanClause c = clauses.get(i);
      if (c.isProhibited())
        buffer.append("-");
      else if (c.isRequired())
        buffer.append("+");

      Query subQuery = c.getQuery();
      if (subQuery != null) {
        if (subQuery instanceof BooleanQuery) {	  // wrap sub-bools in parens
          buffer.append("(");
          buffer.append(subQuery.toString(field));
          buffer.append(")");
        } else {
          buffer.append(subQuery.toString(field));
        }
      } else {
        buffer.append("null");
      }

      if (i != clauses.size()-1)
        buffer.append(" ");
    }

    if (needParens) {
      buffer.append(")");
    }

    if (getMinimumNumberShouldMatch()>0) {
      buffer.append('~');
      buffer.append(getMinimumNumberShouldMatch());
    }

    if (getBoost() != 1.0f)
    {
      buffer.append(ToStringUtils.boost(getBoost()));
    }

    return buffer.toString();
  }

  /** Returns true iff <code>o</code> is equal to this. */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof BooleanQuery))
      return false;
    BooleanQuery other = (BooleanQuery)o;
    return (this.getBoost() == other.getBoost())
        && this.clauses.equals(other.clauses)
        && this.getMinimumNumberShouldMatch() == other.getMinimumNumberShouldMatch()
        && this.disableCoord == other.disableCoord;
  }

  /** Returns a hash code value for this object.*/
  @Override
  public int hashCode() {
    return Float.floatToIntBits(getBoost()) ^ clauses.hashCode()
      + getMinimumNumberShouldMatch() + (disableCoord ? 17:0);
  }
  
}
