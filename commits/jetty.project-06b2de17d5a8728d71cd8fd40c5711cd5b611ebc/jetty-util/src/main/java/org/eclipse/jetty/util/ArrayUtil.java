//
//  ========================================================================
//  Copyright (c) 1995-2012 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.util;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* ------------------------------------------------------------ */
/**
 */
public class ArrayUtil
    implements Cloneable, Serializable
{

    /* ------------------------------------------------------------ */
    public static<T> T[] removeFromArray(T[] array, Object item)
    {
        if (item==null || array==null)
            return array;
        for (int i=array.length;i-->0;)
        {
            if (item.equals(array[i]))
            {
                Class<?> c = array==null?item.getClass():array.getClass().getComponentType();
                @SuppressWarnings("unchecked")
                T[] na = (T[])Array.newInstance(c, Array.getLength(array)-1);
                if (i>0)
                    System.arraycopy(array, 0, na, 0, i);
                if (i+1<array.length)
                    System.arraycopy(array, i+1, na, i, array.length-(i+1));
                return na;
            }
        }
        return array;
    }

    /* ------------------------------------------------------------ */
    /** Add element to an array
     * @param array The array to add to (or null)
     * @param item The item to add
     * @param type The type of the array (in case of null array)
     * @return new array with contents of array plus item
     */
    public static<T> T[] addToArray(T[] array, T item, Class<?> type)
    {
        if (array==null)
        {
            if (type==null && item!=null)
                type= item.getClass();
            @SuppressWarnings("unchecked")
            T[] na = (T[])Array.newInstance(type, 1);
            na[0]=item;
            return na;
        }
        else
        {
            // TODO: Replace with Arrays.copyOf(T[] original, int newLength) from Java 1.6+
            Class<?> c = array.getClass().getComponentType();
            @SuppressWarnings("unchecked")
            T[] na = (T[])Array.newInstance(c, Array.getLength(array)+1);
            System.arraycopy(array, 0, na, 0, array.length);
            na[array.length]=item;
            return na;
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * @param array Any array of object
     * @return A new <i>modifiable</i> list initialised with the elements from <code>array</code>.
     */
    public static<E> List<E> asMutableList(E[] array)
    {	
        if (array==null || array.length==0)
            return new ArrayList<E>();
        return new ArrayList<E>(Arrays.asList(array));
    }
    
}

