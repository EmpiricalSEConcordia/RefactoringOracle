package net.sourceforge.pmd.rules;

import java.util.HashSet;
import java.util.Set;

import net.sourceforge.pmd.lang.java.AbstractJavaRule;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTName;
import net.sourceforge.pmd.lang.java.ast.ASTPrimaryPrefix;
import net.sourceforge.pmd.symboltable.MethodScope;

public class AvoidCallingFinalize extends AbstractJavaRule {

    private Set<MethodScope> checked = new HashSet<MethodScope>();

    public Object visit(ASTCompilationUnit acu, Object ctx) {
        checked.clear();
        return super.visit(acu, ctx);
    }

    public Object visit(ASTName name, Object ctx) {
        if (name.getImage() == null ||  !name.getImage().endsWith("finalize")) {
            return ctx;
        }
        MethodScope meth = name.getScope().getEnclosingMethodScope();
        if (meth.getName().equals("finalize")) {
            return ctx;
        }
        if (checked.contains(meth)) {
            return ctx;
        }
        checked.add(meth);
        addViolation(ctx, name);
        return ctx;
    }

    public Object visit(ASTPrimaryPrefix pp, Object ctx) {
        if (pp.getImage() == null || !pp.getImage().endsWith("finalize")) {
            return super.visit(pp, ctx);
        }
        MethodScope meth = pp.getScope().getEnclosingMethodScope();
        if (meth.getName().equals("finalize")) {
            return super.visit(pp, ctx);
        }
        if (checked.contains(meth)) {
            return super.visit(pp, ctx);
        }
        checked.add(meth);
        addViolation(ctx, pp);
        return super.visit(pp, ctx);
    }
}
