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

package me.tomassetti.symbolsolver.javaparsermodel.declarations;

import com.github.javaparser.ast.body.Parameter;
import me.tomassetti.symbolsolver.model.declarations.ParameterDeclaration;
import me.tomassetti.symbolsolver.model.resolution.TypeSolver;
import me.tomassetti.symbolsolver.model.usages.typesystem.ArrayType;
import me.tomassetti.symbolsolver.model.usages.typesystem.Type;
import me.tomassetti.symbolsolver.javaparsermodel.JavaParserFacade;

public class JavaParserParameterDeclaration implements ParameterDeclaration {

    private Parameter wrappedNode;
    private TypeSolver typeSolver;

    public JavaParserParameterDeclaration(Parameter wrappedNode, TypeSolver typeSolver) {
        this.wrappedNode = wrappedNode;
        this.typeSolver = typeSolver;
    }

    @Override
    public String getName() {
        return wrappedNode.getName();
    }

    @Override
    public boolean isField() {
        return false;
    }

    @Override
    public boolean isParameter() {
        return true;
    }

    @Override
    public boolean isVariadic() {
        return wrappedNode.isVarArgs();
    }

    @Override
    public boolean isType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Type getType() {
        Type res = JavaParserFacade.get(typeSolver).convert(wrappedNode.getType(), wrappedNode);
        if (isVariadic()) {
            res = new ArrayType(res);
        }
        return res;
    }

	/**
	 * Returns the JavaParser node associated with this JavaParserParameterDeclaration.
	 *
	 * @return A visitable JavaParser node wrapped by this object.
	 */
	public Parameter getWrappedNode()
	{
		return wrappedNode;
	}
}
