// ========================================================================
// Copyright 2011-2012 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
//
//     The Eclipse Public License is available at
//     http://www.eclipse.org/legal/epl-v10.html
//
//     The Apache License v2.0 is available at
//     http://www.opensource.org/licenses/apache2.0.php
//
// You may elect to redistribute this code under either of these licenses.
//========================================================================
package org.eclipse.jetty.websocket.server.callbacks;

import org.eclipse.jetty.util.FutureCallback;

public class WebSocketOpenCallback extends FutureCallback<String>
{
    @Override
    public void completed(String context)
    {
        // TODO notify API on connection open
    }

    @Override
    public void failed(String context, Throwable x)
    {
        // TODO notify API on open failure
    }
}
