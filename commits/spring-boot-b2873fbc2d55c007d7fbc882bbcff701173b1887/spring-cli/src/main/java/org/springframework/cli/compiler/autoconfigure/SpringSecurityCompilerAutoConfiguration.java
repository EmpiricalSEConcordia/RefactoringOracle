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

package org.springframework.cli.compiler.autoconfigure;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.springframework.cli.compiler.AstUtils;
import org.springframework.cli.compiler.CompilerAutoConfiguration;
import org.springframework.cli.compiler.DependencyCustomizer;

/**
 * {@link CompilerAutoConfiguration} for Spring Security.
 * 
 * @author Dave Syer
 */
public class SpringSecurityCompilerAutoConfiguration extends CompilerAutoConfiguration {

	@Override
	public boolean matches(ClassNode classNode) {
		return AstUtils.hasAtLeastOneAnnotation(classNode, "EnableWebSecurity");
	}

	@Override
	public void applyDependencies(DependencyCustomizer dependencies) {
		dependencies
				.ifAnyMissingClasses(
						"org.springframework.security.config.annotation.web.configuration.EnableWebSecurity")
				.add("org.springframework.security", "spring-security-config",
						dependencies.getProperty("spring.security.version"))
				.add("org.springframework.security", "spring-security-web",
						dependencies.getProperty("spring.security.version"), false);
	}

	@Override
	public void applyImports(ImportCustomizer imports) {
		imports.addImports("org.springframework.security.core.Authentication",
				"org.springframework.security.core.authority.AuthorityUtils")
				.addStarImports(
						"org.springframework.security.config.annotation.web.configuration",
						"org.springframework.security.authentication",
						"org.springframework.security.config.annotation.web",
						"org.springframework.security.config.annotation.web.builders");
	}
}
