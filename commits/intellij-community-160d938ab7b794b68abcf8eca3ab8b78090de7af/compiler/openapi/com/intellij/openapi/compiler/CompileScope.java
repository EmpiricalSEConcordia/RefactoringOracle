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
package com.intellij.openapi.compiler;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * Interface describing the current compilation scope.
 * Only sources that belong to the scope are compiled.
 *
 * @see CompilerManager#compile(CompileScope, CompileStatusNotification, boolean)
 */
public interface CompileScope extends UserDataHolder {
  /**
   * Returns the list of files within the scope.
   *
   * @param fileType     the type of the files. Null should be passed if all available files are needed.
   * @param inSourceOnly if true, files are searched only in directories within the scope that are marked as "sources" or "test sources" in module settings.
   *                     Otherwise files are searched in all directories that belong to the scope.
   * @return a list of files of given type that belong to this scope.
   */
  VirtualFile[] getFiles(@Nullable FileType fileType, boolean inSourceOnly);

  /**
   * Checks if the file with the specified URL belongs to the scope.
   *
   * @param url an VFS url. Note that actual file may not exist on the disk.
   * @return true if the url specified belongs to the scope, false otherwise.
   *         Note: the method may be time-consuming.
   */
  boolean belongs(String url);

  /**
   * Returns the list of modules files in which belong to the scope.
   *
   * @return a list of modules this scope affects.
   */
  Module[] getAffectedModules();
}
