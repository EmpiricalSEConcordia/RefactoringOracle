package org.apache.commons.lang;

/* ====================================================================
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
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Methods that assist with the serialization process, or perform
 * additional functionality based on serialization.
 * <ul>
 * <li>Deep clone using serialization
 * <li>Serialize managing finally and IOException
 * <li>Deserialize managing finally and IOException
 * </ul>
 *
 * @author <a href="mailto:nissim@nksystems.com">Nissim Karpenstein</a>
 * @author <a href="mailto:janekdb@yahoo.co.uk">Janek Bogucki</a>
 * @author <a href="mailto:dlr@finemaltcoding.com">Daniel Rall</a>
 * @author <a href="mailto:scolebourne@joda.org">Stephen Colebourne</a>
 * @version $Id: SerializationUtils.java,v 1.1 2002/07/19 03:35:54 bayard Exp $
 */
public class SerializationUtils {
    
    /**
     * Constructor for SerializationUtils is private
     */
    private SerializationUtils() {
        super();
    }

    /**
     * Deep clone an object using serialization.
     * <p>
     * This is many times slower than writing clone methods by hand
     * on all objects in your object graph. However, for complex object
     * graphs, or for those that don't support deep cloning this can
     * be a simple alternative implementation. Of course all the objects
     * must be <code>Serializable</code>.
     * 
     * @param object  the <code>Serializable</code> object to clone
     * @return the cloned object
     * @throws SerializationException (runtime) if the serialization fails
     */
    public static Object clone(Serializable object) {
        return deserialize( serialize(object) );
    }
    
    /**
     * Serializes an object to the specified stream. The stream will
     * be closed once the object is written. This avoids the need for
     * a finally clause, and maybe also exception handling, in the
     * application code.
     *
     * @param obj  the object to serialize to bytes
     * @param outputStream  the stream to write to
     * @throws SerializationException (runtime) if the serialization fails
     */
    public static void serialize(Serializable obj, OutputStream outputStream) {
        ObjectOutputStream out = null;
        try {
            // stream closed in the finally
            out = new ObjectOutputStream(outputStream);
            out.writeObject(obj);
            
        } catch (IOException ex) {
            throw new SerializationException(ex);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ex) {
                // ignore;
            }
        }
    }

    /**
     * Serializes an object to a byte array for storage/serialization.
     *
     * @param obj  the object to serialize to bytes
     * @return a byte[] with the converted Serializable.
     * @throws SerializationException (runtime) if the serialization fails
     */
    public static byte[] serialize(Serializable obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serialize(obj, baos);
        return baos.toByteArray();
    }

    /**
     * Deserializes an object from the specified stream. The stream will
     * be closed once the object is written. This avoids the need for
     * a finally clause, and maybe also exception handling, in the
     * application code.
     *
     * @param objectData  the serialized object.
     * @return the deserialized object
     * @throws SerializationException (runtime) if the serialization fails
     */
    public static Object deserialize(InputStream inputStream) {
        ObjectInputStream in = null;
        try {
            // stream closed in the finally
            in = new ObjectInputStream(inputStream);
            return in.readObject();
            
        } catch (ClassNotFoundException ex) {
            throw new SerializationException(ex);
        } catch (IOException ex) {
            throw new SerializationException(ex);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                // ignore
            }
        }
    }

    /**
     * Deserializes a single object from an array of bytes.
     *
     * @param objectData  the serialized object.
     * @return the deserialized object
     * @throws SerializationException (runtime) if the serialization fails
     */
    public static Object deserialize(byte[] objectData) {
        ByteArrayInputStream bais = new ByteArrayInputStream(objectData);
        return deserialize(bais);
    }
    
}
