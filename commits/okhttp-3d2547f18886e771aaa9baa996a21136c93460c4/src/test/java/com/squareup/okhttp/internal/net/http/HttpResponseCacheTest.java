/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;
import static com.google.mockwebserver.SocketPolicy.DISCONNECT_AT_END;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.internal.net.ssl.SslContextBuilder;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ResponseCache;
import java.net.SecureCacheResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import junit.framework.TestCase;

/**
 * Android's HttpResponseCacheTest.
 */
public final class HttpResponseCacheTest extends TestCase {
    private static final HostnameVerifier NULL_HOSTNAME_VERIFIER = new HostnameVerifier() {
        @Override public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    };
    private final OkHttpClient client = new OkHttpClient();
    private MockWebServer server = new MockWebServer();
    private HttpResponseCache cache;
    private final CookieManager cookieManager = new CookieManager();

    private static final SSLContext sslContext;
    static {
        try {
            sslContext = new SslContextBuilder(InetAddress.getLocalHost().getHostName()).build();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override protected void setUp() throws Exception {
        super.setUp();
        String tmp = System.getProperty("java.io.tmpdir");
        File cacheDir = new File(tmp, "HttpCache-" + UUID.randomUUID());
        cache = new HttpResponseCache(cacheDir, Integer.MAX_VALUE);
        ResponseCache.setDefault(cache);
        CookieHandler.setDefault(cookieManager);
    }

    @Override protected void tearDown() throws Exception {
        server.shutdown();
        ResponseCache.setDefault(null);
        cache.getCache().delete();
        CookieHandler.setDefault(null);
        super.tearDown();
    }

    private HttpURLConnection openConnection(URL url) {
        return client.open(url);
    }

    /**
     * Test that response caching is consistent with the RI and the spec.
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.4
     */
    public void testResponseCachingByResponseCode() throws Exception {
        // Test each documented HTTP/1.1 code, plus the first unused value in each range.
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html

        // We can't test 100 because it's not really a response.
        // assertCached(false, 100);
        assertCached(false, 101);
        assertCached(false, 102);
        assertCached(true,  200);
        assertCached(false, 201);
        assertCached(false, 202);
        assertCached(true,  203);
        assertCached(false, 204);
        assertCached(false, 205);
        assertCached(false, 206); // we don't cache partial responses
        assertCached(false, 207);
        assertCached(true,  300);
        assertCached(true,  301);
        for (int i = 302; i <= 308; ++i) {
            assertCached(false, i);
        }
        for (int i = 400; i <= 406; ++i) {
            assertCached(false, i);
        }
        // (See test_responseCaching_407.)
        assertCached(false, 408);
        assertCached(false, 409);
        // (See test_responseCaching_410.)
        for (int i = 411; i <= 418; ++i) {
            assertCached(false, i);
        }
        for (int i = 500; i <= 506; ++i) {
            assertCached(false, i);
        }
    }

    /**
     * Response code 407 should only come from proxy servers. Android's client
     * throws if it is sent by an origin server.
     */
    public void testOriginServerSends407() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(407));
        server.play();

        URL url = server.getUrl("/");
        HttpURLConnection conn = openConnection(url);
        try {
            conn.getResponseCode();
            fail();
        } catch (IOException expected) {
        }
    }

    public void test_responseCaching_410() throws Exception {
        // the HTTP spec permits caching 410s, but the RI doesn't.
        assertCached(true, 410);
    }

    private void assertCached(boolean shouldPut, int responseCode) throws Exception {
        server = new MockWebServer();
        MockResponse response = new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS))
                .setResponseCode(responseCode)
                .setBody("ABCDE")
                .addHeader("WWW-Authenticate: challenge");
        if (responseCode == HttpURLConnection.HTTP_PROXY_AUTH) {
            response.addHeader("Proxy-Authenticate: Basic realm=\"protected area\"");
        } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            response.addHeader("WWW-Authenticate: Basic realm=\"protected area\"");
        }
        server.enqueue(response);
        server.play();

        URL url = server.getUrl("/");
        HttpURLConnection conn = openConnection(url);
        assertEquals(responseCode, conn.getResponseCode());

        // exhaust the content stream
        readAscii(conn);

        CacheResponse cached = cache.get(url.toURI(), "GET",
                Collections.<String, List<String>>emptyMap());
        if (shouldPut) {
            assertNotNull(Integer.toString(responseCode), cached);
            cached.getBody().close();
        } else {
            assertNull(Integer.toString(responseCode), cached);
        }
        server.shutdown(); // tearDown() isn't sufficient; this test starts multiple servers
    }

    /**
     * Test that we can interrogate the response when the cache is being
     * populated. http://code.google.com/p/android/issues/detail?id=7787
     */
    public void testResponseCacheCallbackApis() throws Exception {
        final String body = "ABCDE";
        final AtomicInteger cacheCount = new AtomicInteger();

        server.enqueue(new MockResponse()
                .setStatus("HTTP/1.1 200 Fantastic")
                .addHeader("fgh: ijk")
                .setBody(body));
        server.play();

        ResponseCache.setDefault(new ResponseCache() {
            @Override public CacheResponse get(URI uri, String requestMethod,
                    Map<String, List<String>> requestHeaders) throws IOException {
                return null;
            }
            @Override public CacheRequest put(URI uri, URLConnection conn) throws IOException {
                HttpURLConnection httpConnection = (HttpURLConnection) conn;
                try {
                    httpConnection.getRequestProperties();
                    fail();
                } catch (IllegalStateException expected) {
                }
                try {
                    httpConnection.addRequestProperty("K", "V");
                    fail();
                } catch (IllegalStateException expected) {
                }
                assertEquals("HTTP/1.1 200 Fantastic", httpConnection.getHeaderField(null));
                assertEquals(Arrays.asList("HTTP/1.1 200 Fantastic"),
                        httpConnection.getHeaderFields().get(null));
                assertEquals(200, httpConnection.getResponseCode());
                assertEquals("Fantastic", httpConnection.getResponseMessage());
                assertEquals(body.length(), httpConnection.getContentLength());
                assertEquals("ijk", httpConnection.getHeaderField("fgh"));
                try {
                    httpConnection.getInputStream(); // the RI doesn't forbid this, but it should
                    fail();
                } catch (IOException expected) {
                }
                cacheCount.incrementAndGet();
                return null;
            }
        });

        URL url = server.getUrl("/");
        HttpURLConnection connection = openConnection(url);
        assertEquals(body, readAscii(connection));
        assertEquals(1, cacheCount.get());
    }


    public void testResponseCachingAndInputStreamSkipWithFixedLength() throws IOException {
        testResponseCaching(TransferKind.FIXED_LENGTH);
    }

    public void testResponseCachingAndInputStreamSkipWithChunkedEncoding() throws IOException {
        testResponseCaching(TransferKind.CHUNKED);
    }

    public void testResponseCachingAndInputStreamSkipWithNoLengthHeaders() throws IOException {
        testResponseCaching(TransferKind.END_OF_STREAM);
    }

    /**
     * HttpURLConnection.getInputStream().skip(long) causes ResponseCache corruption
     * http://code.google.com/p/android/issues/detail?id=8175
     */
    private void testResponseCaching(TransferKind transferKind) throws IOException {
        MockResponse response = new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS))
                .setStatus("HTTP/1.1 200 Fantastic");
        transferKind.setBody(response, "I love puppies but hate spiders", 1);
        server.enqueue(response);
        server.play();

        // Make sure that calling skip() doesn't omit bytes from the cache.
        HttpURLConnection urlConnection = openConnection(server.getUrl("/"));
        InputStream in = urlConnection.getInputStream();
        assertEquals("I love ", readAscii(urlConnection, "I love ".length()));
        reliableSkip(in, "puppies but hate ".length());
        assertEquals("spiders", readAscii(urlConnection, "spiders".length()));
        assertEquals(-1, in.read());
        in.close();
        assertEquals(1, cache.getWriteSuccessCount());
        assertEquals(0, cache.getWriteAbortCount());

        urlConnection = openConnection(server.getUrl("/")); // cached!
        in = urlConnection.getInputStream();
        assertEquals("I love puppies but hate spiders",
                readAscii(urlConnection, "I love puppies but hate spiders".length()));
        assertEquals(200, urlConnection.getResponseCode());
        assertEquals("Fantastic", urlConnection.getResponseMessage());

        assertEquals(-1, in.read());
        in.close();
        assertEquals(1, cache.getWriteSuccessCount());
        assertEquals(0, cache.getWriteAbortCount());
        assertEquals(2, cache.getRequestCount());
        assertEquals(1, cache.getHitCount());
    }

    public void testSecureResponseCaching() throws IOException {
        server.useHttps(sslContext.getSocketFactory(), false);
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS))
                .setBody("ABC"));
        server.play();

        HttpsURLConnection connection = (HttpsURLConnection) client.open(server.getUrl("/"));
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        connection.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);
        assertEquals("ABC", readAscii(connection));

        // OpenJDK 6 fails on this line, complaining that the connection isn't open yet
        String suite = connection.getCipherSuite();
        List<Certificate> localCerts = toListOrNull(connection.getLocalCertificates());
        List<Certificate> serverCerts = toListOrNull(connection.getServerCertificates());
        Principal peerPrincipal = connection.getPeerPrincipal();
        Principal localPrincipal = connection.getLocalPrincipal();

        connection = (HttpsURLConnection) client.open(server.getUrl("/")); // cached!
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        connection.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);
        assertEquals("ABC", readAscii(connection));

        assertEquals(2, cache.getRequestCount());
        assertEquals(1, cache.getNetworkCount());
        assertEquals(1, cache.getHitCount());

        assertEquals(suite, connection.getCipherSuite());
        assertEquals(localCerts, toListOrNull(connection.getLocalCertificates()));
        assertEquals(serverCerts, toListOrNull(connection.getServerCertificates()));
        assertEquals(peerPrincipal, connection.getPeerPrincipal());
        assertEquals(localPrincipal, connection.getLocalPrincipal());
    }

    public void testCacheReturnsInsecureResponseForSecureRequest() throws IOException {
        server.useHttps(sslContext.getSocketFactory(), false);
        server.enqueue(new MockResponse().setBody("ABC"));
        server.enqueue(new MockResponse().setBody("DEF"));
        server.play();

        ResponseCache.setDefault(new InsecureResponseCache());

        HttpsURLConnection connection1 = (HttpsURLConnection) client.open(server.getUrl("/"));
        connection1.setSSLSocketFactory(sslContext.getSocketFactory());
        connection1.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);
        assertEquals("ABC", readAscii(connection1));

        // Not cached!
        HttpsURLConnection connection2 = (HttpsURLConnection) client.open(server.getUrl("/"));
        connection2.setSSLSocketFactory(sslContext.getSocketFactory());
        connection2.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);
        assertEquals("DEF", readAscii(connection2));
    }

    public void testResponseCachingAndRedirects() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS))
                .setResponseCode(HttpURLConnection.HTTP_MOVED_PERM)
                .addHeader("Location: /foo"));
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS))
                .setBody("ABC"));
        server.enqueue(new MockResponse().setBody("DEF"));
        server.play();

        HttpURLConnection connection = openConnection(server.getUrl("/"));
        assertEquals("ABC", readAscii(connection));

        connection = openConnection(server.getUrl("/")); // cached!
        assertEquals("ABC", readAscii(connection));

        assertEquals(4, cache.getRequestCount()); // 2 requests + 2 redirects
        assertEquals(2, cache.getNetworkCount());
        assertEquals(2, cache.getHitCount());
    }

    public void testRedirectToCachedResult() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .setBody("ABC"));
        server.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_MOVED_PERM)
                .addHeader("Location: /foo"));
        server.enqueue(new MockResponse().setBody("DEF"));
        server.play();

        assertEquals("ABC", readAscii(openConnection(server.getUrl("/foo"))));
        RecordedRequest request1 = server.takeRequest();
        assertEquals("GET /foo HTTP/1.1", request1.getRequestLine());
        assertEquals(0, request1.getSequenceNumber());

        assertEquals("ABC", readAscii(openConnection(server.getUrl("/bar"))));
        RecordedRequest request2 = server.takeRequest();
        assertEquals("GET /bar HTTP/1.1", request2.getRequestLine());
        assertEquals(1, request2.getSequenceNumber());

        // an unrelated request should reuse the pooled connection
        assertEquals("DEF", readAscii(openConnection(server.getUrl("/baz"))));
        RecordedRequest request3 = server.takeRequest();
        assertEquals("GET /baz HTTP/1.1", request3.getRequestLine());
        assertEquals(2, request3.getSequenceNumber());
    }

    public void testSecureResponseCachingAndRedirects() throws IOException {
        server.useHttps(sslContext.getSocketFactory(), false);
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS))
                .setResponseCode(HttpURLConnection.HTTP_MOVED_PERM)
                .addHeader("Location: /foo"));
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS))
                .setBody("ABC"));
        server.enqueue(new MockResponse().setBody("DEF"));
        server.play();

        HttpsURLConnection connection1 = (HttpsURLConnection) client.open(server.getUrl("/"));
        connection1.setSSLSocketFactory(sslContext.getSocketFactory());
        connection1.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);
        assertEquals("ABC", readAscii(connection1));

        // Cached!
        HttpsURLConnection connection2 = (HttpsURLConnection) client.open(server.getUrl("/"));
        connection1.setSSLSocketFactory(sslContext.getSocketFactory());
        connection1.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);
        assertEquals("ABC", readAscii(connection2));

        assertEquals(4, cache.getRequestCount()); // 2 direct + 2 redirect = 4
        assertEquals(2, cache.getHitCount());
    }

    public void testResponseCacheRequestHeaders() throws IOException, URISyntaxException {
        server.enqueue(new MockResponse().setBody("ABC"));
        server.play();

        final AtomicReference<Map<String, List<String>>> requestHeadersRef
                = new AtomicReference<Map<String, List<String>>>();
        ResponseCache.setDefault(new ResponseCache() {
            @Override public CacheResponse get(URI uri, String requestMethod,
                    Map<String, List<String>> requestHeaders) throws IOException {
                requestHeadersRef.set(requestHeaders);
                return null;
            }
            @Override public CacheRequest put(URI uri, URLConnection conn) throws IOException {
                return null;
            }
        });

        URL url = server.getUrl("/");
        URLConnection urlConnection = openConnection(url);
        urlConnection.addRequestProperty("A", "android");
        readAscii(urlConnection);
        assertEquals(Arrays.asList("android"), requestHeadersRef.get().get("A"));
    }


    public void testServerDisconnectsPrematurelyWithContentLengthHeader() throws IOException {
        testServerPrematureDisconnect(TransferKind.FIXED_LENGTH);
    }

    public void testServerDisconnectsPrematurelyWithChunkedEncoding() throws IOException {
        testServerPrematureDisconnect(TransferKind.CHUNKED);
    }

    public void testServerDisconnectsPrematurelyWithNoLengthHeaders() throws IOException {
        /*
         * Intentionally empty. This case doesn't make sense because there's no
         * such thing as a premature disconnect when the disconnect itself
         * indicates the end of the data stream.
         */
    }

    private void testServerPrematureDisconnect(TransferKind transferKind) throws IOException {
        MockResponse response = new MockResponse();
        transferKind.setBody(response, "ABCDE\nFGHIJKLMNOPQRSTUVWXYZ", 16);
        server.enqueue(truncateViolently(response, 16));
        server.enqueue(new MockResponse().setBody("Request #2"));
        server.play();

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                openConnection(server.getUrl("/")).getInputStream()));
        assertEquals("ABCDE", reader.readLine());
        try {
            reader.readLine();
            fail("This implementation silently ignored a truncated HTTP body.");
        } catch (IOException expected) {
        } finally {
            reader.close();
        }

        assertEquals(1, cache.getWriteAbortCount());
        assertEquals(0, cache.getWriteSuccessCount());
        URLConnection connection = openConnection(server.getUrl("/"));
        assertEquals("Request #2", readAscii(connection));
        assertEquals(1, cache.getWriteAbortCount());
        assertEquals(1, cache.getWriteSuccessCount());
    }

    public void testClientPrematureDisconnectWithContentLengthHeader() throws IOException {
        testClientPrematureDisconnect(TransferKind.FIXED_LENGTH);
    }

    public void testClientPrematureDisconnectWithChunkedEncoding() throws IOException {
        testClientPrematureDisconnect(TransferKind.CHUNKED);
    }

    public void testClientPrematureDisconnectWithNoLengthHeaders() throws IOException {
        testClientPrematureDisconnect(TransferKind.END_OF_STREAM);
    }

    private void testClientPrematureDisconnect(TransferKind transferKind) throws IOException {
        // Setting a low transfer speed ensures that stream discarding will time out.
        MockResponse response = new MockResponse().setBytesPerSecond(6);
        transferKind.setBody(response, "ABCDE\nFGHIJKLMNOPQRSTUVWXYZ", 1024);
        server.enqueue(response);
        server.enqueue(new MockResponse().setBody("Request #2"));
        server.play();

        URLConnection connection = openConnection(server.getUrl("/"));
        InputStream in = connection.getInputStream();
        assertEquals("ABCDE", readAscii(connection, 5));
        in.close();
        try {
            in.read();
            fail("Expected an IOException because the stream is closed.");
        } catch (IOException expected) {
        }

        assertEquals(1, cache.getWriteAbortCount());
        assertEquals(0, cache.getWriteSuccessCount());
        connection = openConnection(server.getUrl("/"));
        assertEquals("Request #2", readAscii(connection));
        assertEquals(1, cache.getWriteAbortCount());
        assertEquals(1, cache.getWriteSuccessCount());
    }

    public void testDefaultExpirationDateFullyCachedForLessThan24Hours() throws Exception {
        //      last modified: 105 seconds ago
        //             served:   5 seconds ago
        //   default lifetime: (105 - 5) / 10 = 10 seconds
        //            expires:  10 seconds from served date = 5 seconds from now
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-105, TimeUnit.SECONDS))
                .addHeader("Date: " + formatDate(-5, TimeUnit.SECONDS))
                .setBody("A"));
        server.play();

        URL url = server.getUrl("/");
        assertEquals("A", readAscii(openConnection(url)));
        URLConnection connection = openConnection(url);
        assertEquals("A", readAscii(connection));
        assertNull(connection.getHeaderField("Warning"));
    }

    public void testDefaultExpirationDateConditionallyCached() throws Exception {
        //      last modified: 115 seconds ago
        //             served:  15 seconds ago
        //   default lifetime: (115 - 15) / 10 = 10 seconds
        //            expires:  10 seconds from served date = 5 seconds ago
        String lastModifiedDate = formatDate(-115, TimeUnit.SECONDS);
        RecordedRequest conditionalRequest = assertConditionallyCached(new MockResponse()
                .addHeader("Last-Modified: " + lastModifiedDate)
                .addHeader("Date: " + formatDate(-15, TimeUnit.SECONDS)));
        List<String> headers = conditionalRequest.getHeaders();
        assertTrue(headers.contains("If-Modified-Since: " + lastModifiedDate));
    }

    public void testDefaultExpirationDateFullyCachedForMoreThan24Hours() throws Exception {
        //      last modified: 105 days ago
        //             served:   5 days ago
        //   default lifetime: (105 - 5) / 10 = 10 days
        //            expires:  10 days from served date = 5 days from now
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-105, TimeUnit.DAYS))
                .addHeader("Date: " + formatDate(-5, TimeUnit.DAYS))
                .setBody("A"));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        URLConnection connection = openConnection(server.getUrl("/"));
        assertEquals("A", readAscii(connection));
        assertEquals("113 HttpURLConnection \"Heuristic expiration\"",
                connection.getHeaderField("Warning"));
    }

    public void testNoDefaultExpirationForUrlsWithQueryString() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-105, TimeUnit.SECONDS))
                .addHeader("Date: " + formatDate(-5, TimeUnit.SECONDS))
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/?foo=bar");
        assertEquals("A", readAscii(openConnection(url)));
        assertEquals("B", readAscii(openConnection(url)));
    }

    public void testExpirationDateInThePastWithLastModifiedHeader() throws Exception {
        String lastModifiedDate = formatDate(-2, TimeUnit.HOURS);
        RecordedRequest conditionalRequest = assertConditionallyCached(new MockResponse()
                .addHeader("Last-Modified: " + lastModifiedDate)
                .addHeader("Expires: " + formatDate(-1, TimeUnit.HOURS)));
        List<String> headers = conditionalRequest.getHeaders();
        assertTrue(headers.contains("If-Modified-Since: " + lastModifiedDate));
    }

    public void testExpirationDateInThePastWithNoLastModifiedHeader() throws Exception {
        assertNotCached(new MockResponse()
                .addHeader("Expires: " + formatDate(-1, TimeUnit.HOURS)));
    }

    public void testExpirationDateInTheFuture() throws Exception {
        assertFullyCached(new MockResponse()
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS)));
    }

    public void testMaxAgePreferredWithMaxAgeAndExpires() throws Exception {
        assertFullyCached(new MockResponse()
                .addHeader("Date: " + formatDate(0, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Cache-Control: max-age=60"));
    }

    public void testMaxAgeInThePastWithDateAndLastModifiedHeaders() throws Exception {
        String lastModifiedDate = formatDate(-2, TimeUnit.HOURS);
        RecordedRequest conditionalRequest = assertConditionallyCached(new MockResponse()
                .addHeader("Date: " + formatDate(-120, TimeUnit.SECONDS))
                .addHeader("Last-Modified: " + lastModifiedDate)
                .addHeader("Cache-Control: max-age=60"));
        List<String> headers = conditionalRequest.getHeaders();
        assertTrue(headers.contains("If-Modified-Since: " + lastModifiedDate));
    }

    public void testMaxAgeInThePastWithDateHeaderButNoLastModifiedHeader() throws Exception {
        /*
         * Chrome interprets max-age relative to the local clock. Both our cache
         * and Firefox both use the earlier of the local and server's clock.
         */
        assertNotCached(new MockResponse()
                .addHeader("Date: " + formatDate(-120, TimeUnit.SECONDS))
                .addHeader("Cache-Control: max-age=60"));
    }

    public void testMaxAgeInTheFutureWithDateHeader() throws Exception {
        assertFullyCached(new MockResponse()
                .addHeader("Date: " + formatDate(0, TimeUnit.HOURS))
                .addHeader("Cache-Control: max-age=60"));
    }

    public void testMaxAgeInTheFutureWithNoDateHeader() throws Exception {
        assertFullyCached(new MockResponse()
                .addHeader("Cache-Control: max-age=60"));
    }

    public void testMaxAgeWithLastModifiedButNoServedDate() throws Exception {
        assertFullyCached(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-120, TimeUnit.SECONDS))
                .addHeader("Cache-Control: max-age=60"));
    }

    public void testMaxAgeInTheFutureWithDateAndLastModifiedHeaders() throws Exception {
        assertFullyCached(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-120, TimeUnit.SECONDS))
                .addHeader("Date: " + formatDate(0, TimeUnit.SECONDS))
                .addHeader("Cache-Control: max-age=60"));
    }

    public void testMaxAgePreferredOverLowerSharedMaxAge() throws Exception {
        assertFullyCached(new MockResponse()
                .addHeader("Date: " + formatDate(-2, TimeUnit.MINUTES))
                .addHeader("Cache-Control: s-maxage=60")
                .addHeader("Cache-Control: max-age=180"));
    }

    public void testMaxAgePreferredOverHigherMaxAge() throws Exception {
        assertNotCached(new MockResponse()
                .addHeader("Date: " + formatDate(-2, TimeUnit.MINUTES))
                .addHeader("Cache-Control: s-maxage=180")
                .addHeader("Cache-Control: max-age=60"));
    }

    public void testRequestMethodOptionsIsNotCached() throws Exception {
        testRequestMethod("OPTIONS", false);
    }

    public void testRequestMethodGetIsCached() throws Exception {
        testRequestMethod("GET", true);
    }

    public void testRequestMethodHeadIsNotCached() throws Exception {
        // We could support this but choose not to for implementation simplicity
        testRequestMethod("HEAD", false);
    }

    public void testRequestMethodPostIsNotCached() throws Exception {
        // We could support this but choose not to for implementation simplicity
        testRequestMethod("POST", false);
    }

    public void testRequestMethodPutIsNotCached() throws Exception {
        testRequestMethod("PUT", false);
    }

    public void testRequestMethodDeleteIsNotCached() throws Exception {
        testRequestMethod("DELETE", false);
    }

    public void testRequestMethodTraceIsNotCached() throws Exception {
        testRequestMethod("TRACE", false);
    }

    private void testRequestMethod(String requestMethod, boolean expectCached) throws Exception {
        /*
         * 1. seed the cache (potentially)
         * 2. expect a cache hit or miss
         */
        server.enqueue(new MockResponse()
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS))
                .addHeader("X-Response-ID: 1"));
        server.enqueue(new MockResponse()
                .addHeader("X-Response-ID: 2"));
        server.play();

        URL url = server.getUrl("/");

        HttpURLConnection request1 = (HttpURLConnection) openConnection(url);
        request1.setRequestMethod(requestMethod);
        addRequestBodyIfNecessary(requestMethod, request1);
        assertEquals("1", request1.getHeaderField("X-Response-ID"));

        URLConnection request2 = openConnection(url);
        if (expectCached) {
            assertEquals("1", request1.getHeaderField("X-Response-ID"));
        } else {
            assertEquals("2", request2.getHeaderField("X-Response-ID"));
        }
    }

    public void testPostInvalidatesCache() throws Exception {
        testMethodInvalidates("POST");
    }

    public void testPutInvalidatesCache() throws Exception {
        testMethodInvalidates("PUT");
    }

    public void testDeleteMethodInvalidatesCache() throws Exception {
        testMethodInvalidates("DELETE");
    }

    private void testMethodInvalidates(String requestMethod) throws Exception {
        /*
         * 1. seed the cache
         * 2. invalidate it
         * 3. expect a cache miss
         */
        server.enqueue(new MockResponse().setBody("A")
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS)));
        server.enqueue(new MockResponse().setBody("B"));
        server.enqueue(new MockResponse().setBody("C"));
        server.play();

        URL url = server.getUrl("/");

        assertEquals("A", readAscii(openConnection(url)));

        HttpURLConnection invalidate = openConnection(url);
        invalidate.setRequestMethod(requestMethod);
        addRequestBodyIfNecessary(requestMethod, invalidate);
        assertEquals("B", readAscii(invalidate));

        assertEquals("C", readAscii(openConnection(url)));
    }

    public void testEtag() throws Exception {
        RecordedRequest conditionalRequest = assertConditionallyCached(new MockResponse()
                .addHeader("ETag: v1"));
        assertTrue(conditionalRequest.getHeaders().contains("If-None-Match: v1"));
    }

    public void testEtagAndExpirationDateInThePast() throws Exception {
        String lastModifiedDate = formatDate(-2, TimeUnit.HOURS);
        RecordedRequest conditionalRequest = assertConditionallyCached(new MockResponse()
                .addHeader("ETag: v1")
                .addHeader("Last-Modified: " + lastModifiedDate)
                .addHeader("Expires: " + formatDate(-1, TimeUnit.HOURS)));
        List<String> headers = conditionalRequest.getHeaders();
        assertTrue(headers.contains("If-None-Match: v1"));
        assertTrue(headers.contains("If-Modified-Since: " + lastModifiedDate));
    }

    public void testEtagAndExpirationDateInTheFuture() throws Exception {
        assertFullyCached(new MockResponse()
                .addHeader("ETag: v1")
                .addHeader("Last-Modified: " + formatDate(-2, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS)));
    }

    public void testCacheControlNoCache() throws Exception {
        assertNotCached(new MockResponse().addHeader("Cache-Control: no-cache"));
    }

    public void testCacheControlNoCacheAndExpirationDateInTheFuture() throws Exception {
        String lastModifiedDate = formatDate(-2, TimeUnit.HOURS);
        RecordedRequest conditionalRequest = assertConditionallyCached(new MockResponse()
                .addHeader("Last-Modified: " + lastModifiedDate)
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS))
                .addHeader("Cache-Control: no-cache"));
        List<String> headers = conditionalRequest.getHeaders();
        assertTrue(headers.contains("If-Modified-Since: " + lastModifiedDate));
    }

    public void testPragmaNoCache() throws Exception {
        assertNotCached(new MockResponse().addHeader("Pragma: no-cache"));
    }

    public void testPragmaNoCacheAndExpirationDateInTheFuture() throws Exception {
        String lastModifiedDate = formatDate(-2, TimeUnit.HOURS);
        RecordedRequest conditionalRequest = assertConditionallyCached(new MockResponse()
                .addHeader("Last-Modified: " + lastModifiedDate)
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS))
                .addHeader("Pragma: no-cache"));
        List<String> headers = conditionalRequest.getHeaders();
        assertTrue(headers.contains("If-Modified-Since: " + lastModifiedDate));
    }

    public void testCacheControlNoStore() throws Exception {
        assertNotCached(new MockResponse().addHeader("Cache-Control: no-store"));
    }

    public void testCacheControlNoStoreAndExpirationDateInTheFuture() throws Exception {
        assertNotCached(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-2, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS))
                .addHeader("Cache-Control: no-store"));
    }

    public void testPartialRangeResponsesDoNotCorruptCache() throws Exception {
        /*
         * 1. request a range
         * 2. request a full document, expecting a cache miss
         */
        server.enqueue(new MockResponse().setBody("AA")
                .setResponseCode(HttpURLConnection.HTTP_PARTIAL)
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS))
                .addHeader("Content-Range: bytes 1000-1001/2000"));
        server.enqueue(new MockResponse().setBody("BB"));
        server.play();

        URL url = server.getUrl("/");

        URLConnection range = openConnection(url);
        range.addRequestProperty("Range", "bytes=1000-1001");
        assertEquals("AA", readAscii(range));

        assertEquals("BB", readAscii(openConnection(url)));
    }

    public void testServerReturnsDocumentOlderThanCache() throws Exception {
        server.enqueue(new MockResponse().setBody("A")
                .addHeader("Last-Modified: " + formatDate(-2, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(-1, TimeUnit.HOURS)));
        server.enqueue(new MockResponse().setBody("B")
                .addHeader("Last-Modified: " + formatDate(-4, TimeUnit.HOURS)));
        server.play();

        URL url = server.getUrl("/");

        assertEquals("A", readAscii(openConnection(url)));
        assertEquals("A", readAscii(openConnection(url)));
    }

    public void testNonIdentityEncodingAndConditionalCache() throws Exception {
        assertNonIdentityEncodingCached(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-2, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(-1, TimeUnit.HOURS)));
    }

    public void testNonIdentityEncodingAndFullCache() throws Exception {
        assertNonIdentityEncodingCached(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-2, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS)));
    }

    private void assertNonIdentityEncodingCached(MockResponse response) throws Exception {
        server.enqueue(response
                .setBody(gzip("ABCABCABC".getBytes("UTF-8")))
                .addHeader("Content-Encoding: gzip"));
        server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));

        server.play();
        assertEquals("ABCABCABC", readAscii(openConnection(server.getUrl("/"))));
        assertEquals("ABCABCABC", readAscii(openConnection(server.getUrl("/"))));
    }

    public void testExpiresDateBeforeModifiedDate() throws Exception {
        assertConditionallyCached(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Expires: " + formatDate(-2, TimeUnit.HOURS)));
    }

    public void testRequestMaxAge() throws IOException {
        server.enqueue(new MockResponse().setBody("A")
                .addHeader("Last-Modified: " + formatDate(-2, TimeUnit.HOURS))
                .addHeader("Date: " + formatDate(-1, TimeUnit.MINUTES))
                .addHeader("Expires: " + formatDate(1, TimeUnit.HOURS)));
        server.enqueue(new MockResponse().setBody("B"));

        server.play();
        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));

        URLConnection connection = openConnection(server.getUrl("/"));
        connection.addRequestProperty("Cache-Control", "max-age=30");
        assertEquals("B", readAscii(connection));
    }

    public void testRequestMinFresh() throws IOException {
        server.enqueue(new MockResponse().setBody("A")
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Date: " + formatDate(0, TimeUnit.MINUTES)));
        server.enqueue(new MockResponse().setBody("B"));

        server.play();
        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));

        URLConnection connection = openConnection(server.getUrl("/"));
        connection.addRequestProperty("Cache-Control", "min-fresh=120");
        assertEquals("B", readAscii(connection));
    }

    public void testRequestMaxStale() throws IOException {
        server.enqueue(new MockResponse().setBody("A")
                .addHeader("Cache-Control: max-age=120")
                .addHeader("Date: " + formatDate(-4, TimeUnit.MINUTES)));
        server.enqueue(new MockResponse().setBody("B"));

        server.play();
        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));

        URLConnection connection = openConnection(server.getUrl("/"));
        connection.addRequestProperty("Cache-Control", "max-stale=180");
        assertEquals("A", readAscii(connection));
        assertEquals("110 HttpURLConnection \"Response is stale\"",
                connection.getHeaderField("Warning"));
    }

    public void testRequestMaxStaleNotHonoredWithMustRevalidate() throws IOException {
        server.enqueue(new MockResponse().setBody("A")
                .addHeader("Cache-Control: max-age=120, must-revalidate")
                .addHeader("Date: " + formatDate(-4, TimeUnit.MINUTES)));
        server.enqueue(new MockResponse().setBody("B"));

        server.play();
        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));

        URLConnection connection = openConnection(server.getUrl("/"));
        connection.addRequestProperty("Cache-Control", "max-stale=180");
        assertEquals("B", readAscii(connection));
    }

    public void testRequestOnlyIfCachedWithNoResponseCached() throws IOException {
        // (no responses enqueued)
        server.play();

        HttpURLConnection connection = openConnection(server.getUrl("/"));
        connection.addRequestProperty("Cache-Control", "only-if-cached");
        assertGatewayTimeout(connection);
    }

    public void testRequestOnlyIfCachedWithFullResponseCached() throws IOException {
        server.enqueue(new MockResponse().setBody("A")
                .addHeader("Cache-Control: max-age=30")
                .addHeader("Date: " + formatDate(0, TimeUnit.MINUTES)));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        URLConnection connection = openConnection(server.getUrl("/"));
        connection.addRequestProperty("Cache-Control", "only-if-cached");
        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
    }

    public void testRequestOnlyIfCachedWithConditionalResponseCached() throws IOException {
        server.enqueue(new MockResponse().setBody("A")
                .addHeader("Cache-Control: max-age=30")
                .addHeader("Date: " + formatDate(-1, TimeUnit.MINUTES)));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        HttpURLConnection connection = openConnection(server.getUrl("/"));
        connection.addRequestProperty("Cache-Control", "only-if-cached");
        assertGatewayTimeout(connection);
    }

    public void testRequestOnlyIfCachedWithUnhelpfulResponseCached() throws IOException {
        server.enqueue(new MockResponse().setBody("A"));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        HttpURLConnection connection = openConnection(server.getUrl("/"));
        connection.addRequestProperty("Cache-Control", "only-if-cached");
        assertGatewayTimeout(connection);
    }

    public void testRequestCacheControlNoCache() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-120, TimeUnit.SECONDS))
                .addHeader("Date: " + formatDate(0, TimeUnit.SECONDS))
                .addHeader("Cache-Control: max-age=60")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        assertEquals("A", readAscii(openConnection(url)));
        URLConnection connection = openConnection(url);
        connection.setRequestProperty("Cache-Control", "no-cache");
        assertEquals("B", readAscii(connection));
    }

    public void testRequestPragmaNoCache() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-120, TimeUnit.SECONDS))
                .addHeader("Date: " + formatDate(0, TimeUnit.SECONDS))
                .addHeader("Cache-Control: max-age=60")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        assertEquals("A", readAscii(openConnection(url)));
        URLConnection connection = openConnection(url);
        connection.setRequestProperty("Pragma", "no-cache");
        assertEquals("B", readAscii(connection));
    }

    public void testClientSuppliedIfModifiedSinceWithCachedResult() throws Exception {
        MockResponse response = new MockResponse()
                .addHeader("ETag: v3")
                .addHeader("Cache-Control: max-age=0");
        String ifModifiedSinceDate = formatDate(-24, TimeUnit.HOURS);
        RecordedRequest request = assertClientSuppliedCondition(
                response, "If-Modified-Since", ifModifiedSinceDate);
        List<String> headers = request.getHeaders();
        assertTrue(headers.contains("If-Modified-Since: " + ifModifiedSinceDate));
        assertFalse(headers.contains("If-None-Match: v3"));
    }

    public void testClientSuppliedIfNoneMatchSinceWithCachedResult() throws Exception {
        String lastModifiedDate = formatDate(-3, TimeUnit.MINUTES);
        MockResponse response = new MockResponse()
                .addHeader("Last-Modified: " + lastModifiedDate)
                .addHeader("Date: " + formatDate(-2, TimeUnit.MINUTES))
                .addHeader("Cache-Control: max-age=0");
        RecordedRequest request = assertClientSuppliedCondition(
                response, "If-None-Match", "v1");
        List<String> headers = request.getHeaders();
        assertTrue(headers.contains("If-None-Match: v1"));
        assertFalse(headers.contains("If-Modified-Since: " + lastModifiedDate));
    }

    private RecordedRequest assertClientSuppliedCondition(MockResponse seed, String conditionName,
            String conditionValue) throws Exception {
        server.enqueue(seed.setBody("A"));
        server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));
        server.play();

        URL url = server.getUrl("/");
        assertEquals("A", readAscii(openConnection(url)));

        HttpURLConnection connection = openConnection(url);
        connection.addRequestProperty(conditionName, conditionValue);
        assertEquals(HttpURLConnection.HTTP_NOT_MODIFIED, connection.getResponseCode());
        assertEquals("", readAscii(connection));

        server.takeRequest(); // seed
        return server.takeRequest();
    }

    public void testSetIfModifiedSince() throws Exception {
        Date since = new Date();
        server.enqueue(new MockResponse().setBody("A"));
        server.play();

        URL url = server.getUrl("/");
        URLConnection connection = openConnection(url);
        connection.setIfModifiedSince(since.getTime());
        assertEquals("A", readAscii(connection));
        RecordedRequest request = server.takeRequest();
        assertTrue(request.getHeaders().contains("If-Modified-Since: " + formatDate(since)));
    }

    public void testClientSuppliedConditionWithoutCachedResult() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));
        server.play();

        HttpURLConnection connection = openConnection(server.getUrl("/"));
        String clientIfModifiedSince = formatDate(-24, TimeUnit.HOURS);
        connection.addRequestProperty("If-Modified-Since", clientIfModifiedSince);
        assertEquals(HttpURLConnection.HTTP_NOT_MODIFIED, connection.getResponseCode());
        assertEquals("", readAscii(connection));
    }

    public void testAuthorizationRequestHeaderPreventsCaching() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-2, TimeUnit.MINUTES))
                .addHeader("Cache-Control: max-age=60")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        URLConnection connection = openConnection(url);
        connection.addRequestProperty("Authorization", "password");
        assertEquals("A", readAscii(connection));
        assertEquals("B", readAscii(openConnection(url)));
    }

    public void testAuthorizationResponseCachedWithSMaxAge() throws Exception {
        assertAuthorizationRequestFullyCached(new MockResponse()
                .addHeader("Cache-Control: s-maxage=60"));
    }

    public void testAuthorizationResponseCachedWithPublic() throws Exception {
        assertAuthorizationRequestFullyCached(new MockResponse()
                .addHeader("Cache-Control: public"));
    }

    public void testAuthorizationResponseCachedWithMustRevalidate() throws Exception {
        assertAuthorizationRequestFullyCached(new MockResponse()
                .addHeader("Cache-Control: must-revalidate"));
    }

    public void assertAuthorizationRequestFullyCached(MockResponse response) throws Exception {
        server.enqueue(response
                .addHeader("Cache-Control: max-age=60")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        URLConnection connection = openConnection(url);
        connection.addRequestProperty("Authorization", "password");
        assertEquals("A", readAscii(connection));
        assertEquals("A", readAscii(openConnection(url)));
    }

    public void testContentLocationDoesNotPopulateCache() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Content-Location: /bar")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/foo"))));
        assertEquals("B", readAscii(openConnection(server.getUrl("/bar"))));
    }

    public void testUseCachesFalseDoesNotWriteToCache() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .setBody("A").setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URLConnection connection = openConnection(server.getUrl("/"));
        connection.setUseCaches(false);
        assertEquals("A", readAscii(connection));
        assertEquals("B", readAscii(openConnection(server.getUrl("/"))));
    }

    public void testUseCachesFalseDoesNotReadFromCache() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .setBody("A").setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        URLConnection connection = openConnection(server.getUrl("/"));
        connection.setUseCaches(false);
        assertEquals("B", readAscii(connection));
    }

    public void testDefaultUseCachesSetsInitialValueOnly() throws Exception {
        URL url = new URL("http://localhost/");
        URLConnection c1 = openConnection(url);
        URLConnection c2 = openConnection(url);
        assertTrue(c1.getDefaultUseCaches());
        c1.setDefaultUseCaches(false);
        try {
            assertTrue(c1.getUseCaches());
            assertTrue(c2.getUseCaches());
            URLConnection c3 = openConnection(url);
            assertFalse(c3.getUseCaches());
        } finally {
            c1.setDefaultUseCaches(true);
        }
    }

    public void testConnectionIsReturnedToPoolAfterConditionalSuccess() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Cache-Control: max-age=0")
                .setBody("A"));
        server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/a"))));
        assertEquals("A", readAscii(openConnection(server.getUrl("/a"))));
        assertEquals("B", readAscii(openConnection(server.getUrl("/b"))));

        assertEquals(0, server.takeRequest().getSequenceNumber());
        assertEquals(1, server.takeRequest().getSequenceNumber());
        assertEquals(2, server.takeRequest().getSequenceNumber());
    }

    public void testStatisticsConditionalCacheMiss() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Cache-Control: max-age=0")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.enqueue(new MockResponse().setBody("C"));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        assertEquals(1, cache.getRequestCount());
        assertEquals(1, cache.getNetworkCount());
        assertEquals(0, cache.getHitCount());
        assertEquals("B", readAscii(openConnection(server.getUrl("/"))));
        assertEquals("C", readAscii(openConnection(server.getUrl("/"))));
        assertEquals(3, cache.getRequestCount());
        assertEquals(3, cache.getNetworkCount());
        assertEquals(0, cache.getHitCount());
    }

    public void testStatisticsConditionalCacheHit() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Cache-Control: max-age=0")
                .setBody("A"));
        server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));
        server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        assertEquals(1, cache.getRequestCount());
        assertEquals(1, cache.getNetworkCount());
        assertEquals(0, cache.getHitCount());
        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        assertEquals(3, cache.getRequestCount());
        assertEquals(3, cache.getNetworkCount());
        assertEquals(2, cache.getHitCount());
    }

    public void testStatisticsFullCacheHit() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .setBody("A"));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        assertEquals(1, cache.getRequestCount());
        assertEquals(1, cache.getNetworkCount());
        assertEquals(0, cache.getHitCount());
        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        assertEquals(3, cache.getRequestCount());
        assertEquals(1, cache.getNetworkCount());
        assertEquals(2, cache.getHitCount());
    }

    public void testVaryMatchesChangedRequestHeaderField() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Vary: Accept-Language")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        HttpURLConnection frConnection = openConnection(url);
        frConnection.addRequestProperty("Accept-Language", "fr-CA");
        assertEquals("A", readAscii(frConnection));

        HttpURLConnection enConnection = openConnection(url);
        enConnection.addRequestProperty("Accept-Language", "en-US");
        assertEquals("B", readAscii(enConnection));
    }

    public void testVaryMatchesUnchangedRequestHeaderField() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Vary: Accept-Language")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        URLConnection connection1 = openConnection(url);
        connection1.addRequestProperty("Accept-Language", "fr-CA");
        assertEquals("A", readAscii(connection1));
        URLConnection connection2 = openConnection(url);
        connection2.addRequestProperty("Accept-Language", "fr-CA");
        assertEquals("A", readAscii(connection2));
    }

    public void testVaryMatchesAbsentRequestHeaderField() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Vary: Foo")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
    }

    public void testVaryMatchesAddedRequestHeaderField() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Vary: Foo")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        URLConnection fooConnection = openConnection(server.getUrl("/"));
        fooConnection.addRequestProperty("Foo", "bar");
        assertEquals("B", readAscii(fooConnection));
    }

    public void testVaryMatchesRemovedRequestHeaderField() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Vary: Foo")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URLConnection fooConnection = openConnection(server.getUrl("/"));
        fooConnection.addRequestProperty("Foo", "bar");
        assertEquals("A", readAscii(fooConnection));
        assertEquals("B", readAscii(openConnection(server.getUrl("/"))));
    }

    public void testVaryFieldsAreCaseInsensitive() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Vary: ACCEPT-LANGUAGE")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        URLConnection connection1 = openConnection(url);
        connection1.addRequestProperty("Accept-Language", "fr-CA");
        assertEquals("A", readAscii(connection1));
        URLConnection connection2 = openConnection(url);
        connection2.addRequestProperty("accept-language", "fr-CA");
        assertEquals("A", readAscii(connection2));
    }

    public void testVaryMultipleFieldsWithMatch() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Vary: Accept-Language, Accept-Charset")
                .addHeader("Vary: Accept-Encoding")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        URLConnection connection1 = openConnection(url);
        connection1.addRequestProperty("Accept-Language", "fr-CA");
        connection1.addRequestProperty("Accept-Charset", "UTF-8");
        connection1.addRequestProperty("Accept-Encoding", "identity");
        assertEquals("A", readAscii(connection1));
        URLConnection connection2 = openConnection(url);
        connection2.addRequestProperty("Accept-Language", "fr-CA");
        connection2.addRequestProperty("Accept-Charset", "UTF-8");
        connection2.addRequestProperty("Accept-Encoding", "identity");
        assertEquals("A", readAscii(connection2));
    }

    public void testVaryMultipleFieldsWithNoMatch() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Vary: Accept-Language, Accept-Charset")
                .addHeader("Vary: Accept-Encoding")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        URLConnection frConnection = openConnection(url);
        frConnection.addRequestProperty("Accept-Language", "fr-CA");
        frConnection.addRequestProperty("Accept-Charset", "UTF-8");
        frConnection.addRequestProperty("Accept-Encoding", "identity");
        assertEquals("A", readAscii(frConnection));
        URLConnection enConnection = openConnection(url);
        enConnection.addRequestProperty("Accept-Language", "en-CA");
        enConnection.addRequestProperty("Accept-Charset", "UTF-8");
        enConnection.addRequestProperty("Accept-Encoding", "identity");
        assertEquals("B", readAscii(enConnection));
    }

    public void testVaryMultipleFieldValuesWithMatch() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Vary: Accept-Language")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        URLConnection connection1 = openConnection(url);
        connection1.addRequestProperty("Accept-Language", "fr-CA, fr-FR");
        connection1.addRequestProperty("Accept-Language", "en-US");
        assertEquals("A", readAscii(connection1));

        URLConnection connection2 = openConnection(url);
        connection2.addRequestProperty("Accept-Language", "fr-CA, fr-FR");
        connection2.addRequestProperty("Accept-Language", "en-US");
        assertEquals("A", readAscii(connection2));
    }

    public void testVaryMultipleFieldValuesWithNoMatch() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Vary: Accept-Language")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        URLConnection connection1 = openConnection(url);
        connection1.addRequestProperty("Accept-Language", "fr-CA, fr-FR");
        connection1.addRequestProperty("Accept-Language", "en-US");
        assertEquals("A", readAscii(connection1));

        URLConnection connection2 = openConnection(url);
        connection2.addRequestProperty("Accept-Language", "fr-CA");
        connection2.addRequestProperty("Accept-Language", "en-US");
        assertEquals("B", readAscii(connection2));
    }

    public void testVaryAsterisk() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Vary: *")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        assertEquals("A", readAscii(openConnection(server.getUrl("/"))));
        assertEquals("B", readAscii(openConnection(server.getUrl("/"))));
    }

    public void testVaryAndHttps() throws Exception {
        server.useHttps(sslContext.getSocketFactory(), false);
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .addHeader("Vary: Accept-Language")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        HttpsURLConnection connection1 = (HttpsURLConnection) client.open(url);
        connection1.setSSLSocketFactory(sslContext.getSocketFactory());
        connection1.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);
        connection1.addRequestProperty("Accept-Language", "en-US");
        assertEquals("A", readAscii(connection1));

        HttpsURLConnection connection2 = (HttpsURLConnection) client.open(url);
        connection2.setSSLSocketFactory(sslContext.getSocketFactory());
        connection2.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);
        connection2.addRequestProperty("Accept-Language", "en-US");
        assertEquals("A", readAscii(connection2));
    }

//    public void testDiskWriteFailureCacheDegradation() throws Exception {
//        Deque<InvocationHandler> writeHandlers = mockOs.getHandlers("write");
//        int i = 0;
//        boolean hasMoreScenarios = true;
//        while (hasMoreScenarios) {
//            mockOs.enqueueNormal("write", i++);
//            mockOs.enqueueFault("write");
//            exercisePossiblyFaultyCache(false);
//            hasMoreScenarios = writeHandlers.isEmpty();
//            writeHandlers.clear();
//        }
//        System.out.println("Exercising the cache performs " + (i - 1) + " writes.");
//    }
//
//    public void testDiskReadFailureCacheDegradation() throws Exception {
//        Deque<InvocationHandler> readHandlers = mockOs.getHandlers("read");
//        int i = 0;
//        boolean hasMoreScenarios = true;
//        while (hasMoreScenarios) {
//            mockOs.enqueueNormal("read", i++);
//            mockOs.enqueueFault("read");
//            exercisePossiblyFaultyCache(true);
//            hasMoreScenarios = readHandlers.isEmpty();
//            readHandlers.clear();
//        }
//        System.out.println("Exercising the cache performs " + (i - 1) + " reads.");
//    }

    public void testCachePlusCookies() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Set-Cookie: a=FIRST; domain=" + server.getCookieDomain() + ";")
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Cache-Control: max-age=0")
                .setBody("A"));
        server.enqueue(new MockResponse()
                .addHeader("Set-Cookie: a=SECOND; domain=" + server.getCookieDomain() + ";")
                .setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));
        server.play();

        URL url = server.getUrl("/");
        assertEquals("A", readAscii(openConnection(url)));
        assertCookies(url, "a=FIRST");
        assertEquals("A", readAscii(openConnection(url)));
        assertCookies(url, "a=SECOND");
    }

    public void testGetHeadersReturnsNetworkEndToEndHeaders() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Allow: GET, HEAD")
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Cache-Control: max-age=0")
                .setBody("A"));
        server.enqueue(new MockResponse()
                .addHeader("Allow: GET, HEAD, PUT")
                .setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));
        server.play();

        URLConnection connection1 = openConnection(server.getUrl("/"));
        assertEquals("A", readAscii(connection1));
        assertEquals("GET, HEAD", connection1.getHeaderField("Allow"));

        URLConnection connection2 = openConnection(server.getUrl("/"));
        assertEquals("A", readAscii(connection2));
        assertEquals("GET, HEAD, PUT", connection2.getHeaderField("Allow"));
    }

    public void testGetHeadersReturnsCachedHopByHopHeaders() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Transfer-Encoding: identity")
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Cache-Control: max-age=0")
                .setBody("A"));
        server.enqueue(new MockResponse()
                .addHeader("Transfer-Encoding: none")
                .setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));
        server.play();

        URLConnection connection1 = openConnection(server.getUrl("/"));
        assertEquals("A", readAscii(connection1));
        assertEquals("identity", connection1.getHeaderField("Transfer-Encoding"));

        URLConnection connection2 = openConnection(server.getUrl("/"));
        assertEquals("A", readAscii(connection2));
        assertEquals("identity", connection2.getHeaderField("Transfer-Encoding"));
    }

    public void testGetHeadersDeletesCached100LevelWarnings() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Warning: 199 test danger")
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Cache-Control: max-age=0")
                .setBody("A"));
        server.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));
        server.play();

        URLConnection connection1 = openConnection(server.getUrl("/"));
        assertEquals("A", readAscii(connection1));
        assertEquals("199 test danger", connection1.getHeaderField("Warning"));

        URLConnection connection2 = openConnection(server.getUrl("/"));
        assertEquals("A", readAscii(connection2));
        assertEquals(null, connection2.getHeaderField("Warning"));
    }

    public void testGetHeadersRetainsCached200LevelWarnings() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Warning: 299 test danger")
                .addHeader("Last-Modified: " + formatDate(-1, TimeUnit.HOURS))
                .addHeader("Cache-Control: max-age=0")
                .setBody("A"));
        server.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));
        server.play();

        URLConnection connection1 = openConnection(server.getUrl("/"));
        assertEquals("A", readAscii(connection1));
        assertEquals("299 test danger", connection1.getHeaderField("Warning"));

        URLConnection connection2 = openConnection(server.getUrl("/"));
        assertEquals("A", readAscii(connection2));
        assertEquals("299 test danger", connection2.getHeaderField("Warning"));
    }

    public void assertCookies(URL url, String... expectedCookies) throws Exception {
        List<String> actualCookies = new ArrayList<String>();
        for (HttpCookie cookie : cookieManager.getCookieStore().get(url.toURI())) {
            actualCookies.add(cookie.toString());
        }
        assertEquals(Arrays.asList(expectedCookies), actualCookies);
    }

    public void testCachePlusRange() throws Exception {
        assertNotCached(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_PARTIAL)
                .addHeader("Date: " + formatDate(0, TimeUnit.HOURS))
                .addHeader("Content-Range: bytes 100-100/200")
                .addHeader("Cache-Control: max-age=60"));
    }

    public void testConditionalHitUpdatesCache() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Last-Modified: " + formatDate(0, TimeUnit.SECONDS))
                .addHeader("Cache-Control: max-age=0")
                .setBody("A"));
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=30")
                .addHeader("Allow: GET, HEAD")
                .setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        // cache miss; seed the cache
        HttpURLConnection connection1 = openConnection(server.getUrl("/a"));
        assertEquals("A", readAscii(connection1));
        assertEquals(null, connection1.getHeaderField("Allow"));

        // conditional cache hit; update the cache
        HttpURLConnection connection2 = openConnection(server.getUrl("/a"));
        assertEquals(HttpURLConnection.HTTP_OK, connection2.getResponseCode());
        assertEquals("A", readAscii(connection2));
        assertEquals("GET, HEAD", connection2.getHeaderField("Allow"));

        // full cache hit
        HttpURLConnection connection3 = openConnection(server.getUrl("/a"));
        assertEquals("A", readAscii(connection3));
        assertEquals("GET, HEAD", connection3.getHeaderField("Allow"));

        assertEquals(2, server.getRequestCount());
    }

    /**
     * @param delta the offset from the current date to use. Negative
     *     values yield dates in the past; positive values yield dates in the
     *     future.
     */
    private String formatDate(long delta, TimeUnit timeUnit) {
        return formatDate(new Date(System.currentTimeMillis() + timeUnit.toMillis(delta)));
    }

    private String formatDate(Date date) {
        DateFormat rfc1123 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        rfc1123.setTimeZone(TimeZone.getTimeZone("UTC"));
        return rfc1123.format(date);
    }

    private void addRequestBodyIfNecessary(String requestMethod, HttpURLConnection invalidate)
            throws IOException {
        if (requestMethod.equals("POST") || requestMethod.equals("PUT")) {
            invalidate.setDoOutput(true);
            OutputStream requestBody = invalidate.getOutputStream();
            requestBody.write('x');
            requestBody.close();
        }
    }

    private void assertNotCached(MockResponse response) throws Exception {
        server.enqueue(response.setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        assertEquals("A", readAscii(openConnection(url)));
        assertEquals("B", readAscii(openConnection(url)));
    }

    private void exercisePossiblyFaultyCache(boolean permitReadBodyFailures) throws Exception {
        server.shutdown();
        server = new MockWebServer();
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control: max-age=60")
                .setBody("A"));
        server.enqueue(new MockResponse().setBody("B"));
        server.play();

        URL url = server.getUrl("/" + UUID.randomUUID());
        assertEquals("A", readAscii(openConnection(url)));

        URLConnection connection = openConnection(url);
        InputStream in = connection.getInputStream();
        try {
            int bodyChar = in.read();
            assertTrue(bodyChar == 'A' || bodyChar == 'B');
            assertEquals(-1, in.read());
        } catch (IOException e) {
            if (!permitReadBodyFailures) {
                throw e;
            }
        }
    }

    /**
     * @return the request with the conditional get headers.
     */
    private RecordedRequest assertConditionallyCached(MockResponse response) throws Exception {
        // scenario 1: condition succeeds
        server.enqueue(response.setBody("A").setStatus("HTTP/1.1 200 A-OK"));
        server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED));

        // scenario 2: condition fails
        server.enqueue(response.setBody("B").setStatus("HTTP/1.1 200 B-OK"));
        server.enqueue(new MockResponse().setStatus("HTTP/1.1 200 C-OK").setBody("C"));

        server.play();

        URL valid = server.getUrl("/valid");
        HttpURLConnection connection1 = openConnection(valid);
        assertEquals("A", readAscii(connection1));
        assertEquals(HttpURLConnection.HTTP_OK, connection1.getResponseCode());
        assertEquals("A-OK", connection1.getResponseMessage());
        HttpURLConnection connection2 = openConnection(valid);
        assertEquals("A", readAscii(connection2));
        assertEquals(HttpURLConnection.HTTP_OK, connection2.getResponseCode());
        assertEquals("A-OK", connection2.getResponseMessage());

        URL invalid = server.getUrl("/invalid");
        HttpURLConnection connection3 = openConnection(invalid);
        assertEquals("B", readAscii(connection3));
        assertEquals(HttpURLConnection.HTTP_OK, connection3.getResponseCode());
        assertEquals("B-OK", connection3.getResponseMessage());
        HttpURLConnection connection4 = openConnection(invalid);
        assertEquals("C", readAscii(connection4));
        assertEquals(HttpURLConnection.HTTP_OK, connection4.getResponseCode());
        assertEquals("C-OK", connection4.getResponseMessage());

        server.takeRequest(); // regular get
        return server.takeRequest(); // conditional get
    }

    private void assertFullyCached(MockResponse response) throws Exception {
        server.enqueue(response.setBody("A"));
        server.enqueue(response.setBody("B"));
        server.play();

        URL url = server.getUrl("/");
        assertEquals("A", readAscii(openConnection(url)));
        assertEquals("A", readAscii(openConnection(url)));
    }

    /**
     * Shortens the body of {@code response} but not the corresponding headers.
     * Only useful to test how clients respond to the premature conclusion of
     * the HTTP body.
     */
    private MockResponse truncateViolently(MockResponse response, int numBytesToKeep) {
        response.setSocketPolicy(DISCONNECT_AT_END);
        List<String> headers = new ArrayList<String>(response.getHeaders());
        response.setBody(Arrays.copyOfRange(response.getBody(), 0, numBytesToKeep));
        response.getHeaders().clear();
        response.getHeaders().addAll(headers);
        return response;
    }

    /**
     * Reads {@code count} characters from the stream. If the stream is
     * exhausted before {@code count} characters can be read, the remaining
     * characters are returned and the stream is closed.
     */
    private String readAscii(URLConnection connection, int count) throws IOException {
        HttpURLConnection httpConnection = (HttpURLConnection) connection;
        InputStream in = httpConnection.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST
                ? connection.getInputStream()
                : httpConnection.getErrorStream();
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

    private String readAscii(URLConnection connection) throws IOException {
        return readAscii(connection, Integer.MAX_VALUE);
    }

    private void reliableSkip(InputStream in, int length) throws IOException {
        while (length > 0) {
            length -= in.skip(length);
        }
    }

    private void assertGatewayTimeout(HttpURLConnection connection) throws IOException {
        try {
            connection.getInputStream();
            fail();
        } catch (FileNotFoundException expected) {
        }
        assertEquals(504, connection.getResponseCode());
        assertEquals(-1, connection.getErrorStream().read());
    }

    enum TransferKind {
        CHUNKED() {
            @Override void setBody(MockResponse response, byte[] content, int chunkSize)
                    throws IOException {
                response.setChunkedBody(content, chunkSize);
            }
        },
        FIXED_LENGTH() {
            @Override void setBody(MockResponse response, byte[] content, int chunkSize) {
                response.setBody(content);
            }
        },
        END_OF_STREAM() {
            @Override void setBody(MockResponse response, byte[] content, int chunkSize) {
                response.setBody(content);
                response.setSocketPolicy(DISCONNECT_AT_END);
                for (Iterator<String> h = response.getHeaders().iterator(); h.hasNext(); ) {
                    if (h.next().startsWith("Content-Length:")) {
                        h.remove();
                        break;
                    }
                }
            }
        };

        abstract void setBody(MockResponse response, byte[] content, int chunkSize)
                throws IOException;

        void setBody(MockResponse response, String content, int chunkSize) throws IOException {
            setBody(response, content.getBytes("UTF-8"), chunkSize);
        }
    }

    private <T> List<T> toListOrNull(T[] arrayOrNull) {
        return arrayOrNull != null ? Arrays.asList(arrayOrNull) : null;
    }

    /**
     * Returns a gzipped copy of {@code bytes}.
     */
    public byte[] gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        OutputStream gzippedOut = new GZIPOutputStream(bytesOut);
        gzippedOut.write(bytes);
        gzippedOut.close();
        return bytesOut.toByteArray();
    }

    private class InsecureResponseCache extends ResponseCache {
        @Override public CacheRequest put(URI uri, URLConnection connection) throws IOException {
            return cache.put(uri, connection);
        }

        @Override public CacheResponse get(URI uri, String requestMethod,
                Map<String, List<String>> requestHeaders) throws IOException {
            final CacheResponse response = cache.get(uri, requestMethod, requestHeaders);
            if (response instanceof SecureCacheResponse) {
                return new CacheResponse() {
                    @Override public InputStream getBody() throws IOException {
                        return response.getBody();
                    }
                    @Override public Map<String, List<String>> getHeaders() throws IOException {
                        return response.getHeaders();
                    }
                };
            }
            return response;
        }
    }
}
