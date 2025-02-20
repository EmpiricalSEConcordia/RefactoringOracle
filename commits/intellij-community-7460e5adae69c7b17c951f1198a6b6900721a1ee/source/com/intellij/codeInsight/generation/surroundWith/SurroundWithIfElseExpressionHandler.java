
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.jsp.JspFile;
import com.intellij.util.IncorrectOperationException;

class SurroundWithIfElseExpressionHandler implements SurroundExpressionHandler{
  public boolean isApplicable(PsiExpression expr) {
    PsiType type = expr.getType();
    if (PsiType.BOOLEAN != type) return false;
    PsiElement parent = expr.getParent();
    if (!(parent instanceof PsiExpressionStatement)) return false;
    if (!(parent.getParent() instanceof PsiCodeBlock) && !(parent.getParent() instanceof JspFile)) return false;
    return true;
  }

  public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
    PsiManager manager = expr.getManager();
    PsiElementFactory factory = manager.getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    String text = "if(a){\nst;\n}else{\n}";
    PsiIfStatement ifStatement = (PsiIfStatement)factory.createStatementFromText(text, null);
    ifStatement = (PsiIfStatement)codeStyleManager.reformat(ifStatement);

    ifStatement.getCondition().replace(expr);

    PsiExpressionStatement statement = (PsiExpressionStatement)expr.getParent();
    ifStatement = (PsiIfStatement)statement.replace(ifStatement);

    PsiCodeBlock block = ((PsiBlockStatement)ifStatement.getThenBranch()).getCodeBlock();
    TextRange range = block.getStatements()[0].getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    return new TextRange(range.getStartOffset(), range.getStartOffset());
  }
}