/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.console.tasks;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.gen5.api.Assertions.assertEquals;
import static org.junit.gen5.api.Assertions.assertFalse;
import static org.junit.gen5.api.Assertions.assertTrue;
import static org.junit.gen5.commons.util.CollectionUtils.getOnlyElement;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.gen5.api.Test;
import org.junit.gen5.console.options.CommandLineOptions;
import org.junit.gen5.engine.TestDescriptor;
import org.junit.gen5.engine.TestTag;
import org.junit.gen5.engine.discovery.ClassFilter;
import org.junit.gen5.engine.discovery.ClassSelector;
import org.junit.gen5.engine.discovery.ClasspathSelector;
import org.junit.gen5.engine.discovery.MethodSelector;
import org.junit.gen5.engine.discovery.PackageSelector;
import org.junit.gen5.launcher.*;

public class DiscoveryRequestCreatorTests {
	private CommandLineOptions options = new CommandLineOptions();

	@Test
	public void convertsClassArgument() {
		Class<?> testClass = getClass();
		options.setArguments(singletonList(testClass.getName()));

		TestDiscoveryRequest request = convert();

		List<ClassSelector> classSelectors = request.getSelectorsByType(ClassSelector.class);
		assertThat(classSelectors).hasSize(1);
		assertEquals(testClass, getOnlyElement(classSelectors).getTestClass());
	}

	@Test
	public void convertsMethodArgument() throws Exception {
		Class<?> testClass = getClass();
		Method testMethod = testClass.getDeclaredMethod("convertsMethodArgument");
		options.setArguments(singletonList(testClass.getName() + "#" + testMethod.getName()));

		TestDiscoveryRequest request = convert();

		List<MethodSelector> methodSelectors = request.getSelectorsByType(MethodSelector.class);
		assertThat(methodSelectors).hasSize(1);
		assertEquals(testClass, getOnlyElement(methodSelectors).getTestClass());
		assertEquals(testMethod, getOnlyElement(methodSelectors).getTestMethod());
	}

	@Test
	public void convertsPackageArgument() {
		String packageName = getClass().getPackage().getName();
		options.setArguments(singletonList(packageName));

		TestDiscoveryRequest request = convert();

		List<PackageSelector> packageSelectors = request.getSelectorsByType(PackageSelector.class);
		assertThat(packageSelectors).extracting(PackageSelector::getPackageName).containsExactly(packageName);
	}

	@Test
	public void convertsAllOptionWithoutExplicitRootDirectories() {
		options.setRunAllTests(true);

		TestDiscoveryRequest request = convert();

		List<ClasspathSelector> classpathSelectors = request.getSelectorsByType(ClasspathSelector.class);
		// @formatter:off
		assertThat(classpathSelectors).extracting(ClasspathSelector::getClasspathRoot)
			.hasAtLeastOneElementOfType(File.class)
			.doesNotContainNull();
		// @formatter:on
	}

	@Test
	public void convertsAllOptionWithExplicitRootDirectories() {
		options.setRunAllTests(true);
		options.setArguments(asList(".", ".."));

		TestDiscoveryRequest request = convert();

		List<ClasspathSelector> classpathSelectors = request.getSelectorsByType(ClasspathSelector.class);
		// @formatter:off
		assertThat(classpathSelectors).extracting(ClasspathSelector::getClasspathRoot)
			.containsExactly(new File("."), new File(".."));
		// @formatter:on
	}

	@Test
	public void convertsClassnameFilterOption() {
		options.setRunAllTests(true);
		options.setClassnameFilter(".*Test");

		TestDiscoveryRequest request = convert();

		List<ClassFilter> filter = request.getDiscoveryFiltersByType(ClassFilter.class);
		assertThat(filter).hasSize(1);
		assertThat(filter.get(0).toString()).contains(".*Test");
	}

	@Test
	public void convertsTagFilterOption() {
		options.setRunAllTests(true);
		options.setTagsFilter(asList("fast", "medium", "slow"));
		options.setExcludeTags(asList("slow"));

		TestDiscoveryRequest request = convert();

		assertTrue(request.acceptDescriptor(testDescriptorWithTag("fast")));
		assertTrue(request.acceptDescriptor(testDescriptorWithTag("medium")));
		assertFalse(request.acceptDescriptor(testDescriptorWithTag("slow")));
		assertFalse(request.acceptDescriptor(testDescriptorWithTag("very slow")));
	}

	private TestDiscoveryRequest convert() {
		DiscoveryRequestCreator creator = new DiscoveryRequestCreator();
		return creator.toDiscoveryRequest(options);
	}

	private TestDescriptor testDescriptorWithTag(String tag) {
		TestDescriptor testDescriptor = mock(TestDescriptor.class);
		when(testDescriptor.getTags()).thenReturn(singleton(new TestTag(tag)));
		return testDescriptor;
	}
}
