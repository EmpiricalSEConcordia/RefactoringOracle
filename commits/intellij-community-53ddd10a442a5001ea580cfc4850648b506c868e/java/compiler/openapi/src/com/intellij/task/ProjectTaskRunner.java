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
package com.intellij.task;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Vladislav.Soroka
 * @since 4/29/2016
 */
public abstract class ProjectTaskRunner {

  public static final ExtensionPointName<ProjectTaskRunner> EP_NAME = ExtensionPointName.create("com.intellij.projectTaskRunner");

  public abstract void run(@NotNull Project project,
                           @NotNull ProjectTaskContext context,
                           @Nullable ProjectTaskNotification callback,
                           @NotNull Collection<? extends ProjectTask> tasks);

  public void run(@NotNull Project project,
                  @NotNull ProjectTaskContext context,
                  @Nullable ProjectTaskNotification callback,
                  @NotNull ProjectTask... tasks) {
    run(project, context, callback, Arrays.asList(tasks));
  }

  public abstract boolean canRun(@NotNull ProjectTask projectTask);

  public abstract ExecutionEnvironment createExecutionEnvironment(@NotNull Project project, @NotNull RunProjectTask task);
}
