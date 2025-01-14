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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * User: anna
 * Date: Apr 18, 2005
 */
public class MoveEditorToOppositeTabGroupAction extends AnAction implements DumbAware {

  public void actionPerformed(final AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final VirtualFile vFile = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (vFile == null || project == null){
      return;
    }
    final EditorWindow window = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    if (window != null) {
      final EditorWindow[] siblings = window.findSiblings ();
      if (siblings != null && siblings.length == 1) {

        ((FileEditorManagerImpl)FileEditorManagerEx.getInstanceEx(project)).openFileImpl3 (siblings [0], vFile, true, null, true);
        window.closeFile(vFile);
      }
    }
  }

  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();
    final VirtualFile vFile = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    final EditorWindow window = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(isEnabled(vFile, window));
    }
    else {
      presentation.setEnabled(isEnabled(vFile, window));
    }
  }

  private static boolean isEnabled(VirtualFile vFile, EditorWindow window) {
    if (vFile != null && window != null) {
      final EditorWindow[] siblings = window.findSiblings ();
      if (siblings != null && siblings.length == 1) {
        return true;
      }
    }
    return false;
  }
}
