/* Generated By:JJTree: Do not edit this line. ASTNestedClassDeclaration.java */

package net.sourceforge.pmd.ast;

public class ASTNestedClassDeclaration extends AccessNode {
  public ASTNestedClassDeclaration(int id) {
    super(id);
  }

  public ASTNestedClassDeclaration(JavaParser p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(JavaParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }
}
