/*
 * Copyright 2015-2017 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.api;

import static org.junit.platform.commons.meta.API.Status.STABLE;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.platform.commons.meta.API;

/**
 * {@code @Tags} is a container for one or more {@code @Tag} declarations.
 *
 * <p>Note, however, that use of the {@code @Tags} container is completely
 * optional since {@code @Tag} is a {@linkplain java.lang.annotation.Repeatable
 * repeatable} annotation.
 *
 * @since 5.0
 * @see Tag
 * @see java.lang.annotation.Repeatable
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@API(status = STABLE, since = "5.0")
public @interface Tags {

	/**
	 * An array of one or more {@link Tag Tags}.
	 */
	Tag[] value();

}
