package test.net.sourceforge.pmd.rules.strings;

import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.Rule;
import test.net.sourceforge.pmd.testframework.SimpleAggregatorTst;
import test.net.sourceforge.pmd.testframework.TestDescriptor;

public class UseIndexOfCharTest extends SimpleAggregatorTst {

    private Rule rule;

    public void setUp() throws Exception {
        rule = findRule("rulesets/strings.xml", "UseIndexOfChar");
    }

    public void testAll() {
        runTests(new TestDescriptor[]{
            new TestDescriptor(TEST1, "failure case", 1, rule),
            new TestDescriptor(TEST2, "using single quotes, OK", 0, rule),
            new TestDescriptor(TEST3, "indexOf multi-character literal, OK", 0, rule),
            new TestDescriptor(TEST4, "using indexOf(singleCharString, int)", 1, rule),
        });
    }

    private static final String TEST1 =
            "public class Foo {" + PMD.EOL +
            " void bar() {" + PMD.EOL +
            "  String x = \"hello\";" + PMD.EOL +
            "  if (x.indexOf(\"o\") == -1) {}" + PMD.EOL +
            " }" + PMD.EOL +
            "}";

    private static final String TEST2 =
            "public class Foo {" + PMD.EOL +
            " void bar() {" + PMD.EOL +
            "  String x = \"hello\";" + PMD.EOL +
            "  if (x.indexOf('o') == -1) {}" + PMD.EOL +
            " }" + PMD.EOL +
            "}";

    private static final String TEST3 =
            "public class Foo {" + PMD.EOL +
            " void bar() {" + PMD.EOL +
            "  String x = \"hello\";" + PMD.EOL +
            "  if (x.indexOf(\"ello\") == -1) {}" + PMD.EOL +
            " }" + PMD.EOL +
            "}";

    private static final String TEST4 =
            "public class Foo {" + PMD.EOL +
            " void bar() {" + PMD.EOL +
            "  String x = \"hello world\";" + PMD.EOL +
            "  if (x.indexOf(\"e\", 5) == -1) {}" + PMD.EOL +
            " }" + PMD.EOL +
            "}";

}
