/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.core.codec.support;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.test.TestSubscriber;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.AbstractDataBufferAllocatingTestCase;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;

import static org.junit.Assert.*;

/**
 * @author Sebastien Deleuze
 */
public class ByteBufferEncoderTests extends AbstractDataBufferAllocatingTestCase {

	private ByteBufferEncoder encoder;

	@Before
	public void createEncoder() {
		this.encoder = new ByteBufferEncoder();
	}

	@Test
	public void canEncode() {
		assertTrue(this.encoder.canEncode(ResolvableType.forClass(ByteBuffer.class),
				MediaType.TEXT_PLAIN));
		assertFalse(this.encoder
				.canEncode(ResolvableType.forClass(Integer.class), MediaType.TEXT_PLAIN));
		assertTrue(this.encoder.canEncode(ResolvableType.forClass(ByteBuffer.class),
				MediaType.APPLICATION_JSON));
	}

	@Test
	public void encode() {
		byte[] fooBytes = "foo".getBytes(StandardCharsets.UTF_8);
		byte[] barBytes = "bar".getBytes(StandardCharsets.UTF_8);
		Flux<ByteBuffer> source =
				Flux.just(ByteBuffer.wrap(fooBytes), ByteBuffer.wrap(barBytes));

		Flux<DataBuffer> output = this.encoder.encode(source, this.allocator,
				ResolvableType.forClassWithGenerics(Publisher.class, ByteBuffer.class),
				null);
		TestSubscriber<DataBuffer> testSubscriber = new TestSubscriber<>();
		testSubscriber.bindTo(output)
				.assertValuesWith(b -> {
					byte[] buf = new byte[3];
					b.read(buf);
					assertArrayEquals(fooBytes, buf);
				}, b -> {
					byte[] buf = new byte[3];
					b.read(buf);
					assertArrayEquals(barBytes, buf);
				});
	}

}
