/*
 * Copyright 2012-2014 the original author or authors.
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

package org.springframework.boot;

import org.junit.Test;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Tests to reproduce reported issues.
 *
 * @author Phillip Webb
 * @author Dave Syer
 */
public class ReproTests {

	@Test
	public void enableProfileViaApplicationProperties() throws Exception {
		// gh-308
		SpringApplication application = new SpringApplication(Config.class);

		application.setWebEnvironment(false);
		ConfigurableApplicationContext context = application.run(
				"--spring.config.name=enableprofileviaapplicationproperties",
				"--spring.profiles.active=dev");
		assertThat(context.getEnvironment().acceptsProfiles("dev"), equalTo(true));
		assertThat(context.getEnvironment().acceptsProfiles("a"), equalTo(true));
	}

	@Test
	public void activeProfilesWithYamlAndCommandLine() throws Exception {
		// gh-322, gh-342
		SpringApplication application = new SpringApplication(Config.class);
		application.setWebEnvironment(false);
		String configName = "--spring.config.name=activeprofilerepro";
		assertVersionProperty(application.run(configName, "--spring.profiles.active=B"),
				"B", "B");
	}

	@Test
	public void activeProfilesWithYamlOnly() throws Exception {
		// gh-322, gh-342
		SpringApplication application = new SpringApplication(Config.class);
		application.setWebEnvironment(false);
		String configName = "--spring.config.name=activeprofilerepro";
		assertVersionProperty(application.run(configName), "B", "B");
	}

	@Test
	public void orderActiveProfilesWithYamlOnly() throws Exception {
		// gh-322, gh-342
		SpringApplication application = new SpringApplication(Config.class);
		application.setWebEnvironment(false);
		String configName = "--spring.config.name=activeprofilerepro-ordered";
		assertVersionProperty(application.run(configName), "B", "A", "B");
	}

	@Test
	public void commandLineBeatsProfilesWithYaml() throws Exception {
		// gh-322, gh-342
		SpringApplication application = new SpringApplication(Config.class);
		application.setWebEnvironment(false);
		String configName = "--spring.config.name=activeprofilerepro";
		assertVersionProperty(application.run(configName, "--spring.profiles.active=C"),
				"C", "C");
	}

	@Test
	public void orderProfilesWithYaml() throws Exception {
		// gh-322, gh-342
		SpringApplication application = new SpringApplication(Config.class);
		application.setWebEnvironment(false);
		String configName = "--spring.config.name=activeprofilerepro";
		assertVersionProperty(application.run(configName, "--spring.profiles.active=A,C"),
				"C", "A", "C");
	}

	@Test
	public void reverseOrderOfProfilesWithYaml() throws Exception {
		// gh-322, gh-342
		SpringApplication application = new SpringApplication(Config.class);
		application.setWebEnvironment(false);
		String configName = "--spring.config.name=activeprofilerepro";
		assertVersionProperty(application.run(configName, "--spring.profiles.active=C,A"),
				"A", "C", "A");
	}

	@Test
	public void activeProfilesWithYamlAndCommandLineAndNoOverride() throws Exception {
		// gh-322, gh-342
		SpringApplication application = new SpringApplication(Config.class);
		application.setWebEnvironment(false);
		String configName = "--spring.config.name=activeprofilerepro-without-override";
		assertVersionProperty(application.run(configName, "--spring.profiles.active=B"),
				"B", "B");
	}

	@Test
	public void activeProfilesWithYamlOnlyAndNoOverride() throws Exception {
		// gh-322, gh-342
		SpringApplication application = new SpringApplication(Config.class);
		application.setWebEnvironment(false);
		String configName = "--spring.config.name=activeprofilerepro-without-override";
		assertVersionProperty(application.run(configName), null);
	}

	@Test
	public void commandLineBeatsProfilesWithYamlAndNoOverride() throws Exception {
		// gh-322, gh-342
		SpringApplication application = new SpringApplication(Config.class);
		application.setWebEnvironment(false);
		String configName = "--spring.config.name=activeprofilerepro-without-override";
		assertVersionProperty(application.run(configName, "--spring.profiles.active=C"),
				"C", "C");
	}

	@Test
	public void orderProfilesWithYamlAndNoOverride() throws Exception {
		// gh-322, gh-342
		SpringApplication application = new SpringApplication(Config.class);
		application.setWebEnvironment(false);
		String configName = "--spring.config.name=activeprofilerepro-without-override";
		assertVersionProperty(application.run(configName, "--spring.profiles.active=A,C"),
				"C", "A", "C");
	}

	@Test
	public void reverseOrderOfProfilesWithYamlAndNoOverride() throws Exception {
		// gh-322, gh-342
		SpringApplication application = new SpringApplication(Config.class);
		application.setWebEnvironment(false);
		String configName = "--spring.config.name=activeprofilerepro-without-override";
		assertVersionProperty(application.run(configName, "--spring.profiles.active=C,A"),
				"A", "C", "A");
	}

	private void assertVersionProperty(ConfigurableApplicationContext context,
			String expectedVersion, String... expectedActiveProfiles) {
		assertThat(context.getEnvironment().getActiveProfiles(),
				equalTo(expectedActiveProfiles));
		assertThat("version mismatch", context.getEnvironment().getProperty("version"),
				equalTo(expectedVersion));
		context.close();
	}

	@Configuration
	public static class Config {

	}
}
