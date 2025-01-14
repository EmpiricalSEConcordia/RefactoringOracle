/**
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

package org.apache.flink.api.java.operator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.java.tuple.Tuple5;
import org.apache.flink.api.java.typeutils.TupleTypeInfo;
import org.junit.BeforeClass;
import org.junit.Test;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;

public class CrossOperatorTest {

	// TUPLE DATA
	private static final List<Tuple5<Integer, Long, String, Long, Integer>> emptyTupleData =
		new ArrayList<Tuple5<Integer, Long, String, Long, Integer>>();

	private final TupleTypeInfo<Tuple5<Integer, Long, String, Long, Integer>> tupleTypeInfo = new
		TupleTypeInfo<Tuple5<Integer, Long, String, Long, Integer>>(
		BasicTypeInfo.INT_TYPE_INFO,
		BasicTypeInfo.LONG_TYPE_INFO,
		BasicTypeInfo.STRING_TYPE_INFO,
		BasicTypeInfo.LONG_TYPE_INFO,
		BasicTypeInfo.INT_TYPE_INFO
	);

	private static List<CustomType> customTypeData = new ArrayList<CustomType>();

	@BeforeClass
	public static void insertCustomData() {
		customTypeData.add(new CustomType());
	}

	@Test
	public void testCrossProjection1() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should work
		try {
			ds1.cross(ds2)
				.projectFirst(0)
				.types(Integer.class);
		} catch(Exception e) {
			Assert.fail();
		}
	}

	@Test
	public void testCrossProjection2() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should work
		try {
			ds1.cross(ds2)
				.projectFirst(0,3)
				.types(Integer.class, Long.class);
		} catch(Exception e) {
			Assert.fail();
		}
	}

	@Test
	public void testCrossProjection3() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should work
		try {
			ds1.cross(ds2)
				.projectFirst(0)
				.projectSecond(3)
				.types(Integer.class, Long.class);
		} catch(Exception e) {
			Assert.fail();
		}
	}

	@Test
	public void testCrossProjection4() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should work
		try {
			ds1.cross(ds2)
				.projectFirst(0,2)
				.projectSecond(1,4)
				.projectFirst(1)
				.types(Integer.class, String.class, Long.class, Integer.class, Long.class);
		} catch(Exception e) {
			Assert.fail();
		}

	}

	@Test
	public void testCrossProjection5() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should work
		try {
			ds1.cross(ds2)
				.projectSecond(0,2)
				.projectFirst(1,4)
				.projectFirst(1)
				.types(Integer.class, String.class, Long.class, Integer.class, Long.class);
		} catch(Exception e) {
			Assert.fail();
		}
	}

	@Test
	public void testCrossProjection6() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<CustomType> ds1 = env.fromCollection(customTypeData);
		DataSet<CustomType> ds2 = env.fromCollection(customTypeData);

		// should work
		try {
			ds1.cross(ds2)
				.projectFirst()
				.projectSecond()
				.types(CustomType.class, CustomType.class);
		} catch(Exception e) {
			Assert.fail();
		}
	}

	@Test
	public void testCrossProjection7() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should work
		try {
			ds1.cross(ds2)
				.projectSecond()
				.projectFirst(1,4)
				.types(Tuple5.class, Long.class, Integer.class);
		} catch(Exception e) {
			Assert.fail();
		}
	}

	@Test(expected=IndexOutOfBoundsException.class)
	public void testCrossProjection8() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should not work, index out of range
		ds1.cross(ds2)
			.projectFirst(5)
			.types(Integer.class);
	}

	@Test(expected=IndexOutOfBoundsException.class)
	public void testCrossProjection9() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should not work, index out of range
		ds1.cross(ds2)
			.projectSecond(5)
			.types(Integer.class);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testCrossProjection10() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should not work, type does not match
		ds1.cross(ds2)
			.projectFirst(2)
			.types(Integer.class);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testCrossProjection11() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should not work, type does not match
		ds1.cross(ds2)
			.projectSecond(2)
			.types(Integer.class);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testCrossProjection12() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should not work, number of types and fields does not match
		ds1.cross(ds2)
			.projectSecond(2)
			.projectFirst(1)
			.types(String.class);
	}

	@Test(expected=IndexOutOfBoundsException.class)
	public void testCrossProjection13() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should not work, index out of range
		ds1.cross(ds2)
			.projectSecond(0)
			.projectFirst(5)
			.types(Integer.class);
	}

	@Test(expected=IndexOutOfBoundsException.class)
	public void testCrossProjection14() {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds1 = env.fromCollection(emptyTupleData, tupleTypeInfo);
		DataSet<Tuple5<Integer, Long, String, Long, Integer>> ds2 = env.fromCollection(emptyTupleData, tupleTypeInfo);

		// should not work, index out of range
		ds1.cross(ds2)
			.projectFirst(0)
			.projectSecond(5)
			.types(Integer.class);
	}
	
	/*
	 * ####################################################################
	 */

	public static class CustomType implements Serializable {

		private static final long serialVersionUID = 1L;

		public int myInt;
		public long myLong;
		public String myString;

		public CustomType() {};

		public CustomType(int i, long l, String s) {
			myInt = i;
			myLong = l;
			myString = s;
		}

		@Override
		public String toString() {
			return myInt+","+myLong+","+myString;
		}
	}

}
