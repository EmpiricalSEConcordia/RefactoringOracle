/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.reactive.web.dispatch.method.annotation;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.reactivestreams.Publisher;
import reactor.rx.Stream;
import reactor.rx.Streams;
import rx.Observable;
import rx.RxReactiveStreams;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.reactive.codec.decoder.ByteToMessageDecoder;
import org.springframework.reactive.web.dispatch.method.HandlerMethodArgumentResolver;
import org.springframework.reactive.web.http.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author Sebastien Deleuze
 */
public class RequestBodyArgumentResolver implements HandlerMethodArgumentResolver {

	private static final Charset UTF_8 = Charset.forName("UTF-8");

	private final List<ByteToMessageDecoder<?>> deserializers;
	private final List<ByteToMessageDecoder<ByteBuffer>> preProcessors;


	public RequestBodyArgumentResolver(List<ByteToMessageDecoder<?>> deserializers) {
		this(deserializers, Collections.EMPTY_LIST);
	}

	public RequestBodyArgumentResolver(List<ByteToMessageDecoder<?>> deserializers, List<ByteToMessageDecoder<ByteBuffer>> preProcessors) {
		this.deserializers = deserializers;
		this.preProcessors = preProcessors;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(RequestBody.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ServerHttpRequest request) {

		MediaType mediaType = resolveMediaType(request);
		ResolvableType type = ResolvableType.forMethodParameter(parameter);
		List<Object> hints = new ArrayList<>();
		hints.add(UTF_8);
		// TODO: Refactor type conversion
		ResolvableType readType = type;
		if (Observable.class.isAssignableFrom(type.getRawClass()) || Publisher.class.isAssignableFrom(type.getRawClass())) {
			readType = type.getGeneric(0);
		}

		ByteToMessageDecoder<?> deserializer = resolveDeserializers(request, type, mediaType, hints.toArray());
		if (deserializer != null) {

			Publisher<ByteBuffer> inputStream = Streams.wrap(request.getBody()).map(bytes -> ByteBuffer.wrap(bytes));
			List<ByteToMessageDecoder<ByteBuffer>> preProcessors = resolvePreProcessors(request, type, mediaType, hints.toArray());
			for (ByteToMessageDecoder<ByteBuffer> preProcessor : preProcessors) {
				inputStream = preProcessor.decode(inputStream, type, mediaType, hints.toArray());
			}
			Publisher<?> elementStream = deserializer.decode(inputStream, readType, mediaType, UTF_8);

			// TODO: Refactor type conversion
			if (Stream.class.isAssignableFrom(type.getRawClass())) {
				return Streams.wrap(elementStream);
			}
			else if (Observable.class.isAssignableFrom(type.getRawClass())) {
				return RxReactiveStreams.toObservable(elementStream);
			}
			else if (Publisher.class.isAssignableFrom(type.getRawClass())) {
				return elementStream;
			}
			else {
				try {
					return Streams.wrap(elementStream).next().await();
				} catch(InterruptedException ex) {
					throw new IllegalStateException("Timeout before getter the value");
				}
			}
		}
		throw new IllegalStateException("Argument type not supported: " + type);
	}

	private MediaType resolveMediaType(ServerHttpRequest request) {
		String acceptHeader = request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
		List<MediaType> mediaTypes = MediaType.parseMediaTypes(acceptHeader);
		MediaType.sortBySpecificityAndQuality(mediaTypes);
		return ( mediaTypes.size() > 0 ? mediaTypes.get(0) : MediaType.TEXT_PLAIN);
	}

	private ByteToMessageDecoder<?> resolveDeserializers(ServerHttpRequest request, ResolvableType type,  MediaType mediaType, Object[] hints) {
		for (ByteToMessageDecoder<?> deserializer : this.deserializers) {
			if (deserializer.canDecode(type, mediaType, hints)) {
				return deserializer;
			}
		}
		return null;
	}

	private List<ByteToMessageDecoder<ByteBuffer>> resolvePreProcessors(ServerHttpRequest request, ResolvableType type, MediaType mediaType, Object[] hints) {
		List<ByteToMessageDecoder<ByteBuffer>> preProcessors = new ArrayList<>();
		for (ByteToMessageDecoder<ByteBuffer> preProcessor : this.preProcessors) {
			if (preProcessor.canDecode(type, mediaType, hints)) {
				preProcessors.add(preProcessor);
			}
		}
		return preProcessors;
	}
}
