/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2016 The JavaParser Team.
 *
 * This file is part of JavaParser.
 * 
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License 
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

package com.github.javaparser.ast.body;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.AssignExpr.Operator;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithJavaDoc;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.github.javaparser.ast.nodeTypes.NodeWithVariables;
import com.github.javaparser.ast.observer.ObservableProperty;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import static com.github.javaparser.ast.Modifier.PUBLIC;
import static com.github.javaparser.ast.NodeList.nodeList;
import static com.github.javaparser.ast.type.VoidType.VOID_TYPE;
import static com.github.javaparser.utils.Utils.assertNotNull;

/**
 * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-8.3">JLS</a>
 * The declaration of a field in a class. "private static int a=15*15;" in this example: <code>class X { private static int a=15*15; }</code>
 * @author Julio Vilmar Gesser
 */
public final class FieldDeclaration extends BodyDeclaration<FieldDeclaration> implements
        NodeWithJavaDoc<FieldDeclaration>,
        NodeWithModifiers<FieldDeclaration>,
        NodeWithVariables<FieldDeclaration> {

    private EnumSet<Modifier> modifiers;

    private NodeList<VariableDeclarator> variables;

    public FieldDeclaration() {
        this(null,
                EnumSet.noneOf(Modifier.class),
                new NodeList<>(),
                new NodeList<>());
    }

    public FieldDeclaration(EnumSet<Modifier> modifiers, VariableDeclarator variable) {
        this(null,
                modifiers,
                new NodeList<>(),
                nodeList(variable));
    }

    public FieldDeclaration(EnumSet<Modifier> modifiers, NodeList<VariableDeclarator> variables) {
        this(null,
                modifiers,
                new NodeList<>(),
                variables);
    }

    public FieldDeclaration(EnumSet<Modifier> modifiers, NodeList<AnnotationExpr> annotations, 
                            NodeList<VariableDeclarator> variables) {
        this(null,
                modifiers,
                annotations,
                variables);
    }

    public FieldDeclaration(Range range, EnumSet<Modifier> modifiers, NodeList<AnnotationExpr> annotations, 
                            NodeList<VariableDeclarator> variables) {
        super(range, annotations);
        setModifiers(modifiers);
        setVariables(variables);
    }

    /**
     * Creates a {@link FieldDeclaration}.
     *
     * @param modifiers modifiers
     * @param type type
     * @param name field name
     */
    public FieldDeclaration(EnumSet<Modifier> modifiers, Type<?> type, String name) {
        this(assertNotNull(modifiers), new VariableDeclarator(type, assertNotNull(name)));
    }

    @Override
    public <R, A> R accept(GenericVisitor<R, A> v, A arg) {
        return v.visit(this, arg);
    }

    @Override
    public <A> void accept(VoidVisitor<A> v, A arg) {
        v.visit(this, arg);
    }

    /**
     * Return the modifiers of this member declaration.
     *
     * @return modifiers
     * @see Modifier
     */
    @Override
    public EnumSet<Modifier> getModifiers() {
        return modifiers;
    }

    @Override
    public NodeList<VariableDeclarator> getVariables() {
        return variables;
    }

    @Override
    public FieldDeclaration setModifiers(EnumSet<Modifier> modifiers) {
        notifyPropertyChange(ObservableProperty.MODIFIERS, this.modifiers, modifiers);
        this.modifiers = assertNotNull(modifiers);
        return this;
    }

    @Override
    public FieldDeclaration setVariables(NodeList<VariableDeclarator> variables) {
        notifyPropertyChange(ObservableProperty.VARIABLES, this.variables, variables);
        this.variables = assertNotNull(variables);
        setAsParentNodeOf(this.variables);
        return this;
    }

    @Override
    public JavadocComment getJavaDoc() {
        if (getComment() instanceof JavadocComment) {
            return (JavadocComment) getComment();
        }
        return null;
    }

    /**
     * Create a getter for this field, <b>will only work if this field declares only 1 identifier and if this field is
     * already added to a ClassOrInterfaceDeclaration</b>
     *
     * @return the {@link MethodDeclaration} created
     * @throws IllegalStateException if there is more than 1 variable identifier or if this field isn't attached to a
     * class or enum
     */
    public MethodDeclaration createGetter() {
        if (getVariables().size() != 1)
            throw new IllegalStateException("You can use this only when the field declares only 1 variable name");
        ClassOrInterfaceDeclaration parentClass = getAncestorOfType(ClassOrInterfaceDeclaration.class);
        EnumDeclaration parentEnum = getAncestorOfType(EnumDeclaration.class);
        if ((parentClass == null && parentEnum == null) || (parentClass != null && parentClass.isInterface()))
            throw new IllegalStateException(
                    "You can use this only when the field is attached to a class or an enum");

        VariableDeclarator variable = getVariable(0);
        String fieldName = variable.getNameAsString();
        String fieldNameUpper = fieldName.toUpperCase().substring(0, 1) + fieldName.substring(1, fieldName.length());
        final MethodDeclaration getter;
        if (parentClass != null)
            getter = parentClass.addMethod("get" + fieldNameUpper, PUBLIC);
        else
            getter = parentEnum.addMethod("get" + fieldNameUpper, PUBLIC);
        getter.setType(variable.getType());
        BlockStmt blockStmt = new BlockStmt();
        getter.setBody(blockStmt);
        blockStmt.addStatement(new ReturnStmt(fieldName));
        return getter;
    }

    /**
     * Create a setter for this field, <b>will only work if this field declares only 1 identifier and if this field is
     * already added to a ClassOrInterfaceDeclaration</b>
     *
     * @return the {@link MethodDeclaration} created
     * @throws IllegalStateException if there is more than 1 variable identifier or if this field isn't attached to a
     * class or enum
     */
    public MethodDeclaration createSetter() {
        if (getVariables().size() != 1)
            throw new IllegalStateException("You can use this only when the field declares only 1 variable name");
        ClassOrInterfaceDeclaration parentClass = getAncestorOfType(ClassOrInterfaceDeclaration.class);
        EnumDeclaration parentEnum = getAncestorOfType(EnumDeclaration.class);
        if ((parentClass == null && parentEnum == null) || (parentClass != null && parentClass.isInterface()))
            throw new IllegalStateException(
                    "You can use this only when the field is attached to a class or an enum");

        VariableDeclarator variable = getVariable(0);
        String fieldName = variable.getNameAsString();
        String fieldNameUpper = fieldName.toUpperCase().substring(0, 1) + fieldName.substring(1, fieldName.length());

        final MethodDeclaration setter;
        if (parentClass != null)
            setter = parentClass.addMethod("set" + fieldNameUpper, PUBLIC);
        else
            setter = parentEnum.addMethod("set" + fieldNameUpper, PUBLIC);
        setter.setType(VOID_TYPE);
        setter.getParameters().add(new Parameter(variable.getType(), fieldName));
        BlockStmt blockStmt2 = new BlockStmt();
        setter.setBody(blockStmt2);
        blockStmt2.addStatement(new AssignExpr(new NameExpr("this." + fieldName), new NameExpr(fieldName), Operator.ASSIGN));
        return setter;
    }

    @Override
    public List<NodeList<?>> getNodeLists() {
        List<NodeList<?>> res = new LinkedList<>(super.getNodeLists());
        res.add(variables);
        return res;
    }
}
