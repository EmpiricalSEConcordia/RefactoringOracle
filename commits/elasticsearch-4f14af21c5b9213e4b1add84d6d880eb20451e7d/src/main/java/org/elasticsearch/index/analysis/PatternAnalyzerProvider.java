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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import java.lang.IllegalArgumentException;
import org.elasticsearch.Version;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.settings.IndexSettings;

import java.util.regex.Pattern;

/**
 *
 */
public class PatternAnalyzerProvider extends AbstractIndexAnalyzerProvider<Analyzer> {

    private final PatternAnalyzer analyzer;

    @Inject
    public PatternAnalyzerProvider(Index index, @IndexSettings Settings indexSettings, Environment env, @Assisted String name, @Assisted Settings settings) {
        super(index, indexSettings, name, settings);

        Version esVersion = Version.indexCreated(indexSettings);
        final CharArraySet defaultStopwords;
        if (esVersion.onOrAfter(Version.V_1_0_0_RC1)) {
            defaultStopwords = CharArraySet.EMPTY_SET;
        } else {
            defaultStopwords = StopAnalyzer.ENGLISH_STOP_WORDS_SET;
        }
        boolean lowercase = settings.getAsBoolean("lowercase", true);
        CharArraySet stopWords = Analysis.parseStopWords(env, settings, defaultStopwords);

        String sPattern = settings.get("pattern", "\\W+" /*PatternAnalyzer.NON_WORD_PATTERN*/);
        if (sPattern == null) {
            throw new IllegalArgumentException("Analyzer [" + name + "] of type pattern must have a `pattern` set");
        }
        Pattern pattern = Regex.compile(sPattern, settings.get("flags"));

        analyzer = new PatternAnalyzer(pattern, lowercase, stopWords);
    }

    @Override
    public PatternAnalyzer get() {
        return analyzer;
    }
}
