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

package org.springframework.core.convert.support;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.util.StringUtils;

/**
 * Converts a comma-delimited String to a Collection.
 *
 * @author Keith Donald
 * @since 3.0
 */
final class StringToCollectionConverter implements ConditionalGenericConverter {

	private final ConversionService conversionService;

	public StringToCollectionConverter(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	public Set<ConvertiblePair> getConvertibleTypes() {
		return Collections.singleton(new ConvertiblePair(String.class, Collection.class));
	}

	public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (targetType.getElementType() == null) {
			return true;
		}
		return this.conversionService.canConvert(sourceType, targetType.getElementType());
	}

	@SuppressWarnings("unchecked")
	public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {		
		if (source == null) {
			return null;
		}
		String string = (String) source;
		String[] fields = StringUtils.commaDelimitedListToStringArray(string);
		Collection<Object> target = CollectionFactory.createCollection(targetType.getType(), fields.length);
		if (targetType.getElementType() == null) {
			for (String field : fields) {
				target.add(field.trim());
			}						
		} else {
			for (String field : fields) {
				Object targetElement = this.conversionService.convert(field.trim(), sourceType, targetType.getElementType());
				target.add(targetElement);
			}			
		}
		return target;
	}

}