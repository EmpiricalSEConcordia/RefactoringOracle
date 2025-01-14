/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage;

import static org.mockito.Mockito.*;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.TestBase;
import org.mockito.exceptions.verification.ArgumentsAreDifferent;
import org.mockito.exceptions.verification.NoInteractionsWanted;

public class UsingVarargsTest extends TestBase {

    private interface IVarArgs {
        void withStringVarargs(int value, String... s);
        String withStringVarargsReturningString(int value, String... s);
        void withObjectVarargs(int value, Object... o);
        boolean withBooleanVarargs(int value, boolean... b);
    }
    
    IVarArgs mock;

    @Before
    public void setup() {
        mock = Mockito.mock(IVarArgs.class);
    }
    
    @Test
    public void shouldStubStringVarargs() {
        stub(mock.withStringVarargsReturningString(1)).toReturn("1");
        stub(mock.withStringVarargsReturningString(2, "1", "2", "3")).toReturn("2");
        
        RuntimeException expected = new RuntimeException();
        stubVoid(mock).toThrow(expected).on().withStringVarargs(3, "1", "2", "3", "4");

        assertEquals("1", mock.withStringVarargsReturningString(1));
        assertEquals(null, mock.withStringVarargsReturningString(2));
        
        assertEquals("2", mock.withStringVarargsReturningString(2, "1", "2", "3"));
        assertEquals(null, mock.withStringVarargsReturningString(2, "1", "2"));
        assertEquals(null, mock.withStringVarargsReturningString(2, "1", "2", "3", "4"));
        assertEquals(null, mock.withStringVarargsReturningString(2, "1", "2", "9999"));
        
        mock.withStringVarargs(3, "1", "2", "3", "9999");
        mock.withStringVarargs(9999, "1", "2", "3", "4");
        
        try {
            mock.withStringVarargs(3, "1", "2", "3", "4");
            fail();
        } catch (Exception e) {
            assertEquals(expected, e);
        }
    }
    
    @Test
    public void shouldStubBooleanVarargs() {
        stub(mock.withBooleanVarargs(1)).toReturn(true);
        stub(mock.withBooleanVarargs(1, true, false)).toReturn(true);
        
        assertEquals(true, mock.withBooleanVarargs(1));
        assertEquals(false, mock.withBooleanVarargs(9999));
        
        assertEquals(true, mock.withBooleanVarargs(1, true, false));
        assertEquals(false, mock.withBooleanVarargs(1, true, false, true));
        assertEquals(false, mock.withBooleanVarargs(2, true, false));
        assertEquals(false, mock.withBooleanVarargs(1, true));
        assertEquals(false, mock.withBooleanVarargs(1, false, false));
    }
    
    @Test
    public void shouldVerifyStringVarargs() {
        mock.withStringVarargs(1);
        mock.withStringVarargs(2, "1", "2", "3");
        mock.withStringVarargs(3, "1", "2", "3", "4");

        verify(mock).withStringVarargs(1);
        verify(mock).withStringVarargs(2, "1", "2", "3");
        try {
            verify(mock).withStringVarargs(2, "1", "2", "79", "4");
            fail();
        } catch (ArgumentsAreDifferent e) {}
    }

    @Test
    public void shouldVerifyObjectVarargs() {
        mock.withObjectVarargs(1);
        mock.withObjectVarargs(2, "1", new ArrayList<Object>(), new Integer(1));
        mock.withObjectVarargs(3, new Integer(1));

        verify(mock).withObjectVarargs(1);
        verify(mock).withObjectVarargs(2, "1", new ArrayList<Object>(), new Integer(1));
        try {
            verifyNoMoreInteractions(mock);
            fail();
        } catch (NoInteractionsWanted e) {}
    }

    @Test
    public void shouldVerifyBooleanVarargs() {
        mock.withBooleanVarargs(1);
        mock.withBooleanVarargs(2, true, false, true);
        mock.withBooleanVarargs(3, true, true, true);

        verify(mock).withBooleanVarargs(1);
        verify(mock).withBooleanVarargs(2, true, false, true);
        try {
            verify(mock).withBooleanVarargs(3, true, true, true, true);
            fail();
        } catch (ArgumentsAreDifferent e) {}
    }
    
    @Test
    public void shouldVerifyWithAnyObject() {
        Foo myClass = Mockito.mock(Foo.class);
        myClass.varArgs("");        
        Mockito.verify(myClass).varArgs((String[]) Mockito.anyObject());
        Mockito.verify(myClass).varArgs((String) Mockito.anyObject());
    }   
    
    @Test
    public void shouldVerifyWithNullVarArgArray() {
        Foo myClass = Mockito.mock(Foo.class);
        myClass.varArgs((String[]) null);    
        Mockito.verify(myClass).varArgs((String[]) Mockito.anyObject());
        Mockito.verify(myClass).varArgs((String[]) null);
    }  
    
    public class Foo {      
        public void varArgs(String... args) {}       
    }
}