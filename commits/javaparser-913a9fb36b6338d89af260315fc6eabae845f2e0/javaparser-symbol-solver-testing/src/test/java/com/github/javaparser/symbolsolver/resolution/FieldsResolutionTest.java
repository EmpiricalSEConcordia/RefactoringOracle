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

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparser.Navigator;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FieldsResolutionTest extends AbstractResolutionTest {

    @Test
    public void accessClassFieldThroughThis() {
        CompilationUnit cu = parseSample("AccessClassMemberThroughThis");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "AccessClassMemberThroughThis");
        MethodDeclaration method = Navigator.demandMethod(clazz, "getLabel2");
        ReturnStmt returnStmt = (ReturnStmt) method.getBody().get().getStatements().get(0);
        Expression expression = returnStmt.getExpression().get();

        ResolvedType ref = JavaParserFacade.get(new ReflectionTypeSolver()).getType(expression);
        assertEquals("java.lang.String", ref.describe());
    }

    @Test
    public void accessClassFieldThroughThisWithCompetingSymbolInParentContext() {
        CompilationUnit cu = parseSample("AccessClassMemberThroughThis");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "AccessClassMemberThroughThis");
        MethodDeclaration method = Navigator.demandMethod(clazz, "setLabel");
        ExpressionStmt expressionStmt = (ExpressionStmt) method.getBody().get().getStatements().get(0);
        AssignExpr assignExpr = (AssignExpr) expressionStmt.getExpression();
        FieldAccessExpr fieldAccessExpr = (FieldAccessExpr) assignExpr.getTarget();

        Path src = adaptPath("src/test/resources");
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(new JavaParserTypeSolver(src), new ReflectionTypeSolver());
        SymbolSolver symbolSolver = new SymbolSolver(typeSolver);
        SymbolReference<? extends ResolvedValueDeclaration> ref = symbolSolver.solveSymbol(fieldAccessExpr.getName().getId(), fieldAccessExpr);

        assertTrue(ref.isSolved());
        assertTrue(ref.getCorrespondingDeclaration().isField());
    }

    @Test
    public void accessEnumFieldThroughThis() {
        CompilationUnit cu = parseSample("AccessEnumMemberThroughThis");
        EnumDeclaration enumDecl = Navigator.demandEnum(cu, "AccessEnumMemberThroughThis");
        MethodDeclaration method = Navigator.demandMethod(enumDecl, "getLabel");
        SimpleName expression = Navigator.findSimpleName(method, "label").get();

        SymbolReference ref = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
        assertTrue(ref.isSolved());
        assertEquals("label", ref.getCorrespondingDeclaration().getName());
    }

    @Test
    public void accessEnumMethodThroughThis() {
        CompilationUnit cu = parseSample("AccessEnumMemberThroughThis");
        EnumDeclaration enumDecl = Navigator.demandEnum(cu, "AccessEnumMemberThroughThis");
        MethodDeclaration method = Navigator.demandMethod(enumDecl, "getLabel2");
        ReturnStmt returnStmt = (ReturnStmt) method.getBody().get().getStatements().get(0);
        Expression expression = returnStmt.getExpression().get();

        ResolvedType ref = JavaParserFacade.get(new ReflectionTypeSolver()).getType(expression);
        assertEquals("java.lang.String", ref.describe());
    }

    @Test
    public void accessClassFieldThroughSuper() {
        CompilationUnit cu = parseSample("AccessThroughSuper");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "AccessThroughSuper.SubClass");
        MethodDeclaration method = Navigator.demandMethod(clazz, "fieldTest");
        ReturnStmt returnStmt = (ReturnStmt) method.getBody().get().getStatements().get(0);
        Expression expression = returnStmt.getExpression().get();

        ResolvedType ref = JavaParserFacade.get(new ReflectionTypeSolver()).getType(expression);
        assertEquals("java.lang.String", ref.describe());
    }

    @Test
    public void resolveClassFieldThroughThis() {
        // configure symbol solver before parsing
        JavaParser.getStaticConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));

        // parse compilation unit and get field access expression
        CompilationUnit cu = parseSample("AccessClassMemberThroughThis");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "AccessClassMemberThroughThis");
        MethodDeclaration method = Navigator.demandMethod(clazz, "getLabel2");
        ReturnStmt returnStmt = (ReturnStmt) method.getBody().get().getStatements().get(0);
        FieldAccessExpr expression = returnStmt.getExpression().get().asFieldAccessExpr();

        // resolve field access expression
        ResolvedValueDeclaration resolvedValueDeclaration = expression.resolve();

        // get expected field declaration
        VariableDeclarator variableDeclarator = Navigator.demandField(clazz, "label");

        // check that the expected field declaration equals the resolved field declaration
        assertEquals(variableDeclarator, ((JavaParserFieldDeclaration) resolvedValueDeclaration).getVariableDeclarator());
    }

    @Test
    public void resolveClassFieldThroughSuper() {
        // configure symbol solver before parsing
        JavaParser.getStaticConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));

        // parse compilation unit and get field access expression
        CompilationUnit cu = parseSample("AccessThroughSuper");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "AccessThroughSuper.SubClass");
        MethodDeclaration method = Navigator.demandMethod(clazz, "fieldTest");
        ReturnStmt returnStmt = (ReturnStmt) method.getBody().get().getStatements().get(0);
        FieldAccessExpr expression = returnStmt.getExpression().get().asFieldAccessExpr();

        // resolve field access expression
        ResolvedValueDeclaration resolvedValueDeclaration = expression.resolve();

        // get expected field declaration
        clazz = Navigator.demandClass(cu, "AccessThroughSuper.SuperClass");
        VariableDeclarator variableDeclarator = Navigator.demandField(clazz, "field");

        // check that the expected field declaration equals the resolved field declaration
        assertEquals(variableDeclarator, ((JavaParserFieldDeclaration) resolvedValueDeclaration).getVariableDeclarator());
    }

    @Test
    public void resolveClassFieldOfClassExtendingUnknownClass1() {
        // configure symbol solver before parsing
        JavaParser.getStaticConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));

        // parse compilation unit and get field access expression
        CompilationUnit cu = parseSample("ClassExtendingUnknownClass");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "ClassExtendingUnknownClass");
        MethodDeclaration method = Navigator.demandMethod(clazz, "getFoo");
        ReturnStmt returnStmt = (ReturnStmt) method.getBody().get().getStatements().get(0);
        NameExpr expression = returnStmt.getExpression().get().asNameExpr();

        // resolve field access expression
        ResolvedValueDeclaration resolvedValueDeclaration = expression.resolve();

        // get expected field declaration
        VariableDeclarator variableDeclarator = Navigator.demandField(clazz, "foo");

        // check that the expected field declaration equals the resolved field declaration
        assertEquals(variableDeclarator, ((JavaParserFieldDeclaration) resolvedValueDeclaration).getVariableDeclarator());
    }

    @Test
    public void resolveClassFieldOfClassExtendingUnknownClass2() {
        // configure symbol solver before parsing
        JavaParser.getStaticConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));

        // parse compilation unit and get field access expression
        CompilationUnit cu = parseSample("ClassExtendingUnknownClass");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "ClassExtendingUnknownClass");
        MethodDeclaration method = Navigator.demandMethod(clazz, "getFoo2");
        ReturnStmt returnStmt = (ReturnStmt) method.getBody().get().getStatements().get(0);
        FieldAccessExpr expression = returnStmt.getExpression().get().asFieldAccessExpr();

        // resolve field access expression
        ResolvedValueDeclaration resolvedValueDeclaration = expression.resolve();

        // get expected field declaration
        VariableDeclarator variableDeclarator = Navigator.demandField(clazz, "foo");

        // check that the expected field declaration equals the resolved field declaration
        assertEquals(variableDeclarator, ((JavaParserFieldDeclaration) resolvedValueDeclaration).getVariableDeclarator());
    }

    @Test
    public void resolveEnumFieldAccess() {
        // configure symbol solver before parsing
        JavaParser.getStaticConfiguration().setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver()));

        // parse compilation unit and get field access expression
        CompilationUnit cu = parseSample("EnumFieldAccess");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "EnumFieldAccess");
        MethodDeclaration method = Navigator.demandMethod(clazz, "accessField");
        ReturnStmt returnStmt = (ReturnStmt) method.getBody().get().getStatements().get(0);
        FieldAccessExpr expression = returnStmt.getExpression().get().asFieldAccessExpr();

        // resolve field access expression
        ResolvedValueDeclaration resolvedValueDeclaration = expression.resolve();

        assertEquals(resolvedValueDeclaration.isField(), true);



        ResolvedFieldDeclaration resolvedFieldDeclaration = (ResolvedFieldDeclaration) resolvedValueDeclaration;
        assertEquals(resolvedFieldDeclaration.isStatic(), true);

    }
}