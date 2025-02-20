package com.puppycrawl.tools.checkstyle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

import junit.framework.TestCase;
import org.apache.regexp.RE;

public class ConfigurationSerializationTest
    extends TestCase
{

    /**
     * Copy mConfig using in-memory serialization
     * @param aConfig the original
     * @return a copy of aConfig obtained by in-memory serialization
     */
    private Configuration copyBySerialization(Configuration aConfig)
        throws IOException, ClassNotFoundException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(aConfig);
        oos.flush();
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Configuration configCopy = (Configuration) ois.readObject();
        ois.close();
        return configCopy;
    }

    /**
     * Tests that all RE fields are restored during deserialization.
     * This test is designed to prevent addition of transient RE
     * fields to GlobalProperties without modification of
     * GlobalProperties.readObject().
     */
    public void testAllRegexpsNotNull()
        throws Exception
    {
        Configuration configOrig = new Configuration();
        Configuration configCopy = copyBySerialization(configOrig);
        assertNotNull(configCopy);

        // ensure that none of the getSomeRE() methods (even the ones
        // we don't know yet) of the configCopy returns null

        Method[] configMethods = Configuration.class.getMethods();
        for (int i = 0; i < configMethods.length; i++)
        {
            Method method = configMethods[i];
            String methodName = method.getName();
            if (methodName.startsWith("get") &&
                method.getReturnType().equals(RE.class) &&
                method.getParameterTypes().length == 0)
            {
                Object[] noArgs = {};
                Object obj = method.invoke(configCopy, noArgs);
                assertNotNull(methodName + "() returned null", obj);
            }
        }
    }
}
