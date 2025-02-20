/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
/* Generated By:JJTree: Do not edit this line. ASTAnnotation.java */

package net.sourceforge.pmd.lang.java.ast;

import java.util.Arrays;
import java.util.List;

import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.lang.ast.Node;

public class ASTAnnotation extends AbstractJavaNode {

    private static List<String> unusedRules = Arrays.asList(new String[] { "UnusedPrivateField", "UnusedLocalVariable",
        "UnusedPrivateMethod", "UnusedFormalParameter" });

    private static List<String> serialRules = Arrays
            .asList(new String[] { "BeanMembersShouldSerialize", "MissingSerialVersionUID" });

    public ASTAnnotation(int id) {
        super(id);
    }

    public ASTAnnotation(JavaParser p, int id) {
        super(p, id);
    }

    public boolean suppresses(Rule rule) {
        final String ruleAnno = "\"PMD." + rule.getName() + "\"";

        if (jjtGetChild(0) instanceof ASTSingleMemberAnnotation) {
            ASTSingleMemberAnnotation n = (ASTSingleMemberAnnotation) jjtGetChild(0);
            return checkAnnototation(n, ruleAnno, rule);
        } else if (jjtGetChild(0) instanceof ASTNormalAnnotation) {
            ASTNormalAnnotation n = (ASTNormalAnnotation) jjtGetChild(0);
            return checkAnnototation(n, ruleAnno, rule);
        }
        return false;
    }

    private boolean checkAnnototation(Node n, String ruleAnno, Rule rule) {
        if (n.jjtGetChild(0) instanceof ASTName) {
            ASTName annName = (ASTName) n.jjtGetChild(0);

            if ("SuppressWarnings".equals(annName.getImage())
                    || "java.lang.SuppressWarnings".equals(annName.getImage())) {
                List<ASTLiteral> nodes = n.findDescendantsOfType(ASTLiteral.class);
                for (ASTLiteral element : nodes) {
                    if (element.hasImageEqualTo("\"PMD\"") || element.hasImageEqualTo(ruleAnno)
                    // Check for standard annotations values
                            || element.hasImageEqualTo("\"all\"")
                            || element.hasImageEqualTo("\"serial\"") && serialRules.contains(rule.getName())
                            || element.hasImageEqualTo("\"unused\"") && unusedRules.contains(rule.getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Accept the visitor.
     */
    @Override
    public Object jjtAccept(JavaParserVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }
}
