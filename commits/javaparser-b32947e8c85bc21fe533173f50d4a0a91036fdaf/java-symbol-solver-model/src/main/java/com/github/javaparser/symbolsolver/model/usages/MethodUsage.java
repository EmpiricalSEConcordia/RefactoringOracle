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

package com.github.javaparser.symbolsolver.model.usages;

import com.github.javaparser.symbolsolver.model.declarations.MethodDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.TypeDeclaration;
import com.github.javaparser.symbolsolver.model.usages.typesystem.Type;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * This is basically a MethodDeclaration with some TypeParameters defined.
 * The defined TypeParameters can comes from the Method itself or from the surrounding types.
 *
 * @author Federico Tomassetti
 */
public class MethodUsage implements TypeParametrized {
    private MethodDeclaration declaration;
    private List<Type> paramTypes = new ArrayList<>();
    private Type returnType;
    private TypeParametersMap typeParametersMap;

    public MethodUsage(MethodDeclaration declaration) {
        this.typeParametersMap = new TypeParametersMap();
        this.declaration = declaration;
        for (int i = 0; i < declaration.getNumberOfParams(); i++) {
            paramTypes.add(declaration.getParam(i).getType());
        }
        returnType = declaration.getReturnType();
    }

    public MethodUsage(MethodDeclaration declaration, List<Type> paramTypes, Type returnType) {
        this.typeParametersMap = new TypeParametersMap();
        this.declaration = declaration;
        this.paramTypes = paramTypes;
        this.returnType = returnType;
    }

    private static Type replaceTypeParameterByName(String name, Type newValue, Type typeToBeExamined) {
        return typeToBeExamined.replaceParam(name, newValue);
    }

    @Override
    public String toString() {
        return "MethodUsage{" +
                "declaration=" + declaration +
                ", paramTypes=" + paramTypes +
                '}';
    }

    public MethodDeclaration getDeclaration() {
        return declaration;
    }

    public String getName() {
        return declaration.getName();
    }

    public TypeDeclaration declaringType() {
        return declaration.declaringType();
    }

    public Type returnType() {
        return returnType;
    }

    public List<Type> getParamTypes() {
        return paramTypes;
    }

    public MethodUsage replaceParamType(int i, Type replaced) {
        if (paramTypes.get(i) == replaced) {
            return this;
        }
        List<Type> newParams = new LinkedList<>(paramTypes);
        newParams.set(i, replaced);
        return new MethodUsage(declaration, newParams, returnType);
    }

    public MethodUsage replaceReturnType(Type returnType) {
        if (returnType == this.returnType) {
            return this;
        } else {
            return new MethodUsage(declaration, paramTypes, returnType);
        }
    }

    /**
     * Return the number of formal arguments accepted by this method.
     */
    public int getNoParams() {
        return paramTypes.size();
    }

    /**
     * Return the type of the formal argument at the given position.
     */
    public Type getParamType(int i) {
        return paramTypes.get(i);
    }

    @Deprecated
    public MethodUsage replaceTypeParameterByName(String name, Type type) {
        if (type == null) {
            throw new IllegalArgumentException();
        }
        // TODO if the method declaration has a type param with that name ignore this call
        MethodUsage res = this;
        for (int i = 0; i < paramTypes.size(); i++) {
            Type originalParamType = paramTypes.get(i);
            Type newParamType = originalParamType.replaceParam(name, type);
            res = res.replaceParamType(i, newParamType);
        }
        res = res.replaceReturnType(replaceTypeParameterByName(name, type, res.returnType));
        return res;
    }

    @Override
    public TypeParametersMap typeParametersMap() {
        return typeParametersMap;
    }

    public String getQualifiedSignature() {
        // TODO use the type parameters
        return this.getDeclaration().getQualifiedSignature();
    }
}
