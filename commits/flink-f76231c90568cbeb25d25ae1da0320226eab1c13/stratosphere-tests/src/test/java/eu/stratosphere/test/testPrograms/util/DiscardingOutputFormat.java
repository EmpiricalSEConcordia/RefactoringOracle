/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
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

package eu.stratosphere.test.testPrograms.util;

import java.io.IOException;

import eu.stratosphere.api.io.OutputFormat;
import eu.stratosphere.configuration.Configuration;
import eu.stratosphere.types.PactRecord;

/**
 * A simple output format that discards all data by doing nothing.
 */
public class DiscardingOutputFormat implements OutputFormat<PactRecord> {
	private static final long serialVersionUID = 1L;
	

	@Override
	public void configure(Configuration parameters)
	{}


	@Override
	public void open(int taskNumber) throws IOException
	{}


	@Override
	public void writeRecord(PactRecord record) throws IOException
	{}


	@Override
	public void close() throws IOException
	{}
}
