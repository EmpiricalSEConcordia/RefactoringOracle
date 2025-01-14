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

package com.github.javaparser.ast.nodeTypes;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

public interface NodeWithStatements<N extends Node> {
    NodeList<Statement> getStatements();

    default Statement getStatement(int i) {
        return getStatements().get(i);
    }

    N setStatements(final NodeList<Statement> statements);

    @SuppressWarnings("unchecked")
    default N addStatement(Statement statement) {
        getStatements().add(statement);
        statement.setParentNode((Node) this);
        return (N) this;
    }

    @SuppressWarnings("unchecked")
    default N addStatement(int index, final Statement statement) {
        getStatements().add(index, statement);
        statement.setParentNode((Node) this);
        return (N) this;
    }

    default N addStatement(Expression expr) {
        ExpressionStmt statement = new ExpressionStmt(expr);
        expr.setParentNode(statement);
        return addStatement(statement);
    }

    /**
     * It will use {@link JavaParser#parseStatement(String)} inside, so it should end with a semi column
     */
    default N addStatement(String statement) {
        return addStatement(JavaParser.parseStatement(statement));
    }

    default N addStatement(int index, final Expression expr) {
        Statement stmt = new ExpressionStmt(expr);
        expr.setParentNode(stmt);
        return addStatement(index, stmt);
    }

    default <A extends Statement> A addAndGetStatement(A statement) {
        getStatements().add(statement);
        statement.setParentNode((Node) this);
        return statement;
    }

    default Statement addAndGetStatement(int index, final Statement statement) {
        getStatements().add(index, statement);
        statement.setParentNode((Node) this);
        return statement;
    }

    default ExpressionStmt addAndGetStatement(Expression expr) {
        ExpressionStmt statement = new ExpressionStmt(expr);
        expr.setParentNode(statement);
        return addAndGetStatement(statement);
    }

    default ExpressionStmt addAndGetStatement(String statement) {
        return addAndGetStatement(new NameExpr(statement));
    }

    default boolean isEmpty() {
        return getStatements().isEmpty();
    }

    @SuppressWarnings("unchecked")
    default N copyStatements(NodeList<Statement> nodeList) {
        for (Statement n : nodeList) {
            addStatement((Statement) n.clone());
        }
        return (N) this;
    }

    default N copyStatements(NodeWithStatements<?> other) {
        return copyStatements(other.getStatements());
    }
}
