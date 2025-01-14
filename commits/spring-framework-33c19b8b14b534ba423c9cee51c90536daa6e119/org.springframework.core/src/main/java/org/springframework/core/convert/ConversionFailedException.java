/*
 * Copyright 2002-2009 the original author or authors.
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

package org.springframework.core.convert;

import org.springframework.core.style.StylerUtils;
import org.springframework.util.ClassUtils;

/**
 * Thrown when an attempt to execute a type conversion fails.
 *
 * @author Keith Donald
 * @since 3.0
 */
public class ConversionFailedException extends ConversionException {

	private Class<?> sourceType;

	private TypeDescriptor targetType;

	/**
	 * Create a new conversion exception.
	 * @param value the value we tried to convert
	 * @param sourceType the value's original type
	 * @param targetType the value's target type
	 * @param cause the cause of the conversion failure
	 */
	public ConversionFailedException(Class<?> sourceType, TypeDescriptor targetType, Object value, Throwable cause) {
		super(buildDefaultMessage(value, sourceType, targetType, cause), cause);
		this.sourceType = sourceType;
		this.targetType = targetType;
	}

	/**
	 * Return the source type we tried to convert the value from.
	 */
	public Class<?> getSourceType() {
		return this.sourceType;
	}

	/**
	 * Returns the target type we tried to convert the value to.
	 */
	public TypeDescriptor getTargetType() {
		return this.targetType;
	}


	private static String buildDefaultMessage(Object value, Class<?> sourceType, TypeDescriptor targetType, Throwable cause) {
		return "Unable to convert value " + StylerUtils.style(value) + " from type '" +
				ClassUtils.getQualifiedName(sourceType) + "' to type '" +
				ClassUtils.getQualifiedName(targetType.getType()) + "'; reason = '" + cause.getMessage() + "'";
	}

}