/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.find.findUsages;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.usageView.UsageViewUtil;

import javax.swing.*;

/**
 * @author max
 */
public class CommonFindUsagesDialog extends AbstractFindUsagesDialog {
  private final PsiElement myPsiElement;

  public CommonFindUsagesDialog(PsiElement element,
                                Project project,
                                FindUsagesOptions findUsagesOptions,
                                boolean toShowInNewTab,
                                boolean mustOpenInNewTab,
                                boolean isSingleFile, FindUsagesHandler handler) {
    super(project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, isTextSearch(element, isSingleFile, handler), !isSingleFile && !element.getManager().isInProject(element));
    myPsiElement = element;
    init();
  }

  private static boolean isTextSearch(PsiElement element, boolean isSingleFile, FindUsagesHandler handler) {
    return FindUsagesUtil.isSearchForTextOccurencesAvailable(element, isSingleFile, handler);
  }

  @Override
  protected boolean isInFileOnly() {
    return super.isInFileOnly() ||
           myPsiElement != null && myPsiElement.getManager().getSearchHelper().getUseScope(myPsiElement)instanceof LocalSearchScope;
  }

  protected JPanel createFindWhatPanel() {
    return null;
  }

  protected JComponent getPreferredFocusedControl() {
    return null;
  }

  public String getLabelText() {
    return StringUtil.capitalize(UsageViewUtil.getType(myPsiElement)) + " " + UsageViewUtil.getDescriptiveName(myPsiElement);
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(FindUsagesManager.getHelpID(myPsiElement));
  }
}
