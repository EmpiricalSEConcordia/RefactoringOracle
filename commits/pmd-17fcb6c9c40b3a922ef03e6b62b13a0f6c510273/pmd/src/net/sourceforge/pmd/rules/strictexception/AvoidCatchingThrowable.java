package net.sourceforge.pmd.rules.strictexception;

import net.sourceforge.pmd.AbstractRule;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.ast.ASTCatch;
import net.sourceforge.pmd.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.ast.ASTCatchStatement;
import net.sourceforge.pmd.ast.ASTType;

/**
 * PMD rule which is going to find <code>catch</code> statements
 * containing <code>throwable</code> as the type definition.
 * <p/>
 *
 * @author <a mailto:trondandersen@c2i.net>Trond Andersen</a>
 */
public class AvoidCatchingThrowable extends AbstractRule {

    public Object visit(ASTCatchStatement node, Object data) {
        ASTType type = (ASTType) node.findChildrenOfType(ASTType.class).get(0);
        ASTClassOrInterfaceType name = (ASTClassOrInterfaceType) type.findChildrenOfType(ASTClassOrInterfaceType.class).get(0);
        if (name.getImage().equals("Throwable")) {
            ((RuleContext)data).getReport().addRuleViolation(createRuleViolation(((RuleContext)data), name));
        }
        return super.visit(node, data);
    }
}
