// ========================================================================
// Copyright (c) 2004-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses.
// ========================================================================

package org.eclipse.jetty.client;

import java.io.FileInputStream;

import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.junit.Test;

public class ExternalKeyStoreAsyncSslHttpExchangeTest extends SslHttpExchangeTest
{
    @Override
    public void setUp() throws Exception
    {
        _scheme = "https";
        startServer();
        _httpClient = new HttpClient();
        _httpClient.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
        _httpClient.setMaxConnectionsPerAddress(2);

        String keystore = MavenTestingUtils.getTestResourceFile("keystore").getAbsolutePath();

        _httpClient.setKeyStoreInputStream(new FileInputStream(keystore));
        _httpClient.setKeyStorePassword("storepwd");
        _httpClient.setKeyManagerPassword("keypwd");
        _httpClient.start();
    }

    @Override
    @Test
    public void testBigPostWithContentExchange() throws Exception
    {
        super.testBigPostWithContentExchange();
    }

}
