/*
 * User: tom
 * Date: Aug 14, 2002
 * Time: 10:43:59 AM
 */
package net.sourceforge.pmd.rules;

import net.sourceforge.pmd.AbstractRule;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.ast.*;

public class SimplifyBooleanReturnsRule extends AbstractRule {

    public Object visit(ASTIfStatement node, Object data) {
        // only deal with if..then..else stmts
        if (node.jjtGetNumChildren() != 3) {
            return super.visit(node, data);
        }

        // don't bother if either the if or the else block is empty
        if (node.jjtGetChild(1).jjtGetNumChildren() == 0 || node.jjtGetChild(2).jjtGetNumChildren() == 0) {
            return super.visit(node, data);
        }

        // first case:
        // If
        //  Expr
        //  Statement
        //   ReturnStatement
        //  Statement
        //   ReturnStatement
        // i.e.,
        // if (foo)
        //  return true;
        // else
        //  return false;
        if (node.jjtGetChild(1).jjtGetChild(0) instanceof ASTReturnStatement
         && node.jjtGetChild(2).jjtGetChild(0) instanceof ASTReturnStatement
         && terminatesInBooleanLiteral((SimpleNode)node.jjtGetChild(1).jjtGetChild(0))
         && terminatesInBooleanLiteral((SimpleNode)node.jjtGetChild(2).jjtGetChild(0))) {
            RuleContext ctx = (RuleContext)data;
            ctx.getReport().addRuleViolation(createRuleViolation(ctx, node.getBeginLine()));
        } else if (node.jjtGetChild(1).jjtGetChild(0) instanceof ASTBlock
            && node.jjtGetChild(1).jjtGetChild(0).jjtGetNumChildren() == 1
            && node.jjtGetChild(1).jjtGetChild(0).jjtGetChild(0) instanceof ASTBlockStatement
            && node.jjtGetChild(1).jjtGetChild(0).jjtGetChild(0).jjtGetChild(0) instanceof ASTStatement
            && node.jjtGetChild(1).jjtGetChild(0).jjtGetChild(0).jjtGetChild(0).jjtGetChild(0) instanceof ASTReturnStatement
            && node.jjtGetChild(2).jjtGetChild(0) instanceof ASTBlock
            && node.jjtGetChild(2).jjtGetChild(0).jjtGetNumChildren() == 1
            && node.jjtGetChild(2).jjtGetChild(0).jjtGetChild(0) instanceof ASTBlockStatement
            && node.jjtGetChild(2).jjtGetChild(0).jjtGetChild(0).jjtGetChild(0) instanceof ASTStatement
            && node.jjtGetChild(2).jjtGetChild(0).jjtGetChild(0).jjtGetChild(0).jjtGetChild(0) instanceof ASTReturnStatement
            && terminatesInBooleanLiteral((SimpleNode)node.jjtGetChild(1).jjtGetChild(0))
            && terminatesInBooleanLiteral((SimpleNode)node.jjtGetChild(2).jjtGetChild(0))) {
            RuleContext ctx = (RuleContext)data;
            ctx.getReport().addRuleViolation(createRuleViolation(ctx, node.getBeginLine()));
            // second case
            // If
            // Expr
            // Statement
            //  Block
            //   BlockStatement
            //    Statement
            //     ReturnStatement
            // Statement
            //  Block
            //   BlockStatement
            //    Statement
            //     ReturnStatement
            // i.e.,
            // if (foo) {
            //  return true;
            // } else {
            //  return false;
            // }
        }

        return super.visit(node, data);
    }

    private boolean terminatesInBooleanLiteral(SimpleNode node) {
        return eachNodeHasOneChild(node) && (getLastChild(node) instanceof ASTBooleanLiteral);
    }

    private boolean eachNodeHasOneChild(SimpleNode node) {
        if (node.jjtGetNumChildren() > 1) {
            return false;
        }
        if (node.jjtGetNumChildren() == 0) {
            return true;
        }
        return eachNodeHasOneChild((SimpleNode)node.jjtGetChild(0));
    }

    private SimpleNode getLastChild(SimpleNode node) {
        if (node.jjtGetNumChildren() == 0) {
            return node;
        } else {
            return getLastChild((SimpleNode)node.jjtGetChild(0));
        }
    }
}
