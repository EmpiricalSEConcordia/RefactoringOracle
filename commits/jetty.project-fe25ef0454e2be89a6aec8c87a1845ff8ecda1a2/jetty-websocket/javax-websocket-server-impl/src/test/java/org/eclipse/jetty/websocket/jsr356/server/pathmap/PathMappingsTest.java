//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.websocket.jsr356.server.pathmap;

import static org.hamcrest.Matchers.*;

import org.eclipse.jetty.websocket.jsr356.server.pathmap.PathMappings.MappedResource;
import org.junit.Assert;
import org.junit.Test;

public class PathMappingsTest
{
    private void assertMatch(PathMappings<String> pathmap, String path, String expectedValue)
    {
        String msg = String.format(".getMatch(\"%s\")",path);
        MappedResource<String> match = pathmap.getMatch(path);
        Assert.assertThat(msg,match,notNullValue());
        String actualMatch = match.getResource();
        Assert.assertEquals(msg,expectedValue,actualMatch);
    }

    /**
     * Test the match order rules imposed by the Servlet API.
     * <p>
     * <ul>
     * <li>Exact match</li>
     * <li>Longest prefix match</li>
     * <li>Longest suffix match</li>
     * <li>default</li>
     * </ul>
     */
    @Test
    public void testServletMatchOrder()
    {
        PathMappings<String> p = new PathMappings<>();

        p.put(new ServletPathSpec("/abs/path"),"path");
        p.put(new ServletPathSpec("/abs/path/longer"),"longpath");
        p.put(new ServletPathSpec("/animal/bird/*"),"birds");
        p.put(new ServletPathSpec("/animal/fish/*"),"fishes");
        p.put(new ServletPathSpec("/animal/*"),"animals");
        p.put(new ServletPathSpec("*.tar.gz"),"tarball");
        p.put(new ServletPathSpec("*.gz"),"gzipped");
        p.put(new ServletPathSpec("/"),"default");

        for (MappedResource<String> res : p)
        {
            System.out.printf("  %s%n",res);
        }

        assertMatch(p,"/abs/path","path");
        assertMatch(p,"/abs/path/longer","longpath");
        assertMatch(p,"/abs/path/foo","default");
        assertMatch(p,"/main.css","default");
        assertMatch(p,"/downloads/script.gz","gzipped");
        assertMatch(p,"/downloads/distribution.tar.gz","tarball");
        assertMatch(p,"/downloads/readme.txt","default");
        assertMatch(p,"/downloads/logs.tgz","default");
        assertMatch(p,"/animal/horse/mustang","animals");
        assertMatch(p,"/animal/bird/eagle/bald","birds");
        assertMatch(p,"/animal/fish/shark/hammerhead","fishes");
        assertMatch(p,"/animal/insect/ladybug","animals");
    }

    /**
     * Test the match order rules imposed by the WebSocket API (JSR-356)
     * <p>
     * <ul>
     * <li>Exact match</li>
     * <li>Longest prefix match</li>
     * <li>Longest suffix match</li>
     * </ul>
     */
    @Test
    public void testWebsocketMatchOrder()
    {
        PathMappings<String> p = new PathMappings<>();

        p.put(new PathParamSpec("/a/{var}/c"),"endpointA");
        p.put(new PathParamSpec("/a/b/c"),"endpointB");
        p.put(new PathParamSpec("/a/{var1}/{var2}"),"endpointC");
        p.put(new PathParamSpec("/{var1}/d"),"endpointD");
        p.put(new PathParamSpec("/b/{var2}"),"endpointE");

        for (MappedResource<String> res : p)
        {
            System.out.printf("  %s%n",res);
        }

        assertMatch(p,"/a/b/c","endpointB");
        assertMatch(p,"/a/d/c","endpointA");
        assertMatch(p,"/a/x/y","endpointC");

        assertMatch(p,"/b/d","endpointE");
    }
}
