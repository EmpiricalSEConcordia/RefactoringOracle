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

package org.elasticsearch.index.store.fs;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.elasticsearch.common.collect.ImmutableSet;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.lucene.store.SwitchDirectory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.index.settings.IndexSettings;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.store.memory.ByteBufferDirectory;
import org.elasticsearch.index.store.memory.HeapDirectory;
import org.elasticsearch.index.store.support.AbstractStore;

import java.io.IOException;

/**
 * @author kimchy (shay.banon)
 */
public abstract class AbstractFsStore<T extends Directory> extends AbstractStore<T> {

    public AbstractFsStore(ShardId shardId, @IndexSettings Settings indexSettings) {
        super(shardId, indexSettings);
    }

    @Override public void fullDelete() throws IOException {
        FileSystemUtils.deleteRecursively(fsDirectory().getFile());
        // if we are the last ones, delete also the actual index
        String[] list = fsDirectory().getFile().getParentFile().list();
        if (list == null || list.length == 0) {
            FileSystemUtils.deleteRecursively(fsDirectory().getFile().getParentFile());
        }
    }

    public abstract FSDirectory fsDirectory();

    protected SwitchDirectory buildSwitchDirectoryIfNeeded(Directory fsDirectory) {
        boolean cache = componentSettings.getAsBoolean("memory.enabled", false);
        if (!cache) {
            return null;
        }
        ByteSizeValue bufferSize = componentSettings.getAsBytesSize("memory.buffer_size", new ByteSizeValue(100, ByteSizeUnit.KB));
        ByteSizeValue cacheSize = componentSettings.getAsBytesSize("memory.cache_size", new ByteSizeValue(20, ByteSizeUnit.MB));
        boolean direct = componentSettings.getAsBoolean("memory.direct", true);
        boolean warmCache = componentSettings.getAsBoolean("memory.warm_cache", true);

        Directory memDir;
        if (direct) {
            memDir = new ByteBufferDirectory((int) bufferSize.bytes(), (int) cacheSize.bytes(), true, warmCache);
        } else {
            memDir = new HeapDirectory(bufferSize, cacheSize, warmCache);
        }
        // see http://lucene.apache.org/java/3_0_1/fileformats.html
        String[] primaryExtensions = componentSettings.getAsArray("memory.extensions", new String[]{"", "del", "gen"});
        return new SwitchDirectory(ImmutableSet.copyOf(primaryExtensions), memDir, fsDirectory, true);
    }
}
