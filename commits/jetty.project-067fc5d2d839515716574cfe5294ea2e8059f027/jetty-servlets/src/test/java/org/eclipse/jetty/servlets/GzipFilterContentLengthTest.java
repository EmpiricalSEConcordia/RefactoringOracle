//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.servlets;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlets.gzip.AsyncScheduledDispatchWrite;
import org.eclipse.jetty.servlets.gzip.AsyncTimeoutDispatchWrite;
import org.eclipse.jetty.servlets.gzip.AsyncTimeoutCompleteWrite;
import org.eclipse.jetty.servlets.gzip.GzipTester;
import org.eclipse.jetty.servlets.gzip.GzipTester.ContentMetadata;
import org.eclipse.jetty.servlets.gzip.TestDirContentServlet;
import org.eclipse.jetty.servlets.gzip.TestServletBufferTypeLengthWrite;
import org.eclipse.jetty.servlets.gzip.TestServletLengthStreamTypeWrite;
import org.eclipse.jetty.servlets.gzip.TestServletLengthTypeStreamWrite;
import org.eclipse.jetty.servlets.gzip.TestServletStreamLengthTypeWrite;
import org.eclipse.jetty.servlets.gzip.TestServletStreamLengthTypeWriteWithFlush;
import org.eclipse.jetty.servlets.gzip.TestServletStreamTypeLengthWrite;
import org.eclipse.jetty.servlets.gzip.TestServletTypeLengthStreamWrite;
import org.eclipse.jetty.servlets.gzip.TestServletTypeStreamLengthWrite;
import org.eclipse.jetty.toolchain.test.TestTracker;
import org.eclipse.jetty.toolchain.test.TestingDir;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test the GzipFilter support for Content-Length setting variations.
 *
 * @see <a href="Eclipse Bug 354014">http://bugs.eclipse.org/354014</a>
 */
@RunWith(Parameterized.class)
public class GzipFilterContentLengthTest
{
    @Rule
    public final TestTracker tracker = new TestTracker();

    @Rule
    public TestingDir testingdir = new TestingDir();
    
    private static final HttpConfiguration defaultHttp = new HttpConfiguration();
    private static final int LARGE = defaultHttp.getOutputBufferSize() * 8;
    private static final int MEDIUM = defaultHttp.getOutputBufferSize();
    private static final int SMALL = defaultHttp.getOutputBufferSize() / 4;
    private static final int TINY = AsyncGzipFilter.DEFAULT_MIN_GZIP_SIZE / 2;
    private static final boolean EXPECT_COMPRESSED = true;

    @Parameters(name = "{0} bytes - {1} - compressed({2}) - type({3}) - filter({4})")
    public static List<Object[]> data()
    {
        List<Object[]> ret = new ArrayList<Object[]>();
        
        String compressionTypes[] = new String[] { GzipFilter.GZIP, GzipFilter.DEFLATE };
        Class<?> gzipFilters[] = new Class<?>[] { GzipFilter.class, AsyncGzipFilter.class };
        
        for(String compressionType: compressionTypes)
        {
            for(Class<?> gzipFilter: gzipFilters)
            {
                ret.add(new Object[] { 0, "empty.txt", !EXPECT_COMPRESSED, compressionType, gzipFilter });
                ret.add(new Object[] { TINY, "file-tiny.txt", !EXPECT_COMPRESSED, compressionType, gzipFilter });
                ret.add(new Object[] { SMALL, "file-small.txt", EXPECT_COMPRESSED, compressionType, gzipFilter });
                ret.add(new Object[] { SMALL, "file-small.mp3", !EXPECT_COMPRESSED, compressionType, gzipFilter });
                ret.add(new Object[] { MEDIUM, "file-med.txt", EXPECT_COMPRESSED, compressionType, gzipFilter });
                ret.add(new Object[] { MEDIUM, "file-medium.mp3", !EXPECT_COMPRESSED, compressionType, gzipFilter });
                ret.add(new Object[] { LARGE, "file-large.txt", EXPECT_COMPRESSED, compressionType, gzipFilter });
                ret.add(new Object[] { LARGE, "file-large.mp3", !EXPECT_COMPRESSED, compressionType, gzipFilter });
            }
        }

        return ret;
    }

    @Parameter(0)
    public int fileSize;
    @Parameter(1)
    public String fileName;
    @Parameter(2)
    public boolean expectCompressed;
    @Parameter(3)
    public String compressionType;
    @Parameter(4)
    public Class<? extends GzipFilter> gzipFilterClass;
    
    private void testWithGzip(Class<? extends TestDirContentServlet> contentServlet) throws Exception
    {
        GzipTester tester = new GzipTester(testingdir, GzipFilter.GZIP);
        
        // Add AsyncGzip Filter
        FilterHolder gzipHolder = new FilterHolder(gzipFilterClass);
        gzipHolder.setAsyncSupported(true);
        tester.addFilter(gzipHolder,"*.txt",EnumSet.of(DispatcherType.REQUEST,DispatcherType.ASYNC));
        tester.addFilter(gzipHolder,"*.mp3",EnumSet.of(DispatcherType.REQUEST,DispatcherType.ASYNC));
        gzipHolder.setInitParameter("mimeTypes","text/plain");

        // Add content servlet
        tester.setContentServlet(contentServlet);
        
        try
        {
            String testFilename = String.format("%s-%s-%s", gzipFilterClass.getSimpleName(), contentServlet.getSimpleName(), fileName);
            File testFile = tester.prepareServerFile(testFilename,fileSize);
            
            tester.start();
            
            HttpTester.Response response = tester.executeRequest("GET","/context/" + testFile.getName(),5,TimeUnit.SECONDS);
            
            if (response.getStatus()!=200)
                System.err.println("DANG!!!! "+response);
            
            assertThat("Response status", response.getStatus(), is(HttpStatus.OK_200));
            
            if (expectCompressed)
            {
                // Must be gzip compressed
                assertThat("Content-Encoding",response.get("Content-Encoding"),containsString(GzipFilter.GZIP));
            } else
            {
                assertThat("Content-Encoding",response.get("Content-Encoding"),not(containsString(GzipFilter.GZIP)));
            }
            
            // Uncompressed content Size
            ContentMetadata content = tester.getResponseMetadata(response);
            assertThat("(Uncompressed) Content Length", content.size, is((long)fileSize));
        }
        finally
        {
            tester.stop();
        }
    }

    /**
     * Test with content servlet that does:  
     * AsyncContext create -> timeout -> onTimeout -> write-response -> complete
     */
    @Test
    public void testAsyncTimeoutCompleteWrite_Default() throws Exception
    {
        if (expectCompressed && gzipFilterClass==GzipFilter.class)
            return; // Default startAsync will never work with GzipFilter, which needs wrapping
        testWithGzip(AsyncTimeoutCompleteWrite.Default.class);
    }
    
    /**
     * Test with content servlet that does:  
     * AsyncContext create -> timeout -> onTimeout -> write-response -> complete
     */
    @Test
    public void testAsyncTimeoutCompleteWrite_Passed() throws Exception
    {
        testWithGzip(AsyncTimeoutCompleteWrite.Passed.class);
    }
    
    /**
     * Test with content servlet that does:  
     * AsyncContext create -> timeout -> onTimeout -> dispatch -> write-response
     */
    @Test
    public void testAsyncTimeoutDispatchWrite_Default() throws Exception
    {
        testWithGzip(AsyncTimeoutDispatchWrite.Default.class);
    }
    
    /**
     * Test with content servlet that does:  
     * AsyncContext create -> timeout -> onTimeout -> dispatch -> write-response
     */
    @Test
    public void testAsyncTimeoutDispatchWrite_Passed() throws Exception
    {
        testWithGzip(AsyncTimeoutDispatchWrite.Passed.class);
    }

    /**
     * Test with content servlet that does:  
     * AsyncContext create -> no-timeout -> scheduler.schedule -> dispatch -> write-response
     */
    @Test
    public void testAsyncScheduledDispatchWrite_Default() throws Exception
    {
        testWithGzip(AsyncScheduledDispatchWrite.Default.class);
    }
    
    /**
     * Test with content servlet that does:  
     * AsyncContext create -> no-timeout -> scheduler.schedule -> dispatch -> write-response
     */
    @Test
    public void testAsyncScheduledDispatchWrite_Passed() throws Exception
    {
        testWithGzip(AsyncScheduledDispatchWrite.Passed.class);
    }

    /**
     * Test with content servlet that does:  
     * 1) setHeader(content-length)
     * 2) getOutputStream()
     * 3) setHeader(content-type)
     * 4) outputStream.write()
     * 
     * @see <a href="Eclipse Bug 354014">http://bugs.eclipse.org/354014</a>
     */
    @Test
    public void testServletLengthStreamTypeWrite() throws Exception
    {
        testWithGzip(TestServletLengthStreamTypeWrite.class);
    }

    /**
     * Test with content servlet that does:  
     * 1) setHeader(content-length)
     * 2) setHeader(content-type)
     * 3) getOutputStream()
     * 4) outputStream.write()
     * 
     * @see <a href="Eclipse Bug 354014">http://bugs.eclipse.org/354014</a>
     */
    @Test
    public void testServletLengthTypeStreamWrite() throws Exception
    {
        testWithGzip(TestServletLengthTypeStreamWrite.class);
    }

    /**
     * Test with content servlet that does:  
     * 1) getOutputStream()
     * 2) setHeader(content-length)
     * 3) setHeader(content-type)
     * 4) outputStream.write()
     * 
     * @see <a href="Eclipse Bug 354014">http://bugs.eclipse.org/354014</a>
     */
    @Test
    public void testServletStreamLengthTypeWrite() throws Exception
    {
        testWithGzip(TestServletStreamLengthTypeWrite.class);
    }

    /**
     * Test with content servlet that does:  
     * 1) getOutputStream()
     * 2) setHeader(content-length)
     * 3) setHeader(content-type)
     * 4) outputStream.write() (with frequent response flush)
     * 
     * @see <a href="Eclipse Bug 354014">http://bugs.eclipse.org/354014</a>
     */
    @Test
    public void testServletStreamLengthTypeWriteWithFlush() throws Exception
    {
        testWithGzip(TestServletStreamLengthTypeWriteWithFlush.class);
    }

    /**
     * Test with content servlet that does:  
     * 1) getOutputStream()
     * 2) setHeader(content-type)
     * 3) setHeader(content-length)
     * 4) outputStream.write()
     * 
     * @see <a href="Eclipse Bug 354014">http://bugs.eclipse.org/354014</a>
     */
    @Test
    public void testServletStreamTypeLengthWrite() throws Exception
    {
        testWithGzip(TestServletStreamTypeLengthWrite.class);
    }

    /**
     * Test with content servlet that does:  
     * 1) setHeader(content-type)
     * 2) setHeader(content-length)
     * 3) getOutputStream()
     * 4) outputStream.write()
     * 
     * @see <a href="Eclipse Bug 354014">http://bugs.eclipse.org/354014</a>
     */
    @Test
    public void testServletTypeLengthStreamWrite() throws Exception
    {
        testWithGzip(TestServletTypeLengthStreamWrite.class);
    }

    /**
     * Test with content servlet that does:  
     * 1) setHeader(content-type)
     * 2) getOutputStream()
     * 3) setHeader(content-length)
     * 4) outputStream.write()
     * 
     * @see <a href="Eclipse Bug 354014">http://bugs.eclipse.org/354014</a>
     */
    @Test
    public void testServletTypeStreamLengthWrite() throws Exception
    {
        testWithGzip(TestServletTypeStreamLengthWrite.class);
    }

    /**
     * Test with content servlet that does:  
     * 2) getOutputStream()
     * 1) setHeader(content-type)
     * 3) setHeader(content-length)
     * 4) (unwrapped) HttpOutput.write(ByteBuffer)
     * 
     * This is done to demonstrate a bug with using HttpOutput.write()
     * while also using GzipFilter
     * 
     * @see <a href="Eclipse Bug 450873">http://bugs.eclipse.org/450873</a>
     */
    @Test
    public void testHttpOutputWrite() throws Exception
    {
        if (gzipFilterClass == GzipFilter.class)
            return;  // Can't downcaste output stream when wrapper is used
        testWithGzip(TestServletBufferTypeLengthWrite.class);
    }
}
