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

package org.apache.flink.api.java.typeutils.runtime;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;

public class KryoSerializer<T> extends TypeSerializer<T> {
	private static final long serialVersionUID = 1L;

	private final Class<T> type;
	private final Class<? extends T> typeToInstantiate;

	private transient Kryo kryo;
	private transient T copyInstance = null;
	private transient Input in = null;

	public KryoSerializer(Class<T> type){
		this(type,type);
	}

	public KryoSerializer(Class<T> type, Class<? extends T> typeToInstantiate){
		if(type == null || typeToInstantiate == null){
			throw new NullPointerException("Type class cannot be null.");
		}

		this.type = type;
		this.typeToInstantiate = typeToInstantiate;
		kryo = new Kryo();
		kryo.setAsmEnabled(true);
		kryo.register(type);
	}

	@Override
	public boolean isImmutableType() {
		return false;
	}

	@Override
	public boolean isStateful() {
		return true;
	}

	@Override
	public T createInstance() {
		checkKryoInitialized();
		return kryo.newInstance(typeToInstantiate);
	}

	@Override
	public T copy(T from, T reuse) {
		checkKryoInitialized();
		reuse = kryo.copy(from);
		return reuse;
	}

	@Override
	public int getLength() {
		return -1;
	}

	@Override
	public void serialize(T record, DataOutputView target) throws IOException {
		checkKryoInitialized();
		DataOutputViewStream outputStream = new DataOutputViewStream(target);
		Output out = new Output(outputStream);
		kryo.writeObject(out, record);
		out.flush();
	}

	@Override
	public T deserialize(T reuse, DataInputView source) throws IOException {
		checkKryoInitialized();
		DataInputViewStream inputStream = new DataInputViewStream(source);
		Input in = new NoFetchingInput(inputStream);
		reuse = kryo.readObject(in, typeToInstantiate);
		return reuse;
	}

	@Override
	public void copy(DataInputView source, DataOutputView target) throws IOException {
		checkKryoInitialized();
		if(this.copyInstance == null){
			this.copyInstance = createInstance();
		}

		T tmp = deserialize(copyInstance, source);
		serialize(tmp, target);
	}

	private final void checkKryoInitialized() {
		if (this.kryo == null) {
			this.kryo = new Kryo();
			this.kryo.setAsmEnabled(true);
			this.kryo.register(type);
		}
	}
}
