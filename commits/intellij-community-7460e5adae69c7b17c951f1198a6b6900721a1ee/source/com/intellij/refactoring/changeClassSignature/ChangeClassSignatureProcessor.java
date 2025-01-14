package com.intellij.refactoring.changeClassSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureUtil;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author dsl
 */
public class ChangeClassSignatureProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeClassSignature.ChangeClassSignatureProcessor");
  private PsiClass myClass;
  private final TypeParameterInfo[] myNewSignature;

  public ChangeClassSignatureProcessor(Project project,
                                       PsiClass aClass,
                                       TypeParameterInfo[] newSignature) {
    super(project);
    myClass = aClass;
    myNewSignature = newSignature;
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1);
    LOG.assertTrue(elements[0] instanceof PsiClass);
    myClass = (PsiClass) elements[0];
  }

  protected String getCommandName() {
    return "Change Class Signature";
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    return super.isPreviewUsages(usages);
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new ChangeClassSigntaureViewDescriptor(myClass, usages, refreshCommand);
  }

  protected UsageInfo[] findUsages() {
    PsiSearchHelper searchHelper = myClass.getManager().getSearchHelper();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
    PsiReference[] references = searchHelper.findReferences(myClass, projectScope, false);
    List<UsageInfo> result = new ArrayList<UsageInfo>();

    boolean hadTypeParameters = myClass.getTypeParameters().length > 0;
    for (int i = 0; i < references.length; i++) {
      final PsiReference reference = references[i];
      if (reference.getElement() instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = ((PsiJavaCodeReferenceElement)reference.getElement());
        PsiElement parent = referenceElement.getParent();
        if (parent instanceof PsiTypeElement || parent instanceof PsiNewExpression
            || parent instanceof PsiAnonymousClass || parent instanceof PsiReferenceList) {
          if (!hadTypeParameters || referenceElement.getTypeParameters().length > 0) {
            result.add(new UsageInfo(referenceElement));
          }
        }
      }
    }
    return (UsageInfo[])result.toArray(new UsageInfo[result.size()]);
  }

  protected void performRefactoring(UsageInfo[] usages) {
    LvcsAction lvcsAction = LvcsIntegration.checkinFilesBeforeRefactoring(myProject, getCommandName());
    try {
      doRefactoring(usages);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      LvcsIntegration.checkinFilesAfterRefactoring(myProject, lvcsAction);
    }
  }

  private void doRefactoring(UsageInfo[] usages) throws IncorrectOperationException {
    final PsiTypeParameter[] typeParameters = myClass.getTypeParameters();
    boolean[] toRemoveParms = detectRemovedParameters(typeParameters);

    for (int i = 0; i < usages.length; i++) {
      final UsageInfo usage = usages[i];
      LOG.assertTrue(usage.getElement() instanceof PsiJavaCodeReferenceElement);
      processUsage(usage, typeParameters, toRemoveParms);
    }

    changeClassSignature(typeParameters, toRemoveParms);
  }

  private void changeClassSignature(final PsiTypeParameter[] originalTypeParameters, boolean[] toRemoveParms)
    throws IncorrectOperationException {
    PsiElementFactory factory = myClass.getManager().getElementFactory();
    List<PsiTypeParameter> newTypeParameters = new ArrayList<PsiTypeParameter>();
    for (int i = 0; i < myNewSignature.length; i++) {
      final TypeParameterInfo info = myNewSignature[i];
      int oldIndex = info.getOldParameterIndex();
      if(oldIndex >= 0) {
        newTypeParameters.add(originalTypeParameters[oldIndex]);
      }
      else {
        newTypeParameters.add(factory.createTypeParameterFromText(info.getNewName(), null));
      }
    }
    ChangeSignatureUtil.synchronizeList(myClass.getTypeParameterList(),
                                        newTypeParameters,
                                        TypeParameterList.INSTANCE,
                                        toRemoveParms);
  }

  private boolean[] detectRemovedParameters(final PsiTypeParameter[] originaltypeParameters) {
    final boolean[] toRemoveParms = new boolean[originaltypeParameters.length];
    Arrays.fill(toRemoveParms, true);
    for (int i = 0; i < myNewSignature.length; i++) {
      final TypeParameterInfo info = myNewSignature[i];
      int oldParameterIndex = info.getOldParameterIndex();
      if (oldParameterIndex >= 0) {
        toRemoveParms[oldParameterIndex] = false;
      }
    }
    return toRemoveParms;
  }

  private void processUsage(final UsageInfo usage, final PsiTypeParameter[] originalTypeParameters, final boolean[] toRemoveParms)
    throws IncorrectOperationException {
    PsiElementFactory factory = myClass.getManager().getElementFactory();
    PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)usage.getElement();
    PsiSubstitutor usageSubstitutor = determineUsageSubstitutor(referenceElement);

    PsiReferenceParameterList referenceParameterList = referenceElement.getParameterList();
    PsiTypeElement[] oldValues = referenceParameterList.getTypeParameterElements();
    if (oldValues.length != originalTypeParameters.length) return;
    List<PsiTypeElement> newValues = new ArrayList<PsiTypeElement>();
    for (int j = 0; j < myNewSignature.length; j++) {
      final TypeParameterInfo info = myNewSignature[j];
      int oldIndex = info.getOldParameterIndex();
      if (oldIndex >= 0) {
        newValues.add(oldValues[oldIndex]);
      }
      else {
        PsiType type = info.getDefaultValue().getType(myClass.getLBrace());

        PsiTypeElement newValue = factory.createTypeElement(usageSubstitutor.substitute(type));
        newValues.add(newValue);
      }
    }
    ChangeSignatureUtil.synchronizeList(
      referenceParameterList,
      newValues,
      ReferenceParameterList.INSTANCE, toRemoveParms
      );
  }

  private PsiSubstitutor determineUsageSubstitutor(PsiJavaCodeReferenceElement referenceElement) {
    PsiType[] typeArguments = referenceElement.getTypeParameters();
    PsiSubstitutor usageSubstitutor = PsiSubstitutor.EMPTY;
    PsiTypeParameter[] typeParameters = myClass.getTypeParameters();
    if (typeParameters.length == typeArguments.length) {
      for (int i = 0; i < typeParameters.length; i++) {
        usageSubstitutor = usageSubstitutor.put(typeParameters[i], typeArguments[i]);
      }
    }
    return usageSubstitutor;
  }

  private static class ReferenceParameterList
    implements ChangeSignatureUtil.ChildrenGenerator<PsiReferenceParameterList, PsiTypeElement> {
    private static final ReferenceParameterList INSTANCE = new ReferenceParameterList();
    public List<PsiTypeElement> getChildren(PsiReferenceParameterList list) {
      return Arrays.asList(list.getTypeParameterElements());
    }
  }

  private static class TypeParameterList
    implements ChangeSignatureUtil.ChildrenGenerator<PsiTypeParameterList, PsiTypeParameter> {
    private static final TypeParameterList INSTANCE = new TypeParameterList();

    public List<PsiTypeParameter> getChildren(PsiTypeParameterList psiTypeParameterList) {
      return Arrays.asList(psiTypeParameterList.getTypeParameters());
    }
  }
}
