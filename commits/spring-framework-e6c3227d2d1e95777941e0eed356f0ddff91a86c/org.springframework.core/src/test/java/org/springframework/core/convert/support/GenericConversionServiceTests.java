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

package org.springframework.core.convert.support;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;

/**
 * @author Keith Donald
 */
public class GenericConversionServiceTests {

	private GenericConversionService conversionService = new GenericConversionService();

	@Test
	public void executeConversion() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		assertEquals(new Integer(3), conversionService.convert("3", Integer.class));
	}

	@Test
	public void executeConversionNullSource() {
		assertEquals(null, conversionService.convert(null, Integer.class));
	}

	@Test
	public void executeCompatibleSource() {
		assertEquals(Boolean.FALSE, conversionService.convert(false, boolean.class));
	}

	@Test
	public void converterNotFound() {
		try {
			conversionService.convert("3", Integer.class);
			fail("Should have thrown an exception");
		} catch (ConverterNotFoundException e) {
		}
	}

	@Test
	public void addConverterNoSourceTargetClassInfoAvailable() {
		try {
			conversionService.addConverter(new Converter() {
				public Object convert(Object source) {
					return source;
				}
			});
			fail("Should have failed");
		} catch (IllegalArgumentException e) {

		}
	}

	@Test
	public void convertNull() {
		assertNull(conversionService.convert(null, Integer.class));
	}

	@Test(expected = IllegalArgumentException.class)
	public void convertNullTargetClass() {
		assertEquals("3", conversionService.convert("3", (Class<?>) null));
	}

	@Test
	public void convertNullTypeDescriptor() {
		assertEquals(null, conversionService.convert(3, TypeDescriptor.valueOf(String.class), TypeDescriptor.NULL));
	}

	@Test
	public void convertWrongTypeArgument() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		try {
			conversionService.convert("BOGUS", Integer.class);
			fail("Should have failed");
		} catch (ConversionFailedException e) {

		}
	}

	@Test
	public void convertSuperSourceType() {
		conversionService.addConverter(new Converter<CharSequence, Integer>() {
			public Integer convert(CharSequence source) {
				return Integer.valueOf(source.toString());
			}
		});
		Integer result = conversionService.convert("3", Integer.class);
		assertEquals(new Integer(3), result);
	}

	@Test
	public void convertObjectToPrimitive() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		Integer three = conversionService.convert("3", int.class);
		assertEquals(3, three.intValue());
	}

	@Test
	public void convertArrayToArray() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		Integer[] result = conversionService.convert(new String[] { "1", "2", "3" }, Integer[].class);
		assertEquals(new Integer(1), result[0]);
		assertEquals(new Integer(2), result[1]);
		assertEquals(new Integer(3), result[2]);
	}

	@Test
	public void convertArrayToPrimitiveArray() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		int[] result = conversionService.convert(new String[] { "1", "2", "3" }, int[].class);
		assertEquals(1, result[0]);
		assertEquals(2, result[1]);
		assertEquals(3, result[2]);
	}

	@Test
	public void convertArrayToArrayAssignable() {
		int[] result = conversionService.convert(new int[] { 1, 2, 3 }, int[].class);
		assertEquals(1, result[0]);
		assertEquals(2, result[1]);
		assertEquals(3, result[2]);
	}

	@Test
	public void convertArrayToListInterface() {
		List<?> result = conversionService.convert(new String[] { "1", "2", "3" }, List.class);
		assertEquals("1", result.get(0));
		assertEquals("2", result.get(1));
		assertEquals("3", result.get(2));
	}

	public List<Integer> genericList = new ArrayList<Integer>();

	@Test
	public void convertArrayToListGenericTypeConversion() throws Exception {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		List<Integer> result = (List<Integer>) conversionService.convert(new String[] { "1", "2", "3" }, TypeDescriptor
				.valueOf(String[].class), new TypeDescriptor(getClass().getDeclaredField("genericList")));
		assertEquals(new Integer("1"), result.get(0));
		assertEquals(new Integer("2"), result.get(1));
		assertEquals(new Integer("3"), result.get(2));
	}

	@Test
	public void convertArrayToListImpl() {
		LinkedList<?> result = conversionService.convert(new String[] { "1", "2", "3" }, LinkedList.class);
		assertEquals("1", result.get(0));
		assertEquals("2", result.get(1));
		assertEquals("3", result.get(2));
	}

	@Test(expected = ConversionFailedException.class)
	public void convertArrayToAbstractList() {
		conversionService.convert(new String[] { "1", "2", "3" }, AbstractList.class);
	}

	@Test
	public void convertArrayToString() {
		String result = conversionService.convert(new String[] { "1", "2", "3" }, String.class);
		assertEquals("1,2,3", result);
	}

	@Test
	public void convertArrayToStringWithElementConversion() {
		conversionService.addConverter(new ObjectToStringConverter());
		String result = conversionService.convert(new Integer[] { 1, 2, 3 }, String.class);
		assertEquals("1,2,3", result);
	}

	@Test
	public void convertEmptyArrayToString() {
		String result = conversionService.convert(new String[0], String.class);
		assertEquals("", result);
	}
	
	@Test
	public void convertArrayToObject() {
		Object[] array = new Object[] { 3L };
		Object result = conversionService.convert(array, Object.class);
		assertEquals(3L, result);
	}

	@Test
	public void convertArrayToObjectWithElementConversion() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		String[] array = new String[] { "3" };
		Integer result = conversionService.convert(array, Integer.class);
		assertEquals(new Integer(3), result);
	}

	@Test
	public void convertCollectionToArray() {
		List<String> list = new ArrayList<String>();
		list.add("1");
		list.add("2");
		list.add("3");
		String[] result = conversionService.convert(list, String[].class);
		assertEquals("1", result[0]);
		assertEquals("2", result[1]);
		assertEquals("3", result[2]);
	}

	@Test
	public void convertCollectionToArrayWithElementConversion() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		List<String> list = new ArrayList<String>();
		list.add("1");
		list.add("2");
		list.add("3");
		Integer[] result = conversionService.convert(list, Integer[].class);
		assertEquals(new Integer(1), result[0]);
		assertEquals(new Integer(2), result[1]);
		assertEquals(new Integer(3), result[2]);
	}

	@Test
	public void convertCollectionToCollection() throws Exception {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		Set<String> foo = new LinkedHashSet<String>();
		foo.add("1");
		foo.add("2");
		foo.add("3");
		List<Integer> bar = (List<Integer>) conversionService.convert(foo, TypeDescriptor.valueOf(List.class),
				new TypeDescriptor(getClass().getField("genericList")));
		assertEquals(new Integer(1), bar.get(0));
		assertEquals(new Integer(2), bar.get(1));
		assertEquals(new Integer(3), bar.get(2));
	}

	@Test
	public void convertCollectionToString() {
		List<String> list = Arrays.asList(new String[] { "foo", "bar" });
		String result = conversionService.convert(list, String.class);
		assertEquals("foo,bar", result);
	}

	@Test
	public void convertCollectionToStringWithElementConversion() throws Exception {
		conversionService.addConverter(new ObjectToStringConverter());
		List<Integer> list = Arrays.asList(new Integer[] { 3, 5 });
		String result = (String) conversionService.convert(list, new TypeDescriptor(getClass().getField("genericList")), TypeDescriptor.valueOf(String.class));
		assertEquals("3,5", result);
	}

	@Test
	public void convertCollectionToObject() {
		List<Long> list = Collections.singletonList(3L);
		Long result = conversionService.convert(list, Long.class);
		assertEquals(new Long(3), result);
	}

	@Test
	public void convertCollectionToObjectWithElementConversion() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		List<String> list = Collections.singletonList("3");
		Integer result = conversionService.convert(list, Integer.class);
		assertEquals(new Integer(3), result);
	}

	public Map<Integer, FooEnum> genericMap = new HashMap<Integer, FooEnum>();

	@Test
	public void convertMapToMap() throws Exception {
		Map<String, String> foo = new HashMap<String, String>();
		foo.put("1", "BAR");
		foo.put("2", "BAZ");
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		conversionService.addConverterFactory(new StringToEnumConverterFactory());
		Map<String, FooEnum> map = (Map<String, FooEnum>) conversionService.convert(foo, TypeDescriptor
				.valueOf(Map.class), new TypeDescriptor(getClass().getField("genericMap")));
		assertEquals(map.get(1), FooEnum.BAR);
		assertEquals(map.get(2), FooEnum.BAZ);
	}

	@Test
	public void convertStringToArray() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		String[] result = conversionService.convert("1,2,3", String[].class);
		assertEquals(3, result.length);
		assertEquals("1", result[0]);
		assertEquals("2", result[1]);
		assertEquals("3", result[2]);
	}

	@Test
	public void convertStringToArrayWithElementConversion() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		Integer[] result = conversionService.convert("1,2,3", Integer[].class);
		assertEquals(3, result.length);
		assertEquals(new Integer(1), result[0]);
		assertEquals(new Integer(2), result[1]);
		assertEquals(new Integer(3), result[2]);
	}

	@Test
	public void convertEmptyStringToArray() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		String[] result = conversionService.convert("", String[].class);
		assertEquals(0, result.length);
	}

	@Test
	public void convertStringToCollection() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		List result = conversionService.convert("1,2,3", List.class);
		assertEquals(3, result.size());
		assertEquals("1", result.get(0));
		assertEquals("2", result.get(1));
		assertEquals("3", result.get(2));
	}

	@Test
	public void convertStringToCollectionWithElementConversion() throws Exception {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		List result = (List) conversionService.convert("1,2,3", TypeDescriptor.valueOf(String.class), new TypeDescriptor(getClass().getField("genericList")));
		assertEquals(3, result.size());
		assertEquals(new Integer(1), result.get(0));
		assertEquals(new Integer(2), result.get(1));
		assertEquals(new Integer(3), result.get(2));
	}

	@Test
	public void convertEmptyStringToCollection() {
		conversionService.addConverterFactory(new StringToNumberConverterFactory());
		String[] result = conversionService.convert("", String[].class);
		assertEquals(0, result.length);
	}

	@Test
	public void convertObjectToCollection() {
		List<String> result = (List<String>) conversionService.convert(3L, List.class);
		assertEquals(1, result.size());
		assertEquals(3L, result.get(0));
	}

	@Test
	public void convertObjectToCollectionWithElementConversion() throws Exception {
		conversionService.addConverterFactory(new NumberToNumberConverterFactory());
		List<Integer> result = (List<Integer>) conversionService.convert(3L, TypeDescriptor.valueOf(Long.class),
				new TypeDescriptor(getClass().getField("genericList")));
		assertEquals(1, result.size());
		assertEquals(new Integer(3), result.get(0));
	}

	@Test
	public void convertObjectToArray() {
		Object[] result = conversionService.convert(3L, Object[].class);
		assertEquals(1, result.length);
		assertEquals(3L, result[0]);
	}

	@Test
	public void convertObjectToArrayWithElementConversion() {
		conversionService.addConverterFactory(new NumberToNumberConverterFactory());
		Integer[] result = conversionService.convert(3L, Integer[].class);
		assertEquals(1, result.length);
		assertEquals(new Integer(3), result[0]);
	}

	@Test
	public void genericConverterDelegatingBackToConversionServiceConverterNotFound() {
		try {
			conversionService.convert("1", Integer[].class);
		} catch (ConversionFailedException e) {
			assertTrue(e.getCause() instanceof ConverterNotFoundException);
		}
	}

	@Test
	public void parent() {
		GenericConversionService parent = new GenericConversionService();
		conversionService.setParent(parent);
		assertFalse(conversionService.canConvert(String.class, Integer.class));
		try {
			conversionService.convert("3", Integer.class);
		} catch (ConverterNotFoundException e) {

		}
	}

	public static enum FooEnum {
		BAR, BAZ
	}

}
