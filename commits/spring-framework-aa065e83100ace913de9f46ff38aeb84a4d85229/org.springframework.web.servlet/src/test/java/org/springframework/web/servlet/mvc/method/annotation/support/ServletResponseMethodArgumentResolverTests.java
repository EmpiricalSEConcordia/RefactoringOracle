/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.web.servlet.mvc.method.annotation.support;

import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.Method;
import javax.servlet.ServletResponse;

import org.junit.Before;
import org.junit.Test;

import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.support.ServletResponseMethodArgumentResolver;

import static org.junit.Assert.*;

/**
 * @author Arjen Poutsma
 */
public class ServletResponseMethodArgumentResolverTests {

	private ServletResponseMethodArgumentResolver resolver;

	private Method supportedParams;

	private ServletWebRequest webRequest;

	private MockHttpServletResponse servletResponse;

	private ModelAndViewContainer mavContainer;

	@Before
	public void setUp() throws Exception {
		resolver = new ServletResponseMethodArgumentResolver();
		supportedParams = getClass().getMethod("supportedParams", ServletResponse.class, OutputStream.class, Writer.class);
		servletResponse = new MockHttpServletResponse();
		mavContainer = new ModelAndViewContainer();
		webRequest = new ServletWebRequest(new MockHttpServletRequest(), servletResponse);
	}

	@Test
	public void servletResponse() throws Exception {
		MethodParameter servletResponseParameter = new MethodParameter(supportedParams, 0);

		assertTrue("ServletResponse not supported", resolver.supportsParameter(servletResponseParameter));

		Object result = resolver.resolveArgument(servletResponseParameter, mavContainer, webRequest, null);
		assertSame("Invalid result", servletResponse, result);
		assertFalse(mavContainer.isResolveView());
	}

	@Test
	public void outputStream() throws Exception {
		MethodParameter outputStreamParameter = new MethodParameter(supportedParams, 1);

		assertTrue("OutputStream not supported", resolver.supportsParameter(outputStreamParameter));

		Object result = resolver.resolveArgument(outputStreamParameter, mavContainer, webRequest, null);
		assertSame("Invalid result", servletResponse.getOutputStream(), result);
		assertFalse(mavContainer.isResolveView());
	}

	@Test
	public void writer() throws Exception {
		MethodParameter writerParameter = new MethodParameter(supportedParams, 2);

		assertTrue("Writer not supported", resolver.supportsParameter(writerParameter));

		Object result = resolver.resolveArgument(writerParameter, mavContainer, webRequest, null);
		assertSame("Invalid result", servletResponse.getWriter(), result);
		assertFalse(mavContainer.isResolveView());
	}

	public void supportedParams(ServletResponse p0, OutputStream p1, Writer p2) {

	}
}
