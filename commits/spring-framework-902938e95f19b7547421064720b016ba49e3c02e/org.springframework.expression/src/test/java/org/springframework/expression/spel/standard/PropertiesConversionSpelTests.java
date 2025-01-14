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

package org.springframework.expression.spel.standard;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * @author Mark Fisher
 */
public class PropertiesConversionSpelTests {

	private static final SpelExpressionParser parser = new SpelExpressionParser();

	@Test
	public void props() {
		Properties props = new Properties();
		props.setProperty("x", "1");
		props.setProperty("y", "2");
		props.setProperty("z", "3");
		Expression expression = parser.parseExpression("foo(#props)");
		StandardEvaluationContext context = new StandardEvaluationContext();
		context.setVariable("props", props);
		String result = expression.getValue(context, new TestBean(), String.class);
		assertEquals("123", result);
	}

	@Test
	public void mapWithAllStringValues() {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("x", "1");
		map.put("y", "2");
		map.put("z", "3");
		Expression expression = parser.parseExpression("foo(#props)");
		StandardEvaluationContext context = new StandardEvaluationContext();
		context.setVariable("props", map);
		String result = expression.getValue(context, new TestBean(), String.class);
		assertEquals("123", result);
	}

	@Test // questionable, but this passes with 3.0.2.RELEASE
	public void mapWithNonStringValue() {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("x", "1");
		map.put("y", 2);
		map.put("z", "3");
		Expression expression = parser.parseExpression("foo(#props)");
		StandardEvaluationContext context = new StandardEvaluationContext();
		context.setVariable("props", map);
		String result = expression.getValue(context, new TestBean(), String.class);
		assertEquals("1null3", result);
	}


	private static class TestBean {

		@SuppressWarnings("unused")
		public String foo(Properties props) {
			return props.getProperty("x") + props.getProperty("y") + props.getProperty("z");
		}
	}

}
