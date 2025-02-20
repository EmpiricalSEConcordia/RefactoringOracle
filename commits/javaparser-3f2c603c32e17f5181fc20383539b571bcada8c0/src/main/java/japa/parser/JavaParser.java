/*
 * Copyright (C) 2008 Júlio Vilmar Gesser.
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
package japa.parser;

import japa.parser.ast.comments.BlockComment;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.ImportDeclaration;
import japa.parser.ast.comments.LineComment;
import japa.parser.ast.comments.Comment;
import japa.parser.ast.body.BodyDeclaration;
import japa.parser.ast.comments.JavadocComment;
import japa.parser.ast.comments.CommentsCollection;
import japa.parser.ast.comments.CommentsParser;
import japa.parser.ast.expr.AnnotationExpr;
import japa.parser.ast.expr.Expression;
import japa.parser.ast.stmt.BlockStmt;
import japa.parser.ast.Node;
import japa.parser.ast.stmt.Statement;
import japa.parser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

// FIXME this file does not seem to be generated by javacc. Is the doc wrong, or the javacc config?
/**
 * <p>
 * This class was generated automatically by javacc, do not edit.
 * </p>
 * <p>
 * Parse Java 1.5 source code and creates Abstract Syntax Tree classes.
 * </p>
 *
 * @author Júlio Vilmar Gesser
 */
public final class JavaParser {

	private static ASTParser parser;


	private JavaParser() {
		// hide the constructor
	}

    public static CompilationUnit parse(final InputStream in,
                                        final String encoding) throws ParseException {
        return parse(in,encoding,true);
    }

	/**
	 * Parses the Java code contained in the {@link InputStream} and returns a
	 * {@link CompilationUnit} that represents it.
	 * 
	 * @param in
	 *            {@link InputStream} containing Java source code
	 * @param encoding
	 *            encoding of the source code
	 * @return CompilationUnit representing the Java source code
	 * @throws ParseException
	 *             if the source code has parser errors
	 */
	public static CompilationUnit parse(final InputStream in,
			final String encoding, boolean considerComments) throws ParseException {
        try {
            String code = SourcesHelper.streamToString(in, encoding);
            InputStream in1 = SourcesHelper.stringToStream(code);
            CompilationUnit cu = new ASTParser(in1, encoding).CompilationUnit();
            if (considerComments){
                insertComments(cu,code);
            }
            return cu;
        } catch (IOException ioe){
            throw new ParseException(ioe.getMessage());
        }
	}

	/**
	 * Parses the Java code contained in the {@link InputStream} and returns a
	 * {@link CompilationUnit} that represents it.
	 * 
	 * @param in
	 *            {@link InputStream} containing Java source code
	 * @return CompilationUnit representing the Java source code
	 * @throws ParseException
	 *             if the source code has parser errors
	 */
	public static CompilationUnit parse(final InputStream in)
			throws ParseException {
		return parse(in, null,true);
	}

    public static CompilationUnit parse(final File file, final String encoding)
            throws ParseException, IOException {
        return parse(file,encoding,true);
    }

	/**
	 * Parses the Java code contained in a {@link File} and returns a
	 * {@link CompilationUnit} that represents it.
	 * 
	 * @param file
	 *            {@link File} containing Java source code
	 * @param encoding
	 *            encoding of the source code
	 * @return CompilationUnit representing the Java source code
	 * @throws ParseException
	 *             if the source code has parser errors
	 * @throws IOException
	 */
	public static CompilationUnit parse(final File file, final String encoding, boolean considerComments)
			throws ParseException, IOException {
		final FileInputStream in = new FileInputStream(file);
		try {
			return parse(in, encoding, considerComments);
		} finally {
			in.close();
		}
	}

	/**
	 * Parses the Java code contained in a {@link File} and returns a
	 * {@link CompilationUnit} that represents it.
	 * 
	 * @param file
	 *            {@link File} containing Java source code
	 * @return CompilationUnit representing the Java source code
	 * @throws ParseException
	 *             if the source code has parser errors
	 * @throws IOException
	 */
	public static CompilationUnit parse(final File file) throws ParseException,
			IOException {
		return parse(file, null,true);
	}

	public static CompilationUnit parse(final Reader reader, boolean considerComments)
			throws ParseException {
        try {
            String code = SourcesHelper.readerToString(reader);
            Reader reader1 = SourcesHelper.stringToReader(code);
            CompilationUnit cu = new ASTParser(reader1).CompilationUnit();
            if (considerComments){
                insertComments(cu,code);
            }
            return cu;
        } catch (IOException ioe){
            throw new ParseException(ioe.getMessage());
        }
	}

	/**
	 * Parses the Java block contained in a {@link String} and returns a
	 * {@link BlockStmt} that represents it.
	 * 
	 * @param blockStatement
	 *            {@link String} containing Java block code
	 * @return BlockStmt representing the Java block
	 * @throws ParseException
	 *             if the source code has parser errors
	 * @throws IOException
	 */
	public static BlockStmt parseBlock(final String blockStatement)
			throws ParseException {
		StringReader sr = new StringReader(blockStatement);
		BlockStmt result = new ASTParser(sr).Block();
		sr.close();
		return result;
	}

	/**
	 * Parses the Java statement contained in a {@link String} and returns a
	 * {@link Statement} that represents it.
	 * 
	 * @param statement
	 *            {@link String} containing Java statement code
	 * @return Statement representing the Java statement
	 * @throws ParseException
	 *             if the source code has parser errors
	 * @throws IOException
	 */
	public static Statement parseStatement(final String statement)
			throws ParseException {
		StringReader sr = new StringReader(statement);
		Statement stmt = new ASTParser(sr).Statement();
		sr.close();
		return stmt;
	}

	/**
	 * Parses the Java import contained in a {@link String} and returns a
	 * {@link ImportDeclaration} that represents it.
	 * 
	 * @param importDeclaration
	 *            {@link String} containing Java import code
	 * @return ImportDeclaration representing the Java import declaration
	 * @throws ParseException
	 *             if the source code has parser errors
	 * @throws IOException
	 */
	public static ImportDeclaration parseImport(final String importDeclaration)
			throws ParseException {
		StringReader sr = new StringReader(importDeclaration);
		ImportDeclaration id = new ASTParser(sr).ImportDeclaration();
		sr.close();
		return id;
	}

	/**
	 * Parses the Java expression contained in a {@link String} and returns a
	 * {@link Expression} that represents it.
	 * 
	 * @param expression
	 *            {@link String} containing Java expression
	 * @return Expression representing the Java expression
	 * @throws ParseException
	 *             if the source code has parser errors
	 * @throws IOException
	 */
	public static Expression parseExpression(final String expression)
			throws ParseException {
		StringReader sr = new StringReader(expression);
		Expression e = new ASTParser(sr).Expression();
		sr.close();
		return e;
	}

	/**
	 * Parses the Java annotation contained in a {@link String} and returns a
	 * {@link AnnotationExpr} that represents it.
	 * 
	 * @param annotation
	 *            {@link String} containing Java annotation
	 * @return AnnotationExpr representing the Java annotation
	 * @throws ParseException
	 *             if the source code has parser errors
	 * @throws IOException
	 */
	public static AnnotationExpr parseAnnotation(final String annotation)
			throws ParseException {
		StringReader sr = new StringReader(annotation);
		AnnotationExpr ae = new ASTParser(sr).Annotation();
		sr.close();
		return ae;
	}

	/**
	 * Parses the Java body declaration(e.g fields or methods) contained in a
	 * {@link String} and returns a {@link BodyDeclaration} that represents it.
	 * 
	 * @param body
	 *            {@link String} containing Java body declaration
	 * @return BodyDeclaration representing the Java annotation
	 * @throws ParseException
	 *             if the source code has parser errors
	 * @throws IOException
	 */
	public static BodyDeclaration parseBodyDeclaration(final String body)
			throws ParseException {
		StringReader sr = new StringReader(body);
		BodyDeclaration bd = new ASTParser(sr).AnnotationBodyDeclaration();
		sr.close();
		return bd;
	}

    /**
     * Comments are attributed to the thing the comment and are removed from
     * allComments.
     */
    private static void insertCommentsInCu(CompilationUnit cu, CommentsCollection commentsCollection){
        if (commentsCollection.size()==0) return;

        // I should sort all the direct children and the comments, if a comment is the first thing then it
        // a comment to the CompilationUnit
        // FIXME if there is no package it could be also a comment to the following class...
        // so I could use some heuristics in these cases to distinguish the two cases

        List<Comment> comments = commentsCollection.getAll();
        sortByBeginPosition(comments);
        List<Node> children = cu.getChildrenNodes();
        sortByBeginPosition(children);

        if (children.size()==0 || areInOrder(comments.get(0), children.get(0))){
            cu.setComment(comments.get(0));
            comments.remove(0);
        }

        insertCommentsInNode(cu,comments);
    }

    private static boolean areInOrder(Node a, Node b){
        return
                (a.getBeginLine()<b.getBeginLine())
                        || (a.getBeginLine()==b.getBeginLine() && a.getBeginColumn()<b.getBeginColumn() );
    }

    private static <T extends Node> void sortByBeginPosition(List<T> nodes){
        for (int i=0;i<nodes.size();i++){
            for (int j=i+1;j<nodes.size();j++){
                T nodeI = nodes.get(i);
                T nodeJ = nodes.get(j);
                if (!areInOrder(nodeI,nodeJ)){
                    nodes.set(i,nodeJ);
                    nodes.set(j,nodeI);
                }
            }
        }
    }

    /**
     * This method try to attributes the nodes received to child of the node.
     * It returns the node that were not attributed.
     */
    private static void insertCommentsInNode(Node node, List<Comment> commentsToAttribute){
        if (commentsToAttribute.size()==0) return;

        System.out.println("Looking to place "+commentsToAttribute.size()+" comments in "+node.getClass());

        // the comments can:
        // 1) Inside one of the child, then it is the child that have to associate them
        // 2) If they are not inside a child they could be preceeding nothing, a comment or a child
        //    if they preceed a child they are assigned to it, otherweise they remain "orphans"

        List<Node> children = node.getChildrenNodes();
        sortByBeginPosition(children);

        for (Node child : children){
            //System.out.println("Considering if some comments stay in "+child.getClass());
            List<Comment> commentsInsideChild = new LinkedList<Comment>();
            for (Comment c : commentsToAttribute){
                if (child.contains(c)){
                    commentsInsideChild.add(c);
                }
            }
            commentsToAttribute.removeAll(commentsInsideChild);
            insertCommentsInNode(child,commentsInsideChild);
        }

        //System.out.println("Comments not placed in children (node:"+node.getClass()+"): "+commentsToAttribute.size()) ;

        // at this point I create an ordered list of all remaining comments and children
        Comment previousComment = null;
        List<Comment> attributedComments = new LinkedList<Comment>();
        List<Node> childrenAndComments = new LinkedList<Node>();
        childrenAndComments.addAll(children);
        childrenAndComments.addAll(commentsToAttribute);
        sortByBeginPosition(childrenAndComments);

        System.out.println("Children and remaining comments: "+childrenAndComments.size()+". Class "+node.getClass());

        for (Node thing : childrenAndComments){
            System.out.println(" * "+thing.getClass()+" L "+thing.getBeginLine()+" C "+thing.getBeginColumn());
            if (thing instanceof Comment){
                previousComment = (Comment)thing;
            } else {
                if (previousComment!=null){
                    thing.setComment(previousComment);
                    attributedComments.add(previousComment);
                    previousComment = null;
                }
            }
        }

        commentsToAttribute.removeAll(attributedComments);

        // all the remaining are orphan nodes
        for (Comment c : commentsToAttribute){
            node.addOrphanComment(c);
        }
    }

    private static void insertComments(CompilationUnit cu, String code) throws IOException {
        CommentsParser commentsParser = new CommentsParser();
        CommentsCollection allComments = commentsParser.parse(code);

        insertCommentsInCu(cu,allComments);
    }

    private static void placeOrphanComments(CompilationUnit cu, List<Comment> orphanComments){
        for (Comment comment : orphanComments){
            placeOrphanComment(cu,comment);
        }
    }

    private static void placeOrphanComment(Node node,Comment comment){
        for (Node child : node.getChildrenNodes()){
            if (child.contains(comment)){
                placeOrphanComment(child,comment);
                return;
            }
        }
        node.addOrphanComment(comment);
    }

}
