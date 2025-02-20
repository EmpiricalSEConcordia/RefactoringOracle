package com.intellij.codeInspection.ui;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.SwitchOffToolAction;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.deadCode.DeadCodeInspection;
import com.intellij.codeInspection.deadCode.DummyEntryPointsTool;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.codeInspection.export.HTMLExportFrameMaker;
import com.intellij.codeInspection.export.HTMLExporter;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefImplicitConstructor;
import com.intellij.codeInspection.util.RefEntityAlphabeticalComparator;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.OccurenceNavigatorSupport;
import com.intellij.ide.actions.NextOccurenceToolbarAction;
import com.intellij.ide.actions.PreviousOccurenceToolbarAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionListPopup;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ListPopup;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SmartExpander;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class InspectionResultsView extends JPanel implements OccurenceNavigator, DataProvider {
  public static final RefElement[] EMPTY_ELEMENTS_ARRAY = new RefElement[0];
  public static final ProblemDescriptor[] EMPTY_DESCRIPTORS = new ProblemDescriptor[0];
  private Project myProject;
  private InspectionTree myTree;
  private Browser myBrowser;
  private Map<HighlightDisplayLevel, Map<String, InspectionGroupNode>> myGroups = null;
  private OccurenceNavigator myOccurenceNavigator;
  private InspectionProfile myInspectionProfile;
  private AnalysisScope myScope;
  public static final String HELP_ID = "codeInspection";
  public final Map<HighlightDisplayLevel, InspectionSeverityGroupNode> mySeverityGroupNodes = new HashMap<HighlightDisplayLevel, InspectionSeverityGroupNode>();

  private Splitter mySplitter;

  public InspectionResultsView(final Project project, InspectionProfile inspectionProfile, AnalysisScope scope) {
    setLayout(new BorderLayout());

    myProject = project;
    myInspectionProfile = inspectionProfile;
    myScope = scope;
    myTree = new InspectionTree(project);
    myOccurenceNavigator = new OccurenceNavigatorSupport(myTree) {
      @Nullable
      protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
        if (node instanceof RefElementNode) {
          final RefElementNode refNode = (RefElementNode)node;
          if (refNode.hasDescriptorsUnder()) return null;
          final RefElement element = refNode.getElement();
          if (element == null || !element.isValid()) return null;
          final ProblemDescriptor problem = refNode.getProblem();
          if (problem != null) {
            final PsiElement psiElement = problem.getPsiElement();
            if (psiElement == null || !psiElement.isValid()) return null;
            return getOpenFileDescriptor(psiElement);
          }
          return getOpenFileDescriptor(element);
        }
        else if (node instanceof ProblemDescriptionNode) {
          if (!((ProblemDescriptionNode)node).getElement().isValid()) return null;
          final PsiElement psiElement = ((ProblemDescriptionNode)node).getDescriptor().getPsiElement();

          if (psiElement == null || !psiElement.isValid()) return null;
          return getOpenFileDescriptor(psiElement);
        }
        return null;
      }

      public String getNextOccurenceActionName() {
        return "Go Next Problem";
      }

      public String getPreviousOccurenceActionName() {
        return "Go Prev Problem";
      }
    };

    myBrowser = new Browser(this);

    final InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(project);
    mySplitter = new Splitter(false, manager.getUIOptions().SPLITTER_PROPORTION);

    mySplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));
    mySplitter.setSecondComponent(myBrowser);

    mySplitter.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (Splitter.PROP_PROPORTION.equals(evt.getPropertyName())) {
          final InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(project);
          manager.setSplitterProportion(((Float)evt.getNewValue()).floatValue());
        }
      }
    });

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        syncBrowser();
        syncSource();
      }
    });

    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!e.isPopupTrigger() && e.getClickCount() == 2) {
          OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(InspectionResultsView.this), true);
        }
      }
    });

    myTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(InspectionResultsView.this), false);
        }
      }
    });

    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(comp, x, y);
      }
    });

    SmartExpander.installOn(myTree);

    myBrowser.addClickListener(new Browser.ClickListener() {
      public void referenceClicked(final Browser.ClickEvent e) {
        if (e.getEventType() == Browser.ClickEvent.REF_ELEMENT) {
          showSource(e.getClickedElement());
        }
        else if (e.getEventType() == Browser.ClickEvent.FILE_OFFSET) {
          final VirtualFile file = e.getFile();
          OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, e.getStartOffset());
          Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
          TextAttributes selectionAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(
            EditorColors.SEARCH_RESULT_ATTRIBUTES);
          HighlightManager.getInstance(project).addRangeHighlight(editor, e.getStartOffset(), e.getEndOffset(),
                                                                  selectionAttributes, true, null);
        }
      }
    });

    add(mySplitter, BorderLayout.CENTER);
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new CloseAction());
    group.add(new RerunAction(this));
    group.add(manager.createToggleAutoscrollAction());
    group.add(manager.createGroupBySeverityAction());
    group.add(new PreviousOccurenceToolbarAction(getOccurenceNavigator()));
    group.add(new NextOccurenceToolbarAction(getOccurenceNavigator()));
    group.add(new ExportHTMLAction());
    group.add(new EditSettingsAction());
    group.add(new HelpAction());
    group.add(new InvokeQuickFixAction());

    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CODE_INSPECTION,
                                                                                  group, false);
    add(actionToolbar.getComponent(), BorderLayout.WEST);
  }

  private static OpenFileDescriptor getOpenFileDescriptor(PsiElement psiElement) {
    return new OpenFileDescriptor(psiElement.getProject(), psiElement.getContainingFile().getVirtualFile(), psiElement.getTextOffset());
  }

  private void syncSource() {
    if (isAutoScrollMode()) {
      OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(this), false);
    }
  }

  private boolean isAutoScrollMode() {
    String activeToolWindowId = ToolWindowManager.getInstance(myProject).getActiveToolWindowId();
    final InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    return manager.getUIOptions().AUTOSCROLL_TO_SOURCE &&
           (activeToolWindowId == null || activeToolWindowId.equals(ToolWindowId.INSPECTION));
  }

  private static void showSource(final RefElement refElement) {
    OpenFileDescriptor descriptor = getOpenFileDescriptor(refElement);
    if (descriptor != null) {
      Project project = refElement.getRefManager().getProject();
      FileEditorManager.getInstance(project).openTextEditor(descriptor, false);
    }
  }

  public void dispose() {
    mySplitter.dispose();
    myBrowser.dispose();
    myTree = null;
    myOccurenceNavigator = null;
  }


  private class CloseAction extends AnAction {
    private CloseAction() {
      super("Close", null, IconLoader.getIcon("/actions/cancel.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      final InspectionManagerEx managerEx = ((InspectionManagerEx)InspectionManagerEx.getInstance(myProject));
      managerEx.close();
      //may differ from CurrentProfile in case of editor set up
      myInspectionProfile.cleanup();
    }
  }

  private class EditSettingsAction extends AnAction {
    private EditSettingsAction() {
      super("Edit Settings", "Edit Settings", IconLoader.getIcon("/general/ideOptions.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      final InspectionManagerEx manager = ((InspectionManagerEx)InspectionManagerEx.getInstance(myProject));
      manager.setExternalProfile(myInspectionProfile);
      final InspectionCodeSettingsPanel dlg = new InspectionCodeSettingsPanel(manager, myScope);
      final InspectionTool selectedTool = getSelectedTool();
      if (selectedTool != null){        
        dlg.selectInspectionTool(selectedTool.getShortName());
      }
      dlg.show();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (dlg.isOK()) {
            InspectionResultsView.this.update();
            DaemonCodeAnalyzer.getInstance(myProject).restart();
          }
        }
      });
    }
  }

  private void exportHTML() {
    ExportToHTMLDialog exportToHTMLDialog = new ExportToHTMLDialog(myProject);
    final ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(myProject);
    if (exportToHTMLSettings.OUTPUT_DIRECTORY == null) {
      exportToHTMLSettings.OUTPUT_DIRECTORY = PathManager.getHomePath() + File.separator + "exportToHTML";
    }
    exportToHTMLDialog.reset();
    exportToHTMLDialog.show();
    if (!exportToHTMLDialog.isOK()) {
      return;
    }
    exportToHTMLDialog.apply();

    final String outputDirectoryName = exportToHTMLSettings.OUTPUT_DIRECTORY;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final Runnable exportRunnable = new Runnable() {
          public void run() {
            HTMLExportFrameMaker maker = new HTMLExportFrameMaker(outputDirectoryName, myProject);
            maker.start();
            try {
              exportHTML(maker);
            }
            catch (ProcessCanceledException e) {
              // Do nothing here.
            }

            maker.done();
          }
        };

        if (!ApplicationManager.getApplication()
          .runProcessWithProgressSynchronously(exportRunnable, "Generating HTML...", true, myProject)) {
          return;
        }

        if (exportToHTMLSettings.OPEN_IN_BROWSER) {
          BrowserUtil.launchBrowser(exportToHTMLSettings.OUTPUT_DIRECTORY + File.separator + "index.html");
        }
      }
    });
  }

  private static class HelpAction extends AnAction {
    private HelpAction() {
      super("Help", null, IconLoader.getIcon("/actions/help.png"));
    }

    public void actionPerformed(AnActionEvent event) {
      HelpManager.getInstance().invokeHelp(HELP_ID);
    }
  }

  private class ExportHTMLAction extends AnAction {
    public ExportHTMLAction() {
      super("Export HTML", null, IconLoader.getIcon("/actions/export.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      exportHTML();
    }
  }


  private static OpenFileDescriptor getOpenFileDescriptor(final RefElement refElement) {
    final VirtualFile[] file = new VirtualFile[1];
    final int[] offset = new int[1];

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        PsiElement psiElement = refElement.getElement();
        if (psiElement != null) {
          file[0] = psiElement.getContainingFile().getVirtualFile();
          offset[0] = psiElement.getTextOffset();
        }
        else {
          file[0] = null;
        }
      }
    });

    if (file[0] != null && file[0].isValid()) {
      return new OpenFileDescriptor(refElement.getRefManager().getProject(), file[0], offset[0]);
    }
    return null;
  }

  public void syncBrowser() {
    if (myTree.getSelectionModel().getSelectionCount() != 1) {
      myBrowser.showEmpty();
    }
    else {
      TreePath pathSelected = myTree.getSelectionModel().getLeadSelectionPath();
      if (pathSelected != null) {
        final InspectionTreeNode node = (InspectionTreeNode)pathSelected.getLastPathComponent();
        if (node instanceof RefElementNode) {
          final RefElementNode refElementNode = (RefElementNode)node;
          final ProblemDescriptor problem = refElementNode.getProblem();
          RefElement refSelected = refElementNode.getElement();
          if (problem != null) {
            showInBrowser(refSelected, problem);
          }
          else {
            showInBrowser(refSelected);
          }
        }
        else if (node instanceof ProblemDescriptionNode) {
          final ProblemDescriptionNode problemNode = (ProblemDescriptionNode)node;
          showInBrowser(problemNode.getElement(), problemNode.getDescriptor());
        }
        else if (node instanceof InspectionNode) {
          showInBrowser(((InspectionNode)node).getTool());
        }
        else {
          myBrowser.showEmpty();
        }
      }
    }
  }

  public void showInBrowser(final RefEntity refEntity) {
    Cursor currentCursor = getCursor();
    setCursor(new Cursor(Cursor.WAIT_CURSOR));
    myBrowser.showPageFor(refEntity);
    setCursor(currentCursor);
  }

  public void showInBrowser(InspectionTool tool) {
    Cursor currentCursor = getCursor();
    setCursor(new Cursor(Cursor.WAIT_CURSOR));
    myBrowser.showDescription(tool);
    setCursor(currentCursor);
  }

  public void showInBrowser(final RefElement refElement, ProblemDescriptor descriptor) {
    Cursor currentCursor = getCursor();
    setCursor(new Cursor(Cursor.WAIT_CURSOR));
    myBrowser.showPageFor(refElement, descriptor);
    setCursor(currentCursor);
  }

  public void addTool(InspectionTool tool, HighlightDisplayLevel errorLevel, boolean groupedBySeverity) {
    tool.updateContent();
    if (tool.hasReportedProblems()) {
      final InspectionNode toolNode = new InspectionNode(tool);
      initToolNode(tool, toolNode,
                   getToolParentNode(tool.getGroupDisplayName().length() > 0 ? tool.getGroupDisplayName() : "General", errorLevel,
                                     groupedBySeverity));
      if (tool instanceof DeadCodeInspection) {
        final DummyEntryPointsTool entryPoints = new DummyEntryPointsTool((DeadCodeInspection)tool);
        entryPoints.updateContent();
        initToolNode(entryPoints, new EntryPointsNode(entryPoints), toolNode);
      }
      regsisterActionShortcuts(tool);
    }
  }

  private void initToolNode(InspectionTool tool, InspectionNode toolNode, InspectionTreeNode parentNode) {
    final InspectionTreeNode[] contents = tool.getContents();
    for (InspectionTreeNode content : contents) {
      toolNode.add(content);
    }
    parentNode.add(toolNode);
  }

  private void regsisterActionShortcuts(InspectionTool tool) {
    final QuickFixAction[] fixes = tool.getQuickFixes(null);
    if (fixes != null) {
      for (QuickFixAction fix : fixes) {
        fix.registerCustomShortcutSet(fix.getShortcutSet(), this);
      }
    }
  }

  private void clearTree() {
    myTree.removeAllNodes();
    mySeverityGroupNodes.clear();
  }

  public String getCurrentProfileName() {
    return myInspectionProfile.getName();
  }

  public boolean update() {
    InspectionTool[] tools = myInspectionProfile.getInspectionTools();
    clearTree();
    boolean resultsFound = false;
    final InspectionManagerEx manager = ((InspectionManagerEx)InspectionManagerEx.getInstance(myProject));
    final boolean isGroupedBySeverity = manager.getUIOptions().GROUP_BY_SEVERITY;
    myGroups = new HashMap<HighlightDisplayLevel, Map<String, InspectionGroupNode>>();
    for (InspectionTool tool : tools) {
      final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
      if (myInspectionProfile.isToolEnabled(key)) {
        addTool(tool, myInspectionProfile.getErrorLevel(key), isGroupedBySeverity);
        resultsFound |= tool.hasReportedProblems();
      }
    }
    myTree.sort();
    myTree.restoreExpantionAndSelection();
    return resultsFound;
  }

  private InspectionTreeNode getToolParentNode(String groupName, HighlightDisplayLevel errorLevel, boolean groupedBySeverity) {
    if ((groupName == null || groupName.length() == 0)) {
      return getRelativeRootNode(groupedBySeverity, errorLevel);
    }
    Map<String, InspectionGroupNode> map = myGroups.get(errorLevel);
    if (map == null) {
      map = new HashMap<String, InspectionGroupNode>();
      myGroups.put(errorLevel, map);
    }
    Map<String, InspectionGroupNode> searchMap = new HashMap<String, InspectionGroupNode>(map);
    if (!groupedBySeverity){
      for (HighlightDisplayLevel level : myGroups.keySet()) {
        searchMap.putAll(myGroups.get(level));
      }
    }
    InspectionGroupNode group = searchMap.get(groupName);
    if (group == null) {
      group = new InspectionGroupNode(groupName);
      map.put(groupName, group);
      getRelativeRootNode(groupedBySeverity, errorLevel).add(group);
    }
    return group;
  }

  private InspectionTreeNode getRelativeRootNode(boolean isGroupedBySeverity, HighlightDisplayLevel level) {
    if (isGroupedBySeverity) {
      if (mySeverityGroupNodes.containsKey(level)) {
        return mySeverityGroupNodes.get(level);
      }
      else {
        final InspectionSeverityGroupNode severityGroupNode = new InspectionSeverityGroupNode(level);
        mySeverityGroupNodes.put(level, severityGroupNode);
        myTree.getRoot().add(severityGroupNode);
        return severityGroupNode;
      }
    }
    else {
      return myTree.getRoot();
    }
  }

  public void exportHTML(HTMLExportFrameMaker frameMaker) {
    final InspectionTreeNode root = myTree.getRoot();
    final Enumeration children = root.children();
    while (children.hasMoreElements()) {
      InspectionTreeNode node = (InspectionTreeNode)children.nextElement();
      if (node instanceof InspectionNode) {
        exportHTML(frameMaker, (InspectionNode)node);
      }
      else if (node instanceof InspectionGroupNode) {
        final Enumeration groupChildren = node.children();
        while (groupChildren.hasMoreElements()) {
          InspectionNode toolNode = (InspectionNode)groupChildren.nextElement();
          exportHTML(frameMaker, toolNode);
        }
      }
    }
  }

  private void exportHTML(HTMLExportFrameMaker frameMaker, InspectionNode node) {
    InspectionTool tool = node.getTool();
    HTMLExporter exporter = new HTMLExporter(frameMaker.getRootFolder() + "/" + tool.getFolderName(),
                                             tool.getComposer(), myProject);
    frameMaker.startInspection(tool);
    exportHTML(tool, exporter);
    exporter.generateReferencedPages();
  }

  public void exportHTML(InspectionTool tool, HTMLExporter exporter) {
    StringBuffer packageIndex = new StringBuffer();
    packageIndex.append("<html><body>");

    final Map<String, Set<RefElement>> content = tool.getPackageContent();
    ArrayList<String> packageNames = new ArrayList<String>(content.keySet());

    Collections.sort(packageNames, RefEntityAlphabeticalComparator.getInstance());
    for (String packageName : packageNames) {
      appendPackageReference(packageIndex, packageName);
      final ArrayList<RefElement> packageContent = new ArrayList<RefElement>(content.get(packageName));
      Collections.sort(packageContent, RefEntityAlphabeticalComparator.getInstance());
      StringBuffer contentIndex = new StringBuffer();
      contentIndex.append("<html><body>");
      for (RefElement refElement : packageContent) {
        if (refElement instanceof RefImplicitConstructor) {
          refElement = ((RefImplicitConstructor)refElement).getOwnerClass();
        }

        contentIndex.append("<a HREF=\"");
        contentIndex.append(exporter.getURL(refElement));
        contentIndex.append("\" target=\"elementFrame\">");
        contentIndex.append(refElement.getName());
        contentIndex.append("</a><br>");

        exporter.createPage(refElement);
      }

      contentIndex.append("</body></html>");
      HTMLExporter.writeFile(exporter.getRootFolder(), packageName + "-index.html", contentIndex, myProject);
    }

    packageIndex.append("</body></html>");

    HTMLExporter.writeFile(exporter.getRootFolder(), "index.html", packageIndex, myProject);
  }

  private static void appendPackageReference(StringBuffer packageIndex, String packageName) {
    packageIndex.append("<a HREF=\"");
    packageIndex.append(packageName);
    packageIndex.append("-index.html\" target=\"packageFrame\">");
    packageIndex.append(packageName);
    packageIndex.append("</a><br>");
  }

  public OccurenceNavigator getOccurenceNavigator() {
    return myOccurenceNavigator;
  }

  public boolean hasNextOccurence() {
    return myOccurenceNavigator.hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return myOccurenceNavigator.hasPreviousOccurence();
  }

  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    return myOccurenceNavigator.goNextOccurence();
  }

  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigator.goPreviousOccurence();
  }

  public String getNextOccurenceActionName() {
    return myOccurenceNavigator.getNextOccurenceActionName();
  }

  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigator.getPreviousOccurenceActionName();
  }

  public void invokeLocalFix(int idx) {
    if (myTree.getSelectionCount() != 1) return;
    final InspectionTreeNode node = (InspectionTreeNode)myTree.getSelectionPath().getLastPathComponent();
    if (node instanceof ProblemDescriptionNode) {
      final ProblemDescriptionNode problemNode = (ProblemDescriptionNode)node;
      final ProblemDescriptor descriptor = problemNode.getDescriptor();
      final RefElement element = problemNode.getElement();
      invokeFix(element, descriptor, idx);
    }
    else if (node instanceof RefElementNode) {
      RefElementNode elementNode = (RefElementNode)node;
      RefElement element = elementNode.getElement();
      ProblemDescriptor descriptor = elementNode.getProblem();
      if (descriptor != null) {
        invokeFix(element, descriptor, idx);
      }
    }
  }

  private void invokeFix(final RefElement element, final ProblemDescriptor descriptor, final int idx) {
    final LocalQuickFix[] fixes = descriptor.getFixes();
    if (fixes != null && fixes.length > idx && fixes[idx] != null) {
      PsiElement psiElement = element.getElement();
      if (psiElement != null && psiElement.isValid()) {
        if (!psiElement.isWritable()) {
          final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(myProject)
            .ensureFilesWritable(new VirtualFile[]{psiElement.getContainingFile().getVirtualFile()});
          if (operationStatus.hasReadonlyFiles()) {
            return;
          }
        }

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            Runnable command = new Runnable() {
              public void run() {
                CommandProcessor.getInstance().markCurrentCommandAsComplex(myProject);
                fixes[idx].applyFix(myProject, descriptor);
              }
            };
            CommandProcessor.getInstance().executeCommand(myProject, command, fixes[idx].getName(), null);
            final DescriptorProviderInspection tool = ((DescriptorProviderInspection)getSelectedTool());
            if (tool != null) {
              tool.ignoreProblem(element, descriptor, idx);
            }
            update();
          }
        });
      }
    }
  }

  protected class InvokeQuickFixAction extends AnAction {
    public InvokeQuickFixAction() {
      super("Apply a quickfix", "Apply an inspection quickfix", IconLoader.getIcon("/actions/createFromUsage.png"));

      registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcutSet(),
                                myTree);
    }

    public void update(AnActionEvent e) {
      if (!isSingleToolInSelection()) {
        e.getPresentation().setEnabled(false);
        return;
      }

      //noinspection ConstantConditions
      final @NotNull InspectionTool tool = getSelectedTool();
      final QuickFixAction[] quickFixes = tool.getQuickFixes(getSelectedElements());
      if (quickFixes == null || quickFixes.length == 0) {
        e.getPresentation().setEnabled(false);
        return;
      }

      ActionGroup fixes = new ActionGroup() {
        public AnAction[] getChildren(@Nullable AnActionEvent e) {
          List<QuickFixAction> children = new ArrayList<QuickFixAction>();
          for (QuickFixAction fix : quickFixes) {
            if (fix != null){
              children.add(fix);
            }
          }
          return children.toArray(new AnAction[children.size()]);
        }
      };

      e.getPresentation().setEnabled(!ActionListPopup.isGroupEmpty(fixes, e, new HashMap<AnAction, Presentation>()));
    }

    public void actionPerformed(AnActionEvent e) {
      final InspectionTool tool = getSelectedTool();
      if (tool == null) return;
      final QuickFixAction[] quickFixes = tool.getQuickFixes(getSelectedElements());
      ActionGroup fixes = new ActionGroup() {
        public AnAction[] getChildren(@Nullable AnActionEvent e) {
          List<QuickFixAction> children = new ArrayList<QuickFixAction>();
          for (QuickFixAction fix : quickFixes) {
            if (fix != null){
              children.add(fix);
            }
          }
          return children.toArray(new AnAction[children.size()]);
        }
      };

      DataContext dataContext = e.getDataContext();
      ListPopup popup = ActionListPopup.createListPopup(" Accept Resolution ", fixes, dataContext, false, false);

      Point location = getSelectedTreeNodeBounds();
      if (location == null) return;
      popup.show(location.x, location.y);
    }

    @Nullable
    private Point getSelectedTreeNodeBounds() {
      int row = myTree.getLeadSelectionRow();
      if (row == -1) return null;
      Rectangle rowBounds = myTree.getRowBounds(row);
      Point location = rowBounds.getLocation();
      location = new Point(location.x, location.y + rowBounds.height);
      SwingUtilities.convertPointToScreen(location, myTree);
      return location;
    }
  }

  public Project getProject() {
    return myProject;
  }

  public Object getData(String dataId) {
    if (dataId.equals(DataConstantsEx.INSPECTION_VIEW)) return this;
    TreePath[] paths = myTree.getSelectionPaths();

    if (paths == null) return null;

    if (paths.length > 1) {
      if (DataConstantsEx.PSI_ELEMENT_ARRAY.equals(dataId)) {
        return collectPsiElements();
      }
      else {
        return null;
      }
    }

    TreePath path = paths[0];

    InspectionTreeNode selectedNode = (InspectionTreeNode)path.getLastPathComponent();

    if (selectedNode instanceof RefElementNode) {
      final RefElementNode refElementNode = (RefElementNode)selectedNode;
      RefElement refElement = refElementNode.getElement();
      final RefElement item;
      if (refElement instanceof RefImplicitConstructor) {
        item = ((RefImplicitConstructor)refElement).getOwnerClass();
      }
      else {
        item = refElement;
      }

      if (!item.isValid()) return null;

      PsiElement psiElement = item.getElement();
      if (psiElement == null) return null;

      if (refElementNode.getProblem() != null) {
        psiElement = refElementNode.getProblem().getPsiElement();
        if (psiElement == null) return null;
      }

      if (DataConstants.NAVIGATABLE.equals(dataId)) {
        final VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
        if (virtualFile != null && virtualFile.isValid()) {
          return new OpenFileDescriptor(myProject, virtualFile, psiElement.getTextOffset());
        }
      }
      else if (DataConstants.PSI_ELEMENT.equals(dataId)) {
        return psiElement;
      }
    }
    else if (selectedNode instanceof ProblemDescriptionNode && DataConstants.NAVIGATABLE.equals(dataId)) {
      PsiElement psiElement = ((ProblemDescriptionNode)selectedNode).getDescriptor().getPsiElement();
      if (psiElement == null || !psiElement.isValid()) return null;
      return new OpenFileDescriptor(myProject, psiElement.getContainingFile().getVirtualFile(), psiElement.getTextOffset());
    }

    return null;
  }

  private PsiElement[] collectPsiElements() {
    RefElement[] refElements = getSelectedElements();
    List<PsiElement> psiElements = new ArrayList<PsiElement>();
    for (RefElement refElement : refElements) {
      PsiElement psiElement = refElement.getElement();
      if (psiElement != null && psiElement.isValid()) {
        psiElements.add(psiElement);
      }
    }

    return psiElements.toArray(new PsiElement[psiElements.size()]);
  }

  private void popupInvoked(Component component, int x, int y) {
    if (!isSingleToolInSelection()) return;

    final TreePath path;
    if (myTree.hasFocus()) {
      path = myTree.getLeadSelectionPath();
    }
    else {
      path = null;
    }

    if (path == null) return;

    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    actions.add(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES));

    final InspectionTool tool = getSelectedTool();
    if (tool == null) return;

    final QuickFixAction[] quickFixes = tool.getQuickFixes(getSelectedElements());
    if (quickFixes != null) {
      for (QuickFixAction quickFixe : quickFixes) {
        actions.add(quickFixe);
      }
    }
    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    if (key == null) return; //e.g. DummyEntryPointsTool
    actions.add(new AnAction("Edit Tool Settings") {
      public void actionPerformed(AnActionEvent e) {
        new SwitchOffToolAction(key).editToolSettings(myProject, myInspectionProfile);
        InspectionResultsView.this.update();
      }
    });
    actions.add(ActionManager.getInstance().getAction(IdeActions.GROUP_VERSION_CONTROLS));

    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.CODE_INSPECTION, actions);
    menu.getComponent().show(component, x, y);
  }

  public ProblemDescriptor[] getSelectedDescriptors() {
    final InspectionTool tool = getSelectedTool();
    if (myTree.getSelectionCount() == 0 || !(tool instanceof DescriptorProviderInspection)) return EMPTY_DESCRIPTORS;
    final TreePath[] paths = myTree.getSelectionPaths();
    Collection<RefElement> out = new ArrayList<RefElement>();
    Set<ProblemDescriptor> descriptors = new HashSet<ProblemDescriptor>();
    for (TreePath path : paths) {
      Object node = path.getLastPathComponent();
      if (node instanceof ProblemDescriptionNode) {
        final ProblemDescriptionNode problemNode = (ProblemDescriptionNode)node;
        descriptors.add(problemNode.getDescriptor());
      } else if (node instanceof InspectionTreeNode){
        addElementsInNode((InspectionTreeNode)node, out);
      }
      for (RefElement element : out) {
        final ProblemDescriptor[] descriptions = ((DescriptorProviderInspection)tool).getDescriptions(element);
        if (descriptions != null) descriptors.addAll(Arrays.asList(descriptions));
      }
    }

    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  public RefElement[] getSelectedElements() {
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths != null) {
      final InspectionTool selectedTool = getSelectedTool();
      if (selectedTool == null) return EMPTY_ELEMENTS_ARRAY;

      Set<RefElement> result = new HashSet<RefElement>();
      for (TreePath selectionPath : selectionPaths) {
        final InspectionTreeNode node = (InspectionTreeNode)selectionPath.getLastPathComponent();
        addElementsInNode(node, result);
      }
      return result.toArray(new RefElement[result.size()]);
    }
    return EMPTY_ELEMENTS_ARRAY;
  }

  public void addElementsInNode(InspectionTreeNode node, Collection<RefElement> out) {
    if (!node.isValid()) return;
    if (node instanceof RefElementNode) {
      out.add(((RefElementNode)node).getElement());
    }
    else if (node instanceof ProblemDescriptionNode){
      out.add(((ProblemDescriptionNode)node).getElement());
    }
    else if (node instanceof InspectionPackageNode || node instanceof InspectionNode) {
      final Enumeration children = node.children();
      while (children.hasMoreElements()) {
        InspectionTreeNode child = (InspectionTreeNode)children.nextElement();
        addElementsInNode(child, out);
      }
    }
  }

  @Nullable
  public InspectionTool getSelectedTool() {
    final TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) return null;
    InspectionTool tool = null;
    for (TreePath path : paths) {
      Object[] nodes = path.getPath();
      for (int j = nodes.length - 1; j >= 0; j--) {
        Object node = nodes[j];
        if (node instanceof InspectionNode) {
          if (tool == null) {
            tool = ((InspectionNode)node).getTool();
          }
          else if (tool != ((InspectionNode)node).getTool()) {
            return null;
          }
          break;
        }
      }
    }

    return tool;
  }

  public boolean isSingleToolInSelection() {
    return getSelectedTool() != null;
  }

  private class RerunAction extends AnAction {
    public RerunAction(JComponent comp) {
      super("Rerun Inspection", "Rerun Inspection", IconLoader.getIcon("/actions/refreshUsages.png"));
      registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myScope.isValid());
    }

    public void actionPerformed(AnActionEvent e) {
      rerun();
    }
  }

  private void rerun() {
    if (myScope.isValid()) {
      final InspectionManagerEx inspectionManagerEx = ((InspectionManagerEx)InspectionManagerEx.getInstance(myProject));
      inspectionManagerEx.setExternalProfile(myInspectionProfile);
      inspectionManagerEx.doInspections(myScope);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          inspectionManagerEx.setExternalProfile(null);
        }
      });
    }
  }


  private static class InspectionCodeSettingsPanel extends InspectCodePanel {
    public InspectionCodeSettingsPanel(final InspectionManagerEx manager, final AnalysisScope scope) {
      super(manager, scope);
      setOKButtonText("OK");
    }

    protected void setOKActionEnabled(boolean isEnabled) {
      super.setOKActionEnabled(true);
    }
  }
}
