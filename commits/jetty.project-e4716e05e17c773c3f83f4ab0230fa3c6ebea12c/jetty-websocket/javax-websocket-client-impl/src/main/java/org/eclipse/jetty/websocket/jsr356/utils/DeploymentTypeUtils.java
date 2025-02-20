//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.jsr356.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jetty.websocket.common.events.annotated.InvalidSignatureException;

/**
 * Collection of common {@link Type} utility methods used during Deployment of the Endpoint
 */
public class DeploymentTypeUtils
{
    private static final Map<Class<?>, Class<?>> PRIMITIVE_CLASS_MAP;
    private static final Map<Class<?>, Class<?>> CLASS_PRIMITIVE_MAP;

    static
    {
        Map<Class<?>, Class<?>> primitives = new HashMap<>();

        // Map of classes to primitive types
        primitives.put(Boolean.class,Boolean.TYPE);
        primitives.put(Byte.class,Byte.TYPE);
        primitives.put(Character.class,Character.TYPE);
        primitives.put(Double.class,Double.TYPE);
        primitives.put(Float.class,Float.TYPE);
        primitives.put(Integer.class,Integer.TYPE);
        primitives.put(Long.class,Long.TYPE);
        primitives.put(Short.class,Short.TYPE);
        primitives.put(Void.class,Void.TYPE);

        CLASS_PRIMITIVE_MAP = Collections.unmodifiableMap(primitives);

        // Map of primitive types to classes
        Map<Class<?>, Class<?>> types = new HashMap<>();
        for (Map.Entry<Class<?>, Class<?>> classEntry : primitives.entrySet())
        {
            types.put(classEntry.getValue(),classEntry.getKey());
        }

        PRIMITIVE_CLASS_MAP = Collections.unmodifiableMap(types);
    }

    public static Type getGenericType(Class<?> clazz, Class<?> interfaceClass)
    {
        for (Type type : clazz.getGenericInterfaces())
        {
            if (type instanceof ParameterizedType)
            {
                ParameterizedType ptype = (ParameterizedType)type;
                if (isAssignable(type,interfaceClass))
                {
                    Type typeArgs[] = ptype.getActualTypeArguments();
                    return typeArgs[0];
                }
            }
        }
        return null;
    }

    public static Class<?> getPrimitiveClass(Class<?> primitiveType)
    {
        return PRIMITIVE_CLASS_MAP.get(primitiveType);
    }

    public static Set<Class<?>> getPrimitiveClasses()
    {
        return CLASS_PRIMITIVE_MAP.keySet();
    }

    public static Set<Class<?>> getPrimitives()
    {
        return PRIMITIVE_CLASS_MAP.keySet();
    }

    public static Class<?> getRawTypeClass(ParameterizedType type)
    {
        Type raw = type.getRawType();
        if (raw instanceof Class<?>)
        {
            return (Class<?>)raw;
        }
        // bail on non-class raw types
        throw new InvalidSignatureException("Unexpected, Non Class Raw Type: " + raw);
    }

    /**
     * Checks if the provided {@link Type} can be cast to the target {@link Type}, taking in account Generics.
     * 
     * @param type
     *            the type to be assigned to the target type
     * @param targetType
     *            the target type
     * @return
     */
    public static boolean isAssignable(Type type, Type targetType)
    {
        return isAssignable(type,targetType,null);
    }

    private static boolean isAssignable(Type type, Type targetType, Map<TypeVariable<?>, Type> typeVars)
    {
        if ((type == null) || (targetType == null))
        {
            return false;
        }

        if (targetType instanceof Class<?>)
        {
            return isAssignableToClass(type,(Class<?>)targetType);
        }

        return false;
    }

    public static boolean isAssignableClass(Class<?> type, Class<?> targetClass)
    {
        if (type == null)
        {
            return false;
        }

        // Casting from primitive
        if (type.isPrimitive())
        {
            // Primitive to Class (autoboxing)
            if (!targetClass.isPrimitive())
            {
                // going from primitive to class, make sure it matches
                Class<?> primitive = CLASS_PRIMITIVE_MAP.get(targetClass);
                return primitive.equals(type);
            }

            // Primitive to Primitive

            /**
             * After reading the javadoc for the various primitive class implementations. It is determined that the appropriate allowable casting from one
             * primitive to another can be discovered via the existence (or not) of various .*Value() methods.
             * 
             * The table:
             * 
             * <pre>
             *        | :From:
             * :To:   | Bool | Byte | Char | Double | Float | Int | Long | Short
             * ------------------------------------------------------------------
             * Bool   |  Y   |      |      |        |       |     |      |     
             * Byte   |      |  Y   |      |  Y     |  Y    |  Y  |  Y   | Y
             * Char   |      |      |  Y   |        |       |     |      |
             * Double |      |  Y   |      |  Y     |  Y    |  Y  |  Y   | Y
             * Float  |      |  Y   |      |  Y     |  Y    |  Y  |  Y   | Y
             * Int    |      |  Y   |      |  Y     |  Y    |  Y  |  Y   | Y
             * Long   |      |  Y   |      |  Y     |  Y    |  Y  |  Y   | Y
             * Short  |      |  Y   |      |  Y     |  Y    |  Y  |  Y   | Y
             * </pre>
             */

            if (Byte.TYPE.equals(type) || Double.TYPE.equals(type) || Float.TYPE.equals(type) || Integer.TYPE.equals(type) || Long.TYPE.equals(type)
                    || Short.TYPE.equals(type))
            {
                return Byte.TYPE.equals(targetClass) || Double.TYPE.equals(targetClass) || Float.TYPE.equals(targetClass) || Integer.TYPE.equals(targetClass)
                        || Long.TYPE.equals(targetClass) || Short.TYPE.equals(targetClass);
            }

            // All others are not valid
            return false;
        }
        else
        {
            if (targetClass.isPrimitive())
            {
                // Class to Primitive (autoboxing)
                Class<?> targetPrimitive = CLASS_PRIMITIVE_MAP.get(type);
                return targetClass.equals(targetPrimitive);
            }
        }

        // Basic class check
        if (type.equals(targetClass))
        {
            return true;
        }

        // Basic form
        return targetClass.isAssignableFrom(type);
    }

    private static boolean isAssignableToClass(Type type, Class<?> targetClass)
    {
        if (targetClass.equals(type))
        {
            // most basic check
            return true;
        }

        if (type instanceof Class<?>)
        {
            return isAssignableClass((Class<?>)type,targetClass);
        }

        if (type instanceof ParameterizedType)
        {
            // Try raw class
            Class<?> rawClass = getRawTypeClass((ParameterizedType)type);
            return isAssignableClass(rawClass,targetClass);
        }

        // TODO: Check TypeVariable
        // TODO: Check GenericArrayType

        if (type instanceof WildcardType)
        {
            // Assigning a type to a wildcard doesn't make sense
            return false;
        }

        throw new InvalidSignatureException("Unhandled Type: " + type);
    }
}
