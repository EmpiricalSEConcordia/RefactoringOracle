/*
 * Copyright 2012-2017 the original author or authors.
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

package org.springframework.boot.context.properties.source;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SpringIterableConfigurationPropertySource}.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 */
public class SpringIterableConfigurationPropertySourceTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void createWhenPropertySourceIsNullShouldThrowException() throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("PropertySource must not be null");
		new SpringIterableConfigurationPropertySource(null, mock(PropertyMapper.class));
	}

	@Test
	public void createWhenMapperIsNullShouldThrowException() throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("Mapper must not be null");
		new SpringIterableConfigurationPropertySource(
				mock(EnumerablePropertySource.class), null);
	}

	@Test
	public void iteratorShouldAdaptNames() throws Exception {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("key1", "value1");
		source.put("key2", "value2");
		source.put("key3", "value3");
		source.put("key4", "value4");
		EnumerablePropertySource<?> propertySource = new MapPropertySource("test",
				source);
		TestPropertyMapper mapper = new TestPropertyMapper();
		mapper.addFromPropertySource("key1", "my.key1");
		mapper.addFromPropertySource("key2", "my.key2a", "my.key2b");
		mapper.addFromPropertySource("key4", "my.key4");
		SpringIterableConfigurationPropertySource adapter = new SpringIterableConfigurationPropertySource(
				propertySource, mapper);
		assertThat(adapter.iterator()).extracting(Object::toString)
				.containsExactly("my.key1", "my.key2a", "my.key2b", "my.key4");
	}

	@Test
	public void getValueShouldUseDirectMapping() throws Exception {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("key1", "value1");
		source.put("key2", "value2");
		source.put("key3", "value3");
		EnumerablePropertySource<?> propertySource = new MapPropertySource("test",
				source);
		TestPropertyMapper mapper = new TestPropertyMapper();
		ConfigurationPropertyName name = ConfigurationPropertyName.of("my.key");
		mapper.addFromConfigurationProperty(name, "key2");
		SpringIterableConfigurationPropertySource adapter = new SpringIterableConfigurationPropertySource(
				propertySource, mapper);
		assertThat(adapter.getConfigurationProperty(name).getValue()).isEqualTo("value2");
	}

	@Test
	public void getValueShouldUseEnumerableMapping() throws Exception {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("key1", "value1");
		source.put("key2", "value2");
		source.put("key3", "value3");
		EnumerablePropertySource<?> propertySource = new MapPropertySource("test",
				source);
		TestPropertyMapper mapper = new TestPropertyMapper();
		mapper.addFromPropertySource("key1", "my.missing");
		mapper.addFromPropertySource("key2", "my.k-e-y");
		SpringIterableConfigurationPropertySource adapter = new SpringIterableConfigurationPropertySource(
				propertySource, mapper);
		ConfigurationPropertyName name = ConfigurationPropertyName.of("my.key");
		assertThat(adapter.getConfigurationProperty(name).getValue()).isEqualTo("value2");
	}

	@Test
	public void getValueShouldUseExtractor() throws Exception {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("key", "value");
		EnumerablePropertySource<?> propertySource = new MapPropertySource("test",
				source);
		TestPropertyMapper mapper = new TestPropertyMapper();
		ConfigurationPropertyName name = ConfigurationPropertyName.of("my.key");
		mapper.addFromConfigurationProperty(name, "key",
				(value) -> value.toString().replace("ue", "let"));
		SpringIterableConfigurationPropertySource adapter = new SpringIterableConfigurationPropertySource(
				propertySource, mapper);
		assertThat(adapter.getConfigurationProperty(name).getValue()).isEqualTo("vallet");
	}

	@Test
	public void getValueOrigin() throws Exception {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("key", "value");
		EnumerablePropertySource<?> propertySource = new MapPropertySource("test",
				source);
		TestPropertyMapper mapper = new TestPropertyMapper();
		ConfigurationPropertyName name = ConfigurationPropertyName.of("my.key");
		mapper.addFromConfigurationProperty(name, "key");
		SpringIterableConfigurationPropertySource adapter = new SpringIterableConfigurationPropertySource(
				propertySource, mapper);
		assertThat(adapter.getConfigurationProperty(name).getOrigin().toString())
				.isEqualTo("\"key\" from property source \"test\"");
	}

	@Test
	public void getValueWhenOriginCapableShouldIncludeSourceOrigin() throws Exception {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("key", "value");
		EnumerablePropertySource<?> propertySource = new OriginCapablePropertySource<>(
				new MapPropertySource("test", source));
		TestPropertyMapper mapper = new TestPropertyMapper();
		ConfigurationPropertyName name = ConfigurationPropertyName.of("my.key");
		mapper.addFromConfigurationProperty(name, "key");
		SpringIterableConfigurationPropertySource adapter = new SpringIterableConfigurationPropertySource(
				propertySource, mapper);
		assertThat(adapter.getConfigurationProperty(name).getOrigin().toString())
				.isEqualTo("TestOrigin key");
	}

	@Test
	public void containsDescendantOfShouldCheckSourceNames() throws Exception {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("foo.bar", "value");
		source.put("faf", "value");
		EnumerablePropertySource<?> propertySource = new OriginCapablePropertySource<>(
				new MapPropertySource("test", source));
		SpringIterableConfigurationPropertySource adapter = new SpringIterableConfigurationPropertySource(
				propertySource, DefaultPropertyMapper.INSTANCE);
		assertThat(adapter.containsDescendantOf(ConfigurationPropertyName.of("foo")))
				.contains(true);
		assertThat(adapter.containsDescendantOf(ConfigurationPropertyName.of("faf")))
				.contains(false);
		assertThat(adapter.containsDescendantOf(ConfigurationPropertyName.of("fof")))
				.contains(false);
	}

	/**
	 * Test {@link PropertySource} that's also a {@link OriginLookup}.
	 */
	private static class OriginCapablePropertySource<T>
			extends EnumerablePropertySource<T> implements OriginLookup<String> {

		private final EnumerablePropertySource<T> propertySource;

		OriginCapablePropertySource(EnumerablePropertySource<T> propertySource) {
			super(propertySource.getName(), propertySource.getSource());
			this.propertySource = propertySource;
		}

		@Override
		public Object getProperty(String name) {
			return this.propertySource.getProperty(name);
		}

		@Override
		public String[] getPropertyNames() {
			return this.propertySource.getPropertyNames();
		}

		@Override
		public Origin getOrigin(String name) {
			return new Origin() {

				@Override
				public String toString() {
					return "TestOrigin " + name;
				}

			};
		}

	}

}
