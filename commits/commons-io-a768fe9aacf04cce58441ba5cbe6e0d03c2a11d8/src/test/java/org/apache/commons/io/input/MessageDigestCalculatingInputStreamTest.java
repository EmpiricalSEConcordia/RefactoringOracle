/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.io.input;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.Random;

import org.junit.Test;

public class MessageDigestCalculatingInputStreamTest {
    public static byte[] generateRandomByteStream(int pSize) {
        final byte[] buffer = new byte[pSize];
        final Random rnd = new Random();
        rnd.nextBytes(buffer);
        return buffer;
    }

    @Test
    public void test() throws Exception {
        for (int i = 256;  i < 8192;  i = i*2) {
            final byte[] buffer = generateRandomByteStream(i);
            final MessageDigest md5Sum = MessageDigest.getInstance("MD5");
            final byte[] expect = md5Sum.digest(buffer);
            try (final MessageDigestCalculatingInputStream md5InputStream =
                    new MessageDigestCalculatingInputStream(new ByteArrayInputStream(buffer))) {
                md5InputStream.consume();
                final byte[] got = md5InputStream.getMessageDigest().digest();
                assertArrayEquals(expect, got);
            }
        }
    }

}
