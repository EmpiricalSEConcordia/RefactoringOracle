package com.intellij.xml.util.documentation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 24.12.2004
 * Time: 23:55:20
 * To change this template use File | Settings | File Templates.
 */
public class XHtmlDocumentationProvider extends HtmlDocumentationProvider {
  public XHtmlDocumentationProvider(Project project) {
    super(project);
  }

  protected String generateDocForHtml(PsiElement element, boolean ommitHtmlSpecifics, XmlTag context) {
    return super.generateDocForHtml(element, true, context);
  }

  protected XmlTag findTagContext(PsiElement context) {
    XmlTag tagBeforeWhiteSpace = findTagBeforeWhiteSpace(context);
    if (tagBeforeWhiteSpace!=null) return tagBeforeWhiteSpace;
    return super.findTagContext(context);
  }

  private XmlTag findTagBeforeWhiteSpace(PsiElement context) {
    if (context instanceof PsiWhiteSpace) {
      PsiElement parent = context.getParent();
      if (parent instanceof XmlText) {
        PsiElement prevSibling = parent.getPrevSibling();
        if (prevSibling instanceof XmlTag) return (XmlTag)prevSibling;
      } else if (parent instanceof XmlTag) {
        return (XmlTag)parent;
      }
    }

    return null;
  }

  protected boolean isAttributeContext(PsiElement context) {
    if (findTagBeforeWhiteSpace(context)!=null) return false;

    return super.isAttributeContext(context);
  }
}
