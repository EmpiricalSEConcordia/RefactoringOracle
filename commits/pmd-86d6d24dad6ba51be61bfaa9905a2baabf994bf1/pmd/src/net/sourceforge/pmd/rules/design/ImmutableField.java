/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.rules.design;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceBodyDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTDoStatement;
import net.sourceforge.pmd.lang.java.ast.ASTForStatement;
import net.sourceforge.pmd.lang.java.ast.ASTIfStatement;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTryStatement;
import net.sourceforge.pmd.lang.java.ast.ASTVariableInitializer;
import net.sourceforge.pmd.lang.java.ast.ASTWhileStatement;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.symboltable.NameOccurrence;
import net.sourceforge.pmd.symboltable.VariableNameDeclaration;

/**
 * @author Olander
 */
public class ImmutableField extends AbstractJavaRule {

    private static final int MUTABLE = 0;
    private static final int IMMUTABLE = 1;
    private static final int CHECKDECL = 2;

    public Object visit(ASTClassOrInterfaceDeclaration node, Object data) {
        Map<VariableNameDeclaration, List<NameOccurrence>> vars = node.getScope().getVariableDeclarations();
        List<ASTConstructorDeclaration> constructors = findAllConstructors(node);
        for (Map.Entry<VariableNameDeclaration, List<NameOccurrence>> entry: vars.entrySet()) {
            VariableNameDeclaration field = entry.getKey();
            if (field.getAccessNodeParent().isStatic() || !field.getAccessNodeParent().isPrivate() || field.getAccessNodeParent().isFinal() || field.getAccessNodeParent().isVolatile()) {
                continue;
            }

            int result = initializedInConstructor(entry.getValue(), new HashSet<ASTConstructorDeclaration>(constructors));
            if (result == MUTABLE) {
                continue;
            }
            if (result == IMMUTABLE || (result == CHECKDECL && initializedWhenDeclared(field))) {
                addViolation(data, field.getNode(), field.getImage());
            }
        }
        return super.visit(node, data);
    }

    private boolean initializedWhenDeclared(VariableNameDeclaration field) {
        return !((Node)field.getAccessNodeParent()).findChildrenOfType(ASTVariableInitializer.class).isEmpty();
    }

    private int initializedInConstructor(List<NameOccurrence> usages, Set<ASTConstructorDeclaration> allConstructors) {
        int result = MUTABLE, methodInitCount = 0;
        Set<Node> consSet = new HashSet<Node>();
        for (NameOccurrence occ: usages) {
            if (occ.isOnLeftHandSide() || occ.isSelfAssignment()) {
        	Node node = occ.getLocation();
                ASTConstructorDeclaration constructor = node.getFirstParentOfType(ASTConstructorDeclaration.class);
                if (constructor != null) {
                    if (inLoopOrTry(node)) {
                        continue;
                    }
                    //Check for assigns in if-statements, which can depend on constructor 
                    //args or other runtime knowledge and can be a valid reason to instantiate
                    //in one constructor only
                    if (node.getFirstParentOfType(ASTIfStatement.class) != null) {
                    	methodInitCount++;
                    }
                    if (inAnonymousInnerClass(node)) {
                        methodInitCount++;
                    } else {
                        consSet.add(constructor);
                    }
                } else {
                    if (node.getFirstParentOfType(ASTMethodDeclaration.class) != null) {
                        methodInitCount++;
                    }
                }
            }
        }
        if (usages.isEmpty() || ((methodInitCount == 0) && consSet.isEmpty())) {
            result = CHECKDECL;
        } else {
            allConstructors.removeAll(consSet);
            if (allConstructors.isEmpty() && (methodInitCount == 0)) {
                result = IMMUTABLE;
            }
        }
        return result;
    }

    private boolean inLoopOrTry(Node node) {
        return node.getFirstParentOfType(ASTTryStatement.class) != null ||
                node.getFirstParentOfType(ASTForStatement.class) != null ||
                node.getFirstParentOfType(ASTWhileStatement.class) != null ||
                node.getFirstParentOfType(ASTDoStatement.class) != null;
    }

    private boolean inAnonymousInnerClass(Node node) {
        ASTClassOrInterfaceBodyDeclaration parent = node.getFirstParentOfType(ASTClassOrInterfaceBodyDeclaration.class);
        return parent != null && parent.isAnonymousInnerClass();
    }

    private List<ASTConstructorDeclaration> findAllConstructors(ASTClassOrInterfaceDeclaration node) {
        List<ASTConstructorDeclaration> cons = new ArrayList<ASTConstructorDeclaration>();
        node.findChildrenOfType(ASTConstructorDeclaration.class, cons, false);
        return cons;
    }
}
