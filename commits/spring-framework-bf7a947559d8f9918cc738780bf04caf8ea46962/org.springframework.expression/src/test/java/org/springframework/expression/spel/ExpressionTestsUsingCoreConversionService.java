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

package org.springframework.expression.spel;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.convert.ConversionExecutor;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.service.DefaultConversionService;
import org.springframework.core.convert.service.GenericConversionService;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Expression evaluation where the TypeConverter plugged in is the {@link GenericConversionService}
 * 
 * @author Andy Clement
 */
public class ExpressionTestsUsingCoreConversionService extends ExpressionTestCase {

	private static List<String> listOfString = new ArrayList<String>();
	private static TypeDescriptor typeDescriptorForListOfString = null;
	private static List<Integer> listOfInteger = new ArrayList<Integer>();
	private static TypeDescriptor typeDescriptorForListOfInteger = null;
	
	static {
		listOfString.add("1");
		listOfString.add("2");
		listOfString.add("3");
		listOfInteger.add(4);
		listOfInteger.add(5);
		listOfInteger.add(6);
	}
	
	public void setUp() throws Exception {
		super.setUp();
		typeDescriptorForListOfString = new TypeDescriptor(ExpressionTestsUsingCoreConversionService.class.getDeclaredField("listOfString"));
		typeDescriptorForListOfInteger = new TypeDescriptor(ExpressionTestsUsingCoreConversionService.class.getDeclaredField("listOfInteger"));
	}
		
	
	/**
	 * Test the service can convert what we are about to use in the expression evaluation tests.
	 */
	public void testConversionsAvailable() throws Exception {
		TypeConvertorUsingConversionService tcs = new TypeConvertorUsingConversionService();
		
		// ArrayList containing List<Integer> to List<String>
		Class<?> clazz = typeDescriptorForListOfString.getElementType();
		assertEquals(String.class,clazz);
		ConversionExecutor executor = tcs.getConversionExecutor(ArrayList.class, typeDescriptorForListOfString);
		assertNotNull(executor);
		List l = (List)executor.execute(listOfInteger);
		assertNotNull(l); 

		// ArrayList containing List<String> to List<Integer>
		clazz = typeDescriptorForListOfInteger.getElementType();
		assertEquals(Integer.class,clazz);
		executor = tcs.getConversionExecutor(ArrayList.class, typeDescriptorForListOfInteger);
		assertNotNull(executor);
		l = (List)executor.execute(listOfString);
		assertNotNull(l);
	}
	
	public void testSetParameterizedList() throws Exception {
		StandardEvaluationContext context = TestScenarioCreator.getTestEvaluationContext();
		Expression e = parser.parseExpression("listOfInteger.size()");
		assertEquals(0,e.getValue(context,Integer.class).intValue());
		context.setTypeConverter(new TypeConvertorUsingConversionService());
		// Assign a List<String> to the List<Integer> field - the component elements should be converted
		parser.parseExpression("listOfInteger").setValue(context,listOfString);
		assertEquals(3,e.getValue(context,Integer.class).intValue()); // size now 3
		Class clazz = parser.parseExpression("listOfInteger[1].getClass()").getValue(context,Class.class); // element type correctly Integer
		assertEquals(Integer.class,clazz);
	}
	

	/**
	 * Type convertor that uses the core conversion service.
	 */
	private static class TypeConvertorUsingConversionService extends DefaultConversionService implements TypeConverter {

		public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
			return super.canConvert(sourceType, TypeDescriptor.valueOf(targetType));
		}

		public boolean canConvert(Class<?> sourceType, TypeDescriptor typeDescriptor) {
			return super.canConvert(sourceType, typeDescriptor);
		}

		@SuppressWarnings("unchecked")
		public <T> T convertValue(Object value, Class<T> targetType) throws EvaluationException {
			return (T)super.executeConversion(value,TypeDescriptor.valueOf(targetType));
		}

		@SuppressWarnings("unchecked")
		public Object convertValue(Object value, TypeDescriptor typeDescriptor)
				throws EvaluationException {
			return super.executeConversion(value, typeDescriptor);
		}
		
	}

}
