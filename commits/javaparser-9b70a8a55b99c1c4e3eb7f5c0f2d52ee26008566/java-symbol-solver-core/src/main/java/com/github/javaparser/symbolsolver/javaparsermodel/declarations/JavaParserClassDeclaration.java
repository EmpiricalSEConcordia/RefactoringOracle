/*
 * Copyright 2016 Federico Tomassetti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.javaparser.symbolsolver.javaparsermodel.declarations;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.symbolsolver.core.resolution.Context;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFactory;
import com.github.javaparser.symbolsolver.javaparsermodel.UnsolvedSymbolException;
import com.github.javaparser.symbolsolver.logic.AbstractClassDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.*;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.model.typesystem.ReferenceType;
import com.github.javaparser.symbolsolver.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.symbolsolver.model.typesystem.Type;
import com.github.javaparser.symbolsolver.resolution.SymbolSolver;
import com.google.common.collect.ImmutableList;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Federico Tomassetti
 */
public class JavaParserClassDeclaration extends AbstractClassDeclaration {

    ///
    /// Fields
    ///

    private TypeSolver typeSolver;
    private com.github.javaparser.ast.body.ClassOrInterfaceDeclaration wrappedNode;
    private JavaParserTypeAdapter javaParserTypeAdapter;

    ///
    /// Constructors
    ///

    public JavaParserClassDeclaration(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration wrappedNode,
                                      TypeSolver typeSolver) {
        if (wrappedNode.isInterface()) {
            throw new IllegalArgumentException("Interface given");
        }
        this.wrappedNode = wrappedNode;
        this.typeSolver = typeSolver;
        this.javaParserTypeAdapter = new JavaParserTypeAdapter(wrappedNode, typeSolver);
    }

    ///
    /// Public methods: from Object
    ///

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JavaParserClassDeclaration that = (JavaParserClassDeclaration) o;

        if (!wrappedNode.equals(that.wrappedNode)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return wrappedNode.hashCode();
    }

    @Override
    public String toString() {
        return "JavaParserClassDeclaration{" +
                "wrappedNode=" + wrappedNode +
                '}';
    }

    ///
    /// Public methods: fields
    ///

    @Override
    public List<FieldDeclaration> getAllFields() {
        ArrayList<FieldDeclaration> fields = new ArrayList<>();
        for (BodyDeclaration member : wrappedNode.getMembers()) {
            if (member instanceof com.github.javaparser.ast.body.FieldDeclaration) {
                com.github.javaparser.ast.body.FieldDeclaration field = (com.github.javaparser.ast.body.FieldDeclaration) member;
                for (VariableDeclarator vd : field.getVariables()) {
                    fields.add(new JavaParserFieldDeclaration(vd, typeSolver));
                }
            }
        }

        ClassDeclaration superclass = (ClassDeclaration) this.getSuperClass().getTypeDeclaration();
        fields.addAll(superclass.getAllFields());

        getInterfaces().forEach(interf -> interf.getTypeDeclaration().getAllFields().forEach(f -> {
            fields.add(f);
        }));

        return fields;
    }

    ///
    /// Public methods
    ///

    public SymbolReference<MethodDeclaration> solveMethod(String name, List<Type> parameterTypes) {
        Context ctx = getContext();
        return ctx.solveMethod(name, parameterTypes, typeSolver);
    }

    @Deprecated
    public Context getContext() {
        return JavaParserFactory.getContext(wrappedNode, typeSolver);
    }

    public Type getUsage(Node node) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return wrappedNode.getName().getId();
    }

    @Override
    public ReferenceType getSuperClass() {
        if (wrappedNode.getExtends().isEmpty()) {
            return object();
        } else {
            return toReferenceType(wrappedNode.getExtends().get(0));
        }
    }

    @Override
    public List<ReferenceType> getInterfaces() {
        List<ReferenceType> interfaces = new ArrayList<>();
        if (wrappedNode.getImplements() != null) {
            for (ClassOrInterfaceType t : wrappedNode.getImplements()) {
                interfaces.add(toReferenceType(t));
            }
        }
        return interfaces;
    }

    @Override
    public List<ConstructorDeclaration> getConstructors() {
        List<ConstructorDeclaration> declared = new LinkedList<>();
        for (BodyDeclaration member : wrappedNode.getMembers()) {
            if (member instanceof com.github.javaparser.ast.body.ConstructorDeclaration) {
                com.github.javaparser.ast.body.ConstructorDeclaration constructorDeclaration = (com.github.javaparser.ast.body.ConstructorDeclaration) member;
                declared.add(new JavaParserConstructorDeclaration(this, constructorDeclaration, typeSolver));
            }
        }
        if (declared.isEmpty()) {
            // If there are no constructors insert the default constructor
            return ImmutableList.of(new DefaultConstructorDeclaration(this));
        } else {
            return declared;
        }
    }

    @Override
    public boolean hasDirectlyAnnotation(String canonicalName) {
        for (AnnotationExpr annotationExpr : wrappedNode.getAnnotations()) {
            if (solveType(annotationExpr.getName().getId(), typeSolver).getCorrespondingDeclaration().getQualifiedName().equals(canonicalName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isInterface() {
        return wrappedNode.isInterface();
    }

    @Override
    public String getQualifiedName() {
       return javaParserTypeAdapter.getQualifiedName();
    }

    @Override
    public boolean isAssignableBy(ReferenceTypeDeclaration other) {
        return javaParserTypeAdapter.isAssignableBy(other);
    }

    @Override
    public boolean isAssignableBy(Type type) {
        return javaParserTypeAdapter.isAssignableBy(type);
    }

    @Override
    public boolean canBeAssignedTo(ReferenceTypeDeclaration other) {
        // TODO consider generic types
        if (this.getQualifiedName().equals(other.getQualifiedName())) {
            return true;
        }
        ClassDeclaration superclass = (ClassDeclaration) getSuperClass().getTypeDeclaration();
        if (superclass != null && superclass.canBeAssignedTo(other)) {
            return true;
        }

        if (this.wrappedNode.getImplements() != null) {
            for (ClassOrInterfaceType type : wrappedNode.getImplements()) {
                ReferenceTypeDeclaration ancestor = (ReferenceTypeDeclaration) new SymbolSolver(typeSolver).solveType(type);
                if (ancestor.canBeAssignedTo(other)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean isTypeParameter() {
        return false;
    }

    @Deprecated
    public SymbolReference<TypeDeclaration> solveType(String name, TypeSolver typeSolver) {
        if (this.wrappedNode.getName().getId().equals(name)) {
            return SymbolReference.solved(this);
        }
        SymbolReference<TypeDeclaration> ref = javaParserTypeAdapter.solveType(name, typeSolver);
        if (ref.isSolved()) {
            return ref;
        }

        String prefix = wrappedNode.getName() + ".";
        if (name.startsWith(prefix) && name.length() > prefix.length()) {
            return new JavaParserClassDeclaration(this.wrappedNode, typeSolver).solveType(name.substring(prefix.length()), typeSolver);
        }

        return getContext().getParent().solveType(name, typeSolver);
    }

    @Override
    public List<ReferenceType> getAncestors() {
        List<ReferenceType> ancestors = new ArrayList<>();
        ReferenceType superclass = getSuperClass();
        if (superclass != null) {
            ancestors.add(superclass);
        }
        if (wrappedNode.getImplements() != null) {
            for (ClassOrInterfaceType implemented : wrappedNode.getImplements()) {
                ReferenceType ancestor = toReferenceType(implemented);
                ancestors.add(ancestor);
            }
        }
        return ancestors;
    }

    @Override
    public Set<MethodDeclaration> getDeclaredMethods() {
        Set<MethodDeclaration> methods = new HashSet<>();
        for (BodyDeclaration member : wrappedNode.getMembers()) {
            if (member instanceof com.github.javaparser.ast.body.MethodDeclaration) {
                methods.add(new JavaParserMethodDeclaration((com.github.javaparser.ast.body.MethodDeclaration) member, typeSolver));
            }
        }
        return methods;
    }

    @Override
    public List<TypeParameterDeclaration> getTypeParameters() {
        return this.wrappedNode.getTypeParameters().stream().map(
                (tp) -> new JavaParserTypeParameter(tp, typeSolver)
        ).collect(Collectors.toList());
    }

    /**
     * Returns the JavaParser node associated with this JavaParserClassDeclaration.
     *
     * @return A visitable JavaParser node wrapped by this object.
     */
    public com.github.javaparser.ast.body.ClassOrInterfaceDeclaration getWrappedNode() {
        return wrappedNode;
    }

    @Override
    public AccessLevel accessLevel() {
        throw new UnsupportedOperationException();
    }

    ///
    /// Protected methods
    ///

    @Override
    protected ReferenceType object() {
        return new ReferenceTypeImpl(typeSolver.solveType(Object.class.getCanonicalName()), typeSolver);
    }

    ///
    /// Private methods
    ///

    private ReferenceType toReferenceType(ClassOrInterfaceType classOrInterfaceType) {
        SymbolReference<TypeDeclaration> ref = solveType(classOrInterfaceType.getName().getId(), typeSolver);
        if (!ref.isSolved()) {
            throw new UnsolvedSymbolException(classOrInterfaceType.getName().getId());
        }
        if (!classOrInterfaceType.getTypeArguments().isPresent()) {
            return new ReferenceTypeImpl(ref.getCorrespondingDeclaration().asReferenceType(), typeSolver);
        }
        List<Type> superClassTypeParameters = classOrInterfaceType.getTypeArguments().get()
                .stream().map(ta -> JavaParserFacade.get(typeSolver).convert(ta, ta))
                .collect(Collectors.toList());
        return new ReferenceTypeImpl(ref.getCorrespondingDeclaration().asReferenceType(), superClassTypeParameters, typeSolver);
    }
}
