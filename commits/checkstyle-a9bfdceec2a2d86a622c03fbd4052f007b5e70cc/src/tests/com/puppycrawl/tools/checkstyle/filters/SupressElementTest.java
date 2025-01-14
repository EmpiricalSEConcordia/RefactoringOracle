package com.puppycrawl.tools.checkstyle.filters;

import org.apache.regexp.RESyntaxException;

import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.Filter;
import com.puppycrawl.tools.checkstyle.api.LocalizedMessage;

import junit.framework.TestCase;

/** Tests IntMatchFilter */
public class SupressElementTest extends TestCase
{
    private SuppressElement filter;
    
    public void setUp()
        throws RESyntaxException
    {
        filter = new SuppressElement("Test", "Test");
    }
    
    public void testDecideDefault()
    {
        final AuditEvent ev = new AuditEvent(this, "Test.java");
        assertEquals(ev.getFileName(), Filter.NEUTRAL, filter.decide(ev));
    }
    
    public void testDecideLocalizedMessage()
    {
        LocalizedMessage message =
            new LocalizedMessage(0, 0, "", "", null, this.getClass());
        final AuditEvent ev = new AuditEvent(this, "ATest.java", message);
        //deny because there are matches on file and check names
        assertEquals("Names match", Filter.DENY, filter.decide(ev));
    }
    
    public void testDecideByLine()
    {
        LocalizedMessage message =
            new LocalizedMessage(10, 10, "", "", null, this.getClass());
        final AuditEvent ev = new AuditEvent(this, "ATest.java", message);
        //deny because there are matches on file name, check name, and line
        filter.setLines("1-10");
        assertEquals("In range 1-10)", Filter.DENY, filter.decide(ev));
        filter.setLines("1-9, 11");
        assertEquals("Not in 1-9, 11)", Filter.NEUTRAL, filter.decide(ev));
    }
    
    public void testDecideByColumn()
    {
        LocalizedMessage message =
            new LocalizedMessage(10, 10, "", "", null, this.getClass());
        final AuditEvent ev = new AuditEvent(this, "ATest.java", message);
        //deny because there are matches on file name, check name, and column
        filter.setColumns("1-10");
        assertEquals("In range 1-10)", Filter.DENY, filter.decide(ev));
        filter.setColumns("1-9, 11");
        assertEquals("Not in 1-9, 11)", Filter.NEUTRAL, filter.decide(ev));
    }

    public void testEquals() throws RESyntaxException
    {
        final SuppressElement filter2 = new SuppressElement("Test", "Test");
        assertEquals("filter, filter2", filter, filter2);
        final SuppressElement filter3 = new SuppressElement("Test", "Test3");
        assertFalse("filter, filter3", filter.equals(filter3));
        filter.setColumns("1-10");
        assertFalse("filter, filter2", filter.equals(filter2));
        filter2.setColumns("1-10");
        assertEquals("filter, filter2", filter, filter2);
        filter.setColumns(null);
        assertFalse("filter, filter2", filter.equals(filter2));
        filter2.setColumns(null);
        filter.setLines("3,4");
        assertFalse("filter, filter2", filter.equals(filter2));
        filter2.setLines("3,4");
        assertEquals("filter, filter2", filter, filter2);
        filter.setColumns("1-10");
        assertFalse("filter, filter2", filter.equals(filter2));
        filter2.setColumns("1-10");
        assertEquals("filter, filter2", filter, filter2);       
    }
}
