package com.github.javaparser.junit.remove;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

public class NodeRemovalTest {
	CompilationUnit cu;

	@Before
	public void setup() {
		cu = new CompilationUnit();
	}

	@After
	public void teardown() {
		cu = null;
	}

	@Test
	public void testRemoveClassFromCompilationUnit() {
		ClassOrInterfaceDeclaration testClass = cu.addClass("test");
		assertEquals(1, cu.getTypes().size());
		boolean remove = testClass.remove();
		assertEquals(true, remove);
		assertEquals(0, cu.getTypes().size());
	}

	@Test
	public void testRemoveFieldFromClass() {
		ClassOrInterfaceDeclaration testClass = cu.addClass("test");

		FieldDeclaration addField = testClass.addField(String.class, "test");
		assertEquals(1, testClass.getMembers().size());
		boolean remove = addField.remove();
		assertEquals(true, remove);
		assertEquals(0, testClass.getMembers().size());
	}

	@Test
	public void testRemoveStatementFromMethodBody() {
		ClassOrInterfaceDeclaration testClass = cu.addClass("testC");

		MethodDeclaration addMethod = testClass.addMethod("testM");
		BlockStmt methodBody = addMethod.createBody();
		Statement addStatement = methodBody.addAndGetStatement("test");
		assertEquals(1, methodBody.getStatements().size());
		boolean remove = addStatement.remove();
		assertEquals(true, remove);
		assertEquals(0, methodBody.getStatements().size());
	}
}
