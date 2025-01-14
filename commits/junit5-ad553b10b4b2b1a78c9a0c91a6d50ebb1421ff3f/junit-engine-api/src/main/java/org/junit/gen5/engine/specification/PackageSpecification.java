/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.specification;

import org.junit.gen5.engine.DiscoverySelector;
import org.junit.gen5.engine.DiscoverySelectorVisitor;

public class PackageSpecification implements DiscoverySelector {
	private final String packageName;

	public PackageSpecification(String packageName) {
		this.packageName = packageName;
	}

	@Override
	public void accept(DiscoverySelectorVisitor visitor) {
		visitor.visitPackage(packageName);
	}

	public String getPackageName() {
		return packageName;
	}
}
