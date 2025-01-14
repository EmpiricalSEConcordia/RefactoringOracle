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

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.Lucene43StopFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.search.suggest.analyzing.SuggestStopFilter;
import org.apache.lucene.util.Version;
import java.lang.IllegalArgumentException;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.settings.IndexSettings;

import java.util.Set;

/**
 *
 */
@SuppressWarnings("deprecation")
public class StopTokenFilterFactory extends AbstractTokenFilterFactory {

    private final CharArraySet stopWords;

    private final boolean ignoreCase;

    private final boolean enablePositionIncrements;
    private final boolean removeTrailing;

    @Inject
    public StopTokenFilterFactory(Index index, @IndexSettings Settings indexSettings, Environment env, @Assisted String name, @Assisted Settings settings) {
        super(index, indexSettings, name, settings);
        this.ignoreCase = settings.getAsBoolean("ignore_case", false);
        this.removeTrailing = settings.getAsBoolean("remove_trailing", true);
        this.stopWords = Analysis.parseStopWords(env, settings, StopAnalyzer.ENGLISH_STOP_WORDS_SET, ignoreCase);
        if (version.onOrAfter(Version.LUCENE_4_4) && settings.get("enable_position_increments") != null) {
            throw new IllegalArgumentException("enable_position_increments is not supported anymore as of Lucene 4.4 as it can create broken token streams."
                    + " Please fix your analysis chain or use an older compatibility version (<= 4.3).");
        }
        this.enablePositionIncrements = settings.getAsBoolean("enable_position_increments", true);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        if (removeTrailing) {
            if (version.onOrAfter(Version.LUCENE_4_4)) {
                return new StopFilter(tokenStream, stopWords);
            } else {
                return new Lucene43StopFilter(enablePositionIncrements, tokenStream, stopWords);
            }
        } else {
            return new SuggestStopFilter(tokenStream, stopWords);
        }
    }

    public Set<?> stopWords() {
        return stopWords;
    }

    public boolean ignoreCase() {
        return ignoreCase;
    }

}
