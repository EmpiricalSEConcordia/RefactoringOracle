package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 * Date: Jan 19, 2005
 */
public abstract class DependenciesBuilder {
  private Project myProject;
  private final AnalysisScope myScope;
  private final AnalysisScope myScopeOfInterest;
  private final Map<PsiFile, Set<PsiFile>> myDependencies = new HashMap<PsiFile, Set<PsiFile>>();
  protected int myTotalFileCount;
  protected int myFileCount = 0;
  protected int myTransitive = 0;

  protected DependenciesBuilder(@NotNull final Project project, @NotNull final AnalysisScope scope) {
    this(project, scope, null);
  }

  public DependenciesBuilder(final Project project, final AnalysisScope scope, final AnalysisScope scopeOfInterest) {
    myProject = project;
    myScope = scope;
    myScopeOfInterest = scopeOfInterest;
    myTotalFileCount = scope.getFileCount();
  }

  public void setInitialFileCount(final int fileCount) {
    myFileCount = fileCount;
  }

  public void setTotalFileCount(final int totalFileCount) {
    myTotalFileCount = totalFileCount;
  }

  public int getTotalFileCount() {
    return myTotalFileCount;
  }

  public Map<PsiFile, Set<PsiFile>> getDependencies() {
    return myDependencies;
  }

  public Map<PsiFile, Set<PsiFile>> getDirectDependencies() {
    return getDependencies();
  }

  public AnalysisScope getScope() {
    return myScope;
  }

  public AnalysisScope getScopeOfInterest() {
    return myScopeOfInterest;
  }

  public Project getProject() {
    return myProject;
  }

  public abstract String getRootNodeNameInUsageView();

  public abstract String getInitialUsagesPosition();

  public abstract boolean isBackward();

  public abstract void analyze();

  public Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> getIllegalDependencies(){
    Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> result = new HashMap<PsiFile, Map<DependencyRule, Set<PsiFile>>>();
    DependencyValidationManager validator = DependencyValidationManager.getInstance(myProject);
    for (PsiFile file : getDirectDependencies().keySet()) {
      Set<PsiFile> deps = getDirectDependencies().get(file);
      Map<DependencyRule, Set<PsiFile>> illegal = null;
      for (PsiFile dependency : deps) {
        final DependencyRule rule = isBackward() ?
                                    validator.getViolatorDependencyRule(dependency, file) :
                                    validator.getViolatorDependencyRule(file, dependency);
        if (rule != null) {
          if (illegal == null) {
            illegal = new HashMap<DependencyRule, Set<PsiFile>>();
            result.put(file, illegal);
          }
          Set<PsiFile> illegalFilesByRule = illegal.get(rule);
          if (illegalFilesByRule == null) {
            illegalFilesByRule = new HashSet<PsiFile>();
          }
          illegalFilesByRule.add(dependency);
          illegal.put(rule, illegalFilesByRule);
        }
      }
    }
    return result;
  }

  public List<List<PsiFile>> findPaths(PsiFile from, PsiFile to) {
    return findPaths(from, to, new HashSet<PsiFile>());
  }

  private List<List<PsiFile>> findPaths(PsiFile from, PsiFile to, Set<PsiFile> processed) {
    final List<List<PsiFile>> result = new ArrayList<List<PsiFile>>();
    final Set<PsiFile> reachable = getDirectDependencies().get(from);
    if (reachable != null) {
      if (reachable.contains(to)) {
        final ArrayList<PsiFile> path = new ArrayList<PsiFile>();
        result.add(path);
        return result;
      }
      if (!processed.contains(from)) {
        processed.add(from);
        for (PsiFile file : reachable) {
          if (!getScope().contains(file)) { //exclude paths through scope
            final List<List<PsiFile>> paths = findPaths(file, to, processed);
            for (List<PsiFile> path : paths) {
              path.add(0, file);
            }
            result.addAll(paths);
          }
        }
      }
    }
    return result;
  }



  public static void analyzeFileDependencies(PsiFile file, DependencyProcessor processor) {
    file.accept(new DependenciesWalker(processor));
  }

  public boolean isTransitive() {
    return myTransitive > 0;
  }

  public int getTransitiveBorder() {
    return myTransitive;
  }

  public interface DependencyProcessor {
    void process(PsiElement place, PsiElement dependency);
  }


  private static class DependenciesWalker extends PsiRecursiveElementVisitor {
    private final DependencyProcessor myProcessor;

    public DependenciesWalker(DependencyProcessor processor) {
      myProcessor = processor;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public void visitElement(PsiElement element) {
      super.visitElement(element);
      PsiReference[] refs = element.getReferences();
      for (PsiReference ref : refs) {
        PsiElement resolved = ref.resolve();
        if (resolved != null) {
          myProcessor.process(ref.getElement(), resolved);
        }
      }
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      // empty
      // TODO: thus we'll skip property references and references to file resources. We can't validate them anyway now since
      // TODO: rule syntax does not allow this.
    }

    public void visitDocComment(PsiDocComment comment) {
      //empty
    }

    public void visitImportStatement(PsiImportStatement statement) {
      if (!DependencyValidationManager.getInstance(statement.getProject()).skipImportStatements()) {
        visitElement(statement);
      }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiMethod psiMethod = expression.resolveMethod();
      if (psiMethod != null) {
        PsiType returnType = psiMethod.getReturnType();
        if (returnType != null) {
          PsiClass psiClass = PsiUtil.resolveClassInType(returnType);
          if (psiClass != null) {
            myProcessor.process(expression, psiClass);
          }
        }
      }
    }
  }
}
