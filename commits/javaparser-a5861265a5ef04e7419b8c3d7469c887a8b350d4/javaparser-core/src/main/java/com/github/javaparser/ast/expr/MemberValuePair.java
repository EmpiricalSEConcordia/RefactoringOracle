/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2015 The JavaParser Team.
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

import static com.github.javaparser.Position.pos;

import com.github.javaparser.Range;
import com.github.javaparser.ast.NamedNode;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;

/**
 * @author Julio Vilmar Gesser
 */
public final class MemberValuePair extends Node implements NamedNode<MemberValuePair> {

	private String name;

	private Expression value;

	public MemberValuePair() {
	}

	public MemberValuePair(final String name, final Expression value) {
		setName(name);
		setValue(value);
	}

	/**
	 * @deprecated prefer using Range objects.
	 */
	@Deprecated
	public MemberValuePair(final int beginLine, final int beginColumn, final int endLine, final int endColumn,
	                       final String name, final Expression value) {
		this(new Range(pos(beginLine, beginColumn), pos(endLine, endColumn)), name, value);
	}
	
	public MemberValuePair(final Range range, final String name, final Expression value) {
		super(range);
		setName(name);
		setValue(value);
	}

	@Override public <R, A> R accept(final GenericVisitor<R, A> v, final A arg) {
		return v.visit(this, arg);
	}

	@Override public <A> void accept(final VoidVisitor<A> v, final A arg) {
		v.visit(this, arg);
	}

	@Override
	public String getName() {
		return name;
	}

	public Expression getValue() {
		return value;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public void setValue(final Expression value) {
		this.value = value;
		setAsParentNodeOf(this.value);
	}
}
