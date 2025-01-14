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

import com.carrotsearch.hppc.ObjectHashSet;
import com.carrotsearch.hppc.ObjectSet;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.cache.CacheLoader;
import org.elasticsearch.common.cache.RemovalListener;
import org.elasticsearch.common.cache.RemovalNotification;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.lucene.index.ElasticsearchDirectoryReader;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * The indices request cache allows to cache a shard level request stage responses, helping with improving
 * similar requests that are potentially expensive (because of aggs for example). The cache is fully coherent
 * with the semantics of NRT (the index reader version is part of the cache key), and relies on size based
 * eviction to evict old reader associated cache entries as well as scheduler reaper to clean readers that
 * are no longer used or closed shards.
 * <p>
 * Currently, the cache is only enabled for count requests, and can only be opted in on an index
 * level setting that can be dynamically changed and defaults to false.
 * <p>
 * There are still several TODOs left in this class, some easily addressable, some more complex, but the support
 * is functional.
 */
public final class IndicesRequestCache extends AbstractComponent implements RemovalListener<IndicesRequestCache.Key,
    IndicesRequestCache.Value>, Closeable {

    /**
     * A setting to enable or disable request caching on an index level. Its dynamic by default
     * since we are checking on the cluster state IndexMetaData always.
     */
    public static final Setting<Boolean> INDEX_CACHE_REQUEST_ENABLED_SETTING =
        Setting.boolSetting("index.requests.cache.enable", false, Property.Dynamic, Property.IndexScope);
    public static final Setting<ByteSizeValue> INDICES_CACHE_QUERY_SIZE =
        Setting.byteSizeSetting("indices.requests.cache.size", "1%", Property.NodeScope);
    public static final Setting<TimeValue> INDICES_CACHE_QUERY_EXPIRE =
        Setting.positiveTimeSetting("indices.requests.cache.expire", new TimeValue(0), Property.NodeScope);

    private final ConcurrentMap<CleanupKey, Boolean> registeredClosedListeners = ConcurrentCollections.newConcurrentMap();
    private final Set<CleanupKey> keysToClean = ConcurrentCollections.newConcurrentSet();
    private final ByteSizeValue size;
    private final TimeValue expire;
    private final Cache<Key, Value> cache;

    IndicesRequestCache(Settings settings) {
        super(settings);
        this.size = INDICES_CACHE_QUERY_SIZE.get(settings);
        this.expire = INDICES_CACHE_QUERY_EXPIRE.exists(settings) ? INDICES_CACHE_QUERY_EXPIRE.get(settings) : null;
        long sizeInBytes = size.bytes();
        CacheBuilder<Key, Value> cacheBuilder = CacheBuilder.<Key, Value>builder()
            .setMaximumWeight(sizeInBytes).weigher((k, v) -> k.ramBytesUsed() + v.ramBytesUsed()).removalListener(this);
        if (expire != null) {
            cacheBuilder.setExpireAfterAccess(TimeUnit.MILLISECONDS.toNanos(expire.millis()));
        }
        cache = cacheBuilder.build();
    }

    @Override
    public void close() {
        cache.invalidateAll();
    }

    void clear(CacheEntity entity) {
        keysToClean.add(new CleanupKey(entity, -1));
        cleanCache();
    }

    @Override
    public void onRemoval(RemovalNotification<Key, Value> notification) {
        notification.getKey().entity.onRemoval(notification);
    }

    BytesReference getOrCompute(CacheEntity cacheEntity, DirectoryReader reader, BytesReference cacheKey) throws Exception {
        final Key key =  new Key(cacheEntity, reader.getVersion(), cacheKey);
        Loader loader = new Loader(cacheEntity);
        Value value = cache.computeIfAbsent(key, loader);
        if (loader.isLoaded()) {
            key.entity.onMiss();
            // see if its the first time we see this reader, and make sure to register a cleanup key
            CleanupKey cleanupKey = new CleanupKey(cacheEntity, reader.getVersion());
            if (!registeredClosedListeners.containsKey(cleanupKey)) {
                Boolean previous = registeredClosedListeners.putIfAbsent(cleanupKey, Boolean.TRUE);
                if (previous == null) {
                    ElasticsearchDirectoryReader.addReaderCloseListener(reader, cleanupKey);
                }
            }
        } else {
            key.entity.onHit();
        }
        return value.reference;
    }

    private static class Loader implements CacheLoader<Key, Value> {

        private final CacheEntity entity;
        private boolean loaded;

        Loader(CacheEntity entity) {
            this.entity = entity;
        }

        public boolean isLoaded() {
            return this.loaded;
        }

        @Override
        public Value load(Key key) throws Exception {
            Value value = entity.loadValue();
            entity.onCached(key, value);
            loaded = true;
            return value;
        }
    }

    /**
     * Basic interface to make this cache testable.
     */
    interface CacheEntity {
        /**
         * Loads the actual cache value. this is the heavy lifting part.
         */
        Value loadValue() throws IOException;

        /**
         * Called after the value was loaded via {@link #loadValue()}
         */
        void onCached(Key key, Value value);

        /**
         * Returns <code>true</code> iff the resource behind this entity is still open ie.
         * entities assiciated with it can remain in the cache. ie. IndexShard is still open.
         */
        boolean isOpen();

        /**
         * Returns the cache identity. this is, similar to {@link #isOpen()} the resource identity behind this cache entity.
         * For instance IndexShard is the identity while a CacheEntity is per DirectoryReader. Yet, we group by IndexShard instance.
         */
        Object getCacheIdentity();

        /**
         * Called each time this entity has a cache hit.
         */
        void onHit();

        /**
         * Called each time this entity has a cache miss.
         */
        void onMiss();

        /**
         * Called when this entity instance is removed
         */
        void onRemoval(RemovalNotification<Key, Value> notification);
    }



    static class Value implements Accountable {
        final BytesReference reference;
        final long ramBytesUsed;

        Value(BytesReference reference, long ramBytesUsed) {
            this.reference = reference;
            this.ramBytesUsed = ramBytesUsed;
        }

        @Override
        public long ramBytesUsed() {
            return ramBytesUsed;
        }

        @Override
        public Collection<Accountable> getChildResources() {
            return Collections.emptyList();
        }
    }

    static class Key implements Accountable {
        public final CacheEntity entity; // use as identity equality
        public final long readerVersion; // use the reader version to now keep a reference to a "short" lived reader until its reaped
        public final BytesReference value;

        Key(CacheEntity entity, long readerVersion, BytesReference value) {
            this.entity = entity;
            this.readerVersion = readerVersion;
            this.value = value;
        }

        @Override
        public long ramBytesUsed() {
            return RamUsageEstimator.NUM_BYTES_OBJECT_REF + Long.BYTES + value.length();
        }

        @Override
        public Collection<Accountable> getChildResources() {
            // TODO: more detailed ram usage?
            return Collections.emptyList();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            Key key = (Key) o;
            if (readerVersion != key.readerVersion) return false;
            if (!entity.getCacheIdentity().equals(key.entity.getCacheIdentity())) return false;
            if (!value.equals(key.value)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = entity.getCacheIdentity().hashCode();
            result = 31 * result + Long.hashCode(readerVersion);
            result = 31 * result + value.hashCode();
            return result;
        }
    }

    private class CleanupKey implements IndexReader.ReaderClosedListener {
        final CacheEntity entity;
        final long readerVersion; // use the reader version to now keep a reference to a "short" lived reader until its reaped

        private CleanupKey(CacheEntity entity, long readerVersion) {
            this.entity = entity;
            this.readerVersion = readerVersion;
        }

        @Override
        public void onClose(IndexReader reader) {
            Boolean remove = registeredClosedListeners.remove(this);
            if (remove != null) {
                keysToClean.add(this);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            CleanupKey that = (CleanupKey) o;
            if (readerVersion != that.readerVersion) return false;
            if (!entity.getCacheIdentity().equals(that.entity.getCacheIdentity())) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = entity.getCacheIdentity().hashCode();
            result = 31 * result + Long.hashCode(readerVersion);
            return result;
        }
    }



    synchronized void cleanCache() {
        final ObjectSet<CleanupKey> currentKeysToClean = new ObjectHashSet<>();
        final ObjectSet<Object> currentFullClean = new ObjectHashSet<>();
        currentKeysToClean.clear();
        currentFullClean.clear();
        for (Iterator<CleanupKey> iterator = keysToClean.iterator(); iterator.hasNext(); ) {
            CleanupKey cleanupKey = iterator.next();
            iterator.remove();
            if (cleanupKey.readerVersion == -1 || cleanupKey.entity.isOpen() == false) {
                // -1 indicates full cleanup, as does a closed shard
                currentFullClean.add(cleanupKey.entity.getCacheIdentity());
            } else {
                currentKeysToClean.add(cleanupKey);
            }
        }
        if (!currentKeysToClean.isEmpty() || !currentFullClean.isEmpty()) {
            for (Iterator<Key> iterator = cache.keys().iterator(); iterator.hasNext(); ) {
                Key key = iterator.next();
                if (currentFullClean.contains(key.entity.getCacheIdentity())) {
                    iterator.remove();
                } else {
                    if (currentKeysToClean.contains(new CleanupKey(key.entity, key.readerVersion))) {
                        iterator.remove();
                    }
                }
            }
        }

        cache.refresh();
    }


    /**
     * Returns the current size of the cache
     */
    final int count() {
        return cache.count();
    }

    final int numRegisteredCloseListeners() { // for testing
        return registeredClosedListeners.size();
    }
}
