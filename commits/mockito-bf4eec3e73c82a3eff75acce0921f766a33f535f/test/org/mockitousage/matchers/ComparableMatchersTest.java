/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.matchers;

import static org.junit.Assert.*;

import java.math.BigDecimal;

import org.hamcrest.Description;
import org.hamcrest.StringDescription;
import org.junit.Test;
import org.mockito.TestBase;
import org.mockito.internal.matchers.CompareEqual;
import org.mockito.internal.matchers.CompareTo;
import org.mockito.internal.matchers.GreaterOrEqual;
import org.mockito.internal.matchers.GreaterThan;
import org.mockito.internal.matchers.LessOrEqual;
import org.mockito.internal.matchers.LessThan;

public class ComparableMatchersTest extends TestBase {

    @Test
    public void testLessThan() {
        test(new LessThan<String>("b"), true, false, false, "lt");
    }

    @Test
    public void testGreateThan() {
        test(new GreaterThan<String>("b"), false, true, false, "gt");
    }

    @Test
    public void testLessOrEqual() {
        test(new LessOrEqual<String>("b"), true, false, true, "leq");
    }

    @Test
    public void testGreateOrEqual() {
        test(new GreaterOrEqual<String>("b"), false, true, true, "geq");
    }

    @Test
    public void testCompareEqual() {
        test(new CompareEqual<String>("b"), false, false, true, "cmpEq");

        // Make sure it works when equals provide a different result than
        // compare
        CompareEqual<BigDecimal> cmpEq = new CompareEqual<BigDecimal>(
                new BigDecimal("5.00"));
        assertTrue(cmpEq.matches(new BigDecimal("5")));
    }

    private void test(CompareTo<String> compareTo, boolean lower, boolean higher,
            boolean equals, String name) {

        assertEquals(lower, compareTo.matches("a"));
        assertEquals(equals, compareTo.matches("b"));
        assertEquals(higher, compareTo.matches("c"));

        Description d = new StringDescription();
        compareTo.describeTo(d);
        assertEquals(name + "(b)", d.toString());
    }
}
