package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class PsiTryStatementImpl extends CompositePsiElement implements PsiTryStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiTryStatementImpl");
  private volatile PsiParameter[] myCachedCatchParameters = null;

  public void clearCaches() {
    super.clearCaches();
    myCachedCatchParameters = null;
  }

  public PsiTryStatementImpl() {
    super(TRY_STATEMENT);
  }

  public PsiCodeBlock getTryBlock() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.TRY_BLOCK);
  }

  @NotNull
  public PsiCodeBlock[] getCatchBlocks() {
    ASTNode tryBlock = SourceTreeToPsiMap.psiElementToTree(getTryBlock());
    if (tryBlock != null) {
      PsiCatchSection[] catchSections = getCatchSections();
      if (catchSections.length == 0) return PsiCodeBlock.EMPTY_ARRAY;
      boolean lastIncomplete = catchSections[catchSections.length - 1].getCatchBlock() == null;
      PsiCodeBlock[] blocks = new PsiCodeBlock[lastIncomplete ? catchSections.length - 1 : catchSections.length];
      for (int i = 0; i < blocks.length; i++) {
        blocks[i] = catchSections[i].getCatchBlock();
      }
      return blocks;
    }
    return PsiCodeBlock.EMPTY_ARRAY;
  }

  @NotNull
  public PsiParameter[] getCatchBlockParameters() {
    PsiParameter[] catchParameters = myCachedCatchParameters;
    if (catchParameters == null) {
      PsiCatchSection[] catchSections = getCatchSections();
      if (catchSections.length == 0) return PsiParameter.EMPTY_ARRAY;
      boolean lastIncomplete = catchSections[catchSections.length - 1].getCatchBlock() == null;
      int limit = lastIncomplete ? catchSections.length - 1 : catchSections.length;
      ArrayList<PsiParameter> parameters = new ArrayList<PsiParameter>();
      for (int i = 0; i < limit; i++) {
        PsiParameter parameter = catchSections[i].getParameter();
        if (parameter != null) parameters.add(parameter);
      }
      myCachedCatchParameters = catchParameters = parameters.toArray(new PsiParameter[parameters.size()]);
    }
    return catchParameters;
  }

  @NotNull
  public PsiCatchSection[] getCatchSections() {
    return getChildrenAsPsiElements(CATCH_SECTION_BIT_SET, PSI_CATCH_SECTION_ARRAYS_CONSTRUCTOR);
  }

  public PsiCodeBlock getFinallyBlock() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.FINALLY_BLOCK);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.TRY_KEYWORD:
        return TreeUtil.findChild(this, TRY_KEYWORD);

      case ChildRole.TRY_BLOCK:
        return TreeUtil.findChild(this, CODE_BLOCK);

      case ChildRole.FINALLY_KEYWORD:
        return TreeUtil.findChild(this, FINALLY_KEYWORD);

      case ChildRole.FINALLY_BLOCK:
        {
          ASTNode finallyKeyword = findChildByRole(ChildRole.FINALLY_KEYWORD);
          if (finallyKeyword == null) return null;
          for(ASTNode child = finallyKeyword.getTreeNext(); child != null; child = child.getTreeNext()){
            if (child.getElementType() == CODE_BLOCK){
              return child;
            }
          }
          return null;
        }
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == TRY_KEYWORD) {
      return ChildRole.TRY_KEYWORD;
    }
    else if (i == FINALLY_KEYWORD) {
      return ChildRole.FINALLY_KEYWORD;
    }
    else if (i == CATCH_SECTION) {
      return ChildRole.CATCH_SECTION;
    }
    else {
      if (child.getElementType() == CODE_BLOCK) {
        int role = getChildRole(child, ChildRole.TRY_BLOCK);
        if (role != ChildRole.NONE) return role;
        return getChildRole(child, ChildRole.FINALLY_BLOCK);
      }
      else {
        return ChildRole.NONE;
      }
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTryStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiTryStatement";
  }
}
