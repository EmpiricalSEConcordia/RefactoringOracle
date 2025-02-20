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

package org.elasticsearch.action.admin.cluster.snapshots.create;

import org.elasticsearch.ElasticsearchGenerationException;
import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.master.MasterNodeOperationRequest;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.action.ValidateActions.addValidationError;
import static org.elasticsearch.common.Strings.EMPTY_ARRAY;
import static org.elasticsearch.common.Strings.hasLength;
import static org.elasticsearch.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS;
import static org.elasticsearch.common.settings.ImmutableSettings.readSettingsFromStream;
import static org.elasticsearch.common.settings.ImmutableSettings.writeSettingsToStream;

/**
 * Create snapshot request
 * <p/>
 * The only mandatory parameter is repository name. The repository name has to satisfy the following requirements
 * <ul>
 * <li>be a non-empty string</li>
 * <li>must not contain whitespace (tabs or spaces)</li>
 * <li>must not contain comma (',')</li>
 * <li>must not contain hash sign ('#')</li>
 * <li>must not start with underscore ('-')</li>
 * <li>must be lowercase</li>
 * <li>must not contain invalid file name characters {@link org.elasticsearch.common.Strings#INVALID_FILENAME_CHARS} </li>
 * </ul>
 */
public class CreateSnapshotRequest extends MasterNodeOperationRequest<CreateSnapshotRequest> {

    private String snapshot;

    private String repository;

    private String[] indices = EMPTY_ARRAY;

    private IndicesOptions indicesOptions = IndicesOptions.strict();

    private Settings settings = EMPTY_SETTINGS;

    private boolean includeGlobalState = true;

    private boolean waitForCompletion;

    CreateSnapshotRequest() {
    }

    /**
     * Constructs a new put repository request with the provided snapshot and repository names
     *
     * @param repository repository name
     * @param snapshot   snapshot name
     */
    public CreateSnapshotRequest(String repository, String snapshot) {
        this.snapshot = snapshot;
        this.repository = repository;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (snapshot == null) {
            validationException = addValidationError("snapshot is missing", validationException);
        }
        if (repository == null) {
            validationException = addValidationError("repository is missing", validationException);
        }
        if (indices == null) {
            validationException = addValidationError("indices is null", validationException);
        }
        for (String index : indices) {
            if (index == null) {
                validationException = addValidationError("index is null", validationException);
                break;
            }
        }
        if (indicesOptions == null) {
            validationException = addValidationError("indicesOptions is null", validationException);
        }
        if (settings == null) {
            validationException = addValidationError("settings is null", validationException);
        }
        return validationException;
    }

    /**
     * Sets the snapshot name
     *
     * @param snapshot snapshot name
     */
    public CreateSnapshotRequest snapshot(String snapshot) {
        this.snapshot = snapshot;
        return this;
    }

    /**
     * The snapshot name
     *
     * @return snapshot name
     */
    public String snapshot() {
        return this.snapshot;
    }

    /**
     * Sets repository name
     *
     * @param repository name
     * @return this request
     */
    public CreateSnapshotRequest repository(String repository) {
        this.repository = repository;
        return this;
    }

    /**
     * Returns repository name
     *
     * @return repository name
     */
    public String repository() {
        return this.repository;
    }

    /**
     * Sets a list of indices that should be included into the snapshot
     * <p/>
     * The list of indices supports multi-index syntax. For example: "+test*" ,"-test42" will index all indices with
     * prefix "test" except index "test42". Aliases are supported. An empty list or {"_all"} will snapshot all open
     * indices in the cluster.
     *
     * @param indices
     * @return this request
     */
    public CreateSnapshotRequest indices(String... indices) {
        this.indices = indices;
        return this;
    }

    /**
     * Sets a list of indices that should be included into the snapshot
     * <p/>
     * The list of indices supports multi-index syntax. For example: "+test*" ,"-test42" will index all indices with
     * prefix "test" except index "test42". Aliases are supported. An empty list or {"_all"} will snapshot all open
     * indices in the cluster.
     *
     * @param indices
     * @return this request
     */
    public CreateSnapshotRequest indices(List<String> indices) {
        this.indices = indices.toArray(new String[indices.size()]);
        return this;
    }

    /**
     * Retuns a list of indices that should be included into the snapshot
     *
     * @return list of indices
     */
    public String[] indices() {
        return indices;
    }

    /**
     * Specifies the indices options. Like what type of requested indices to ignore. For example indices that don't exist.
     *
     * @return the desired behaviour regarding indices options
     */
    public IndicesOptions indicesOptions() {
        return indicesOptions;
    }

    /**
     * Specifies the indices options. Like what type of requested indices to ignore. For example indices that don't exist.
     *
     * @param indicesOptions the desired behaviour regarding indices options
     * @return this request
     */
    public CreateSnapshotRequest indicesOptions(IndicesOptions indicesOptions) {
        this.indicesOptions = indicesOptions;
        return this;
    }

    /**
     * If set to true the request should wait for the snapshot completion before returning.
     *
     * @param waitForCompletion true if
     * @return this request
     */
    public CreateSnapshotRequest waitForCompletion(boolean waitForCompletion) {
        this.waitForCompletion = waitForCompletion;
        return this;
    }

    /**
     * Returns true if the request should wait for the snapshot completion before returning
     *
     * @return true if the request should wait for completion
     */
    public boolean waitForCompletion() {
        return waitForCompletion;
    }

    /**
     * Sets repository-specific snapshot settings.
     * <p/>
     * See repository documentation for more information.
     *
     * @param settings repository-specific snapshot settings
     * @return this request
     */
    public CreateSnapshotRequest settings(Settings settings) {
        this.settings = settings;
        return this;
    }

    /**
     * Sets repository-specific snapshot settings.
     * <p/>
     * See repository documentation for more information.
     *
     * @param settings repository-specific snapshot settings
     * @return this request
     */
    public CreateSnapshotRequest settings(Settings.Builder settings) {
        this.settings = settings.build();
        return this;
    }

    /**
     * Sets repository-specific snapshot settings in JSON, YAML or properties format
     * <p/>
     * See repository documentation for more information.
     *
     * @param source repository-specific snapshot settings
     * @return this request
     */
    public CreateSnapshotRequest settings(String source) {
        this.settings = ImmutableSettings.settingsBuilder().loadFromSource(source).build();
        return this;
    }

    /**
     * Sets repository-specific snapshot settings.
     * <p/>
     * See repository documentation for more information.
     *
     * @param source repository-specific snapshot settings
     * @return this request
     */
    public CreateSnapshotRequest settings(Map<String, Object> source) {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
            builder.map(source);
            settings(builder.string());
        } catch (IOException e) {
            throw new ElasticsearchGenerationException("Failed to generate [" + source + "]", e);
        }
        return this;
    }

    /**
     * Returns repository-specific snapshot settings
     *
     * @return repository-specific snapshot settings
     */
    public Settings settings() {
        return this.settings;
    }

    /**
     * Set to true if global state should be stored as part of the snapshot
     *
     * @param includeGlobalState true if global state should be stored
     * @return this request
     */
    public CreateSnapshotRequest includeGlobalState(boolean includeGlobalState) {
        this.includeGlobalState = includeGlobalState;
        return this;
    }

    /**
     * Returns true if global state should be stored as part of the snapshot
     * @return true if global state should be stored as part of the snapshot
     */
    public boolean includeGlobalState() {
        return includeGlobalState;
    }

    /**
     * Parses snapshot definition.
     *
     * @param source snapshot definition
     * @return this request
     */
    public CreateSnapshotRequest source(XContentBuilder source) {
        return source(source.bytes());
    }

    /**
     * Parses snapshot definition.
     *
     * @param source snapshot definition
     * @return this request
     */
    public CreateSnapshotRequest source(Map source) {
        boolean ignoreUnavailable = IndicesOptions.lenient().ignoreUnavailable();
        boolean allowNoIndices = IndicesOptions.lenient().allowNoIndices();
        boolean expandWildcardsOpen = IndicesOptions.lenient().expandWildcardsOpen();
        boolean expandWildcardsClosed = IndicesOptions.lenient().expandWildcardsClosed();
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) source).entrySet()) {
            String name = entry.getKey();
            if (name.equals("indices")) {
                if (entry.getValue() instanceof String) {
                    indices(Strings.splitStringByCommaToArray((String) entry.getValue()));
                } else if (entry.getValue() instanceof ArrayList) {
                    indices((ArrayList<String>) entry.getValue());
                } else {
                    throw new ElasticsearchIllegalArgumentException("malformed indices section, should be an array of strings");
                }
            } else if (name.equals("ignore_unavailable") || name.equals("ignoreUnavailable")) {
                assert entry.getValue() instanceof String;
                ignoreUnavailable = Boolean.valueOf(entry.getValue().toString());
            } else if (name.equals("allow_no_indices") || name.equals("allowNoIndices")) {
                assert entry.getValue() instanceof String;
                allowNoIndices = Boolean.valueOf(entry.getValue().toString());
            } else if (name.equals("expand_wildcards_open") || name.equals("expandWildcardsOpen")) {
                assert entry.getValue() instanceof String;
                expandWildcardsOpen = Boolean.valueOf(entry.getValue().toString());
            } else if (name.equals("expand_wildcards_closed") || name.equals("expandWildcardsClosed")) {
                assert entry.getValue() instanceof String;
                expandWildcardsClosed = Boolean.valueOf(entry.getValue().toString());
            } else if (name.equals("settings")) {
                if (!(entry.getValue() instanceof Map)) {
                    throw new ElasticsearchIllegalArgumentException("malformed settings section, should indices an inner object");
                }
                settings((Map<String, Object>) entry.getValue());
            } else if (name.equals("include_global_state")) {
                if (!(entry.getValue() instanceof Boolean)) {
                    throw new ElasticsearchIllegalArgumentException("malformed include_global_state, should be boolean");
                }
                includeGlobalState((Boolean) entry.getValue());
            }
        }
        indicesOptions(IndicesOptions.fromOptions(ignoreUnavailable, allowNoIndices, expandWildcardsOpen, expandWildcardsClosed));
        return this;
    }

    /**
     * Parses snapshot definition. JSON, YAML and properties formats are supported
     *
     * @param source snapshot definition
     * @return this request
     */
    public CreateSnapshotRequest source(String source) {
        if (hasLength(source)) {
            try {
                return source(XContentFactory.xContent(source).createParser(source).mapOrderedAndClose());
            } catch (Exception e) {
                throw new ElasticsearchIllegalArgumentException("failed to parse repository source [" + source + "]", e);
            }
        }
        return this;
    }

    /**
     * Parses snapshot definition. JSON, YAML and properties formats are supported
     *
     * @param source snapshot definition
     * @return this request
     */
    public CreateSnapshotRequest source(byte[] source) {
        return source(source, 0, source.length);
    }

    /**
     * Parses snapshot definition. JSON, YAML and properties formats are supported
     *
     * @param source snapshot definition
     * @param offset offset
     * @param length length
     * @return this request
     */
    public CreateSnapshotRequest source(byte[] source, int offset, int length) {
        if (length > 0) {
            try {
                return source(XContentFactory.xContent(source, offset, length).createParser(source, offset, length).mapOrderedAndClose());
            } catch (IOException e) {
                throw new ElasticsearchIllegalArgumentException("failed to parse repository source", e);
            }
        }
        return this;
    }

    /**
     * Parses snapshot definition. JSON, YAML and properties formats are supported
     *
     * @param source snapshot definition
     * @return this request
     */
    public CreateSnapshotRequest source(BytesReference source) {
        try {
            return source(XContentFactory.xContent(source).createParser(source).mapOrderedAndClose());
        } catch (IOException e) {
            throw new ElasticsearchIllegalArgumentException("failed to parse snapshot source", e);
        }
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        snapshot = in.readString();
        repository = in.readString();
        indices = in.readStringArray();
        indicesOptions = IndicesOptions.readIndicesOptions(in);
        settings = readSettingsFromStream(in);
        includeGlobalState = in.readBoolean();
        waitForCompletion = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(snapshot);
        out.writeString(repository);
        out.writeStringArray(indices);
        indicesOptions.writeIndicesOptions(out);
        writeSettingsToStream(settings, out);
        out.writeBoolean(includeGlobalState);
        out.writeBoolean(waitForCompletion);
    }
}
