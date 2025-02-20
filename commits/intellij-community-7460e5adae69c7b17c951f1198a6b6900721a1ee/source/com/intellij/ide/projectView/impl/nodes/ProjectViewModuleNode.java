package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProjectViewModuleNode extends AbstractModuleNode {

  static private final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode");

  public ProjectViewModuleNode(Project project, Module value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public ProjectViewModuleNode(Project project, Object value, ViewSettings viewSettings) {
    this(project, (Module)value, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildren() {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(getValue());
    ModuleFileIndex moduleFileIndex = rootManager.getFileIndex();

    final VirtualFile[] contentRoots = rootManager.getContentRoots();
    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>(contentRoots.length + 1);
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    for (int idx = 0; idx < contentRoots.length; idx++) {
      final VirtualFile contentRoot = contentRoots[idx];

      LOG.assertTrue(contentRoot.isDirectory());

      if (!moduleFileIndex.isInContent(contentRoot)) continue;

      final PsiDirectory psiDirectory = psiManager.findDirectory(contentRoot);
      LOG.assertTrue(psiDirectory != null);

      children.add(new PsiDirectoryNode(getProject(), psiDirectory, getSettings()));
    }
    children.add(new LibraryGroupNode(getProject(), new LibraryGroupElement(getValue()), getSettings()));
    return children;
  }

}
