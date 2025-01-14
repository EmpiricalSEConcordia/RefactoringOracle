/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.boot.actuate.endpoint.mvc;

import java.io.IOException;
import java.nio.charset.Charset;

import javax.servlet.http.HttpServletRequest;

import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

public class ActuatorHalBrowserEndpoint extends ActuatorHalJsonEndpoint implements
		ResourceLoaderAware {

	private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

	private static HalBrowserLocation[] HAL_BROWSER_RESOURCE_LOCATIONS = {
			new HalBrowserLocation("classpath:/META-INF/spring-data-rest/hal-browser/",
					"index.html"),
			new HalBrowserLocation(
					"classpath:/META-INF/resources/webjars/hal-browser/9f96c74/",
					"browser.html") };

	private HalBrowserLocation location;

	public ActuatorHalBrowserEndpoint(ManagementServletContext managementServletContext) {
		super(managementServletContext);
	}

	@RequestMapping(produces = MediaType.TEXT_HTML_VALUE)
	public String browse(HttpServletRequest request) {
		String contextPath = getManagementServletContext().getContextPath()
				+ (getPath().endsWith("/") ? getPath() : getPath() + "/");
		if (request.getRequestURI().endsWith("/")) {
			return "forward:" + contextPath + this.location.getHtmlFile();
		}
		return "redirect:" + contextPath;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.location = getHalBrowserLocation(resourceLoader);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// Make sure the root path is not cached so the browser comes back for the JSON
		// and add a transformer to set the initial link
		if (this.location != null) {
			String start = getManagementServletContext().getContextPath() + getPath();
			registry.addResourceHandler(start + "/", start + "/**")
					.addResourceLocations(this.location.getResourceLocation())
					.setCachePeriod(0).resourceChain(true)
					.addTransformer(new InitialUrlTransformer());
		}
	}

	public static HalBrowserLocation getHalBrowserLocation(ResourceLoader resourceLoader) {
		for (HalBrowserLocation candidate : HAL_BROWSER_RESOURCE_LOCATIONS) {
			try {
				Resource resource = resourceLoader.getResource(candidate.toString());
				if (resource != null && resource.exists()) {
					return candidate;
				}
			}
			catch (Exception ex) {
				// Ignore
			}
		}
		return null;
	}

	public static class HalBrowserLocation {

		private final String resourceLocation;

		private final String htmlFile;

		public HalBrowserLocation(String resourceLocation, String html) {
			this.resourceLocation = resourceLocation;
			this.htmlFile = html;
		}

		public String getResourceLocation() {
			return this.resourceLocation;
		}

		public String getHtmlFile() {
			return this.htmlFile;
		}

		@Override
		public String toString() {
			return this.resourceLocation + this.htmlFile;
		}

	}

	/**
	 * {@link ResourceTransformer} to change the initial link location.
	 */
	private class InitialUrlTransformer implements ResourceTransformer {

		@Override
		public Resource transform(HttpServletRequest request, Resource resource,
				ResourceTransformerChain transformerChain) throws IOException {
			resource = transformerChain.transform(request, resource);
			if (resource.getFilename().equalsIgnoreCase(
					ActuatorHalBrowserEndpoint.this.location.getHtmlFile())) {
				return replaceInitialLink(resource);
			}
			return resource;
		}

		private Resource replaceInitialLink(Resource resource) throws IOException {
			byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
			String content = new String(bytes, DEFAULT_CHARSET);
			String initial = getManagementServletContext().getContextPath() + getPath();
			content = content.replace("entryPoint: '/'", "entryPoint: '" + initial + "'");
			return new TransformedResource(resource, content.getBytes(DEFAULT_CHARSET));
		}

	}

}
