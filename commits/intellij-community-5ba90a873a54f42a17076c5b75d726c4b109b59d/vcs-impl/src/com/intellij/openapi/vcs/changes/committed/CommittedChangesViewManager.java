/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 30.11.2006
 * Time: 18:12:47
 */
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class CommittedChangesViewManager implements ChangesViewContentProvider {
  private ProjectLevelVcsManager myVcsManager;
  private MessageBus myBus;
  private MessageBusConnection myConnection;
  private CommittedChangesPanel myComponent;
  private Project myProject;
  private VcsListener myVcsListener = new MyVcsListener();

  public CommittedChangesViewManager(final Project project, final ProjectLevelVcsManager vcsManager, final MessageBus bus) {
    myProject = project;
    myVcsManager = vcsManager;
    myBus = bus;
  }

  private void updateChangesContent() {
    final CommittedChangesProvider provider = CommittedChangesCache.getInstance(myProject).getProviderForProject();
    if (provider == null) return;

    if (myComponent == null) {
      myComponent = new CommittedChangesPanel(myProject, provider, provider.createDefaultSettings(), null, null);
      myConnection.subscribe(VcsConfigurationChangeListener.BRANCHES_CHANGED, new VcsConfigurationChangeListener.Notification() {
        public void execute(final Project project, final VirtualFile vcsRoot) {
          sendUpdateCachedListsMessage(vcsRoot);
        }
      });
    }
    else {
      myComponent.setProvider(provider);
      // called from listener to notification of vcs root changes
      sendUpdateCachedListsMessage(null);
    }
  }

  private void sendUpdateCachedListsMessage(final VirtualFile vcsRoot) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myComponent.passCachedListsToListener(myBus.syncPublisher(VcsConfigurationChangeListener.BRANCHES_CHANGED_RESPONSE),
                                              myProject, vcsRoot);
      }
    });
  }

  public JComponent initContent() {
    myVcsManager.addVcsListener(myVcsListener);
    myConnection = myBus.connect();
    myConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new MyCommittedChangesListener());
    updateChangesContent();
    myComponent.refreshChanges(true);
    return myComponent;
  }

  public void disposeContent() {
    myVcsManager.removeVcsListener(myVcsListener);
    myConnection.disconnect();
    myComponent = null;
  }

  private class MyVcsListener implements VcsListener {
    public void directoryMappingChanged() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (!myProject.isDisposed()) {
            updateChangesContent();
          }
        }
      });
    }
  }

  private class MyCommittedChangesListener extends CommittedChangesAdapter {
    public void changesLoaded(RepositoryLocation location, List<CommittedChangeList> changes) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myComponent != null && !myProject.isDisposed()) {
            myComponent.refreshChanges(true);
          }
        }
      });
    }

    public void refreshErrorStatusChanged(@Nullable final VcsException lastError) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myComponent != null && !myProject.isDisposed()) {
            if (lastError != null) {
              myComponent.setErrorText("Error refreshing changes: " + lastError.getMessage());
            }
            else {
              myComponent.setErrorText("");
            }
          }
        }
      });
    }
  }
}