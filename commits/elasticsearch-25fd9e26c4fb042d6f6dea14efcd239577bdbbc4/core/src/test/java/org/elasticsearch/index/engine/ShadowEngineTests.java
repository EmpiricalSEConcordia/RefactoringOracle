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

package org.elasticsearch.index.engine;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy;
import org.apache.lucene.index.LiveIndexWriterConfig;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MockDirectoryWrapper;
import org.apache.lucene.util.IOUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.Version;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.lucene.Lucene;
import org.elasticsearch.common.lucene.uid.Versions;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.codec.CodecService;
import org.elasticsearch.index.mapper.Mapping;
import org.elasticsearch.index.mapper.ParseContext;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.index.mapper.SourceFieldMapper;
import org.elasticsearch.index.mapper.UidFieldMapper;
import org.elasticsearch.index.seqno.SequenceNumbersService;
import org.elasticsearch.index.shard.DocsStats;
import org.elasticsearch.index.shard.RefreshListeners;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.shard.ShardUtils;
import org.elasticsearch.index.store.DirectoryService;
import org.elasticsearch.index.store.DirectoryUtils;
import org.elasticsearch.index.store.Store;
import org.elasticsearch.index.translog.Translog;
import org.elasticsearch.index.translog.TranslogConfig;
import org.elasticsearch.test.DummyShardLock;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.IndexSettingsModule;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.elasticsearch.index.engine.Engine.Operation.Origin.PRIMARY;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class ShadowEngineTests extends ESTestCase {

    protected final ShardId shardId = new ShardId("index", "_na_", 1);

    protected ThreadPool threadPool;

    private Store store;
    private Store storeReplica;


    protected Engine primaryEngine;
    protected Engine replicaEngine;

    private IndexSettings defaultSettings;
    private String codecName;
    private Path dirPath;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        CodecService codecService = new CodecService(null, logger);
        String name = Codec.getDefault().getName();
        if (Arrays.asList(codecService.availableCodecs()).contains(name)) {
            // some codecs are read only so we only take the ones that we have in the service and randomly
            // selected by lucene test case.
            codecName = name;
        } else {
            codecName = "default";
        }
        defaultSettings = IndexSettingsModule.newIndexSettings("test", Settings.builder()
                .put(IndexSettings.INDEX_GC_DELETES_SETTING, "1h") // make sure this doesn't kick in on us
                .put(EngineConfig.INDEX_CODEC_SETTING.getKey(), codecName)
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .build()); // TODO randomize more settings

        threadPool = new TestThreadPool(getClass().getName());
        dirPath = createTempDir();
        store = createStore(dirPath);
        storeReplica = createStore(dirPath);
        Lucene.cleanLuceneIndex(store.directory());
        Lucene.cleanLuceneIndex(storeReplica.directory());
        primaryEngine = createInternalEngine(store, createTempDir("translog-primary"));
        LiveIndexWriterConfig currentIndexWriterConfig = ((InternalEngine)primaryEngine).getCurrentIndexWriterConfig();

        assertEquals(primaryEngine.config().getCodec().getName(), codecService.codec(codecName).getName());
        assertEquals(currentIndexWriterConfig.getCodec().getName(), codecService.codec(codecName).getName());
        if (randomBoolean()) {
            primaryEngine.config().setEnableGcDeletes(false);
        }

        replicaEngine = createShadowEngine(storeReplica);

        assertEquals(replicaEngine.config().getCodec().getName(), codecService.codec(codecName).getName());
        if (randomBoolean()) {
            replicaEngine.config().setEnableGcDeletes(false);
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        replicaEngine.close();
        storeReplica.close();
        primaryEngine.close();
        store.close();
        terminate(threadPool);
    }

    private ParseContext.Document testDocumentWithTextField() {
        ParseContext.Document document = testDocument();
        document.add(new TextField("value", "test", Field.Store.YES));
        return document;
    }

    private ParseContext.Document testDocument() {
        return new ParseContext.Document();
    }


    private ParsedDocument testParsedDocument(String uid, String id, String type, String routing, long timestamp, long ttl, ParseContext.Document document, BytesReference source, Mapping mappingsUpdate) {
        Field uidField = new Field("_uid", uid, UidFieldMapper.Defaults.FIELD_TYPE);
        Field versionField = new NumericDocValuesField("_version", 0);
        Field seqNoField = new NumericDocValuesField("_seq_no", 0);
        document.add(uidField);
        document.add(versionField);
        document.add(new LongPoint("point_field", 42)); // so that points report memory/disk usage
        return new ParsedDocument(versionField, seqNoField, id, type, routing, timestamp, ttl, Arrays.asList(document), source, mappingsUpdate);
    }

    protected Store createStore(Path p) throws IOException {
        return createStore(newMockFSDirectory(p));
    }


    protected Store createStore(final Directory directory) throws IOException {
        IndexSettings indexSettings = IndexSettingsModule.newIndexSettings(shardId.getIndex(), Settings.EMPTY);
        final DirectoryService directoryService = new DirectoryService(shardId, indexSettings) {
            @Override
            public Directory newDirectory() throws IOException {
                return directory;
            }

            @Override
            public long throttleTimeInNanos() {
                return 0;
            }
        };
        return new Store(shardId, indexSettings, directoryService, new DummyShardLock(shardId));
    }

    protected SnapshotDeletionPolicy createSnapshotDeletionPolicy() {
        return new SnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy());
    }

    protected ShadowEngine createShadowEngine(Store store) {
        return createShadowEngine(defaultSettings, store);
    }

    protected InternalEngine createInternalEngine(Store store, Path translogPath) {
        return createInternalEngine(defaultSettings, store, translogPath);
    }

    protected ShadowEngine createShadowEngine(IndexSettings indexSettings, Store store) {
        return new ShadowEngine(config(indexSettings, store, null, null, null));
    }

    protected InternalEngine createInternalEngine(IndexSettings indexSettings, Store store, Path translogPath) {
        return createInternalEngine(indexSettings, store, translogPath, newMergePolicy());
    }

    protected InternalEngine createInternalEngine(IndexSettings indexSettings, Store store, Path translogPath, MergePolicy mergePolicy) {
        EngineConfig config = config(indexSettings, store, translogPath, mergePolicy, null);
        return new InternalEngine(config);
    }

    public EngineConfig config(IndexSettings indexSettings, Store store, Path translogPath, MergePolicy mergePolicy,
            RefreshListeners refreshListeners) {
        IndexWriterConfig iwc = newIndexWriterConfig();
        final EngineConfig.OpenMode openMode;
        try {
            if (Lucene.indexExists(store.directory()) == false) {
                openMode = EngineConfig.OpenMode.CREATE_INDEX_AND_TRANSLOG;
            } else {
                openMode = EngineConfig.OpenMode.OPEN_INDEX_CREATE_TRANSLOG;
            }
        } catch (IOException e) {
            throw new ElasticsearchException("can't find index?", e);
        }
        Engine.EventListener eventListener = new Engine.EventListener() {
            @Override
            public void onFailedEngine(String reason, @Nullable Exception e) {
                // we don't need to notify anybody in this test
            }
        };
        TranslogConfig translogConfig = new TranslogConfig(shardId, translogPath, indexSettings, BigArrays.NON_RECYCLING_INSTANCE);
        EngineConfig config = new EngineConfig(openMode, shardId, threadPool, indexSettings, null, store, createSnapshotDeletionPolicy(),
                mergePolicy, iwc.getAnalyzer(), iwc.getSimilarity(), new CodecService(null, logger), eventListener, null,
                IndexSearcher.getDefaultQueryCache(), IndexSearcher.getDefaultQueryCachingPolicy(), translogConfig,
                TimeValue.timeValueMinutes(5), refreshListeners, IndexRequest.UNSET_AUTO_GENERATED_TIMESTAMP);

        return config;
    }

    protected Term newUid(String id) {
        return new Term("_uid", id);
    }

    protected static final BytesReference B_1 = new BytesArray(new byte[]{1});
    protected static final BytesReference B_2 = new BytesArray(new byte[]{2});
    protected static final BytesReference B_3 = new BytesArray(new byte[]{3});

    public void testCommitStats() {
        // create a doc and refresh
        ParsedDocument doc = testParsedDocument("1", "1", "test", null, -1, -1, testDocumentWithTextField(), B_1, null);
        primaryEngine.index(new Engine.Index(newUid("1"), doc));

        CommitStats stats1 = replicaEngine.commitStats();
        assertThat(stats1.getGeneration(), greaterThan(0L));
        assertThat(stats1.getId(), notNullValue());
        assertThat(stats1.getUserData(), hasKey(Translog.TRANSLOG_GENERATION_KEY));

        // flush the primary engine
        primaryEngine.flush();
        // flush on replica to make flush visible
        replicaEngine.flush();

        CommitStats stats2 = replicaEngine.commitStats();
        assertThat(stats2.getGeneration(), greaterThan(stats1.getGeneration()));
        assertThat(stats2.getId(), notNullValue());
        assertThat(stats2.getId(), not(equalTo(stats1.getId())));
        assertThat(stats2.getUserData(), hasKey(Translog.TRANSLOG_GENERATION_KEY));
        assertThat(stats2.getUserData(), hasKey(Translog.TRANSLOG_UUID_KEY));
        assertThat(stats2.getUserData().get(Translog.TRANSLOG_GENERATION_KEY), not(equalTo(stats1.getUserData().get(Translog.TRANSLOG_GENERATION_KEY))));
        assertThat(stats2.getUserData().get(Translog.TRANSLOG_UUID_KEY), equalTo(stats1.getUserData().get(Translog.TRANSLOG_UUID_KEY)));
    }

    public void testSegments() throws Exception {
        primaryEngine.close(); // recreate without merging
        primaryEngine = createInternalEngine(defaultSettings, store, createTempDir(), NoMergePolicy.INSTANCE);
        List<Segment> segments = primaryEngine.segments(false);
        assertThat(segments.isEmpty(), equalTo(true));
        assertThat(primaryEngine.segmentsStats(false).getCount(), equalTo(0L));
        assertThat(primaryEngine.segmentsStats(false).getMemoryInBytes(), equalTo(0L));

        // create a doc and refresh
        ParsedDocument doc = testParsedDocument("1", "1", "test", null, -1, -1, testDocumentWithTextField(), B_1, null);
        primaryEngine.index(new Engine.Index(newUid("1"), doc));

        ParsedDocument doc2 = testParsedDocument("2", "2", "test", null, -1, -1, testDocumentWithTextField(), B_2, null);
        primaryEngine.index(new Engine.Index(newUid("2"), doc2));
        primaryEngine.refresh("test");

        segments = primaryEngine.segments(false);
        assertThat(segments.size(), equalTo(1));
        SegmentsStats stats = primaryEngine.segmentsStats(false);
        assertThat(stats.getCount(), equalTo(1L));
        assertThat(stats.getTermsMemoryInBytes(), greaterThan(0L));
        assertThat(stats.getStoredFieldsMemoryInBytes(), greaterThan(0L));
        assertThat(stats.getTermVectorsMemoryInBytes(), equalTo(0L));
        assertThat(stats.getNormsMemoryInBytes(), greaterThan(0L));
        assertThat(stats.getPointsMemoryInBytes(), greaterThan(0L));
        assertThat(stats.getDocValuesMemoryInBytes(), greaterThan(0L));
        assertThat(segments.get(0).isCommitted(), equalTo(false));
        assertThat(segments.get(0).isSearch(), equalTo(true));
        assertThat(segments.get(0).getNumDocs(), equalTo(2));
        assertThat(segments.get(0).getDeletedDocs(), equalTo(0));
        assertTrue(segments.get(0).isCompound());
        assertThat(segments.get(0).ramTree, nullValue());

        // Check that the replica sees nothing
        segments = replicaEngine.segments(false);
        assertThat(segments.size(), equalTo(0));
        stats = replicaEngine.segmentsStats(false);
        assertThat(stats.getCount(), equalTo(0L));
        assertThat(stats.getTermsMemoryInBytes(), equalTo(0L));
        assertThat(stats.getStoredFieldsMemoryInBytes(), equalTo(0L));
        assertThat(stats.getTermVectorsMemoryInBytes(), equalTo(0L));
        assertThat(stats.getNormsMemoryInBytes(), equalTo(0L));
        assertThat(stats.getPointsMemoryInBytes(), equalTo(0L));
        assertThat(stats.getDocValuesMemoryInBytes(), equalTo(0L));
        assertThat(segments.size(), equalTo(0));

        // flush the primary engine
        primaryEngine.flush();
        // refresh the replica
        replicaEngine.refresh("tests");

        // Check that the primary AND replica sees segments now
        segments = primaryEngine.segments(false);
        assertThat(segments.size(), equalTo(1));
        assertThat(primaryEngine.segmentsStats(false).getCount(), equalTo(1L));
        assertThat(segments.get(0).isCommitted(), equalTo(true));
        assertThat(segments.get(0).isSearch(), equalTo(true));
        assertThat(segments.get(0).getNumDocs(), equalTo(2));
        assertThat(segments.get(0).getDeletedDocs(), equalTo(0));
        assertThat(segments.get(0).isCompound(), equalTo(true));

        segments = replicaEngine.segments(false);
        assertThat(segments.size(), equalTo(1));
        assertThat(replicaEngine.segmentsStats(false).getCount(), equalTo(1L));
        assertThat(segments.get(0).isCommitted(), equalTo(true));
        assertThat(segments.get(0).isSearch(), equalTo(true));
        assertThat(segments.get(0).getNumDocs(), equalTo(2));
        assertThat(segments.get(0).getDeletedDocs(), equalTo(0));
        assertThat(segments.get(0).isCompound(), equalTo(true));


        ParsedDocument doc3 = testParsedDocument("3", "3", "test", null, -1, -1, testDocumentWithTextField(), B_3, null);
        primaryEngine.index(new Engine.Index(newUid("3"), doc3));
        primaryEngine.refresh("test");

        segments = primaryEngine.segments(false);
        assertThat(segments.size(), equalTo(2));
        assertThat(primaryEngine.segmentsStats(false).getCount(), equalTo(2L));
        assertThat(primaryEngine.segmentsStats(false).getTermsMemoryInBytes(), greaterThan(stats.getTermsMemoryInBytes()));
        assertThat(primaryEngine.segmentsStats(false).getStoredFieldsMemoryInBytes(), greaterThan(stats.getStoredFieldsMemoryInBytes()));
        assertThat(primaryEngine.segmentsStats(false).getTermVectorsMemoryInBytes(), equalTo(0L));
        assertThat(primaryEngine.segmentsStats(false).getNormsMemoryInBytes(), greaterThan(stats.getNormsMemoryInBytes()));
        assertThat(primaryEngine.segmentsStats(false).getPointsMemoryInBytes(), greaterThan(stats.getPointsMemoryInBytes()));
        assertThat(primaryEngine.segmentsStats(false).getDocValuesMemoryInBytes(), greaterThan(stats.getDocValuesMemoryInBytes()));
        assertThat(segments.get(0).getGeneration() < segments.get(1).getGeneration(), equalTo(true));
        assertThat(segments.get(0).isCommitted(), equalTo(true));
        assertThat(segments.get(0).isSearch(), equalTo(true));
        assertThat(segments.get(0).getNumDocs(), equalTo(2));
        assertThat(segments.get(0).getDeletedDocs(), equalTo(0));
        assertThat(segments.get(0).isCompound(), equalTo(true));
        assertThat(segments.get(1).isCommitted(), equalTo(false));
        assertThat(segments.get(1).isSearch(), equalTo(true));
        assertThat(segments.get(1).getNumDocs(), equalTo(1));
        assertThat(segments.get(1).getDeletedDocs(), equalTo(0));
        assertThat(segments.get(1).isCompound(), equalTo(true));

        // Make visible to shadow replica
        primaryEngine.flush();
        replicaEngine.refresh("test");

        segments = replicaEngine.segments(false);
        assertThat(segments.size(), equalTo(2));
        assertThat(replicaEngine.segmentsStats(false).getCount(), equalTo(2L));
        assertThat(replicaEngine.segmentsStats(false).getTermsMemoryInBytes(), greaterThan(stats.getTermsMemoryInBytes()));
        assertThat(replicaEngine.segmentsStats(false).getStoredFieldsMemoryInBytes(), greaterThan(stats.getStoredFieldsMemoryInBytes()));
        assertThat(replicaEngine.segmentsStats(false).getTermVectorsMemoryInBytes(), equalTo(0L));
        assertThat(replicaEngine.segmentsStats(false).getNormsMemoryInBytes(), greaterThan(stats.getNormsMemoryInBytes()));
        assertThat(replicaEngine.segmentsStats(false).getPointsMemoryInBytes(), greaterThan(stats.getPointsMemoryInBytes()));
        assertThat(replicaEngine.segmentsStats(false).getDocValuesMemoryInBytes(), greaterThan(stats.getDocValuesMemoryInBytes()));
        assertThat(segments.get(0).getGeneration() < segments.get(1).getGeneration(), equalTo(true));
        assertThat(segments.get(0).isCommitted(), equalTo(true));
        assertThat(segments.get(0).isSearch(), equalTo(true));
        assertThat(segments.get(0).getNumDocs(), equalTo(2));
        assertThat(segments.get(0).getDeletedDocs(), equalTo(0));
        assertThat(segments.get(0).isCompound(), equalTo(true));
        assertThat(segments.get(1).isCommitted(), equalTo(true));
        assertThat(segments.get(1).isSearch(), equalTo(true));
        assertThat(segments.get(1).getNumDocs(), equalTo(1));
        assertThat(segments.get(1).getDeletedDocs(), equalTo(0));
        assertThat(segments.get(1).isCompound(), equalTo(true));

        primaryEngine.delete(new Engine.Delete("test", "1", newUid("1")));
        primaryEngine.refresh("test");

        segments = primaryEngine.segments(false);
        assertThat(segments.size(), equalTo(2));
        assertThat(primaryEngine.segmentsStats(false).getCount(), equalTo(2L));
        assertThat(segments.get(0).getGeneration() < segments.get(1).getGeneration(), equalTo(true));
        assertThat(segments.get(0).isCommitted(), equalTo(true));
        assertThat(segments.get(0).isSearch(), equalTo(true));
        assertThat(segments.get(0).getNumDocs(), equalTo(1));
        assertThat(segments.get(0).getDeletedDocs(), equalTo(1));
        assertThat(segments.get(0).isCompound(), equalTo(true));
        assertThat(segments.get(1).isCommitted(), equalTo(true));
        assertThat(segments.get(1).isSearch(), equalTo(true));
        assertThat(segments.get(1).getNumDocs(), equalTo(1));
        assertThat(segments.get(1).getDeletedDocs(), equalTo(0));
        assertThat(segments.get(1).isCompound(), equalTo(true));

        // Make visible to shadow replica
        primaryEngine.flush();
        replicaEngine.refresh("test");

        ParsedDocument doc4 = testParsedDocument("4", "4", "test", null, -1, -1, testDocumentWithTextField(), B_3, null);
        primaryEngine.index(new Engine.Index(newUid("4"), doc4));
        primaryEngine.refresh("test");

        segments = primaryEngine.segments(false);
        assertThat(segments.size(), equalTo(3));
        assertThat(primaryEngine.segmentsStats(false).getCount(), equalTo(3L));
        assertThat(segments.get(0).getGeneration() < segments.get(1).getGeneration(), equalTo(true));
        assertThat(segments.get(0).isCommitted(), equalTo(true));
        assertThat(segments.get(0).isSearch(), equalTo(true));
        assertThat(segments.get(0).getNumDocs(), equalTo(1));
        assertThat(segments.get(0).getDeletedDocs(), equalTo(1));
        assertThat(segments.get(0).isCompound(), equalTo(true));

        assertThat(segments.get(1).isCommitted(), equalTo(true));
        assertThat(segments.get(1).isSearch(), equalTo(true));
        assertThat(segments.get(1).getNumDocs(), equalTo(1));
        assertThat(segments.get(1).getDeletedDocs(), equalTo(0));
        assertThat(segments.get(1).isCompound(), equalTo(true));

        assertThat(segments.get(2).isCommitted(), equalTo(false));
        assertThat(segments.get(2).isSearch(), equalTo(true));
        assertThat(segments.get(2).getNumDocs(), equalTo(1));
        assertThat(segments.get(2).getDeletedDocs(), equalTo(0));
        assertThat(segments.get(2).isCompound(), equalTo(true));
    }

    public void testVerboseSegments() throws Exception {
        primaryEngine.close(); // recreate without merging
        primaryEngine = createInternalEngine(defaultSettings, store, createTempDir(), NoMergePolicy.INSTANCE);
        List<Segment> segments = primaryEngine.segments(true);
        assertThat(segments.isEmpty(), equalTo(true));

        ParsedDocument doc = testParsedDocument("1", "1", "test", null, -1, -1, testDocumentWithTextField(), B_1, null);
        primaryEngine.index(new Engine.Index(newUid("1"), doc));
        primaryEngine.refresh("test");

        segments = primaryEngine.segments(true);
        assertThat(segments.size(), equalTo(1));
        assertThat(segments.get(0).ramTree, notNullValue());

        ParsedDocument doc2 = testParsedDocument("2", "2", "test", null, -1, -1, testDocumentWithTextField(), B_2, null);
        primaryEngine.index(new Engine.Index(newUid("2"), doc2));
        primaryEngine.refresh("test");
        ParsedDocument doc3 = testParsedDocument("3", "3", "test", null, -1, -1, testDocumentWithTextField(), B_3, null);
        primaryEngine.index(new Engine.Index(newUid("3"), doc3));
        primaryEngine.refresh("test");

        segments = primaryEngine.segments(true);
        assertThat(segments.size(), equalTo(3));
        assertThat(segments.get(0).ramTree, notNullValue());
        assertThat(segments.get(1).ramTree, notNullValue());
        assertThat(segments.get(2).ramTree, notNullValue());

        // Now make the changes visible to the replica
        primaryEngine.flush();
        replicaEngine.refresh("test");

        segments = replicaEngine.segments(true);
        assertThat(segments.size(), equalTo(3));
        assertThat(segments.get(0).ramTree, notNullValue());
        assertThat(segments.get(1).ramTree, notNullValue());
        assertThat(segments.get(2).ramTree, notNullValue());

    }

    public void testShadowEngineIgnoresWriteOperations() throws Exception {
        // create a document
        ParseContext.Document document = testDocumentWithTextField();
        document.add(new Field(SourceFieldMapper.NAME, BytesReference.toBytes(B_1), SourceFieldMapper.Defaults.FIELD_TYPE));
        ParsedDocument doc = testParsedDocument("1", "1", "test", null, -1, -1, document, B_1, null);
        try {
            replicaEngine.index(new Engine.Index(newUid("1"), doc));
            fail("should have thrown an exception");
        } catch (UnsupportedOperationException e) {}
        replicaEngine.refresh("test");

        // its not there...
        Engine.Searcher searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        searchResult.close();
        Engine.GetResult getResult = replicaEngine.get(new Engine.Get(true, newUid("1")));
        assertThat(getResult.exists(), equalTo(false));
        getResult.release();

        // index a document
        document = testDocument();
        document.add(new TextField("value", "test1", Field.Store.YES));
        doc = testParsedDocument("1", "1", "test", null, -1, -1, document, B_1, null);
        try {
            replicaEngine.index(new Engine.Index(newUid("1"), doc));
            fail("should have thrown an exception");
        } catch (UnsupportedOperationException e) {}
        replicaEngine.refresh("test");

        // its still not there...
        searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        searchResult.close();
        getResult = replicaEngine.get(new Engine.Get(true, newUid("1")));
        assertThat(getResult.exists(), equalTo(false));
        getResult.release();

        // Now, add a document to the primary so we can test shadow engine deletes
        document = testDocumentWithTextField();
        document.add(new Field(SourceFieldMapper.NAME, BytesReference.toBytes(B_1), SourceFieldMapper.Defaults.FIELD_TYPE));
        doc = testParsedDocument("1", "1", "test", null, -1, -1, document, B_1, null);
        primaryEngine.index(new Engine.Index(newUid("1"), doc));
        primaryEngine.flush();
        replicaEngine.refresh("test");

        // Now the replica can see it
        searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        searchResult.close();

        // And the replica can retrieve it
        getResult = replicaEngine.get(new Engine.Get(false, newUid("1")));
        assertThat(getResult.exists(), equalTo(true));
        assertThat(getResult.docIdAndVersion(), notNullValue());
        getResult.release();

        // try to delete it on the replica
        try {
            replicaEngine.delete(new Engine.Delete("test", "1", newUid("1")));
            fail("should have thrown an exception");
        } catch (UnsupportedOperationException e) {}
        replicaEngine.flush();
        replicaEngine.refresh("test");
        primaryEngine.refresh("test");

        // it's still there!
        searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        searchResult.close();
        getResult = replicaEngine.get(new Engine.Get(false, newUid("1")));
        assertThat(getResult.exists(), equalTo(true));
        assertThat(getResult.docIdAndVersion(), notNullValue());
        getResult.release();

        // it's still there on the primary also!
        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        searchResult.close();
        getResult = primaryEngine.get(new Engine.Get(false, newUid("1")));
        assertThat(getResult.exists(), equalTo(true));
        assertThat(getResult.docIdAndVersion(), notNullValue());
        getResult.release();
    }

    public void testSimpleOperations() throws Exception {
        Engine.Searcher searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        searchResult.close();

        // create a document
        ParseContext.Document document = testDocumentWithTextField();
        document.add(new Field(SourceFieldMapper.NAME, BytesReference.toBytes(B_1), SourceFieldMapper.Defaults.FIELD_TYPE));
        ParsedDocument doc = testParsedDocument("1", "1", "test", null, -1, -1, document, B_1, null);
        primaryEngine.index(new Engine.Index(newUid("1"), doc));

        // its not there...
        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        searchResult.close();

        // not on the replica either...
        searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        searchResult.close();

        // but, we can still get it (in realtime)
        Engine.GetResult getResult = primaryEngine.get(new Engine.Get(true, newUid("1")));
        assertThat(getResult.exists(), equalTo(true));
        assertThat(getResult.docIdAndVersion(), notNullValue());
        getResult.release();

        // can't get it from the replica, because it's not in the translog for a shadow replica
        getResult = replicaEngine.get(new Engine.Get(true, newUid("1")));
        assertThat(getResult.exists(), equalTo(false));
        getResult.release();

        // but, not there non realtime
        getResult = primaryEngine.get(new Engine.Get(false, newUid("1")));
        assertThat(getResult.exists(), equalTo(true));
        getResult.release();

        // now its there...
        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        searchResult.close();

        // also in non realtime
        getResult = primaryEngine.get(new Engine.Get(false, newUid("1")));
        assertThat(getResult.exists(), equalTo(true));
        assertThat(getResult.docIdAndVersion(), notNullValue());
        getResult.release();

        // still not in the replica because no flush
        searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        searchResult.close();

        // now do an update
        document = testDocument();
        document.add(new TextField("value", "test1", Field.Store.YES));
        document.add(new Field(SourceFieldMapper.NAME, BytesReference.toBytes(B_2), SourceFieldMapper.Defaults.FIELD_TYPE));
        doc = testParsedDocument("1", "1", "test", null, -1, -1, document, B_2, null);
        primaryEngine.index(new Engine.Index(newUid("1"), doc));

        // its not updated yet...
        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 0));
        searchResult.close();

        // but, we can still get it (in realtime)
        getResult = primaryEngine.get(new Engine.Get(true, newUid("1")));
        assertThat(getResult.exists(), equalTo(true));
        assertThat(getResult.docIdAndVersion(), notNullValue());
        getResult.release();

        // refresh and it should be updated
        primaryEngine.refresh("test");

        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 1));
        searchResult.close();

        // flush, now shadow replica should have the files
        primaryEngine.flush();

        // still not in the replica because the replica hasn't refreshed
        searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        searchResult.close();

        replicaEngine.refresh("test");

        // the replica finally sees it because primary has flushed and replica refreshed
        searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 1));
        searchResult.close();

        // now delete
        primaryEngine.delete(new Engine.Delete("test", "1", newUid("1")));

        // its not deleted yet
        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 1));
        searchResult.close();

        // but, get should not see it (in realtime)
        getResult = primaryEngine.get(new Engine.Get(true, newUid("1")));
        assertThat(getResult.exists(), equalTo(false));
        getResult.release();

        // refresh and it should be deleted
        primaryEngine.refresh("test");

        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 0));
        searchResult.close();

        // add it back
        document = testDocumentWithTextField();
        document.add(new Field(SourceFieldMapper.NAME, BytesReference.toBytes(B_1), SourceFieldMapper.Defaults.FIELD_TYPE));
        doc = testParsedDocument("1", "1", "test", null, -1, -1, document, B_1, null);
        primaryEngine.index(new Engine.Index(newUid("1"), doc));

        // its not there...
        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 0));
        searchResult.close();

        // refresh and it should be there
        primaryEngine.refresh("test");

        // now its there...
        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 0));
        searchResult.close();

        // now flush
        primaryEngine.flush();

        // and, verify get (in real time)
        getResult = primaryEngine.get(new Engine.Get(true, newUid("1")));
        assertThat(getResult.exists(), equalTo(true));
        assertThat(getResult.docIdAndVersion(), notNullValue());
        getResult.release();

        // the replica should see it if we refresh too!
        replicaEngine.refresh("test");
        searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 0));
        searchResult.close();
        getResult = replicaEngine.get(new Engine.Get(true, newUid("1")));
        assertThat(getResult.exists(), equalTo(true));
        assertThat(getResult.docIdAndVersion(), notNullValue());
        getResult.release();

        // make sure we can still work with the engine
        // now do an update
        document = testDocument();
        document.add(new TextField("value", "test1", Field.Store.YES));
        doc = testParsedDocument("1", "1", "test", null, -1, -1, document, B_1, null);
        primaryEngine.index(new Engine.Index(newUid("1"), doc));

        // its not updated yet...
        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 0));
        searchResult.close();

        // refresh and it should be updated
        primaryEngine.refresh("test");

        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 1));
        searchResult.close();

        // Make visible to shadow replica
        primaryEngine.flush();
        replicaEngine.refresh("test");

        searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 1));
        searchResult.close();
    }

    public void testSearchResultRelease() throws Exception {
        Engine.Searcher searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        searchResult.close();

        // create a document
        ParsedDocument doc = testParsedDocument("1", "1", "test", null, -1, -1, testDocumentWithTextField(), B_1, null);
        primaryEngine.index(new Engine.Index(newUid("1"), doc));

        // its not there...
        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        searchResult.close();
        searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        searchResult.close();

        // flush & refresh and it should everywhere
        primaryEngine.flush();
        primaryEngine.refresh("test");
        replicaEngine.refresh("test");

        // now its there...
        searchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        searchResult.close();

        searchResult = replicaEngine.acquireSearcher("test");
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        // don't release the replica search result yet...

        // delete, refresh and do a new search, it should not be there
        primaryEngine.delete(new Engine.Delete("test", "1", newUid("1")));
        primaryEngine.flush();
        primaryEngine.refresh("test");
        replicaEngine.refresh("test");
        Engine.Searcher updateSearchResult = primaryEngine.acquireSearcher("test");
        MatcherAssert.assertThat(updateSearchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(0));
        updateSearchResult.close();

        // the non released replica search result should not see the deleted yet...
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
        MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        searchResult.close();
    }

    public void testFailEngineOnCorruption() {
        ParsedDocument doc = testParsedDocument("1", "1", "test", null, -1, -1, testDocumentWithTextField(), B_1, null);
        primaryEngine.index(new Engine.Index(newUid("1"), doc));
        primaryEngine.flush();
        MockDirectoryWrapper leaf = DirectoryUtils.getLeaf(replicaEngine.config().getStore().directory(), MockDirectoryWrapper.class);
        leaf.setRandomIOExceptionRate(1.0);
        leaf.setRandomIOExceptionRateOnOpen(1.0);
        try {
            replicaEngine.refresh("foo");
            fail("exception expected");
        } catch (Exception ex) {

        }
        try {
            Engine.Searcher searchResult = replicaEngine.acquireSearcher("test");
            MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(1));
            MatcherAssert.assertThat(searchResult, EngineSearcherTotalHitsMatcher.engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
            searchResult.close();
            fail("exception expected");
        } catch (EngineClosedException ex) {
            // all is well
        }
    }

    public void testExtractShardId() {
        try (Engine.Searcher test = replicaEngine.acquireSearcher("test")) {
            ShardId shardId = ShardUtils.extractShardId(test.getDirectoryReader());
            assertNotNull(shardId);
            assertEquals(shardId, replicaEngine.config().getShardId());
        }
    }

    /**
     * Random test that throws random exception and ensures all references are
     * counted down / released and resources are closed.
     */
    public void testFailStart() throws IOException {
        // Need a commit point for this
        ParsedDocument doc = testParsedDocument("1", "1", "test", null, -1, -1, testDocumentWithTextField(), B_1, null);
        primaryEngine.index(new Engine.Index(newUid("1"), doc));
        primaryEngine.flush();

        // this test fails if any reader, searcher or directory is not closed - MDW FTW
        final int iters = scaledRandomIntBetween(10, 100);
        for (int i = 0; i < iters; i++) {
            MockDirectoryWrapper wrapper = newMockFSDirectory(dirPath);
            wrapper.setFailOnOpenInput(randomBoolean());
            wrapper.setAllowRandomFileNotFoundException(randomBoolean());
            wrapper.setRandomIOExceptionRate(randomDouble());
            wrapper.setRandomIOExceptionRateOnOpen(randomDouble());
            try (Store store = createStore(wrapper)) {
                int refCount = store.refCount();
                assertTrue("refCount: "+ store.refCount(), store.refCount() > 0);
                ShadowEngine holder;
                try {
                    holder = createShadowEngine(store);
                } catch (EngineCreationFailureException ex) {
                    assertEquals(store.refCount(), refCount);
                    continue;
                }
                assertEquals(store.refCount(), refCount+1);
                final int numStarts = scaledRandomIntBetween(1, 5);
                for (int j = 0; j < numStarts; j++) {
                    try {
                        assertEquals(store.refCount(), refCount + 1);
                        holder.close();
                        holder = createShadowEngine(store);
                        assertEquals(store.refCount(), refCount + 1);
                    } catch (EngineCreationFailureException ex) {
                        // all is fine
                        assertEquals(store.refCount(), refCount);
                        break;
                    }
                }
                holder.close();
                assertEquals(store.refCount(), refCount);
            }
        }
    }

    public void testSettings() {
        CodecService codecService = new CodecService(null, logger);
        assertEquals(replicaEngine.config().getCodec().getName(), codecService.codec(codecName).getName());
    }

    public void testShadowEngineCreationRetry() throws Exception {
        final Path srDir = createTempDir();
        final Store srStore = createStore(srDir);
        Lucene.cleanLuceneIndex(srStore.directory());

        final AtomicBoolean succeeded = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        // Create a shadow Engine, which will freak out because there is no
        // index yet
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    // ignore interruptions
                }
                try (ShadowEngine srEngine = createShadowEngine(srStore)) {
                    succeeded.set(true);
                } catch (Exception e) {
                    fail("should have been able to create the engine!");
                }
            }
        });
        t.start();

        // count down latch
        // now shadow engine should try to be created
        latch.countDown();

        // Create an InternalEngine, which creates the index so the shadow
        // replica will handle it correctly
        Store pStore = createStore(srDir);
        InternalEngine pEngine = createInternalEngine(pStore, createTempDir("translog-primary"));

        // create a document
        ParseContext.Document document = testDocumentWithTextField();
        document.add(new Field(SourceFieldMapper.NAME, BytesReference.toBytes(B_1), SourceFieldMapper.Defaults.FIELD_TYPE));
        ParsedDocument doc = testParsedDocument("1", "1", "test", null, -1, -1, document, B_1, null);
        pEngine.index(new Engine.Index(newUid("1"), doc));
        pEngine.flush(true, true);

        t.join();
        assertTrue("ShadowEngine should have been able to be created", succeeded.get());
        // (shadow engine is already shut down in the try-with-resources)
        IOUtils.close(srStore, pEngine, pStore);
    }

    public void testNoTranslog() {
        try {
            replicaEngine.getTranslog();
            fail("shadow engine has no translog");
        } catch (UnsupportedOperationException ex) {
            // all good
        }
    }

    public void testDocStats() throws IOException {
        final int numDocs = randomIntBetween(2, 10); // at least 2 documents otherwise we don't see any deletes below
        for (int i = 0; i < numDocs; i++) {
            ParsedDocument doc = testParsedDocument(Integer.toString(i), Integer.toString(i), "test", null, -1, -1, testDocument(), new BytesArray("{}"), null);
            Engine.Index firstIndexRequest = new Engine.Index(newUid(Integer.toString(i)), doc, SequenceNumbersService.UNASSIGNED_SEQ_NO, Versions.MATCH_ANY, VersionType.INTERNAL, PRIMARY, System.nanoTime(), -1, false);
            primaryEngine.index(firstIndexRequest);
            assertThat(firstIndexRequest.version(), equalTo(1L));
        }
        DocsStats docStats = primaryEngine.getDocStats();
        assertEquals(numDocs, docStats.getCount());
        assertEquals(0, docStats.getDeleted());

        docStats = replicaEngine.getDocStats();
        assertEquals(0, docStats.getCount());
        assertEquals(0, docStats.getDeleted());
        primaryEngine.flush();

        docStats = replicaEngine.getDocStats();
        assertEquals(0, docStats.getCount());
        assertEquals(0, docStats.getDeleted());
        replicaEngine.refresh("test");
        docStats = replicaEngine.getDocStats();
        assertEquals(numDocs, docStats.getCount());
        assertEquals(0, docStats.getDeleted());
        primaryEngine.forceMerge(randomBoolean(), 1, false, false, false);
    }

    public void testRefreshListenersFails() throws IOException {
        EngineConfig config = config(defaultSettings, store, createTempDir(), newMergePolicy(),
                new RefreshListeners(null, null, null, logger));
        Exception e = expectThrows(IllegalArgumentException.class, () -> new ShadowEngine(config));
        assertEquals("ShadowEngine doesn't support RefreshListeners", e.getMessage());
    }
}
