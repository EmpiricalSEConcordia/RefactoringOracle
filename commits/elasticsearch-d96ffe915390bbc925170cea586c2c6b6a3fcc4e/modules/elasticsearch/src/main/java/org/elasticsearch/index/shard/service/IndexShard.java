/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.index.shard.service;

import org.apache.lucene.index.Term;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.component.CloseableComponent;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.util.concurrent.ThreadSafe;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.engine.EngineException;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.index.shard.IndexShardComponent;
import org.elasticsearch.index.shard.IndexShardState;

import javax.annotation.Nullable;

/**
 * @author kimchy (shay.banon)
 */
@ThreadSafe
public interface IndexShard extends IndexShardComponent, CloseableComponent {

    ShardRouting routingEntry();

    IndexShardState state();

    /**
     * Returns the estimated flushable memory size. Returns <tt>null</tt> if not available.
     */
    ByteSizeValue estimateFlushableMemorySize() throws ElasticSearchException;

    Engine.Create prepareCreate(String type, String id, byte[] source) throws ElasticSearchException;

    ParsedDocument create(String type, String id, byte[] source) throws ElasticSearchException;

    ParsedDocument create(Engine.Create create) throws ElasticSearchException;

    Engine.Index prepareIndex(String type, String id, byte[] source) throws ElasticSearchException;

    ParsedDocument index(Engine.Index index) throws ElasticSearchException;

    ParsedDocument index(String type, String id, byte[] source) throws ElasticSearchException;

    Engine.Delete prepareDelete(String type, String id) throws ElasticSearchException;

    void delete(Engine.Delete delete) throws ElasticSearchException;

    void delete(String type, String id) throws ElasticSearchException;

    void delete(Term uid) throws ElasticSearchException;

    void deleteByQuery(byte[] querySource, @Nullable String queryParserName, String... types) throws ElasticSearchException;

    byte[] get(String type, String id) throws ElasticSearchException;

    long count(float minScore, byte[] querySource, @Nullable String queryParserName, String... types) throws ElasticSearchException;

    long count(float minScore, byte[] querySource, int querySourceOffset, int querySourceLength, @Nullable String queryParserName, String... types) throws ElasticSearchException;

    void refresh(Engine.Refresh refresh) throws ElasticSearchException;

    void flush(Engine.Flush flush) throws ElasticSearchException;

    void optimize(Engine.Optimize optimize) throws ElasticSearchException;

    <T> T snapshot(Engine.SnapshotHandler<T> snapshotHandler) throws EngineException;

    void recover(Engine.RecoveryHandler recoveryHandler) throws EngineException;

    Engine.Searcher searcher();

    /**
     * Returns <tt>true</tt> if this shard can ignore a recovery attempt made to it (since the already doing/done it)
     */
    public boolean ignoreRecoveryAttempt();
}
