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

package eu.stratosphere.nephele.io.channels.bytebuffered;

import java.io.IOException;

import eu.stratosphere.nephele.event.task.AbstractEvent;
import eu.stratosphere.nephele.event.task.AbstractTaskEvent;
import eu.stratosphere.nephele.io.OutputGate;
import eu.stratosphere.nephele.io.channels.AbstractOutputChannel;
import eu.stratosphere.nephele.io.channels.Buffer;
import eu.stratosphere.nephele.io.channels.ChannelID;
import eu.stratosphere.nephele.io.channels.SerializationBuffer;
import eu.stratosphere.nephele.io.compression.CompressionEvent;
import eu.stratosphere.nephele.io.compression.CompressionLevel;
import eu.stratosphere.nephele.io.compression.CompressionLoader;
import eu.stratosphere.nephele.io.compression.Compressor;
import eu.stratosphere.nephele.types.Record;

public abstract class AbstractByteBufferedOutputChannel<T extends Record> extends AbstractOutputChannel<T> {

	/**
	 * The serialization buffer used to serialize records.
	 */
	private final SerializationBuffer<T> serializationBuffer = new SerializationBuffer<T>();

	/**
	 * Buffer for the compressed output data.
	 */
	private Buffer compressedDataBuffer = null;

	/**
	 * Stores whether the channel is requested to be closed.
	 */
	private boolean closeRequested = false;

	/**
	 * Stores whether the channel has received the acknowledgement
	 * for the close request from its connected input channel.
	 */
	private boolean closeAcknowledgementReceived = false;

	/**
	 * The output channel broker the channel should contact to request and release write buffers.
	 */
	private ByteBufferedOutputChannelBroker outputChannelBroker = null;

	/**
	 * Synchronization object to protect variables that are accessed by the task and the framework.
	 */
	private final Object synchronisationObject = new Object();

	/**
	 * Temporarily stores a possible IOException that may be reported by the framework.
	 */
	private IOException ioException = null;

	/**
	 * The compressor used to compress the outgoing data.
	 */
	private Compressor compressor = null;

	/**
	 * Buffer for the uncompressed data.
	 */
	private Buffer uncompressedDataBuffer = null;

	/**
	 * Creates a new byte buffered output channel.
	 * 
	 * @param outputGate
	 *        the output gate this channel is wired to
	 * @param channelIndex
	 *        the channel's index at the associated output gate
	 * @param channelID
	 *        the channel ID to assign to the new channel, <code>null</code> to generate a new ID
	 * @param compressionLevel
	 *        the level of compression to be used for this channel
	 */
	public AbstractByteBufferedOutputChannel(OutputGate<T> outputGate, int channelIndex, ChannelID channelID,
			CompressionLevel compressionLevel) {
		super(outputGate, channelIndex, channelID, compressionLevel);

		this.compressor = CompressionLoader.getCompressorByCompressionLevel(compressionLevel);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isClosed() throws IOException {

		if (this.closeRequested && this.uncompressedDataBuffer == null
			&& !this.serializationBuffer.dataLeftFromPreviousSerialization()) {

			if (this.ioException != null) {
				throw this.ioException;
			}

			synchronized (this.synchronisationObject) {
				if (this.closeAcknowledgementReceived) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void requestClose() throws IOException {

		if (!this.closeRequested) {
			this.closeRequested = true;
			transferEvent(new ByteBufferedChannelCloseEvent());
		}
	}

	/**
	 * Requests new write buffers from the framework. If compression is
	 * used the call will result an a request for two buffers, otherwise one
	 * buffer.
	 * This method blocks until the requested number of buffers is available.
	 * 
	 * @throws InterruptedException
	 *         throws if the thread is interrupted while waiting for the buffers
	 */
	private void requestWriteBuffersFromBroker() throws InterruptedException {

		final BufferPairResponse bufferPair = this.outputChannelBroker.requestEmptyWriteBuffers();
		this.compressedDataBuffer = bufferPair.getCompressedDataBuffer();
		this.uncompressedDataBuffer = bufferPair.getUncompressedDataBuffer();
		if (this.compressor != null) {

			this.compressor.setCompressedDataBuffer(this.compressedDataBuffer);
			this.compressor.setUncompressedDataBuffer(this.uncompressedDataBuffer);
		}
	}

	/**
	 * Returns the filled buffers to the framework and triggers
	 * further processing.
	 */
	private void releaseWriteBuffers() {

		if (getCompressionLevel() == CompressionLevel.DYNAMIC_COMPRESSION) {
			this.outputChannelBroker.transferEventToInputChannel(new CompressionEvent(this.compressor
				.getCurrentInternalCompressionLibraryIndex()));
		}

		this.outputChannelBroker.releaseWriteBuffers();
		this.compressedDataBuffer = null;
		this.uncompressedDataBuffer = null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void writeRecord(T record) throws IOException, InterruptedException {

		// Get a write buffer from the broker
		if (this.uncompressedDataBuffer == null) {

			requestWriteBuffersFromBroker();

			synchronized (this.synchronisationObject) {

				if (this.ioException != null) {
					throw this.ioException;
				}
			}
		}

		if (this.closeRequested) {
			throw new IOException("Channel is aready requested to be closed");
		}

		// Check if we can accept new records or if there are still old
		// records to be transmitted
		if (this.compressor != null) {
			while (this.serializationBuffer.dataLeftFromPreviousSerialization()) {

				this.serializationBuffer.read(this.uncompressedDataBuffer);
				if (this.uncompressedDataBuffer.remaining() == 0) {

					this.compressor.compress();
					// this.leasedWriteBuffer.flip();
					releaseWriteBuffers();
					requestWriteBuffersFromBroker();
				}
			}
		} else {
			while (this.serializationBuffer.dataLeftFromPreviousSerialization()) {

				this.serializationBuffer.read(this.uncompressedDataBuffer);
				if (this.uncompressedDataBuffer.remaining() == 0) {
					releaseWriteBuffers();
					requestWriteBuffersFromBroker();
				}
			}
		}

		if (this.serializationBuffer.dataLeftFromPreviousSerialization()) {
			throw new IOException("Serialization buffer is expected to be empty!");
		}

		this.serializationBuffer.serialize(record);

		if (this.compressor != null) {
			this.serializationBuffer.read(this.uncompressedDataBuffer);

			if (this.uncompressedDataBuffer.remaining() == 0) {
				this.compressor.compress();
				// this.leasedWriteBuffer.flip();
				releaseWriteBuffers();
			}
		} else {
			this.serializationBuffer.read(this.uncompressedDataBuffer);
			if (this.uncompressedDataBuffer.remaining() == 0) {
				releaseWriteBuffers();
			}
		}
	}

	/**
	 * Sets the output channel broker this channel should contact to request and release write buffers.
	 * 
	 * @param byteBufferedOutputChannelBroker
	 *        the output channel broker the channel should contact to request and release write buffers
	 */
	public void setByteBufferedOutputChannelBroker(ByteBufferedOutputChannelBroker byteBufferedOutputChannelBroker) {
		this.outputChannelBroker = byteBufferedOutputChannelBroker;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processEvent(AbstractEvent event) {

		if (event instanceof AbstractTaskEvent) {
			getOutputGate().deliverEvent((AbstractTaskEvent) event);
		} else if (event instanceof ByteBufferedChannelCloseEvent) {
			synchronized (this.synchronisationObject) {
				this.closeAcknowledgementReceived = true;
			}
		} else {
			System.out.println("Received unknown event: " + event);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void transferEvent(AbstractEvent event) throws IOException {

		this.outputChannelBroker.transferEventToInputChannel(event);
		flush();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void flush() throws IOException {

		// Get rid of remaining data in the serialization buffer
		while (this.serializationBuffer.dataLeftFromPreviousSerialization()) {

			if (this.uncompressedDataBuffer == null) {

				try {
					requestWriteBuffersFromBroker();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (this.compressor != null) {
				this.serializationBuffer.read(this.uncompressedDataBuffer);
				if (this.uncompressedDataBuffer.remaining() == 0) {
					this.compressor.compress();
					// this.leasedWriteBuffer.flip();
					releaseWriteBuffers();
				}
			} else {
				this.serializationBuffer.read(this.uncompressedDataBuffer);
				if (this.uncompressedDataBuffer.remaining() == 0) {
					releaseWriteBuffers();
				}
			}
		}

		// Get rid of the leased write buffer
		if (this.compressor != null) {
			if (this.uncompressedDataBuffer != null) {
				this.compressor.compress();

				// this.leasedWriteBuffer.flip();
				releaseWriteBuffers();
			}
		} else {
			if (this.uncompressedDataBuffer != null) {
				releaseWriteBuffers();
			}
		}
	}

	/**
	 * This method is called by the framework to report
	 * an {@link IOException} that occurred while trying to process
	 * one of the buffers issued by this channel.
	 * 
	 * @param ioe
	 *        the {@link IOException} which occurred
	 */
	public void reportIOException(IOException ioe) {

		synchronized (this.synchronisationObject) {
			this.ioException = ioe;
			// Wake up thread if it has been throttled down
			this.synchronisationObject.notify();
		}
	}
}
