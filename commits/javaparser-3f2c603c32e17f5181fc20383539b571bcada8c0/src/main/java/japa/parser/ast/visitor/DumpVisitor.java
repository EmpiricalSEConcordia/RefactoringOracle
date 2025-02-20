/*
 * Copyright (C) 2007 Júlio Vilmar Gesser.
 * 
 * This file is part of Java 1.5 parser and Abstract Syntax Tree.
 *
 * Java 1.5 parser and Abstract Syntax Tree is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Java 1.5 parser and Abstract Syntax Tree is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Java 1.5 parser and Abstract Syntax Tree.  If not, see <http://www.gnu.org/licenses/>.
 */
/*
 * Created on 05/10/2006
 */
package japa.parser.ast.visitor;

import japa.parser.ast.comments.BlockComment;
import japa.parser.ast.comments.Comment;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.Node;
import japa.parser.ast.ImportDeclaration;
import japa.parser.ast.comments.LineComment;
import japa.parser.ast.PackageDeclaration;
import japa.parser.ast.TypeParameter;
import japa.parser.ast.body.AnnotationDeclaration;
import japa.parser.ast.body.AnnotationMemberDeclaration;
import japa.parser.ast.body.BodyDeclaration;
import japa.parser.ast.body.ClassOrInterfaceDeclaration;
import japa.parser.ast.body.ConstructorDeclaration;
import japa.parser.ast.body.EmptyMemberDeclaration;
import japa.parser.ast.body.EmptyTypeDeclaration;
import japa.parser.ast.body.EnumConstantDeclaration;
import japa.parser.ast.body.EnumDeclaration;
import japa.parser.ast.body.FieldDeclaration;
import japa.parser.ast.body.InitializerDeclaration;
import japa.parser.ast.comments.JavadocComment;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.body.ModifierSet;
import japa.parser.ast.body.MultiTypeParameter;
import japa.parser.ast.body.Parameter;
import japa.parser.ast.body.TypeDeclaration;
import japa.parser.ast.body.VariableDeclarator;
import japa.parser.ast.body.VariableDeclaratorId;
import japa.parser.ast.expr.AnnotationExpr;
import japa.parser.ast.expr.ArrayAccessExpr;
import japa.parser.ast.expr.ArrayCreationExpr;
import japa.parser.ast.expr.ArrayInitializerExpr;
import japa.parser.ast.expr.AssignExpr;
import japa.parser.ast.expr.BinaryExpr;
import japa.parser.ast.expr.BooleanLiteralExpr;
import japa.parser.ast.expr.CastExpr;
import japa.parser.ast.expr.CharLiteralExpr;
import japa.parser.ast.expr.ClassExpr;
import japa.parser.ast.expr.ConditionalExpr;
import japa.parser.ast.expr.DoubleLiteralExpr;
import japa.parser.ast.expr.EnclosedExpr;
import japa.parser.ast.expr.Expression;
import japa.parser.ast.expr.FieldAccessExpr;
import japa.parser.ast.expr.InstanceOfExpr;
import japa.parser.ast.expr.IntegerLiteralExpr;
import japa.parser.ast.expr.IntegerLiteralMinValueExpr;
import japa.parser.ast.expr.LongLiteralExpr;
import japa.parser.ast.expr.LongLiteralMinValueExpr;
import japa.parser.ast.expr.MarkerAnnotationExpr;
import japa.parser.ast.expr.MemberValuePair;
import japa.parser.ast.expr.MethodCallExpr;
import japa.parser.ast.expr.NameExpr;
import japa.parser.ast.expr.NormalAnnotationExpr;
import japa.parser.ast.expr.NullLiteralExpr;
import japa.parser.ast.expr.ObjectCreationExpr;
import japa.parser.ast.expr.QualifiedNameExpr;
import japa.parser.ast.expr.SingleMemberAnnotationExpr;
import japa.parser.ast.expr.StringLiteralExpr;
import japa.parser.ast.expr.SuperExpr;
import japa.parser.ast.expr.ThisExpr;
import japa.parser.ast.expr.UnaryExpr;
import japa.parser.ast.expr.VariableDeclarationExpr;
import japa.parser.ast.stmt.AssertStmt;
import japa.parser.ast.stmt.BlockStmt;
import japa.parser.ast.stmt.BreakStmt;
import japa.parser.ast.stmt.CatchClause;
import japa.parser.ast.stmt.ContinueStmt;
import japa.parser.ast.stmt.DoStmt;
import japa.parser.ast.stmt.EmptyStmt;
import japa.parser.ast.stmt.ExplicitConstructorInvocationStmt;
import japa.parser.ast.stmt.ExpressionStmt;
import japa.parser.ast.stmt.ForStmt;
import japa.parser.ast.stmt.ForeachStmt;
import japa.parser.ast.stmt.IfStmt;
import japa.parser.ast.stmt.LabeledStmt;
import japa.parser.ast.stmt.ReturnStmt;
import japa.parser.ast.stmt.Statement;
import japa.parser.ast.stmt.SwitchEntryStmt;
import japa.parser.ast.stmt.SwitchStmt;
import japa.parser.ast.stmt.SynchronizedStmt;
import japa.parser.ast.stmt.ThrowStmt;
import japa.parser.ast.stmt.TryStmt;
import japa.parser.ast.stmt.TypeDeclarationStmt;
import japa.parser.ast.stmt.WhileStmt;
import japa.parser.ast.type.ClassOrInterfaceType;
import japa.parser.ast.type.PrimitiveType;
import japa.parser.ast.type.ReferenceType;
import japa.parser.ast.type.Type;
import japa.parser.ast.type.VoidType;
import japa.parser.ast.type.WildcardType;

import java.util.Iterator;
import java.util.List;

/**
 * Dumps the AST to formatted Java source code.
 * 
 * @author Julio Vilmar Gesser
 */
public final class DumpVisitor implements VoidVisitor<Object> {

	private static class SourcePrinter {

		private int level = 0;

		private boolean indented = false;

		private final StringBuilder buf = new StringBuilder();

		public void indent() {
			level++;
		}

		public void unindent() {
			level--;
		}

		private void makeIndent() {
			for (int i = 0; i < level; i++) {
				buf.append("    ");
			}
		}

		public void print(final String arg) {
			if (!indented) {
				makeIndent();
				indented = true;
			}
			buf.append(arg);
		}

		public void printLn(final String arg) {
			print(arg);
			printLn();
		}

		public void printLn() {
			buf.append("\n");
			indented = false;
		}

		public String getSource() {
			return buf.toString();
		}

		@Override public String toString() {
			return getSource();
		}
	}

	private final SourcePrinter printer = new SourcePrinter();

	public String getSource() {
		return printer.getSource();
	}

	private void printModifiers(final int modifiers) {
		if (ModifierSet.isPrivate(modifiers)) {
			printer.print("private ");
		}
		if (ModifierSet.isProtected(modifiers)) {
			printer.print("protected ");
		}
		if (ModifierSet.isPublic(modifiers)) {
			printer.print("public ");
		}
		if (ModifierSet.isAbstract(modifiers)) {
			printer.print("abstract ");
		}
		if (ModifierSet.isStatic(modifiers)) {
			printer.print("static ");
		}
		if (ModifierSet.isFinal(modifiers)) {
			printer.print("final ");
		}
		if (ModifierSet.isNative(modifiers)) {
			printer.print("native ");
		}
		if (ModifierSet.isStrictfp(modifiers)) {
			printer.print("strictfp ");
		}
		if (ModifierSet.isSynchronized(modifiers)) {
			printer.print("synchronized ");
		}
		if (ModifierSet.isTransient(modifiers)) {
			printer.print("transient ");
		}
		if (ModifierSet.isVolatile(modifiers)) {
			printer.print("volatile ");
		}
	}

	private void printMembers(final List<BodyDeclaration> members, final Object arg) {
		for (final BodyDeclaration member : members) {
			printer.printLn();
			member.accept(this, arg);
			printer.printLn();
		}
	}

	private void printMemberAnnotations(final List<AnnotationExpr> annotations, final Object arg) {
		if (annotations != null) {
			for (final AnnotationExpr a : annotations) {
				a.accept(this, arg);
				printer.printLn();
			}
		}
	}

	private void printAnnotations(final List<AnnotationExpr> annotations, final Object arg) {
		if (annotations != null) {
			for (final AnnotationExpr a : annotations) {
				a.accept(this, arg);
				printer.print(" ");
			}
		}
	}

	private void printTypeArgs(final List<Type> args, final Object arg) {
		if (args != null) {
			printer.print("<");
			for (final Iterator<Type> i = args.iterator(); i.hasNext();) {
				final Type t = i.next();
				t.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
			printer.print(">");
		}
	}

	private void printTypeParameters(final List<TypeParameter> args, final Object arg) {
		if (args != null) {
			printer.print("<");
			for (final Iterator<TypeParameter> i = args.iterator(); i.hasNext();) {
				final TypeParameter t = i.next();
				t.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
			printer.print(">");
		}
	}

	private void printArguments(final List<Expression> args, final Object arg) {
		printer.print("(");
		if (args != null) {
			for (final Iterator<Expression> i = args.iterator(); i.hasNext();) {
				final Expression e = i.next();
				e.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}
		printer.print(")");
	}

	private void printJavadoc(final JavadocComment javadoc, final Object arg) {
		if (javadoc != null) {
			javadoc.accept(this, arg);
		}
	}

	private void printJavaComment(final Comment javacomment, final Object arg) {
		if (javacomment != null) {
			javacomment.accept(this, arg);
		}
	}

	@Override public void visit(final CompilationUnit n, final Object arg) {
        printOrphanCommentsBetween(n.getOrphanComments(),arg,
                after(),
                before(n.getComment(), n.getPackage(), n.getImports(), n.getTypes()));

		printJavaComment(n.getComment(), arg);

        printOrphanCommentsBetween(n.getOrphanComments(),arg,
                after(n.getComment()),
                before(n.getPackage(),n.getImports(),n.getTypes()));

		if (n.getPackage() != null) {
			n.getPackage().accept(this, arg);
		}

        printOrphanCommentsBetween(n.getOrphanComments(),arg,
                after(n.getComment(),n.getPackage()),
                before(n.getImports(), n.getTypes()));

		if (n.getImports() != null) {
			for (final ImportDeclaration i : n.getImports()) {
				i.accept(this, arg);
			}
			printer.printLn();
		}

        printOrphanCommentsBetween(n.getOrphanComments(),arg,
                after(n.getComment(),n.getPackage(),n.getImports()),
                before(n.getTypes()));

		if (n.getTypes() != null) {
			for (final Iterator<TypeDeclaration> i = n.getTypes().iterator(); i.hasNext();) {
				i.next().accept(this, arg);
				printer.printLn();
				if (i.hasNext()) {
					printer.printLn();
				}
			}
		}

        printOrphanCommentsBetween(n.getOrphanComments(),arg,
                after(n.getComment(),n.getPackage(),n.getImports(),n.getTypes()),
                before());
	}

	@Override public void visit(final PackageDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printAnnotations(n.getAnnotations(), arg);
		printer.print("package ");
		n.getName().accept(this, arg);
		printer.printLn(";");
		printer.printLn();
	}

	@Override public void visit(final NameExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(n.getName());
	}

	@Override public void visit(final QualifiedNameExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		n.getQualifier().accept(this, arg);
		printer.print(".");
		printer.print(n.getName());
	}

	@Override public void visit(final ImportDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("import ");
		if (n.isStatic()) {
			printer.print("static ");
		}
		n.getName().accept(this, arg);
		if (n.isAsterisk()) {
			printer.print(".*");
		}
		printer.printLn(";");
	}

	@Override public void visit(final ClassOrInterfaceDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printJavadoc(n.getJavaDoc(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		if (n.isInterface()) {
			printer.print("interface ");
		} else {
			printer.print("class ");
		}

		printer.print(n.getName());

		printTypeParameters(n.getTypeParameters(), arg);

		if (n.getExtends() != null) {
			printer.print(" extends ");
			for (final Iterator<ClassOrInterfaceType> i = n.getExtends().iterator(); i.hasNext();) {
				final ClassOrInterfaceType c = i.next();
				c.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}

		if (n.getImplements() != null) {
			printer.print(" implements ");
			for (final Iterator<ClassOrInterfaceType> i = n.getImplements().iterator(); i.hasNext();) {
				final ClassOrInterfaceType c = i.next();
				c.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}

		printer.printLn(" {");
		printer.indent();
		if (n.getMembers() != null) {
			printMembers(n.getMembers(), arg);
		}
		printer.unindent();
		printer.print("}");
	}

	@Override public void visit(final EmptyTypeDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printJavadoc(n.getJavaDoc(), arg);
		printer.print(";");
	}

	@Override public void visit(final JavadocComment n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("/**");
		printer.print(n.getContent());
		printer.printLn("*/");
	}

	@Override public void visit(final ClassOrInterfaceType n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		if (n.getScope() != null) {
			n.getScope().accept(this, arg);
			printer.print(".");
		}
		printer.print(n.getName());
		printTypeArgs(n.getTypeArgs(), arg);
	}

	@Override public void visit(final TypeParameter n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(n.getName());
		if (n.getTypeBound() != null) {
			printer.print(" extends ");
			for (final Iterator<ClassOrInterfaceType> i = n.getTypeBound().iterator(); i.hasNext();) {
				final ClassOrInterfaceType c = i.next();
				c.accept(this, arg);
				if (i.hasNext()) {
					printer.print(" & ");
				}
			}
		}
	}

	@Override public void visit(final PrimitiveType n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		switch (n.getType()) {
		case Boolean:
			printer.print("boolean");
			break;
		case Byte:
			printer.print("byte");
			break;
		case Char:
			printer.print("char");
			break;
		case Double:
			printer.print("double");
			break;
		case Float:
			printer.print("float");
			break;
		case Int:
			printer.print("int");
			break;
		case Long:
			printer.print("long");
			break;
		case Short:
			printer.print("short");
			break;
		}
	}

	@Override public void visit(final ReferenceType n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		n.getType().accept(this, arg);
		for (int i = 0; i < n.getArrayCount(); i++) {
			printer.print("[]");
		}
	}

	@Override public void visit(final WildcardType n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("?");
		if (n.getExtends() != null) {
			printer.print(" extends ");
			n.getExtends().accept(this, arg);
		}
		if (n.getSuper() != null) {
			printer.print(" super ");
			n.getSuper().accept(this, arg);
		}
	}

	@Override public void visit(final FieldDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printJavadoc(n.getJavaDoc(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());
		n.getType().accept(this, arg);

		printer.print(" ");
		for (final Iterator<VariableDeclarator> i = n.getVariables().iterator(); i.hasNext();) {
			final VariableDeclarator var = i.next();
			var.accept(this, arg);
			if (i.hasNext()) {
				printer.print(", ");
			}
		}

		printer.print(";");
	}

	@Override public void visit(final VariableDeclarator n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		n.getId().accept(this, arg);
		if (n.getInit() != null) {
			printer.print(" = ");
			n.getInit().accept(this, arg);
		}
	}

	@Override public void visit(final VariableDeclaratorId n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(n.getName());
		for (int i = 0; i < n.getArrayCount(); i++) {
			printer.print("[]");
		}
	}

	@Override public void visit(final ArrayInitializerExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("{");
		if (n.getValues() != null) {
			printer.print(" ");
			for (final Iterator<Expression> i = n.getValues().iterator(); i.hasNext();) {
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

	@Override public void visit(final VoidType n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("void");
	}

	@Override public void visit(final ArrayAccessExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		n.getName().accept(this, arg);
		printer.print("[");
		n.getIndex().accept(this, arg);
		printer.print("]");
	}

	@Override public void visit(final ArrayCreationExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("new ");
		n.getType().accept(this, arg);

		if (n.getDimensions() != null) {
			for (final Expression dim : n.getDimensions()) {
				printer.print("[");
				dim.accept(this, arg);
				printer.print("]");
			}
			for (int i = 0; i < n.getArrayCount(); i++) {
				printer.print("[]");
			}
		} else {
			for (int i = 0; i < n.getArrayCount(); i++) {
				printer.print("[]");
			}
			printer.print(" ");
			n.getInitializer().accept(this, arg);
		}
	}

	@Override public void visit(final AssignExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		n.getTarget().accept(this, arg);
		printer.print(" ");
		switch (n.getOperator()) {
		case assign:
			printer.print("=");
			break;
		case and:
			printer.print("&=");
			break;
		case or:
			printer.print("|=");
			break;
		case xor:
			printer.print("^=");
			break;
		case plus:
			printer.print("+=");
			break;
		case minus:
			printer.print("-=");
			break;
		case rem:
			printer.print("%=");
			break;
		case slash:
			printer.print("/=");
			break;
		case star:
			printer.print("*=");
			break;
		case lShift:
			printer.print("<<=");
			break;
		case rSignedShift:
			printer.print(">>=");
			break;
		case rUnsignedShift:
			printer.print(">>>=");
			break;
		}
		printer.print(" ");
		n.getValue().accept(this, arg);
	}

	@Override public void visit(final BinaryExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		n.getLeft().accept(this, arg);
		printer.print(" ");
		switch (n.getOperator()) {
		case or:
			printer.print("||");
			break;
		case and:
			printer.print("&&");
			break;
		case binOr:
			printer.print("|");
			break;
		case binAnd:
			printer.print("&");
			break;
		case xor:
			printer.print("^");
			break;
		case equals:
			printer.print("==");
			break;
		case notEquals:
			printer.print("!=");
			break;
		case less:
			printer.print("<");
			break;
		case greater:
			printer.print(">");
			break;
		case lessEquals:
			printer.print("<=");
			break;
		case greaterEquals:
			printer.print(">=");
			break;
		case lShift:
			printer.print("<<");
			break;
		case rSignedShift:
			printer.print(">>");
			break;
		case rUnsignedShift:
			printer.print(">>>");
			break;
		case plus:
			printer.print("+");
			break;
		case minus:
			printer.print("-");
			break;
		case times:
			printer.print("*");
			break;
		case divide:
			printer.print("/");
			break;
		case remainder:
			printer.print("%");
			break;
		}
		printer.print(" ");
		n.getRight().accept(this, arg);
	}

	@Override public void visit(final CastExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("(");
		n.getType().accept(this, arg);
		printer.print(") ");
		n.getExpr().accept(this, arg);
	}

	@Override public void visit(final ClassExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		n.getType().accept(this, arg);
		printer.print(".class");
	}

	@Override public void visit(final ConditionalExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		n.getCondition().accept(this, arg);
		printer.print(" ? ");
		n.getThenExpr().accept(this, arg);
		printer.print(" : ");
		n.getElseExpr().accept(this, arg);
	}

	@Override public void visit(final EnclosedExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("(");
		n.getInner().accept(this, arg);
		printer.print(")");
	}

	@Override public void visit(final FieldAccessExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		n.getScope().accept(this, arg);
		printer.print(".");
		printer.print(n.getField());
	}

	@Override public void visit(final InstanceOfExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		n.getExpr().accept(this, arg);
		printer.print(" instanceof ");
		n.getType().accept(this, arg);
	}

	@Override public void visit(final CharLiteralExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("'");
		printer.print(n.getValue());
		printer.print("'");
	}

	@Override public void visit(final DoubleLiteralExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(n.getValue());
	}

	@Override public void visit(final IntegerLiteralExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(n.getValue());
	}

	@Override public void visit(final LongLiteralExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(n.getValue());
	}

	@Override public void visit(final IntegerLiteralMinValueExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(n.getValue());
	}

	@Override public void visit(final LongLiteralMinValueExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(n.getValue());
	}

	@Override public void visit(final StringLiteralExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("\"");
		printer.print(n.getValue());
		printer.print("\"");
	}

	@Override public void visit(final BooleanLiteralExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(String.valueOf(n.getValue()));
	}

	@Override public void visit(final NullLiteralExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("null");
	}

	@Override public void visit(final ThisExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		if (n.getClassExpr() != null) {
			n.getClassExpr().accept(this, arg);
			printer.print(".");
		}
		printer.print("this");
	}

	@Override public void visit(final SuperExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		if (n.getClassExpr() != null) {
			n.getClassExpr().accept(this, arg);
			printer.print(".");
		}
		printer.print("super");
	}

	@Override public void visit(final MethodCallExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		if (n.getScope() != null) {
			n.getScope().accept(this, arg);
			printer.print(".");
		}
		printTypeArgs(n.getTypeArgs(), arg);
		printer.print(n.getName());
		printArguments(n.getArgs(), arg);
	}

	@Override public void visit(final ObjectCreationExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		if (n.getScope() != null) {
			n.getScope().accept(this, arg);
			printer.print(".");
		}

		printer.print("new ");

		printTypeArgs(n.getTypeArgs(), arg);
		n.getType().accept(this, arg);

		printArguments(n.getArgs(), arg);

		if (n.getAnonymousClassBody() != null) {
			printer.printLn(" {");
			printer.indent();
			printMembers(n.getAnonymousClassBody(), arg);
			printer.unindent();
			printer.print("}");
		}
	}

	@Override public void visit(final UnaryExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		switch (n.getOperator()) {
		case positive:
			printer.print("+");
			break;
		case negative:
			printer.print("-");
			break;
		case inverse:
			printer.print("~");
			break;
		case not:
			printer.print("!");
			break;
		case preIncrement:
			printer.print("++");
			break;
		case preDecrement:
			printer.print("--");
			break;
		}

		n.getExpr().accept(this, arg);

		switch (n.getOperator()) {
		case posIncrement:
			printer.print("++");
			break;
		case posDecrement:
			printer.print("--");
			break;
		}
	}

	@Override public void visit(final ConstructorDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printJavadoc(n.getJavaDoc(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		printTypeParameters(n.getTypeParameters(), arg);
		if (n.getTypeParameters() != null) {
			printer.print(" ");
		}
		printer.print(n.getName());

		printer.print("(");
		if (n.getParameters() != null) {
			for (final Iterator<Parameter> i = n.getParameters().iterator(); i.hasNext();) {
				final Parameter p = i.next();
				p.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}
		printer.print(")");

		if (n.getThrows() != null) {
			printer.print(" throws ");
			for (final Iterator<NameExpr> i = n.getThrows().iterator(); i.hasNext();) {
				final NameExpr name = i.next();
				name.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}
		printer.print(" ");
		n.getBlock().accept(this, arg);
	}

	@Override public void visit(final MethodDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printJavadoc(n.getJavaDoc(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		printTypeParameters(n.getTypeParameters(), arg);
		if (n.getTypeParameters() != null) {
			printer.print(" ");
		}

		n.getType().accept(this, arg);
		printer.print(" ");
		printer.print(n.getName());

		printer.print("(");
		if (n.getParameters() != null) {
			for (final Iterator<Parameter> i = n.getParameters().iterator(); i.hasNext();) {
				final Parameter p = i.next();
				p.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}
		printer.print(")");

		for (int i = 0; i < n.getArrayCount(); i++) {
			printer.print("[]");
		}

		if (n.getThrows() != null) {
			printer.print(" throws ");
			for (final Iterator<NameExpr> i = n.getThrows().iterator(); i.hasNext();) {
				final NameExpr name = i.next();
				name.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}
		if (n.getBody() == null) {
			printer.print(";");
		} else {
			printer.print(" ");
			n.getBody().accept(this, arg);
		}
	}

	@Override public void visit(final Parameter n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		n.getType().accept(this, arg);
		if (n.isVarArgs()) {
			printer.print("...");
		}
		printer.print(" ");
		n.getId().accept(this, arg);
	}
	
    public void visit(MultiTypeParameter n, Object arg) {
        printAnnotations(n.getAnnotations(), arg);
        printModifiers(n.getModifiers());

        Iterator<Type> types = n.getTypes().iterator();
        types.next().accept(this, arg);
        while (types.hasNext()) {
        	printer.print(" | ");
        	types.next().accept(this, arg);
        }
        
        printer.print(" ");
        n.getId().accept(this, arg);
    }

	@Override public void visit(final ExplicitConstructorInvocationStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		if (n.isThis()) {
			printTypeArgs(n.getTypeArgs(), arg);
			printer.print("this");
		} else {
			if (n.getExpr() != null) {
				n.getExpr().accept(this, arg);
				printer.print(".");
			}
			printTypeArgs(n.getTypeArgs(), arg);
			printer.print("super");
		}
		printArguments(n.getArgs(), arg);
		printer.print(";");
	}

	@Override public void visit(final VariableDeclarationExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		n.getType().accept(this, arg);
		printer.print(" ");

		for (final Iterator<VariableDeclarator> i = n.getVars().iterator(); i.hasNext();) {
			final VariableDeclarator v = i.next();
			v.accept(this, arg);
			if (i.hasNext()) {
				printer.print(", ");
			}
		}
	}

	@Override public void visit(final TypeDeclarationStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		n.getTypeDeclaration().accept(this, arg);
	}

	@Override public void visit(final AssertStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("assert ");
		n.getCheck().accept(this, arg);
		if (n.getMessage() != null) {
			printer.print(" : ");
			n.getMessage().accept(this, arg);
		}
		printer.print(";");
	}

	@Override public void visit(final BlockStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.printLn("{");
		if (n.getStmts() != null) {
			printer.indent();
			for (final Statement s : n.getStmts()) {
				s.accept(this, arg);
				printer.printLn();
			}
			printer.unindent();
		}
		printer.print("}");

	}

	@Override public void visit(final LabeledStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(n.getLabel());
		printer.print(": ");
		n.getStmt().accept(this, arg);
	}

	@Override public void visit(final EmptyStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(";");
	}

	@Override public void visit(final ExpressionStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		n.getExpression().accept(this, arg);
		printer.print(";");
	}

	@Override public void visit(final SwitchStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("switch(");
		n.getSelector().accept(this, arg);
		printer.printLn(") {");
		if (n.getEntries() != null) {
			printer.indent();
			for (final SwitchEntryStmt e : n.getEntries()) {
				e.accept(this, arg);
			}
			printer.unindent();
		}
		printer.print("}");

	}

	@Override public void visit(final SwitchEntryStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		if (n.getLabel() != null) {
			printer.print("case ");
			n.getLabel().accept(this, arg);
			printer.print(":");
		} else {
			printer.print("default:");
		}
		printer.printLn();
		printer.indent();
		if (n.getStmts() != null) {
			for (final Statement s : n.getStmts()) {
				s.accept(this, arg);
				printer.printLn();
			}
		}
		printer.unindent();
	}

	@Override public void visit(final BreakStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("break");
		if (n.getId() != null) {
			printer.print(" ");
			printer.print(n.getId());
		}
		printer.print(";");
	}

	@Override public void visit(final ReturnStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("return");
		if (n.getExpr() != null) {
			printer.print(" ");
			n.getExpr().accept(this, arg);
		}
		printer.print(";");
	}

	@Override public void visit(final EnumDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printJavadoc(n.getJavaDoc(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		printer.print("enum ");
		printer.print(n.getName());

		if (n.getImplements() != null) {
			printer.print(" implements ");
			for (final Iterator<ClassOrInterfaceType> i = n.getImplements().iterator(); i.hasNext();) {
				final ClassOrInterfaceType c = i.next();
				c.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}

		printer.printLn(" {");
		printer.indent();
		if (n.getEntries() != null) {
			printer.printLn();
			for (final Iterator<EnumConstantDeclaration> i = n.getEntries().iterator(); i.hasNext();) {
				final EnumConstantDeclaration e = i.next();
				e.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}
		if (n.getMembers() != null) {
			printer.printLn(";");
			printMembers(n.getMembers(), arg);
		} else {
			if (n.getEntries() != null) {
				printer.printLn();
			}
		}
		printer.unindent();
		printer.print("}");
	}

	@Override public void visit(final EnumConstantDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printJavadoc(n.getJavaDoc(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printer.print(n.getName());

		if (n.getArgs() != null) {
			printArguments(n.getArgs(), arg);
		}

		if (n.getClassBody() != null) {
			printer.printLn(" {");
			printer.indent();
			printMembers(n.getClassBody(), arg);
			printer.unindent();
			printer.printLn("}");
		}
	}

	@Override public void visit(final EmptyMemberDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printJavadoc(n.getJavaDoc(), arg);
		printer.print(";");
	}

	@Override public void visit(final InitializerDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printJavadoc(n.getJavaDoc(), arg);
		if (n.isStatic()) {
			printer.print("static ");
		}
		n.getBlock().accept(this, arg);
	}

	@Override public void visit(final IfStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("if (");
		n.getCondition().accept(this, arg);
		final boolean thenBlock = n.getThenStmt() instanceof BlockStmt;
		if (thenBlock) // block statement should start on the same line
			printer.print(") ");
		else {
			printer.printLn(")");
			printer.indent();
		}
		n.getThenStmt().accept(this, arg);
		if (!thenBlock)
			printer.unindent();
		if (n.getElseStmt() != null) {
			if (thenBlock)
				printer.print(" ");
			else
				printer.printLn();
			final boolean elseIf = n.getElseStmt() instanceof IfStmt;
			final boolean elseBlock = n.getElseStmt() instanceof BlockStmt;
			if (elseIf || elseBlock) // put chained if and start of block statement on a same level
				printer.print("else ");
			else {
				printer.printLn("else");
				printer.indent();
			}
			n.getElseStmt().accept(this, arg);
			if (!(elseIf || elseBlock))
				printer.unindent();
		}
	}

	@Override public void visit(final WhileStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("while (");
		n.getCondition().accept(this, arg);
		printer.print(") ");
		n.getBody().accept(this, arg);
	}

	@Override public void visit(final ContinueStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("continue");
		if (n.getId() != null) {
			printer.print(" ");
			printer.print(n.getId());
		}
		printer.print(";");
	}

	@Override public void visit(final DoStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("do ");
		n.getBody().accept(this, arg);
		printer.print(" while (");
		n.getCondition().accept(this, arg);
		printer.print(");");
	}

	@Override public void visit(final ForeachStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("for (");
		n.getVariable().accept(this, arg);
		printer.print(" : ");
		n.getIterable().accept(this, arg);
		printer.print(") ");
		n.getBody().accept(this, arg);
	}

	@Override public void visit(final ForStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("for (");
		if (n.getInit() != null) {
			for (final Iterator<Expression> i = n.getInit().iterator(); i.hasNext();) {
				final Expression e = i.next();
				e.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}
		printer.print("; ");
		if (n.getCompare() != null) {
			n.getCompare().accept(this, arg);
		}
		printer.print("; ");
		if (n.getUpdate() != null) {
			for (final Iterator<Expression> i = n.getUpdate().iterator(); i.hasNext();) {
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

	@Override public void visit(final ThrowStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("throw ");
		n.getExpr().accept(this, arg);
		printer.print(";");
	}

	@Override public void visit(final SynchronizedStmt n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("synchronized (");
		n.getExpr().accept(this, arg);
		printer.print(") ");
		n.getBlock().accept(this, arg);
	}

	@Override public void visit(final TryStmt n, final Object arg) {
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
					printer.printLn();
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
		n.getTryBlock().accept(this, arg);
		if (n.getCatchs() != null) {
			for (final CatchClause c : n.getCatchs()) {
				c.accept(this, arg);
			}
		}
		if (n.getFinallyBlock() != null) {
			printer.print(" finally ");
			n.getFinallyBlock().accept(this, arg);
		}
	}

	@Override public void visit(final CatchClause n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(" catch (");
		n.getExcept().accept(this, arg);
		printer.print(") ");
		n.getCatchBlock().accept(this, arg);

	}

	@Override public void visit(final AnnotationDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printJavadoc(n.getJavaDoc(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		printer.print("@interface ");
		printer.print(n.getName());
		printer.printLn(" {");
		printer.indent();
		if (n.getMembers() != null) {
			printMembers(n.getMembers(), arg);
		}
		printer.unindent();
		printer.print("}");
	}

	@Override public void visit(final AnnotationMemberDeclaration n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printJavadoc(n.getJavaDoc(), arg);
		printMemberAnnotations(n.getAnnotations(), arg);
		printModifiers(n.getModifiers());

		n.getType().accept(this, arg);
		printer.print(" ");
		printer.print(n.getName());
		printer.print("()");
		if (n.getDefaultValue() != null) {
			printer.print(" default ");
			n.getDefaultValue().accept(this, arg);
		}
		printer.print(";");
	}

	@Override public void visit(final MarkerAnnotationExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("@");
		n.getName().accept(this, arg);
	}

	@Override public void visit(final SingleMemberAnnotationExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("@");
		n.getName().accept(this, arg);
		printer.print("(");
		n.getMemberValue().accept(this, arg);
		printer.print(")");
	}

	@Override public void visit(final NormalAnnotationExpr n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("@");
		n.getName().accept(this, arg);
		printer.print("(");
		if (n.getPairs() != null) {
			for (final Iterator<MemberValuePair> i = n.getPairs().iterator(); i.hasNext();) {
				final MemberValuePair m = i.next();
				m.accept(this, arg);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}
		printer.print(")");
	}

	@Override public void visit(final MemberValuePair n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print(n.getName());
		printer.print(" = ");
		n.getValue().accept(this, arg);
	}

	@Override public void visit(final LineComment n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("//");
		String tmp = n.getContent();
		tmp = tmp.replace('\r', ' ');
		tmp = tmp.replace('\n', ' ');
		printer.printLn(tmp);
	}

	@Override public void visit(final BlockComment n, final Object arg) {
		printJavaComment(n.getComment(), arg);
		printer.print("/*");
		printer.print(n.getContent());
		printer.printLn("*/");
	}

    private Position before(Object... things){
        Node n = firstOf(things);
        if (n==null){
            return Position.ABSOLUTE_START;
        } else {
            return Position.beginOf(n);
        }
    }

    private Position after(Object... things){
        Node n = lastOf(things);
        if (n==null){
            return Position.ABSOLUTE_START;
        } else {
            return Position.endOf(n);
        }
    }

    private Node lastOf(Object... things){
        for (int i=things.length-1;i>=0;i--){
            Object thing = things[i];
            if (thing != null) {
                if (thing instanceof Node){
                    return (Node)thing;
                } else if (thing instanceof List){
                    List<Node> list = (List<Node>)thing;
                    if (list.size()>0){
                        return list.get(list.size()-1);
                    }
                } else {
                    throw new RuntimeException("Wrong thing passed");
                }
            }
        }
        return null;
    }

    private Node firstOf(Object... things){
        for (Object thing : things){
            if (thing != null) {
                if (thing instanceof Node){
                    return (Node)thing;
                } else if (thing instanceof List){
                    List<Node> list = (List<Node>)thing;
                    if (list.size()>0){
                        return list.get(0);
                    }
                } else {
                    throw new RuntimeException("Wrong thing passed");
                }
            }
        }
        return null;
    }

    private Position endOf(final List<Node> nodes){
        Node lastNode = nodes.get(nodes.size()-1);
        if (lastNode==null){
            return Position.ABSOLUTE_START;
        } else {
            return Position.endOf(lastNode);
        }
    }

    private Position startOf(final List<Node> nodes){
        Node firstNode = nodes.get(0);
        if (firstNode==null){
            return Position.ABSOLUTE_END;
        } else {
            return Position.beginOf(firstNode);
        }
    }

    private void printOrphanCommentsBetween(final List<Comment> comments,final Object arg,Position start, Position end){
        printOrphanCommentsBetween(comments,arg,start.getLine(),start.getColumn(),end.getLine(),end.getColumn());
    }

    private void printOrphanCommentsBetween(final List<Comment> comments,final Object arg,int startLine,int startColumn,int endLine,int endColumn){
        for (Comment comment : comments){
            if (comment.isPositionedAfter(startLine,startColumn)
                && comment.isPositionedBefore(endLine,endColumn)){
                printOrphanComment(comment,arg);
            }
        }
    }

    private void printOrphanComment(final Comment comment,final Object arg){
        comment.accept(this,arg);
    }

}
