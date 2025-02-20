/*
 * Copyright 2012-2013 the original author or authors.
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
package org.springframework.bootstrap.actuate.autoconfigure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.bootstrap.actuate.audit.AuditEventRepository;
import org.springframework.bootstrap.actuate.audit.InMemoryAuditEventRepository;
import org.springframework.bootstrap.actuate.audit.listener.AuditListener;
import org.springframework.bootstrap.actuate.security.AuthenticationAuditListener;
import org.springframework.bootstrap.actuate.security.AuthorizationAuditListener;
import org.springframework.bootstrap.context.annotation.ConditionalOnClass;
import org.springframework.bootstrap.context.annotation.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Dave Syer
 * 
 */
@Configuration
public class AuditConfiguration {

	@Autowired(required = false)
	private AuditEventRepository auditEventRepository = new InMemoryAuditEventRepository();

	@ConditionalOnMissingBean(AuditEventRepository.class)
	protected static class AuditEventRepositoryConfiguration {
		@Bean
		public AuditEventRepository auditEventRepository() throws Exception {
			return new InMemoryAuditEventRepository();
		}
	}

	@Bean
	public AuditListener auditListener() throws Exception {
		return new AuditListener(this.auditEventRepository);
	}

	@Bean
	@ConditionalOnClass(name = "org.springframework.security.authentication.event.AbstractAuthenticationEvent")
	public AuthenticationAuditListener authenticationAuditListener() throws Exception {
		return new AuthenticationAuditListener();
	}

	@Bean
	@ConditionalOnClass(name = "org.springframework.security.access.event.AbstractAuthorizationEvent")
	public AuthorizationAuditListener authorizationAuditListener() throws Exception {
		return new AuthorizationAuditListener();
	}

}
