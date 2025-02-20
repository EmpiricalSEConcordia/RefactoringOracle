package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlAttributeDecl;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlEnumeratedType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Mike
 */
public class XmlAttributeDeclImpl extends XmlElementImpl implements XmlAttributeDecl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlAttributeDeclImpl");

  public XmlAttributeDeclImpl() {
    super(XML_ATTRIBUTE_DECL);
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == XML_NAME) {
      return ChildRole.XML_NAME;
    }
    else if (i == XML_ATT_REQUIRED) {
      return ChildRole.XML_ATT_REQUIRED;
    }
    else if (i == XML_ATT_FIXED) {
      return ChildRole.XML_ATT_FIXED;
    }
    else if (i == XML_ATT_IMPLIED) {
      return ChildRole.XML_ATT_IMPLIED;
    }
    else if (i == XML_ATTRIBUTE_VALUE) {
      return ChildRole.XML_DEFAULT_VALUE;
    }
    else if (i == XML_ENUMERATED_TYPE) {
      return ChildRole.XML_ENUMERATED_TYPE;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public XmlElement getNameElement() {
    return (XmlElement)findElementByTokenType(XML_NAME);
  }

  public boolean isAttributeRequired() {
    return findElementByTokenType(XML_ATT_REQUIRED) != null;
  }

  public boolean isAttributeFixed() {
    return findElementByTokenType(XML_ATT_FIXED) != null;
  }

  public boolean isAttributeImplied() {
    return findElementByTokenType(XML_ATT_IMPLIED) != null;
  }

  public XmlAttributeValue getDefaultValue() {
    return (XmlAttributeValue)findElementByTokenType(XML_ATTRIBUTE_VALUE);
  }

  public boolean isEnumerated() {
    return findElementByTokenType(XML_ENUMERATED_TYPE) != null;
  }

  public XmlElement[] getEnumeratedValues() {
    XmlEnumeratedType enumeratedType = (XmlEnumeratedType)findElementByTokenType(XML_ENUMERATED_TYPE);
    if (enumeratedType != null){
      return enumeratedType.getEnumeratedValues();
    }
    else{
      return XmlElement.EMPTY_ARRAY;
    }
  }

  public boolean isIdAttribute() {
    final PsiElement elementType = findElementType();

    return elementType!=null && elementType.getText().equals("ID");
  }

  private PsiElement findElementType() {
    final PsiElement elementName = findElementByTokenType(XML_NAME);
    final PsiElement nextSibling = (elementName!=null)?elementName.getNextSibling():null;
    final PsiElement elementType = (nextSibling instanceof PsiWhiteSpace)?nextSibling.getNextSibling():nextSibling;

    return elementType;
  }

  public boolean isIdRefAttribute() {
    final PsiElement elementType = findElementType();

    return elementType!=null && elementType.getText().equals("IDREF");
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough() {
    return true;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    return null;
  }

  public String getName() {
    XmlElement name = getNameElement();
    return (name != null )? name.getText():null;
  }
}
