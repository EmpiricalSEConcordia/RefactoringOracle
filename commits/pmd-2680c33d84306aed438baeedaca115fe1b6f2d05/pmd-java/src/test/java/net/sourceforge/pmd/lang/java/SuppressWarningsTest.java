/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import net.sourceforge.pmd.FooRule;
import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.testframework.RuleTst;

import junit.framework.JUnit4TestAdapter;

public class SuppressWarningsTest extends RuleTst {

    private static class BarRule extends AbstractJavaRule {
        @Override
        public Object visit(ASTCompilationUnit cu, Object ctx) {
            // Convoluted rule to make sure the violation is reported for the
            // ASTCompilationUnit node
            for (ASTClassOrInterfaceDeclaration c : cu.findDescendantsOfType(ASTClassOrInterfaceDeclaration.class)) {
                if (c.getImage().equalsIgnoreCase("bar")) {
                    addViolation(ctx, cu);
                }
            }
            return super.visit(cu, ctx);
        }

        @Override
        public String getName() {
            return "NoBar";
        }
    }

    @Test
    public void testClassLevelSuppression() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST1, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(0, rpt.size());
        runTestFromString(TEST2, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(0, rpt.size());
    }

    @Test
    public void testInheritedSuppression() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST3, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(0, rpt.size());
    }

    @Test
    public void testMethodLevelSuppression() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST4, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(1, rpt.size());
    }

    @Test
    public void testConstructorLevelSuppression() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST5, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(0, rpt.size());
    }

    @Test
    public void testFieldLevelSuppression() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST6, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(1, rpt.size());
    }

    @Test
    public void testParameterLevelSuppression() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST7, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(1, rpt.size());
    }

    @Test
    public void testLocalVariableLevelSuppression() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST8, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(1, rpt.size());
    }

    @Test
    public void testSpecificSuppression() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST9, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(1, rpt.size());
    }

    @Test
    public void testSpecificSuppressionValue1() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST9_VALUE1, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(1, rpt.size());
    }

    @Test
    public void testSpecificSuppressionValue2() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST9_VALUE2, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(1, rpt.size());
    }

    @Test
    public void testSpecificSuppressionValue3() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST9_VALUE3, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(1, rpt.size());
    }

    @Test
    public void testSpecificSuppressionMulitpleValues1() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST9_MULTIPLE_VALUES_1, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(0, rpt.size());
    }

    @Test
    public void testSpecificSuppressionMulitpleValues2() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST9_MULTIPLE_VALUES_2, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(0, rpt.size());
    }

    @Test
    public void testNoSuppressionBlank() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST10, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(2, rpt.size());
    }

    @Test
    public void testNoSuppressionSomethingElseS() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST11, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(2, rpt.size());
    }

    @Test
    public void testSuppressAll() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST12, new FooRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(0, rpt.size());
    }

    @Test
    public void testSpecificSuppressionAtTopLevel() throws Throwable {
        Report rpt = new Report();
        runTestFromString(TEST13, new BarRule(), rpt,
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals(0, rpt.size());
    }

    private static final String TEST1 = "@SuppressWarnings(\"PMD\")" + PMD.EOL + "public class Foo {}";

    private static final String TEST2 = "@SuppressWarnings(\"PMD\")" + PMD.EOL + "public class Foo {" + PMD.EOL
            + " void bar() {" + PMD.EOL + "  int foo;" + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST3 = "public class Baz {" + PMD.EOL + " @SuppressWarnings(\"PMD\")" + PMD.EOL
            + " public class Bar {" + PMD.EOL + "  void bar() {" + PMD.EOL + "   int foo;" + PMD.EOL + "  }" + PMD.EOL
            + " }" + PMD.EOL + "}";

    private static final String TEST4 = "public class Foo {" + PMD.EOL + " @SuppressWarnings(\"PMD\")" + PMD.EOL
            + " void bar() {" + PMD.EOL + "  int foo;" + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST5 = "public class Bar {" + PMD.EOL + " @SuppressWarnings(\"PMD\")" + PMD.EOL
            + " public Bar() {" + PMD.EOL + "  int foo;" + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST6 = "public class Bar {" + PMD.EOL + " @SuppressWarnings(\"PMD\")" + PMD.EOL
            + " int foo;" + PMD.EOL + " void bar() {" + PMD.EOL + "  int foo;" + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST7 = "public class Bar {" + PMD.EOL + " int foo;" + PMD.EOL
            + " void bar(@SuppressWarnings(\"PMD\") int foo) {}" + PMD.EOL + "}";

    private static final String TEST8 = "public class Bar {" + PMD.EOL + " int foo;" + PMD.EOL + " void bar() {"
            + PMD.EOL + "  @SuppressWarnings(\"PMD\") int foo;" + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST9 = "public class Bar {" + PMD.EOL + " int foo;" + PMD.EOL + " void bar() {"
            + PMD.EOL + "  @SuppressWarnings(\"PMD.NoFoo\") int foo;" + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST9_VALUE1 = "public class Bar {" + PMD.EOL + " int foo;" + PMD.EOL + " void bar() {"
            + PMD.EOL + "  @SuppressWarnings(value = \"PMD.NoFoo\") int foo;" + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST9_VALUE2 = "public class Bar {" + PMD.EOL + " int foo;" + PMD.EOL + " void bar() {"
            + PMD.EOL + "  @SuppressWarnings({\"PMD.NoFoo\"}) int foo;" + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST9_VALUE3 = "public class Bar {" + PMD.EOL + " int foo;" + PMD.EOL + " void bar() {"
            + PMD.EOL + "  @SuppressWarnings(value = {\"PMD.NoFoo\"}) int foo;" + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST9_MULTIPLE_VALUES_1 = "@SuppressWarnings({\"PMD.NoFoo\", \"PMD.NoBar\"})" + PMD.EOL
            + "public class Bar {" + PMD.EOL + " int foo;" + PMD.EOL + " void bar() {" + PMD.EOL + "  int foo;"
            + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST9_MULTIPLE_VALUES_2 = "@SuppressWarnings(value = {\"PMD.NoFoo\", \"PMD.NoBar\"})"
            + PMD.EOL + "public class Bar {" + PMD.EOL + " int foo;" + PMD.EOL + " void bar() {" + PMD.EOL
            + "  int foo;" + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST10 = "public class Bar {" + PMD.EOL + " int foo;" + PMD.EOL + " void bar() {"
            + PMD.EOL + "  @SuppressWarnings(\"\") int foo;" + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST11 = "public class Bar {" + PMD.EOL + " int foo;" + PMD.EOL + " void bar() {"
            + PMD.EOL + "  @SuppressWarnings(\"SomethingElse\") int foo;" + PMD.EOL + " }" + PMD.EOL + "}";

    private static final String TEST12 = "public class Bar {" + PMD.EOL + " @SuppressWarnings(\"all\") int foo;"
            + PMD.EOL + "}";

    private static final String TEST13 = "@SuppressWarnings(\"PMD.NoBar\")" + PMD.EOL + "public class Bar {" + PMD.EOL
            + "}";

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(SuppressWarningsTest.class);
    }
}
