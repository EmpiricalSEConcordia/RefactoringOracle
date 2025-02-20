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

package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootsTraversing;

public final class ModulePathMacro extends Macro {
  public String getName() {
    return "ModuleSourcePath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.module.source.path");
  }

  public String expand(DataContext dataContext) {
    final Module module = LangDataKeys.MODULE.getData(dataContext);
    if (module == null) {
      return null;
    }
    return ProjectRootsTraversing.collectRoots(module, ProjectRootsTraversing.PROJECT_SOURCES).getPathsString();
  }
}
