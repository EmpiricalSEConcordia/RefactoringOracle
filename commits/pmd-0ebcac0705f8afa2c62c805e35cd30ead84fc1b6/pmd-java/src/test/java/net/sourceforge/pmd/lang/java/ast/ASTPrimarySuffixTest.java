package net.sourceforge.pmd.lang.java.ast;

import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.lang.java.ParserTst;

public class ASTPrimarySuffixTest extends ParserTst {

    @Test
    public void testArrayDereference() throws Throwable {
        Set<ASTPrimarySuffix> ops = getNodes(ASTPrimarySuffix.class, TEST1);
        assertTrue(ops.iterator().next().isArrayDereference());
    }

    @Test
    public void testArguments() throws Throwable {
        Set<ASTPrimarySuffix> ops = getNodes(ASTPrimarySuffix.class, TEST2);
        assertTrue(ops.iterator().next().isArguments());
    }

    private static final String TEST1 = "public class Foo {" + PMD.EOL + "  {x[0] = 2;}" + PMD.EOL + "}";

    private static final String TEST2 = "public class Foo {" + PMD.EOL + "  {foo(a);}" + PMD.EOL + "}";

    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(ASTPrimarySuffixTest.class);
    }
}
