/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine.evaluation;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.codeinsight.RuntimeTypeEvaluator;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.DebuggerExpressionComboBox;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PairFunction;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 7, 2005
 */
public class DefaultCodeFragmentFactory implements CodeFragmentFactory {
  private static final class SingletonHolder {
    public static final DefaultCodeFragmentFactory ourInstance = new DefaultCodeFragmentFactory();
  }

  public static DefaultCodeFragmentFactory getInstance() {
    return SingletonHolder.ourInstance;
  }

  public JavaCodeFragment createPresentationCodeFragment(final TextWithImports item, final PsiElement context, final Project project) {
    return createCodeFragment(item, context, project);
  }

  public JavaCodeFragment createCodeFragment(TextWithImports item, PsiElement context, final Project project) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    final String text = item.getText();

    final JavaCodeFragment fragment;
    if (CodeFragmentKind.EXPRESSION == item.getKind()) {
      final String expressionText = StringUtil.endsWithChar(text, ';')? text.substring(0, text.length() - 1) : text;
      fragment = elementFactory.createExpressionCodeFragment(expressionText, context, null, true);
    }
    else /*if (CodeFragmentKind.CODE_BLOCK == item.getKind())*/ {
      fragment = elementFactory.createCodeBlockCodeFragment(text, context, true);
    }

    if(item.getImports().length() > 0) {
      fragment.addImportsFromString(item.getImports());
    }
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    //noinspection HardCodedStringLiteral
    fragment.putUserData(DebuggerExpressionComboBox.KEY, "DebuggerComboBoxEditor.IS_DEBUGGER_EDITOR");
    fragment.putCopyableUserData(JavaCompletionUtil.DYNAMIC_TYPE_EVALUATOR, new PairFunction<PsiExpression, CompletionParameters, PsiType>() {
      public PsiType fun(PsiExpression expression, CompletionParameters parameters) {
        if (!RuntimeTypeEvaluator.isSubtypeable(expression)) {
          return null;
        }

        if (parameters.getInvocationCount() == 1 && JavaCompletionUtil.containsMethodCalls(expression)) {
          final CompletionService service = CompletionService.getCompletionService();
          if (service.getAdvertisementText() == null) {
            service.setAdvertisementText("Invoke completion once more to see runtime type variants");
          }
          return null;
        }

        final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(project).getContext();
        DebuggerSession debuggerSession = debuggerContext.getDebuggerSession();
        if (debuggerSession != null) {
          final Semaphore semaphore = new Semaphore();
          semaphore.down();
          final AtomicReference<String> nameRef = new AtomicReference<String>();
          final RuntimeTypeEvaluator worker =
            new RuntimeTypeEvaluator(null, expression, debuggerContext, ProgressManager.getInstance().getProgressIndicator()) {
              @Override
              protected void typeCalculationFinished(@Nullable String type) {
                nameRef.set(type);
                semaphore.up();
              }
            };
          debuggerContext.getDebugProcess().getManagerThread().invoke(worker);
          semaphore.waitFor(1000);
          final String className = nameRef.get();
          if (className != null) {
            final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
            if (psiClass != null) {
              return JavaPsiFacade.getElementFactory(project).createType(psiClass);
            }
          }
        }
        return null;
      }
    });

    return fragment;
  }

  public boolean isContextAccepted(PsiElement contextElement) {
    return true; // default factory works everywhere debugger can stop
  }

  public LanguageFileType getFileType() {
    return StdFileTypes.JAVA;
  }
}
