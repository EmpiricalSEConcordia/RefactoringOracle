/*
 * Copyright 2002-2010 the original author or authors.
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

package org.springframework.web.context.support;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.junit.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.DefaultEnvironment;
import org.springframework.core.env.PropertySource;


public class DefaultWebEnvironmentTests {

	@Test
	public void propertySourceOrder() {
		ConfigurableEnvironment env = new DefaultWebEnvironment();
		List<PropertySource<?>> sources = env.getPropertySources().asList();
		assertThat(sources.size(), is(4));
		assertThat(sources.get(0).getName(), equalTo(DefaultWebEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME));
		assertThat(sources.get(1).getName(), equalTo(DefaultWebEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME));
		assertThat(sources.get(2).getName(), equalTo(DefaultEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME));
		assertThat(sources.get(3).getName(), equalTo(DefaultEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME));
	}
}
