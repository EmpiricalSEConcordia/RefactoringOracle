//
//  ========================================================================
//  Copyright (c) 1995-2012 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty;

import com.acme.DispatchServlet;
import junit.framework.TestCase;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletTester;

/**
 * Simple tests against DispatchServlet.
 */
public class DispatchServletTest extends TestCase
{
    /**
     * As filed in JETTY-978.
     *
     * Security problems in demo dispatch servlet.
     *
     * <blockquote>
     * <p>
     * The dispatcher servlet (com.acme.DispatchServlet) is prone to a Denial of
     * Service vulnerability.
     * </p>
     * <p>
     * This example servlet is meant to be used as a resources dispatcher,
     * however a malicious aggressor may abuse this functionality in order to
     * cause a recursive inclusion. In details, it is possible to abuse the
     * method com.acme.DispatchServlet.doGet(DispatchServlet.java:203) forcing
     * the application to recursively include the "Dispatch" servlet.
     * </p>
     * <p>
     * Dispatch com.acme.DispatchServlet 1 Dispatch /dispatch/* As a result, it
     * is possible to trigger a "java.lang.StackOverflowError" and consequently
     * an internal server error (500).
     * </p>
     * <p>
     * Multiple requests may easily affect the availability of the servlet
     * container. Since this attack can cause the server to consume resources in
     * a non-linear relationship to the size of inputs, it should be considered
     * as a server flaw.
     * </p>
     * <p>
     * The vulnerability seems confined to the example servlet and it does not
     * afflict the Jetty's core."
     * </p>
     * </blockquote>
     *
     * @throws Exception
     */
    public void testSelfRefForwardDenialOfService() throws Exception
    {
        ServletTester tester = new ServletTester();
        tester.setContextPath("/tests");

        ServletHolder dispatch = tester.addServlet(DispatchServlet.class,"/dispatch/*");
        tester.addServlet(DefaultServlet.class,"/");
        tester.start();

        StringBuffer req1 = new StringBuffer();
        req1.append("GET /tests/dispatch/includeN/"+dispatch.getName()+" HTTP/1.1\n");
        req1.append("Host: tester\n");
        req1.append("Connection: close\n");
        req1.append("\n");

        String response = tester.getResponses(req1.toString());

        String msg = "Response code on SelfRefDoS";

        assertFalse(msg + " should not be code 500.",response.startsWith("HTTP/1.1 500 "));
        assertTrue(msg + " should return error code 403 (Forbidden)", response.startsWith("HTTP/1.1 403 "));
    }

    public void testSelfRefDeep() throws Exception
    {
        ServletTester tester = new ServletTester();
        tester.setContextPath("/tests");
        tester.addServlet(DispatchServlet.class,"/dispatch/*");
        tester.addServlet(DefaultServlet.class,"/");
        tester.start();

        String selfRefs[] =
        { "/dispatch/forward", "/dispatch/includeS", "/dispatch/includeW", "/dispatch/includeN", };

        /*
         * Number of nested dispatch requests. 220 is a good value, as it won't
         * trigger an Error 413 response (Entity too large). Anything larger
         * than 220 will trigger a 413 response.
         */
        int nestedDepth = 220;

        for (int sri = 0; sri < selfRefs.length; sri++)
        {
            String selfRef = selfRefs[sri];

            StringBuffer req1 = new StringBuffer();
            req1.append("GET /tests");
            for (int i = 0; i < nestedDepth; i++)
            {
                req1.append(selfRef);
            }

            req1.append("/ HTTP/1.1\n");
            req1.append("Host: tester\n");
            req1.append("Connection: close\n");
            req1.append("\n");

            String response = tester.getResponses(req1.toString());

            StringBuffer msg = new StringBuffer();
            msg.append("Response code on nested \"").append(selfRef).append("\"");
            msg.append(" (depth:").append(nestedDepth).append(")");

            assertFalse(msg + " should not be code 413 (Request Entity Too Large)," +
                    "the nestedDepth in the TestCase is too large (reduce it)",
                    response.startsWith("HTTP/1.1 413 "));

            assertFalse(msg + " should not be code 500.",response.startsWith("HTTP/1.1 500 "));

            assertTrue(msg + " should return error code 403 (Forbidden)", response.startsWith("HTTP/1.1 403 "));
        }
    }
}
