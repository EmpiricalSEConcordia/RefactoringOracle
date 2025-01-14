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

package eu.stratosphere.nephele.example.events;

/*
 *  Copyright 2010 casp.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

import eu.stratosphere.nephele.event.task.StringTaskEvent;
import eu.stratosphere.nephele.io.RecordReader;
import eu.stratosphere.nephele.io.RecordWriter;
import eu.stratosphere.nephele.template.AbstractTask;
import eu.stratosphere.nephele.types.StringRecord;

/**
 * @author casp
 */
public class EventSender extends AbstractTask {

	// this is just a dummy output gate...
	private RecordReader<StringRecord> input = null;

	private RecordWriter<StringRecord> output = null;

	@Override
	public void registerInputOutput() {
		this.input = new RecordReader<StringRecord>(this, StringRecord.class, null);
		this.output = new RecordWriter<StringRecord>(this, StringRecord.class);
	}

	@Override
	public void invoke() throws Exception {

		int i = 1;
		while (this.input.hasNext()) {
			i++;
			if (i % 1000 == 0) {
				this.output.publishEvent(new StringTaskEvent("this is the " + i + "th message"));
			}
			StringRecord s = input.next();
			this.output.emit(s);
		}

	}
}
