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

package org.elasticsearch.memcached;

import org.elasticsearch.rest.support.AbstractRestRequest;
import org.elasticsearch.rest.support.RestUtils;
import org.elasticsearch.util.Booleans;
import org.elasticsearch.util.Unicode;
import org.elasticsearch.util.collect.ImmutableList;
import org.elasticsearch.util.collect.ImmutableSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author kimchy (shay.banon)
 */
public class MemcachedRestRequest extends AbstractRestRequest {

    private final Method method;

    private final String uri;

    private final byte[] uriBytes;

    private final int dataSize;

    private boolean binary;

    private final Map<String, String> params;

    private final String path;

    private byte[] data;

    private int opaque;

    public MemcachedRestRequest(Method method, String uri, byte[] uriBytes, int dataSize, boolean binary) {
        this.method = method;
        this.uri = uri;
        this.uriBytes = uriBytes;
        this.dataSize = dataSize;
        this.binary = binary;
        this.params = new HashMap<String, String>();
        int pathEndPos = uri.indexOf('?');
        if (pathEndPos < 0) {
            this.path = uri;
        } else {
            this.path = uri.substring(0, pathEndPos);
            RestUtils.decodeQueryString(uri, pathEndPos + 1, params);
        }
    }

    @Override public Method method() {
        return this.method;
    }

    @Override public String uri() {
        return this.uri;
    }

    @Override public String path() {
        return this.path;
    }

    public byte[] getUriBytes() {
        return uriBytes;
    }

    public boolean isBinary() {
        return binary;
    }

    public int getOpaque() {
        return opaque;
    }

    public void setOpaque(int opaque) {
        this.opaque = opaque;
    }

    public int getDataSize() {
        return dataSize;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override public boolean hasContent() {
        return data != null;
    }

    @Override public boolean contentUnsafe() {
        return false;
    }

    @Override public byte[] contentByteArray() {
        return data;
    }

    @Override public int contentByteArrayOffset() {
        return 0;
    }

    @Override public int contentLength() {
        return dataSize;
    }

    @Override public String contentAsString() {
        return Unicode.fromBytes(data);
    }

    @Override public Set<String> headerNames() {
        return ImmutableSet.of();
    }

    @Override public String header(String name) {
        return null;
    }

    @Override public List<String> headers(String name) {
        return ImmutableList.of();
    }

    @Override public String cookie() {
        return null;
    }

    @Override public boolean hasParam(String key) {
        return params.containsKey(key);
    }

    @Override public String param(String key) {
        return params.get(key);
    }

    @Override public Map<String, String> params() {
        return params;
    }

    @Override public String param(String key, String defaultValue) {
        String value = params.get(key);
        if (value == null) {
            return value;
        }
        return defaultValue;
    }

    @Override public boolean paramAsBoolean(String key, boolean defaultValue) {
        return Booleans.parseBoolean(param(key), defaultValue);
    }

    @Override public Boolean paramAsBoolean(String key, Boolean defaultValue) {
        String sValue = param(key);
        if (sValue == null) {
            return defaultValue;
        }
        return !(sValue.equals("false") || sValue.equals("0") || sValue.equals("off"));
    }
}
