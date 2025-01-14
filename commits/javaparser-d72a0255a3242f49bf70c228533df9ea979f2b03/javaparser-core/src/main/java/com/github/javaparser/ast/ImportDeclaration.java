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
package com.github.javaparser.ast;

import com.github.javaparser.Range;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.observer.ObservableProperty;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;
import static com.github.javaparser.utils.Utils.assertNotNull;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.CloneVisitor;
import com.github.javaparser.metamodel.ImportDeclarationMetaModel;
import com.github.javaparser.metamodel.JavaParserMetaModel;
import javax.annotation.Generated;

/**
 * An import declaration.
 * <br/><code>import com.github.javaparser.JavaParser;</code>
 * <br/><code>import com.github.javaparser.*;</code>
 * <br/><code>import com.github.javaparser.JavaParser.*; </code>
 * <br/><code>import static com.github.javaparser.JavaParser.*;</code> 
 * <br/><code>import static com.github.javaparser.JavaParser.parse;</code> 
 * 
 * <p>The name does not include the asterisk or the static keyword.</p>
 * @author Julio Vilmar Gesser
 */
public final class ImportDeclaration extends Node implements NodeWithName<ImportDeclaration> {

    private Name name;

    private boolean isStatic;

    private boolean isAsterisk;

    private ImportDeclaration() {
        this(null, new Name(), false, false);
    }

    @AllFieldsConstructor
    public ImportDeclaration(Name name, boolean isStatic, boolean isAsterisk) {
        this(null, name, isStatic, isAsterisk);
    }

    /**This constructor is used by the parser and is considered private.*/
    @Generated("com.github.javaparser.generator.core.node.MainConstructorGenerator")
    public ImportDeclaration(Range range, Name name, boolean isStatic, boolean isAsterisk) {
        super(range);
        setName(name);
        setStatic(isStatic);
        setAsterisk(isAsterisk);
        customInitialization();
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
     * Retrieves the name of the import (.* is not included.)
     */
    public Name getName() {
        return name;
    }

    /**
     * Return if the import ends with "*".
     */
    public boolean isAsterisk() {
        return isAsterisk;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public ImportDeclaration setAsterisk(final boolean isAsterisk) {
        if (isAsterisk == this.isAsterisk) {
            return (ImportDeclaration) this;
        }
        notifyPropertyChange(ObservableProperty.ASTERISK, this.isAsterisk, isAsterisk);
        this.isAsterisk = isAsterisk;
        return this;
    }

    public ImportDeclaration setName(final Name name) {
        assertNotNull(name);
        if (name == this.name) {
            return (ImportDeclaration) this;
        }
        notifyPropertyChange(ObservableProperty.NAME, this.name, name);
        if (this.name != null)
            this.name.setParentNode(null);
        this.name = name;
        setAsParentNodeOf(name);
        return this;
    }

    public ImportDeclaration setStatic(final boolean isStatic) {
        if (isStatic == this.isStatic) {
            return (ImportDeclaration) this;
        }
        notifyPropertyChange(ObservableProperty.STATIC, this.isStatic, isStatic);
        this.isStatic = isStatic;
        return this;
    }

    @Override
    public boolean remove(Node node) {
        if (node == null)
            return false;
        return super.remove(node);
    }

    @Override
    public ImportDeclaration clone() {
        return (ImportDeclaration) accept(new CloneVisitor(), null);
    }

    @Override
    public ImportDeclarationMetaModel getMetaModel() {
        return JavaParserMetaModel.importDeclarationMetaModel;
    }
}
