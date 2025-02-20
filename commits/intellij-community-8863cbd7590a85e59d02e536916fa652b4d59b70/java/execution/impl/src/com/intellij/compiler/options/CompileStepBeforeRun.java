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
package com.intellij.compiler.options;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileWithCompileBeforeLaunchOption;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public class CompileStepBeforeRun extends BeforeRunTaskProvider<CompileStepBeforeRun.MakeBeforeRunTask> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.options.CompileStepBeforeRun");
  public static final Key<MakeBeforeRunTask> ID = Key.create("Make");
  private static final Key<RunConfiguration> RUN_CONFIGURATION = Key.create("RUN_CONFIGURATION");
  private static final Key<String> RUN_CONFIGURATION_TYPE_ID = Key.create("RUN_CONFIGURATION_TYPE_ID");

  @NonNls protected static final String MAKE_PROJECT_ON_RUN_KEY = "makeProjectOnRun";

  private static final Icon ICON = AllIcons.Actions.Compile;

  private final Project myProject;

  public CompileStepBeforeRun(@NotNull final Project project) {
    myProject = project;
  }

  public Key<MakeBeforeRunTask> getId() {
    return ID;
  }

  @Override
  public String getName() {
    return ExecutionBundle.message("before.launch.compile.step");
  }

  @Override
  public String getDescription(MakeBeforeRunTask task) {
    return ExecutionBundle.message("before.launch.compile.step");
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public Icon getTaskIcon(MakeBeforeRunTask task) {
    return ICON;
  }

  public MakeBeforeRunTask createTask(RunConfiguration runConfiguration) {
    return !(runConfiguration instanceof RemoteConfiguration) && runConfiguration instanceof RunProfileWithCompileBeforeLaunchOption
           ? new MakeBeforeRunTask()
           : null;
  }

  public boolean configureTask(RunConfiguration runConfiguration, MakeBeforeRunTask task) {
    return false;
  }

  @Override
  public boolean canExecuteTask(RunConfiguration configuration, MakeBeforeRunTask task) {
    return true;
  }

  public boolean executeTask(DataContext context, final RunConfiguration configuration, ExecutionEnvironment env, MakeBeforeRunTask task) {
    if (!(configuration instanceof RunProfileWithCompileBeforeLaunchOption)) {
      return true;
    }

    if (configuration instanceof RunConfigurationBase && ((RunConfigurationBase)configuration).excludeCompileBeforeLaunchOption()) {
      return true;
    }

    final RunProfileWithCompileBeforeLaunchOption runConfiguration = (RunProfileWithCompileBeforeLaunchOption)configuration;
    final Ref<Boolean> result = new Ref<Boolean>(Boolean.FALSE);
    try {

      final Semaphore done = new Semaphore();
      done.down();
      final CompileStatusNotification callback = new CompileStatusNotification() {
        public void finished(final boolean aborted, final int errors, final int warnings, CompileContext compileContext) {
          if (errors == 0 && !aborted) {
            result.set(Boolean.TRUE);
          }
          done.up();
        }
      };

      SwingUtilities.invokeAndWait(new Runnable() {
        public void run() {
          CompileScope scope;
          final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
          if (Boolean.valueOf(System.getProperty(MAKE_PROJECT_ON_RUN_KEY, Boolean.FALSE.toString())).booleanValue()) {
            // user explicitly requested whole-project make
            scope = compilerManager.createProjectCompileScope(myProject);
          }
          else {
            final Module[] modules = runConfiguration.getModules();
            if (modules.length > 0) {
              for (Module module : modules) {
                if (module == null) {
                  LOG.error("RunConfiguration should not return null modules. Configuration=" + runConfiguration.getName() + "; class=" +
                            runConfiguration.getClass().getName());
                }
              }
              scope = compilerManager.createModulesCompileScope(modules, true);
            }
            else {
              scope = compilerManager.createProjectCompileScope(myProject);
            }
          }

          if (!myProject.isDisposed()) {
            scope.putUserData(RUN_CONFIGURATION, configuration);
            scope.putUserData(RUN_CONFIGURATION_TYPE_ID, configuration.getType().getId());
            compilerManager.make(scope, callback);
          }
          else {
            done.up();
          }
        }
      });
      done.waitFor();
    }
    catch (Exception e) {
      return false;
    }

    return result.get();
  }

  public boolean isConfigurable() {
    return false;
  }

  @Nullable
  public static RunConfiguration getRunConfiguration(final CompileContext context) {
    return context.getCompileScope().getUserData(RUN_CONFIGURATION);
  }

  @Nullable
  public static RunConfiguration getRunConfiguration(final CompileScope compileScope) {
    return compileScope.getUserData(RUN_CONFIGURATION);
  }

  public static class MakeBeforeRunTask extends BeforeRunTask<MakeBeforeRunTask> {
    private MakeBeforeRunTask() {
      super(ID);
      setEnabled(true);
    }
  }
}
