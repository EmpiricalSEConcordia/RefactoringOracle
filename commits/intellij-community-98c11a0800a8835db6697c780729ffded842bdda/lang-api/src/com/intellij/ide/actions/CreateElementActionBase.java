/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The base class for actions which create new file elements.
 *
 * @since 5.1
 */
public abstract class CreateElementActionBase extends AnAction {
  protected CreateElementActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  /**
   * @return created elements. Never null.
   */
  @NotNull
  protected abstract PsiElement[] invokeDialog(Project project, PsiDirectory directory);

  protected abstract void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException;

  /**
   * @return created elements. Never null.
   */
  @NotNull
  protected abstract PsiElement[] create(String newName, PsiDirectory directory) throws Exception;

  protected abstract String getErrorTitle();

  protected abstract String getCommandName();

  protected abstract String getActionName(PsiDirectory directory, String newName);

  public final void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    final PsiDirectory dir = view.getOrChooseDirectory();
    if (dir == null) return;
    final PsiElement[] createdElements = invokeDialog(project, dir);

    for (PsiElement createdElement : createdElements) {
      view.selectElement(createdElement);
    }
  }

  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();

    final boolean enabled = isAvailable(dataContext);

    presentation.setVisible(enabled);
    presentation.setEnabled(enabled);
  }

  protected boolean isDumbAware() {
    return false;
  }

  protected boolean isAvailable(final DataContext dataContext) {
    if (DumbService.getInstance().isDumb() && !isDumbAware()) {
      return false;
    }

    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null || view.getDirectories().length == 0) {
      return false;
    }

    return true;
  }

  protected static String filterMessage(String message) {
    if (message == null) return null;
    @NonNls final String ioExceptionPrefix = "java.io.IOException:";
    if (message.startsWith(ioExceptionPrefix)) {
      message = message.substring(ioExceptionPrefix.length());
    }
    return message;
  }

  protected class MyInputValidator implements InputValidator {
    private final Project myProject;
    private final PsiDirectory myDirectory;
    private SmartPsiElementPointer[] myCreatedElements;

    public MyInputValidator(final Project project, final PsiDirectory directory) {
      myProject = project;
      myDirectory = directory;
      myCreatedElements = new SmartPsiElementPointer[0];
    }

    public boolean checkInput(final String inputString) {
      return true;
    }

    public boolean canClose(final String inputString) {
      if (inputString.length() == 0) {
        Messages.showMessageDialog(myProject, IdeBundle.message("error.name.should.be.specified"), CommonBundle.getErrorTitle(),
                                   Messages.getErrorIcon());
        return false;
      }

      try {
        checkBeforeCreate(inputString, myDirectory);
      }
      catch (IncorrectOperationException e) {
        Messages.showMessageDialog(myProject, filterMessage(e.getMessage()), getErrorTitle(), Messages.getErrorIcon());
        return false;
      }

      final Exception[] exception = new Exception[1];

      final Runnable command = new Runnable() {
        public void run() {
          final Runnable run = new Runnable() {
            public void run() {
              LocalHistoryAction action = LocalHistoryAction.NULL;
              try {
                action = LocalHistory.startAction(myProject, getActionName(myDirectory, inputString));

                PsiElement[] psiElements = create(inputString, myDirectory);
                myCreatedElements = new SmartPsiElementPointer[psiElements.length];
                SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
                for (int i = 0; i < myCreatedElements.length; i++) {
                  myCreatedElements[i] = manager.createSmartPsiElementPointer(psiElements[i]);
                }
              }
              catch (Exception ex) {
                exception[0] = ex;
              }
              finally {
                action.finish();
              }
            }
          };
          ApplicationManager.getApplication().runWriteAction(run);
        }
      };
      CommandProcessor.getInstance().executeCommand(myProject, command, getCommandName(), null);

      if (exception[0] != null) {
        String errorMessage = filterMessage(exception[0].getMessage());
        if (errorMessage == null || errorMessage.length() == 0) {
          errorMessage = exception[0].toString();
        }
        Messages.showMessageDialog(myProject, errorMessage, getErrorTitle(), Messages.getErrorIcon());

      }

      return myCreatedElements.length != 0;
    }

    public final PsiElement[] getCreatedElements() {
      List<PsiElement> elts = new ArrayList<PsiElement>();
      for (SmartPsiElementPointer pointer : myCreatedElements) {
        final PsiElement elt = pointer.getElement();
        if (elt != null) elts.add(elt);
      }
      return elts.toArray(new PsiElement[elts.size()]);
    }
  }
}
