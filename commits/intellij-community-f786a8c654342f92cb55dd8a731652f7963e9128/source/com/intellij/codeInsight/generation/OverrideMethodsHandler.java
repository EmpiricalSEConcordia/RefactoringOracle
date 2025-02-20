package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

public class OverrideMethodsHandler implements CodeInsightActionHandler{
  public final void invoke(final Project project, final Editor editor, PsiFile file) {
    Document document = editor.getDocument();
    if (!file.isWritable()){
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(document, project)){
        return;
      }
    }
    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, true);
    if (aClass != null) {
      OverrideImplementUtil.chooseAndOverrideMethods(project, editor, aClass);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}