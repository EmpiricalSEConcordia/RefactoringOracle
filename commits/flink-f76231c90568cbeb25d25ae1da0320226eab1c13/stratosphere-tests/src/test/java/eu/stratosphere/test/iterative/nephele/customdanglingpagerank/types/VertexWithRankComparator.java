/***********************************************************************************************************************
 *
 * Copyright (C) 2012 by the Stratosphere project (http://stratosphere.eu)
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
package eu.stratosphere.test.iterative.nephele.customdanglingpagerank.types;

import java.io.IOException;

import eu.stratosphere.api.typeutils.TypeComparator;
import eu.stratosphere.core.memory.DataInputView;
import eu.stratosphere.core.memory.DataOutputView;
import eu.stratosphere.core.memory.MemorySegment;


/**
 *
 */
public final class VertexWithRankComparator extends TypeComparator<VertexWithRank> {

	private long reference;
	
	@Override
	public int hash(VertexWithRank record) {
		final long value = record.getVertexID();
		return 43 + (int) (value ^ value >>> 32);
	}

	@Override
	public void setReference(VertexWithRank toCompare) {
		this.reference = toCompare.getVertexID();
	}

	@Override
	public boolean equalToReference(VertexWithRank candidate) {
		return candidate.getVertexID() == this.reference;
	}

	@Override
	public int compareToReference(TypeComparator<VertexWithRank> referencedComparator) {
		VertexWithRankComparator comp = (VertexWithRankComparator) referencedComparator;
		final long diff = comp.reference - this.reference;
		return diff < 0 ? -1 : diff > 0 ? 1 : 0;
	}

	@Override
	public int compare(DataInputView source1, DataInputView source2) throws IOException {
		final long diff = source1.readLong() - source2.readLong();
		return diff < 0 ? -1 : diff > 0 ? 1 : 0;
	}

	@Override
	public boolean supportsNormalizedKey() {
		return true;
	}

	@Override
	public int getNormalizeKeyLen() {
		return 8;
	}

	@Override
	public boolean isNormalizedKeyPrefixOnly(int keyBytes) {
		return keyBytes < 8;
	}

	@Override
	public void putNormalizedKey(VertexWithRank record, MemorySegment target, int offset, int len) {
		final long value = record.getVertexID() - Long.MIN_VALUE;
		
		// see PactInteger for an explanation of the logic
		if (len == 8) {
			// default case, full normalized key
			target.putLongBigEndian(offset, value);
		}
		else if (len <= 0) {
		}
		else if (len < 8) {
			for (int i = 0; len > 0; len--, i++) {
				target.put(offset + i, (byte) ((value >>> ((3-i)<<3)) & 0xff));
			}
		}
		else {
			target.putLongBigEndian(offset, value);
			for (int i = 8; i < len; i++) {
				target.put(offset + i, (byte) 0);
			}
		}
	}

	@Override
	public boolean invertNormalizedKey() {
		return false;
	}
	
	@Override
	public boolean supportsSerializationWithKeyNormalization() {
		return true;
	}

	@Override
	public void writeWithKeyNormalization(VertexWithRank record, DataOutputView target) throws IOException {
		target.writeLong(record.getVertexID() - Long.MIN_VALUE);
		target.writeDouble(record.getRank());
	}

	@Override
	public void readWithKeyDenormalization(VertexWithRank record, DataInputView source) throws IOException {
		record.setVertexID(source.readLong() + Long.MIN_VALUE);
		record.setRank(source.readDouble());
	}

	@Override
	public VertexWithRankComparator duplicate() {
		return new VertexWithRankComparator();
	}
}
