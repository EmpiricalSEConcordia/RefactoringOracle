/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.matchers;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.exceptions.verification.WantedButNotInvoked;
import org.mockitousage.IMethods;
import org.mockitoutil.TestBase;

public class CapturingArgumentsTest extends TestBase {

    @Mock IMethods mock;
    
    class Person {

        private final Integer age;

        public Person(Integer age) {
            this.age = age;
        }

        public int getAge() {
            return age;
        }
    }
    
    class Emailer {
     
        private EmailService service;
        
        public Emailer(EmailService service) {
            this.service = service;
        }

        public void email(Integer ... personId) {
            for (Integer i : personId) {
                Person person = new Person(i);
                service.sendEmailTo(person);
            }
        }
    }
    
    interface EmailService {
        boolean sendEmailTo(Person person);
    }

    EmailService emailService = mock(EmailService.class);
    Emailer emailer = new Emailer(emailService);

    @Test
    public void shouldAllowAssertionsOnCapturedArgument() {
        //when
        emailer.email(12);
        
        //then
        ArgumentCaptor<Person> argument = new ArgumentCaptor<Person>();
        verify(emailService).sendEmailTo(argument.capture());
        
        assertEquals(12, argument.getValue().getAge());
    }
    
    @Test
    public void shouldAllowAssertionsOnAllCapturedArguments() {
        //when
        emailer.email(11, 12);
        
        //then
        ArgumentCaptor<Person> argument = new ArgumentCaptor<Person>();
        verify(emailService, atLeastOnce()).sendEmailTo(argument.capture());
        List<Person> allValues = argument.getAllValues();
        
        assertEquals(11, allValues.get(0).getAge());
        assertEquals(12, allValues.get(1).getAge());
    }
    
    @Test
    public void shouldAllowAssertionsOnLastArgument() {
        //when
        emailer.email(11, 12, 13);
        
        //then
        ArgumentCaptor<Person> argument = new ArgumentCaptor<Person>();
        verify(emailService, atLeastOnce()).sendEmailTo(argument.capture());
        
        assertEquals(13, argument.getValue().getAge());
    }
    
    @Test
    public void shouldPrintCaptorMatcher() {
        //given
        ArgumentCaptor<Person> person = new ArgumentCaptor<Person>();
        
        try {
            //when
            verify(emailService).sendEmailTo(person.capture());
            fail();
        } catch(WantedButNotInvoked e) {
            //then
            assertContains("<Capturing argument>", e.getMessage());
        }
    }
    
    @Test
    public void shouldAllowAssertionsOnCapturedNull() {
        //when
        emailService.sendEmailTo(null);
        
        //then
        ArgumentCaptor<Person> argument = new ArgumentCaptor<Person>();
        verify(emailService).sendEmailTo(argument.capture());
        assertEquals(null, argument.getValue());
    }
    
    @Test
    public void shouldAllowCapturingForStubbing() {
        //given
        ArgumentCaptor<Person> argument = new ArgumentCaptor<Person>();
        when(emailService.sendEmailTo(argument.capture())).thenReturn(false);
        
        //when
        emailService.sendEmailTo(new Person(10));
        
        //then
        assertEquals(10, argument.getValue().getAge());
    }
    
    @Test
    public void shouldSaySomethingSmartWhenMisused() {
        ArgumentCaptor<Person> argument = new ArgumentCaptor<Person>();
        try {
            argument.getValue();
            fail();
        } catch (MockitoException e) {}
    }
    
    @Test
    public void shouldCaptureWhenFullArgListMatches() throws Exception {
        //given
        mock.simpleMethod("foo", 1);
        mock.simpleMethod("bar", 2);
        
        //when
        ArgumentCaptor<String> captor = new ArgumentCaptor<String>();
        verify(mock).simpleMethod(captor.capture(), eq(1));
        
        //then
        assertEquals(1, captor.getAllValues().size());
        assertEquals("foo", captor.getValue());
    }
    
    //TODO: not yet implemented
    @Ignore
    @Test
    public void shouldCaptureInt() {
        //given
        IMethods mock = mock(IMethods.class);
        ArgumentCaptor<Integer> argument = new ArgumentCaptor<Integer>();

        //when
        mock.intArgumentMethod(10);
        
        //then
        verify(mock).intArgumentMethod(argument.capture());
        assertEquals(10, (int) argument.getValue());
    }
}