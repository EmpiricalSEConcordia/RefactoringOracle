package com.intellij.application.options;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class CodeStyleImportsConfigurable extends BaseConfigurable {
  private CodeStyleImportsPanel myPanel;
  private CodeStyleSettings mySettings;

  public CodeStyleImportsConfigurable(CodeStyleSettings settings) {
    mySettings = settings;
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public JComponent createComponent() {
    myPanel = new CodeStyleImportsPanel(mySettings);
    return myPanel;
  }

  public String getDisplayName() {
    return "Imports";
  }

  public Icon getIcon() {
    return null;
  }

  public void reset() {
    myPanel.reset();
  }

  public void apply() {
    myPanel.apply();
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public String getHelpTopic() {
    return "preferences.sourceCode.imports";
  }
}