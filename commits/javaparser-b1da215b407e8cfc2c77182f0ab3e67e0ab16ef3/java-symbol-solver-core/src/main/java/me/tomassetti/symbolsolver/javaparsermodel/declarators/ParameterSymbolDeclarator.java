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

package me.tomassetti.symbolsolver.javaparsermodel.declarators;

import com.github.javaparser.ast.body.Parameter;
import me.tomassetti.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import me.tomassetti.symbolsolver.model.declarations.ValueDeclaration;
import me.tomassetti.symbolsolver.model.resolution.TypeSolver;

import java.util.LinkedList;
import java.util.List;

public class ParameterSymbolDeclarator extends AbstractSymbolDeclarator<Parameter> {


    public ParameterSymbolDeclarator(Parameter wrappedNode, TypeSolver typeSolver) {
        super(wrappedNode, typeSolver);
    }

    @Override
    public List<ValueDeclaration> getSymbolDeclarations() {
        List<ValueDeclaration> symbols = new LinkedList<>();
        symbols.add(JavaParserSymbolDeclaration.parameter(wrappedNode, typeSolver));
        return symbols;
    }
}
