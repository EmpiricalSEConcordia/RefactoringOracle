/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//io/src/java/org/apache/commons/io/HexDump.java,v 1.7 2004/02/23 04:27:06 bayard Exp $
 * $Revision: 1.7 $
 * $Date: 2004/02/23 04:27:06 $
 *
 * ====================================================================
 *
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2002 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowledgement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgement may appear in the software itself,
 *    if and wherever such third-party acknowledgements normally appear.
 *
 * 4. The names "The Jakarta Project", "Commons", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package org.apache.commons.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Dumps data in hexadecimal format.
 *
 * Derived from a HexDump utility I wrote in June 2001.
 *
 * Taken from the POI project.
 *
 * @author Scott Sanders (sanders at apache dot org)
 * @author Marc Johnson
 * @version $Revision: 1.7 $ $Date: 2004/02/23 04:27:06 $
 */
public class HexDump {

    /**
     * Instances should NOT be constructed in standard programming.
     */
    public HexDump() { }

    /**
     * dump an array of bytes to an OutputStream
     *
     * @param data the byte array to be dumped
     * @param offset its offset, whatever that might mean
     * @param stream the OutputStream to which the data is to be
     *               written
     * @param index initial index into the byte array
     *
     * @exception IOException is thrown if anything goes wrong writing
     *            the data to stream
     * @exception ArrayIndexOutOfBoundsException if the index is
     *            outside the data array's bounds
     * @exception IllegalArgumentException if the output stream is
     *            null
     */

    public static void dump(byte[] data, long offset,
                            OutputStream stream, int index)
            throws IOException, ArrayIndexOutOfBoundsException,
            IllegalArgumentException {
        if ((index < 0) || (index >= data.length)) {
            throw new ArrayIndexOutOfBoundsException(
                    "illegal index: " + index + " into array of length "
                    + data.length);
        }
        if (stream == null) {
            throw new IllegalArgumentException("cannot write to nullstream");
        }
        long display_offset = offset + index;
        StringBuffer buffer = new StringBuffer(74);

        for (int j = index; j < data.length; j += 16) {
            int chars_read = data.length - j;

            if (chars_read > 16) {
                chars_read = 16;
            }
            buffer.append(dump(display_offset)).append(' ');
            for (int k = 0; k < 16; k++) {
                if (k < chars_read) {
                    buffer.append(dump(data[k + j]));
                } else {
                    buffer.append("  ");
                }
                buffer.append(' ');
            }
            for (int k = 0; k < chars_read; k++) {
                if ((data[k + j] >= ' ') && (data[k + j] < 127)) {
                    buffer.append((char) data[k + j]);
                } else {
                    buffer.append('.');
                }
            }
            buffer.append(EOL);
            stream.write(buffer.toString().getBytes());
            stream.flush();
            buffer.setLength(0);
            display_offset += chars_read;
        }
    }

    /** line-separator (initializes to "line.separator" system property. */
    public static final String EOL =
            System.getProperty("line.separator");
    private static final StringBuffer _lbuffer = new StringBuffer(8);
    private static final StringBuffer _cbuffer = new StringBuffer(2);
    private static final char _hexcodes[] =
            {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D',
                'E', 'F'
            };
    private static final int _shifts[] =
            {
                28, 24, 20, 16, 12, 8, 4, 0
            };

    private static StringBuffer dump(long value) {
        _lbuffer.setLength(0);
        for (int j = 0; j < 8; j++) {
            _lbuffer
                    .append(_hexcodes[((int) (value >> _shifts[j])) & 15]);
        }
        return _lbuffer;
    }

    private static StringBuffer dump(byte value) {
        _cbuffer.setLength(0);
        for (int j = 0; j < 2; j++) {
            _cbuffer.append(_hexcodes[(value >> _shifts[j + 6]) & 15]);
        }
        return _cbuffer;
    }
}
