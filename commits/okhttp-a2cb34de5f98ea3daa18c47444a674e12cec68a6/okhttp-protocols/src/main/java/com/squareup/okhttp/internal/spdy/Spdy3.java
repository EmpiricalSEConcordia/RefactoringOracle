/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.squareup.okhttp.internal.spdy;

import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.internal.Platform;
import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.bytes.ByteString;
import com.squareup.okhttp.internal.bytes.Deadline;
import com.squareup.okhttp.internal.bytes.OkBuffer;
import com.squareup.okhttp.internal.bytes.OkBuffers;
import com.squareup.okhttp.internal.bytes.Source;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ProtocolException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;

/**
 * Read and write spdy/3.1 frames.
 * http://www.chromium.org/spdy/spdy-protocol/spdy-protocol-draft3-1
 */
final class Spdy3 implements Variant {

  @Override public Protocol getProtocol() {
    return Protocol.SPDY_3;
  }

  static final int TYPE_DATA = 0x0;
  static final int TYPE_SYN_STREAM = 0x1;
  static final int TYPE_SYN_REPLY = 0x2;
  static final int TYPE_RST_STREAM = 0x3;
  static final int TYPE_SETTINGS = 0x4;
  static final int TYPE_PING = 0x6;
  static final int TYPE_GOAWAY = 0x7;
  static final int TYPE_HEADERS = 0x8;
  static final int TYPE_WINDOW_UPDATE = 0x9;

  static final int FLAG_FIN = 0x1;
  static final int FLAG_UNIDIRECTIONAL = 0x2;

  static final int VERSION = 3;

  static final byte[] DICTIONARY;
  static {
    try {
      DICTIONARY = ("\u0000\u0000\u0000\u0007options\u0000\u0000\u0000\u0004hea"
          + "d\u0000\u0000\u0000\u0004post\u0000\u0000\u0000\u0003put\u0000\u0000\u0000\u0006dele"
          + "te\u0000\u0000\u0000\u0005trace\u0000\u0000\u0000\u0006accept\u0000\u0000\u0000"
          + "\u000Eaccept-charset\u0000\u0000\u0000\u000Faccept-encoding\u0000\u0000\u0000\u000Fa"
          + "ccept-language\u0000\u0000\u0000\raccept-ranges\u0000\u0000\u0000\u0003age\u0000"
          + "\u0000\u0000\u0005allow\u0000\u0000\u0000\rauthorization\u0000\u0000\u0000\rcache-co"
          + "ntrol\u0000\u0000\u0000\nconnection\u0000\u0000\u0000\fcontent-base\u0000\u0000"
          + "\u0000\u0010content-encoding\u0000\u0000\u0000\u0010content-language\u0000\u0000"
          + "\u0000\u000Econtent-length\u0000\u0000\u0000\u0010content-location\u0000\u0000\u0000"
          + "\u000Bcontent-md5\u0000\u0000\u0000\rcontent-range\u0000\u0000\u0000\fcontent-type"
          + "\u0000\u0000\u0000\u0004date\u0000\u0000\u0000\u0004etag\u0000\u0000\u0000\u0006expe"
          + "ct\u0000\u0000\u0000\u0007expires\u0000\u0000\u0000\u0004from\u0000\u0000\u0000"
          + "\u0004host\u0000\u0000\u0000\bif-match\u0000\u0000\u0000\u0011if-modified-since"
          + "\u0000\u0000\u0000\rif-none-match\u0000\u0000\u0000\bif-range\u0000\u0000\u0000"
          + "\u0013if-unmodified-since\u0000\u0000\u0000\rlast-modified\u0000\u0000\u0000\blocati"
          + "on\u0000\u0000\u0000\fmax-forwards\u0000\u0000\u0000\u0006pragma\u0000\u0000\u0000"
          + "\u0012proxy-authenticate\u0000\u0000\u0000\u0013proxy-authorization\u0000\u0000"
          + "\u0000\u0005range\u0000\u0000\u0000\u0007referer\u0000\u0000\u0000\u000Bretry-after"
          + "\u0000\u0000\u0000\u0006server\u0000\u0000\u0000\u0002te\u0000\u0000\u0000\u0007trai"
          + "ler\u0000\u0000\u0000\u0011transfer-encoding\u0000\u0000\u0000\u0007upgrade\u0000"
          + "\u0000\u0000\nuser-agent\u0000\u0000\u0000\u0004vary\u0000\u0000\u0000\u0003via"
          + "\u0000\u0000\u0000\u0007warning\u0000\u0000\u0000\u0010www-authenticate\u0000\u0000"
          + "\u0000\u0006method\u0000\u0000\u0000\u0003get\u0000\u0000\u0000\u0006status\u0000"
          + "\u0000\u0000\u0006200 OK\u0000\u0000\u0000\u0007version\u0000\u0000\u0000\bHTTP/1.1"
          + "\u0000\u0000\u0000\u0003url\u0000\u0000\u0000\u0006public\u0000\u0000\u0000\nset-coo"
          + "kie\u0000\u0000\u0000\nkeep-alive\u0000\u0000\u0000\u0006origin100101201202205206300"
          + "302303304305306307402405406407408409410411412413414415416417502504505203 Non-Authori"
          + "tative Information204 No Content301 Moved Permanently400 Bad Request401 Unauthorized"
          + "403 Forbidden404 Not Found500 Internal Server Error501 Not Implemented503 Service Un"
          + "availableJan Feb Mar Apr May Jun Jul Aug Sept Oct Nov Dec 00:00:00 Mon, Tue, Wed, Th"
          + "u, Fri, Sat, Sun, GMTchunked,text/html,image/png,image/jpg,image/gif,application/xml"
          + ",application/xhtml+xml,text/plain,text/javascript,publicprivatemax-age=gzip,deflate,"
          + "sdchcharset=utf-8charset=iso-8859-1,utf-,*,enq=0.").getBytes(Util.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError();
    }
  }

  @Override public FrameReader newReader(InputStream in, boolean client) {
    return new Reader(OkBuffers.source(in), client);
  }

  @Override public FrameWriter newWriter(OutputStream out, boolean client) {
    return new Writer(out, client);
  }

  /** Read spdy/3 frames. */
  static final class Reader implements FrameReader {
    private final OkBuffer buffer = new OkBuffer();
    private final Source source;
    private final boolean client;
    private final NameValueBlockReader headerBlockReader;

    Reader(Source source, boolean client) {
      this.source = source;
      this.headerBlockReader = new NameValueBlockReader(buffer, source);
      this.client = client;
    }

    @Override public void readConnectionHeader() {
    }

    /**
     * Send the next frame to {@code handler}. Returns true unless there are no
     * more frames on the stream.
     */
    @Override public boolean nextFrame(Handler handler) throws IOException {
      try {
        OkBuffers.require(source, buffer, 8, Deadline.NONE);
      } catch (IOException e) {
        return false; // This might be a normal socket close.
      }
      int w1 = buffer.readInt();
      int w2 = buffer.readInt();

      boolean control = (w1 & 0x80000000) != 0;
      int flags = (w2 & 0xff000000) >>> 24;
      int length = (w2 & 0xffffff);

      if (control) {
        int version = (w1 & 0x7fff0000) >>> 16;
        int type = (w1 & 0xffff);

        if (version != 3) {
          throw new ProtocolException("version != 3: " + version);
        }

        switch (type) {
          case TYPE_SYN_STREAM:
            readSynStream(handler, flags, length);
            return true;

          case TYPE_SYN_REPLY:
            readSynReply(handler, flags, length);
            return true;

          case TYPE_RST_STREAM:
            readRstStream(handler, flags, length);
            return true;

          case TYPE_SETTINGS:
            readSettings(handler, flags, length);
            return true;

          case TYPE_PING:
            readPing(handler, flags, length);
            return true;

          case TYPE_GOAWAY:
            readGoAway(handler, flags, length);
            return true;

          case TYPE_HEADERS:
            readHeaders(handler, flags, length);
            return true;

          case TYPE_WINDOW_UPDATE:
            readWindowUpdate(handler, flags, length);
            return true;

          default:
            Logger logger = Logger.getLogger("com.squareup.okhttp.internal.spdy.Spdy3");
            logger.log(Level.INFO, "Ignoring unknown frame type " + type);
            OkBuffers.skip(source, buffer, length, Deadline.NONE);
            return true;
        }
      } else {
        int streamId = w1 & 0x7fffffff;
        boolean inFinished = (flags & FLAG_FIN) != 0;
        handler.data(inFinished, streamId, OkBuffers.inputStream(source, buffer), length);
        return true;
      }
    }

    private void readSynStream(Handler handler, int flags, int length) throws IOException {
      OkBuffers.require(source, buffer, 12, Deadline.NONE);
      int w1 = buffer.readInt();
      int w2 = buffer.readInt();
      int s3 = buffer.readShort();
      int streamId = w1 & 0x7fffffff;
      int associatedStreamId = w2 & 0x7fffffff;
      int priority = (s3 & 0xe000) >>> 13;
      // int slot = s3 & 0xff;
      List<Header> headerBlock = headerBlockReader.readNameValueBlock(length - 10);

      boolean inFinished = (flags & FLAG_FIN) != 0;
      boolean outFinished = (flags & FLAG_UNIDIRECTIONAL) != 0;
      handler.headers(outFinished, inFinished, streamId, associatedStreamId, priority,
          headerBlock, HeadersMode.SPDY_SYN_STREAM);
    }

    private void readSynReply(Handler handler, int flags, int length) throws IOException {
      OkBuffers.require(source, buffer, 4, Deadline.NONE);
      int w1 = buffer.readInt();
      int streamId = w1 & 0x7fffffff;
      List<Header> headerBlock = headerBlockReader.readNameValueBlock(length - 4);
      boolean inFinished = (flags & FLAG_FIN) != 0;
      handler.headers(false, inFinished, streamId, -1, -1, headerBlock, HeadersMode.SPDY_REPLY);
    }

    private void readRstStream(Handler handler, int flags, int length) throws IOException {
      if (length != 8) throw ioException("TYPE_RST_STREAM length: %d != 8", length);
      OkBuffers.require(source, buffer, 8, Deadline.NONE);
      int streamId = buffer.readInt() & 0x7fffffff;
      int errorCodeInt = buffer.readInt();
      ErrorCode errorCode = ErrorCode.fromSpdy3Rst(errorCodeInt);
      if (errorCode == null) {
        throw ioException("TYPE_RST_STREAM unexpected error code: %d", errorCodeInt);
      }
      handler.rstStream(streamId, errorCode);
    }

    private void readHeaders(Handler handler, int flags, int length) throws IOException {
      OkBuffers.require(source, buffer, 4, Deadline.NONE);
      int w1 = buffer.readInt();
      int streamId = w1 & 0x7fffffff;
      List<Header> headerBlock = headerBlockReader.readNameValueBlock(length - 4);
      handler.headers(false, false, streamId, -1, -1, headerBlock, HeadersMode.SPDY_HEADERS);
    }

    private void readWindowUpdate(Handler handler, int flags, int length) throws IOException {
      if (length != 8) throw ioException("TYPE_WINDOW_UPDATE length: %d != 8", length);
      OkBuffers.require(source, buffer, 8, Deadline.NONE);
      int w1 = buffer.readInt();
      int w2 = buffer.readInt();
      int streamId = w1 & 0x7fffffff;
      long increment = w2 & 0x7fffffff;
      if (increment == 0) throw ioException("windowSizeIncrement was 0", increment);
      handler.windowUpdate(streamId, increment);
    }

    private void readPing(Handler handler, int flags, int length) throws IOException {
      if (length != 4) throw ioException("TYPE_PING length: %d != 4", length);
      OkBuffers.require(source, buffer, 4, Deadline.NONE);
      int id = buffer.readInt();
      boolean ack = client == ((id & 1) == 1);
      handler.ping(ack, id, 0);
    }

    private void readGoAway(Handler handler, int flags, int length) throws IOException {
      if (length != 8) throw ioException("TYPE_GOAWAY length: %d != 8", length);
      OkBuffers.require(source, buffer, 8, Deadline.NONE);
      int lastGoodStreamId = buffer.readInt() & 0x7fffffff;
      int errorCodeInt = buffer.readInt();
      ErrorCode errorCode = ErrorCode.fromSpdyGoAway(errorCodeInt);
      if (errorCode == null) {
        throw ioException("TYPE_GOAWAY unexpected error code: %d", errorCodeInt);
      }
      handler.goAway(lastGoodStreamId, errorCode, Util.EMPTY_BYTE_ARRAY);
    }

    private void readSettings(Handler handler, int flags, int length) throws IOException {
      OkBuffers.require(source, buffer, 4, Deadline.NONE);
      int numberOfEntries = buffer.readInt();
      if (length != 4 + 8 * numberOfEntries) {
        throw ioException("TYPE_SETTINGS length: %d != 4 + 8 * %d", length, numberOfEntries);
      }
      OkBuffers.require(source, buffer, 8 * numberOfEntries, Deadline.NONE);
      Settings settings = new Settings();
      for (int i = 0; i < numberOfEntries; i++) {
        int w1 = buffer.readInt();
        int value = buffer.readInt();
        int idFlags = (w1 & 0xff000000) >>> 24;
        int id = w1 & 0xffffff;
        settings.set(id, idFlags, value);
      }
      boolean clearPrevious = (flags & Settings.FLAG_CLEAR_PREVIOUSLY_PERSISTED_SETTINGS) != 0;
      handler.settings(clearPrevious, settings);
    }

    private static IOException ioException(String message, Object... args) throws IOException {
      throw new IOException(String.format(message, args));
    }

    @Override public void close() throws IOException {
      headerBlockReader.close(Deadline.NONE);
    }
  }

  /** Write spdy/3 frames. */
  static final class Writer implements FrameWriter {
    private final DataOutputStream out;
    private final ByteArrayOutputStream headerBlockBuffer;
    private final DataOutputStream headerBlockOut;
    private final boolean client;

    Writer(OutputStream out, boolean client) {
      this.out = new DataOutputStream(out);
      this.client = client;

      Deflater deflater = new Deflater();
      deflater.setDictionary(DICTIONARY);
      headerBlockBuffer = new ByteArrayOutputStream();
      headerBlockOut = new DataOutputStream(
          Platform.get().newDeflaterOutputStream(headerBlockBuffer, deflater, true));
    }

    @Override public void ackSettings() {
      // Do nothing: no ACK for SPDY/3 settings.
    }

    @Override
    public void pushPromise(int streamId, int promisedStreamId, List<Header> requestHeaders)
        throws IOException {
      // Do nothing: no push promise for SPDY/3.
    }

    @Override public synchronized void connectionHeader() {
      // Do nothing: no connection header for SPDY/3.
    }

    @Override public synchronized void flush() throws IOException {
      out.flush();
    }

    @Override
    public synchronized void synStream(boolean outFinished, boolean inFinished, int streamId,
        int associatedStreamId, int priority, int slot, List<Header> headerBlock)
        throws IOException {
      writeNameValueBlockToBuffer(headerBlock);
      int length = 10 + headerBlockBuffer.size();
      int type = TYPE_SYN_STREAM;
      int flags = (outFinished ? FLAG_FIN : 0) | (inFinished ? FLAG_UNIDIRECTIONAL : 0);

      int unused = 0;
      out.writeInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
      out.writeInt((flags & 0xff) << 24 | length & 0xffffff);
      out.writeInt(streamId & 0x7fffffff);
      out.writeInt(associatedStreamId & 0x7fffffff);
      out.writeShort((priority & 0x7) << 13 | (unused & 0x1f) << 8 | (slot & 0xff));
      headerBlockBuffer.writeTo(out);
      out.flush();
    }

    @Override public synchronized void synReply(boolean outFinished, int streamId,
        List<Header> headerBlock) throws IOException {
      writeNameValueBlockToBuffer(headerBlock);
      int type = TYPE_SYN_REPLY;
      int flags = (outFinished ? FLAG_FIN : 0);
      int length = headerBlockBuffer.size() + 4;

      out.writeInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
      out.writeInt((flags & 0xff) << 24 | length & 0xffffff);
      out.writeInt(streamId & 0x7fffffff);
      headerBlockBuffer.writeTo(out);
      out.flush();
    }

    @Override public synchronized void headers(int streamId, List<Header> headerBlock)
        throws IOException {
      writeNameValueBlockToBuffer(headerBlock);
      int flags = 0;
      int type = TYPE_HEADERS;
      int length = headerBlockBuffer.size() + 4;

      out.writeInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
      out.writeInt((flags & 0xff) << 24 | length & 0xffffff);
      out.writeInt(streamId & 0x7fffffff);
      headerBlockBuffer.writeTo(out);
    }

    @Override public synchronized void rstStream(int streamId, ErrorCode errorCode)
        throws IOException {
      if (errorCode.spdyRstCode == -1) throw new IllegalArgumentException();
      int flags = 0;
      int type = TYPE_RST_STREAM;
      int length = 8;
      out.writeInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
      out.writeInt((flags & 0xff) << 24 | length & 0xffffff);
      out.writeInt(streamId & 0x7fffffff);
      out.writeInt(errorCode.spdyRstCode);
      out.flush();
    }

    @Override public synchronized void data(boolean outFinished, int streamId, byte[] data)
        throws IOException {
      data(outFinished, streamId, data, 0, data.length);
    }

    @Override public synchronized void data(boolean outFinished, int streamId, byte[] data,
        int offset, int byteCount) throws IOException {
      // TODO: Implement looping strategy.
      int flags = (outFinished ? FLAG_FIN : 0);
      sendDataFrame(streamId, flags, data, offset, byteCount);
    }

    void sendDataFrame(int streamId, int flags, byte[] data, int offset, int byteCount)
        throws IOException {
      if (byteCount > 0xffffffL) {
        throw new IllegalArgumentException("FRAME_TOO_LARGE max size is 16Mib: " + byteCount);
      }
      out.writeInt(streamId & 0x7fffffff);
      out.writeInt((flags & 0xff) << 24 | byteCount & 0xffffff);
      out.write(data, offset, byteCount);
    }

    private void writeNameValueBlockToBuffer(List<Header> headerBlock) throws IOException {
      headerBlockBuffer.reset();
      headerBlockOut.writeInt(headerBlock.size());
      for (int i = 0, size = headerBlock.size(); i < size; i++) {
        ByteString name = headerBlock.get(i).name;
        headerBlockOut.writeInt(name.size());
        name.write(headerBlockOut);
        ByteString value = headerBlock.get(i).value;
        headerBlockOut.writeInt(value.size());
        value.write(headerBlockOut);
      }
      headerBlockOut.flush();
    }

    @Override public synchronized void settings(Settings settings) throws IOException {
      int type = TYPE_SETTINGS;
      int flags = 0;
      int size = settings.size();
      int length = 4 + size * 8;
      out.writeInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
      out.writeInt((flags & 0xff) << 24 | length & 0xffffff);
      out.writeInt(size);
      for (int i = 0; i <= Settings.COUNT; i++) {
        if (!settings.isSet(i)) continue;
        int settingsFlags = settings.flags(i);
        out.writeInt((settingsFlags & 0xff) << 24 | (i & 0xffffff));
        out.writeInt(settings.get(i));
      }
      out.flush();
    }

    @Override public synchronized void ping(boolean reply, int payload1, int payload2)
        throws IOException {
      boolean payloadIsReply = client != ((payload1 & 1) == 1);
      if (reply != payloadIsReply) throw new IllegalArgumentException("payload != reply");
      int type = TYPE_PING;
      int flags = 0;
      int length = 4;
      out.writeInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
      out.writeInt((flags & 0xff) << 24 | length & 0xffffff);
      out.writeInt(payload1);
      out.flush();
    }

    @Override
    public synchronized void goAway(int lastGoodStreamId, ErrorCode errorCode, byte[] ignored)
        throws IOException {
      if (errorCode.spdyGoAwayCode == -1) {
        throw new IllegalArgumentException("errorCode.spdyGoAwayCode == -1");
      }
      int type = TYPE_GOAWAY;
      int flags = 0;
      int length = 8;
      out.writeInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
      out.writeInt((flags & 0xff) << 24 | length & 0xffffff);
      out.writeInt(lastGoodStreamId);
      out.writeInt(errorCode.spdyGoAwayCode);
      out.flush();
    }

    @Override public synchronized void windowUpdate(int streamId, long increment)
        throws IOException {
      if (increment == 0 || increment > 0x7fffffffL) {
        throw new IllegalArgumentException(
            "windowSizeIncrement must be between 1 and 0x7fffffff: " + increment);
      }
      int type = TYPE_WINDOW_UPDATE;
      int flags = 0;
      int length = 8;
      out.writeInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
      out.writeInt((flags & 0xff) << 24 | length & 0xffffff);
      out.writeInt(streamId);
      out.writeInt((int) increment);
      out.flush();
    }

    @Override public void close() throws IOException {
      Util.closeAll(out, headerBlockOut);
    }
  }
}
