/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.CloseAction;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.docking.DragSession;
import com.intellij.ui.switcher.SwitchProvider;
import com.intellij.ui.switcher.SwitchTarget;
import com.intellij.ui.tabs.*;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.ui.AwtVisitor;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class EditorTabbedContainer implements Disposable, CloseAction.CloseTarget {
  private final EditorWindow myWindow;
  private final Project myProject;
  private final JBEditorTabs myTabs;

  @NonNls public static final String HELP_ID = "ideaInterface.editor";

  private TabInfo.DragOutDelegate myDragOutDelegate = new MyDragOutDelegate();

  EditorTabbedContainer(final EditorWindow window, Project project, final int tabPlacement) {
    myWindow = window;
    myProject = project;
    final ActionManager actionManager = ActionManager.getInstance();
    myTabs = new JBEditorTabs(project, actionManager, IdeFocusManager.getInstance(project), this);
    myTabs.setDataProvider(new MyDataProvider()).setPopupGroup(new Getter<ActionGroup>() {
      @Override
      public ActionGroup get() {
        return (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_TAB_POPUP);
      }
    }, ActionPlaces.EDITOR_TAB_POPUP, false).setNavigationActionsEnabled(false).addTabMouseListener(new TabMouseListener()).getPresentation()
      .setTabDraggingEnabled(true).setUiDecorator(new UiDecorator() {
      @Override
      @NotNull
      public UiDecoration getDecoration() {
        return new UiDecoration(null, new Insets(TabsUtil.TAB_VERTICAL_PADDING, 10, TabsUtil.TAB_VERTICAL_PADDING, 10));
      }
    }).setTabLabelActionsMouseDeadzone(TimedDeadzone.NULL).setGhostsAlwaysVisible(true).setTabLabelActionsAutoHide(false)
      .setActiveTabFillIn(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground()).setPaintFocus(false).getJBTabs()
      .addListener(new TabsListener.Adapter() {
        @Override
        public void selectionChanged(final TabInfo oldSelection, final TabInfo newSelection) {
          final FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
          final FileEditor oldEditor = oldSelection != null ? editorManager.getSelectedEditor((VirtualFile)oldSelection.getObject()) : null;
          if (oldEditor != null) {
            oldEditor.deselectNotify();
          }

          final FileEditor newEditor = editorManager.getSelectedEditor((VirtualFile)newSelection.getObject());
          if (newEditor != null) {
            newEditor.selectNotify();
          }
        }
      }).setAdditionalSwitchProviderWhenOriginal(new MySwitchProvider())
    .setSelectionChangeHandler(new JBTabs.SelectionChangeHandler() {
      @NotNull
      @Override
      public ActionCallback execute(TabInfo info, boolean requestFocus, @NotNull final ActiveRunnable doChangeSelection) {
        final ActionCallback result = new ActionCallback();
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          @Override
          public void run() {
            ((IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(myProject)).onSelectionChanged();
            result.notify(doChangeSelection.run());
          }
        }, "EditorChange", null);
        return result;
      }
    }).getPresentation().setRequestFocusOnLastFocusedComponent(true);
    myTabs.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (myTabs.findInfo(e) != null || isFloating()) return;
        if (!e.isPopupTrigger() && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
          final ActionManager mgr = ActionManager.getInstance();
          mgr.tryToExecute(mgr.getAction("HideAllWindows"), e, null, ActionPlaces.UNKNOWN, true);
        }
      }
    });

    setTabPlacement(UISettings.getInstance().EDITOR_TAB_PLACEMENT);

    updateTabBorder();

    ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerAdapter() {
      @Override
      public void stateChanged() {
        updateTabBorder();
      }

      @Override
      public void toolWindowRegistered(@NotNull final String id) {
        updateTabBorder();
      }
    });

    UISettings.getInstance().addUISettingsListener(new UISettingsListener() {
      @Override
      public void uiSettingsChanged(UISettings source) {
        updateTabBorder();
      }
    }, this);

    Disposer.register(project, this);
  }

  public int getTabCount() {
    return myTabs.getTabCount();
  }

  public ActionCallback setSelectedIndex(final int indexToSelect) {
    return setSelectedIndex(indexToSelect, true);
  }

  public ActionCallback setSelectedIndex(final int indexToSelect, boolean focusEditor) {
    if (indexToSelect >= myTabs.getTabCount()) return new ActionCallback.Rejected();
    return myTabs.select(myTabs.getTabAt(indexToSelect), focusEditor);
  }


  public static DockableEditor createDockableEditor(Project project, Image image, VirtualFile file, Presentation presentation, EditorWindow window) {
    return new DockableEditor(project, image, file, presentation, window);
  }

  private void updateTabBorder() {
    if (!myProject.isOpen()) return;

    ToolWindowManagerEx mgr = (ToolWindowManagerEx)ToolWindowManager.getInstance(myProject);

    String[] ids = mgr.getToolWindowIds();

    Insets border = new Insets(0, 0, 0, 0);

    UISettings uiSettings = UISettings.getInstance();

    List<String> topIds = mgr.getIdsOn(ToolWindowAnchor.TOP);
    List<String> bottom = mgr.getIdsOn(ToolWindowAnchor.BOTTOM);
    List<String> rightIds = mgr.getIdsOn(ToolWindowAnchor.RIGHT);
    List<String> leftIds = mgr.getIdsOn(ToolWindowAnchor.LEFT);

    if (!uiSettings.HIDE_TOOL_STRIPES && !uiSettings.PRESENTATION_MODE) {
      border.top = topIds.size() > 0 ? 1 : 0;
      border.bottom = bottom.size() > 0 ? 1 : 0;
      border.left = leftIds.size() > 0 ? 1 : 0;
      border.right = rightIds.size() > 0 ? 1 : 0;
    }

    for (String each : ids) {
      ToolWindow eachWnd = mgr.getToolWindow(each);
      if (!eachWnd.isAvailable()) continue;

      if (eachWnd.isVisible() && eachWnd.getType() == ToolWindowType.DOCKED) {
        ToolWindowAnchor eachAnchor = eachWnd.getAnchor();
        if (eachAnchor == ToolWindowAnchor.TOP) {
          border.top = 0;
        }
        else if (eachAnchor == ToolWindowAnchor.BOTTOM) {
          border.bottom = 0;
        }
        else if (eachAnchor == ToolWindowAnchor.LEFT) {
          border.left = 0;
        }
        else if (eachAnchor == ToolWindowAnchor.RIGHT) {
          border.right = 0;
        }
      }
    }

    myTabs.getPresentation().setPaintBorder(border.top, border.left, border.right, border.bottom).setTabSidePaintBorder(5);
  }

  public Component getComponent() {
    return myTabs.getComponent();
  }

  public ActionCallback removeTabAt(final int componentIndex, int indexToSelect, boolean transferFocus) {
    TabInfo toSelect = indexToSelect >= 0 && indexToSelect < myTabs.getTabCount() ? myTabs.getTabAt(indexToSelect) : null;
    final TabInfo info = myTabs.getTabAt(componentIndex);
    // removing hidden tab happens on end of drag-out, we've already selected the correct tab for this case in dragOutStarted
    if (info.isHidden()) {
      toSelect = null;
    }
    final ActionCallback callback = myTabs.removeTab(info, toSelect, transferFocus);
    return myProject.isOpen() ? callback : new ActionCallback.Done();
  }

  public ActionCallback removeTabAt(final int componentIndex, int indexToSelect) {
    return removeTabAt(componentIndex, indexToSelect, true);
  }

  public int getSelectedIndex() {
    return myTabs.getIndexOf(myTabs.getSelectedInfo());
  }

  public void setForegroundAt(final int index, final Color color) {
    myTabs.getTabAt(index).setDefaultForeground(color);
  }

  public void setWaveColor(final int index, @Nullable final Color color) {
    final TabInfo tab = myTabs.getTabAt(index);
    tab.setDefaultStyle(color == null ? SimpleTextAttributes.STYLE_PLAIN : SimpleTextAttributes.STYLE_WAVED);
    tab.setDefaultWaveColor(color);
  }

  public void setIconAt(final int index, final Icon icon) {
    myTabs.getTabAt(index).setIcon(icon);
  }

  public void setTitleAt(final int index, final String text) {
    myTabs.getTabAt(index).setText(text);
  }

  public void setToolTipTextAt(final int index, final String text) {
    myTabs.getTabAt(index).setTooltipText(text);
  }

  public void setBackgroundColorAt(final int index, final Color color) {
    myTabs.getTabAt(index).setTabColor(color);
  }

  public void setTabLayoutPolicy(final int policy) {
    switch (policy) {
      case JTabbedPane.SCROLL_TAB_LAYOUT:
        myTabs.getPresentation().setSingleRow(true);
        break;
      case JTabbedPane.WRAP_TAB_LAYOUT:
        myTabs.getPresentation().setSingleRow(false);
        break;
      default:
        throw new IllegalArgumentException("Unsupported tab layout policy: " + policy);
    }
  }

  public void setTabPlacement(final int tabPlacement) {
    switch (tabPlacement) {
      case SwingConstants.TOP:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.top);
        break;
      case SwingConstants.BOTTOM:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.bottom);
        break;
      case SwingConstants.LEFT:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.left);
        break;
      case SwingConstants.RIGHT:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.right);
        break;
      default:
        throw new IllegalArgumentException("Unknown tab placement code=" + tabPlacement);
    }
  }

  @Nullable
  public Object getSelectedComponent() {
    final TabInfo info = myTabs.getTargetInfo();
    return info != null ? info.getComponent() : null;
  }

  public void insertTab(final VirtualFile file, final Icon icon, final JComponent comp, final String tooltip, final int indexToInsert) {

    TabInfo tab = myTabs.findInfo(file);
    if (tab != null) return;

    tab = new TabInfo(comp).setText(calcTabTitle(myProject, file)).setIcon(icon).setTooltipText(tooltip).setObject(file)
      .setTabColor(calcTabColor(myProject, file)).setDragOutDelegate(myDragOutDelegate);
    tab.setTestableUi(new MyQueryable(tab));

    final DefaultActionGroup tabActions = new DefaultActionGroup();
    tabActions.add(new CloseTab(comp, tab));

    tab.setTabLabelActions(tabActions, ActionPlaces.EDITOR_TAB);
    myTabs.addTabSilently(tab, indexToInsert);
  }

  public boolean isEmptyVisible() {
    return myTabs.isEmptyVisible();
  }

  public JBTabs getTabs() {
    return myTabs;
  }

  public void requestFocus(boolean forced) {
    if (myTabs != null) {
      IdeFocusManager.getInstance(myProject).requestFocus(myTabs.getComponent(), forced);
    }
  }

  public void setPaintBlocked(boolean blocked) {
    ((JBTabsImpl)myTabs).setPaintBlocked(blocked, true);
  }

  private static class MyQueryable implements Queryable {

    private final TabInfo myTab;

    public MyQueryable(TabInfo tab) {
      myTab = tab;
    }

    @Override
    public void putInfo(@NotNull Map<String, String> info) {
      info.put("editorTab", myTab.getText());
    }
  }

  public static String calcTabTitle(final Project project, final VirtualFile file) {
    for (EditorTabTitleProvider provider : Extensions.getExtensions(EditorTabTitleProvider.EP_NAME)) {
      final String result = provider.getEditorTabTitle(project, file);
      if (result != null) {
        return result;
      }
    }

    return file.getPresentableName();
  }

  @Nullable
  public static Color calcTabColor(final Project project, final VirtualFile file) {
    for (EditorTabColorProvider provider : Extensions.getExtensions(EditorTabColorProvider.EP_NAME)) {
      final Color result = provider.getEditorTabColor(project, file);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  public Component getComponentAt(final int i) {
    final TabInfo tab = myTabs.getTabAt(i);
    return tab.getComponent();
  }

  @Override
  public void dispose() {

  }

  private class CloseTab extends AnAction implements DumbAware {

    ShadowAction myShadow;
    private final TabInfo myTabInfo;

    public CloseTab(JComponent c, TabInfo info) {
      myTabInfo = info;
      myShadow = new ShadowAction(this, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE), c);
    }

    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setIcon(myTabs.isEditorTabs() ? AllIcons.Actions.CloseNew : AllIcons.Actions.Close);
      e.getPresentation().setHoveredIcon(myTabs.isEditorTabs()? AllIcons.Actions.CloseNewHovered : AllIcons.Actions.CloseHovered);
      e.getPresentation().setVisible(UISettings.getInstance().SHOW_CLOSE_BUTTON);
      e.getPresentation().setText("Close. Alt-click to close others.");
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      final FileEditorManagerEx mgr = FileEditorManagerEx.getInstanceEx(myProject);
      EditorWindow window;
      final VirtualFile file = (VirtualFile)myTabInfo.getObject();
      if (ActionPlaces.EDITOR_TAB.equals(e.getPlace())) {
        window = myWindow;
      }
      else {
        window = mgr.getCurrentWindow();
      }

      if (window != null) {
        if ((e.getModifiers() & InputEvent.ALT_MASK) != 0) {
          window.closeAllExcept(file);
        }
        else {
          if (window.findFileComposite(file) != null) {
            mgr.closeFile(file, window);
          }
        }
      }
    }
  }

  private class MyDataProvider implements DataProvider {
    @Override
    public Object getData(@NonNls final String dataId) {
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      if (PlatformDataKeys.VIRTUAL_FILE.is(dataId)) {
        final VirtualFile selectedFile = myWindow.getSelectedFile();
        return selectedFile != null && selectedFile.isValid() ? selectedFile : null;
      }
      if (EditorWindow.DATA_KEY.is(dataId)) {
        return myWindow;
      }
      if (PlatformDataKeys.HELP_ID.is(dataId)) {
        return HELP_ID;
      }

      if (CloseAction.CloseTarget.KEY.is(dataId)) {
        TabInfo selected = myTabs.getSelectedInfo();
        if (selected != null) {
          return EditorTabbedContainer.this;
        }
      }

      if (EditorWindow.DATA_KEY.is(dataId)) {
        return myWindow;
      }

      return null;
    }
  }

  @Override
  public void close() {
    TabInfo selected = myTabs.getTargetInfo();
    if (selected == null) return;

    final VirtualFile file = (VirtualFile)selected.getObject();
    final FileEditorManagerEx mgr = FileEditorManagerEx.getInstanceEx(myProject);

    AsyncResult<EditorWindow> window = mgr.getActiveWindow();
    window.doWhenDone(new AsyncResult.Handler<EditorWindow>() {
      @Override
      public void run(EditorWindow wnd) {
        if (wnd != null) {
          if (wnd.findFileComposite(file) != null) {
            mgr.closeFile(file, wnd);
          }
        }
      }
    });
  }

  private boolean isFloating() {
    return myWindow.getOwner().isFloating();
  }

  private class TabMouseListener extends MouseAdapter {
    private int myActionClickCount;

    @Override
    public void mouseReleased(MouseEvent e) {
      if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
        final TabInfo info = myTabs.findInfo(e);
        if (info != null) {
          IdeEventQueue.getInstance().blockNextEvents(e);
          FileEditorManagerEx.getInstanceEx(myProject).closeFile((VirtualFile)info.getObject(), myWindow);
        }
      }
    }

    @Override
    public void mousePressed(final MouseEvent e) {
      if (UIUtil.isActionClick(e)) {
        if (e.getClickCount() == 1) {
          myActionClickCount = 0;
        }
        // clicks on the close window button don't count in determining whether we have a double-click on tab (IDEA-70403)
        final Component deepestComponent = SwingUtilities.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
        if (!(deepestComponent instanceof InplaceButton)) {
          myActionClickCount++;
        }
        if (myActionClickCount > 1 && !isFloating()) {
          final ActionManager mgr = ActionManager.getInstance();
          mgr.tryToExecute(mgr.getAction("HideAllWindows"), e, null, ActionPlaces.UNKNOWN, true);
        }
      }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (UIUtil.isActionClick(e, MouseEvent.MOUSE_CLICKED) && (e.isMetaDown() || (!SystemInfo.isMac && e.isControlDown()))) {
        final TabInfo info = myTabs.findInfo(e);
        if (info != null && info.getObject() != null) {
          final VirtualFile vFile = (VirtualFile)info.getObject();
          ShowFilePathAction.show(vFile, e);
        }
      }
    }
  }

  private class MySwitchProvider implements SwitchProvider {
    @Override
    public List<SwitchTarget> getTargets(final boolean onlyVisible, boolean originalProvider) {
      final ArrayList<SwitchTarget> result = new ArrayList<SwitchTarget>();
      TabInfo selected = myTabs.getSelectedInfo();
      new AwtVisitor(selected.getComponent()) {
        @Override
        public boolean visit(Component component) {
          if (component instanceof JBTabs) {
            JBTabs tabs = (JBTabs)component;
            if (tabs != myTabs) {
              result.addAll(tabs.getTargets(onlyVisible, false));
              return true;
            }
          }
          return false;
        }
      };
      return result;
    }

    @Override
    public SwitchTarget getCurrentTarget() {
      TabInfo selected = myTabs.getSelectedInfo();
      final Ref<SwitchTarget> targetRef = new Ref<SwitchTarget>();
      new AwtVisitor(selected.getComponent()) {
        @Override
        public boolean visit(Component component) {
          if (component instanceof JBTabs) {
            JBTabs tabs = (JBTabs)component;
            if (tabs != myTabs) {
              targetRef.set(tabs.getCurrentTarget());
              return true;
            }
          }
          return false;
        }
      };

      return targetRef.get();
    }

    @Override
    public JComponent getComponent() {
      return null;
    }

    @Override
    public boolean isCycleRoot() {
      return false;
    }
  }

  class MyDragOutDelegate implements TabInfo.DragOutDelegate {

    private VirtualFile myFile;
    private DragSession mySession;

    @Override
    public void dragOutStarted(MouseEvent mouseEvent, TabInfo info) {
      final TabInfo previousSelection = info.getPreviousSelection();
      final Image img = JBTabsImpl.getComponentImage(info);
      info.setHidden(true);
      if (previousSelection != null) {
        myTabs.select(previousSelection, true);
      }

      myFile = (VirtualFile)info.getObject();
      Presentation presentation = new Presentation(info.getText());
      presentation.setIcon(info.getIcon());
      mySession = getDockManager().createDragSession(mouseEvent, createDockableEditor(myProject, img, myFile, presentation, myWindow));
    }

    private DockManager getDockManager() {
      return DockManager.getInstance(myProject);
    }

    @Override
    public void processDragOut(MouseEvent event, TabInfo source) {
      mySession.process(event);
    }

    @Override
    public void dragOutFinished(MouseEvent event, TabInfo source) {
      boolean copy = UIUtil.isControlKeyDown(event) || mySession.getResponse(event) == DockContainer.ContentResponse.ACCEPT_COPY;
      if (!copy) {
        myFile.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, Boolean.TRUE);
        FileEditorManagerEx.getInstanceEx(myProject).closeFile(myFile, myWindow);
      }
      else {
        source.setHidden(false);
      }

      mySession.process(event);
      if (!copy) {
        myFile.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, null);
      }

      myFile = null;
      mySession = null;
    }

    @Override
    public void dragOutCancelled(TabInfo source) {
      source.setHidden(false);
      if (mySession != null) {
        mySession.cancel();
      }

      myFile = null;
      mySession = null;
    }

  }

  public static class DockableEditor implements DockableContent<VirtualFile> {
    final Image myImg;
    private DockableEditorTabbedContainer myContainer;
    private Presentation myPresentation;
    private EditorWindow myEditorWindow;
    private Dimension myPreferredSize;
    private boolean myPinned;
    private VirtualFile myFile;

    public DockableEditor(Project project, Image img, VirtualFile file, Presentation presentation, EditorWindow window) {
      myImg = img;
      myFile = file;
      myPresentation = presentation;
      myContainer = new DockableEditorTabbedContainer(project);
      myEditorWindow = window;
      myPreferredSize = myEditorWindow.getSize();
      myPinned = window.isFilePinned(file);
    }

    @NotNull
    @Override
    public VirtualFile getKey() {
      return myFile;
    }

    @Override
    public Image getPreviewImage() {
      return myImg;
    }

    @Override
    public Dimension getPreferredSize() {
      return myPreferredSize;
    }

    @Override
    public String getDockContainerType() {
      return DockableEditorContainerFactory.TYPE;
    }

    @Override
    public Presentation getPresentation() {
      return myPresentation;
    }

    @Override
    public void close() {
      myContainer.close(myFile);
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public boolean isPinned() {
      return myPinned;
    }
  }

}
