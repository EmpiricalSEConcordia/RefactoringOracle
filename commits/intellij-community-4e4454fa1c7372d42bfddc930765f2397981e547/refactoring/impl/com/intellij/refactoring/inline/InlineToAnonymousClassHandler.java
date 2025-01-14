package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class InlineToAnonymousClassHandler {
  private InlineToAnonymousClassHandler() {
  }

  public static void invoke(final Project project, final Editor editor, final PsiClass psiClass) {
    PsiCall callToInline = findCallToInline(editor);

    String errorMessage = getCannotInlineMessage(psiClass);
    if (errorMessage != null) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.to.anonymous.refactoring"), errorMessage, null, project);
      return;
    }

    InlineToAnonymousClassDialog dlg = new InlineToAnonymousClassDialog(project, psiClass, callToInline);
    dlg.show();
  }

  public static PsiCall findCallToInline(final Editor editor) {
    PsiCall callToInline = null;
    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor) : null;
    if (reference != null) {
      callToInline = RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)reference.getElement());
    }
    return callToInline;
  }

  @Nullable
  public static String getCannotInlineMessage(final PsiClass psiClass) {
    if (psiClass.isAnnotationType()) {
      return "Annotation types cannot be inlined";
    }
    if (psiClass.isInterface()) {
      return "Interfaces cannot be inlined";
    }
    if (psiClass.isEnum()) {
      return "Enums cannot be inlined";
    }
    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return RefactoringBundle.message("inline.to.anonymous.no.abstract");
    }
    if (!psiClass.getManager().isInProject(psiClass)) {
      return "Library classes cannot be inlined";
    }

    if (ClassInheritorsSearch.search(psiClass).findFirst() != null) {
      return RefactoringBundle.message("inline.to.anonymous.no.inheritors");
    }

    PsiClassType[] classTypes = psiClass.getExtendsListTypes();
    for(PsiClassType classType: classTypes) {
      PsiClass superClass = classType.resolve();
      if (superClass == null) {
        return "Class cannot be inlined because its superclass cannot be resolved";
      }
    }

    final PsiClassType[] interfaces = psiClass.getImplementsListTypes();
    if (interfaces.length > 1) {
      return RefactoringBundle.message("inline.to.anonymous.no.multiple.interfaces");
    }
    if (interfaces.length == 1) {
      if (interfaces [0].resolve() == null) {
        return "Class cannot be inlined because an interface implemented by it cannot be resolved";
      }
      final PsiClass superClass = psiClass.getSuperClass();
      if (superClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
        PsiClassType interfaceType = interfaces[0];
        if (!isRedundantImplements(superClass, interfaceType)) {
          return RefactoringBundle.message("inline.to.anonymous.no.superclass.and.interface");
        }
      }
    }

    final PsiMethod[] methods = psiClass.getMethods();
    for(PsiMethod method: methods) {
      if (method.isConstructor()) {
        PsiReturnStatement stmt = findReturnStatement(method);
        if (stmt != null) {
          return "Class cannot be inlined because its constructor contains 'return' statements";
        }
      }
      else if (method.findSuperMethods().length == 0) {
        if (!ReferencesSearch.search(method).forEach(new AllowedUsagesProcessor(psiClass))) {
          return "Class cannot be inlined because it has usages of methods not inherited from its superclass or interface";
        }
      }
      if (method.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
        return "Class cannot be inlined because it has static methods";
      }
    }

    final PsiClass[] innerClasses = psiClass.getInnerClasses();
    for(PsiClass innerClass: innerClasses) {
      PsiModifierList classModifiers = innerClass.getModifierList();
      if (classModifiers.hasModifierProperty(PsiModifier.STATIC)) {
        return "Class cannot be inlined because it has static inner classes";
      }
      if (!ReferencesSearch.search(innerClass).forEach(new AllowedUsagesProcessor(psiClass))) {
        return "Class cannot be inlined because it has usages of its inner classes";
      }
    }

    final PsiField[] fields = psiClass.getFields();
    for(PsiField field: fields) {
      final PsiModifierList fieldModifiers = field.getModifierList();
      if (fieldModifiers != null && fieldModifiers.hasModifierProperty(PsiModifier.STATIC)) {
        if (!fieldModifiers.hasModifierProperty(PsiModifier.FINAL)) {
          return "Class cannot be inlined because it has static non-final fields";
        }
        Object initValue = null;
        final PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          initValue = JavaPsiFacade.getInstance(psiClass.getProject()).getConstantEvaluationHelper().computeConstantExpression(initializer);
        }
        if (initValue == null) {
          return "Class cannot be inlined because it has static fields with non-constant initializers";
        }
      }
      if (!ReferencesSearch.search(field).forEach(new AllowedUsagesProcessor(psiClass))) {
        return "Class cannot be inlined because it has usages of fields not inherited from its superclass";
      }
    }

    final PsiClassInitializer[] initializers = psiClass.getInitializers();
    for(PsiClassInitializer initializer: initializers) {
      final PsiModifierList modifiers = initializer.getModifierList();
      if (modifiers != null && modifiers.hasModifierProperty(PsiModifier.STATIC)) {
        return "Class cannot be inlined because it has static initializers";
      }
    }

    return null;
  }

  static boolean isRedundantImplements(final PsiClass superClass, final PsiClassType interfaceType) {
    boolean redundantImplements = false;
    PsiClassType[] superClassInterfaces = superClass.getImplementsListTypes();
    for(PsiClassType superClassInterface: superClassInterfaces) {
      if (superClassInterface.equals(interfaceType)) {
        redundantImplements = true;
        break;
      }
    }
    return redundantImplements;
  }

  private static PsiReturnStatement findReturnStatement(final PsiMethod method) {
    final Ref<PsiReturnStatement> stmt = Ref.create(null);
    method.accept(new PsiRecursiveElementVisitor() {
      public void visitReturnStatement(final PsiReturnStatement statement) {
        super.visitReturnStatement(statement);
        stmt.set(statement);
      }
    });
    return stmt.get();
  }

  private static class AllowedUsagesProcessor implements Processor<PsiReference> {
    private final PsiElement myPsiElement;

    public AllowedUsagesProcessor(final PsiElement psiElement) {
      myPsiElement = psiElement;
    }

    public boolean process(final PsiReference psiReference) {
      if (PsiTreeUtil.isAncestor(myPsiElement, psiReference.getElement(), false)) {
        return true;
      }
      PsiElement element = psiReference.getElement();
      if (element instanceof PsiReferenceExpression) {
        PsiExpression qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
        while (qualifier instanceof PsiParenthesizedExpression) {
          qualifier = ((PsiParenthesizedExpression) qualifier).getExpression();
        }
        if (qualifier instanceof PsiNewExpression) {
          PsiNewExpression newExpr = (PsiNewExpression) qualifier;
          PsiJavaCodeReferenceElement classRef = newExpr.getClassReference();
          if (classRef != null && myPsiElement.equals(classRef.resolve())) {
            return true;
          }
        }
      }
      return false;
    }
  }
}