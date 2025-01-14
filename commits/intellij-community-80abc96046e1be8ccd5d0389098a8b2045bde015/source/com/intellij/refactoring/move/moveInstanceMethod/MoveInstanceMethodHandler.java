package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;

import java.util.*;

/**
 * @author ven
 */
public class MoveInstanceMethodHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler");
  static final String REFACTORING_NAME = "Move Instance Method";

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }

    if (element == null) return;
    if (element instanceof PsiIdentifier) element = element.getParent();

    if(!(element instanceof PsiMethod)) {
      String message = "Cannot perform the refactoring.\n" +
              "The caret should be positioned at the name of the method to be refactored.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.CONVERT_TO_INSTANCE_METHOD, project);
      return;
    }
    if(LOG.isDebugEnabled()) {
      LOG.debug("Move Instance Method invoked");
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1 || !(elements[0] instanceof PsiMethod)) return;
    final PsiMethod method = ((PsiMethod)elements[0]);
    if (method.isConstructor()) {
      String message = "Cannot perform the refactoring.\n" +
                       "Move method is not supported for constructors";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_INSTANCE_METHOD, project);
      return;
    }

    if (PsiUtil.typeParametersIterator(method.getContainingClass()).hasNext()) {
      String message = "Cannot perform the refactoring.\n" +
                       "Move method is not supported for generic classes";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_INSTANCE_METHOD, project);
      return;
    }

    if (PsiSuperMethodUtil.findSuperMethods(method).length > 0 ||
        method.getManager().getSearchHelper().findOverridingMethods(method, GlobalSearchScope.allScope(project), true).length > 0) {
      String message = "Cannot perform the refactoring.\n" +
                       "Move method is not supported when method is a part of inheritance hierarchy";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_INSTANCE_METHOD, project);
      return;
    }

    final Set<PsiClass> classes = MoveInstanceMembersUtil.getThisClassesToMembers(method).keySet();
    for (PsiClass aClass : classes) {
      if (aClass instanceof JspClass) {
        String message = "Cannot perform the refactoring.\n" +
                         "Synthetic jsp class is referenced in the method";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_INSTANCE_METHOD, project);
        return;
      }
    }

    List<PsiVariable> suitableVariables = new ArrayList<PsiVariable>();
    List<PsiVariable> allVariables = new ArrayList<PsiVariable>();
    allVariables.addAll(Arrays.asList(method.getParameterList().getParameters()));
    allVariables.addAll(Arrays.asList(method.getContainingClass().getFields()));
    boolean classTypesFound = false;
    boolean resolvableClassesFound = false;
    boolean classesInProjectFound = false;
    for (PsiVariable variable : allVariables) {
      final PsiType type = variable.getType();
      if (type instanceof PsiClassType && !((PsiClassType)type).hasParameters()) {
        classTypesFound = true;
        final PsiClass psiClass = ((PsiClassType)type).resolve();
        if (psiClass != null && !(psiClass instanceof PsiTypeParameter)) {
          resolvableClassesFound = true;
          final boolean inProject = method.getManager().isInProject(psiClass);
          if (inProject) {
            classesInProjectFound = true;
            suitableVariables.add(variable);
          }
        }
      }
    }

    if (suitableVariables.isEmpty()) {
      String message = null;
      if (!classTypesFound) {
        message = "There are no variables that have a reference type";
      }
      else if (!resolvableClassesFound) {
        message = "All candidate variables have unknown types";
      }
      else if (!classesInProjectFound) {
        message = "All candidate variables have types that are not in project";
      }
      LOG.assertTrue(message != null);
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME,
                                              "Cannot perform refactoring.\n" + message,
                                              HelpID.CONVERT_TO_INSTANCE_METHOD, project);
      return;
    }

    new MoveInstanceMethodDialog(
      method,
      suitableVariables.toArray(new PsiVariable[suitableVariables.size()])).show();
  }

  public static String suggestParameterNameForThisClass(final PsiClass thisClass) {
    PsiManager manager = thisClass.getManager();
    PsiType type = manager.getElementFactory().createType(thisClass);
    final SuggestedNameInfo suggestedNameInfo = manager.getCodeStyleManager()
        .suggestVariableName(VariableKind.PARAMETER, null, null, type);
    String suggestedName = suggestedNameInfo.names.length > 0 ? suggestedNameInfo.names[0] : "";
    return suggestedName;
  }

  public static Map<PsiClass, String> suggestParameterNames(final PsiMethod method, final PsiVariable targetVariable) {
    final Map<PsiClass, Set<PsiMember>> classesToMembers = MoveInstanceMembersUtil.getThisClassesToMembers(method);
    Map<PsiClass, String> result = new LinkedHashMap<PsiClass, String>();
    for (Map.Entry<PsiClass,Set<PsiMember>> entry : classesToMembers.entrySet()) {
      PsiClass aClass = entry.getKey();
      final Set<PsiMember> members = entry.getValue();
      if (members.size() == 1 && members.contains(targetVariable)) continue;
      result.put(aClass, suggestParameterNameForThisClass(aClass));
    }
    return result;
  }
}
