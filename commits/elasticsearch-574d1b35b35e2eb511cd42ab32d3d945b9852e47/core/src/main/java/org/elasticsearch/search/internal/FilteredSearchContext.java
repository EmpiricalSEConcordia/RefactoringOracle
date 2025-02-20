/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.internal;

import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.util.Counter;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.cache.recycler.PageCacheRecycler;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.cache.bitset.BitsetFilterCache;
import org.elasticsearch.index.fielddata.IndexFieldDataService;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.object.ObjectMapper;
import org.elasticsearch.index.query.ParsedQuery;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.similarity.SimilarityService;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.search.SearchShardTarget;
import org.elasticsearch.search.aggregations.SearchContextAggregations;
import org.elasticsearch.search.dfs.DfsSearchResult;
import org.elasticsearch.search.fetch.FetchSearchResult;
import org.elasticsearch.search.fetch.FetchSubPhase;
import org.elasticsearch.search.fetch.FetchSubPhaseContext;
import org.elasticsearch.search.fetch.innerhits.InnerHitsContext;
import org.elasticsearch.search.fetch.script.ScriptFieldsContext;
import org.elasticsearch.search.fetch.source.FetchSourceContext;
import org.elasticsearch.search.highlight.SearchContextHighlight;
import org.elasticsearch.search.lookup.SearchLookup;
import org.elasticsearch.search.profile.Profilers;
import org.elasticsearch.search.query.QuerySearchResult;
import org.elasticsearch.search.rescore.RescoreSearchContext;
import org.elasticsearch.search.suggest.SuggestionSearchContext;

import java.util.List;
import java.util.Map;

public abstract class FilteredSearchContext extends SearchContext {

    private final SearchContext in;

    public FilteredSearchContext(SearchContext in) {
        //inner_hits in percolator ends up with null inner search context
        super(in == null ? ParseFieldMatcher.EMPTY : in.parseFieldMatcher());
        this.in = in;
    }

    @Override
    protected void doClose() {
        in.doClose();
    }

    @Override
    public void preProcess() {
        in.preProcess();
    }

    @Override
    public Query searchFilter(String[] types) {
        return in.searchFilter(types);
    }

    @Override
    public long id() {
        return in.id();
    }

    @Override
    public String source() {
        return in.source();
    }

    @Override
    public ShardSearchRequest request() {
        return in.request();
    }

    @Override
    public SearchType searchType() {
        return in.searchType();
    }

    @Override
    public SearchContext searchType(SearchType searchType) {
        return in.searchType(searchType);
    }

    @Override
    public SearchShardTarget shardTarget() {
        return in.shardTarget();
    }

    @Override
    public int numberOfShards() {
        return in.numberOfShards();
    }

    @Override
    public boolean hasTypes() {
        return in.hasTypes();
    }

    @Override
    public String[] types() {
        return in.types();
    }

    @Override
    public float queryBoost() {
        return in.queryBoost();
    }

    @Override
    public SearchContext queryBoost(float queryBoost) {
        return in.queryBoost(queryBoost);
    }

    @Override
    public long getOriginNanoTime() {
        return in.getOriginNanoTime();
    }

    @Override
    protected long nowInMillisImpl() {
        return in.nowInMillisImpl();
    }

    @Override
    public ScrollContext scrollContext() {
        return in.scrollContext();
    }

    @Override
    public SearchContext scrollContext(ScrollContext scroll) {
        return in.scrollContext(scroll);
    }

    @Override
    public SearchContextAggregations aggregations() {
        return in.aggregations();
    }

    @Override
    public SearchContext aggregations(SearchContextAggregations aggregations) {
        return in.aggregations(aggregations);
    }

    @Override
    public SearchContextHighlight highlight() {
        return in.highlight();
    }

    @Override
    public void highlight(SearchContextHighlight highlight) {
        in.highlight(highlight);
    }

    @Override
    public void innerHits(InnerHitsContext innerHitsContext) {
        in.innerHits(innerHitsContext);
    }

    @Override
    public InnerHitsContext innerHits() {
        return in.innerHits();
    }

    @Override
    public SuggestionSearchContext suggest() {
        return in.suggest();
    }

    @Override
    public void suggest(SuggestionSearchContext suggest) {
        in.suggest(suggest);
    }

    @Override
    public List<RescoreSearchContext> rescore() {
        return in.rescore();
    }

    @Override
    public void addRescore(RescoreSearchContext rescore) {
        in.addRescore(rescore);
    }

    @Override
    public boolean hasScriptFields() {
        return in.hasScriptFields();
    }

    @Override
    public ScriptFieldsContext scriptFields() {
        return in.scriptFields();
    }

    @Override
    public boolean sourceRequested() {
        return in.sourceRequested();
    }

    @Override
    public boolean hasFetchSourceContext() {
        return in.hasFetchSourceContext();
    }

    @Override
    public FetchSourceContext fetchSourceContext() {
        return in.fetchSourceContext();
    }

    @Override
    public SearchContext fetchSourceContext(FetchSourceContext fetchSourceContext) {
        return in.fetchSourceContext(fetchSourceContext);
    }

    @Override
    public ContextIndexSearcher searcher() {
        return in.searcher();
    }

    @Override
    public IndexShard indexShard() {
        return in.indexShard();
    }

    @Override
    public MapperService mapperService() {
        return in.mapperService();
    }

    @Override
    public AnalysisService analysisService() {
        return in.analysisService();
    }

    @Override
    public SimilarityService similarityService() {
        return in.similarityService();
    }

    @Override
    public ScriptService scriptService() {
        return in.scriptService();
    }

    @Override
    public PageCacheRecycler pageCacheRecycler() {
        return in.pageCacheRecycler();
    }

    @Override
    public BigArrays bigArrays() {
        return in.bigArrays();
    }

    @Override
    public BitsetFilterCache bitsetFilterCache() {
        return in.bitsetFilterCache();
    }

    @Override
    public IndexFieldDataService fieldData() {
        return in.fieldData();
    }

    @Override
    public long timeoutInMillis() {
        return in.timeoutInMillis();
    }

    @Override
    public void timeoutInMillis(long timeoutInMillis) {
        in.timeoutInMillis(timeoutInMillis);
    }

    @Override
    public int terminateAfter() {
        return in.terminateAfter();
    }

    @Override
    public void terminateAfter(int terminateAfter) {
        in.terminateAfter(terminateAfter);
    }

    @Override
    public SearchContext minimumScore(float minimumScore) {
        return in.minimumScore(minimumScore);
    }

    @Override
    public Float minimumScore() {
        return in.minimumScore();
    }

    @Override
    public SearchContext sort(Sort sort) {
        return in.sort(sort);
    }

    @Override
    public Sort sort() {
        return in.sort();
    }

    @Override
    public SearchContext trackScores(boolean trackScores) {
        return in.trackScores(trackScores);
    }

    @Override
    public boolean trackScores() {
        return in.trackScores();
    }

    @Override
    public SearchContext parsedPostFilter(ParsedQuery postFilter) {
        return in.parsedPostFilter(postFilter);
    }

    @Override
    public ParsedQuery parsedPostFilter() {
        return in.parsedPostFilter();
    }

    @Override
    public Query aliasFilter() {
        return in.aliasFilter();
    }

    @Override
    public SearchContext parsedQuery(ParsedQuery query) {
        return in.parsedQuery(query);
    }

    @Override
    public ParsedQuery parsedQuery() {
        return in.parsedQuery();
    }

    @Override
    public Query query() {
        return in.query();
    }

    @Override
    public int from() {
        return in.from();
    }

    @Override
    public SearchContext from(int from) {
        return in.from(from);
    }

    @Override
    public int size() {
        return in.size();
    }

    @Override
    public SearchContext size(int size) {
        return in.size(size);
    }

    @Override
    public boolean hasFieldNames() {
        return in.hasFieldNames();
    }

    @Override
    public List<String> fieldNames() {
        return in.fieldNames();
    }

    @Override
    public void emptyFieldNames() {
        in.emptyFieldNames();
    }

    @Override
    public boolean explain() {
        return in.explain();
    }

    @Override
    public void explain(boolean explain) {
        in.explain(explain);
    }

    @Override
    public List<String> groupStats() {
        return in.groupStats();
    }

    @Override
    public void groupStats(List<String> groupStats) {
        in.groupStats(groupStats);
    }

    @Override
    public boolean version() {
        return in.version();
    }

    @Override
    public void version(boolean version) {
        in.version(version);
    }

    @Override
    public int[] docIdsToLoad() {
        return in.docIdsToLoad();
    }

    @Override
    public int docIdsToLoadFrom() {
        return in.docIdsToLoadFrom();
    }

    @Override
    public int docIdsToLoadSize() {
        return in.docIdsToLoadSize();
    }

    @Override
    public SearchContext docIdsToLoad(int[] docIdsToLoad, int docsIdsToLoadFrom, int docsIdsToLoadSize) {
        return in.docIdsToLoad(docIdsToLoad, docsIdsToLoadFrom, docsIdsToLoadSize);
    }

    @Override
    public void accessed(long accessTime) {
        in.accessed(accessTime);
    }

    @Override
    public long lastAccessTime() {
        return in.lastAccessTime();
    }

    @Override
    public long keepAlive() {
        return in.keepAlive();
    }

    @Override
    public void keepAlive(long keepAlive) {
        in.keepAlive(keepAlive);
    }

    @Override
    public SearchLookup lookup() {
        return in.lookup();
    }

    @Override
    public DfsSearchResult dfsResult() {
        return in.dfsResult();
    }

    @Override
    public QuerySearchResult queryResult() {
        return in.queryResult();
    }

    @Override
    public FetchSearchResult fetchResult() {
        return in.fetchResult();
    }

    @Override
    public MappedFieldType smartNameFieldType(String name) {
        return in.smartNameFieldType(name);
    }

    @Override
    public ObjectMapper getObjectMapper(String name) {
        return in.getObjectMapper(name);
    }

    @Override
    public Counter timeEstimateCounter() {
        return in.timeEstimateCounter();
    }

    @Override
    public <SubPhaseContext extends FetchSubPhaseContext> SubPhaseContext getFetchSubPhaseContext(FetchSubPhase.ContextFactory<SubPhaseContext> contextFactory) {
        return in.getFetchSubPhaseContext(contextFactory);
    }

    @Override
    public Profilers getProfilers() {
        return in.getProfilers();
    }

    @Override
    public Map<Class<?>, Collector> queryCollectors() { return in.queryCollectors();}

}
