/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.lang.java.rule.junit;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTName;
import net.sourceforge.pmd.lang.java.ast.ASTPrimaryExpression;
import net.sourceforge.pmd.lang.java.ast.ASTPrimaryPrefix;
import net.sourceforge.pmd.lang.java.ast.ASTStatementExpression;

public class JUnitTestsShouldIncludeAssertRule extends AbstractJUnitRule {

    public Object visit(ASTClassOrInterfaceDeclaration node, Object data) {
        if (node.isInterface()) {
            return data;
        }
        return super.visit(node, data);
    }

    public Object visit(ASTMethodDeclaration method, Object data) {
        if (isJUnitMethod(method, data))  {
            if (!containsAssert(method.getBlock(), false)) {
                addViolation(data, method);
            }
        }
		return data;
	}

    private boolean containsAssert(Node n, boolean assertFound) {
        if (!assertFound) {
            if (n instanceof ASTStatementExpression) {
                if (isAssertOrFailStatement((ASTStatementExpression)n)) {
                    return true;
                }
            }
            if (!assertFound) {
                for (int i=0;i<n.jjtGetNumChildren() && ! assertFound;i++) {
                    Node c = n.jjtGetChild(i);
                    if (containsAssert(c, assertFound)) 
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Tells if the expression is an assert statement or not.
     */
    private boolean isAssertOrFailStatement(ASTStatementExpression expression) {
        if (expression!=null 
                && expression.jjtGetNumChildren()>0
                && expression.jjtGetChild(0) instanceof ASTPrimaryExpression
                ) {
            ASTPrimaryExpression pe = (ASTPrimaryExpression) expression.jjtGetChild(0);
            if (pe.jjtGetNumChildren()> 0 && pe.jjtGetChild(0) instanceof ASTPrimaryPrefix) {
                ASTPrimaryPrefix pp = (ASTPrimaryPrefix) pe.jjtGetChild(0);
                if (pp.jjtGetNumChildren()>0 && pp.jjtGetChild(0) instanceof ASTName) {
                    ASTName n = (ASTName) pp.jjtGetChild(0);
                    if (n.getImage()!=null && (n.getImage().startsWith("assert") || n.getImage().startsWith("fail") )) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
