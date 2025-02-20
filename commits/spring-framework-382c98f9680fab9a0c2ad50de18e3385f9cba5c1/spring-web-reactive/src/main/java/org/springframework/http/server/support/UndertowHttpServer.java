/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.http.server.support;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.http.server.undertow.HttpHandlerHttpHandler;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;

/**
 * @author Marek Hawrylczak
 */
public class UndertowHttpServer extends HttpServerSupport implements InitializingBean, HttpServer {

	private Undertow server;

	private boolean running;


	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.notNull(getHttpHandler());
		HttpHandler handler = new HttpHandlerHttpHandler(getHttpHandler());
		int port = (getPort() != -1 ? getPort() : 8080);
		this.server = Undertow.builder().addHttpListener(port, "localhost")
				.setHandler(handler).build();
	}

	@Override
	public void start() {
		if (!this.running) {
			this.server.start();
			this.running = true;
		}

	}

	@Override
	public void stop() {
		if (this.running) {
			this.server.stop();
			this.running = false;
		}
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

}
