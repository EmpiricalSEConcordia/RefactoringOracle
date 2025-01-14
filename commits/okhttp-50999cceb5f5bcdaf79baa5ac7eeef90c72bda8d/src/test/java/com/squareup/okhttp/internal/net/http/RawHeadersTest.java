/*
 * Copyright (C) 2012 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.okhttp.internal.net.http;

import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

public final class RawHeadersTest extends TestCase {
    public void testParseNameValueBlock() {
        List<String> nameValueBlock = Arrays.asList(
                "cache-control",
                "no-cache, no-store",
                "set-cookie",
                "Cookie1\u0000Cookie2",
                "status", "200 OK"
        );
        RawHeaders rawHeaders = RawHeaders.fromNameValueBlock(nameValueBlock);
        assertEquals("no-cache, no-store", rawHeaders.get("cache-control"));
        assertEquals("Cookie2", rawHeaders.get("set-cookie"));
        assertEquals("200 OK", rawHeaders.get("status"));
        assertEquals("cache-control", rawHeaders.getFieldName(0));
        assertEquals("no-cache, no-store", rawHeaders.getValue(0));
        assertEquals("set-cookie", rawHeaders.getFieldName(1));
        assertEquals("Cookie1", rawHeaders.getValue(1));
        assertEquals("set-cookie", rawHeaders.getFieldName(2));
        assertEquals("Cookie2", rawHeaders.getValue(2));
        assertEquals("status", rawHeaders.getFieldName(3));
        assertEquals("200 OK", rawHeaders.getValue(3));
    }

    public void testToNameValueBlock() {
        RawHeaders rawHeaders = new RawHeaders();
        rawHeaders.add("cache-control", "no-cache, no-store");
        rawHeaders.add("set-cookie", "Cookie1");
        rawHeaders.add("set-cookie", "Cookie2");
        rawHeaders.add("status", "200 OK");
        List<String> nameValueBlock = rawHeaders.toNameValueBlock();
        List<String> expected = Arrays.asList(
                "cache-control",
                "no-cache, no-store",
                "set-cookie",
                "Cookie1\u0000Cookie2",
                "status", "200 OK"
        );
        assertEquals(expected, nameValueBlock);
    }
}
