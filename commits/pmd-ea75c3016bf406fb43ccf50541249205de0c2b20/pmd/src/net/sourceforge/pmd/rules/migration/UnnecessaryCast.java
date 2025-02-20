package net.sourceforge.pmd.rules.migration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sourceforge.pmd.lang.java.AbstractJavaRule;
import net.sourceforge.pmd.lang.java.ast.ASTCastExpression;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTLocalVariableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTName;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclaratorId;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.symboltable.NameOccurrence;

public class UnnecessaryCast extends AbstractJavaRule {

    private static Set<String> implClassNames = new HashSet<String>();

    static {
        implClassNames.add("List");
        implClassNames.add("Set");
        implClassNames.add("Map");
        implClassNames.add("java.util.List");
        implClassNames.add("java.util.Set");
        implClassNames.add("java.util.Map");
        implClassNames.add("ArrayList");
        implClassNames.add("HashSet");
        implClassNames.add("HashMap");
        implClassNames.add("LinkedHashMap");
        implClassNames.add("LinkedHashSet");
        implClassNames.add("TreeSet");
        implClassNames.add("TreeMap");
        implClassNames.add("Vector");
        implClassNames.add("java.util.ArrayList");
        implClassNames.add("java.util.HashSet");
        implClassNames.add("java.util.HashMap");
        implClassNames.add("java.util.LinkedHashMap");
        implClassNames.add("java.util.LinkedHashSet");
        implClassNames.add("java.util.TreeSet");
        implClassNames.add("java.util.TreeMap");
        implClassNames.add("java.util.Vector");
    }

    public Object visit(ASTLocalVariableDeclaration node, Object data) {
        return process(node, data);
    }

    public Object visit(ASTFieldDeclaration node, Object data) {
        return process(node, data);
    }

    private Object process(Node node, Object data) {
        ASTClassOrInterfaceType cit = node.getFirstChildOfType(ASTClassOrInterfaceType.class);
        if (cit == null || !implClassNames.contains(cit.getImage())) {
            return data;
        }
        cit = cit.getFirstChildOfType(ASTClassOrInterfaceType.class);
        if (cit == null) {
            return data;
        }
        ASTVariableDeclaratorId decl = node.getFirstChildOfType(ASTVariableDeclaratorId.class);
        List<NameOccurrence> usages = decl.getUsages();
        for (NameOccurrence no: usages) {
            ASTName name = (ASTName) no.getLocation();
            Node n = name.jjtGetParent().jjtGetParent().jjtGetParent();
            if (ASTCastExpression.class.equals(n.getClass())) {
                addViolation(data, n);
            }
        }
        return null;
    }
}
