/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.apex.rule.security;

import java.util.Arrays;
import java.util.List;

import net.sourceforge.pmd.lang.apex.ast.ASTDmlDeleteStatement;
import net.sourceforge.pmd.lang.apex.ast.ASTDmlInsertStatement;
import net.sourceforge.pmd.lang.apex.ast.ASTDmlMergeStatement;
import net.sourceforge.pmd.lang.apex.ast.ASTDmlUndeleteStatement;
import net.sourceforge.pmd.lang.apex.ast.ASTDmlUpdateStatement;
import net.sourceforge.pmd.lang.apex.ast.ASTDmlUpsertStatement;
import net.sourceforge.pmd.lang.apex.ast.ASTField;
import net.sourceforge.pmd.lang.apex.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.apex.ast.ASTMethodCallExpression;
import net.sourceforge.pmd.lang.apex.ast.ASTModifierNode;
import net.sourceforge.pmd.lang.apex.ast.ASTNewKeyValueObjectExpression;
import net.sourceforge.pmd.lang.apex.ast.ASTReferenceExpression;
import net.sourceforge.pmd.lang.apex.ast.ASTSoqlExpression;
import net.sourceforge.pmd.lang.apex.ast.ASTSoslExpression;
import net.sourceforge.pmd.lang.apex.ast.ASTUserClass;
import net.sourceforge.pmd.lang.apex.ast.ASTVariableDeclaration;
import net.sourceforge.pmd.lang.apex.ast.ASTVariableExpression;
import net.sourceforge.pmd.lang.apex.ast.ApexNode;

import apex.jorje.data.Identifier;
import apex.jorje.data.ast.TypeRef;
import apex.jorje.semantic.ast.expression.MethodCallExpression;
import apex.jorje.semantic.ast.expression.NewKeyValueObjectExpression;
import apex.jorje.semantic.ast.expression.VariableExpression;
import apex.jorje.semantic.ast.member.Field;
import apex.jorje.semantic.ast.member.Parameter;
import apex.jorje.semantic.ast.statement.FieldDeclaration;
import apex.jorje.semantic.ast.statement.VariableDeclaration;

/**
 * Helper methods
 * 
 * @author sergey.gorbaty
 *
 */
public final class Helper {
    protected static final String ANY_METHOD = "*";

    private Helper() {
        throw new AssertionError("Can't instantiate helper classes");
    }

    static boolean isTestMethodOrClass(final ApexNode<?> node) {
        final List<ASTModifierNode> modifierNode = node.findChildrenOfType(ASTModifierNode.class);
        for (final ASTModifierNode m : modifierNode) {
            if (m.getNode().getModifiers().isTest()) {
                return true;
            }
        }

        final String className = node.getNode().getDefiningType().getApexName();
        if (className.endsWith("Test")) {
            return true;
        }

        return false;
    }

    static boolean foundAnySOQLorSOSL(final ApexNode<?> node) {
        final List<ASTSoqlExpression> dmlSoqlExpression = node.findDescendantsOfType(ASTSoqlExpression.class);
        final List<ASTSoslExpression> dmlSoslExpression = node.findDescendantsOfType(ASTSoslExpression.class);

        if (dmlSoqlExpression.isEmpty() && dmlSoslExpression.isEmpty()) {
            return false;
        }

        return true;
    }

    /**
     * Finds DML operations in a given node descendants' path
     * 
     * @param node
     * 
     * @return true if found DML operations in node descendants
     */
    static boolean foundAnyDML(final ApexNode<?> node) {

        final List<ASTDmlUpsertStatement> dmlUpsertStatement = node.findDescendantsOfType(ASTDmlUpsertStatement.class);
        final List<ASTDmlUpdateStatement> dmlUpdateStatement = node.findDescendantsOfType(ASTDmlUpdateStatement.class);
        final List<ASTDmlUndeleteStatement> dmlUndeleteStatement = node
                .findDescendantsOfType(ASTDmlUndeleteStatement.class);
        final List<ASTDmlMergeStatement> dmlMergeStatement = node.findDescendantsOfType(ASTDmlMergeStatement.class);
        final List<ASTDmlInsertStatement> dmlInsertStatement = node.findDescendantsOfType(ASTDmlInsertStatement.class);
        final List<ASTDmlDeleteStatement> dmlDeleteStatement = node.findDescendantsOfType(ASTDmlDeleteStatement.class);

        if (dmlUpsertStatement.isEmpty() && dmlUpdateStatement.isEmpty() && dmlUndeleteStatement.isEmpty()
                && dmlMergeStatement.isEmpty() && dmlInsertStatement.isEmpty() && dmlDeleteStatement.isEmpty()) {
            return false;
        }

        return true;
    }

    static boolean isMethodName(final ASTMethodCallExpression methodNode, final String className,
            final String methodName) {
        final ASTReferenceExpression reference = methodNode.getFirstChildOfType(ASTReferenceExpression.class);
        if (reference != null && reference.getNode().getNames().size() == 1) {
            if (reference.getNode().getNames().get(0).getValue().equalsIgnoreCase(className)) {
                if (methodName.equals(ANY_METHOD) || isMethodName(methodNode, methodName)) {
                    return true;
                }
            }
        }

        return false;

    }

    static boolean isMethodName(final ASTMethodCallExpression m, final String methodName) {
        return isMethodName(m.getNode(), methodName);
    }

    static boolean isMethodName(final MethodCallExpression m, final String methodName) {
        return m.getMethodName().equalsIgnoreCase(methodName);
    }

    static boolean isMethodCallChain(final ASTMethodCallExpression methodNode, final String... methodNames) {
        String methodName = methodNames[methodNames.length - 1];
        if (Helper.isMethodName(methodNode, methodName)) {
            final ASTReferenceExpression reference = methodNode.getFirstChildOfType(ASTReferenceExpression.class);
            if (reference != null) {
                final ASTMethodCallExpression nestedMethod = reference
                        .getFirstChildOfType(ASTMethodCallExpression.class);
                if (nestedMethod != null) {
                    String[] newMethodNames = Arrays.copyOf(methodNames, methodNames.length - 1);
                    return isMethodCallChain(nestedMethod, newMethodNames);
                } else {
                    String[] newClassName = Arrays.copyOf(methodNames, methodNames.length - 1);
                    if (newClassName.length == 1) {
                        return Helper.isMethodName(methodNode, newClassName[0], methodName);
                    }
                }
            }
        }

        return false;
    }

    static String getFQVariableName(final ASTVariableExpression variable) {
        final ASTReferenceExpression ref = variable.getFirstChildOfType(ASTReferenceExpression.class);
        String objectName = "";
        if (ref != null) {
            if (ref.getNode().getNames().size() == 1) {
                objectName = ref.getNode().getNames().get(0).getValue() + ".";
            }
        }

        VariableExpression n = variable.getNode();
        StringBuilder sb = new StringBuilder().append(n.getDefiningType().getApexName()).append(":").append(objectName)
                .append(n.getIdentifier().getValue());
        return sb.toString();
    }

    static String getFQVariableName(final ASTVariableDeclaration variable) {
        VariableDeclaration n = variable.getNode();
        StringBuilder sb = new StringBuilder().append(n.getDefiningType().getApexName()).append(":")
                .append(n.getLocalInfo().getName());
        return sb.toString();
    }

    static String getFQVariableName(final ASTField variable) {
        Field n = variable.getNode();
        StringBuilder sb = new StringBuilder().append(n.getDefiningType().getApexName()).append(":")
                .append(n.getFieldInfo().getName());
        return sb.toString();
    }

    static String getVariableType(final ASTField variable) {
        Field n = variable.getNode();
        StringBuilder sb = new StringBuilder().append(n.getDefiningType().getApexName()).append(":")
                .append(n.getFieldInfo().getName());
        return sb.toString();
    }
    
    static String getFQVariableName(final ASTFieldDeclaration variable) {
        FieldDeclaration n = variable.getNode();
        String name = "";

        try {
            java.lang.reflect.Field f = n.getClass().getDeclaredField("name");
            f.setAccessible(true);
            Identifier nameField = (Identifier) f.get(n);
            name = nameField.getValue();

        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        StringBuilder sb = new StringBuilder().append(n.getDefiningType().getApexName()).append(":").append(name);
        return sb.toString();
    }

    static String getFQVariableName(final ASTNewKeyValueObjectExpression variable) {
        NewKeyValueObjectExpression n = variable.getNode();
        TypeRef typeRef = n.getTypeRef();
        String objType = typeRef.getNames().get(0).getValue();

        StringBuilder sb = new StringBuilder().append(n.getDefiningType().getApexName()).append(":").append(objType);
        return sb.toString();
    }

    static boolean isSystemLevelClass(ASTUserClass node) {
        List<TypeRef> interfaces = node.getNode().getDefiningType().getCodeUnitDetails().getInterfaceTypeRefs();

        boolean hasWhitelistedInterfaces = false;
        for (TypeRef intObject : interfaces) {
            if (isWhitelisted(intObject.getNames())) {
                hasWhitelistedInterfaces = true;
                break;
            }
        }

        return hasWhitelistedInterfaces;
    }

    private static boolean isWhitelisted(List<Identifier> ids) {
        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < ids.size(); i++) {
            sb.append(ids.get(i).getValue());

            if (i != ids.size() - 1) {
                sb.append(".");
            }
        }

        switch (sb.toString().toLowerCase()) {
        case "queueable":
        case "database.batchable":
        case "installhandler":
            return true;
        default:
            break;
        }
        return false;
    }

    public static String getFQVariableName(Parameter p) {
        StringBuffer sb = new StringBuffer();
        sb.append(p.getDefiningType()).append(":").append(p.getName().getValue());
        return sb.toString();
    }

}
