/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.util.gotoByName;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.ArrayList;

public class ProjectBaseDirNavigationContributor implements ChooseByNameContributor {

  public String[] getNames(Project project, boolean includeNonProjectItems) {
    final VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    final VirtualFile[] files = baseDir.getChildren();
    final ArrayList<String> list = new ArrayList<String>();
    for (VirtualFile file : files) {
      if (!file.isDirectory()) {
        list.add(file.getName());
      }
    }
    return list.toArray(new String[list.size()]);
  }

  public NavigationItem[] getItemsByName(String name, final String pattern, Project project, boolean includeNonProjectItems) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    final VirtualFile[] files = baseDir.getChildren();
    final ArrayList<PsiFile> list = new ArrayList<PsiFile>();
    for (VirtualFile file : files) {
      if (isEditable(file, includeNonProjectItems) && Comparing.strEqual(name, file.getName())) {
        final PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) {
          list.add(psiFile);
        }
      }
    }
    return list.toArray(new PsiFile[list.size()]);
  }

  private static boolean isEditable(VirtualFile file, final boolean checkboxState) {
    FileType type = file.getFileType();
    if (!checkboxState && type == StdFileTypes.JAVA) return false;
    return type != StdFileTypes.CLASS;
  }
}