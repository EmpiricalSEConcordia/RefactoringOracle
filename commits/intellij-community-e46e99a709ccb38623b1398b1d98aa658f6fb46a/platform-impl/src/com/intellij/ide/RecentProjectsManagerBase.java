package com.intellij.ide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author yole
 */
public abstract class RecentProjectsManagerBase implements PersistentStateComponent<RecentProjectsManagerBase.State> {
  public static RecentProjectsManagerBase getInstance() {
    return ApplicationManager.getApplication().getComponent(RecentProjectsManagerBase.class);
  }

  public static class State {
    public List<String> recentPaths = new ArrayList<String>();
    public String lastPath;
  }

  protected State myState = new State();

  private static final int MAX_RECENT_PROJECTS = 15;

  public RecentProjectsManagerBase(ProjectManager projectManager, MessageBus messageBus) {
    projectManager.addProjectManagerListener(new MyProjectManagerListener());
    messageBus.connect().subscribe(AppLifecycleListener.TOPIC, new MyAppLifecycleListener());
  }

  public State getState() {
    validateRecentProjects();
    return myState;
  }

  public void loadState(final State state) {
    myState = state;
    if (myState.lastPath != null && !new File(myState.lastPath).exists()) {
      myState.lastPath = null;
    }
  }

  private void validateRecentProjects() {
    for (Iterator i = myState.recentPaths.iterator(); i.hasNext();) {
      String s = (String)i.next();
      if (s == null || !(new File(s).exists())) {
        i.remove();
      }
    }
    while (myState.recentPaths.size() > MAX_RECENT_PROJECTS) {
      myState.recentPaths.remove(myState.recentPaths.size() - 1);
    }
  }

  private void removePath(final String path) {
    if (SystemInfo.isFileSystemCaseSensitive) {
      myState.recentPaths.remove(path);
    }
    else {
      Iterator<String> i = myState.recentPaths.iterator();
      while (i.hasNext()) {
        String p = i.next();
        if (path.equalsIgnoreCase(p)) {
          i.remove();
        }
      }
    }
  }

  public String getLastProjectPath() {
    return myState.lastPath;
  }

  public void updateLastProjectPath() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 0) {
      myState.lastPath = null;
    } else {
      myState.lastPath = getProjectPath(openProjects[openProjects.length - 1]);
    }
  }

  /**
   * @param addClearListItem - used for detecting whether the "Clear List" action should be added
   * to the end of the returned list of actions
   * @return
   */
  public AnAction[] getRecentProjectsActions(boolean addClearListItem) {
    validateRecentProjects();

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();

    ArrayList<AnAction> actions = new ArrayList<AnAction>();
    outer: for (String recentPath : myState.recentPaths) {

      for (Project openProject : openProjects) {
        if (recentPath.equals(getProjectPath(openProject))) {
          continue outer;
        }
      }

      actions.add(new ReopenProjectAction(recentPath));
    }


    if (actions.size() == 0) {
      return new AnAction[0];
    }

    ArrayList<AnAction> list = new ArrayList<AnAction>();
    for (AnAction action : actions) {
      list.add(action);
    }
    if (addClearListItem) {
      AnAction clearListAction = new AnAction(IdeBundle.message("action.clear.list")) {
        public void actionPerformed(AnActionEvent e) {
          myState.recentPaths.clear();
        }
      };
      list.add(Separator.getInstance());
      list.add(clearListAction);
    }

    return list.toArray(new AnAction[list.size()]);
  }

  public String[] getRecentProjectPaths() {
    validateRecentProjects();
    return myState.recentPaths.toArray(new String[myState.recentPaths.size()]);
  }

  @Nullable
  protected abstract String getProjectPath(Project project);

  protected abstract void doOpenProject(String projectPath, Project projectToClose);

  private class MyProjectManagerListener extends ProjectManagerAdapter {
    public void projectOpened(final Project project) {
      String path = getProjectPath(project);
      myState.lastPath = path;
      removePath(path);
      myState.recentPaths.add(0, path);
    }

    public void projectClosed(final Project project) {
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      if (openProjects.length > 0) {
        String path = getProjectPath(openProjects[openProjects.length - 1]);
        if (path != null) {
          myState.lastPath = path;
          removePath(path);
          myState.recentPaths.add(0, path);
        }
      }
    }
  }

  private class ReopenProjectAction extends AnAction {
    private final String myProjectPath;

    public ReopenProjectAction(String projectPath) {
      myProjectPath = projectPath;
      getTemplatePresentation().setText(projectPath, false);
    }

    public void actionPerformed(AnActionEvent e) {
      doOpenProject(myProjectPath, PlatformDataKeys.PROJECT.getData(e.getDataContext()));
    }
  }

  private class MyAppLifecycleListener extends AppLifecycleListener.Adapter {
    public void appFrameCreated(final String[] commandLineArgs, @NotNull final Ref<Boolean> willOpenProject) {
      if (GeneralSettings.getInstance().isReopenLastProject() && getLastProjectPath() != null) {
        willOpenProject.set(Boolean.TRUE);
      }
    }

    public void appStarting(final Project projectFromCommandLine) {
      if (projectFromCommandLine != null) return;
      GeneralSettings generalSettings = GeneralSettings.getInstance();
      if (generalSettings.isReopenLastProject()) {
        String lastProjectPath = getLastProjectPath();
        if (lastProjectPath != null) {
          doOpenProject(lastProjectPath, null);
        }
      }
    }

    public void projectFrameClosed() {
      updateLastProjectPath();
    }

    public void projectOpenFailed() {
      updateLastProjectPath();
    }
  }
}
