package test.net.sourceforge.pmd.ast;

import junit.framework.TestCase;
import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.TargetJDK1_5;
import net.sourceforge.pmd.ast.JavaParser;

import java.io.StringReader;

public class JDKVersionTest extends TestCase  {

/*
    // enum keyword/identifier
    public void testEnumAsKeywordShouldFailWith14() throws Throwable {
        try {
            JavaParser p = new TargetJDK1_4().createParser(new StringReader(JDK15_ENUM));
            p.CompilationUnit();
            throw new Error("JDK 1.4 parser should have failed to parse enum used as keyword");
        } catch (ParseException e) {}    // cool
    }

    public void testEnumAsIdentifierShouldPassWith14() throws Throwable {
        JavaParser p = new TargetJDK1_4().createParser(new StringReader(JDK14_ENUM));
        p.CompilationUnit();
    }

    public void testEnumAsKeywordShouldPassWith15() throws Throwable {
        JavaParser p = new TargetJDK1_5().createParser(new StringReader(JDK15_ENUM));
        p.CompilationUnit();
    }

    public void testEnumAsIdentifierShouldFailWith15() throws Throwable {
        try {
            TargetJDKVersion jdk = new TargetJDK1_5();
            JavaParser p = jdk.createParser(new StringReader(JDK14_ENUM));
            p.CompilationUnit();
            throw new Error("JDK 1.5 parser should have failed to parse enum used as identifier");
        } catch (ParseException e) {}    // cool
    }
    // enum keyword/identifier

    // assert keyword/identifier
    public void testAssertAsKeywordVariantsSucceedWith1_4() {
        (new TargetJDK1_4()).createParser(new StringReader(ASSERT_TEST1)).CompilationUnit();
        (new TargetJDK1_4()).createParser(new StringReader(ASSERT_TEST2)).CompilationUnit();
        (new TargetJDK1_4()).createParser(new StringReader(ASSERT_TEST3)).CompilationUnit();
        (new TargetJDK1_4()).createParser(new StringReader(ASSERT_TEST4)).CompilationUnit();
    }

    public void testAssertAsVariableDeclIdentifierFailsWith1_4() {
        try {
            (new TargetJDK1_4()).createParser(new StringReader(ASSERT_TEST5)).CompilationUnit();
            throw new RuntimeException("Usage of assert as identifier should have failed with 1.4");
        } catch (ParseException pe) {
            // cool
        }
    }

    public void testAssertAsMethodNameIdentifierFailsWith1_4() {
        try {
            (new TargetJDK1_4()).createParser(new StringReader(ASSERT_TEST7)).CompilationUnit();
            throw new RuntimeException("Usage of assert as identifier should have failed with 1.4");
        } catch (ParseException pe) {
            // cool
        }
    }

    public void testAssertAsIdentifierSucceedsWith1_3() {
        JavaParser jp = (new TargetJDK1_3()).createParser(new StringReader(ASSERT_TEST5));
        jp.CompilationUnit();
    }

    public void testAssertAsKeywordFailsWith1_3() {
        try {
            JavaParser jp = (new TargetJDK1_3()).createParser(new StringReader(ASSERT_TEST6));
            jp.CompilationUnit();
            throw new RuntimeException("Usage of assert as keyword should have failed with 1.3");
        } catch (ParseException pe) {
            // cool
        }
    }
    // assert keyword/identifier

    public void testVarargsShouldPassWith15() throws Throwable {
        JavaParser p = new TargetJDK1_5().createParser(new StringReader(JDK15_VARARGS));
        p.CompilationUnit();
    }

    public void testVarargsShouldFailWith14() throws Throwable {
        try {
            JavaParser p = new TargetJDK1_4().createParser(new StringReader(JDK15_VARARGS));
            p.CompilationUnit();
            fail("Should have throw ParseException!");
        } catch (ParseException pe) {
            // cool
        }
    }

    public void testJDK15ForLoopSyntaxShouldPassWith15() throws Throwable {
        JavaParser p = new TargetJDK1_5().createParser(new StringReader(JDK15_FORLOOP));
        p.CompilationUnit();
    }

    public void testJDK15ForLoopSyntaxWithModifiers() throws Throwable {
        JavaParser p = new TargetJDK1_5().createParser(new StringReader(JDK15_FORLOOP_WITH_MODIFIER));
        p.CompilationUnit();
    }

    public void testJDK15ForLoopShouldFailWith14() throws Throwable {
        try {
            JavaParser p = new TargetJDK1_4().createParser(new StringReader(JDK15_FORLOOP));
            p.CompilationUnit();
            fail("Should have throw ParseException!");
        } catch (ParseException pe) {
            // cool
        }
    }

    public void testJDK15GenericsSyntaxShouldPassWith15() throws Throwable {
        JavaParser p = new TargetJDK1_5().createParser(new StringReader(JDK15_GENERICS));
        p.CompilationUnit();
    }

    public void testFieldsBug() throws Throwable {
        JavaParser p = new TargetJDK1_4().createParser(new StringReader(FIELDS_BUG));
        p.CompilationUnit();
    }

    public void testGTBug() throws Throwable {
        JavaParser p = new TargetJDK1_4().createParser(new StringReader(GT_BUG));
        p.CompilationUnit();
    }

    public void testAnnotationsBug() throws Throwable {
        JavaParser p = new TargetJDK1_5().createParser(new StringReader(ANNOTATIONS_BUG));
        p.CompilationUnit();
    }

    public void testGenericsBug() throws Throwable {
        JavaParser p = new TargetJDK1_5().createParser(new StringReader(GENERICS_BUG));
        p.CompilationUnit();
    }
*/

    public void testGenericsAnnotationBug() throws Throwable {
        JavaParser p = new TargetJDK1_5().createParser(new StringReader(GENERICS_ANNOTATION_BUG));
        p.CompilationUnit();
    }

    private static final String ASSERT_TEST1 =
    "public class Foo {" + PMD.EOL +
    " void bar() {" + PMD.EOL +
    "  assert x == 2;" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String ASSERT_TEST2 =
    "public class Foo {" + PMD.EOL +
    " void bar() {" + PMD.EOL +
    "  assert (x == 2);" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String ASSERT_TEST3 =
    "public class Foo {" + PMD.EOL +
    " void bar() {" + PMD.EOL +
    "  assert (x==2) : \"hi!\";" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String ASSERT_TEST4 =
    "public class Foo {" + PMD.EOL +
    " void bar() {" + PMD.EOL +
    "  assert (x==2) : \"hi!\";" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String ASSERT_TEST5 =
    "public class Foo {" + PMD.EOL +
    "  int assert = 2;" + PMD.EOL +
    "}";

    private static final String ASSERT_TEST6 =
    "public class Foo {" + PMD.EOL +
    " void foo() {" + PMD.EOL +
    "  assert (x == 2) : \"hi!\";" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String ASSERT_TEST7 =
    "public class Foo {" + PMD.EOL +
    " void assert() {}" + PMD.EOL +
    "}";

    private static final String JDK15_ENUM =
    "public class Test {" + PMD.EOL +
    " enum Season { winter, spring, summer, fall };" + PMD.EOL +
    "}";

    private static final String JDK14_ENUM =
    "public class Test {" + PMD.EOL +
    " int enum;" + PMD.EOL +
    "}";

    private static final String JDK15_VARARGS =
    "public class Test {" + PMD.EOL +
    " void bar(Object ... args) {}" + PMD.EOL +
    "}";

    private static final String JDK15_FORLOOP =
    "public class Test {" + PMD.EOL +
    " void foo(List list) {" + PMD.EOL +
    "  for (Integer i : list) {}" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String JDK15_FORLOOP_WITH_MODIFIER =
    "public class Test {" + PMD.EOL +
    " void foo(List list) {" + PMD.EOL +
    "  for (final Integer i : list) {}" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String JDK15_GENERICS =
    "public class Test {" + PMD.EOL +
    "  ArrayList<Integer> list =  new ArrayList<Integer>();" + PMD.EOL +
    "}";

    private static final String FIELDS_BUG =
    "public class Test {" + PMD.EOL +
    "  private Foo bar;" + PMD.EOL +
    "}";

    private static final String GT_BUG =
    "public class Test {" + PMD.EOL +
    "  int y = x > 32;" + PMD.EOL +
    "}";

    private static final String ANNOTATIONS_BUG =
    "@Target(ElementType.METHOD)" + PMD.EOL +
    "public @interface Foo {" + PMD.EOL +
    "}";

    private static final String GENERICS_BUG =
    "public class Foo {" + PMD.EOL +
    " Class<Double> foo = (Class<Double>)clazz;" + PMD.EOL +
    "}";

    private static final String GENERICS_ANNOTATION_BUG =
    "public class Foo {" + PMD.EOL +
    " public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {\n"  + PMD.EOL +
    "  return null;"  + PMD.EOL +
    " }" + PMD.EOL +
    "}";
}
