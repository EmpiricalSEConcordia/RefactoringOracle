package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author mike
 */
public class PsiDocTagValueImpl extends CompositePsiElement implements PsiDocTagValue {
  public PsiDocTagValueImpl() {
    super(DOC_TAG_VALUE_TOKEN);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitDocTagValue(this);
  }

  public PsiReference getReference() {
    PsiDocTag docTag = PsiTreeUtil.getParentOfType(this, PsiDocTag.class);
    final String name = docTag.getName();
    final JavadocManager manager = getManager().getJavadocManager();
    final JavadocTagInfo info = manager.getTagInfo(name);
    if (info == null) return null;

    return info.getReference(this);
  }
}
