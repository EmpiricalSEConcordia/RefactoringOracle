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

package eu.stratosphere.nephele.types;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class FileRecord implements Record {

	private String fileName;

	private static final byte[] EMPTY_BYTES = new byte[0];

	private byte[] bytes;

	private int length;

	public FileRecord() {
		bytes = EMPTY_BYTES;
		fileName = "empty";
		this.length = 0;
	}

	public FileRecord(String fileName) {
		bytes = EMPTY_BYTES;
		this.fileName = fileName;
		this.length = 0;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFileName() {
		return this.fileName;
	}

	public byte[] getDataBuffer() {
		return this.bytes;
	}

	/**
	 * Append a range of bytes to the end of the given data
	 * 
	 * @param data
	 *        the data to copy from
	 * @param start
	 *        the first position to append from data
	 * @param len
	 *        the number of bytes to append
	 */
	public void append(byte[] data, int start, int len) {
		setCapacity(length + len, true);
		System.arraycopy(data, start, bytes, length, len);
		this.length += len;
	}

	private void setCapacity(int len, boolean keepData) {
		if (this.bytes == null || this.bytes.length < len) {
			final byte[] newBytes = new byte[len];
			if (this.bytes != null && keepData) {
				System.arraycopy(this.bytes, 0, newBytes, 0, this.length);
			}
			this.bytes = newBytes;
		}
	}

	@Override
	public void read(DataInput in) throws IOException {
		this.fileName = StringRecord.readString(in);

		final int newLength = in.readInt();
		setCapacity(newLength, false);
		in.readFully(this.bytes, 0, newLength);
		this.length = newLength;

	}

	@Override
	public void write(DataOutput out) throws IOException {
		StringRecord.writeString(out, fileName);
		out.writeInt(this.length);
		out.write(this.bytes, 0, this.length);
	}

}
