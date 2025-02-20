/*
 * Copyright 2015 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.meta;

import static de.schauderhaft.degraph.check.JCheck.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import de.schauderhaft.degraph.configuration.NamedPattern;

import org.junit.gen5.api.Tag;
import org.junit.gen5.api.Test;

/**
 * Checks against dependency cycles on package and module level.
 *
 * <p>Modules in that sense are defined by the package name element after org.junit.gen5,
 * so "org.junit.gen5.console.ConsoleRunner" belongs to the module "console".
 */
@Tag("slow")
public class DependencyTests {

	@Test
	public void noCycles() {
		// we can't use noJar(), because with gradle the dependencies of other modules are
		// included as jar files in the path.
		//@formatter:off
		assertThat(
			classpath()
				.printTo("dependencies.graphml")
				.including("org.junit.gen5.**")
				.excluding("**Tests")
				.withSlicing("module",
					new NamedPattern("org.junit.gen5.engine.junit4.**", "junit4-engine"),
					new NamedPattern("org.junit.gen5.engine.junit5.**", "junit5-engine"),
					new NamedPattern("org.junit.gen5.engine.**", "engine-api"),
					"org.junit.gen5.(*).**"),
			is(violationFree()));
		//@formatter:on
	}
}
