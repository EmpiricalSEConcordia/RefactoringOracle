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
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.nodeTypes.NodeWithJavaDoc;
import com.github.javaparser.ast.observing.ObservableProperty;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;

import static com.github.javaparser.utils.Utils.assertNotNull;

/**
 * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-8.6">JLS</a>
 * A (possibly static) initializer block. "static { a=3; }" in this example: <code>class X { static { a=3; }  } </code> 
 * @author Julio Vilmar Gesser
 */
public final class InitializerDeclaration extends BodyDeclaration<InitializerDeclaration>
        implements NodeWithJavaDoc<InitializerDeclaration> {

    private boolean isStatic;

    private BlockStmt block;

    public InitializerDeclaration() {
        this(null, false, new BlockStmt());
    }

    public InitializerDeclaration(boolean isStatic, BlockStmt block) {
        this(null, isStatic, block);
    }

    public InitializerDeclaration(Range range, boolean isStatic, BlockStmt block) {
        super(range, new NodeList<>());
        setStatic(isStatic);
        setBlock(block);
    }

    @Override
    public <R, A> R accept(GenericVisitor<R, A> v, A arg) {
        return v.visit(this, arg);
    }

    @Override
    public <A> void accept(VoidVisitor<A> v, A arg) {
        v.visit(this, arg);
    }

    public BlockStmt getBlock() {
        return block;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public InitializerDeclaration setBlock(BlockStmt block) {
        notifyPropertyChange(ObservableProperty.BLOCK, this.block, block);
        this.block = assertNotNull(block);
        setAsParentNodeOf(this.block);
        return this;
    }

    public InitializerDeclaration setStatic(boolean isStatic) {
        notifyPropertyChange(ObservableProperty.STATIC, this.isStatic, isStatic);
        this.isStatic = isStatic;
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
