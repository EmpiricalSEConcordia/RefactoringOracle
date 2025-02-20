package com.intellij.pom.xml.impl.events;

import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.XmlAttributeSet;
import com.intellij.pom.xml.impl.XmlAspectChangeSetImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

public class XmlAttributeSetImpl implements XmlAttributeSet {
  private final String myName;
  private final String myValue;
  private final XmlTag myTag;

  public XmlAttributeSetImpl(XmlTag xmlTag, String name, String value) {
    myName = name;
    myValue = value;
    myTag = xmlTag;
  }

  public String getName() {
    return myName;
  }

  public String getValue() {
    return myValue;
  }

  public XmlTag getTag() {
    return myTag;
  }

  public static PomModelEvent createXmlAttributeSet(PomModel model, XmlTag xmlTag, String name, String value) {
    final PomModelEvent event = new PomModelEvent(model);
    final XmlAspectChangeSetImpl xmlAspectChangeSet = new XmlAspectChangeSetImpl(model, PsiTreeUtil.getParentOfType(xmlTag, XmlFile.class));
    xmlAspectChangeSet.add(new XmlAttributeSetImpl(xmlTag, name, value));
    event.registerChangeSet(model.getModelAspect(XmlAspect.class), xmlAspectChangeSet);
    return event;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "Attribute \"" + getName() + "\" for tag \"" + getTag().getName() + "\" set to \"" + getValue() + "\"";
  }

  public void accept(XmlChangeVisitor visitor) {
    visitor.visitXmlAttributeSet(this);
  }
}
