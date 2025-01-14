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

package org.springframework.ui.format.support;

import java.beans.PropertyEditorSupport;
import java.text.ParseException;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.ui.format.Formatter;
import org.springframework.util.Assert;

/**
 * Adapter that exposes a {@link java.beans.PropertyEditor} for any given
 * {@link org.springframework.ui.format.Formatter}, retrieving the current
 * Locale from {@link org.springframework.context.i18n.LocaleContextHolder}.
 *
 * @author Juergen Hoeller
 * @since 3.0
 */
public class FormattingPropertyEditorAdapter extends PropertyEditorSupport {

	private final Formatter<Object> formatter;


	/**
	 * Create a new FormattingPropertyEditorAdapter for the given Formatter.
	 * @param formatter the Formatter to wrap
	 */
	public FormattingPropertyEditorAdapter(Formatter<Object> formatter) {
		Assert.notNull(formatter, "Formatter must not be null");
		this.formatter = formatter;
	}


	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		try {
			setValue(this.formatter.parse(text, LocaleContextHolder.getLocale()));
		}
		catch (ParseException ex) {
			throw new IllegalArgumentException("Failed to parse formatted value", ex);
		}
	}

	@Override
	public String getAsText() {
		return this.formatter.format(getValue(), LocaleContextHolder.getLocale());
	}

}
