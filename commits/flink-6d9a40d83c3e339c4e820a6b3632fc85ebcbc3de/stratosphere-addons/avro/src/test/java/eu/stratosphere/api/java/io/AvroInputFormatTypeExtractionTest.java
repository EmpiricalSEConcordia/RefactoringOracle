/***********************************************************************************************************************
 *
 * Copyright (C) 2010-2013 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.api.java.io;

import org.junit.Assert;
import org.junit.Test;

import eu.stratosphere.api.common.io.InputFormat;
import eu.stratosphere.api.java.DataSet;
import eu.stratosphere.api.java.ExecutionEnvironment;
import eu.stratosphere.api.java.typeutils.PojoTypeInfo;
import eu.stratosphere.api.java.typeutils.TypeExtractor;
import eu.stratosphere.core.fs.Path;
import eu.stratosphere.types.TypeInformation;

public class AvroInputFormatTypeExtractionTest {

	@Test
	public void testTypeExtraction() {
		try {
			InputFormat<MyAvroType, ?> format = new AvroInputFormat<MyAvroType>(new Path("file:///ignore/this/file"), MyAvroType.class);
			
			TypeInformation<?> typeInfoDirect = TypeExtractor.getInputFormatTypes(format);
			
			ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
			DataSet<MyAvroType> input = env.createInput(format);
			TypeInformation<?> typeInfoDataSet = input.getType();
			
			
			Assert.assertTrue(typeInfoDirect instanceof PojoTypeInfo);
			Assert.assertTrue(typeInfoDataSet instanceof PojoTypeInfo);
			
			Assert.assertEquals(MyAvroType.class, typeInfoDirect.getTypeClass());
			Assert.assertEquals(MyAvroType.class, typeInfoDataSet.getTypeClass());
		}
		catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}
	
	public static final class MyAvroType {
		
		public String theString;
		
		private double aDouble;
		
		public double getaDouble() {
			return aDouble;
		}
		
		public void setaDouble(double aDouble) {
			this.aDouble = aDouble;
		}
		
		public void setTheString(String theString) {
			this.theString = theString;
		}
		
		public String getTheString() {
			return theString;
		}
	}
}
