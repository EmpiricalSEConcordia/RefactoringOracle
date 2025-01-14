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

package org.springframework.boot.cloudfoundry;

import org.junit.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.test.EnvironmentTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests for {@link VcapApplicationListener}.
 *
 * @author Dave Syer
 */
public class VcapApplicationListenerTests {

	private final VcapApplicationListener initializer = new VcapApplicationListener();

	private final ConfigurableApplicationContext context = new AnnotationConfigApplicationContext();

	private final ApplicationEnvironmentPreparedEvent event = new ApplicationEnvironmentPreparedEvent(
			new SpringApplication(), new String[0], this.context.getEnvironment());

	@Test
	public void testApplicationProperties() {
		EnvironmentTestUtils.addEnvironment(this.context,
				"VCAP_APPLICATION:{\"application_users\":[],"
						+ "\"instance_id\":\"bb7935245adf3e650dfb7c58a06e9ece\","
						+ "\"instance_index\":0,\"version\":\"3464e092-1c13-462e-a47c-807c30318a50\","
						+ "\"name\":\"foo\",\"uris\":[\"foo.cfapps.io\"],"
						+ "\"started_at\":\"2013-05-29 02:37:59 +0000\","
						+ "\"started_at_timestamp\":1369795079,"
						+ "\"host\":\"0.0.0.0\",\"port\":61034,"
						+ "\"limits\":{\"mem\":128,\"disk\":1024,\"fds\":16384},"
						+ "\"version\":\"3464e092-1c13-462e-a47c-807c30318a50\","
						+ "\"name\":\"dsyerenv\",\"uris\":[\"dsyerenv.cfapps.io\"],"
						+ "\"users\":[],\"start\":\"2013-05-29 02:37:59 +0000\","
						+ "\"state_timestamp\":1369795079}");
		this.initializer.onApplicationEvent(this.event);
		assertEquals("bb7935245adf3e650dfb7c58a06e9ece", this.context.getEnvironment()
				.getProperty("vcap.application.instance_id"));
	}

	@Test
	public void testApplicationUris() {
		EnvironmentTestUtils.addEnvironment(this.context,
				"VCAP_APPLICATION:{\"instance_id\":\"bb7935245adf3e650dfb7c58a06e9ece\",\"instance_index\":0,\"uris\":[\"foo.cfapps.io\"]}");
		this.initializer.onApplicationEvent(this.event);
		assertEquals("foo.cfapps.io",
				this.context.getEnvironment().getProperty("vcap.application.uris[0]"));
	}

	@Test
	public void testUnparseableApplicationProperties() {
		EnvironmentTestUtils.addEnvironment(this.context, "VCAP_APPLICATION:");
		this.initializer.onApplicationEvent(this.event);
		assertNull(getProperty("vcap"));
	}

	@Test
	public void testNullApplicationProperties() {
		EnvironmentTestUtils.addEnvironment(this.context,
				"VCAP_APPLICATION:{\"application_users\":null,"
						+ "\"instance_id\":\"bb7935245adf3e650dfb7c58a06e9ece\","
						+ "\"instance_index\":0,\"version\":\"3464e092-1c13-462e-a47c-807c30318a50\","
						+ "\"name\":\"foo\",\"uris\":[\"foo.cfapps.io\"],"
						+ "\"started_at\":\"2013-05-29 02:37:59 +0000\","
						+ "\"started_at_timestamp\":1369795079,"
						+ "\"host\":\"0.0.0.0\",\"port\":61034,"
						+ "\"limits\":{\"mem\":128,\"disk\":1024,\"fds\":16384},"
						+ "\"version\":\"3464e092-1c13-462e-a47c-807c30318a50\","
						+ "\"name\":\"dsyerenv\",\"uris\":[\"dsyerenv.cfapps.io\"],"
						+ "\"users\":[],\"start\":\"2013-05-29 02:37:59 +0000\","
						+ "\"state_timestamp\":1369795079}");
		this.initializer.onApplicationEvent(this.event);
		assertNull(getProperty("vcap"));
	}

	@Test
	public void testServiceProperties() {
		EnvironmentTestUtils.addEnvironment(this.context,
				"VCAP_SERVICES:{\"rds-mysql-n/a\":[{"
						+ "\"name\":\"mysql\",\"label\":\"rds-mysql-n/a\","
						+ "\"plan\":\"10mb\",\"credentials\":{"
						+ "\"name\":\"d04fb13d27d964c62b267bbba1cffb9da\","
						+ "\"hostname\":\"mysql-service-public.clqg2e2w3ecf.us-east-1.rds.amazonaws.com\","
						+ "\"ssl\":true,\"location\":null,"
						+ "\"host\":\"mysql-service-public.clqg2e2w3ecf.us-east-1.rds.amazonaws.com\","
						+ "\"port\":3306,\"user\":\"urpRuqTf8Cpe6\",\"username\":"
						+ "\"urpRuqTf8Cpe6\",\"password\":\"pxLsGVpsC9A5S\"}}]}");
		this.initializer.onApplicationEvent(this.event);
		assertEquals("mysql", getProperty("vcap.services.mysql.name"));
		assertEquals("3306", getProperty("vcap.services.mysql.credentials.port"));
		assertEquals("true", getProperty("vcap.services.mysql.credentials.ssl"));
		assertEquals("", getProperty("vcap.services.mysql.credentials.location"));
	}

	@Test
	public void testServicePropertiesWithoutNA() {
		EnvironmentTestUtils.addEnvironment(this.context,
				"VCAP_SERVICES:{\"rds-mysql\":[{"
						+ "\"name\":\"mysql\",\"label\":\"rds-mysql\",\"plan\":\"10mb\","
						+ "\"credentials\":{\"name\":\"d04fb13d27d964c62b267bbba1cffb9da\","
						+ "\"hostname\":\"mysql-service-public.clqg2e2w3ecf.us-east-1.rds.amazonaws.com\","
						+ "\"host\":\"mysql-service-public.clqg2e2w3ecf.us-east-1.rds.amazonaws.com\","
						+ "\"port\":3306,\"user\":\"urpRuqTf8Cpe6\","
						+ "\"username\":\"urpRuqTf8Cpe6\","
						+ "\"password\":\"pxLsGVpsC9A5S\"}}]}");
		this.initializer.onApplicationEvent(this.event);
		assertEquals("mysql", getProperty("vcap.services.mysql.name"));
		assertEquals("3306", getProperty("vcap.services.mysql.credentials.port"));
	}

	private String getProperty(String key) {
		return this.context.getEnvironment().getProperty(key);
	}
}
