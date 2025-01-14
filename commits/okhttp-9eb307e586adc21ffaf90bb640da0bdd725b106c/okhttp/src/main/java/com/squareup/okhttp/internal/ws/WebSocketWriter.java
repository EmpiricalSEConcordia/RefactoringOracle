/*
 * Copyright (C) 2014 Square, Inc.
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
package com.squareup.okhttp.internal.ws;

import java.io.IOException;
import java.util.Random;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import okio.Timeout;

import static com.squareup.okhttp.WebSocket.PayloadType;
import static com.squareup.okhttp.internal.ws.Protocol.B0_FLAG_FIN;
import static com.squareup.okhttp.internal.ws.Protocol.B1_FLAG_MASK;
import static com.squareup.okhttp.internal.ws.Protocol.OPCODE_BINARY;
import static com.squareup.okhttp.internal.ws.Protocol.OPCODE_CONTINUATION;
import static com.squareup.okhttp.internal.ws.Protocol.OPCODE_CONTROL_CLOSE;
import static com.squareup.okhttp.internal.ws.Protocol.OPCODE_CONTROL_PING;
import static com.squareup.okhttp.internal.ws.Protocol.OPCODE_CONTROL_PONG;
import static com.squareup.okhttp.internal.ws.Protocol.OPCODE_TEXT;
import static com.squareup.okhttp.internal.ws.Protocol.PAYLOAD_LONG;
import static com.squareup.okhttp.internal.ws.Protocol.PAYLOAD_MAX;
import static com.squareup.okhttp.internal.ws.Protocol.PAYLOAD_SHORT;
import static com.squareup.okhttp.internal.ws.Protocol.toggleMask;

/**
 * An <a href="http://tools.ietf.org/html/rfc6455">RFC 6455</a>-compatible WebSocket frame writer.
 * <p>
 * This class is partially thread safe. Only a single "main" thread should be sending messages via
 * calls to {@link #newMessageSink} or {@link #sendMessage} as well as any calls to
 * {@link #writePing} or {@link #writeClose}. Other threads may call {@link #writePing},
 * {@link #writePong}, or {@link #writeClose} which will interleave on the wire with frames from
 * the main thread.
 */
public final class WebSocketWriter {
  private final boolean isClient;
  /** Writes must be guarded by synchronizing on this instance! */
  private final BufferedSink sink;
  private final Random random;

  private final FrameSink frameSink = new FrameSink();

  private boolean closed;
  private boolean activeWriter;

  private final byte[] maskKey = new byte[4];
  private final byte[] maskBuffer = new byte[2048];

  public WebSocketWriter(boolean isClient, BufferedSink sink, Random random) {
    this.isClient = isClient;
    this.sink = sink;
    this.random = random;
  }

  /** Send a ping with the supplied {@code payload}. Payload may be {@code null} */
  public void writePing(Buffer payload) throws IOException {
    if (closed) throw new IllegalStateException("Closed");

    synchronized (sink) {
      writeControlFrame(OPCODE_CONTROL_PING, payload);
    }
  }

  /** Send a pong with the supplied {@code payload}. Payload may be {@code null} */
  public void writePong(Buffer payload) throws IOException {
    if (closed) throw new IllegalStateException("Closed");

    synchronized (sink) {
      writeControlFrame(OPCODE_CONTROL_PONG, payload);
    }
  }

  /**
   * Send a close frame with optional code and reason.
   *
   * @param code Status code as defined by
   * <a href="http://tools.ietf.org/html/rfc6455#section-7.4">Section 7.4 of RFC 6455</a> or
   * {@code 0}.
   * @param reason Reason for shutting down or {@code null}. {@code code} is required if set.
   */
  public void writeClose(int code, String reason) throws IOException {
    if (closed) throw new IllegalStateException("Closed");

    Buffer buffer = null;
    if (code != 0) {
      if (code < 1000 || code >= 5000) {
        throw new IllegalArgumentException("Code must be in range [1000,5000).");
      }
      buffer = new Buffer();
      buffer.writeShort(code);
      if (reason != null) {
        buffer.writeUtf8(reason);
      }
    } else if (reason != null) {
      throw new IllegalArgumentException("Code required to include reason.");
    }

    synchronized (sink) {
      writeControlFrame(OPCODE_CONTROL_CLOSE, buffer);
      sink.close();
      closed = true;
    }
  }

  private void writeControlFrame(int opcode, Buffer payload) throws IOException {
    int length = 0;
    if (payload != null) {
      length = (int) payload.size();
      if (length > PAYLOAD_MAX) {
        throw new IllegalArgumentException(
            "Control frame payload must be less than " + PAYLOAD_MAX + "B.");
      }
    }

    int b0 = B0_FLAG_FIN | opcode;
    sink.writeByte(b0);

    int b1 = length;
    if (isClient) {
      b1 |= B1_FLAG_MASK;
      sink.writeByte(b1);

      random.nextBytes(maskKey);
      sink.write(maskKey);

      if (payload != null) {
        writeAllMasked(payload, length);
      }
    } else {
      sink.writeByte(b1);

      if (payload != null) {
        sink.writeAll(payload);
      }
    }

    sink.flush();
  }

  /**
   * Stream a message payload as a series of frames. This allows control frames to be interleaved
   * between parts of the message.
   */
  public BufferedSink newMessageSink(PayloadType type) {
    if (type == null) throw new NullPointerException("type == null");
    if (closed) throw new IllegalStateException("Closed");
    if (activeWriter) {
      throw new IllegalStateException("Another message writer is active. Did you call close()?");
    }
    activeWriter = true;

    frameSink.payloadType = type;
    frameSink.isFirstFrame = true;
    return Okio.buffer(frameSink);
  }

  /**
   * Send a message payload as a single frame. This will block any control frames that need sent
   * until it is completed.
   */
  public void sendMessage(PayloadType type, Buffer payload) throws IOException {
    if (type == null) throw new NullPointerException("type == null");
    if (payload == null) throw new NullPointerException("payload == null");
    if (closed) throw new IllegalStateException("Closed");
    if (activeWriter) {
      throw new IllegalStateException("A message writer is active. Did you call close()?");
    }
    writeFrame(type, payload, payload.size(), true /* first frame */, true /* final */);
  }

  private void writeFrame(PayloadType payloadType, Buffer source, long byteCount,
      boolean isFirstFrame, boolean isFinal) throws IOException {
    int opcode = OPCODE_CONTINUATION;
    if (isFirstFrame) {
      switch (payloadType) {
        case TEXT:
          opcode = OPCODE_TEXT;
          break;
        case BINARY:
          opcode = OPCODE_BINARY;
          break;
        default:
          throw new IllegalStateException("Unknown payload type: " + payloadType);
      }
    }

    synchronized (sink) {
      int b0 = opcode;
      if (isFinal) {
        b0 |= B0_FLAG_FIN;
      }
      sink.writeByte(b0);

      int b1 = 0;
      if (isClient) {
        b1 |= B1_FLAG_MASK;
        random.nextBytes(maskKey);
      }
      if (byteCount <= PAYLOAD_MAX) {
        b1 |= (int) byteCount;
        sink.writeByte(b1);
      } else if (byteCount <= Short.MAX_VALUE) {
        b1 |= PAYLOAD_SHORT;
        sink.writeByte(b1);
        sink.writeShort((int) byteCount);
      } else {
        b1 |= PAYLOAD_LONG;
        sink.writeByte(b1);
        sink.writeLong(byteCount);
      }

      if (isClient) {
        sink.write(maskKey);
        writeAllMasked(source, byteCount);
      } else {
        sink.write(source, byteCount);
      }
    }
  }

  private void writeAllMasked(BufferedSource source, long byteCount) throws IOException {
    long written = 0;
    while (written < byteCount) {
      int toRead = (int) Math.min(byteCount, maskBuffer.length);
      int read = source.read(maskBuffer, 0, toRead);
      if (read == -1) throw new AssertionError();
      toggleMask(maskBuffer, read, maskKey, written);
      sink.write(maskBuffer, 0, read);
      written += read;
    }
  }

  private final class FrameSink implements Sink {
    private PayloadType payloadType;
    private boolean isFirstFrame;

    @Override public void write(Buffer source, long byteCount) throws IOException {
      writeFrame(payloadType, source, byteCount, isFirstFrame, false /* final */);
      isFirstFrame = false;
    }

    @Override public void flush() throws IOException {
      synchronized (sink) {
        sink.flush();
      }
    }

    @Override public Timeout timeout() {
      return sink.timeout();
    }

    @SuppressWarnings("PointlessBitwiseExpression")
    @Override public void close() throws IOException {
      int length = 0;

      synchronized (sink) {
        sink.writeByte(B0_FLAG_FIN | OPCODE_CONTINUATION);

        if (isClient) {
          sink.writeByte(B1_FLAG_MASK | length);
          random.nextBytes(maskKey);
          sink.write(maskKey);
        } else {
          sink.writeByte(length);
        }
        sink.flush();
      }

      activeWriter = false;
    }
  }
}
