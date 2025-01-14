package com.intellij.pom.xml.impl.events;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.events.XmlChange;
import com.intellij.pom.xml.events.XmlTextChanged;
import com.intellij.pom.xml.impl.XmlAspectChangeSetImpl;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlText;

public class XmlTextChangedImpl implements XmlTextChanged {
  private final String myOldText;
  private final XmlText myText;
  public XmlTextChangedImpl(XmlText xmlText, String oldText) {
    myOldText = oldText;
    myText = xmlText;
  }

  public String getOldText() {
    return myOldText;
  }

  public XmlText getText() {
    return myText;
  }

  public static PomModelEvent createXmlTextChanged(PomModel source, XmlText xmlText, String oldText) {
    final PomModelEvent event = new PomModelEvent(source);
    final XmlAspectChangeSetImpl xmlAspectChangeSet = new XmlAspectChangeSetImpl(source, PsiTreeUtil.getParentOfType(xmlText, XmlFile.class));
    xmlAspectChangeSet.add(new XmlTextChangedImpl(xmlText, oldText));
    event.registerChangeSet(source.getModelAspect(XmlAspect.class), xmlAspectChangeSet);
    return event;
  }

  public String toString() {
    return "text changed to '" + StringUtil.escapeStringCharacters(myText.getValue()) + "' was: '"
           + StringUtil.escapeStringCharacters(myOldText) + "'";
  }

  public void accept(XmlChangeVisitor visitor) {
    visitor.visitXmlTextChanged(this);
  }
}
