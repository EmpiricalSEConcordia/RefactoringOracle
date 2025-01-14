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

package com.github.javaparser.symbolsolver.resolution;

import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.javaparser.Navigator;
import com.github.javaparser.symbolsolver.model.declarations.ValueDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JreTypeSolver;
import com.github.javaparser.symbolsolver.model.usages.typesystem.Type;
import org.junit.Test;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnumResolutionTest extends AbstractResolutionTest {

    @Test
    public void switchOnEnum() throws ParseException {
        CompilationUnit cu = parseSample("SwitchOnEnum");
        com.github.javaparser.ast.body.ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "SwitchOnEnum");
        MethodDeclaration method = Navigator.demandMethod(clazz, "foo");
        SwitchStmt switchStmt = Navigator.findSwitch(method);
        Expression expression = switchStmt.getEntries().get(0).getLabel().get();

        SymbolReference<? extends ValueDeclaration> ref = JavaParserFacade.get(new JreTypeSolver()).solve(expression);
        assertTrue(ref.isSolved());
        assertEquals("SwitchOnEnum.MyEnum", ref.getCorrespondingDeclaration().getType().asReferenceType().getQualifiedName());
    }

    @Test
    public void enumAndStaticInitializer() throws ParseException {
        CompilationUnit cu = parseSample("EnumAndStaticInitializer");
        com.github.javaparser.ast.body.ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "MyClass");
        MethodCallExpr call = Navigator.findMethodCall(clazz, "put");

        Type ref = JavaParserFacade.get(new JreTypeSolver()).getType(call);
        assertEquals("MyClass.Primitive", ref.describe());
    }

}
