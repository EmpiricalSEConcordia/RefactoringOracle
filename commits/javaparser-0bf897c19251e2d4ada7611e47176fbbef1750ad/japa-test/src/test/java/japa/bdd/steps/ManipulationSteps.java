package japa.bdd.steps;


import japa.parser.ASTHelper;
import japa.parser.JavaParser;
import japa.parser.ParseException;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.Node;
import japa.parser.ast.PackageDeclaration;
import japa.parser.ast.body.*;
import japa.parser.ast.expr.*;
import japa.parser.ast.stmt.BlockStmt;
import japa.parser.ast.stmt.Statement;
import japa.parser.ast.stmt.TryStmt;
import japa.parser.ast.type.PrimitiveType;
import japa.parser.ast.type.VoidType;
import japa.parser.ast.visitor.VoidVisitorAdapter;
import org.jbehave.core.annotations.Alias;
import org.jbehave.core.annotations.Given;
import org.jbehave.core.annotations.Then;
import org.jbehave.core.annotations.When;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class ManipulationSteps {

    /* Fields used to maintain step state within this step class */
    private BlockStmt blockStmt;
    private Statement statement;
    private TryStmt tryStmt;
    private List<VariableDeclarationExpr> variableDeclarationExprList;
    private ChangeMethodNameToUpperCaseVisitor changeMethodNameToUpperCaseVisitor;
    private AddNewIntParameterCalledValueVisitor addNewIntParameterCalledValueVisitor;

    /* Map that maintains shares state across step classes.  If manipulating the objects in the map you must update the state */
    private Map<String, Object> state;

    public ManipulationSteps(Map<String, Object> state){
        this.state = state;
    }

    @Given("a BlockStmt")
    public void givenABlockStatement() {
        blockStmt = new BlockStmt();
    }

    @Given("a Statement")
    public void givenAStatement() {
        statement = null;
    }

    @Given("a TryStmt")
    public void givenATryStmt() {
        tryStmt = new TryStmt();
    }

    @Given("a List of VariableDeclarations")
    public void givenAListOfVariableDeclarations() {
        variableDeclarationExprList = new ArrayList<VariableDeclarationExpr>();
        variableDeclarationExprList.add(new VariableDeclarationExpr());
        variableDeclarationExprList.add(new VariableDeclarationExpr());
    }

    @Given("a ChangeNameToUpperCaseVisitor")
    public void givenAChangeNameToUpperCaseVisitor() {
        changeMethodNameToUpperCaseVisitor = new ChangeMethodNameToUpperCaseVisitor();
    }

    @Given("a AddNewIntParameterCalledValueVisitor")
    public void givenAAddNewParameterCalledValueVisitor() {
        addNewIntParameterCalledValueVisitor = new AddNewIntParameterCalledValueVisitor();
    }

    @When("is the String \"$value\" is parsed by the JavaParser using parseBlock")
    public void whenIsTheStringIsParsedByTheJavaParser(String value) throws ParseException {
        blockStmt = JavaParser.parseBlock(value);
    }

    @When("is the String \"$value\" is parsed by the JavaParser using parseStatement")
    public void whenIsTheStringIsParsedByTheJavaParserUsingParseStatement(String value) throws ParseException {
        statement= JavaParser.parseStatement(value);
    }

    @When("the List of VariableDeclarations are set as the resources on TryStmt")
    public void whenTheListOfVariableDeclarationsAreSetAsTheResourcesOnTryStmt() {
        tryStmt.setResources(variableDeclarationExprList);
    }

    @When("null is set as the resources on TryStmt")
    public void whenNullIsSetAsTheResourcesOnTryStmt() {
        tryStmt.setResources(null);
    }

    @When("the package declaration is set to \"$packageName\"")
    public void whenThePackageDeclarationIsSetTo(String packageName) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        compilationUnit.setPackage(new PackageDeclaration(new NameExpr(packageName)));
        state.put("cu1", compilationUnit);
    }

    @When("a public class called \"$className\" is added to the CompilationUnit")
    public void whenAClassCalledIsAddedToTheCompilationUnit(String className) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        TypeDeclaration type = new ClassOrInterfaceDeclaration(ModifierSet.PUBLIC, false, "CreateClass");
        compilationUnit.setTypes(Arrays.asList(type));
        state.put("cu1", compilationUnit);
    }

    @When("a public static method called \"$methodName\" returning void is added to class $position in the compilation unit")
    public void whenAStaticMethodCalledReturningIsAddedToClassInTheCompilationUnit(String methodName, int position) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        TypeDeclaration type = compilationUnit.getTypes().get(position -1);
        MethodDeclaration method = new MethodDeclaration(ModifierSet.PUBLIC, new VoidType(), methodName);
        method.setModifiers(ModifierSet.addModifier(method.getModifiers(), ModifierSet.STATIC));
        ASTHelper.addMember(type, method);
        state.put("cu1", compilationUnit);
    }

    @When("$typeName varargs called \"$parameterName\" are added to method $methodPosition in class $classPosition")
    public void whenVarargsCalledAreAddedToMethodInClass(String typeName, String parameterName, int methodPosition, int classPosition) {
        MethodDeclaration method = getMethodByPositionAndClassPosition(methodPosition, classPosition);
        Parameter param = ASTHelper.createParameter(ASTHelper.createReferenceType(typeName, 0), parameterName);
        param.setVarArgs(true);
        ASTHelper.addParameter(method, param);
    }

    @When("a BlockStmt is added to method $methodPosition in class $classPosition")
    public void whenABlockStmtIsAddedToMethodInClass(int methodPosition, int classPosition) {
        MethodDeclaration method = getMethodByPositionAndClassPosition(methodPosition, classPosition);
        method.setBody(new BlockStmt());
    }

    @When("$className.$fieldName.$methodName(\"$stringValue\"); is added to the body of method $methodPosition in class $classPosition")
    public void whenHelloWorldIsAddedToTheBodyOfMethodInClass(String className, String fieldName, String methodName, String stringValue,
                                                              int methodPosition, int classPosition) {
        MethodDeclaration method = getMethodByPositionAndClassPosition(methodPosition, classPosition);
        NameExpr clazz = new NameExpr(className);
        FieldAccessExpr field = new FieldAccessExpr(clazz, fieldName);
        MethodCallExpr call = new MethodCallExpr(field, methodName);
        ASTHelper.addArgument(call, new StringLiteralExpr(stringValue));
        ASTHelper.addStmt(method.getBody(), call);
    }

    @When("method $methodPosition in class $classPosition has it's name converted to uppercase")
    public void whenMethodInClassHasItsNameConvertedToUppercase(int methodPosition, int classPosition) {
        MethodDeclaration method = getMethodByPositionAndClassPosition(methodPosition, classPosition);
        method.setName(method.getName().toUpperCase());
    }

    @When("method $methodPosition in class $classPosition has an int parameter called \"$paramName\" added")
    public void whenMethodInClassHasAnIntArgumentCalledAdded(int methodPosition, int classPosition, String paramName) {
        MethodDeclaration method = getMethodByPositionAndClassPosition(methodPosition, classPosition);
        ASTHelper.addParameter(method, ASTHelper.createParameter(ASTHelper.INT_TYPE, paramName));
    }

    @When("the ChangeNameToUpperCaseVisitor visits to compilation unit")
    public void whenTheVisitorVisitsToCompilationUnit() {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        changeMethodNameToUpperCaseVisitor.visit(compilationUnit, null);
        state.put("cu1", compilationUnit);
    }

    @When("the AddNewIntParameterCalledValueVisitor visits to compilation unit")
    public void whenTheAddNewParameterCalledValueVisitorVisitsToCompilationUnit() {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        addNewIntParameterCalledValueVisitor.visit(compilationUnit, null);
        state.put("cu1", compilationUnit);
    }

    @Then("is not equal to null")
    public void thenIsNotEqualToNull() {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        assertNotEquals(compilationUnit, null);
    }

    @Then("is not equal to $value")
    public void thenIsNotEqualTo(String value) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        assertNotEquals(compilationUnit, value);
    }

    @Then("Statement $position in BlockStmt toString is \"$expectedContent\"")
    public void thenTheBlockStmtContentIs(int position, String expectedContent) {
        Statement statementUnderTest = blockStmt.getStmts().get(position-1);
        assertThat(statementUnderTest.toString(), is(expectedContent));
    }

    @Then("Statement toString is \"$expectedContent\"")
    public void thenStatementToStringIsxXy(String expectedContent) {
        assertThat(statement.toString(), is(expectedContent));
    }

    @Then("all the VariableDeclarations parent is the TryStmt")
    public void thenAllTheVariableDeclarationsParentIsTheTryStmt() {
        for(VariableDeclarationExpr expr : variableDeclarationExprList){
            assertThat(expr.getParentNode(), is((Node)tryStmt));
        }
    }

    @Then("the TryStmt has no child nodes")
    public void thenTheTryStmtHasNotChildNodes() {
        assertThat(tryStmt.getChildrenNodes().size(), is(0));
    }

    @Then("method $methodPosition in class $classPosition has the name \"$expectedName\"")
    public void thenMethodInClassHasTheName(int methodPosition, int classPosition, String expectedName) {
        MethodDeclaration method = getMethodByPositionAndClassPosition(methodPosition, classPosition);
        assertThat(method.getName(), is(expectedName));
    }

    @Then("method $methodPosition in class $classPosition has $expectedCount parameters")
    @Alias("method $methodPosition in class $classPosition has $expectedCount parameter")
    public void thenMethodInClassHasArguments(int methodPosition, int classPosition, int expectedCount) {
        MethodDeclaration method = getMethodByPositionAndClassPosition(methodPosition, classPosition);

        assertThat(method.getParameters().size(), is(expectedCount));
    }

    @Then("method $methodPosition in class $classPosition parameter $parameterPosition is type int called \"$expectedName\"")
    public void thenMethodInClassParameterIsTypeIntCalled(int methodPosition, int classPosition, int parameterPosition, String expectedName) {
        MethodDeclaration method = getMethodByPositionAndClassPosition(methodPosition, classPosition);
        Parameter parameter = method.getParameters().get(parameterPosition -1);
        assertThat((PrimitiveType)parameter.getType(), is(ASTHelper.INT_TYPE));
        assertThat(parameter.getId().getName(), is(expectedName));
    }

    private MethodDeclaration getMethodByPositionAndClassPosition(int methodPosition, int classPosition) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        TypeDeclaration type = compilationUnit.getTypes().get(classPosition -1);
        return (MethodDeclaration) type.getMembers().get(methodPosition -1);
    }

    private static class ChangeMethodNameToUpperCaseVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            n.setName(n.getName().toUpperCase());
        }
    }

    private static class AddNewIntParameterCalledValueVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            ASTHelper.addParameter(n, ASTHelper.createParameter(ASTHelper.INT_TYPE, "value"));
        }
    }
}

