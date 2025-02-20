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
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithJavaDoc;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.observer.ObservableProperty;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;

import java.util.EnumSet;
import java.util.Optional;

import static com.github.javaparser.utils.Utils.assertNotNull;

/**
 * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-AnnotationTypeElementDeclaration">JLS</a> 
 * The "int id();" in <code>@interface X { int id(); }</code>
 * @author Julio Vilmar Gesser
 */
public final class AnnotationMemberDeclaration extends BodyDeclaration<AnnotationMemberDeclaration> implements
        NodeWithJavaDoc<AnnotationMemberDeclaration>,
        NodeWithSimpleName<AnnotationMemberDeclaration>,
        NodeWithType<AnnotationMemberDeclaration, Type<?>>,
        NodeWithModifiers<AnnotationMemberDeclaration> {

    private EnumSet<Modifier> modifiers;

    private Type<?> type;

    private SimpleName name;

    private Expression defaultValue;

    public AnnotationMemberDeclaration() {
        this(null,
                EnumSet.noneOf(Modifier.class),
                new NodeList<>(),
                new ClassOrInterfaceType(),
                new SimpleName(),
                null);
    }

    public AnnotationMemberDeclaration(EnumSet<Modifier> modifiers, Type<?> type, String name, Expression defaultValue) {
        this(null,
                modifiers,
                new NodeList<>(),
                type,
                new SimpleName(name),
                defaultValue);
    }

    public AnnotationMemberDeclaration(EnumSet<Modifier> modifiers, NodeList<AnnotationExpr> annotations, Type<?> type, SimpleName name,
                                       Expression defaultValue) {
        this(null,
                modifiers,
                annotations,
                type,
                name,
                defaultValue);
    }

    public AnnotationMemberDeclaration(Range range, EnumSet<Modifier> modifiers, NodeList<AnnotationExpr> annotations, Type type,
                                       SimpleName name, Expression defaultValue) {
        super(range, annotations);
        setModifiers(modifiers);
        setType(type);
        setName(name);
        setDefaultValue(defaultValue);
    }

    @Override
    public <R, A> R accept(GenericVisitor<R, A> v, A arg) {
        return v.visit(this, arg);
    }

    @Override
    public <A> void accept(VoidVisitor<A> v, A arg) {
        v.visit(this, arg);
    }

    public Optional<Expression> getDefaultValue() {
        return Optional.ofNullable(defaultValue);
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
    public SimpleName getName() {
        return name;
    }

    @Override
    public Type<?> getType() {
        return type;
    }

    /**
     * Sets the default value
     *
     * @param defaultValue the default value, can be null
     * @return this, the AnnotationMemberDeclaration
     */
    public AnnotationMemberDeclaration setDefaultValue(Expression defaultValue) {
        notifyPropertyChange(ObservableProperty.DEFAULT_VALUE, this.defaultValue, defaultValue);
        this.defaultValue = defaultValue;
        setAsParentNodeOf(defaultValue);
        return this;
    }

    @Override
    public AnnotationMemberDeclaration setModifiers(EnumSet<Modifier> modifiers) {
        notifyPropertyChange(ObservableProperty.MODIFIERS, this.modifiers, modifiers);
        this.modifiers = assertNotNull(modifiers);
        return this;
    }

    @Override
    public AnnotationMemberDeclaration setName(SimpleName name) {
        notifyPropertyChange(ObservableProperty.NAME, this.name, name);
        this.name = assertNotNull(name);
        return this;
    }

    @Override
    public AnnotationMemberDeclaration setType(Type<?> type) {
        notifyPropertyChange(ObservableProperty.TYPE, this.type, type);
        this.type = assertNotNull(type);
        setAsParentNodeOf(type);
        return this;
    }

    @Override
    public JavadocComment getJavaDoc() {
        if (getComment() instanceof JavadocComment) {
            return (JavadocComment) getComment();
        }
        return null;
    }
}
