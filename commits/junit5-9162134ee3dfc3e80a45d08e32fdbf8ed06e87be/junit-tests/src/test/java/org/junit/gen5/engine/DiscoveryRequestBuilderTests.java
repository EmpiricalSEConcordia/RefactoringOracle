/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.gen5.engine.discoveryrequest.dsl.ClassSelectorBuilder.forClass;
import static org.junit.gen5.engine.discoveryrequest.dsl.ClassSelectorBuilder.forClassName;
import static org.junit.gen5.engine.discoveryrequest.dsl.DiscoveryRequestBuilder.request;
import static org.junit.gen5.engine.discoveryrequest.dsl.MethodSelectorBuilder.byMethod;
import static org.junit.gen5.engine.discoveryrequest.dsl.PackageSelectorBuilder.byPackageName;
import static org.junit.gen5.engine.discoveryrequest.dsl.UniqueIdSelectorBuilder.byUniqueId;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

import org.assertj.core.util.Files;
import org.junit.gen5.api.Test;
import org.junit.gen5.engine.discoveryrequest.*;
import org.junit.gen5.engine.discoveryrequest.dsl.ClasspathSelectorBuilder;

public class DiscoveryRequestBuilderTests {
	@Test
	public void packagesAreStoredInDiscoveryRequest() throws Exception {
		// @formatter:off
        DiscoveryRequest discoveryRequest = request()
				.select(
						byPackageName("org.junit.gen5.engine")
				).build();
        // @formatter:on

		List<String> packageSelectors = discoveryRequest.getSelectorsByType(PackageNameSelector.class).stream().map(
			PackageNameSelector::getPackageName).collect(toList());
		assertThat(packageSelectors).contains("org.junit.gen5.engine");
	}

	@Test
	public void classesAreStoredInDiscoveryRequest() throws Exception {
		// @formatter:off
        DiscoveryRequest discoveryRequest = request()
				.select(
						forClassName("org.junit.gen5.engine.DiscoveryRequestBuilderTests"),
						forClass(SampleTestClass.class)
				)
            .build();
        // @formatter:on

		List<Class<?>> classes = discoveryRequest.getSelectorsByType(ClassSelector.class).stream().map(
			ClassSelector::getTestClass).collect(toList());
		assertThat(classes).contains(SampleTestClass.class, DiscoveryRequestBuilderTests.class);
	}

	@Test
	public void methodsByNameAreStoredInDiscoveryRequest() throws Exception {
		Class<?> testClass = SampleTestClass.class;
		Method testMethod = testClass.getMethod("test");

		// @formatter:off
        DiscoveryRequest discoveryRequest = request()
				.select(
						byMethod("org.junit.gen5.engine.DiscoveryRequestBuilderTests$SampleTestClass", "test")
				).build();
        // @formatter:on

		List<MethodSelector> methodSelectors = discoveryRequest.getSelectorsByType(MethodSelector.class);
		assertThat(methodSelectors).hasSize(1);

		MethodSelector methodSelector = methodSelectors.get(0);
		assertThat(methodSelector.getTestClass()).isEqualTo(testClass);
		assertThat(methodSelector.getTestMethod()).isEqualTo(testMethod);
	}

	@Test
	public void methodsByClassAreStoredInDiscoveryRequest() throws Exception {
		Class<?> testClass = SampleTestClass.class;
		Method testMethod = testClass.getMethod("test");

		// @formatter:off
        DiscoveryRequest discoveryRequest = request()
				.select(
						byMethod(SampleTestClass.class, "test")
				).build();
		// @formatter:on

		List<MethodSelector> methodSelectors = discoveryRequest.getSelectorsByType(MethodSelector.class);
		assertThat(methodSelectors).hasSize(1);

		MethodSelector methodSelector = methodSelectors.get(0);
		assertThat(methodSelector.getTestClass()).isEqualTo(testClass);
		assertThat(methodSelector.getTestMethod()).isEqualTo(testMethod);
	}

	@Test
	public void unavailableFoldersAreNotStoredInDiscoveryRequest() throws Exception {
		// @formatter:off
        DiscoveryRequest discoveryRequest = request()
				.select(
						ClasspathSelectorBuilder.byPath("/some/local/path")
				).build();
        // @formatter:on

		List<String> folders = discoveryRequest.getSelectorsByType(ClasspathSelector.class).stream().map(
			ClasspathSelector::getClasspathRoot).map(File::getAbsolutePath).collect(toList());

		assertThat(folders).isEmpty();
	}

	@Test
	public void availableFoldersAreStoredInDiscoveryRequest() throws Exception {
		File temporaryFolder = Files.newTemporaryFolder();
		try {
			// @formatter:off
			DiscoveryRequest discoveryRequest = request()
					.select(
							ClasspathSelectorBuilder.byPath(temporaryFolder.getAbsolutePath())
					).build();
			// @formatter:on

			List<String> folders = discoveryRequest.getSelectorsByType(ClasspathSelector.class).stream().map(
				ClasspathSelector::getClasspathRoot).map(File::getAbsolutePath).collect(toList());

			assertThat(folders).contains(temporaryFolder.getAbsolutePath());
		}
		finally {
			temporaryFolder.delete();
		}
	}

	@Test
	public void uniqueIdsAreStoredInDiscoveryRequest() throws Exception {
		// @formatter:off
        DiscoveryRequest discoveryRequest = request()
				.select(
						byUniqueId("engine:bla:foo:bar:id1"),
						byUniqueId("engine:bla:foo:bar:id2")
				).build();
        // @formatter:on

		List<String> uniqueIds = discoveryRequest.getSelectorsByType(UniqueIdSelector.class).stream().map(
			UniqueIdSelector::getUniqueId).collect(toList());

		assertThat(uniqueIds).contains("engine:bla:foo:bar:id1", "engine:bla:foo:bar:id2");
	}

	private static class SampleTestClass {
		@Test
		public void test() {
		}
	}
}
