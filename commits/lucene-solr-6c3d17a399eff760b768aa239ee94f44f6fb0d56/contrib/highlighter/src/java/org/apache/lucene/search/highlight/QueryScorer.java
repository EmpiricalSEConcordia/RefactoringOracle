package org.apache.lucene.search.highlight;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.util.StringHelper;

/**
 * {@link Scorer} implementation which scores text fragments by the number of
 * unique query terms found. This class converts appropriate {@link Query}s to
 * {@link SpanQuery}s and attempts to score only those terms that participated in
 * generating the 'hit' on the document.
 */
public class QueryScorer implements Scorer {
  private float totalScore;
  private Set foundTerms;
  private Map fieldWeightedSpanTerms;
  private float maxTermWeight;
  private int position = -1;
  private String defaultField;
  private TermAttribute termAtt;
  private PositionIncrementAttribute posIncAtt;
  private boolean expandMultiTermQuery = true;
  private Query query;
  private String field;
  private IndexReader reader;
  private boolean skipInitExtractor;

  /**
   * @param query Query to use for highlighting
   */
  public QueryScorer(Query query) {
    init(query, null, null, true);
  }

  /**
   * @param query Query to use for highlighting
   * @param field Field to highlight - pass null to ignore fields
   */
  public QueryScorer(Query query, String field) {
    init(query, field, null, true);
  }

  /**
   * @param query Query to use for highlighting
   * @param field Field to highlight - pass null to ignore fields
   * @param reader {@link IndexReader} to use for quasi tf/idf scoring
   */
  public QueryScorer(Query query, IndexReader reader, String field) {
    init(query, field, reader, true);
  }


  /**
   * @param query to use for highlighting
   * @param reader {@link IndexReader} to use for quasi tf/idf scoring
   * @param field to highlight - pass null to ignore fields
   * @param defaultField
   */
  public QueryScorer(Query query, IndexReader reader, String field, String defaultField) {
    this.defaultField = StringHelper.intern(defaultField);
    init(query, field, reader, true);
  }

  /**
   * @param defaultField - The default field for queries with the field name unspecified
   */
  public QueryScorer(Query query, String field, String defaultField) {
    this.defaultField = StringHelper.intern(defaultField);
    init(query, field, null, true);
  }

  /**
   * @param weightedTerms an array of pre-created {@link WeightedSpanTerm}s
   */
  public QueryScorer(WeightedSpanTerm[] weightedTerms) {
    this.fieldWeightedSpanTerms = new HashMap(weightedTerms.length);

    for (int i = 0; i < weightedTerms.length; i++) {
      WeightedSpanTerm existingTerm = (WeightedSpanTerm) fieldWeightedSpanTerms.get(weightedTerms[i].term);

      if ((existingTerm == null) ||
            (existingTerm.weight < weightedTerms[i].weight)) {
        // if a term is defined more than once, always use the highest
        // scoring weight
        fieldWeightedSpanTerms.put(weightedTerms[i].term, weightedTerms[i]);
        maxTermWeight = Math.max(maxTermWeight, weightedTerms[i].getWeight());
      }
    }
    skipInitExtractor = true;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.lucene.search.highlight.Scorer#getFragmentScore()
   */
  public float getFragmentScore() {
    return totalScore;
  }

  /**
   *
   * @return The highest weighted term (useful for passing to
   *         GradientFormatter to set top end of coloring scale).
   */
  public float getMaxTermWeight() {
    return maxTermWeight;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.lucene.search.highlight.Scorer#getTokenScore(org.apache.lucene.analysis.Token,
   *      int)
   */
  public float getTokenScore() {
    position += posIncAtt.getPositionIncrement();
    String termText = termAtt.term();

    WeightedSpanTerm weightedSpanTerm;

    if ((weightedSpanTerm = (WeightedSpanTerm) fieldWeightedSpanTerms.get(
              termText)) == null) {
      return 0;
    }

    if (weightedSpanTerm.positionSensitive &&
          !weightedSpanTerm.checkPosition(position)) {
      return 0;
    }

    float score = weightedSpanTerm.getWeight();

    // found a query term - is it unique in this doc?
    if (!foundTerms.contains(termText)) {
      totalScore += score;
      foundTerms.add(termText);
    }

    return score;
  }

  /* (non-Javadoc)
   * @see org.apache.lucene.search.highlight.Scorer#init(org.apache.lucene.analysis.TokenStream)
   */
  public TokenStream init(TokenStream tokenStream) throws IOException {
    position = -1;
    termAtt = (TermAttribute) tokenStream.addAttribute(TermAttribute.class);
    posIncAtt = (PositionIncrementAttribute) tokenStream.addAttribute(PositionIncrementAttribute.class);
    if(!skipInitExtractor) {
      if(fieldWeightedSpanTerms != null) {
        fieldWeightedSpanTerms.clear();
      }
      return initExtractor(tokenStream);
    }
    return null;
  }
  
  /**
   * Retrieve the {@link WeightedSpanTerm} for the specified token. Useful for passing
   * Span information to a {@link Fragmenter}.
   *
   * @param token to get {@link WeightedSpanTerm} for
   * @return WeightedSpanTerm for token
   */
  public WeightedSpanTerm getWeightedSpanTerm(String token) {
    return (WeightedSpanTerm) fieldWeightedSpanTerms.get(token);
  }

  /**
   */
  private void init(Query query, String field, IndexReader reader, boolean expandMultiTermQuery) {
    this.reader = reader;
    this.expandMultiTermQuery = expandMultiTermQuery;
    this.query = query;
    this.field = field;
  }
  
  private TokenStream initExtractor(TokenStream tokenStream) throws IOException {
    WeightedSpanTermExtractor qse = defaultField == null ? new WeightedSpanTermExtractor()
        : new WeightedSpanTermExtractor(defaultField);

    qse.setExpandMultiTermQuery(expandMultiTermQuery);
    if (reader == null) {
      this.fieldWeightedSpanTerms = qse.getWeightedSpanTerms(query,
          tokenStream, field);
    } else {
      this.fieldWeightedSpanTerms = qse.getWeightedSpanTermsWithScores(query,
          tokenStream, field, reader);
    }
    if(qse.isCachedTokenStream()) {
      return qse.getTokenStream();
    }
    
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.lucene.search.highlight.Scorer#startFragment(org.apache.lucene.search.highlight.TextFragment)
   */
  public void startFragment(TextFragment newFragment) {
    foundTerms = new HashSet();
    totalScore = 0;
  }
  
  /**
   * @return true if multi-term queries should be expanded
   */
  public boolean isExpandMultiTermQuery() {
    return expandMultiTermQuery;
  }

  /**
   * Controls whether or not multi-term queries are expanded
   * against a {@link MemoryIndex} {@link IndexReader}.
   * 
   * @param expandMultiTermQuery true if multi-term queries should be expanded
   */
  public void setExpandMultiTermQuery(boolean expandMultiTermQuery) {
    this.expandMultiTermQuery = expandMultiTermQuery;
  }
}
