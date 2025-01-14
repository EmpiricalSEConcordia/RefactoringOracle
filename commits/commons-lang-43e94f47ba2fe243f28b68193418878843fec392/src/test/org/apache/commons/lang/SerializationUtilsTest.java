/*
 * Copyright 2002-2004 The Apache Software Foundation.
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
package org.apache.commons.lang;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.HashMap;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Unit tests {@link org.apache.commons.lang.SerializationUtils}.
 *
 * @author <a href="mailto:scolebourne@joda.org">Stephen Colebourne</a>
 * @author <a href="mailto:ridesmet@users.sourceforge.net">Ringo De Smet</a>
 * @version $Id: SerializationUtilsTest.java,v 1.6 2004/02/18 23:06:19 ggregory Exp $
 */
public class SerializationUtilsTest extends TestCase {
    private String iString;
    private Integer iInteger;
    private HashMap iMap;

    public SerializationUtilsTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }

    public static Test suite() {
    	TestSuite suite = new TestSuite(SerializationUtilsTest.class);
    	suite.setName("SerializationUtils Tests");
        return suite;
    }

    protected void setUp() throws Exception {
        super.setUp();

        iString = "foo";
        iInteger = new Integer(7);
        iMap = new HashMap();
        iMap.put("FOO", iString);
        iMap.put("BAR", iInteger);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    //-----------------------------------------------------------------------
    public void testConstructor() {
        assertNotNull(new SerializationUtils());
        Constructor[] cons = SerializationUtils.class.getDeclaredConstructors();
        assertEquals(1, cons.length);
        assertEquals(true, Modifier.isPublic(cons[0].getModifiers()));
        assertEquals(true, Modifier.isPublic(SerializationUtils.class.getModifiers()));
        assertEquals(false, Modifier.isFinal(SerializationUtils.class.getModifiers()));
    }
    
    public void testException() {
        SerializationException serEx;
        Exception ex = new Exception();
        
        serEx = new SerializationException();
        assertSame(null, serEx.getMessage());
        assertSame(null, serEx.getCause());
        
        serEx = new SerializationException("Message");
        assertSame("Message", serEx.getMessage());
        assertSame(null, serEx.getCause());
        
        serEx = new SerializationException(ex);
        assertEquals("java.lang.Exception", serEx.getMessage());
        assertSame(ex, serEx.getCause());
        
        serEx = new SerializationException("Message", ex);
        assertSame("Message", serEx.getMessage());
        assertSame(ex, serEx.getCause());
    }
    
    //-----------------------------------------------------------------------
    public void testSerializeStream() throws Exception {
        ByteArrayOutputStream streamTest = new ByteArrayOutputStream();
        SerializationUtils.serialize(iMap, streamTest);

        ByteArrayOutputStream streamReal = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(streamReal);
        oos.writeObject(iMap);
        oos.flush();
        oos.close();

        byte[] testBytes = streamTest.toByteArray();
        byte[] realBytes = streamReal.toByteArray();
        assertEquals(testBytes.length, realBytes.length);
        for (int i = 0; i < realBytes.length; i++) {
            assertEquals(realBytes[i], testBytes[i]);
        }
    }

    public void testSerializeStreamUnserializable() throws Exception {
        ByteArrayOutputStream streamTest = new ByteArrayOutputStream();
        try {
            iMap.put(new Object(), new Object());
            SerializationUtils.serialize(iMap, streamTest);
        } catch (SerializationException ex) {
            return;
        }
        fail();
    }

    public void testSerializeStreamNullObj() throws Exception {
        ByteArrayOutputStream streamTest = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, streamTest);

        ByteArrayOutputStream streamReal = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(streamReal);
        oos.writeObject(null);
        oos.flush();
        oos.close();

        byte[] testBytes = streamTest.toByteArray();
        byte[] realBytes = streamReal.toByteArray();
        assertEquals(testBytes.length, realBytes.length);
        for (int i = 0; i < realBytes.length; i++) {
            assertEquals(realBytes[i], testBytes[i]);
        }
    }

    public void testSerializeStreamObjNull() throws Exception {
        ByteArrayOutputStream streamTest = new ByteArrayOutputStream();
        try {
            SerializationUtils.serialize(iMap, null);
        } catch (IllegalArgumentException ex) {
            return;
        }
        fail();
    }

    public void testSerializeStreamNullNull() throws Exception {
        ByteArrayOutputStream streamTest = new ByteArrayOutputStream();
        try {
            SerializationUtils.serialize(null, null);
        } catch (IllegalArgumentException ex) {
            return;
        }
        fail();
    }

    //-----------------------------------------------------------------------

    public void testDeserializeStream() throws Exception {
        ByteArrayOutputStream streamReal = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(streamReal);
        oos.writeObject(iMap);
        oos.flush();
        oos.close();

        ByteArrayInputStream inTest = new ByteArrayInputStream(streamReal.toByteArray());
        Object test = SerializationUtils.deserialize(inTest);
        assertNotNull(test);
        assertTrue(test instanceof HashMap);
        assertTrue(test != iMap);
        HashMap testMap = (HashMap) test;
        assertEquals(iString, testMap.get("FOO"));
        assertTrue(iString != testMap.get("FOO"));
        assertEquals(iInteger, testMap.get("BAR"));
        assertTrue(iInteger != testMap.get("BAR"));
        assertEquals(iMap, testMap);
    }

    public void testDeserializeStreamOfNull() throws Exception {
        ByteArrayOutputStream streamReal = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(streamReal);
        oos.writeObject(null);
        oos.flush();
        oos.close();

        ByteArrayInputStream inTest = new ByteArrayInputStream(streamReal.toByteArray());
        Object test = SerializationUtils.deserialize(inTest);
        assertNull(test);
    }

    public void testDeserializeStreamNull() throws Exception {
        try {
            SerializationUtils.deserialize((InputStream) null);
        } catch (IllegalArgumentException ex) {
            return;
        }
        fail();
    }

    public void testDeserializeStreamBadStream() throws Exception {
        try {
            SerializationUtils.deserialize(new ByteArrayInputStream(new byte[0]));
        } catch (SerializationException ex) {
            return;
        }
        fail();
    }

    //-----------------------------------------------------------------------

    public void testSerializeBytes() throws Exception {
        byte[] testBytes = SerializationUtils.serialize(iMap);

        ByteArrayOutputStream streamReal = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(streamReal);
        oos.writeObject(iMap);
        oos.flush();
        oos.close();

        byte[] realBytes = streamReal.toByteArray();
        assertEquals(testBytes.length, realBytes.length);
        for (int i = 0; i < realBytes.length; i++) {
            assertEquals(realBytes[i], testBytes[i]);
        }
    }

    public void testSerializeBytesUnserializable() throws Exception {
        try {
            iMap.put(new Object(), new Object());
            SerializationUtils.serialize(iMap);
        } catch (SerializationException ex) {
            return;
        }
        fail();
    }

    public void testSerializeBytesNull() throws Exception {
        byte[] testBytes = SerializationUtils.serialize(null);

        ByteArrayOutputStream streamReal = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(streamReal);
        oos.writeObject(null);
        oos.flush();
        oos.close();

        byte[] realBytes = streamReal.toByteArray();
        assertEquals(testBytes.length, realBytes.length);
        for (int i = 0; i < realBytes.length; i++) {
            assertEquals(realBytes[i], testBytes[i]);
        }
    }

    //-----------------------------------------------------------------------

    public void testDeserializeBytes() throws Exception {
        ByteArrayOutputStream streamReal = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(streamReal);
        oos.writeObject(iMap);
        oos.flush();
        oos.close();

        Object test = SerializationUtils.deserialize(streamReal.toByteArray());
        assertNotNull(test);
        assertTrue(test instanceof HashMap);
        assertTrue(test != iMap);
        HashMap testMap = (HashMap) test;
        assertEquals(iString, testMap.get("FOO"));
        assertTrue(iString != testMap.get("FOO"));
        assertEquals(iInteger, testMap.get("BAR"));
        assertTrue(iInteger != testMap.get("BAR"));
        assertEquals(iMap, testMap);
    }

    public void testDeserializeBytesOfNull() throws Exception {
        ByteArrayOutputStream streamReal = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(streamReal);
        oos.writeObject(null);
        oos.flush();
        oos.close();

        Object test = SerializationUtils.deserialize(streamReal.toByteArray());
        assertNull(test);
    }

    public void testDeserializeBytesNull() throws Exception {
        try {
            SerializationUtils.deserialize((byte[]) null);
        } catch (IllegalArgumentException ex) {
            return;
        }
        fail();
    }

    public void testDeserializeBytesBadStream() throws Exception {
        try {
            SerializationUtils.deserialize(new byte[0]);
        } catch (SerializationException ex) {
            return;
        }
        fail();
    }

    //-----------------------------------------------------------------------

    public void testClone() throws Exception {
        Object test = SerializationUtils.clone(iMap);
        assertNotNull(test);
        assertTrue(test instanceof HashMap);
        assertTrue(test != iMap);
        HashMap testMap = (HashMap) test;
        assertEquals(iString, testMap.get("FOO"));
        assertTrue(iString != testMap.get("FOO"));
        assertEquals(iInteger, testMap.get("BAR"));
        assertTrue(iInteger != testMap.get("BAR"));
        assertEquals(iMap, testMap);
    }

    public void testCloneNull() throws Exception {
        Object test = SerializationUtils.clone(null);
        assertNull(test);
    }

    public void testCloneUnserializable() throws Exception {
        try {
            iMap.put(new Object(), new Object());
            SerializationUtils.clone(iMap);
        } catch (SerializationException ex) {
            return;
        }
        fail();
    }

}
