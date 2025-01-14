package org.mockito.internal.matchers;

import org.fest.assertions.Assertions;
import org.junit.Test;
import org.mockito.exceptions.base.MockitoException;
import org.mockitoutil.TestBase;

@SuppressWarnings("unchecked")
public class CapturingMatcherTest extends TestBase {

    @Test
    public void shouldCaptureArguments() throws Exception {
        //given
        CapturingMatcher m = new CapturingMatcher();
        
        //when
        m.captureFrom("foo");
        m.captureFrom("bar");
        
        //then
        Assertions.assertThat(m.getAllValues()).containsSequence("foo", "bar");
    }
    
    @Test
    public void shouldKnowLastCapturedValue() throws Exception {
        //given
        CapturingMatcher m = new CapturingMatcher();
        
        //when
        m.captureFrom("foo");
        m.captureFrom("bar");
        
        //then
        assertEquals("bar", m.getLastValue());
    }
    
    @Test
    public void shouldScreamWhenNothingYetCaptured() throws Exception {
        //given
        CapturingMatcher m = new CapturingMatcher();

        try {
            //when
            m.getLastValue();
            //then
            fail();
        } catch (MockitoException e) {}
    }
}