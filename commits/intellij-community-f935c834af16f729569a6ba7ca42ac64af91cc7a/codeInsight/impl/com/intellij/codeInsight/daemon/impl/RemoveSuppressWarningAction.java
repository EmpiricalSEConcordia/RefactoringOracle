package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoveSuppressWarningAction implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.RemoveSuppressWarningAction");

  private final String myID;
  private final String myProblemLine;

  public RemoveSuppressWarningAction(final String ID, final String problemLine) {
    myID = ID;
    myProblemLine = problemLine;
  }

  public RemoveSuppressWarningAction(String id) {
    final int idx = id.indexOf(";");
    if (idx > -1) {
      myID = id.substring(0, idx);
      myProblemLine = id.substring(idx);
    }
    else {
      myID = id;
      myProblemLine = null;
    }
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.suppression.action.family");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    try {
      if (element instanceof PsiIdentifier) {
        if (!CodeInsightUtil.prepareFileForWrite(element.getContainingFile())) return;
        final PsiIdentifier identifier = (PsiIdentifier)element;
        final PsiDocCommentOwner commentOwner = PsiTreeUtil.getParentOfType(identifier, PsiDocCommentOwner.class);
        if (commentOwner != null) {
          final PsiElement psiElement = GlobalInspectionContextImpl.getElementMemberSuppressedIn(commentOwner, myID);
          if (psiElement instanceof PsiAnnotation) {
            removeFromAnnotation((PsiAnnotation)psiElement);
          } else if (psiElement instanceof PsiDocComment) {
            removeFromJavaDoc((PsiDocComment)psiElement);
          } else { //try to remove from all comments
            final Set<PsiComment> comments = new HashSet<PsiComment>();
            commentOwner.accept(new PsiRecursiveElementVisitor() {
              @Override public void visitComment(final PsiComment comment) {
                super.visitComment(comment);
                if (comment.getText().contains(myID)) {
                  comments.add(comment);
                }
              }
            });
            for (PsiComment comment : comments) {
              try {
                removeFromComment(comment, comments.size() > 1);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public String getName() {
    return QuickFixBundle.message("remove.suppression.action.name", myID);
  }

  private void removeFromComment(final PsiComment comment, final boolean checkLine) throws IncorrectOperationException {
    if (checkLine) {
      final PsiStatement statement = PsiTreeUtil.getNextSiblingOfType(comment, PsiStatement.class);
      if (statement != null && !Comparing.strEqual(statement.getText(), myProblemLine)) return;
    }
    String newText = removeFromElementText(comment);
    if (newText != null) {
      if (newText.length() == 0) {
        comment.delete();
      }
      else {
        PsiComment newComment = comment.getManager().getElementFactory().createCommentFromText("// " + GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME+" "+newText, comment);
        comment.replace(newComment);
      }
    }
  }

  private void removeFromJavaDoc(PsiDocComment docComment) throws IncorrectOperationException {
    PsiDocTag tag = docComment.findTagByName(GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME);
    if (tag == null) return;
    String newText = removeFromElementText(tag.getDataElements());
    if (newText != null && newText.length() == 0) {
      tag.delete();
    }
    else if (newText != null) {
      newText = "@" + GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME + " " + newText;
      PsiDocTag newTag = tag.getManager().getElementFactory().createDocTagFromText(newText, tag);
      tag.replace(newTag);
    }
  }

  @Nullable
  private String removeFromElementText(final PsiElement... elements) {
    String text = "";
    for (PsiElement element : elements) {
      text += StringUtil.trimStart(element.getText(), "//").trim();
    }
    text = StringUtil.trimStart(text, "@").trim();
    text = StringUtil.trimStart(text, GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME).trim();
    List<String> ids = StringUtil.split(text, ",");
    int i = ArrayUtil.find(ids.toArray(), myID);
    if (i==-1) return null;
    ids.remove(i);
    return StringUtil.join(ids, ",");
  }

  private void removeFromAnnotation(final PsiAnnotation annotation) throws IncorrectOperationException {
    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      PsiAnnotationMemberValue value = attribute.getValue();
      if (value instanceof PsiArrayInitializerMemberValue) {
        PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
        for (PsiAnnotationMemberValue initializer : initializers) {
          if (removeFromValue(annotation, initializer, initializers.length==1)) return;
        }
      }
      if (removeFromValue(annotation, value, attributes.length==1)) return;
    }
  }

  private boolean removeFromValue(final PsiAnnotationMemberValue parent, final PsiAnnotationMemberValue value, final boolean removeParent) throws IncorrectOperationException {
    String text = value.getText();
    text = StringUtil.trimStart(text, "\"");
    text = StringUtil.trimEnd(text, "\"");
    if (myID.equals(text)) {
      if (removeParent) {
        parent.delete();
      }
      else {
        value.delete();
      }
      return true;
    }
    return false;
  }
}
