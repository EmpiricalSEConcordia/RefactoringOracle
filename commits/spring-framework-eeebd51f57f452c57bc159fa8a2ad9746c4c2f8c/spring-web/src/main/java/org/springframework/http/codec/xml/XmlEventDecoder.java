/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.http.codec.xml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.XMLEventAllocator;

import com.fasterxml.aalto.AsyncByteBufferFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.evt.EventAllocatorImpl;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.xml.StaxUtils;

/**
 * Decodes a {@link DataBuffer} stream into a stream of {@link XMLEvent DataBuffer} stream into a stream of {@link XMLEvents}.
 * That is, given the following XML:
 *
 * <pre>
 * &lt;root>
 *     &lt;child&gt;foo&lt;/child&gt;
 *     &lt;child&gt;bar&lt;/child&gt;
 * &lt;/root&gt;
 * </pre>
 *
 * this method with result in a flux with the following events:
 * <ol>
 * <li>{@link javax.xml.stream.events.StartDocument}</li>
 * <li>{@link javax.xml.stream.events.StartElement} {@code root}</li>
 * <li>{@link javax.xml.stream.events.StartElement} {@code child}</li>
 * <li>{@link javax.xml.stream.events.Characters} {@code foo}</li>
 * <li>{@link javax.xml.stream.events.EndElement} {@code child}</li>
 * <li>{@link javax.xml.stream.events.StartElement} {@code child}</li>
 * <li>{@link javax.xml.stream.events.Characters} {@code bar}</li>
 * <li>{@link javax.xml.stream.events.EndElement} {@code child}</li>
 * <li>{@link javax.xml.stream.events.EndElement} {@code root}</li>
 * </ol>
 *
 * Note that this decoder is not registered by default but used internally
 * by other decoders who are there by default.
 *
 * @author Arjen Poutsma
 * @since 5.0
 */
public class XmlEventDecoder extends AbstractDecoder<XMLEvent> {

	private static final XMLInputFactory inputFactory = StaxUtils.createDefensiveInputFactory();

	private static final boolean aaltoPresent = ClassUtils.isPresent(
			"com.fasterxml.aalto.AsyncXMLStreamReader", XmlEventDecoder.class.getClassLoader());

	boolean useAalto = aaltoPresent;


	public XmlEventDecoder() {
		super(MimeTypeUtils.APPLICATION_XML, MimeTypeUtils.TEXT_XML);
	}


	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})  // on JDK 9 where XMLEventReader is Iterator<Object>
	public Flux<XMLEvent> decode(Publisher<DataBuffer> inputStream, ResolvableType elementType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		Flux<DataBuffer> flux = Flux.from(inputStream);
		if (useAalto) {
			AaltoDataBufferToXmlEvent aaltoMapper = new AaltoDataBufferToXmlEvent();
			return flux.flatMap(aaltoMapper)
					.doFinally(signalType -> aaltoMapper.endOfInput());
		}
		else {
			Mono<DataBuffer> singleBuffer = DataBufferUtils.join(flux);
			return singleBuffer.
					flatMapMany(dataBuffer -> {
						try {
							InputStream is = dataBuffer.asInputStream();
							Iterator eventReader = inputFactory.createXMLEventReader(is);
							return Flux.fromIterable((Iterable<XMLEvent>) () -> eventReader)
									.doFinally(t -> DataBufferUtils.release(dataBuffer));
						}
						catch (XMLStreamException ex) {
							return Mono.error(ex);
						}
					});
		}
	}


	/*
	 * Separate static class to isolate Aalto dependency.
	 */
	private static class AaltoDataBufferToXmlEvent implements Function<DataBuffer, Publisher<? extends XMLEvent>> {

		private static final AsyncXMLInputFactory inputFactory = new InputFactoryImpl();

		private final AsyncXMLStreamReader<AsyncByteBufferFeeder> streamReader =
				inputFactory.createAsyncForByteBuffer();

		private final XMLEventAllocator eventAllocator = EventAllocatorImpl.getDefaultInstance();

		@Override
		public Publisher<? extends XMLEvent> apply(DataBuffer dataBuffer) {
			try {
				streamReader.getInputFeeder().feedInput(dataBuffer.asByteBuffer());
				List<XMLEvent> events = new ArrayList<>();
				while (true) {
					if (streamReader.next() == AsyncXMLStreamReader.EVENT_INCOMPLETE) {
						// no more events with what currently has been fed to the reader
						break;
					}
					else {
						XMLEvent event = eventAllocator.allocate(streamReader);
						events.add(event);
						if (event.isEndDocument()) {
							break;
						}
					}
				}
				return Flux.fromIterable(events);
			}
			catch (XMLStreamException ex) {
				return Mono.error(ex);
			}
			finally {
				DataBufferUtils.release(dataBuffer);
			}
		}

		public void endOfInput() {
			this.streamReader.getInputFeeder().endOfInput();
		}
	}

}
