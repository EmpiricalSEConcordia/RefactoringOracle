package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.hint.PrevNextParameterHandler;
import com.intellij.openapi.editor.actionSystem.EditorAction;

/**
 * @author ven
 */
public class PrevParameterAction extends EditorAction {
  public PrevParameterAction() {
    super(new PrevNextParameterHandler(false));
  }
}
