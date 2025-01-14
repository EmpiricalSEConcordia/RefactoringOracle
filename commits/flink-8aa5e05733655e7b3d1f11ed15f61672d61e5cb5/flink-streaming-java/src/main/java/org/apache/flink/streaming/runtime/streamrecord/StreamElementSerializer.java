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
 * WITHOUStreamRecord<?>WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.runtime.streamrecord;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.typeutils.CompatibilityResult;
import org.apache.flink.api.common.typeutils.CompositeTypeSerializerConfigSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerConfigSnapshot;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.streamstatus.StreamStatus;

/**
 * Serializer for {@link StreamRecord}, {@link Watermark}, {@link LatencyMarker}, and
 * {@link StreamStatus}.
 *
 * <p>This does not behave like a normal {@link TypeSerializer}, instead, this is only used at the
 * stream task/operator level for transmitting StreamRecords and Watermarks.
 *
 * @param <T> The type of value in the StreamRecord
 */
@Internal
public final class StreamElementSerializer<T> extends TypeSerializer<StreamElement> {

	private static final long serialVersionUID = 1L;

	private static final int TAG_REC_WITH_TIMESTAMP = 0;
	private static final int TAG_REC_WITHOUT_TIMESTAMP = 1;
	private static final int TAG_WATERMARK = 2;
	private static final int TAG_LATENCY_MARKER = 3;
	private static final int TAG_STREAM_STATUS = 4;


	private final TypeSerializer<T> typeSerializer;


	public StreamElementSerializer(TypeSerializer<T> serializer) {
		if (serializer instanceof StreamElementSerializer) {
			throw new RuntimeException("StreamRecordSerializer given to StreamRecordSerializer as value TypeSerializer: " + serializer);
		}
		this.typeSerializer = requireNonNull(serializer);
	}

	public TypeSerializer<T> getContainedTypeSerializer() {
		return this.typeSerializer;
	}

	// ------------------------------------------------------------------------
	//  Utilities
	// ------------------------------------------------------------------------

	@Override
	public boolean isImmutableType() {
		return false;
	}

	@Override
	public StreamElementSerializer<T> duplicate() {
		TypeSerializer<T> copy = typeSerializer.duplicate();
		return (copy == typeSerializer) ? this : new StreamElementSerializer<T>(copy);
	}

	// ------------------------------------------------------------------------
	//  Utilities
	// ------------------------------------------------------------------------

	@Override
	public StreamRecord<T> createInstance() {
		return new StreamRecord<T>(typeSerializer.createInstance());
	}

	@Override
	public int getLength() {
		return -1;
	}

	@Override
	public StreamElement copy(StreamElement from) {
		// we can reuse the timestamp since Instant is immutable
		if (from.isRecord()) {
			StreamRecord<T> fromRecord = from.asRecord();
			return fromRecord.copy(typeSerializer.copy(fromRecord.getValue()));
		}
		else if (from.isWatermark() || from.isStreamStatus() || from.isLatencyMarker()) {
			// is immutable
			return from;
		}
		else {
			throw new RuntimeException();
		}
	}

	@Override
	public StreamElement copy(StreamElement from, StreamElement reuse) {
		if (from.isRecord() && reuse.isRecord()) {
			StreamRecord<T> fromRecord = from.asRecord();
			StreamRecord<T> reuseRecord = reuse.asRecord();

			T valueCopy = typeSerializer.copy(fromRecord.getValue(), reuseRecord.getValue());
			fromRecord.copyTo(valueCopy, reuseRecord);
			return reuse;
		}
		else if (from.isWatermark() || from.isStreamStatus() || from.isLatencyMarker()) {
			// is immutable
			return from;
		}
		else {
			throw new RuntimeException("Cannot copy " + from + " -> " + reuse);
		}
	}

	@Override
	public void copy(DataInputView source, DataOutputView target) throws IOException {
		int tag = source.readByte();
		target.write(tag);

		if (tag == TAG_REC_WITH_TIMESTAMP) {
			// move timestamp
			target.writeLong(source.readLong());
			typeSerializer.copy(source, target);
		}
		else if (tag == TAG_REC_WITHOUT_TIMESTAMP) {
			typeSerializer.copy(source, target);
		}
		else if (tag == TAG_WATERMARK) {
			target.writeLong(source.readLong());
		}
		else if (tag == TAG_STREAM_STATUS) {
			target.writeInt(source.readInt());
		}
		else if (tag == TAG_LATENCY_MARKER) {
			target.writeLong(source.readLong());
			target.writeInt(source.readInt());
			target.writeInt(source.readInt());
		} else {
			throw new IOException("Corrupt stream, found tag: " + tag);
		}
	}

	@Override
	public void serialize(StreamElement value, DataOutputView target) throws IOException {
		if (value.isRecord()) {
			StreamRecord<T> record = value.asRecord();

			if (record.hasTimestamp()) {
				target.write(TAG_REC_WITH_TIMESTAMP);
				target.writeLong(record.getTimestamp());
			} else {
				target.write(TAG_REC_WITHOUT_TIMESTAMP);
			}
			typeSerializer.serialize(record.getValue(), target);
		}
		else if (value.isWatermark()) {
			target.write(TAG_WATERMARK);
			target.writeLong(value.asWatermark().getTimestamp());
		}
		else if (value.isStreamStatus()) {
			target.write(TAG_STREAM_STATUS);
			target.writeInt(value.asStreamStatus().getStatus());
		}
		else if (value.isLatencyMarker()) {
			target.write(TAG_LATENCY_MARKER);
			target.writeLong(value.asLatencyMarker().getMarkedTime());
			target.writeInt(value.asLatencyMarker().getVertexID());
			target.writeInt(value.asLatencyMarker().getSubtaskIndex());
		}
		else {
			throw new RuntimeException();
		}
	}

	@Override
	public StreamElement deserialize(DataInputView source) throws IOException {
		int tag = source.readByte();
		if (tag == TAG_REC_WITH_TIMESTAMP) {
			long timestamp = source.readLong();
			return new StreamRecord<T>(typeSerializer.deserialize(source), timestamp);
		}
		else if (tag == TAG_REC_WITHOUT_TIMESTAMP) {
			return new StreamRecord<T>(typeSerializer.deserialize(source));
		}
		else if (tag == TAG_WATERMARK) {
			return new Watermark(source.readLong());
		}
		else if (tag == TAG_STREAM_STATUS) {
			return new StreamStatus(source.readInt());
		}
		else if (tag == TAG_LATENCY_MARKER) {
			return new LatencyMarker(source.readLong(), source.readInt(), source.readInt());
		}
		else {
			throw new IOException("Corrupt stream, found tag: " + tag);
		}
	}

	@Override
	public StreamElement deserialize(StreamElement reuse, DataInputView source) throws IOException {
		int tag = source.readByte();
		if (tag == TAG_REC_WITH_TIMESTAMP) {
			long timestamp = source.readLong();
			T value = typeSerializer.deserialize(source);
			StreamRecord<T> reuseRecord = reuse.asRecord();
			reuseRecord.replace(value, timestamp);
			return reuseRecord;
		}
		else if (tag == TAG_REC_WITHOUT_TIMESTAMP) {
			T value = typeSerializer.deserialize(source);
			StreamRecord<T> reuseRecord = reuse.asRecord();
			reuseRecord.replace(value);
			return reuseRecord;
		}
		else if (tag == TAG_WATERMARK) {
			return new Watermark(source.readLong());
		}
		else if (tag == TAG_LATENCY_MARKER) {
			return new LatencyMarker(source.readLong(), source.readInt(), source.readInt());
		}
		else {
			throw new IOException("Corrupt stream, found tag: " + tag);
		}
	}

	// ------------------------------------------------------------------------
	//  Utilities
	// ------------------------------------------------------------------------

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof StreamElementSerializer) {
			StreamElementSerializer<?> other = (StreamElementSerializer<?>) obj;

			return other.canEqual(this) && typeSerializer.equals(other.typeSerializer);
		} else {
			return false;
		}
	}

	@Override
	public boolean canEqual(Object obj) {
		return obj instanceof StreamElementSerializer;
	}

	@Override
	public int hashCode() {
		return typeSerializer.hashCode();
	}

	// --------------------------------------------------------------------------------------------
	// Serializer configuration snapshotting & compatibility
	//
	// This serializer may be used by Flink internal operators that need to checkpoint
	// buffered records. Therefore, it may be part of managed state and need to implement
	// the configuration snapshot and compatibility methods.
	// --------------------------------------------------------------------------------------------

	@Override
	public StreamElementSerializerConfigSnapshot snapshotConfiguration() {
		return new StreamElementSerializerConfigSnapshot(typeSerializer.snapshotConfiguration());
	}

	@Override
	public CompatibilityResult<StreamElement> ensureCompatibility(TypeSerializerConfigSnapshot configSnapshot) {
		if (configSnapshot instanceof StreamElementSerializerConfigSnapshot) {
			CompatibilityResult<T> compatResult = typeSerializer.ensureCompatibility(
				((StreamElementSerializerConfigSnapshot) configSnapshot).getSingleNestedSerializerConfigSnapshot());

			if (!compatResult.requiresMigration()) {
				return CompatibilityResult.compatible();
			} else if (compatResult.getConvertDeserializer() != null) {
				return CompatibilityResult.requiresMigration(
					new StreamElementSerializer<>(compatResult.getConvertDeserializer()));
			}
		}

		return CompatibilityResult.requiresMigration(null);
	}

	/**
	 * Configuration snapshot specific to the {@link StreamElementSerializer}.
	 */
	public static final class StreamElementSerializerConfigSnapshot extends CompositeTypeSerializerConfigSnapshot {

		private static final int VERSION = 1;

		/** This empty nullary constructor is required for deserializing the configuration. */
		public StreamElementSerializerConfigSnapshot() {}

		public StreamElementSerializerConfigSnapshot(TypeSerializerConfigSnapshot typeSerializerConfigSnapshot) {
			super(typeSerializerConfigSnapshot);
		}

		@Override
		public int getVersion() {
			return VERSION;
		}
	}
}
