package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ForwardDependenciesBuilder extends DependenciesBuilder {
  private Map<PsiFile, Set<PsiFile>> myDirectDependencies = new HashMap<PsiFile, Set<PsiFile>>();

  public ForwardDependenciesBuilder(@NotNull Project project, @NotNull AnalysisScope scope) {
    super(project, scope);
  }

  public ForwardDependenciesBuilder(final Project project, final AnalysisScope scope, final AnalysisScope scopeOfInterest) {
    super(project, scope, scopeOfInterest);
  }

  public ForwardDependenciesBuilder(final Project project, final AnalysisScope scope, final int transitive) {
    super(project, scope);
    myTransitive = transitive;
  }

  public String getRootNodeNameInUsageView(){
    return AnalysisScopeBundle.message("forward.dependencies.usage.view.root.node.text");
  }

  public String getInitialUsagesPosition(){
    return AnalysisScopeBundle.message("forward.dependencies.usage.view.initial.text");
  }

  public boolean isBackward(){
    return false;
  }

  public void analyze() {
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    psiManager.startBatchFilesProcessingMode();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
    try {
      getScope().accept(new PsiRecursiveElementVisitor() {
        public void visitFile(final PsiFile file) {
          visit(file, fileIndex, psiManager, 0);
        }
      });
    }
    finally {
      psiManager.finishBatchFilesProcessingMode();
    }
  }

  private void visit(final PsiFile file, final ProjectFileIndex fileIndex, final PsiManager psiManager, int depth) {

    final FileViewProvider viewProvider = file.getViewProvider();
    if (viewProvider.getBaseLanguage() != file.getLanguage()) return;

    if (getScopeOfInterest() != null && !getScopeOfInterest().contains(file)) return;

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      if (indicator.isCanceled()) {
        throw new ProcessCanceledException();
      }
      indicator.setText(AnalysisScopeBundle.message("package.dependencies.progress.text"));
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        indicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, getProject()));
      }
      if ( myTotalFileCount > 0) {
        indicator.setFraction(((double)++ myFileCount) / myTotalFileCount);
      }
    }

    final Set<PsiFile> collectedDeps = new HashSet<PsiFile>();
    final HashSet<PsiFile> processed = new HashSet<PsiFile>();
    collectedDeps.add(file);
    do {
      if (depth++ > getTransitiveBorder()) return;
      for (PsiFile psiFile : new HashSet<PsiFile>(collectedDeps)) {
        final Set<PsiFile> found = new HashSet<PsiFile>();
        if (!processed.contains(psiFile)) {
          processed.add(psiFile);
          analyzeFileDependencies(psiFile, new DependencyProcessor() {
            public void process(PsiElement place, PsiElement dependency) {
              PsiFile dependencyFile = dependency.getContainingFile();
              if (dependencyFile != null) {
                if (viewProvider == dependencyFile.getViewProvider()) return;
                if (dependencyFile.isPhysical()) {
                  final VirtualFile virtualFile = dependencyFile.getVirtualFile();
                  if (virtualFile != null &&
                      (fileIndex.isInContent(virtualFile) ||
                       fileIndex.isInLibraryClasses(virtualFile) ||
                       fileIndex.isInLibrarySource(virtualFile))) {
                    found.add(dependencyFile);
                  }
                }
              }
            }
          });
          Set<PsiFile> deps = getDependencies().get(file);
          if (deps == null) {
            deps = new HashSet<PsiFile>();
            getDependencies().put(file, deps);
          }
          deps.addAll(found);

          getDirectDependencies().put(psiFile, new HashSet<PsiFile>(found));

          collectedDeps.addAll(found);

          psiManager.dropResolveCaches();
        }
      }
      collectedDeps.removeAll(processed);
    }
    while (isTransitive() && !collectedDeps.isEmpty());
  }

  public Map<PsiFile, Set<PsiFile>> getDirectDependencies() {
    return myDirectDependencies;
  }

}