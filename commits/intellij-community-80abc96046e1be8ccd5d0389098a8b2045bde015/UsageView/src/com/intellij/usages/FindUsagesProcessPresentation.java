/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Factory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 21, 2004
 * Time: 9:15:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class FindUsagesProcessPresentation {
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static final String NAME_WITH_MNEMONIC_KEY = "NameWithMnemonic";

  private List<Action> myNotFoundActions;
  private boolean myShowPanelIfOnlyOneUsage;
  private boolean myShowNotFoundMessage;
  private Factory<ProgressIndicator> myProgressIndicatorFactory;

  public void addNotFoundAction(Action _action) {
    if (myNotFoundActions == null) myNotFoundActions = new ArrayList<Action>();
    myNotFoundActions.add(_action);
  }

  public List<Action> getNotFoundActions() {
    return myNotFoundActions;
  }

  public boolean isShowNotFoundMessage() {
    return myShowNotFoundMessage;
  }

  public void setShowNotFoundMessage(final boolean showNotFoundMessage) {
    myShowNotFoundMessage = showNotFoundMessage;
  }

  public boolean isShowPanelIfOnlyOneUsage() {
    return myShowPanelIfOnlyOneUsage;
  }

  public void setShowPanelIfOnlyOneUsage(final boolean showPanelIfOnlyOneUsage) {
    myShowPanelIfOnlyOneUsage = showPanelIfOnlyOneUsage;
  }

  public Factory<ProgressIndicator> getProgressIndicatorFactory() {
    return myProgressIndicatorFactory;
  }

  public void setProgressIndicatorFactory(final Factory<ProgressIndicator> progressIndicatorFactory) {
    myProgressIndicatorFactory = progressIndicatorFactory;
  }
}

