/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.impl;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationWithAlternativeJre;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.NonClasspathDirectoriesScope;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.indexing.IndexableSetContributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author egor
 */
public class AlternativeJreIndexHelper extends IndexableSetContributor {
  @NotNull
  @Override
  public Set<VirtualFile> getAdditionalRootsToIndex() {
    return Collections.emptySet();
  }

  @NotNull
  @Override
  public Set<VirtualFile> getAdditionalProjectRootsToIndex(@NotNull Project project) {
    SmartHashSet<VirtualFile> res = new SmartHashSet<>();
    for (RunConfiguration configuration : RunManager.getInstance(project).getAllConfigurationsList()) {
      Sdk jre = getAlternativeJre(configuration);
      if (jre != null) {
        res.addAll(getClassRoots(jre));
      }
    }
    return res;
  }

  @Nullable
  public static Sdk getAlternativeJre(RunProfile profile) {
    if (profile instanceof ConfigurationWithAlternativeJre) {
      ConfigurationWithAlternativeJre appConfig = (ConfigurationWithAlternativeJre)profile;
      if (appConfig.isAlternativeJrePathEnabled()) {
        return ProjectJdkTable.getInstance().findJdk(appConfig.getAlternativeJrePath());
      }
    }
    return null;
  }

  @Nullable
  private static Collection<VirtualFile> getClassRoots(@NotNull Sdk jre) {
    return Arrays.asList(jre.getRootProvider().getFiles(OrderRootType.CLASSES));
  }

  @NotNull
  public static Collection<VirtualFile> getSourceRoots(@NotNull Sdk jre) {
    return Arrays.asList(jre.getRootProvider().getFiles(OrderRootType.SOURCES));
  }

  @Nullable
  public static GlobalSearchScope getSearchScope(@NotNull Sdk jre) {
    return new NonClasspathDirectoriesScope(getClassRoots(jre));
  }
}
