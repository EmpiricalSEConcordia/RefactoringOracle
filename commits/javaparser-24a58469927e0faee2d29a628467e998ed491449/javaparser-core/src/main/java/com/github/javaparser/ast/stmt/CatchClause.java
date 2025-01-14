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

package com.github.javaparser.ast.stmt;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithBlockStmt;
import com.github.javaparser.ast.observing.ObservableProperty;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;

import java.util.EnumSet;

/**
 * @author Julio Vilmar Gesser
 */
public final class CatchClause extends Node implements NodeWithBlockStmt<CatchClause> {

    private Parameter parameter;

    private BlockStmt catchBlock;

    public CatchClause() {
        this(null, new Parameter(), new BlockStmt());
    }

    public CatchClause(final Parameter parameter, final BlockStmt catchBlock) {
        this(null, parameter, catchBlock);
    }

    public CatchClause(final EnumSet<Modifier> exceptModifier,
                       final NodeList<AnnotationExpr> exceptAnnotations,
                       final ClassOrInterfaceType exceptType,
                       final SimpleName exceptName,
                       final BlockStmt catchBlock) {
        this(null,
                new Parameter(null,
                        exceptModifier,
                        exceptAnnotations,
                        exceptType,
                        false,
                        exceptName),
                catchBlock);
    }

    public CatchClause(final Range range,
                       final Parameter parameter,
                       final BlockStmt catchBlock) {
        super(range);
        setParameter(parameter);
        setBody(catchBlock);
    }

    @Override
    public <R, A> R accept(final GenericVisitor<R, A> v, final A arg) {
        return v.visit(this, arg);
    }

    @Override
    public <A> void accept(final VoidVisitor<A> v, final A arg) {
        v.visit(this, arg);
    }

    /**
     * Use {@link #getBody()} instead
     */
    @Deprecated
    public BlockStmt getCatchBlock() {
        return catchBlock;
    }

    /**
     * Note that the type of the Parameter can be a UnionType. In this case, any annotations found at the start of the
     * catch(@X A a |...) are found directly in the Parameter. Annotations that are on the second or later type -
     * catch(A a | @X B b ...) are found on those types.
     */
    public Parameter getParameter() {
        return parameter;
    }

    /**
     * Use {@link #setBody(BlockStmt)} instead
     */
    @Deprecated
    public CatchClause setCatchBlock(final BlockStmt catchBlock) {
        return setBody(catchBlock);
    }

    public CatchClause setParameter(final Parameter parameter) {
        notifyPropertyChange(ObservableProperty.PARAMETER, this.parameter, parameter);
        this.parameter = parameter;
        setAsParentNodeOf(this.parameter);
        return this;
    }

    @Override
    public BlockStmt getBody() {
        return catchBlock;
    }

    @Override
    public CatchClause setBody(BlockStmt block) {
        notifyPropertyChange(ObservableProperty.CATCH_BLOCK, this.catchBlock, block);
        this.catchBlock = block;
        setAsParentNodeOf(this.catchBlock);
        return this;
    }
}
