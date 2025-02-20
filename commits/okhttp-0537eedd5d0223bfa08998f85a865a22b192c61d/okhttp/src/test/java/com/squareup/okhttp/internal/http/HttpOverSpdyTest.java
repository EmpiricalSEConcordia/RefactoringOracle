/*
 * Copyright (C) 2013 Square, Inc.
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

import com.squareup.okhttp.HttpResponseCache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.internal.RecordingAuthenticator;
import com.squareup.okhttp.internal.SslContextBuilder;
import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import com.squareup.okhttp.mockwebserver.SocketPolicy;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Test how SPDY interacts with HTTP features. */
public abstract class HttpOverSpdyTest {

  /** Protocol to test, for example {@link com.squareup.okhttp.Protocol#SPDY_3} */
  private final Protocol protocol;
  protected String hostHeader = ":host";

  protected HttpOverSpdyTest(Protocol protocol){
    this.protocol = protocol;
  }

  private static final HostnameVerifier NULL_HOSTNAME_VERIFIER = new HostnameVerifier() {
    public boolean verify(String hostname, SSLSession session) {
      return true;
    }
  };

  private static final SSLContext sslContext = SslContextBuilder.localhost();
  private final MockWebServer server = new MockWebServer();
  private final String hostName = server.getHostName();
  private final OkHttpClient client = new OkHttpClient();
  private HttpURLConnection connection;
  private HttpResponseCache cache;

  @Before public void setUp() throws Exception {
    server.useHttps(sslContext.getSocketFactory(), false);
    client.setProtocols(Arrays.asList(protocol, Protocol.HTTP_11));
    client.setSslSocketFactory(sslContext.getSocketFactory());
    client.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);
    String systemTmpDir = System.getProperty("java.io.tmpdir");
    File cacheDir = new File(systemTmpDir, "HttpCache-" + protocol + "-" + UUID.randomUUID());
    cache = new HttpResponseCache(cacheDir, Integer.MAX_VALUE);
  }

  @After public void tearDown() throws Exception {
    Authenticator.setDefault(null);
    server.shutdown();
  }

  @Test public void get() throws Exception {
    MockResponse response = new MockResponse().setBody("ABCDE").setStatus("HTTP/1.1 200 Sweet");
    server.enqueue(response);
    server.play();

    connection = client.open(server.getUrl("/foo"));
    assertContent("ABCDE", connection, Integer.MAX_VALUE);
    assertEquals(200, connection.getResponseCode());
    assertEquals("Sweet", connection.getResponseMessage());

    RecordedRequest request = server.takeRequest();
    assertEquals("GET /foo HTTP/1.1", request.getRequestLine());
    assertContains(request.getHeaders(), ":scheme: https");
    assertContains(request.getHeaders(), hostHeader + ": " + hostName + ":" + server.getPort());
  }

  @Test public void emptyResponse() throws IOException {
    server.enqueue(new MockResponse());
    server.play();

    connection = client.open(server.getUrl("/foo"));
    assertEquals(-1, connection.getInputStream().read());
  }

  byte[] postBytes = "FGHIJ".getBytes(Util.UTF_8);

  /** An output stream can be written to more than once, so we can't guess content length. */
  @Test public void noDefaultContentLengthOnPost() throws Exception {
    MockResponse response = new MockResponse().setBody("ABCDE");
    server.enqueue(response);
    server.play();

    connection = client.open(server.getUrl("/foo"));
    connection.setDoOutput(true);
    connection.getOutputStream().write(postBytes);
    assertContent("ABCDE", connection, Integer.MAX_VALUE);

    RecordedRequest request = server.takeRequest();
    assertEquals("POST /foo HTTP/1.1", request.getRequestLine());
    assertArrayEquals(postBytes, request.getBody());
    assertNull(request.getHeader("Content-Length"));
  }

  @Test public void userSuppliedContentLengthHeader() throws Exception {
    MockResponse response = new MockResponse().setBody("ABCDE");
    server.enqueue(response);
    server.play();

    connection = client.open(server.getUrl("/foo"));
    connection.setRequestProperty("Content-Length", String.valueOf(postBytes.length));
    connection.setDoOutput(true);
    connection.getOutputStream().write(postBytes);
    assertContent("ABCDE", connection, Integer.MAX_VALUE);

    RecordedRequest request = server.takeRequest();
    assertEquals("POST /foo HTTP/1.1", request.getRequestLine());
    assertArrayEquals(postBytes, request.getBody());
    assertEquals(postBytes.length, Integer.parseInt(request.getHeader("Content-Length")));
  }

  @Test public void closeAfterFlush() throws Exception {
    MockResponse response = new MockResponse().setBody("ABCDE");
    server.enqueue(response);
    server.play();

    connection = client.open(server.getUrl("/foo"));
    connection.setRequestProperty("Content-Length", String.valueOf(postBytes.length));
    connection.setDoOutput(true);
    connection.getOutputStream().write(postBytes); // push bytes into SpdyDataOutputStream.buffer
    connection.getOutputStream().flush(); // SpdyConnection.writeData subject to write window
    connection.getOutputStream().close(); // SpdyConnection.writeData empty frame
    assertContent("ABCDE", connection, Integer.MAX_VALUE);

    RecordedRequest request = server.takeRequest();
    assertEquals("POST /foo HTTP/1.1", request.getRequestLine());
    assertArrayEquals(postBytes, request.getBody());
    assertEquals(postBytes.length, Integer.parseInt(request.getHeader("Content-Length")));
  }

  @Test public void setFixedLengthStreamingModeSetsContentLength() throws Exception {
    MockResponse response = new MockResponse().setBody("ABCDE");
    server.enqueue(response);
    server.play();

    connection = client.open(server.getUrl("/foo"));
    connection.setFixedLengthStreamingMode(postBytes.length);
    connection.setDoOutput(true);
    connection.getOutputStream().write(postBytes);
    assertContent("ABCDE", connection, Integer.MAX_VALUE);

    RecordedRequest request = server.takeRequest();
    assertEquals("POST /foo HTTP/1.1", request.getRequestLine());
    assertArrayEquals(postBytes, request.getBody());
    assertEquals(postBytes.length, Integer.parseInt(request.getHeader("Content-Length")));
  }

  @Test public void spdyConnectionReuse() throws Exception {
    server.enqueue(new MockResponse().setBody("ABCDEF"));
    server.enqueue(new MockResponse().setBody("GHIJKL"));
    server.play();

    HttpURLConnection connection1 = client.open(server.getUrl("/r1"));
    HttpURLConnection connection2 = client.open(server.getUrl("/r2"));
    assertEquals("ABC", readAscii(connection1.getInputStream(), 3));
    assertEquals("GHI", readAscii(connection2.getInputStream(), 3));
    assertEquals("DEF", readAscii(connection1.getInputStream(), 3));
    assertEquals("JKL", readAscii(connection2.getInputStream(), 3));
    assertEquals(0, server.takeRequest().getSequenceNumber());
    assertEquals(1, server.takeRequest().getSequenceNumber());
  }

  @Test @Ignore public void synchronousSpdyRequest() throws Exception {
    server.enqueue(new MockResponse().setBody("A"));
    server.enqueue(new MockResponse().setBody("A"));
    server.play();

    ExecutorService executor = Executors.newCachedThreadPool();
    CountDownLatch countDownLatch = new CountDownLatch(2);
    executor.execute(new SpdyRequest("/r1", countDownLatch));
    executor.execute(new SpdyRequest("/r2", countDownLatch));
    countDownLatch.await();
    assertEquals(0, server.takeRequest().getSequenceNumber());
    assertEquals(1, server.takeRequest().getSequenceNumber());
  }

  @Test public void gzippedResponseBody() throws Exception {
    server.enqueue(new MockResponse().addHeader("Content-Encoding: gzip")
        .setBody(gzip("ABCABCABC".getBytes(Util.UTF_8))));
    server.play();
    assertContent("ABCABCABC", client.open(server.getUrl("/r1")), Integer.MAX_VALUE);
  }

  @Test public void authenticate() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED)
        .addHeader("www-authenticate: Basic realm=\"protected area\"")
        .setBody("Please authenticate."));
    server.enqueue(new MockResponse().setBody("Successful auth!"));
    server.play();

    Authenticator.setDefault(new RecordingAuthenticator());
    connection = client.open(server.getUrl("/"));
    assertEquals("Successful auth!", readAscii(connection.getInputStream(), Integer.MAX_VALUE));

    RecordedRequest denied = server.takeRequest();
    assertContainsNoneMatching(denied.getHeaders(), "authorization: Basic .*");
    RecordedRequest accepted = server.takeRequest();
    assertEquals("GET / HTTP/1.1", accepted.getRequestLine());
    assertContains(accepted.getHeaders(),
        "authorization: Basic " + RecordingAuthenticator.BASE_64_CREDENTIALS);
  }

  @Test public void redirect() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_MOVED_TEMP)
        .addHeader("Location: /foo")
        .setBody("This page has moved!"));
    server.enqueue(new MockResponse().setBody("This is the new location!"));
    server.play();

    connection = client.open(server.getUrl("/"));
    assertContent("This is the new location!", connection, Integer.MAX_VALUE);

    RecordedRequest request1 = server.takeRequest();
    assertEquals("/", request1.getPath());
    RecordedRequest request2 = server.takeRequest();
    assertEquals("/foo", request2.getPath());
  }

  @Test public void readAfterLastByte() throws Exception {
    server.enqueue(new MockResponse().setBody("ABC"));
    server.play();

    connection = client.open(server.getUrl("/"));
    InputStream in = connection.getInputStream();
    assertEquals("ABC", readAscii(in, 3));
    assertEquals(-1, in.read());
    assertEquals(-1, in.read());
  }

  @Test(timeout = 3000) public void readResponseHeaderTimeout() throws Exception {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
    server.enqueue(new MockResponse().setBody("A"));
    server.play();

    connection = client.open(server.getUrl("/"));
    connection.setReadTimeout(1000);
    assertContent("A", connection, Integer.MAX_VALUE);
  }

  @Test public void spdyConnectionTimeout() throws Exception {
    MockResponse response = new MockResponse().setBody("A");
    response.setBodyDelayTimeMs(1000);
    server.enqueue(response);
    server.play();

    HttpURLConnection connection1 = client.open(server.getUrl("/"));
    connection1.setReadTimeout(2000);
    HttpURLConnection connection2 = client.open(server.getUrl("/"));
    connection2.setReadTimeout(200);
    connection1.connect();
    connection2.connect();
    assertContent("A", connection1, Integer.MAX_VALUE);
  }

  @Test public void responsesAreCached() throws IOException {
    client.setOkResponseCache(cache);

    server.enqueue(new MockResponse().addHeader("cache-control: max-age=60").setBody("A"));
    server.play();

    assertContent("A", client.open(server.getUrl("/")), Integer.MAX_VALUE);
    assertEquals(1, cache.getRequestCount());
    assertEquals(1, cache.getNetworkCount());
    assertEquals(0, cache.getHitCount());
    assertContent("A", client.open(server.getUrl("/")), Integer.MAX_VALUE);
    assertContent("A", client.open(server.getUrl("/")), Integer.MAX_VALUE);
    assertEquals(3, cache.getRequestCount());
    assertEquals(1, cache.getNetworkCount());
    assertEquals(2, cache.getHitCount());
  }

  @Test public void conditionalCache() throws IOException {
    client.setOkResponseCache(cache);

    server.enqueue(new MockResponse().addHeader("ETag: v1").setBody("A"));
    server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));
    server.play();

    assertContent("A", client.open(server.getUrl("/")), Integer.MAX_VALUE);
    assertEquals(1, cache.getRequestCount());
    assertEquals(1, cache.getNetworkCount());
    assertEquals(0, cache.getHitCount());
    assertContent("A", client.open(server.getUrl("/")), Integer.MAX_VALUE);
    assertEquals(2, cache.getRequestCount());
    assertEquals(2, cache.getNetworkCount());
    assertEquals(1, cache.getHitCount());
  }

  @Test public void responseCachedWithoutConsumingFullBody() throws IOException {
    client.setOkResponseCache(cache);

    server.enqueue(new MockResponse().addHeader("cache-control: max-age=60").setBody("ABCD"));
    server.enqueue(new MockResponse().addHeader("cache-control: max-age=60").setBody("EFGH"));
    server.play();

    HttpURLConnection connection1 = client.open(server.getUrl("/"));
    InputStream in1 = connection1.getInputStream();
    assertEquals("AB", readAscii(in1, 2));
    in1.close();

    HttpURLConnection connection2 = client.open(server.getUrl("/"));
    InputStream in2 = connection2.getInputStream();
    assertEquals("ABCD", readAscii(in2, Integer.MAX_VALUE));
    in2.close();
  }

  @Test public void acceptAndTransmitCookies() throws Exception {
    CookieManager cookieManager = new CookieManager();
    client.setCookieHandler(cookieManager);
    server.enqueue(
        new MockResponse().addHeader("set-cookie: c=oreo; domain=" + server.getCookieDomain())
            .setBody("A"));
    server.enqueue(new MockResponse().setBody("B"));
    server.play();

    URL url = server.getUrl("/");
    assertContent("A", client.open(url), Integer.MAX_VALUE);
    Map<String, List<String>> requestHeaders = Collections.emptyMap();
    assertEquals(Collections.singletonMap("Cookie", Arrays.asList("c=oreo")),
        cookieManager.get(url.toURI(), requestHeaders));

    assertContent("B", client.open(url), Integer.MAX_VALUE);
    RecordedRequest requestA = server.takeRequest();
    assertContainsNoneMatching(requestA.getHeaders(), "Cookie.*");
    RecordedRequest requestB = server.takeRequest();
    assertContains(requestB.getHeaders(), "cookie: c=oreo");
  }

  private <T> void assertContains(Collection<T> collection, T value) {
    assertTrue(collection.toString(), collection.contains(value));
  }

  private void assertContent(String expected, HttpURLConnection connection, int limit)
      throws IOException {
    connection.connect();
    assertEquals(expected, readAscii(connection.getInputStream(), limit));
  }

  private void assertContainsNoneMatching(List<String> headers, String pattern) {
    for (String header : headers) {
      if (header.matches(pattern)) {
        fail("Header " + header + " matches " + pattern);
      }
    }
  }

  private String readAscii(InputStream in, int count) throws IOException {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < count; i++) {
      int value = in.read();
      if (value == -1) {
        in.close();
        break;
      }
      result.append((char) value);
    }
    return result.toString();
  }

  public byte[] gzip(byte[] bytes) throws IOException {
    ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    OutputStream gzippedOut = new GZIPOutputStream(bytesOut);
    gzippedOut.write(bytes);
    gzippedOut.close();
    return bytesOut.toByteArray();
  }

  class SpdyRequest implements Runnable {
    String path;
    CountDownLatch countDownLatch;
    public SpdyRequest(String path, CountDownLatch countDownLatch) {
      this.path = path;
      this.countDownLatch = countDownLatch;
    }

    @Override public void run() {
      try {
        HttpURLConnection conn = client.open(server.getUrl(path));
        assertEquals("A", readAscii(conn.getInputStream(), 1));
        countDownLatch.countDown();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
