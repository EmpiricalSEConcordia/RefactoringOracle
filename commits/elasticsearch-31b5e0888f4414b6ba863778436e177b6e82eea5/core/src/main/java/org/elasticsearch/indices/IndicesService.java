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

package org.elasticsearch.indices;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.CollectionUtil;
import org.apache.lucene.util.IOUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.stats.CommonStats;
import org.elasticsearch.action.admin.indices.stats.CommonStatsFlags;
import org.elasticsearch.action.admin.indices.stats.CommonStatsFlags.Flag;
import org.elasticsearch.action.admin.indices.stats.IndexShardStats;
import org.elasticsearch.action.admin.indices.stats.ShardStats;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.breaker.CircuitBreaker;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.cache.RemovalNotification;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.SettingsProperty;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.env.ShardLock;
import org.elasticsearch.gateway.MetaDataStateFormat;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexModule;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.NodeServicesProvider;
import org.elasticsearch.index.analysis.AnalysisRegistry;
import org.elasticsearch.index.cache.request.ShardRequestCache;
import org.elasticsearch.index.fielddata.FieldDataType;
import org.elasticsearch.index.fielddata.IndexFieldDataCache;
import org.elasticsearch.index.flush.FlushStats;
import org.elasticsearch.index.get.GetStats;
import org.elasticsearch.index.merge.MergeStats;
import org.elasticsearch.index.recovery.RecoveryStats;
import org.elasticsearch.index.refresh.RefreshStats;
import org.elasticsearch.index.search.stats.SearchStats;
import org.elasticsearch.index.shard.IllegalIndexShardStateException;
import org.elasticsearch.index.shard.IndexEventListener;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.IndexShardState;
import org.elasticsearch.index.shard.IndexingStats;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.store.IndexStoreConfig;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.indices.fielddata.cache.IndicesFieldDataCache;
import org.elasticsearch.indices.mapper.MapperRegistry;
import org.elasticsearch.indices.query.IndicesQueriesRegistry;
import org.elasticsearch.plugins.PluginsService;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.search.internal.ShardSearchRequest;
import org.elasticsearch.search.query.QueryPhase;
import org.elasticsearch.search.query.QuerySearchResult;
import org.elasticsearch.threadpool.ThreadPool;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static org.elasticsearch.common.collect.MapBuilder.newMapBuilder;
import static org.elasticsearch.common.util.CollectionUtils.arrayAsArrayList;

/**
 *
 */
public class IndicesService extends AbstractLifecycleComponent<IndicesService> implements Iterable<IndexService>, IndexService.ShardStoreDeleter {

    public static final String INDICES_SHARDS_CLOSED_TIMEOUT = "indices.shards_closed_timeout";
    public static final Setting<TimeValue> INDICES_CACHE_CLEAN_INTERVAL_SETTING =
        Setting.positiveTimeSetting("indices.cache.cleanup_interval", TimeValue.timeValueMinutes(1), false, SettingsProperty.ClusterScope);
    private final PluginsService pluginsService;
    private final NodeEnvironment nodeEnv;
    private final TimeValue shardsClosedTimeout;
    private final AnalysisRegistry analysisRegistry;
    private final IndicesQueriesRegistry indicesQueriesRegistry;
    private final ClusterService clusterService;
    private final IndexNameExpressionResolver indexNameExpressionResolver;
    private final IndexScopedSettings indexScopeSetting;
    private final IndicesFieldDataCache indicesFieldDataCache;
    private final CacheCleaner cacheCleaner;
    private final ThreadPool threadPool;
    private final CircuitBreakerService circuitBreakerService;
    private volatile Map<String, IndexService> indices = emptyMap();
    private final Map<Index, List<PendingDelete>> pendingDeletes = new HashMap<>();
    private final OldShardsStats oldShardsStats = new OldShardsStats();
    private final IndexStoreConfig indexStoreConfig;
    private final MapperRegistry mapperRegistry;
    private final IndexingMemoryController indexingMemoryController;
    private final TimeValue cleanInterval;
    private final IndicesRequestCache indicesRequestCache;
    private final IndicesQueryCache indicesQueryCache;

    @Override
    protected void doStart() {
        // Start thread that will manage cleaning the field data cache periodically
        threadPool.schedule(this.cleanInterval, ThreadPool.Names.SAME, this.cacheCleaner);
    }

    @Inject
    public IndicesService(Settings settings, PluginsService pluginsService, NodeEnvironment nodeEnv,
                          ClusterSettings clusterSettings, AnalysisRegistry analysisRegistry,
                          IndicesQueriesRegistry indicesQueriesRegistry, IndexNameExpressionResolver indexNameExpressionResolver,
                          ClusterService clusterService, MapperRegistry mapperRegistry, ThreadPool threadPool, IndexScopedSettings indexScopedSettings, CircuitBreakerService circuitBreakerService) {
        super(settings);
        this.threadPool = threadPool;
        this.pluginsService = pluginsService;
        this.nodeEnv = nodeEnv;
        this.shardsClosedTimeout = settings.getAsTime(INDICES_SHARDS_CLOSED_TIMEOUT, new TimeValue(1, TimeUnit.DAYS));
        this.indexStoreConfig = new IndexStoreConfig(settings);
        this.analysisRegistry = analysisRegistry;
        this.indicesQueriesRegistry = indicesQueriesRegistry;
        this.clusterService = clusterService;
        this.indexNameExpressionResolver = indexNameExpressionResolver;
        this.indicesRequestCache = new IndicesRequestCache(settings);
        this.indicesQueryCache = new IndicesQueryCache(settings);
        this.mapperRegistry = mapperRegistry;
        clusterSettings.addSettingsUpdateConsumer(IndexStoreConfig.INDICES_STORE_THROTTLE_TYPE_SETTING, indexStoreConfig::setRateLimitingType);
        clusterSettings.addSettingsUpdateConsumer(IndexStoreConfig.INDICES_STORE_THROTTLE_MAX_BYTES_PER_SEC_SETTING, indexStoreConfig::setRateLimitingThrottle);
        indexingMemoryController = new IndexingMemoryController(settings, threadPool, this);
        this.indexScopeSetting = indexScopedSettings;
        this.circuitBreakerService = circuitBreakerService;
        this.indicesFieldDataCache = new IndicesFieldDataCache(settings, new IndexFieldDataCache.Listener() {
            @Override
            public void onRemoval(ShardId shardId, String fieldName, FieldDataType fieldDataType, boolean wasEvicted, long sizeInBytes) {
                assert sizeInBytes >= 0 : "When reducing circuit breaker, it should be adjusted with a number higher or equal to 0 and not [" + sizeInBytes + "]";
                circuitBreakerService.getBreaker(CircuitBreaker.FIELDDATA).addWithoutBreaking(-sizeInBytes);
            }
        });
        this.cleanInterval = INDICES_CACHE_CLEAN_INTERVAL_SETTING.get(settings);
        this.cacheCleaner = new CacheCleaner(indicesFieldDataCache, indicesRequestCache,  logger, threadPool, this.cleanInterval);


    }

    @Override
    protected void doStop() {
        ExecutorService indicesStopExecutor = Executors.newFixedThreadPool(5, EsExecutors.daemonThreadFactory("indices_shutdown"));

        // Copy indices because we modify it asynchronously in the body of the loop
        Set<String> indices = new HashSet<>(this.indices.keySet());
        final CountDownLatch latch = new CountDownLatch(indices.size());
        for (final String index : indices) {
            indicesStopExecutor.execute(() -> {
                try {
                    removeIndex(index, "shutdown", false);
                } catch (Throwable e) {
                    logger.warn("failed to remove index on stop [" + index + "]", e);
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            if (latch.await(shardsClosedTimeout.seconds(), TimeUnit.SECONDS) == false) {
              logger.warn("Not all shards are closed yet, waited {}sec - stopping service", shardsClosedTimeout.seconds());
            }
        } catch (InterruptedException e) {
            // ignore
        } finally {
            indicesStopExecutor.shutdown();
        }
    }

    @Override
    protected void doClose() {
        IOUtils.closeWhileHandlingException(analysisRegistry, indexingMemoryController, indicesFieldDataCache, cacheCleaner, indicesRequestCache, indicesQueryCache);
    }

    /**
     * Returns the node stats indices stats. The <tt>includePrevious</tt> flag controls
     * if old shards stats will be aggregated as well (only for relevant stats, such as
     * refresh and indexing, not for docs/store).
     */
    public NodeIndicesStats stats(boolean includePrevious) {
        return stats(includePrevious, new CommonStatsFlags().all());
    }

    public NodeIndicesStats stats(boolean includePrevious, CommonStatsFlags flags) {
        CommonStats oldStats = new CommonStats(flags);

        if (includePrevious) {
            Flag[] setFlags = flags.getFlags();
            for (Flag flag : setFlags) {
                switch (flag) {
                    case Get:
                        oldStats.get.add(oldShardsStats.getStats);
                        break;
                    case Indexing:
                        oldStats.indexing.add(oldShardsStats.indexingStats);
                        break;
                    case Search:
                        oldStats.search.add(oldShardsStats.searchStats);
                        break;
                    case Merge:
                        oldStats.merge.add(oldShardsStats.mergeStats);
                        break;
                    case Refresh:
                        oldStats.refresh.add(oldShardsStats.refreshStats);
                        break;
                    case Recovery:
                        oldStats.recoveryStats.add(oldShardsStats.recoveryStats);
                        break;
                    case Flush:
                        oldStats.flush.add(oldShardsStats.flushStats);
                        break;
                }
            }
        }

        Map<Index, List<IndexShardStats>> statsByShard = new HashMap<>();
        for (IndexService indexService : indices.values()) {
            for (IndexShard indexShard : indexService) {
                try {
                    if (indexShard.routingEntry() == null) {
                        continue;
                    }
                    IndexShardStats indexShardStats = new IndexShardStats(indexShard.shardId(), new ShardStats[] { new ShardStats(indexShard.routingEntry(), indexShard.shardPath(), new CommonStats(indicesQueryCache, indexShard, flags), indexShard.commitStats()) });
                    if (!statsByShard.containsKey(indexService.index())) {
                        statsByShard.put(indexService.index(), arrayAsArrayList(indexShardStats));
                    } else {
                        statsByShard.get(indexService.index()).add(indexShardStats);
                    }
                } catch (IllegalIndexShardStateException e) {
                    // we can safely ignore illegal state on ones that are closing for example
                    logger.trace("{} ignoring shard stats", e, indexShard.shardId());
                }
            }
        }
        return new NodeIndicesStats(oldStats, statsByShard);
    }

    /**
     * Returns <tt>true</tt> if changes (adding / removing) indices, shards and so on are allowed.
     */
    public boolean changesAllowed() {
        // we check on stop here since we defined stop when we delete the indices
        return lifecycle.started();
    }

    @Override
    public Iterator<IndexService> iterator() {
        return indices.values().iterator();
    }

    public boolean hasIndex(String index) {
        return indices.containsKey(index);
    }

    /**
     * Returns an IndexService for the specified index if exists otherwise returns <code>null</code>.
     *
     */
    @Nullable
    public IndexService indexService(String index) {
        return indices.get(index);
    }

    /**
     * Returns an IndexService for the specified index if exists otherwise returns <code>null</code>.
     *
     */
    @Nullable
    public IndexService indexService(Index index) {
        return indexService(index.getName());
    }

    /**
     * Returns an IndexService for the specified index if exists otherwise a {@link IndexNotFoundException} is thrown.
     */
    public IndexService indexServiceSafe(String index) {
        IndexService indexService = indexService(index);
        if (indexService == null) {
            throw new IndexNotFoundException(index);
        }
        return indexService;
    }

    /**
     * Returns an IndexService for the specified index if exists otherwise a {@link IndexNotFoundException} is thrown.
     */
    public IndexService indexServiceSafe(Index index) {
        IndexService indexService = indexServiceSafe(index.getName());
        if (indexService.indexUUID().equals(index.getUUID()) == false) {
            throw new IndexNotFoundException(index);
        }
        return indexService;
    }



    /**
     * Creates a new {@link IndexService} for the given metadata.
     * @param indexMetaData the index metadata to create the index for
     * @param builtInListeners a list of built-in lifecycle {@link IndexEventListener} that should should be used along side with the per-index listeners
     * @throws IndexAlreadyExistsException if the index already exists.
     */
    public synchronized IndexService createIndex(final NodeServicesProvider nodeServicesProvider, IndexMetaData indexMetaData, List<IndexEventListener> builtInListeners) throws IOException {
        if (!lifecycle.started()) {
            throw new IllegalStateException("Can't create an index [" + indexMetaData.getIndex() + "], node is closed");
        }
        final Index index = indexMetaData.getIndex();
        final Predicate<String> indexNameMatcher = (indexExpression) -> indexNameExpressionResolver.matchesIndex(index.getName(), indexExpression, clusterService.state());
        final IndexSettings idxSettings = new IndexSettings(indexMetaData, this.settings, indexNameMatcher, indexScopeSetting);
        if (indices.containsKey(index.getName())) {
            throw new IndexAlreadyExistsException(index);
        }
        logger.debug("creating Index [{}], shards [{}]/[{}{}]",
                indexMetaData.getIndex(),
                idxSettings.getNumberOfShards(),
                idxSettings.getNumberOfReplicas(),
                idxSettings.isShadowReplicaIndex() ? "s" : "");

        final IndexModule indexModule = new IndexModule(idxSettings, indexStoreConfig, analysisRegistry);
        pluginsService.onIndexModule(indexModule);
        for (IndexEventListener listener : builtInListeners) {
            indexModule.addIndexEventListener(listener);
        }
        final IndexEventListener onStoreClose = new IndexEventListener() {
            @Override
            public void onStoreClosed(ShardId shardId) {
                indicesQueryCache.onClose(shardId);
            }
        };
        indexModule.addIndexEventListener(onStoreClose);
        indexModule.addIndexEventListener(oldShardsStats);
        final IndexEventListener listener = indexModule.freeze();
        listener.beforeIndexCreated(index, idxSettings.getSettings());
        final IndexService indexService = indexModule.newIndexService(nodeEnv, this, nodeServicesProvider, indicesQueryCache, mapperRegistry, indicesFieldDataCache, indexingMemoryController);
        boolean success = false;
        try {
            assert indexService.getIndexEventListener() == listener;
            listener.afterIndexCreated(indexService);
            indices = newMapBuilder(indices).put(index.getName(), indexService).immutableMap();
            success = true;
            return indexService;
        } finally {
            if (success == false) {
                indexService.close("plugins_failed", true);
            }
        }

    }

    /**
     * Removes the given index from this service and releases all associated resources. Persistent parts of the index
     * like the shards files, state and transaction logs are kept around in the case of a disaster recovery.
     * @param index the index to remove
     * @param reason  the high level reason causing this removal
     */
    public void removeIndex(String index, String reason) {
        removeIndex(index, reason, false);
    }

    private void removeIndex(String index, String reason, boolean delete) {
        try {
            final IndexService indexService;
            final IndexEventListener listener;
            synchronized (this) {
                if (indices.containsKey(index) == false) {
                    return;
                }

                logger.debug("[{}] closing ... (reason [{}])", index, reason);
                Map<String, IndexService> newIndices = new HashMap<>(indices);
                indexService = newIndices.remove(index);
                indices = unmodifiableMap(newIndices);
                listener = indexService.getIndexEventListener();
            }

            listener.beforeIndexClosed(indexService);
            if (delete) {
                listener.beforeIndexDeleted(indexService);
            }
            logger.debug("[{}] closing index service (reason [{}])", index, reason);
            indexService.close(reason, delete);
            logger.debug("[{}] closed... (reason [{}])", index, reason);
            listener.afterIndexClosed(indexService.index(), indexService.getIndexSettings().getSettings());
            if (delete) {
                final IndexSettings indexSettings = indexService.getIndexSettings();
                listener.afterIndexDeleted(indexService.index(), indexSettings.getSettings());
                // now we are done - try to wipe data on disk if possible
                deleteIndexStore(reason, indexService.index(), indexSettings, false);
            }
        } catch (IOException ex) {
            throw new ElasticsearchException("failed to remove index " + index, ex);
        }
    }

    public IndicesFieldDataCache getIndicesFieldDataCache() {
        return indicesFieldDataCache;
    }

    public CircuitBreakerService getCircuitBreakerService() {
        return circuitBreakerService;
    }

    public IndicesQueryCache getIndicesQueryCache() {
        return indicesQueryCache;
    }

    static class OldShardsStats implements IndexEventListener {

        final SearchStats searchStats = new SearchStats();
        final GetStats getStats = new GetStats();
        final IndexingStats indexingStats = new IndexingStats();
        final MergeStats mergeStats = new MergeStats();
        final RefreshStats refreshStats = new RefreshStats();
        final FlushStats flushStats = new FlushStats();
        final RecoveryStats recoveryStats = new RecoveryStats();

        @Override
        public synchronized void beforeIndexShardClosed(ShardId shardId, @Nullable IndexShard indexShard, Settings indexSettings) {
            if (indexShard != null) {
                getStats.addTotals(indexShard.getStats());
                indexingStats.addTotals(indexShard.indexingStats());
                searchStats.addTotals(indexShard.searchStats());
                mergeStats.addTotals(indexShard.mergeStats());
                refreshStats.addTotals(indexShard.refreshStats());
                flushStats.addTotals(indexShard.flushStats());
                recoveryStats.addTotals(indexShard.recoveryStats());
            }
        }
    }

    /**
     * Deletes the given index. Persistent parts of the index
     * like the shards files, state and transaction logs are removed once all resources are released.
     *
     * Equivalent to {@link #removeIndex(String, String)} but fires
     * different lifecycle events to ensure pending resources of this index are immediately removed.
     * @param index the index to delete
     * @param reason the high level reason causing this delete
     */
    public void deleteIndex(String index, String reason) throws IOException {
        removeIndex(index, reason, true);
    }

    public void deleteClosedIndex(String reason, IndexMetaData metaData, ClusterState clusterState) {
        if (nodeEnv.hasNodeFile()) {
            String indexName = metaData.getIndex().getName();
            try {
                if (clusterState.metaData().hasIndex(indexName)) {
                    final IndexMetaData index = clusterState.metaData().index(indexName);
                    throw new IllegalStateException("Can't delete closed index store for [" + indexName + "] - it's still part of the cluster state [" + index.getIndexUUID() + "] [" + metaData.getIndexUUID() + "]");
                }
                deleteIndexStore(reason, metaData, clusterState, true);
            } catch (IOException e) {
                logger.warn("[{}] failed to delete closed index", e, metaData.getIndex());
            }
        }
    }

    /**
     * Deletes the index store trying to acquire all shards locks for this index.
     * This method will delete the metadata for the index even if the actual shards can't be locked.
     */
    public void deleteIndexStore(String reason, IndexMetaData metaData, ClusterState clusterState, boolean closed) throws IOException {
        if (nodeEnv.hasNodeFile()) {
            synchronized (this) {
                String indexName = metaData.getIndex().getName();
                if (indices.containsKey(indexName)) {
                    String localUUid = indices.get(indexName).indexUUID();
                    throw new IllegalStateException("Can't delete index store for [" + indexName + "] - it's still part of the indices service [" + localUUid + "] [" + metaData.getIndexUUID() + "]");
                }
                if (clusterState.metaData().hasIndex(indexName) && (clusterState.nodes().localNode().masterNode() == true)) {
                    // we do not delete the store if it is a master eligible node and the index is still in the cluster state
                    // because we want to keep the meta data for indices around even if no shards are left here
                    final IndexMetaData index = clusterState.metaData().index(indexName);
                    throw new IllegalStateException("Can't delete closed index store for [" + indexName + "] - it's still part of the cluster state [" + index.getIndexUUID() + "] [" + metaData.getIndexUUID() + "]");
                }
            }
            final IndexSettings indexSettings = buildIndexSettings(metaData);
            deleteIndexStore(reason, indexSettings.getIndex(), indexSettings, closed);
        }
    }

    private void deleteIndexStore(String reason, Index index, IndexSettings indexSettings, boolean closed) throws IOException {
        boolean success = false;
        try {
            // we are trying to delete the index store here - not a big deal if the lock can't be obtained
            // the store metadata gets wiped anyway even without the lock this is just best effort since
            // every shards deletes its content under the shard lock it owns.
            logger.debug("{} deleting index store reason [{}]", index, reason);
            if (canDeleteIndexContents(index, indexSettings, closed)) {
                nodeEnv.deleteIndexDirectorySafe(index, 0, indexSettings);
            }
            success = true;
        } catch (LockObtainFailedException ex) {
            logger.debug("{} failed to delete index store - at least one shards is still locked", ex, index);
        } catch (Exception ex) {
            logger.warn("{} failed to delete index", ex, index);
        } finally {
            if (success == false) {
                addPendingDelete(index, indexSettings);
            }
            // this is a pure protection to make sure this index doesn't get re-imported as a dangling index.
            // we should in the future rather write a tombstone rather than wiping the metadata.
            MetaDataStateFormat.deleteMetaState(nodeEnv.indexPaths(index.getName()));
        }
    }

    /**
     * Deletes the shard with an already acquired shard lock.
     * @param reason the reason for the shard deletion
     * @param lock the lock of the shard to delete
     * @param indexSettings the shards index settings.
     * @throws IOException if an IOException occurs
     */
    public void deleteShardStore(String reason, ShardLock lock, IndexSettings indexSettings) throws IOException {
        ShardId shardId = lock.getShardId();
        logger.trace("{} deleting shard reason [{}]", shardId, reason);
        nodeEnv.deleteShardDirectoryUnderLock(lock, indexSettings);
    }

    /**
     * This method deletes the shard contents on disk for the given shard ID. This method will fail if the shard deleting
     * is prevented by {@link #canDeleteShardContent(ShardId, IndexSettings)}
     * of if the shards lock can not be acquired.
     *
     * On data nodes, if the deleted shard is the last shard folder in its index, the method will attempt to remove the index folder as well.
     *
     * @param reason the reason for the shard deletion
     * @param shardId the shards ID to delete
     * @param clusterState . This is required to access the indexes settings etc.
     * @throws IOException if an IOException occurs
     */
    public void deleteShardStore(String reason, ShardId shardId, ClusterState clusterState) throws IOException {
        final IndexMetaData metaData = clusterState.getMetaData().indices().get(shardId.getIndexName());

        final IndexSettings indexSettings = buildIndexSettings(metaData);
        if (canDeleteShardContent(shardId, indexSettings) == false) {
            throw new IllegalStateException("Can't delete shard " + shardId);
        }
        nodeEnv.deleteShardDirectorySafe(shardId, indexSettings);
        logger.debug("{} deleted shard reason [{}]", shardId, reason);

        if (clusterState.nodes().localNode().isMasterNode() == false && // master nodes keep the index meta data, even if having no shards..
                canDeleteIndexContents(shardId.getIndex(), indexSettings, false)) {
            if (nodeEnv.findAllShardIds(shardId.getIndex()).isEmpty()) {
                try {
                    // note that deleteIndexStore have more safety checks and may throw an exception if index was concurrently created.
                    deleteIndexStore("no longer used", metaData, clusterState, false);
                } catch (Exception e) {
                    // wrap the exception to indicate we already deleted the shard
                    throw new ElasticsearchException("failed to delete unused index after deleting its last shard (" + shardId + ")", e);
                }
            } else {
                logger.trace("[{}] still has shard stores, leaving as is", shardId.getIndex());
            }
        }
    }

    /**
     * This method returns true if the current node is allowed to delete the
     * given index. If the index uses a shared filesystem this method always
     * returns false.
     * @param index {@code Index} to check whether deletion is allowed
     * @param indexSettings {@code IndexSettings} for the given index
     * @return true if the index can be deleted on this node
     */
    public boolean canDeleteIndexContents(Index index, IndexSettings indexSettings, boolean closed) {
        final IndexService indexService = this.indices.get(index.getName());
        // Closed indices may be deleted, even if they are on a shared
        // filesystem. Since it is closed we aren't deleting it for relocation
        if (indexSettings.isOnSharedFilesystem() == false || closed) {
            if (indexService == null && nodeEnv.hasNodeFile()) {
                return true;
            }
        } else {
            logger.trace("{} skipping index directory deletion due to shadow replicas", index);
        }
        return false;
    }

    /**
     * Returns <code>true</code> iff the shards content for the given shard can be deleted.
     * This method will return <code>false</code> if:
     * <ul>
     *     <li>if the shard is still allocated / active on this node</li>
     *     <li>if for instance if the shard is located on shared and should not be deleted</li>
     *     <li>if the shards data locations do not exists</li>
     * </ul>
     *
     * @param shardId the shard to delete.
     * @param indexSettings the shards's relevant {@link IndexSettings}. This is required to access the indexes settings etc.
     */
    public boolean canDeleteShardContent(ShardId shardId, IndexSettings indexSettings) {
        assert shardId.getIndex().equals(indexSettings.getIndex());
        final IndexService indexService = this.indices.get(shardId.getIndexName());
        if (indexSettings.isOnSharedFilesystem() == false) {
            if (indexService != null && nodeEnv.hasNodeFile()) {
                return indexService.hasShard(shardId.id()) == false;
            } else if (nodeEnv.hasNodeFile()) {
                if (indexSettings.hasCustomDataPath()) {
                    return Files.exists(nodeEnv.resolveCustomLocation(indexSettings, shardId));
                } else {
                    return FileSystemUtils.exists(nodeEnv.availableShardPaths(shardId));
                }
            }
        } else {
            logger.trace("{} skipping shard directory deletion due to shadow replicas", shardId);
        }
        return false;
    }

    private IndexSettings buildIndexSettings(IndexMetaData metaData) {
        // play safe here and make sure that we take node level settings into account.
        // we might run on nodes where we use shard FS and then in the future don't delete
        // actual content.
        return new IndexSettings(metaData, settings);
    }

    /**
     * Adds a pending delete for the given index shard.
     */
    public void addPendingDelete(ShardId shardId, IndexSettings settings) {
        if (shardId == null) {
            throw new IllegalArgumentException("shardId must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        PendingDelete pendingDelete = new PendingDelete(shardId, settings);
        addPendingDelete(shardId.getIndex(), pendingDelete);
    }

    /**
     * Adds a pending delete for the given index.
     */
    public void addPendingDelete(Index index, IndexSettings settings) {
        PendingDelete pendingDelete = new PendingDelete(index, settings);
        addPendingDelete(index, pendingDelete);
    }

    private void addPendingDelete(Index index, PendingDelete pendingDelete) {
        synchronized (pendingDeletes) {
            List<PendingDelete> list = pendingDeletes.get(index);
            if (list == null) {
                list = new ArrayList<>();
                pendingDeletes.put(index, list);
            }
            list.add(pendingDelete);
        }
    }

    private static final class PendingDelete implements Comparable<PendingDelete> {
        final Index index;
        final int shardId;
        final IndexSettings settings;
        final boolean deleteIndex;

        /**
         * Creates a new pending delete of an index
         */
        public PendingDelete(ShardId shardId, IndexSettings settings) {
            this.index = shardId.getIndex();
            this.shardId = shardId.getId();
            this.settings = settings;
            this.deleteIndex = false;
        }

        /**
         * Creates a new pending delete of a shard
         */
        public PendingDelete(Index index, IndexSettings settings) {
            this.index = index;
            this.shardId = -1;
            this.settings = settings;
            this.deleteIndex = true;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(index).append("]");
            if (shardId != -1) {
                sb.append("[").append(shardId).append("]");
            }
            return sb.toString();
        }

        @Override
        public int compareTo(PendingDelete o) {
            return Integer.compare(shardId, o.shardId);
        }
    }

    /**
     * Processes all pending deletes for the given index. This method will acquire all locks for the given index and will
     * process all pending deletes for this index. Pending deletes might occur if the OS doesn't allow deletion of files because
     * they are used by a different process ie. on Windows where files might still be open by a virus scanner. On a shared
     * filesystem a replica might not have been closed when the primary is deleted causing problems on delete calls so we
     * schedule there deletes later.
     * @param index the index to process the pending deletes for
     * @param timeout the timeout used for processing pending deletes
     */
    public void processPendingDeletes(Index index, IndexSettings indexSettings, TimeValue timeout) throws IOException, InterruptedException {
        logger.debug("{} processing pending deletes", index);
        final long startTimeNS = System.nanoTime();
        final List<ShardLock> shardLocks = nodeEnv.lockAllForIndex(index, indexSettings, timeout.millis());
        try {
            Map<ShardId, ShardLock> locks = new HashMap<>();
            for (ShardLock lock : shardLocks) {
                locks.put(lock.getShardId(), lock);
            }
            final List<PendingDelete> remove;
            synchronized (pendingDeletes) {
                 remove = pendingDeletes.remove(index);
            }
            if (remove != null && remove.isEmpty() == false) {
                CollectionUtil.timSort(remove); // make sure we delete indices first
                final long maxSleepTimeMs = 10 * 1000; // ensure we retry after 10 sec
                long sleepTime = 10;
                do {
                    if (remove.isEmpty()) {
                        break;
                    }
                    Iterator<PendingDelete> iterator = remove.iterator();
                    while (iterator.hasNext()) {
                        PendingDelete delete = iterator.next();

                        if (delete.deleteIndex) {
                            assert delete.shardId == -1;
                            logger.debug("{} deleting index store reason [{}]", index, "pending delete");
                            try {
                                nodeEnv.deleteIndexDirectoryUnderLock(index, indexSettings);
                                iterator.remove();
                            } catch (IOException ex) {
                                logger.debug("{} retry pending delete", ex, index);
                            }
                        } else {
                            assert delete.shardId != -1;
                            ShardLock shardLock = locks.get(new ShardId(delete.index, delete.shardId));
                            if (shardLock != null) {
                                try {
                                    deleteShardStore("pending delete", shardLock, delete.settings);
                                    iterator.remove();
                                } catch (IOException ex) {
                                    logger.debug("{} retry pending delete", ex, shardLock.getShardId());
                                }
                            } else {
                                logger.warn("{} no shard lock for pending delete", delete.shardId);
                                iterator.remove();
                            }
                        }
                    }
                    if (remove.isEmpty() == false) {
                        logger.warn("{} still pending deletes present for shards {} - retrying", index, remove.toString());
                        Thread.sleep(sleepTime);
                        sleepTime = Math.min(maxSleepTimeMs, sleepTime * 2); // increase the sleep time gradually
                        logger.debug("{} schedule pending delete retry after {} ms", index, sleepTime);
                    }
                } while ((System.nanoTime() - startTimeNS) < timeout.nanos());
            }
        } finally {
            IOUtils.close(shardLocks);
        }
    }

    int numPendingDeletes(Index index) {
        synchronized (pendingDeletes) {
            List<PendingDelete> deleteList = pendingDeletes.get(index);
            if (deleteList == null) {
                return 0;
            }
            return deleteList.size();
        }
    }

    /**
     * Returns this nodes {@link IndicesQueriesRegistry}
     */
    public IndicesQueriesRegistry getIndicesQueryRegistry() {
        return indicesQueriesRegistry;
    }

    public AnalysisRegistry getAnalysis() {
        return analysisRegistry;
    }

    /**
     * FieldDataCacheCleaner is a scheduled Runnable used to clean a Guava cache
     * periodically. In this case it is the field data cache, because a cache that
     * has an entry invalidated may not clean up the entry if it is not read from
     * or written to after invalidation.
     */
    private final static class CacheCleaner implements Runnable, Releasable {

        private final IndicesFieldDataCache cache;
        private final ESLogger logger;
        private final ThreadPool threadPool;
        private final TimeValue interval;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final IndicesRequestCache requestCache;

        public CacheCleaner(IndicesFieldDataCache cache, IndicesRequestCache requestCache, ESLogger logger, ThreadPool threadPool, TimeValue interval) {
            this.cache = cache;
            this.requestCache = requestCache;
            this.logger = logger;
            this.threadPool = threadPool;
            this.interval = interval;
        }

        @Override
        public void run() {
            long startTimeNS = System.nanoTime();
            if (logger.isTraceEnabled()) {
                logger.trace("running periodic field data cache cleanup");
            }
            try {
                this.cache.getCache().refresh();
            } catch (Exception e) {
                logger.warn("Exception during periodic field data cache cleanup:", e);
            }
            if (logger.isTraceEnabled()) {
                logger.trace("periodic field data cache cleanup finished in {} milliseconds", TimeValue.nsecToMSec(System.nanoTime() - startTimeNS));
            }

            try {
                this.requestCache.cleanCache();
            } catch (Exception e) {
                logger.warn("Exception during periodic request cache cleanup:", e);
            }
            // Reschedule itself to run again if not closed
            if (closed.get() == false) {
                threadPool.schedule(interval, ThreadPool.Names.SAME, this);
            }
        }

        @Override
        public void close() {
            closed.compareAndSet(false, true);
        }
    }


    private static final Set<SearchType> CACHEABLE_SEARCH_TYPES = EnumSet.of(SearchType.QUERY_THEN_FETCH, SearchType.QUERY_AND_FETCH);

    /**
     * Can the shard request be cached at all?
     */
    public boolean canCache(ShardSearchRequest request, SearchContext context) {
        if (request.template() != null) {
            return false;
        }

        // for now, only enable it for requests with no hits
        if (context.size() != 0) {
            return false;
        }

        // We cannot cache with DFS because results depend not only on the content of the index but also
        // on the overridden statistics. So if you ran two queries on the same index with different stats
        // (because an other shard was updated) you would get wrong results because of the scores
        // (think about top_hits aggs or scripts using the score)
        if (!CACHEABLE_SEARCH_TYPES.contains(context.searchType())) {
            return false;
        }
        IndexSettings settings = context.indexShard().getIndexSettings();
        // if not explicitly set in the request, use the index setting, if not, use the request
        if (request.requestCache() == null) {
            if (settings.getValue(IndicesRequestCache.INDEX_CACHE_REQUEST_ENABLED_SETTING) == false) {
                return false;
            }
        } else if (request.requestCache() == false) {
            return false;
        }
        // if the reader is not a directory reader, we can't get the version from it
        if ((context.searcher().getIndexReader() instanceof DirectoryReader) == false) {
            return false;
        }
        // if now in millis is used (or in the future, a more generic "isDeterministic" flag
        // then we can't cache based on "now" key within the search request, as it is not deterministic
        if (context.nowInMillisUsed()) {
            return false;
        }
        return true;

    }

    public void clearRequestCache(IndexShard shard) {
        if (shard == null) {
            return;
        }
        indicesRequestCache.clear(new IndexShardCacheEntity(shard));
        logger.trace("{} explicit cache clear", shard.shardId());
    }
    /**
     * Loads the cache result, computing it if needed by executing the query phase and otherwise deserializing the cached
     * value into the {@link SearchContext#queryResult() context's query result}. The combination of load + compute allows
     * to have a single load operation that will cause other requests with the same key to wait till its loaded an reuse
     * the same cache.
     */
    public void loadIntoContext(ShardSearchRequest request, SearchContext context, QueryPhase queryPhase) throws Exception {
        assert canCache(request, context);
        final IndexShardCacheEntity entity = new IndexShardCacheEntity(context.indexShard(), queryPhase, context);
        final DirectoryReader directoryReader = context.searcher().getDirectoryReader();
        final BytesReference bytesReference = indicesRequestCache.getOrCompute(entity, directoryReader, request.cacheKey());
        if (entity.loaded == false) { // if we have loaded this we don't need to do anything
            // restore the cached query result into the context
            final QuerySearchResult result = context.queryResult();
            result.readFromWithId(context.id(), bytesReference.streamInput());
            result.shardTarget(context.shardTarget());
        }
    }

    static final class IndexShardCacheEntity implements IndicesRequestCache.CacheEntity {
        private final QueryPhase queryPhase;
        private final SearchContext context;
        private final IndexShard indexShard;
        private final ShardRequestCache requestCache;
        private boolean loaded = false;

        IndexShardCacheEntity(IndexShard indexShard) {
            this(indexShard, null, null);
        }

        public IndexShardCacheEntity(IndexShard indexShard, QueryPhase queryPhase, SearchContext context) {
            this.queryPhase = queryPhase;
            this.context = context;
            this.indexShard = indexShard;
            this.requestCache = indexShard.requestCache();
        }

        @Override
        public IndicesRequestCache.Value loadValue() throws IOException {
            queryPhase.execute(context);
            /* BytesStreamOutput allows to pass the expected size but by default uses
             * BigArrays.PAGE_SIZE_IN_BYTES which is 16k. A common cached result ie.
             * a date histogram with 3 buckets is ~100byte so 16k might be very wasteful
             * since we don't shrink to the actual size once we are done serializing.
             * By passing 512 as the expected size we will resize the byte array in the stream
             * slowly until we hit the page size and don't waste too much memory for small query
             * results.*/
            final int expectedSizeInBytes = 512;
            try (BytesStreamOutput out = new BytesStreamOutput(expectedSizeInBytes)) {
                context.queryResult().writeToNoId(out);
                // for now, keep the paged data structure, which might have unused bytes to fill a page, but better to keep
                // the memory properly paged instead of having varied sized bytes
                final BytesReference reference = out.bytes();
                loaded = true;
                return new IndicesRequestCache.Value(reference, out.ramBytesUsed());
            }
        }

        @Override
        public void onCached(IndicesRequestCache.Key key, IndicesRequestCache.Value value) {
            requestCache.onCached(key, value);
        }


        @Override
        public boolean isOpen() {
            return indexShard.state() != IndexShardState.CLOSED;
        }

        @Override
        public Object getCacheIdentity() {
            return indexShard;
        }

        @Override
        public void onHit() {
            requestCache.onHit();
        }

        @Override
        public void onMiss() {
            requestCache.onMiss();
        }

        @Override
        public void onRemoval(RemovalNotification<IndicesRequestCache.Key, IndicesRequestCache.Value> notification) {
            requestCache.onRemoval(notification.getKey(), notification.getValue(), notification.getRemovalReason() == RemovalNotification.RemovalReason.EVICTED);
        }

    }

}
