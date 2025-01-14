package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.MainWatchPanel;
import com.intellij.debugger.ui.tree.render.BatchEvaluator;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentListener;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class DebuggerPanelsManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerPanelsManager");

  private final Project myProject;
  private final ExecutionManager myExecutionManager;

  private final PositionHighlighter myEditorManager;
  private final HashMap<ProcessHandler, DebuggerSessionTab> mySessionTabs = new HashMap<ProcessHandler, DebuggerSessionTab>();

  public DebuggerPanelsManager(Project project, final EditorColorsManager colorsManager, ExecutionManager executionManager) {
    myProject = project;
    myExecutionManager = executionManager;

    myEditorManager = new PositionHighlighter(myProject, getContextManager());

    final EditorColorsListener myColorsListener = new EditorColorsListener() {
      public void globalSchemeChange(EditorColorsScheme scheme) {
        myEditorManager.updateContextPointDescription();
      }
    };
    colorsManager.addEditorColorsListener(myColorsListener);
    Disposer.register(project, new Disposable() {
      public void dispose() {
        colorsManager.removeEditorColorsListener(myColorsListener);
      }
    });

    getContextManager().addListener(new DebuggerContextListener() {
      public void changeEvent(final DebuggerContextImpl newContext, int event) {
        if (event == DebuggerSession.EVENT_PAUSE) {
          DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
            public void run() {
              toFront(newContext.getDebuggerSession());
            }
          });
        }
      }
    });
  }

  private DebuggerStateManager getContextManager() {
    return DebuggerManagerEx.getInstanceEx(myProject).getContextManager();
  }

  @Nullable
  public
  RunContentDescriptor attachVirtualMachine(Executor executor,
                                            ProgramRunner runner,
                                            ExecutionEnvironment environment,
                                            RunProfileState state,
                                            RunContentDescriptor reuseContent,
                                            RemoteConnection remoteConnection,
                                            boolean pollConnection) throws ExecutionException {

    final DebuggerSession debuggerSession =
      DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(executor, runner, (ModuleRunProfile) environment.getRunProfile(), state, remoteConnection, pollConnection);
    if (debuggerSession == null) {
      return null;
    }

    final DebugProcessImpl debugProcess = debuggerSession.getProcess();
    if (debugProcess.isDetached() || debugProcess.isDetaching()) {
      return null;
    }
    if (state instanceof RemoteState) {
      // optimization: that way BatchEvaluator will not try to lookup the class file in remote VM
      // which is an expensive oparation when executed first time
      debugProcess.putUserData(BatchEvaluator.REMOTE_SESSION_KEY, Boolean.TRUE);
    }

    final DebuggerSessionTab sessionTab = new DebuggerSessionTab(myProject, debuggerSession.getSessionName());
    Disposer.register(myProject, sessionTab);
    RunContentDescriptor runContentDescriptor =
      sessionTab.attachToSession(debuggerSession, runner, environment);
    if (reuseContent != null) {
      final ProcessHandler prevHandler = reuseContent.getProcessHandler();
      if (prevHandler != null) {
        final DebuggerSessionTab prevSession = mySessionTabs.get(prevHandler);
        if (prevSession != null) {
          sessionTab.reuse(prevSession);
        }
      }
    }
    mySessionTabs.put(runContentDescriptor.getProcessHandler(), sessionTab);
    return runContentDescriptor;
  }


  public void projectOpened() {
    final RunContentManager contentManager = myExecutionManager.getContentManager();
    LOG.assertTrue(contentManager != null, "Content manager is null");

    final RunContentListener myContentListener = new RunContentListener() {
      public void contentSelected(RunContentDescriptor descriptor) {
        DebuggerSessionTab sessionTab = descriptor != null ? getSessionTab(descriptor.getProcessHandler()) : null;

        if (sessionTab != null) {
          getContextManager()
            .setState(sessionTab.getContextManager().getContext(), sessionTab.getSession().getState(), DebuggerSession.EVENT_CONTEXT, null);
        }
        else {
          getContextManager()
            .setState(DebuggerContextImpl.EMPTY_CONTEXT, DebuggerSession.STATE_DISPOSED, DebuggerSession.EVENT_CONTEXT, null);
        }
      }

      public void contentRemoved(RunContentDescriptor descriptor) {
        DebuggerSessionTab sessionTab = getSessionTab(descriptor.getProcessHandler());
        if (sessionTab != null) {
          mySessionTabs.remove(descriptor.getProcessHandler());
          Disposer.dispose(sessionTab);
        }
      }
    };

    contentManager.addRunContentListener(myContentListener, DefaultDebugExecutor.getDebugExecutorInstance());
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        contentManager.removeRunContentListener(myContentListener);
      }
    });
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "DebuggerPanelsManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static DebuggerPanelsManager getInstance(Project project) {
    return project.getComponent(DebuggerPanelsManager.class);
  }

  @Nullable
  public MainWatchPanel getWatchPanel() {
    DebuggerSessionTab sessionTab = getSessionTab();
    return sessionTab != null ? sessionTab.getWatchPanel() : null;
  }

  @Nullable
  public DebuggerSessionTab getSessionTab() {
    DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(myProject).getContext();
    return getSessionTab(context.getDebuggerSession());
  }

  public void showFramePanel() {
    DebuggerSessionTab sessionTab = getSessionTab();
    if (sessionTab != null) {
      sessionTab.showFramePanel();
    }
  }

  public void toFront(DebuggerSession session) {
    DebuggerSessionTab sessionTab = getSessionTab(session);
    if (sessionTab != null) {
      sessionTab.toFront();
    }
  }

  private DebuggerSessionTab getSessionTab(ProcessHandler processHandler) {
    return mySessionTabs.get(processHandler);
  }

  @Nullable
  private DebuggerSessionTab getSessionTab(DebuggerSession session) {
    return session != null ? getSessionTab(session.getProcess().getExecutionResult().getProcessHandler()) : null;
  }

}
