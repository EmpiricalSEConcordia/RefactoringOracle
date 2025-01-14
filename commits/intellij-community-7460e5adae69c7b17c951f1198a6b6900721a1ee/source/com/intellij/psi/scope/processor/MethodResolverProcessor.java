package com.intellij.psi.scope.processor;

import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;

import java.util.ArrayList;

public class MethodResolverProcessor extends MethodCandidatesProcessor implements NameHint, ElementClassHint, PsiResolverProcessor {
  private boolean myStopAcceptingCandidates = false;

  public MethodResolverProcessor(PsiMethodCallExpression place){
    super(place, new PsiConflictResolver[]{new JavaMethodsConflictResolver(place.getArgumentList())}, new ArrayList());
    setArguments(place.getArgumentList());
    obtainTypeArguments(place);
  }

  public MethodResolverProcessor(PsiClass classConstr, PsiExpressionList argumentList, PsiElement place) {
    super(place, new PsiConflictResolver[]{new JavaMethodsConflictResolver(argumentList)}, new ArrayList());
    setIsConstructor(true);
    setAccessClass(classConstr);
    setArguments(argumentList);
  }

  public MethodResolverProcessor(String name, PsiClass accessClass, PsiExpressionList argumentList, PsiElement place) {
    super(place, new PsiConflictResolver[]{new JavaMethodsConflictResolver(argumentList)}, new ArrayList());

    setName(name);
    setIsConstructor(false);
    setAccessClass(accessClass);
    setArguments(argumentList);
  }

  public void setArguments(PsiExpressionList argList){
    super.setArgumentList(argList);
    // todo[dsl]: push type arguments thru to JavaMethodsConflictResolver
    ((JavaMethodsConflictResolver)getResolvers()[0]).setArgumentsList(argList);
  }

  public String getProcessorType(){
    return "method resolver";
  }

  public void handleEvent(Event event, Object associated) {
    super.handleEvent(event, associated);
    if (event == Event.CHANGE_LEVEL) {
      myStopAcceptingCandidates = myResults.size() > 0;
    }
  }

  public void add(PsiElement element, PsiSubstitutor substitutor) {
    if (!myStopAcceptingCandidates) {
      super.add(element, substitutor);
    }
  }
}
