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

package com.github.javaparser.printer;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.imports.*;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeArguments;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.VoidVisitor;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.javaparser.utils.PositionUtils.sortByBeginPosition;
import static com.github.javaparser.utils.Utils.isNullOrEmpty;

/**
 * Outputs the AST as formatted Java source code.
 *
 * @author Julio Vilmar Gesser
 */
public class PrettyPrintVisitor implements VoidVisitor<Void> {
    private final PrettyPrinterConfiguration configuration;

    private final SourcePrinter printer;

    public PrettyPrintVisitor(PrettyPrinterConfiguration prettyPrinterConfiguration) {
        configuration = prettyPrinterConfiguration;
        printer = new SourcePrinter(configuration.getIndent());
    }

    public String getSource() {
        return printer.getSource();
    }

    private void printModifiers(final EnumSet<Modifier> modifiers) {
        if (modifiers.size() > 0)
            printer.print(modifiers.stream().map(Modifier::asString).collect(Collectors.joining(" ")) + " ");
    }

    private void printMembers(final NodeList<BodyDeclaration<?>> members, final Void arg) {
        for (final BodyDeclaration<?> member : members) {
            printer.println();
            member.accept(this, arg);
            printer.println();
        }
    }

    private void printMemberAnnotations(final NodeList<AnnotationExpr> annotations, final Void arg) {
        if (annotations.isEmpty()) {
            return;
        }
        for (final AnnotationExpr a : annotations) {
            a.accept(this, arg);
            printer.println();
        }
    }

    private void printAnnotations(final NodeList<AnnotationExpr> annotations, boolean prefixWithASpace,
                                  final Void arg) {
        if (annotations.isEmpty()) {
            return;
        }
        if (prefixWithASpace) {
            printer.print(" ");
        }
        for (AnnotationExpr annotation : annotations) {
            annotation.accept(this, arg);
            printer.print(" ");
        }
    }

    private void printTypeArgs(final NodeWithTypeArguments<?> nodeWithTypeArguments, final Void arg) {
        NodeList<Type> typeArguments = nodeWithTypeArguments.getTypeArguments().orElse(null);
        if (!isNullOrEmpty(typeArguments)) {
            printer.print("<");
            for (final Iterator<Type> i = typeArguments.iterator(); i.hasNext(); ) {
                final Type t = i.next();
                t.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
            printer.print(">");
        }
    }

    private void printTypeParameters(final NodeList<TypeParameter> args, final Void arg) {
        if (!isNullOrEmpty(args)) {
            printer.print("<");
            for (final Iterator<TypeParameter> i = args.iterator(); i.hasNext(); ) {
                final TypeParameter t = i.next();
                t.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
            printer.print(">");
        }
    }

    private void printArguments(final NodeList<Expression> args, final Void arg) {
        printer.print("(");
        if (!isNullOrEmpty(args)) {
            for (final Iterator<Expression> i = args.iterator(); i.hasNext(); ) {
                final Expression e = i.next();
                e.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        printer.print(")");
    }

    private void printJavaComment(final Comment javacomment, final Void arg) {
        if (javacomment != null) {
            javacomment.accept(this, arg);
        }
    }

    @Override
    public void visit(final CompilationUnit n, final Void arg) {
        printJavaComment(n.getComment(), arg);

        if (n.getPackage().isPresent()) {
            n.getPackage().get().accept(this, arg);
        }

        n.getImports().accept(this, arg);
        if (!n.getImports().isEmpty()) {
            printer.println();
        }

        for (final Iterator<TypeDeclaration<?>> i = n.getTypes().iterator(); i.hasNext(); ) {
            i.next().accept(this, arg);
            printer.println();
            if (i.hasNext()) {
                printer.println();
            }
        }

        printOrphanCommentsEnding(n);
    }

    @Override
    public void visit(final PackageDeclaration n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printAnnotations(n.getAnnotations(), false, arg);
        printer.print("package ");
        n.getName().accept(this, arg);
        printer.println(";");
        printer.println();

        printOrphanCommentsEnding(n);
    }

    @Override
    public void visit(final NameExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        n.getName().accept(this, arg);

        printOrphanCommentsEnding(n);
    }

    @Override
    public void visit(final Name n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        if (n.getQualifier().isPresent()) {
            n.getQualifier().get().accept(this, arg);
            printer.print(".");
        }
        printer.print(n.getIdentifier());

        printOrphanCommentsEnding(n);
    }

    @Override
    public void visit(SimpleName n, Void arg) {
        printer.print(n.getIdentifier());
    }

    @Override
    public void visit(final ClassOrInterfaceDeclaration n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printMemberAnnotations(n.getAnnotations(), arg);
        printModifiers(n.getModifiers());

        if (n.isInterface()) {
            printer.print("interface ");
        } else {
            printer.print("class ");
        }

        n.getName().accept(this, arg);

        printTypeParameters(n.getTypeParameters(), arg);

        if (!n.getExtends().isEmpty()) {
            printer.print(" extends ");
            for (final Iterator<ClassOrInterfaceType> i = n.getExtends().iterator(); i.hasNext(); ) {
                final ClassOrInterfaceType c = i.next();
                c.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }

        if (!n.getImplements().isEmpty()) {
            printer.print(" implements ");
            for (final Iterator<ClassOrInterfaceType> i = n.getImplements().iterator(); i.hasNext(); ) {
                final ClassOrInterfaceType c = i.next();
                c.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }

        printer.println(" {");
        printer.indent();
        if (!isNullOrEmpty(n.getMembers())) {
            printMembers(n.getMembers(), arg);
        }

        printOrphanCommentsEnding(n);

        printer.unindent();
        printer.print("}");
    }

    @Override
    public void visit(final JavadocComment n, final Void arg) {
        printer.print("/**");
        printer.print(n.getContent());
        printer.println("*/");
    }

    @Override
    public void visit(final ClassOrInterfaceType n, final Void arg) {
        printJavaComment(n.getComment(), arg);

        if (n.getScope().isPresent()) {
            n.getScope().get().accept(this, arg);
            printer.print(".");
        }
        for (AnnotationExpr ae : n.getAnnotations()) {
            ae.accept(this, arg);
            printer.print(" ");
        }

        n.getName().accept(this, arg);

        if (n.isUsingDiamondOperator()) {
            printer.print("<>");
        } else {
            printTypeArgs(n, arg);
        }
    }

    @Override
    public void visit(final TypeParameter n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        for (AnnotationExpr ann : n.getAnnotations()) {
            ann.accept(this, arg);
            printer.print(" ");
        }
        n.getName().accept(this, arg);
        if (!isNullOrEmpty(n.getTypeBound())) {
            printer.print(" extends ");
            for (final Iterator<ClassOrInterfaceType> i = n.getTypeBound().iterator(); i.hasNext(); ) {
                final ClassOrInterfaceType c = i.next();
                c.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(" & ");
                }
            }
        }
    }

    @Override
    public void visit(final PrimitiveType n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printAnnotations(n.getAnnotations(), true, arg);
        printer.print(n.getType().asString());
    }

    @Override
    public void visit(final ArrayType n, final Void arg) {
        final List<ArrayType> arrayTypeBuffer = new LinkedList<>();
        Type type = n;
        while (type instanceof ArrayType) {
            final ArrayType arrayType = (ArrayType) type;
            arrayTypeBuffer.add(arrayType);
            type = arrayType.getComponentType();
        }

        type.accept(this, arg);
        for (ArrayType arrayType : arrayTypeBuffer) {
            printAnnotations(arrayType.getAnnotations(), true, arg);
            printer.print("[]");
        }
    }

    @Override
    public void visit(final ArrayCreationLevel n, final Void arg) {
        printAnnotations(n.getAnnotations(), true, arg);
        printer.print("[");
        if (n.getDimension().isPresent()) {
            n.getDimension().get().accept(this, arg);
        }
        printer.print("]");
    }

    @Override
    public void visit(final IntersectionType n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printAnnotations(n.getAnnotations(), false, arg);
        boolean isFirst = true;
        for (ReferenceType element : n.getElements()) {
            element.accept(this, arg);
            if (isFirst) {
                isFirst = false;
            } else {
                printer.print(" & ");
            }
        }
    }

    @Override
    public void visit(final UnionType n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printAnnotations(n.getAnnotations(), true, arg);
        boolean isFirst = true;
        for (ReferenceType element : n.getElements()) {
            if (isFirst) {
                isFirst = false;
            } else {
                printer.print(" | ");
            }
            element.accept(this, arg);
        }
    }

    @Override
    public void visit(final WildcardType n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printAnnotations(n.getAnnotations(), false, arg);
        printer.print("?");
        if (n.getExtendedTypes().isPresent()) {
            printer.print(" extends ");
            n.getExtendedTypes().get().accept(this, arg);
        }
        if (n.getSuperTypes().isPresent()) {
            printer.print(" super ");
            n.getSuperTypes().get().accept(this, arg);
        }
    }

    @Override
    public void visit(final UnknownType n, final Void arg) {
        // Nothing to print
    }

    @Override
    public void visit(final FieldDeclaration n, final Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);

        printJavaComment(n.getComment(), arg);
        printMemberAnnotations(n.getAnnotations(), arg);
        printModifiers(n.getModifiers());
        if(!n.getVariables().isEmpty()) {
            n.getVariables().get(0).getType().getElementType().accept(this, arg);
        }

        printer.print(" ");
        for (final Iterator<VariableDeclarator> i = n.getVariables().iterator(); i.hasNext(); ) {
            final VariableDeclarator var = i.next();
            var.accept(this, arg);
            if (i.hasNext()) {
                printer.print(", ");
            }
        }

        printer.print(";");
    }

    @Override
    public void visit(final VariableDeclarator n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        n.getName().accept(this, arg);


        final List<ArrayType> arrayTypeBuffer = new LinkedList<>();
        Type type = n.getType();
        while (type instanceof ArrayType) {
            final ArrayType arrayType = (ArrayType) type;
            arrayTypeBuffer.add(arrayType);
            type = arrayType.getComponentType();
        }

        for (ArrayType arrayType : arrayTypeBuffer) {
            printAnnotations(arrayType.getAnnotations(), true, arg);
            printer.print("[]");
        }

        if (n.getInitializer().isPresent()) {
            printer.print(" = ");
            n.getInitializer().get().accept(this, arg);
        }
    }

    @Override
    public void visit(final ArrayInitializerExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("{");
        if (!isNullOrEmpty(n.getValues())) {
            printer.print(" ");
            for (final Iterator<Expression> i = n.getValues().iterator(); i.hasNext(); ) {
                final Expression expr = i.next();
                expr.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
            printer.print(" ");
        }
        printer.print("}");
    }

    @Override
    public void visit(final VoidType n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printAnnotations(n.getAnnotations(), false, arg);
        printer.print("void");
    }

    @Override
    public void visit(final ArrayAccessExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        n.getName().accept(this, arg);
        printer.print("[");
        n.getIndex().accept(this, arg);
        printer.print("]");
    }

    @Override
    public void visit(final ArrayCreationExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("new ");
        n.getElementType().accept(this, arg);
        for (ArrayCreationLevel level : n.getLevels()) {
            level.accept(this, arg);
        }
        if (n.getInitializer().isPresent()) {
            printer.print(" ");
            n.getInitializer().get().accept(this, arg);
        }
    }

    @Override
    public void visit(final AssignExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        n.getTarget().accept(this, arg);
        printer.print(" ");
        printer.print(n.getOperator().asString());
        printer.print(" ");
        n.getValue().accept(this, arg);
    }

    @Override
    public void visit(final BinaryExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        n.getLeft().accept(this, arg);
        printer.print(" ");
        printer.print(n.getOperator().asString());
        printer.print(" ");
        n.getRight().accept(this, arg);
    }

    @Override
    public void visit(final CastExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("(");
        n.getType().accept(this, arg);
        printer.print(") ");
        n.getExpression().accept(this, arg);
    }

    @Override
    public void visit(final ClassExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        n.getType().accept(this, arg);
        printer.print(".class");
    }

    @Override
    public void visit(final ConditionalExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        n.getCondition().accept(this, arg);
        printer.print(" ? ");
        n.getThenExpr().accept(this, arg);
        printer.print(" : ");
        n.getElseExpr().accept(this, arg);
    }

    @Override
    public void visit(final EnclosedExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("(");
        if (n.getInner().isPresent()) {
            n.getInner().get().accept(this, arg);
        }
        printer.print(")");
    }

    @Override
    public void visit(final FieldAccessExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        if (n.getScope().isPresent())
            n.getScope().get().accept(this, arg);
        printer.print(".");
        n.getField().accept(this, arg);
    }

    @Override
    public void visit(final InstanceOfExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        n.getExpression().accept(this, arg);
        printer.print(" instanceof ");
        n.getType().accept(this, arg);
    }

    @Override
    public void visit(final CharLiteralExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("'");
        printer.print(n.getValue());
        printer.print("'");
    }

    @Override
    public void visit(final DoubleLiteralExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print(n.getValue());
    }

    @Override
    public void visit(final IntegerLiteralExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print(n.getValue());
    }

    @Override
    public void visit(final LongLiteralExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print(n.getValue());
    }

    @Override
    public void visit(final StringLiteralExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("\"");
        printer.print(n.getValue());
        printer.print("\"");
    }

    @Override
    public void visit(final BooleanLiteralExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print(String.valueOf(n.getValue()));
    }

    @Override
    public void visit(final NullLiteralExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("null");
    }

    @Override
    public void visit(final ThisExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        if (n.getClassExpr().isPresent()) {
            n.getClassExpr().get().accept(this, arg);
            printer.print(".");
        }
        printer.print("this");
    }

    @Override
    public void visit(final SuperExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        if (n.getClassExpr().isPresent()) {
            n.getClassExpr().get().accept(this, arg);
            printer.print(".");
        }
        printer.print("super");
    }

    @Override
    public void visit(final MethodCallExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        if (n.getScope() != null) {
            n.getScope().accept(this, arg);
            printer.print(".");
        }
        printTypeArgs(n, arg);
        n.getName().accept(this, arg);
        printArguments(n.getArguments(), arg);
    }

    @Override
    public void visit(final ObjectCreationExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        if (n.getScope().isPresent()) {
            n.getScope().get().accept(this, arg);
            printer.print(".");
        }

        printer.print("new ");

        printTypeArgs(n, arg);
        if (!isNullOrEmpty(n.getTypeArguments().orElse(null))) {
            printer.print(" ");
        }

        n.getType().accept(this, arg);

        printArguments(n.getArguments(), arg);

        if (n.getAnonymousClassBody().isPresent()) {
            printer.println(" {");
            printer.indent();
            printMembers(n.getAnonymousClassBody().get(), arg);
            printer.unindent();
            printer.print("}");
        }
    }

    @Override
    public void visit(final UnaryExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        if (n.getOperator().isPrefix()) {
            printer.print(n.getOperator().asString());
        }

        n.getExpression().accept(this, arg);

        if (n.getOperator().isPostfix()) {
            printer.print(n.getOperator().asString());
        }
    }

    @Override
    public void visit(final ConstructorDeclaration n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printMemberAnnotations(n.getAnnotations(), arg);
        printModifiers(n.getModifiers());

        printTypeParameters(n.getTypeParameters(), arg);
        if (n.isGeneric()) {
            printer.print(" ");
        }
        n.getName().accept(this, arg);

        printer.print("(");
        if (!n.getParameters().isEmpty()) {
            for (final Iterator<Parameter> i = n.getParameters().iterator(); i.hasNext(); ) {
                final Parameter p = i.next();
                p.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        printer.print(")");

        if (!isNullOrEmpty(n.getThrownExceptions())) {
            printer.print(" throws ");
            for (final Iterator<ReferenceType> i = n.getThrownExceptions().iterator(); i.hasNext(); ) {
                final ReferenceType name = i.next();
                name.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        printer.print(" ");
        n.getBody().accept(this, arg);
    }

    @Override
    public void visit(final MethodDeclaration n, final Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);

        printJavaComment(n.getComment(), arg);
        printMemberAnnotations(n.getAnnotations(), arg);
        printModifiers(n.getModifiers());
        if (n.isDefault()) {
            printer.print("default ");
        }
        printTypeParameters(n.getTypeParameters(), arg);
        if (!isNullOrEmpty(n.getTypeParameters())) {
            printer.print(" ");
        }

        n.getType().accept(this, arg);
        printer.print(" ");
        n.getName().accept(this, arg);

        printer.print("(");
        if (!isNullOrEmpty(n.getParameters())) {
            for (final Iterator<Parameter> i = n.getParameters().iterator(); i.hasNext(); ) {
                final Parameter p = i.next();
                p.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        printer.print(")");

        if (!isNullOrEmpty(n.getThrownExceptions())) {
            printer.print(" throws ");
            for (final Iterator<ReferenceType> i = n.getThrownExceptions().iterator(); i.hasNext(); ) {
                final ReferenceType name = i.next();
                name.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        if (!n.getBody().isPresent()) {
            printer.print(";");
        } else {
            printer.print(" ");
            n.getBody().get().accept(this, arg);
        }
    }

    @Override
    public void visit(final Parameter n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printAnnotations(n.getAnnotations(), false, arg);
        printModifiers(n.getModifiers());
        if (n.getType() != null) {
            n.getType().accept(this, arg);
        }
        if (n.isVarArgs()) {
            printer.print("...");
        }
        printer.print(" ");
        n.getName().accept(this, arg);
    }

    @Override
    public void visit(final ExplicitConstructorInvocationStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        if (n.isThis()) {
            printTypeArgs(n, arg);
            printer.print("this");
        } else {
            if (n.getExpression().isPresent()) {
                n.getExpression().get().accept(this, arg);
                printer.print(".");
            }
            printTypeArgs(n, arg);
            printer.print("super");
        }
        printArguments(n.getArguments(), arg);
        printer.print(";");
    }

    @Override
    public void visit(final VariableDeclarationExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printAnnotations(n.getAnnotations(), false, arg);
        printModifiers(n.getModifiers());

        if(!n.getVariables().isEmpty()) {
            n.getVariables().get(0).getType().getElementType().accept(this, arg);
        }
        printer.print(" ");

        for (final Iterator<VariableDeclarator> i = n.getVariables().iterator(); i.hasNext(); ) {
            final VariableDeclarator v = i.next();
            v.accept(this, arg);
            if (i.hasNext()) {
                printer.print(", ");
            }
        }
    }

    @Override
    public void visit(final TypeDeclarationStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        n.getTypeDeclaration().accept(this, arg);
    }

    @Override
    public void visit(final AssertStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("assert ");
        n.getCheck().accept(this, arg);
        if (n.getMessage().isPresent()) {
            printer.print(" : ");
            n.getMessage().get().accept(this, arg);
        }
        printer.print(";");
    }

    @Override
    public void visit(final BlockStmt n, final Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printJavaComment(n.getComment(), arg);
        printer.println("{");
        if (n.getStatements() != null) {
            printer.indent();
            for (final Statement s : n.getStatements()) {
                s.accept(this, arg);
                printer.println();
            }
            printer.unindent();
        }
        printOrphanCommentsEnding(n);
        printer.print("}");

    }

    @Override
    public void visit(final LabeledStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print(n.getLabel());
        printer.print(": ");
        n.getStatement().accept(this, arg);
    }

    @Override
    public void visit(final EmptyStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print(";");
    }

    @Override
    public void visit(final ExpressionStmt n, final Void arg) {
        printOrphanCommentsBeforeThisChildNode(n);
        printJavaComment(n.getComment(), arg);
        n.getExpression().accept(this, arg);
        printer.print(";");
    }

    @Override
    public void visit(final SwitchStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("switch(");
        n.getSelector().accept(this, arg);
        printer.println(") {");
        if (n.getEntries() != null) {
            printer.indent();
            for (final SwitchEntryStmt e : n.getEntries()) {
                e.accept(this, arg);
            }
            printer.unindent();
        }
        printer.print("}");

    }

    @Override
    public void visit(final SwitchEntryStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        if (n.getLabel().isPresent()) {
            printer.print("case ");
            n.getLabel().get().accept(this, arg);
            printer.print(":");
        } else {
            printer.print("default:");
        }
        printer.println();
        printer.indent();
        if (n.getStatements() != null) {
            for (final Statement s : n.getStatements()) {
                s.accept(this, arg);
                printer.println();
            }
        }
        printer.unindent();
    }

    @Override
    public void visit(final BreakStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("break");
        if (n.getIdentifier().isPresent()) {
            printer.print(" ");
            printer.print(n.getIdentifier().get());
        }
        printer.print(";");
    }

    @Override
    public void visit(final ReturnStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("return");
        if (n.getExpression().isPresent()) {
            printer.print(" ");
            n.getExpression().get().accept(this, arg);
        }
        printer.print(";");
    }

    @Override
    public void visit(final EnumDeclaration n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printMemberAnnotations(n.getAnnotations(), arg);
        printModifiers(n.getModifiers());

        printer.print("enum ");
        n.getName().accept(this, arg);

        if (!n.getImplements().isEmpty()) {
            printer.print(" implements ");
            for (final Iterator<ClassOrInterfaceType> i = n.getImplements().iterator(); i.hasNext(); ) {
                final ClassOrInterfaceType c = i.next();
                c.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }

        printer.println(" {");
        printer.indent();
        if (n.getEntries() != null) {
            printer.println();
            for (final Iterator<EnumConstantDeclaration> i = n.getEntries().iterator(); i.hasNext(); ) {
                final EnumConstantDeclaration e = i.next();
                e.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        if (!n.getMembers().isEmpty()) {
            printer.println(";");
            printMembers(n.getMembers(), arg);
        } else {
            if (!n.getEntries().isEmpty()) {
                printer.println();
            }
        }
        printer.unindent();
        printer.print("}");
    }

    @Override
    public void visit(final EnumConstantDeclaration n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printMemberAnnotations(n.getAnnotations(), arg);
        n.getName().accept(this, arg);

        if (!n.getArguments().isEmpty()) {
            printArguments(n.getArguments(), arg);
        }

        if (!n.getClassBody().isEmpty()) {
            printer.println(" {");
            printer.indent();
            printMembers(n.getClassBody(), arg);
            printer.unindent();
            printer.println("}");
        }
    }

    @Override
    public void visit(final EmptyMemberDeclaration n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print(";");
    }

    @Override
    public void visit(final InitializerDeclaration n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        if (n.isStatic()) {
            printer.print("static ");
        }
        n.getBlock().accept(this, arg);
    }

    @Override
    public void visit(final IfStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("if (");
        n.getCondition().accept(this, arg);
        final boolean thenBlock = n.getThenStmt() instanceof BlockStmt;
        if (thenBlock) // block statement should start on the same line
            printer.print(") ");
        else {
            printer.println(")");
            printer.indent();
        }
        n.getThenStmt().accept(this, arg);
        if (!thenBlock)
            printer.unindent();
        if (n.getElseStmt().isPresent()) {
            if (thenBlock)
                printer.print(" ");
            else
                printer.println();
            final boolean elseIf = n.getElseStmt().orElse(null) instanceof IfStmt;
            final boolean elseBlock = n.getElseStmt().orElse(null) instanceof BlockStmt;
            if (elseIf || elseBlock) // put chained if and start of block statement on a same level
                printer.print("else ");
            else {
                printer.println("else");
                printer.indent();
            }
            if (n.getElseStmt().isPresent())
                n.getElseStmt().get().accept(this, arg);
            if (!(elseIf || elseBlock))
                printer.unindent();
        }
    }

    @Override
    public void visit(final WhileStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("while (");
        n.getCondition().accept(this, arg);
        printer.print(") ");
        n.getBody().accept(this, arg);
    }

    @Override
    public void visit(final ContinueStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("continue");
        if (n.getIdentifier().isPresent()) {
            printer.print(" ");
            printer.print(n.getIdentifier().get());
        }
        printer.print(";");
    }

    @Override
    public void visit(final DoStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("do ");
        n.getBody().accept(this, arg);
        printer.print(" while (");
        n.getCondition().accept(this, arg);
        printer.print(");");
    }

    @Override
    public void visit(final ForeachStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("for (");
        n.getVariable().accept(this, arg);
        printer.print(" : ");
        n.getIterable().accept(this, arg);
        printer.print(") ");
        n.getBody().accept(this, arg);
    }

    @Override
    public void visit(final ForStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("for (");
        if (n.getInitialization() != null) {
            for (final Iterator<Expression> i = n.getInitialization().iterator(); i.hasNext(); ) {
                final Expression e = i.next();
                e.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        printer.print("; ");
        if (n.getCompare().isPresent()) {
            n.getCompare().get().accept(this, arg);
        }
        printer.print("; ");
        if (n.getUpdate() != null) {
            for (final Iterator<Expression> i = n.getUpdate().iterator(); i.hasNext(); ) {
                final Expression e = i.next();
                e.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        printer.print(") ");
        n.getBody().accept(this, arg);
    }

    @Override
    public void visit(final ThrowStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("throw ");
        n.getExpression().accept(this, arg);
        printer.print(";");
    }

    @Override
    public void visit(final SynchronizedStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("synchronized (");
        n.getExpression().accept(this, arg);
        printer.print(") ");
        n.getBody().accept(this, arg);
    }

    @Override
    public void visit(final TryStmt n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("try ");
        if (!n.getResources().isEmpty()) {
            printer.print("(");
            Iterator<VariableDeclarationExpr> resources = n.getResources().iterator();
            boolean first = true;
            while (resources.hasNext()) {
                visit(resources.next(), arg);
                if (resources.hasNext()) {
                    printer.print(";");
                    printer.println();
                    if (first) {
                        printer.indent();
                    }
                }
                first = false;
            }
            if (n.getResources().size() > 1) {
                printer.unindent();
            }
            printer.print(") ");
        }
        if (n.getTryBlock().isPresent()) {
            n.getTryBlock().get().accept(this, arg);
        }
        for (final CatchClause c : n.getCatchClauses()) {
            c.accept(this, arg);
        }
        if (n.getFinallyBlock().isPresent()) {
            printer.print(" finally ");
            n.getFinallyBlock().get().accept(this, arg);
        }
    }

    @Override
    public void visit(final CatchClause n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print(" catch (");
        n.getParameter().accept(this, arg);
        printer.print(") ");
        n.getBody().accept(this, arg);

    }

    @Override
    public void visit(final AnnotationDeclaration n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printMemberAnnotations(n.getAnnotations(), arg);
        printModifiers(n.getModifiers());

        printer.print("@interface ");
        n.getName().accept(this, arg);
        printer.println(" {");
        printer.indent();
        if (n.getMembers() != null) {
            printMembers(n.getMembers(), arg);
        }
        printer.unindent();
        printer.print("}");
    }

    @Override
    public void visit(final AnnotationMemberDeclaration n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printMemberAnnotations(n.getAnnotations(), arg);
        printModifiers(n.getModifiers());

        n.getType().accept(this, arg);
        printer.print(" ");
        n.getName().accept(this, arg);
        printer.print("()");
        if (n.getDefaultValue().isPresent()) {
            printer.print(" default ");
            n.getDefaultValue().get().accept(this, arg);
        }
        printer.print(";");
    }

    @Override
    public void visit(final MarkerAnnotationExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("@");
        n.getName().accept(this, arg);
    }

    @Override
    public void visit(final SingleMemberAnnotationExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("@");
        n.getName().accept(this, arg);
        printer.print("(");
        n.getMemberValue().accept(this, arg);
        printer.print(")");
    }

    @Override
    public void visit(final NormalAnnotationExpr n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("@");
        n.getName().accept(this, arg);
        printer.print("(");
        if (n.getPairs() != null) {
            for (final Iterator<MemberValuePair> i = n.getPairs().iterator(); i.hasNext(); ) {
                final MemberValuePair m = i.next();
                m.accept(this, arg);
                if (i.hasNext()) {
                    printer.print(", ");
                }
            }
        }
        printer.print(")");
    }

    @Override
    public void visit(final MemberValuePair n, final Void arg) {
        printJavaComment(n.getComment(), arg);
        n.getName().accept(this, arg);
        printer.print(" = ");
        n.getValue().accept(this, arg);
    }

    @Override
    public void visit(final LineComment n, final Void arg) {
        if (!configuration.isPrintComments()) {
            return;
        }
        printer.print("//");
        String tmp = n.getContent();
        tmp = tmp.replace('\r', ' ');
        tmp = tmp.replace('\n', ' ');
        printer.println(tmp);
    }

    @Override
    public void visit(final BlockComment n, final Void arg) {
        if (!configuration.isPrintComments()) {
            return;
        }
        printer.print("/*").print(n.getContent()).println("*/");
    }

    @Override
    public void visit(LambdaExpr n, Void arg) {
        printJavaComment(n.getComment(), arg);

        final NodeList<Parameter> parameters = n.getParameters();
        final boolean printPar = n.isEnclosingParameters();

        if (printPar) {
            printer.print("(");
        }
        for (Iterator<Parameter> i = parameters.iterator(); i.hasNext(); ) {
            Parameter p = i.next();
            p.accept(this, arg);
            if (i.hasNext()) {
                printer.print(", ");
            }
        }
        if (printPar) {
            printer.print(")");
        }

        printer.print(" -> ");
        final Statement body = n.getBody();
        if (body instanceof ExpressionStmt) {
            // Print the expression directly
            ((ExpressionStmt) body).getExpression().accept(this, arg);
        } else {
            body.accept(this, arg);
        }
    }

    @Override
    public void visit(MethodReferenceExpr n, Void arg) {
        printJavaComment(n.getComment(), arg);
        Expression scope = n.getScope();
        String identifier = n.getIdentifier();
        if (scope != null) {
            n.getScope().accept(this, arg);
        }

        printer.print("::");
        printTypeArgs(n, arg);
        if (identifier != null) {
            printer.print(identifier);
        }

    }

    @Override
    public void visit(TypeExpr n, Void arg) {
        printJavaComment(n.getComment(), arg);
        if (n.getType() != null) {
            n.getType().accept(this, arg);
        }
    }

    @Override
    public void visit(NodeList n, Void arg) {
        for (Object node : n) {
            ((Node) node).accept(this, arg);
        }
    }

    @Override
    public void visit(BadImportDeclaration n, Void arg) {
        printer.println("???");
    }

    @Override
    public void visit(SingleStaticImportDeclaration n, Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("import static ");
        n.getType().accept(this, arg);
        printer.print(".");
        printer.print(n.getStaticMember());
        printer.println(";");
        printOrphanCommentsEnding(n);
    }

    @Override
    public void visit(SingleTypeImportDeclaration n, Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("import ");
        n.getType().accept(this, arg);
        printer.println(";");
        printOrphanCommentsEnding(n);
    }

    @Override
    public void visit(StaticImportOnDemandDeclaration n, Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("import static ");
        n.getType().accept(this, arg);
        printer.println(".*;");
        printOrphanCommentsEnding(n);
    }

    @Override
    public void visit(TypeImportOnDemandDeclaration n, Void arg) {
        printJavaComment(n.getComment(), arg);
        printer.print("import ");
        n.getName().accept(this, arg);
        printer.println(".*;");
        printOrphanCommentsEnding(n);
    }

    private void printOrphanCommentsBeforeThisChildNode(final Node node) {
        if (node instanceof Comment) return;

        Node parent = node.getParentNode().orElse(null);
        if (parent == null) return;
        List<Node> everything = new LinkedList<>();
        everything.addAll(parent.getChildNodes());
        sortByBeginPosition(everything);
        int positionOfTheChild = -1;
        for (int i = 0; i < everything.size(); i++) {
            if (everything.get(i) == node) positionOfTheChild = i;
        }
        if (positionOfTheChild == -1) {
            throw new AssertionError("I am not a child of my parent.");
        }
        int positionOfPreviousChild = -1;
        for (int i = positionOfTheChild - 1; i >= 0 && positionOfPreviousChild == -1; i--) {
            if (!(everything.get(i) instanceof Comment)) positionOfPreviousChild = i;
        }
        for (int i = positionOfPreviousChild + 1; i < positionOfTheChild; i++) {
            Node nodeToPrint = everything.get(i);
            if (!(nodeToPrint instanceof Comment))
                throw new RuntimeException(
                        "Expected comment, instead " + nodeToPrint.getClass() + ". Position of previous child: "
                                + positionOfPreviousChild + ", position of child " + positionOfTheChild);
            nodeToPrint.accept(this, null);
        }
    }

    private void printOrphanCommentsEnding(final Node node) {
        List<Node> everything = new LinkedList<>();
        everything.addAll(node.getChildNodes());
        sortByBeginPosition(everything);
        if (everything.isEmpty()) {
            return;
        }

        int commentsAtEnd = 0;
        boolean findingComments = true;
        while (findingComments && commentsAtEnd < everything.size()) {
            Node last = everything.get(everything.size() - 1 - commentsAtEnd);
            findingComments = (last instanceof Comment);
            if (findingComments) {
                commentsAtEnd++;
            }
        }
        for (int i = 0; i < commentsAtEnd; i++) {
            everything.get(everything.size() - commentsAtEnd + i).accept(this, null);
        }
    }
}
