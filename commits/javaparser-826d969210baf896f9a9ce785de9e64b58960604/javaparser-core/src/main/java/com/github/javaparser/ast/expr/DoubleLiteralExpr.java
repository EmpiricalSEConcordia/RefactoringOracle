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
package com.github.javaparser.ast.expr;

import com.github.javaparser.Range;
import com.github.javaparser.ast.AllFieldsConstructor;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.CloneVisitor;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.metamodel.DoubleLiteralExprMetaModel;
import com.github.javaparser.metamodel.JavaParserMetaModel;
import javax.annotation.Generated;

/**
 * A float or a double constant. This value is stored exactly as found in the source.
 * <br/><code>100.1f</code>
 * <br/><code>23958D</code>
 * <br/><code>0x4.5p1f</code>
 *
 * @author Julio Vilmar Gesser
 */
public final class DoubleLiteralExpr extends LiteralStringValueExpr {

    public DoubleLiteralExpr() {
        this(null, "0");
    }

    @AllFieldsConstructor
    public DoubleLiteralExpr(final String value) {
        this(null, value);
    }

    /**This constructor is used by the parser and is considered private.*/
    @Generated("com.github.javaparser.generator.core.node.MainConstructorGenerator")
    public DoubleLiteralExpr(Range range, String value) {
        super(range, value);
        customInitialization();
    }

    public DoubleLiteralExpr(final double value) {
        this(null, String.valueOf(value));
    }

    @Override
    public <R, A> R accept(final GenericVisitor<R, A> v, final A arg) {
        return v.visit(this, arg);
    }

    @Override
    public <A> void accept(final VoidVisitor<A> v, final A arg) {
        v.visit(this, arg);
    }

    @Override
    public boolean remove(Node node) {
        if (node == null)
            return false;
        return super.remove(node);
    }

    /**
     * @return the literal value as a double
     */
    public double asDouble() {
        return Double.parseDouble(value);
    }

    public DoubleLiteralExpr setDouble(double value) {
        this.value = String.valueOf(value);
        return this;
    }

    @Override
    @Generated("com.github.javaparser.generator.core.node.CloneGenerator")
    public DoubleLiteralExpr clone() {
        return (DoubleLiteralExpr) accept(new CloneVisitor(), null);
    }

    @Override
    @Generated("com.github.javaparser.generator.core.node.GetMetaModelGenerator")
    public DoubleLiteralExprMetaModel getMetaModel() {
        return JavaParserMetaModel.doubleLiteralExprMetaModel;
    }
}
