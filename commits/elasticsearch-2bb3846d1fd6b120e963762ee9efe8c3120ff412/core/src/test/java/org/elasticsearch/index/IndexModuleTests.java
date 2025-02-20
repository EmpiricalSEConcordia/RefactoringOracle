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
package org.elasticsearch.index;

import org.apache.lucene.index.AssertingDirectoryReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.elasticsearch.Version;
import org.elasticsearch.cache.recycler.PageCacheRecycler;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.env.ShardLock;
import org.elasticsearch.index.analysis.AnalysisRegistry;
import org.elasticsearch.index.cache.query.QueryCache;
import org.elasticsearch.index.cache.query.index.IndexQueryCache;
import org.elasticsearch.index.cache.query.none.NoneQueryCache;
import org.elasticsearch.index.engine.EngineException;
import org.elasticsearch.index.fielddata.IndexFieldDataCache;
import org.elasticsearch.index.shard.IndexEventListener;
import org.elasticsearch.index.shard.IndexSearcherWrapper;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.similarity.SimilarityProvider;
import org.elasticsearch.index.similarity.SimilarityService;
import org.elasticsearch.index.store.IndexStore;
import org.elasticsearch.index.store.IndexStoreConfig;
import org.elasticsearch.indices.IndicesModule;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.indices.breaker.NoneCircuitBreakerService;
import org.elasticsearch.indices.IndicesQueryCache;
import org.elasticsearch.indices.fielddata.cache.IndicesFieldDataCache;
import org.elasticsearch.indices.mapper.MapperRegistry;
import org.elasticsearch.indices.query.IndicesQueriesRegistry;
import org.elasticsearch.script.ScriptContextRegistry;
import org.elasticsearch.script.ScriptEngineRegistry;
import org.elasticsearch.script.ScriptEngineService;
import org.elasticsearch.script.ScriptSettings;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.IndexSettingsModule;
import org.elasticsearch.test.engine.MockEngineFactory;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.Collections.emptyMap;

public class IndexModuleTests extends ESTestCase {
    private Index index;
    private Settings settings;
    private IndexSettings indexSettings;
    private Environment environment;
    private NodeEnvironment nodeEnvironment;
    private NodeServicesProvider nodeServicesProvider;
    private IndicesQueryCache indicesQueryCache;

    private IndexService.ShardStoreDeleter deleter = new IndexService.ShardStoreDeleter() {
        @Override
        public void deleteShardStore(String reason, ShardLock lock, IndexSettings indexSettings) throws IOException {
        }
        @Override
        public void addPendingDelete(ShardId shardId, IndexSettings indexSettings) {
        }
    };

    private final IndexFieldDataCache.Listener listener = new IndexFieldDataCache.Listener() {};
    private MapperRegistry mapperRegistry;

    static NodeServicesProvider newNodeServiceProvider(Settings settings, Environment environment, Client client, ScriptEngineService... scriptEngineServices) throws IOException {
        // TODO this can be used in other place too - lets first refactor the IndicesQueriesRegistry
        ThreadPool threadPool = new ThreadPool("test");
        CircuitBreakerService circuitBreakerService = new NoneCircuitBreakerService();
        PageCacheRecycler recycler = new PageCacheRecycler(settings, threadPool);
        BigArrays bigArrays = new BigArrays(recycler, circuitBreakerService);
        Set<ScriptEngineService> scriptEngines = Collections.emptySet();
        scriptEngines.addAll(Arrays.asList(scriptEngineServices));
        ScriptEngineRegistry scriptEngineRegistry = new ScriptEngineRegistry(Collections.emptyList());
        ScriptContextRegistry scriptContextRegistry = new ScriptContextRegistry(Collections.emptyList());
        ScriptSettings scriptSettings = new ScriptSettings(scriptEngineRegistry, scriptContextRegistry);
        ScriptService scriptService = new ScriptService(settings, environment, scriptEngines, new ResourceWatcherService(settings, threadPool), scriptEngineRegistry, scriptContextRegistry, scriptSettings);
        IndicesQueriesRegistry indicesQueriesRegistry = new IndicesQueriesRegistry(settings, emptyMap());
        return new NodeServicesProvider(threadPool, bigArrays, client, scriptService, indicesQueriesRegistry, circuitBreakerService);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        settings = Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT).put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString()).build();
        indicesQueryCache = new IndicesQueryCache(settings);
        indexSettings = IndexSettingsModule.newIndexSettings("foo", settings);
        index = indexSettings.getIndex();
        environment = new Environment(settings);
        nodeServicesProvider = newNodeServiceProvider(settings, environment, null);
        nodeEnvironment = new NodeEnvironment(settings, environment);
        mapperRegistry = new IndicesModule().getMapperRegistry();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        nodeEnvironment.close();
        indicesQueryCache.close();
        ThreadPool.terminate(nodeServicesProvider.getThreadPool(), 10, TimeUnit.SECONDS);
    }

    public void testWrapperIsBound() throws IOException {
        IndexModule module = new IndexModule(indexSettings, null, new AnalysisRegistry(null, environment));
        module.setSearcherWrapper((s) -> new Wrapper());
        module.engineFactory.set(new MockEngineFactory(AssertingDirectoryReader.class));
        IndexService indexService = module.newIndexService(nodeEnvironment, deleter, nodeServicesProvider, indicesQueryCache, mapperRegistry, new IndicesFieldDataCache(settings, listener));
        assertTrue(indexService.getSearcherWrapper() instanceof Wrapper);
        assertSame(indexService.getEngineFactory(), module.engineFactory.get());
        indexService.close("simon says", false);
    }


    public void testRegisterIndexStore() throws IOException {
        final Settings settings = Settings
            .builder()
            .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
            .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
            .put(IndexModule.INDEX_STORE_TYPE_SETTING.getKey(), "foo_store")
            .build();
        IndexSettings indexSettings = IndexSettingsModule.newIndexSettings(index, settings);
        IndexModule module = new IndexModule(indexSettings, null, new AnalysisRegistry(null, environment));
        module.addIndexStore("foo_store", FooStore::new);
        IndexService indexService = module.newIndexService(nodeEnvironment, deleter, nodeServicesProvider, indicesQueryCache, mapperRegistry, new IndicesFieldDataCache(settings, listener));
        assertTrue(indexService.getIndexStore() instanceof FooStore);
        try {
            module.addIndexStore("foo_store", FooStore::new);
            fail("already registered");
        } catch (IllegalArgumentException ex) {
            // fine
        }
        indexService.close("simon says", false);
    }

    public void testOtherServiceBound() throws IOException {
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        final IndexEventListener eventListener = new IndexEventListener() {
            @Override
            public void beforeIndexDeleted(IndexService indexService) {
                atomicBoolean.set(true);
            }
        };
        IndexSettings indexSettings = IndexSettingsModule.newIndexSettings(index, settings);
        IndexModule module = new IndexModule(indexSettings, null, new AnalysisRegistry(null, environment));
        Consumer<Settings> listener = (s) -> {};
        module.addIndexEventListener(eventListener);
        IndexService indexService = module.newIndexService(nodeEnvironment, deleter, nodeServicesProvider, indicesQueryCache, mapperRegistry,
            new IndicesFieldDataCache(settings, this.listener));
        IndexSettings x = indexService.getIndexSettings();
        assertEquals(x.getSettings().getAsMap(), indexSettings.getSettings().getAsMap());
        assertEquals(x.getIndex(), index);
        indexService.getIndexEventListener().beforeIndexDeleted(null);
        assertTrue(atomicBoolean.get());
        indexService.close("simon says", false);
    }


    public void testListener() throws IOException {
        Setting<Boolean> booleanSetting = Setting.boolSetting("foo.bar", false, Property.Dynamic, Property.IndexScope);
        IndexModule module = new IndexModule(IndexSettingsModule.newIndexSettings(index, settings, booleanSetting), null, new AnalysisRegistry(null, environment));
        Setting<Boolean> booleanSetting2 = Setting.boolSetting("foo.bar.baz", false, Property.Dynamic, Property.IndexScope);
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        module.addSettingsUpdateConsumer(booleanSetting, atomicBoolean::set);

        try {
            module.addSettingsUpdateConsumer(booleanSetting2, atomicBoolean::set);
            fail("not registered");
        } catch (IllegalArgumentException ex) {

        }

        IndexService indexService = module.newIndexService(nodeEnvironment, deleter, nodeServicesProvider, indicesQueryCache, mapperRegistry,
            new IndicesFieldDataCache(settings, listener));
        assertSame(booleanSetting, indexService.getIndexSettings().getScopedSettings().get(booleanSetting.getKey()));

        indexService.close("simon says", false);
    }

    public void testAddSimilarity() throws IOException {
        Settings indexSettings = Settings.settingsBuilder()
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .put("index.similarity.my_similarity.type", "test_similarity")
                .put("index.similarity.my_similarity.key", "there is a key")
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .build();
        IndexModule module = new IndexModule(IndexSettingsModule.newIndexSettings("foo", indexSettings), null, new AnalysisRegistry(null, environment));
        module.addSimilarity("test_similarity", (string, settings) -> new SimilarityProvider() {
            @Override
            public String name() {
                return string;
            }

            @Override
            public Similarity get() {
                return new TestSimilarity(settings.get("key"));
            }
        });

        IndexService indexService = module.newIndexService(nodeEnvironment, deleter, nodeServicesProvider, indicesQueryCache, mapperRegistry,
            new IndicesFieldDataCache(settings, listener));
        SimilarityService similarityService = indexService.similarityService();
        assertNotNull(similarityService.getSimilarity("my_similarity"));
        assertTrue(similarityService.getSimilarity("my_similarity").get() instanceof TestSimilarity);
        assertEquals("my_similarity", similarityService.getSimilarity("my_similarity").name());
        assertEquals("there is a key", ((TestSimilarity) similarityService.getSimilarity("my_similarity").get()).key);
        indexService.close("simon says", false);
    }

    public void testSetupUnknownSimilarity() throws IOException {
        Settings indexSettings = Settings.settingsBuilder()
                .put("index.similarity.my_similarity.type", "test_similarity")
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .build();
        IndexModule module = new IndexModule(IndexSettingsModule.newIndexSettings("foo", indexSettings), null, new AnalysisRegistry(null, environment));
        try {
            module.newIndexService(nodeEnvironment, deleter, nodeServicesProvider, indicesQueryCache, mapperRegistry,
                new IndicesFieldDataCache(settings, listener));
        } catch (IllegalArgumentException ex) {
            assertEquals("Unknown Similarity type [test_similarity] for [my_similarity]", ex.getMessage());
        }
    }

    public void testSetupWithoutType() throws IOException {
        Settings indexSettings = Settings.settingsBuilder()
                .put("index.similarity.my_similarity.foo", "bar")
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .build();
        IndexModule module = new IndexModule(IndexSettingsModule.newIndexSettings("foo", indexSettings), null, new AnalysisRegistry(null, environment));
        try {
            module.newIndexService(nodeEnvironment, deleter, nodeServicesProvider, indicesQueryCache, mapperRegistry,
                new IndicesFieldDataCache(settings, listener));
        } catch (IllegalArgumentException ex) {
            assertEquals("Similarity [my_similarity] must have an associated type", ex.getMessage());
        }
    }

    public void testCannotRegisterProvidedImplementations() {
        Settings indexSettings = Settings.settingsBuilder()
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT).build();
        IndexModule module = new IndexModule(IndexSettingsModule.newIndexSettings("foo", indexSettings), null, new AnalysisRegistry(null, environment));
        try {
            module.registerQueryCache("index", IndexQueryCache::new);
            fail("only once");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Can't register the same [query_cache] more than once for [index]");
        }

        try {
            module.registerQueryCache("none", (settings, x) -> new NoneQueryCache(settings));
            fail("only once");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Can't register the same [query_cache] more than once for [none]");
        }

        try {
            module.registerQueryCache("index", null);
            fail("must not be null");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "provider must not be null");
        }
    }

    public void testRegisterCustomQueryCache() throws IOException {
        Settings indexSettings = Settings.settingsBuilder()
                .put(IndexModule.INDEX_QUERY_CACHE_TYPE_SETTING.getKey(), "custom")
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT).build();
        IndexModule module = new IndexModule(IndexSettingsModule.newIndexSettings("foo", indexSettings), null, new AnalysisRegistry(null, environment));
        module.registerQueryCache("custom", (a, b) -> new CustomQueryCache());
        try {
            module.registerQueryCache("custom", (a, b) -> new CustomQueryCache());
            fail("only once");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Can't register the same [query_cache] more than once for [custom]");
        }

        IndexService indexService = module.newIndexService(nodeEnvironment, deleter, nodeServicesProvider, indicesQueryCache, mapperRegistry,
            new IndicesFieldDataCache(settings, listener));
        assertTrue(indexService.cache().query() instanceof CustomQueryCache);
        indexService.close("simon says", false);
    }

    public void testDefaultQueryCacheImplIsSelected() throws IOException {
        Settings indexSettings = Settings.settingsBuilder()
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT).build();
        IndexModule module = new IndexModule(IndexSettingsModule.newIndexSettings("foo", indexSettings), null, new AnalysisRegistry(null, environment));
        IndexService indexService = module.newIndexService(nodeEnvironment, deleter, nodeServicesProvider, indicesQueryCache, mapperRegistry,
            new IndicesFieldDataCache(settings, listener));
        assertTrue(indexService.cache().query() instanceof IndexQueryCache);
        indexService.close("simon says", false);
    }

    class CustomQueryCache implements QueryCache {

        @Override
        public void clear(String reason) {
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public Index index() {
            return new Index("test", "_na_");
        }

        @Override
        public Weight doCache(Weight weight, QueryCachingPolicy policy) {
            return weight;
        }
    }

    private static class TestSimilarity extends Similarity {
        private final Similarity delegate = new BM25Similarity();
        private final String key;


        public TestSimilarity(String key) {
            if (key == null) {
                throw new AssertionError("key is null");
            }
            this.key = key;
        }

        @Override
        public long computeNorm(FieldInvertState state) {
            return delegate.computeNorm(state);
        }

        @Override
        public SimWeight computeWeight(CollectionStatistics collectionStats, TermStatistics... termStats) {
            return delegate.computeWeight(collectionStats, termStats);
        }

        @Override
        public SimScorer simScorer(SimWeight weight, LeafReaderContext context) throws IOException {
            return delegate.simScorer(weight, context);
        }
    }

    public static final class FooStore extends IndexStore {

        public FooStore(IndexSettings indexSettings, IndexStoreConfig config) {
            super(indexSettings, config);
        }
    }

    public static final class Wrapper extends IndexSearcherWrapper {

        @Override
        public DirectoryReader wrap(DirectoryReader reader) {
            return null;
        }

        @Override
        public IndexSearcher wrap(IndexSearcher searcher) throws EngineException {
            return null;
        }
    }

}
