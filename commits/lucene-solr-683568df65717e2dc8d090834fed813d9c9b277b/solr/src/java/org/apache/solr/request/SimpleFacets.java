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

package org.apache.solr.request;

import org.apache.lucene.index.*;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.packed.Direct16;
import org.apache.lucene.util.packed.Direct32;
import org.apache.lucene.util.packed.Direct8;
import org.apache.lucene.util.packed.PackedInts;
import org.apache.noggit.CharArr;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.RequiredSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams.FacetDateOther;
import org.apache.solr.common.params.FacetParams.FacetDateInclude;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.core.SolrCore;
import org.apache.solr.schema.*;
import org.apache.solr.search.*;
import org.apache.solr.util.BoundedTreeSet;
import org.apache.solr.util.ByteUtils;
import org.apache.solr.util.DateMathParser;
import org.apache.solr.handler.component.ResponseBuilder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A class that generates simple Facet information for a request.
 *
 * More advanced facet implementations may compose or subclass this class 
 * to leverage any of it's functionality.
 */
public class SimpleFacets {

  /** The main set of documents all facet counts should be relative to */
  protected DocSet docs;
  /** Configuration params behavior should be driven by */
  protected SolrParams params;
  /** Searcher to use for all calculations */
  protected SolrIndexSearcher searcher;
  protected SolrQueryRequest req;
  protected ResponseBuilder rb;

  // per-facet values
  SolrParams localParams; // localParams on this particular facet command
  String facetValue;      // the field to or query to facet on (minus local params)
  DocSet base;            // the base docset for this particular facet
  String key;             // what name should the results be stored under
  int threads;

  public SimpleFacets(SolrQueryRequest req,
                      DocSet docs,
                      SolrParams params) {
    this(req,docs,params,null);
  }

  public SimpleFacets(SolrQueryRequest req,
                      DocSet docs,
                      SolrParams params,
                      ResponseBuilder rb) {
    this.req = req;
    this.searcher = req.getSearcher();
    this.base = this.docs = docs;
    this.params = params;
    this.rb = rb;
  }


  void parseParams(String type, String param) throws ParseException, IOException {
    localParams = QueryParsing.getLocalParams(param, req.getParams());
    base = docs;
    facetValue = param;
    key = param;
    threads = -1;

    if (localParams == null) return;

    // remove local params unless it's a query
    if (type != FacetParams.FACET_QUERY) {
      facetValue = localParams.get(CommonParams.VALUE);
    }

    // reset set the default key now that localParams have been removed
    key = facetValue;

    // allow explicit set of the key
    key = localParams.get(CommonParams.OUTPUT_KEY, key);

    String threadStr = localParams.get(CommonParams.THREADS);
    if (threadStr != null) {
      threads = Integer.parseInt(threadStr);
    }

    // figure out if we need a new base DocSet
    String excludeStr = localParams.get(CommonParams.EXCLUDE);
    if (excludeStr == null) return;

    Map tagMap = (Map)req.getContext().get("tags");
    if (tagMap != null && rb != null) {
      List<String> excludeTagList = StrUtils.splitSmart(excludeStr,',');

      IdentityHashMap<Query,Boolean> excludeSet = new IdentityHashMap<Query,Boolean>();
      for (String excludeTag : excludeTagList) {
        Object olst = tagMap.get(excludeTag);
        // tagMap has entries of List<String,List<QParser>>, but subject to change in the future
        if (!(olst instanceof Collection)) continue;
        for (Object o : (Collection)olst) {
          if (!(o instanceof QParser)) continue;
          QParser qp = (QParser)o;
          excludeSet.put(qp.getQuery(), Boolean.TRUE);
        }
      }
      if (excludeSet.size() == 0) return;

      List<Query> qlist = new ArrayList<Query>();

      // add the base query
      qlist.add(rb.getQuery());

      // add the filters
      for (Query q : rb.getFilters()) {
        if (!excludeSet.containsKey(q)) {
          qlist.add(q);
        }

      }

      // get the new base docset for this facet
      base = searcher.getDocSet(qlist);
    }

  }


  /**
   * Looks at various Params to determing if any simple Facet Constraint count
   * computations are desired.
   *
   * @see #getFacetQueryCounts
   * @see #getFacetFieldCounts
   * @see #getFacetDateCounts
   * @see FacetParams#FACET
   * @return a NamedList of Facet Count info or null
   */
  public NamedList getFacetCounts() {

    // if someone called this method, benefit of the doubt: assume true
    if (!params.getBool(FacetParams.FACET,true))
      return null;

    NamedList res = new SimpleOrderedMap();
    try {

      res.add("facet_queries", getFacetQueryCounts());
      res.add("facet_fields", getFacetFieldCounts());
      res.add("facet_dates", getFacetDateCounts());
      
    } catch (Exception e) {
      SolrException.logOnce(SolrCore.log, "Exception during facet counts", e);
      res.add("exception", SolrException.toStr(e));
    }
    return res;
  }

  /**
   * Returns a list of facet counts for each of the facet queries 
   * specified in the params
   *
   * @see FacetParams#FACET_QUERY
   */
  public NamedList getFacetQueryCounts() throws IOException,ParseException {

    NamedList res = new SimpleOrderedMap();

    /* Ignore CommonParams.DF - could have init param facet.query assuming
     * the schema default with query param DF intented to only affect Q.
     * If user doesn't want schema default for facet.query, they should be
     * explicit.
     */
    // SolrQueryParser qp = searcher.getSchema().getSolrQueryParser(null);

    String[] facetQs = params.getParams(FacetParams.FACET_QUERY);
    if (null != facetQs && 0 != facetQs.length) {
      for (String q : facetQs) {
        parseParams(FacetParams.FACET_QUERY, q);

        // TODO: slight optimization would prevent double-parsing of any localParams
        Query qobj = QParser.getParser(q, null, req).getQuery();
        res.add(key, searcher.numDocs(qobj, base));
      }
    }

    return res;
  }


  public NamedList getTermCounts(String field) throws IOException {
    int offset = params.getFieldInt(field, FacetParams.FACET_OFFSET, 0);
    int limit = params.getFieldInt(field, FacetParams.FACET_LIMIT, 100);
    if (limit == 0) return new NamedList();
    Integer mincount = params.getFieldInt(field, FacetParams.FACET_MINCOUNT);
    if (mincount==null) {
      Boolean zeros = params.getFieldBool(field, FacetParams.FACET_ZEROS);
      // mincount = (zeros!=null && zeros) ? 0 : 1;
      mincount = (zeros!=null && !zeros) ? 1 : 0;
      // current default is to include zeros.
    }
    boolean missing = params.getFieldBool(field, FacetParams.FACET_MISSING, false);
    // default to sorting if there is a limit.
    String sort = params.getFieldParam(field, FacetParams.FACET_SORT, limit>0 ? FacetParams.FACET_SORT_COUNT : FacetParams.FACET_SORT_INDEX);
    String prefix = params.getFieldParam(field,FacetParams.FACET_PREFIX);


    NamedList counts;
    SchemaField sf = searcher.getSchema().getField(field);
    FieldType ft = sf.getType();

    // determine what type of faceting method to use
    String method = params.getFieldParam(field, FacetParams.FACET_METHOD);
    boolean enumMethod = FacetParams.FACET_METHOD_enum.equals(method);

    // TODO: default to per-segment or not?
    boolean per_segment = FacetParams.FACET_METHOD_fcs.equals(method);

    if (method == null && ft instanceof BoolField) {
      // Always use filters for booleans... we know the number of values is very small.
      enumMethod = true;
    }
    boolean multiToken = sf.multiValued() || ft.multiValuedFieldCache();

    if (TrieField.getMainValuePrefix(ft) != null) {
      // A TrieField with multiple parts indexed per value... currently only
      // UnInvertedField can handle this case, so force it's use.
      enumMethod = false;
      multiToken = true;
    }

    // unless the enum method is explicitly specified, use a counting method.
    if (enumMethod) {
      counts = getFacetTermEnumCounts(searcher, base, field, offset, limit, mincount,missing,sort,prefix);
    } else {
      if (multiToken) {
        UnInvertedField uif = UnInvertedField.getUnInvertedField(field, searcher);
        counts = uif.getCounts(searcher, base, offset, limit, mincount,missing,sort,prefix);
      } else {
        // TODO: future logic could use filters instead of the fieldcache if
        // the number of terms in the field is small enough.

        if (per_segment) {
          PerSegmentSingleValuedFaceting ps = new PerSegmentSingleValuedFaceting(searcher, base, field, offset,limit, mincount, missing, sort, prefix);
          Executor executor = threads==0 ? directExecutor : facetExecutor;
          ps.setNumThreads(threads);
          counts = ps.getFacetCounts(executor);
        } else {
          counts = getFieldCacheCounts(searcher, base, field, offset,limit, mincount, missing, sort, prefix);         
        }

      }
    }

    return counts;
  }


  static final Executor directExecutor = new Executor() {
    public void execute(Runnable r) {
      r.run();
    }
  };

  static final Executor facetExecutor = new ThreadPoolExecutor(
          0,
          Integer.MAX_VALUE,
          10, TimeUnit.SECONDS, // terminate idle threads after 10 sec
          new SynchronousQueue<Runnable>()  // directly hand off tasks
  );
  
  /**
   * Returns a list of value constraints and the associated facet counts 
   * for each facet field specified in the params.
   *
   * @see FacetParams#FACET_FIELD
   * @see #getFieldMissingCount
   * @see #getFacetTermEnumCounts
   */
  public NamedList getFacetFieldCounts()
          throws IOException, ParseException {

    NamedList res = new SimpleOrderedMap();
    String[] facetFs = params.getParams(FacetParams.FACET_FIELD);
    if (null != facetFs) {
      for (String f : facetFs) {
        parseParams(FacetParams.FACET_FIELD, f);
        String termList = localParams == null ? null : localParams.get(CommonParams.TERMS);
        if (termList != null) {
          res.add(key, getListedTermCounts(facetValue, termList));
        } else {
          res.add(key, getTermCounts(facetValue));
        }
      }
    }
    return res;
  }


  private NamedList getListedTermCounts(String field, String termList) throws IOException {
    FieldType ft = searcher.getSchema().getFieldType(field);
    List<String> terms = StrUtils.splitSmart(termList, ",", true);
    NamedList res = new NamedList();
    Term t = new Term(field);
    for (String term : terms) {
      String internal = ft.toInternal(term);
      int count = searcher.numDocs(new TermQuery(t.createTerm(internal)), base);
      res.add(term, count);
    }
    return res;    
  }


  /**
   * Returns a count of the documents in the set which do not have any 
   * terms for for the specified field.
   *
   * @see FacetParams#FACET_MISSING
   */
  public static int getFieldMissingCount(SolrIndexSearcher searcher, DocSet docs, String fieldName)
    throws IOException {

    DocSet hasVal = searcher.getDocSet
      (new TermRangeQuery(fieldName, null, null, false, false));
    return docs.andNotSize(hasVal);
  }


  /**
   * Use the Lucene FieldCache to get counts for each unique field value in <code>docs</code>.
   * The field must have at most one indexed token per document.
   */
  public static NamedList getFieldCacheCounts(SolrIndexSearcher searcher, DocSet docs, String fieldName, int offset, int limit, int mincount, boolean missing, String sort, String prefix) throws IOException {
    // TODO: If the number of terms is high compared to docs.size(), and zeros==false,
    //  we should use an alternate strategy to avoid
    //  1) creating another huge int[] for the counts
    //  2) looping over that huge int[] looking for the rare non-zeros.
    //
    // Yet another variation: if docs.size() is small and termvectors are stored,
    // then use them instead of the FieldCache.
    //

    // TODO: this function is too big and could use some refactoring, but
    // we also need a facet cache, and refactoring of SimpleFacets instead of
    // trying to pass all the various params around.

    FieldType ft = searcher.getSchema().getFieldType(fieldName);
    NamedList res = new NamedList();

    FieldCache.DocTermsIndex si = FieldCache.DEFAULT.getTermsIndex(searcher.getReader(), fieldName);

    final BytesRef prefixRef;
    if (prefix == null) {
      prefixRef = null;
    } else if (prefix.length()==0) {
      prefix = null;
      prefixRef = null;
    } else {
      prefixRef = new BytesRef(prefix);
    }

    final BytesRef br = new BytesRef();

    int startTermIndex, endTermIndex;
    if (prefix!=null) {
      startTermIndex = si.binarySearchLookup(prefixRef, br);
      if (startTermIndex<0) startTermIndex=-startTermIndex-1;
      // find the end term.  \uffff isn't a legal unicode char, but only compareTo
      // is used, so it should be fine, and is guaranteed to be bigger than legal chars.
      endTermIndex = si.binarySearchLookup(new BytesRef(prefix+"\uffff\uffff\uffff\uffff"), br);
      assert endTermIndex < 0;
      endTermIndex = -endTermIndex-1;
    } else {
      startTermIndex=0;
      endTermIndex=si.numOrd();
    }

    final int nTerms=endTermIndex-startTermIndex;

    CharArr spare = new CharArr();

    if (nTerms>0 && docs.size() >= mincount) {

      // count collection array only needs to be as big as the number of terms we are
      // going to collect counts for.
      final int[] counts = new int[nTerms];

      DocIterator iter = docs.iterator();

      PackedInts.Reader ordReader = si.getDocToOrd();
      if (ordReader instanceof Direct32) {
        int[] ords = ((Direct32)ordReader).getArray();
        if (prefix==null) {
          while (iter.hasNext()) {
            counts[ords[iter.nextDoc()]]++;
          }
        } else {
          while (iter.hasNext()) {
            int term = ords[iter.nextDoc()];
            int arrIdx = term-startTermIndex;
            if (arrIdx>=0 && arrIdx<nTerms) counts[arrIdx]++;
          }
        }
      } else if (ordReader instanceof Direct16) {
        short[] ords = ((Direct16)ordReader).getArray();
        if (prefix==null) {
          while (iter.hasNext()) {
            counts[ords[iter.nextDoc()] & 0xffff]++;
          }
        } else {
          while (iter.hasNext()) {
            int term = ords[iter.nextDoc()] & 0xffff;
            int arrIdx = term-startTermIndex;
            if (arrIdx>=0 && arrIdx<nTerms) counts[arrIdx]++;
          }
        }
      } else if (ordReader instanceof Direct8) {
        byte[] ords = ((Direct8)ordReader).getArray();
        if (prefix==null) {
          while (iter.hasNext()) {
            counts[ords[iter.nextDoc()] & 0xff]++;
          }
        } else {
          while (iter.hasNext()) {
            int term = ords[iter.nextDoc()] & 0xff;
            int arrIdx = term-startTermIndex;
            if (arrIdx>=0 && arrIdx<nTerms) counts[arrIdx]++;
          }
        }
      } else {
        while (iter.hasNext()) {
          int term = si.getOrd(iter.nextDoc());
          int arrIdx = term-startTermIndex;
          if (arrIdx>=0 && arrIdx<nTerms) counts[arrIdx]++;
        }
      }

      // IDEA: we could also maintain a count of "other"... everything that fell outside
      // of the top 'N'

      int off=offset;
      int lim=limit>=0 ? limit : Integer.MAX_VALUE;

      if (sort.equals(FacetParams.FACET_SORT_COUNT) || sort.equals(FacetParams.FACET_SORT_COUNT_LEGACY)) {
        int maxsize = limit>0 ? offset+limit : Integer.MAX_VALUE-1;
        maxsize = Math.min(maxsize, nTerms);
        final BoundedTreeSet<CountPair<BytesRef,Integer>> queue = new BoundedTreeSet<CountPair<BytesRef,Integer>>(maxsize);
        int min=mincount-1;  // the smallest value in the top 'N' values
        for (int i=(startTermIndex==0)?1:0; i<nTerms; i++) {
          int c = counts[i];
          if (c>min) {
            // NOTE: we use c>min rather than c>=min as an optimization because we are going in
            // index order, so we already know that the keys are ordered.  This can be very
            // important if a lot of the counts are repeated (like zero counts would be).
            queue.add(new CountPair<BytesRef,Integer>(si.lookup(startTermIndex+i, new BytesRef()), c));
            if (queue.size()>=maxsize) min=queue.last().val;
          }
        }
        // now select the right page from the results
        for (CountPair<BytesRef,Integer> p : queue) {
          if (--off>=0) continue;
          if (--lim<0) break;
          spare.reset();
          ft.indexedToReadable(p.key, spare);
          res.add(spare.toString(), p.val);
        }
      } else {
        // add results in index order
        int i=(startTermIndex==0)?1:0;
        if (mincount<=0) {
          // if mincount<=0, then we won't discard any terms and we know exactly
          // where to start.
          i+=off;
          off=0;
        }

        for (; i<nTerms; i++) {          
          int c = counts[i];
          if (c<mincount || --off>=0) continue;
          if (--lim<0) break;
          spare.reset();
          ft.indexedToReadable(si.lookup(startTermIndex+i, br), spare);
          res.add(spare.toString(), c);
        }
      }
    }

    if (missing) {
      res.add(null, getFieldMissingCount(searcher,docs,fieldName));
    }
    
    return res;
  }


  /**
   * Returns a list of terms in the specified field along with the 
   * corresponding count of documents in the set that match that constraint.
   * This method uses the FilterCache to get the intersection count between <code>docs</code>
   * and the DocSet for each term in the filter.
   *
   * @see FacetParams#FACET_LIMIT
   * @see FacetParams#FACET_ZEROS
   * @see FacetParams#FACET_MISSING
   */
  public NamedList getFacetTermEnumCounts(SolrIndexSearcher searcher, DocSet docs, String field, int offset, int limit, int mincount, boolean missing, String sort, String prefix)
    throws IOException {

    /* :TODO: potential optimization...
    * cache the Terms with the highest docFreq and try them first
    * don't enum if we get our max from them
    */

    // Minimum term docFreq in order to use the filterCache for that term.
    int minDfFilterCache = params.getFieldInt(field, FacetParams.FACET_ENUM_CACHE_MINDF, 0);

    // make sure we have a set that is fast for random access, if we will use it for that
    DocSet fastForRandomSet = docs;
    if (minDfFilterCache>0 && docs instanceof SortedIntDocSet) {
      SortedIntDocSet sset = (SortedIntDocSet)docs;
      fastForRandomSet = new HashDocSet(sset.getDocs(), 0, sset.size());
    }


    IndexSchema schema = searcher.getSchema();
    IndexReader r = searcher.getReader();
    FieldType ft = schema.getFieldType(field);

    boolean sortByCount = sort.equals("count") || sort.equals("true");
    final int maxsize = limit>=0 ? offset+limit : Integer.MAX_VALUE-1;
    final BoundedTreeSet<CountPair<BytesRef,Integer>> queue = sortByCount ? new BoundedTreeSet<CountPair<BytesRef,Integer>>(maxsize) : null;
    final NamedList res = new NamedList();

    int min=mincount-1;  // the smallest value in the top 'N' values    
    int off=offset;
    int lim=limit>=0 ? limit : Integer.MAX_VALUE;

    BytesRef startTermBytes = null;
    if (prefix != null) {
      String indexedPrefix = ft.toInternal(prefix);
      startTermBytes = new BytesRef(indexedPrefix);
    }

    Fields fields = MultiFields.getFields(r);
    Terms terms = fields==null ? null : fields.terms(field);
    TermsEnum termsEnum = null;
    SolrIndexSearcher.DocsEnumState deState = null;
    BytesRef term = null;
    if (terms != null) {
      termsEnum = terms.iterator();

      // TODO: OPT: if seek(ord) is supported for this termsEnum, then we could use it for
      // facet.offset when sorting by index order.

      if (startTermBytes != null) {
        if (termsEnum.seek(startTermBytes, true) == TermsEnum.SeekStatus.END) {
          termsEnum = null;
        } else {
          term = termsEnum.term();
        }
      } else {
        // position termsEnum on first term
        term = termsEnum.next();
      }
    }

    Term template = new Term(field);
    DocsEnum docsEnum = null;
    CharArr spare = new CharArr();

    if (docs.size() >= mincount) {
      while (term != null) {

        if (startTermBytes != null && !term.startsWith(startTermBytes))
          break;

        int df = termsEnum.docFreq();

        // If we are sorting, we can use df>min (rather than >=) since we
        // are going in index order.  For certain term distributions this can
        // make a large difference (for example, many terms with df=1).
        if (df>0 && df>min) {
          int c;

          if (df >= minDfFilterCache) {
            // use the filter cache
            // TODO: need a term query that takes a BytesRef to handle binary terms
            spare.reset();
            ByteUtils.UTF8toUTF16(term, spare);
            Term t = template.createTerm(spare.toString());

            if (deState==null) {
              deState = new SolrIndexSearcher.DocsEnumState();
              deState.deletedDocs = MultiFields.getDeletedDocs(r);
              deState.termsEnum = termsEnum;
              deState.reuse = docsEnum;
            }

            c = searcher.numDocs(new TermQuery(t), docs, deState);

            docsEnum = deState.reuse;
          } else {
            // iterate over TermDocs to calculate the intersection

            // TODO: specialize when base docset is a bitset or hash set (skipDocs)?  or does it matter for this?
            // TODO: do this per-segment for better efficiency (MultiDocsEnum just uses base class impl)
            // TODO: would passing deleted docs lead to better efficiency over checking the fastForRandomSet?
            docsEnum = termsEnum.docs(null, docsEnum);
            c=0;

            if (docsEnum instanceof MultiDocsEnum) {
              MultiDocsEnum.EnumWithSlice[] subs = ((MultiDocsEnum)docsEnum).getSubs();
              int numSubs = ((MultiDocsEnum)docsEnum).getNumSubs();
              for (int subindex = 0; subindex<numSubs; subindex++) {
                MultiDocsEnum.EnumWithSlice sub = subs[subindex];
                if (sub.docsEnum == null) continue;
                DocsEnum.BulkReadResult bulk = sub.docsEnum.getBulkResult();
                int base = sub.slice.start;
                for (;;) {
                  int nDocs = sub.docsEnum.read();
                  if (nDocs == 0) break;
                  int[] docArr = bulk.docs.ints;  // this might be movable outside the loop, but perhaps not worth the risk.
                  int end = bulk.docs.offset + nDocs;
                  for (int i=bulk.docs.offset; i<end; i++) {
                    if (fastForRandomSet.exists(docArr[i]+base)) c++;
                  }
                }
              }
            } else {

              // this should be the same bulk result object if sharing of the docsEnum succeeded
              DocsEnum.BulkReadResult bulk = docsEnum.getBulkResult();

              for (;;) {
                int nDocs = docsEnum.read();
                if (nDocs == 0) break;
                int[] docArr = bulk.docs.ints;  // this might be movable outside the loop, but perhaps not worth the risk.
                int end = bulk.docs.offset + nDocs;
                for (int i=bulk.docs.offset; i<end; i++) {
                  if (fastForRandomSet.exists(docArr[i])) c++;
                }
              }
            }
            

          }

          if (sortByCount) {
            if (c>min) {
              BytesRef termCopy = new BytesRef(term);
              queue.add(new CountPair<BytesRef,Integer>(termCopy, c));
              if (queue.size()>=maxsize) min=queue.last().val;
            }
          } else {
            if (c >= mincount && --off<0) {
              if (--lim<0) break;
              spare.reset();
              ft.indexedToReadable(term, spare);
              res.add(spare.toString(), c);
            }
          }
        }

        term = termsEnum.next();
      }
    }

    if (sortByCount) {
      for (CountPair<BytesRef,Integer> p : queue) {
        if (--off>=0) continue;
        if (--lim<0) break;
        spare.reset();
        ft.indexedToReadable(p.key, spare);
        res.add(spare.toString(), p.val);
      }
    }

    if (missing) {
      res.add(null, getFieldMissingCount(searcher,docs,field));
    }

    return res;
  }

  /**
   * Returns a list of value constraints and the associated facet counts 
   * for each facet date field, range, and interval specified in the
   * SolrParams
   *
   * @see FacetParams#FACET_DATE
   */
  public NamedList getFacetDateCounts()
          throws IOException, ParseException {

    final SolrParams required = new RequiredSolrParams(params);
    final NamedList resOuter = new SimpleOrderedMap();
    final String[] fields = params.getParams(FacetParams.FACET_DATE);
    final Date NOW = new Date();
    
    if (null == fields || 0 == fields.length) return resOuter;
    
    final IndexSchema schema = searcher.getSchema();
    for (String f : fields) {
      parseParams(FacetParams.FACET_DATE, f);
      f = facetValue;


      final NamedList resInner = new SimpleOrderedMap();
      resOuter.add(key, resInner);
      final SchemaField sf = schema.getField(f);
      if (! (sf.getType() instanceof DateField)) {
        throw new SolrException
          (SolrException.ErrorCode.BAD_REQUEST,
           "Can not date facet on a field which is not a DateField: " + f);
      }
      final DateField ft = (DateField) sf.getType();
      final String startS
        = required.getFieldParam(f,FacetParams.FACET_DATE_START);
      final Date start;
      try {
        start = ft.parseMath(NOW, startS);
      } catch (SolrException e) {
        throw new SolrException
          (SolrException.ErrorCode.BAD_REQUEST,
           "date facet 'start' is not a valid Date string: " + startS, e);
      }
      final String endS
        = required.getFieldParam(f,FacetParams.FACET_DATE_END);
      Date end; // not final, hardend may change this
      try {
        end = ft.parseMath(NOW, endS);
      } catch (SolrException e) {
        throw new SolrException
          (SolrException.ErrorCode.BAD_REQUEST,
           "date facet 'end' is not a valid Date string: " + endS, e);
      }
          
      if (end.before(start)) {
        throw new SolrException
          (SolrException.ErrorCode.BAD_REQUEST,
           "date facet 'end' comes before 'start': "+endS+" < "+startS);
      }

      final String gap = required.getFieldParam(f,FacetParams.FACET_DATE_GAP);
      final DateMathParser dmp = new DateMathParser(ft.UTC, Locale.US);
      dmp.setNow(NOW);

      int minCount = params.getFieldInt(f,FacetParams.FACET_MINCOUNT, 0);

      final EnumSet<FacetDateInclude> include = FacetDateInclude.parseParam
        (params.getFieldParams(f,FacetParams.FACET_DATE_INCLUDE));

      try {
        Date low = start;
        while (low.before(end)) {
          dmp.setNow(low);
          String label = ft.toExternal(low);
          
          Date high = dmp.parseMath(gap);
          if (end.before(high)) {
            if (params.getFieldBool(f,FacetParams.FACET_DATE_HARD_END,false)) {
              high = end;
            } else {
              end = high;
            }
          }
          if (high.before(low)) {
            throw new SolrException
              (SolrException.ErrorCode.BAD_REQUEST,
               "date facet infinite loop (is gap negative?)");
          }
          boolean includeLower = 
            (include.contains(FacetDateInclude.LOWER) ||
             (include.contains(FacetDateInclude.EDGE) && low.equals(start)));
          boolean includeUpper = 
            (include.contains(FacetDateInclude.UPPER) ||
             (include.contains(FacetDateInclude.EDGE) && high.equals(end)));

          int count = rangeCount(sf,low,high,includeLower,includeUpper);
          if (count >= minCount) {
            resInner.add(label, count);
          }
          low = high;
        }
      } catch (java.text.ParseException e) {
        throw new SolrException
          (SolrException.ErrorCode.BAD_REQUEST,
           "date facet 'gap' is not a valid Date Math string: " + gap, e);
      }
      
      // explicitly return the gap and end so all the counts are meaningful
      resInner.add("gap", gap);
      resInner.add("end", end);

      final String[] othersP =
        params.getFieldParams(f,FacetParams.FACET_DATE_OTHER);
      if (null != othersP && 0 < othersP.length ) {
        Set<FacetDateOther> others = EnumSet.noneOf(FacetDateOther.class);

        for (final String o : othersP) {
          others.add(FacetDateOther.get(o));
        }

        // no matter what other values are listed, we don't do
        // anything if "none" is specified.
        if (! others.contains(FacetDateOther.NONE) ) {          
          boolean all = others.contains(FacetDateOther.ALL);
        
          if (all || others.contains(FacetDateOther.BEFORE)) {
            // include upper bound if "outer" or if first gap doesn't already include it
            resInner.add(FacetDateOther.BEFORE.toString(),
                         rangeCount(sf,null,start,
                                    false,
                                    (include.contains(FacetDateInclude.OUTER) ||
                                     (! (include.contains(FacetDateInclude.LOWER) ||
                                         include.contains(FacetDateInclude.EDGE))))));
          }
          if (all || others.contains(FacetDateOther.AFTER)) {
            // include lower bound if "outer" or if last gap doesn't already include it
            resInner.add(FacetDateOther.AFTER.toString(),
                         rangeCount(sf,end,null,
                                    (include.contains(FacetDateInclude.OUTER) ||
                                     (! (include.contains(FacetDateInclude.UPPER) ||
                                         include.contains(FacetDateInclude.EDGE)))),
                                    false));
          }
          if (all || others.contains(FacetDateOther.BETWEEN)) {
            resInner.add(FacetDateOther.BETWEEN.toString(),
                         rangeCount(sf,start,end,
                                    (include.contains(FacetDateInclude.LOWER) ||
                                     include.contains(FacetDateInclude.EDGE)),
                                    (include.contains(FacetDateInclude.UPPER) ||
                                     include.contains(FacetDateInclude.EDGE))));
          }
        }
      }
    }
    
    return resOuter;
  }

  /**
   * Macro for getting the numDocs of range over docs
   * @see SolrIndexSearcher#numDocs
   * @see TermRangeQuery
   */
  protected int rangeCount(SchemaField sf, String low, String high,
                           boolean iLow, boolean iHigh) throws IOException {
    Query rangeQ = sf.getType().getRangeQuery(null, sf,low,high,iLow,iHigh);
    return searcher.numDocs(rangeQ ,base);
  }

  protected int rangeCount(SchemaField sf, Date low, Date high,
                           boolean iLow, boolean iHigh) throws IOException {
    Query rangeQ = ((DateField)(sf.getType())).getRangeQuery(null, sf,low,high,iLow,iHigh);
    return searcher.numDocs(rangeQ ,base);
  }
  
  /**
   * A simple key=>val pair whose natural order is such that 
   * <b>higher</b> vals come before lower vals.
   * In case of tie vals, then <b>lower</b> keys come before higher keys.
   */
  public static class CountPair<K extends Comparable<? super K>, V extends Comparable<? super V>>
    implements Comparable<CountPair<K,V>> {

    public CountPair(K k, V v) {
      key = k; val = v;
    }
    public K key;
    public V val;
    public int hashCode() {
      return key.hashCode() ^ val.hashCode();
    }
    public boolean equals(Object o) {
      return (o instanceof CountPair)
        && (0 == this.compareTo((CountPair<K,V>) o));
    }
    public int compareTo(CountPair<K,V> o) {
      int vc = o.val.compareTo(val);
      return (0 != vc ? vc : key.compareTo(o.key));
    }
  }
}

