/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
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

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.StemmerOverrideFilter;
import org.apache.lucene.analysis.miscellaneous.StemmerOverrideFilter.StemmerOverrideMap;
import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.settings.IndexSettings;

import java.io.IOException;
import java.util.List;

@AnalysisSettingsRequired
public class StemmerOverrideTokenFilterFactory extends AbstractTokenFilterFactory {

    private final StemmerOverrideMap overrideMap;

    @Inject
    public StemmerOverrideTokenFilterFactory(Index index, @IndexSettings Settings indexSettings, Environment env, @Assisted String name, @Assisted Settings settings) throws IOException {
        super(index, indexSettings, name, settings);

        List<String> rules = Analysis.getWordList(env, settings, "rules");
        if (rules == null) {
            throw new ElasticsearchIllegalArgumentException("stemmer override filter requires either `rules` or `rules_path` to be configured");
        }
        
        StemmerOverrideFilter.Builder builder = new StemmerOverrideFilter.Builder(false);
        parseRules(rules, builder, "=>");
        overrideMap = builder.build();

    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new StemmerOverrideFilter(tokenStream, overrideMap);
    }

    static void parseRules(List<String> rules, StemmerOverrideFilter.Builder builder, String mappingSep) {
        for (String rule : rules) {
            String key, override;
            List<String> mapping = Strings.splitSmart(rule, mappingSep, false);
            if (mapping.size() == 2) {
                key = mapping.get(0).trim();
                override = mapping.get(1).trim();
            } else {
                throw new RuntimeException("Invalid Keyword override Rule:" + rule);
            }

            if (key.isEmpty() || override.isEmpty()) {
                throw new RuntimeException("Invalid Keyword override Rule:" + rule);
            } else {
                builder.add(key, override);
            }
        }
    }

}
