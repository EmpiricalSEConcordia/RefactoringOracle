/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.symboltable;

import net.sourceforge.pmd.util.Applier;
import net.sourceforge.pmd.ast.SimpleNode;
import net.sourceforge.pmd.ast.ASTConstructorDeclaration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodScope extends AbstractScope {

    protected Map variableNames = new HashMap();
    private SimpleNode node;

    public MethodScope(SimpleNode node) {
        this.node = node;
    }

    public MethodScope getEnclosingMethodScope() {
        return this;
    }

    public Map getVariableDeclarations() {
        VariableUsageFinderFunction f = new VariableUsageFinderFunction(variableNames);
        Applier.apply(f, variableNames.keySet().iterator());
        return f.getUsed();
    }

    public NameDeclaration addVariableNameOccurrence(NameOccurrence occurrence) {
        NameDeclaration decl = findVariableHere(occurrence);
        if (decl != null && !occurrence.isThisOrSuper()) {
            ((List) variableNames.get(decl)).add(occurrence);
        }
        return decl;
    }

    public void addDeclaration(VariableNameDeclaration variableDecl) {
        if (variableNames.containsKey(variableDecl)) {
            throw new RuntimeException("Variable " + variableDecl + " is already in the symbol table");
        }
        variableNames.put(variableDecl, new ArrayList());
    }

    public NameDeclaration findVariableHere(NameOccurrence occurrence) {
        if (occurrence.isThisOrSuper()) {
            return null;
        }
        ImageFinderFunction finder = new ImageFinderFunction(occurrence.getImage());
        Applier.apply(finder, variableNames.keySet().iterator());
        return finder.getDecl();
    }

    public String getName() {
        if (node instanceof ASTConstructorDeclaration) {
            return this.getEnclosingClassScope().getClassName();
        }
        return ((SimpleNode)node.jjtGetChild(1)).getImage();
    }

    public String toString() {
        return "MethodScope variable names:" + super.glomNames(variableNames.keySet().iterator());
    }
}
