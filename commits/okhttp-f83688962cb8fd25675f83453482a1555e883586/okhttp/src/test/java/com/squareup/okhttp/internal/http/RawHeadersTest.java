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
package com.squareup.okhttp.internal.http;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;

public final class RawHeadersTest {
  @Test public void parseNameValueBlock() throws IOException {
    List<String> nameValueBlock = Arrays.asList(
        "cache-control", "no-cache, no-store",
        "set-cookie", "Cookie1\u0000Cookie2",
        ":status", "200 OK",
        ":version", "HTTP/1.1");
    RawHeaders rawHeaders = RawHeaders.fromNameValueBlock(nameValueBlock);
    assertEquals(4, rawHeaders.length());
    assertEquals("HTTP/1.1 200 OK", rawHeaders.getStatusLine());
    assertEquals("no-cache, no-store", rawHeaders.get("cache-control"));
    assertEquals("Cookie2", rawHeaders.get("set-cookie"));
    assertEquals("spdy/3", rawHeaders.get(ResponseHeaders.SELECTED_TRANSPORT));
    assertEquals(ResponseHeaders.SELECTED_TRANSPORT, rawHeaders.getFieldName(0));
    assertEquals("spdy/3", rawHeaders.getValue(0));
    assertEquals("cache-control", rawHeaders.getFieldName(1));
    assertEquals("no-cache, no-store", rawHeaders.getValue(1));
    assertEquals("set-cookie", rawHeaders.getFieldName(2));
    assertEquals("Cookie1", rawHeaders.getValue(2));
    assertEquals("set-cookie", rawHeaders.getFieldName(3));
    assertEquals("Cookie2", rawHeaders.getValue(3));
    assertNull(rawHeaders.get(":status"));
    assertNull(rawHeaders.get(":version"));
  }

  @Test public void toNameValueBlock() {
    RawHeaders.Builder builder = new RawHeaders.Builder();
    builder.add("cache-control", "no-cache, no-store");
    builder.add("set-cookie", "Cookie1");
    builder.add("set-cookie", "Cookie2");
    builder.add(":status", "200 OK");
    // TODO: fromNameValueBlock should take the status line headers
    List<String> nameValueBlock = builder.build().toNameValueBlock();
    List<String> expected = Arrays.asList(
        "cache-control", "no-cache, no-store",
        "set-cookie", "Cookie1\u0000Cookie2",
        ":status", "200 OK");
    assertEquals(expected, nameValueBlock);
  }

  @Test public void toNameValueBlockDropsForbiddenHeaders() {
    RawHeaders.Builder builder = new RawHeaders.Builder();
    builder.add("Connection", "close");
    builder.add("Transfer-Encoding", "chunked");
    assertEquals(Arrays.<String>asList(), builder.build().toNameValueBlock());
  }

  @Test public void statusMessage() throws IOException {
    RawHeaders.Builder builder = new RawHeaders.Builder();
    String message = "Temporary Redirect";
    int version = 1;
    int code = 200;
    builder.setStatusLine("HTTP/1." + version + " " + code + " " + message);
    RawHeaders rawHeaders = builder.build();
    assertEquals(message, rawHeaders.getResponseMessage());
    assertEquals(version, rawHeaders.getHttpMinorVersion());
    assertEquals(code, rawHeaders.getResponseCode());
  }

  @Test public void statusMessageWithEmptyMessage() throws IOException {
    RawHeaders.Builder builder = new RawHeaders.Builder();
    int version = 1;
    int code = 503;
    builder.setStatusLine("HTTP/1." + version + " " + code + " ");
    RawHeaders rawHeaders = builder.build();
    assertEquals("", rawHeaders.getResponseMessage());
    assertEquals(version, rawHeaders.getHttpMinorVersion());
    assertEquals(code, rawHeaders.getResponseCode());
  }

  /**
   * This is not defined in the protocol but some servers won't add the leading
   * empty space when the message is empty.
   * http://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6.1
   */
  @Test public void statusMessageWithEmptyMessageAndNoLeadingSpace() throws IOException {
    RawHeaders.Builder builder = new RawHeaders.Builder();
    int version = 1;
    int code = 503;
    builder.setStatusLine("HTTP/1." + version + " " + code);
    RawHeaders rawHeaders = builder.build();
    assertEquals("", rawHeaders.getResponseMessage());
    assertEquals(version, rawHeaders.getHttpMinorVersion());
    assertEquals(code, rawHeaders.getResponseCode());
  }
}
