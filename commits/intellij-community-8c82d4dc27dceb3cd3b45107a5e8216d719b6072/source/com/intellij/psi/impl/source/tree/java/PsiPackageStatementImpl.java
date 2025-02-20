package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class PsiPackageStatementImpl extends CompositePsiElement implements PsiPackageStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiPackageStatementImpl");

  public PsiPackageStatementImpl() {
    super(PACKAGE_STATEMENT);
  }

  public PsiJavaCodeReferenceElement getPackageReference() {
    return (PsiJavaCodeReferenceElement)findChildByRoleAsPsiElement(ChildRole.PACKAGE_REFERENCE);
  }

  public String getPackageName() {
    PsiJavaCodeReferenceElement ref = getPackageReference();
    return SourceUtil.getTextSkipWhiteSpaceAndComments(SourceTreeToPsiMap.psiElementToTree(ref));
  }

  public PsiModifierList getAnnotationList() {
    return (PsiModifierList)findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.PACKAGE_KEYWORD:
        return TreeUtil.findChild(this, PACKAGE_KEYWORD);

      case ChildRole.PACKAGE_REFERENCE:
        return TreeUtil.findChild(this, JAVA_CODE_REFERENCE);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, SEMICOLON);

      case ChildRole.MODIFIER_LIST:
        return TreeUtil.findChild(this, MODIFIER_LIST);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == PACKAGE_KEYWORD) {
      return ChildRole.PACKAGE_KEYWORD;
    }
    else if (i == JAVA_CODE_REFERENCE) {
      return ChildRole.PACKAGE_REFERENCE;
    }
    else if (i == SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitPackageStatement(this);
  }

  public String toString() {
    return "PsiPackageStatement:" + getPackageName();
  }
}
