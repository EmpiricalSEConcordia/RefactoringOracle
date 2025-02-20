/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.java.operators;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.java.operators.Keys.ExpressionKeys;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple7;
import org.apache.flink.api.java.typeutils.TupleTypeInfo;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

@RunWith(PowerMockRunner.class)
public class KeysTest {
	
	@Test
	public void testTupleRangeCheck() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		
		// test private static final int[] rangeCheckFields(int[] fields, int maxAllowedField)
		Method rangeCheckFieldsMethod = Whitebox.getMethod(Keys.class, "rangeCheckFields", int[].class, int.class);
		int[] result = (int[]) rangeCheckFieldsMethod.invoke(null, new int[] {1,2,3,4}, 4);
		Assert.assertArrayEquals(new int[] {1,2,3,4}, result);
		
		// test duplicate elimination
		result = (int[]) rangeCheckFieldsMethod.invoke(null, new int[] {1,2,2,3,4}, 4);
		Assert.assertArrayEquals(new int[] {1,2,3,4}, result);
		
		result = (int[]) rangeCheckFieldsMethod.invoke(null, new int[] {1,2,2,2,2,2,2,3,3,4}, 4);
		Assert.assertArrayEquals(new int[] {1,2,3,4}, result);
		
		// corner case tests
		result = (int[]) rangeCheckFieldsMethod.invoke(null, new int[] {0}, 0);
		Assert.assertArrayEquals(new int[] {0}, result);
		
		Throwable ex = null;
		try {
			// throws illegal argument.
			result = (int[]) rangeCheckFieldsMethod.invoke(null, new int[] {5}, 0);
		} catch(Throwable iae) {
			ex = iae;
		}
		Assert.assertNotNull(ex);
	}
	
	@Test
	public void testStandardTupleKeys() {
		TupleTypeInfo<Tuple7<String, String, String, String, String, String, String>> typeInfo = new TupleTypeInfo<Tuple7<String, String, String, String, String, String, String>>(
				BasicTypeInfo.STRING_TYPE_INFO,BasicTypeInfo.STRING_TYPE_INFO,BasicTypeInfo.STRING_TYPE_INFO,BasicTypeInfo.STRING_TYPE_INFO,BasicTypeInfo.STRING_TYPE_INFO,
				BasicTypeInfo.STRING_TYPE_INFO,BasicTypeInfo.STRING_TYPE_INFO);
		
		ExpressionKeys<Tuple7<String, String, String, String, String, String, String>> ek;
		
		for( int i = 1; i < 8; i++) {
			int[] ints = new int[i];
			for( int j = 0; j < i; j++) {
				ints[j] = j;
			}
			int[] inInts = Arrays.copyOf(ints, ints.length); // copy, just to make sure that the code is not cheating by changing the ints.
			ek = new ExpressionKeys<Tuple7<String, String, String, String, String, String, String>>(inInts, typeInfo);
			Assert.assertArrayEquals(ints, ek.computeLogicalKeyPositions());
			Assert.assertEquals(ints.length, ek.computeLogicalKeyPositions().length);
			
			ArrayUtils.reverse(ints);
			inInts = Arrays.copyOf(ints, ints.length);
			ek = new ExpressionKeys<Tuple7<String, String, String, String, String, String, String>>(inInts, typeInfo);
			Assert.assertArrayEquals(ints, ek.computeLogicalKeyPositions());
			Assert.assertEquals(ints.length, ek.computeLogicalKeyPositions().length);
		}
	}
	
	@Test 
	public void testInvalid() throws Throwable {
		TupleTypeInfo<Tuple3<String, Tuple3<String, String, String>, String>> typeInfo = new TupleTypeInfo<Tuple3<String,Tuple3<String,String,String>,String>>(
				BasicTypeInfo.STRING_TYPE_INFO,
				new TupleTypeInfo<Tuple3<String, String, String>>(BasicTypeInfo.STRING_TYPE_INFO,BasicTypeInfo.STRING_TYPE_INFO,BasicTypeInfo.STRING_TYPE_INFO),
				BasicTypeInfo.STRING_TYPE_INFO);
		ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>> fpk;
		
		String[][] tests = new String[][] {
				new String[] {"f11"},new String[] {"f-35"}, new String[] {"f0.f33"}, new String[] {"f1.f33"}
		};
		for(int i = 0; i < tests.length; i++) {
			Throwable e = null;
			try {
				fpk = new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(tests[i], typeInfo);
			} catch(Throwable t) {
				// System.err.println("Message: "+t.getMessage()); t.printStackTrace();
				e = t;	
			}
			Assert.assertNotNull(e);
		}
	}
	
	@Test
	public void testTupleKeyExpansion() {
		TupleTypeInfo<Tuple3<String, Tuple3<String, String, String>, String>> typeInfo = new TupleTypeInfo<Tuple3<String,Tuple3<String,String,String>,String>>(
				BasicTypeInfo.STRING_TYPE_INFO,
				new TupleTypeInfo<Tuple3<String, String, String>>(BasicTypeInfo.STRING_TYPE_INFO,BasicTypeInfo.STRING_TYPE_INFO,BasicTypeInfo.STRING_TYPE_INFO),
				BasicTypeInfo.STRING_TYPE_INFO);
		ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>> fpk = 
				new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(new int[] {0}, typeInfo);
		Assert.assertArrayEquals(new int[] {0}, fpk.computeLogicalKeyPositions());
		
		fpk = new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(new int[] {1}, typeInfo);
		Assert.assertArrayEquals(new int[] {1,2,3}, fpk.computeLogicalKeyPositions());
		
		fpk = new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(new int[] {2}, typeInfo);
		Assert.assertArrayEquals(new int[] {4}, fpk.computeLogicalKeyPositions());
		
		fpk = new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(new int[] {0,1,2}, typeInfo);
		Assert.assertArrayEquals(new int[] {0,1,2,3,4}, fpk.computeLogicalKeyPositions());
		
		fpk = new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(null, typeInfo, true); // empty case
		Assert.assertArrayEquals(new int[] {0,1,2,3,4}, fpk.computeLogicalKeyPositions());
		
		// duplicate elimination
		fpk = new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(new int[] {0,1,1,1,2}, typeInfo);
		Assert.assertArrayEquals(new int[] {0,1,2,3,4}, fpk.computeLogicalKeyPositions());
		
		fpk = new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(new String[] {"*"}, typeInfo);
		Assert.assertArrayEquals(new int[] {0,1,2,3,4}, fpk.computeLogicalKeyPositions());
		
		// this was a bug:
		fpk = new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(new String[] {"f2"}, typeInfo);
		Assert.assertArrayEquals(new int[] {4}, fpk.computeLogicalKeyPositions());
		
		fpk = new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(new String[] {"f0","f1.f0","f1.f1", "f1.f2", "f2"}, typeInfo);
		Assert.assertArrayEquals(new int[] {0,1,2,3,4}, fpk.computeLogicalKeyPositions());
		
		fpk = new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(new String[] {"f0","f1.f0","f1.f1", "f2"}, typeInfo);
		Assert.assertArrayEquals(new int[] {0,1,2,4}, fpk.computeLogicalKeyPositions());
		
		fpk = new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(new String[] {"f2", "f0"}, typeInfo);
		Assert.assertArrayEquals(new int[] {4,0}, fpk.computeLogicalKeyPositions());
		
		// duplicate elimination
		fpk = new ExpressionKeys<Tuple3<String, Tuple3<String, String, String>, String>>(new String[] {"f2","f2","f2", "f0"}, typeInfo);
		Assert.assertArrayEquals(new int[] {4,0}, fpk.computeLogicalKeyPositions());
		
		
		TupleTypeInfo<Tuple3<String, Tuple3<Tuple3<String, String, String>, String, String>, String>> complexTypeInfo = new TupleTypeInfo<Tuple3<String,Tuple3<Tuple3<String, String, String>,String,String>,String>>(
				BasicTypeInfo.STRING_TYPE_INFO,
				new TupleTypeInfo<Tuple3<Tuple3<String, String, String>, String, String>>(new TupleTypeInfo<Tuple3<String, String, String>>(BasicTypeInfo.STRING_TYPE_INFO,BasicTypeInfo.STRING_TYPE_INFO,BasicTypeInfo.STRING_TYPE_INFO),BasicTypeInfo.STRING_TYPE_INFO,BasicTypeInfo.STRING_TYPE_INFO),
				BasicTypeInfo.STRING_TYPE_INFO);
		
		ExpressionKeys<Tuple3<String, Tuple3<Tuple3<String, String, String>, String, String>, String>> complexFpk = 
		new ExpressionKeys<Tuple3<String, Tuple3<Tuple3<String, String, String>, String, String>, String>>(new int[] {0}, complexTypeInfo);
		Assert.assertArrayEquals(new int[] {0}, complexFpk.computeLogicalKeyPositions());
		
		complexFpk = new ExpressionKeys<Tuple3<String, Tuple3<Tuple3<String, String, String>, String, String>, String>>(new int[] {0,1,2}, complexTypeInfo);
		Assert.assertArrayEquals(new int[] {0,1,2,3,4,5,6}, complexFpk.computeLogicalKeyPositions());
		
		complexFpk = new ExpressionKeys<Tuple3<String, Tuple3<Tuple3<String, String, String>, String, String>, String>>(new String[] {"*"}, complexTypeInfo);
		Assert.assertArrayEquals(new int[] {0,1,2,3,4,5,6}, complexFpk.computeLogicalKeyPositions());
		
		complexFpk = new ExpressionKeys<Tuple3<String, Tuple3<Tuple3<String, String, String>, String, String>, String>>(new String[] {"f1.f0.*"}, complexTypeInfo);
		Assert.assertArrayEquals(new int[] {1,2,3}, complexFpk.computeLogicalKeyPositions());
		
		complexFpk = new ExpressionKeys<Tuple3<String, Tuple3<Tuple3<String, String, String>, String, String>, String>>(new String[] {"f2"}, complexTypeInfo);
		Assert.assertArrayEquals(new int[] {6}, complexFpk.computeLogicalKeyPositions());
	}
}
