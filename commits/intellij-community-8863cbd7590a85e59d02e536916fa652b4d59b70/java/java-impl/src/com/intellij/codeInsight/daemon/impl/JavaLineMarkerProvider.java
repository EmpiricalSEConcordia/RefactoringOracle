/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class JavaLineMarkerProvider implements LineMarkerProvider, DumbAware {
  protected static final Icon OVERRIDING_METHOD_ICON = AllIcons.Gutter.OverridingMethod;
  protected static final Icon IMPLEMENTING_METHOD_ICON = AllIcons.Gutter.ImplementingMethod;

  protected static final Icon OVERRIDEN_METHOD_MARKER_RENDERER = AllIcons.Gutter.OverridenMethod;
  protected static final Icon IMPLEMENTED_METHOD_MARKER_RENDERER = AllIcons.Gutter.ImplementedMethod;
  private static final Icon IMPLEMENTED_INTERFACE_MARKER_RENDERER = IMPLEMENTED_METHOD_MARKER_RENDERER;
  private static final Icon SUBCLASSED_CLASS_MARKER_RENDERER = OVERRIDEN_METHOD_MARKER_RENDERER;

  protected final DaemonCodeAnalyzerSettings myDaemonSettings;
  protected final EditorColorsManager myColorsManager;

  public JavaLineMarkerProvider(DaemonCodeAnalyzerSettings daemonSettings, EditorColorsManager colorsManager) {
    myDaemonSettings = daemonSettings;
    myColorsManager = colorsManager;
  }

  @Override
  @Nullable
  public LineMarkerInfo getLineMarkerInfo(@NotNull final PsiElement element) {
    PsiElement parent;
    if (element instanceof PsiIdentifier && (parent = element.getParent()) instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      MethodSignatureBackedByPsiMethod superSignature = null;
      try {
        superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst();
      }
      catch (IndexNotReadyException e) {
        //some searchers (EJB) require indices. What shall we do?
      }
      if (superSignature != null) {
        boolean overrides =
          method.hasModifierProperty(PsiModifier.ABSTRACT) == superSignature.getMethod().hasModifierProperty(PsiModifier.ABSTRACT);

        final Icon icon = overrides ? OVERRIDING_METHOD_ICON : IMPLEMENTING_METHOD_ICON;
        final MarkerType type = MarkerType.OVERRIDING_METHOD;
        return new ArrowUpLineMarkerInfo(element, icon, type);
      }
    }

    if (myDaemonSettings.SHOW_METHOD_SEPARATORS && element.getFirstChild() == null) {
      PsiElement element1 = element;
      boolean isMember = false;
      while (element1 != null && !(element1 instanceof PsiFile) && element1.getPrevSibling() == null) {
        element1 = element1.getParent();
        if (element1 instanceof PsiMember) {
          isMember = true;
          break;
        }
      }
      if (isMember && !(element1 instanceof PsiAnonymousClass || element1.getParent() instanceof PsiAnonymousClass)) {
        PsiFile file = element1.getContainingFile();
        Document document = file == null ? null : PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        boolean drawSeparator = false;

        if (document != null) {
          CharSequence documentChars = document.getCharsSequence();
          int category = getCategory(element1, documentChars);
          for (PsiElement child = element1.getPrevSibling(); child != null; child = child.getPrevSibling()) {
            int category1 = getCategory(child, documentChars);
            if (category1 == 0) continue;
            drawSeparator = category != 1 || category1 != 1;
            break;
          }
        }

        if (drawSeparator) {
          LineMarkerInfo info = new LineMarkerInfo<PsiElement>(element, element.getTextRange(), null, Pass.UPDATE_ALL,
                                                               FunctionUtil.<Object, String>nullConstant(), null,
                                                               GutterIconRenderer.Alignment.RIGHT);
          EditorColorsScheme scheme = myColorsManager.getGlobalScheme();
          info.separatorColor = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
          info.separatorPlacement = SeparatorPlacement.TOP;
          return info;
        }
      }
    }

    return null;
  }

  protected static int getCategory(@NotNull PsiElement element, @NotNull CharSequence documentChars) {
    if (element instanceof PsiField || element instanceof PsiTypeParameter) return 1;
    if (element instanceof PsiClass || element instanceof PsiClassInitializer) return 2;
    if (element instanceof PsiMethod) {
      if (((PsiMethod)element).hasModifierProperty(PsiModifier.ABSTRACT)) {
        return 1;
      }
      TextRange textRange = element.getTextRange();
      int start = textRange.getStartOffset();
      int end = Math.min(documentChars.length(), textRange.getEndOffset());
      int crlf = StringUtil.getLineBreakCount(documentChars.subSequence(start, end));
      return crlf == 0 ? 1 : 2;
    }
    return 0;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull final List<PsiElement> elements, @NotNull final Collection<LineMarkerInfo> result) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (elements.isEmpty() || DumbService.getInstance(elements.get(0).getProject()).isDumb()) {
      return;
    }

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      ProgressManager.checkCanceled();
      if (element instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)element;
        if (PsiUtil.canBeOverriden(method)) {
          methods.add(method);
        }
      }
      else if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
        collectInheritingClasses((PsiClass)element, result);
      }
    }
    if (!methods.isEmpty()) {
      collectOverridingAccessors(methods, result);
    }
  }

  private static void collectInheritingClasses(PsiClass aClass, Collection<LineMarkerInfo> result) {
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
      return;
    }
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) return; // It's useless to have overridden markers for object.

    PsiClass inheritor = ClassInheritorsSearch.search(aClass, false).findFirst();
    if (inheritor != null) {
      final Icon icon = aClass.isInterface() ? IMPLEMENTED_INTERFACE_MARKER_RENDERER : SUBCLASSED_CLASS_MARKER_RENDERER;
      PsiElement range = aClass.getNameIdentifier();
      if (range == null) range = aClass;
      MarkerType type = MarkerType.SUBCLASSED_CLASS;
      LineMarkerInfo info = new LineMarkerInfo<PsiElement>(range, range.getTextRange(),
                                                           icon, Pass.UPDATE_OVERRIDEN_MARKERS, type.getTooltip(),
                                                           type.getNavigationHandler(),
                                                           GutterIconRenderer.Alignment.RIGHT);
      result.add(info);
    }
  }

  private static void collectOverridingAccessors(final Set<PsiMethod> methods, Collection<LineMarkerInfo> result) {
    final Set<PsiMethod> overridden = new HashSet<PsiMethod>();
    Set<PsiClass> classes = new THashSet<PsiClass>();
    for (PsiMethod method : methods) {
      ProgressManager.checkCanceled();
      final PsiClass parentClass = method.getContainingClass();
      if (!CommonClassNames.JAVA_LANG_OBJECT.equals(parentClass.getQualifiedName())) {
        classes.add(parentClass);
      }
    }

    for (final PsiClass aClass : classes) {
      AllOverridingMethodsSearch.search(aClass).forEach(new Processor<Pair<PsiMethod, PsiMethod>>() {
        @Override
        public boolean process(final Pair<PsiMethod, PsiMethod> pair) {
          ProgressManager.checkCanceled();

          final PsiMethod superMethod = pair.getFirst();
          if (superMethod.isPhysical() && pair.getSecond().isPhysical() //groovy, scala
              && methods.remove(superMethod)) {
            overridden.add(superMethod);
          }
          return !methods.isEmpty();
        }
      });
    }

    for (PsiMethod method : overridden) {
      ProgressManager.checkCanceled();
      boolean overrides = !method.hasModifierProperty(PsiModifier.ABSTRACT);

      final Icon icon = overrides ? OVERRIDEN_METHOD_MARKER_RENDERER : IMPLEMENTED_METHOD_MARKER_RENDERER;
      PsiElement range;
      if (method.isPhysical()) {
        range = method.getNameIdentifier();
      }
      else {
        final PsiElement navigationElement = method.getNavigationElement();
        if (navigationElement instanceof PsiNameIdentifierOwner) {
          range = ((PsiNameIdentifierOwner)navigationElement).getNameIdentifier();
        }
        else {
          range = navigationElement;
        }
      }
      if (range == null) range = method;
      final MarkerType type = MarkerType.OVERRIDEN_METHOD;
      LineMarkerInfo info = new LineMarkerInfo<PsiElement>(range, range.getTextRange(),
                                                           icon, Pass.UPDATE_OVERRIDEN_MARKERS, type.getTooltip(),
                                                           type.getNavigationHandler(),
                                                           GutterIconRenderer.Alignment.RIGHT);
      result.add(info);
    }
  }

  private static class ArrowUpLineMarkerInfo extends MergeableLineMarkerInfo<PsiElement> {
    private ArrowUpLineMarkerInfo(@NotNull PsiElement element, Icon icon, @NotNull MarkerType markerType) {
      super(element, element.getTextRange(), icon, Pass.UPDATE_ALL, markerType.getTooltip(),
            markerType.getNavigationHandler(), GutterIconRenderer.Alignment.LEFT);
    }

    @Override
    public boolean canMergeWith(@NotNull MergeableLineMarkerInfo<?> info) {
      if (!(info instanceof ArrowUpLineMarkerInfo)) return false;
      PsiElement otherElement = info.getElement();
      PsiElement myElement = getElement();
      return otherElement != null && myElement != null;
    }


    @Override
    public Icon getCommonIcon(@NotNull List<MergeableLineMarkerInfo> infos) {
      return myIcon;
    }

    @Override
    public Function<? super PsiElement, String> getCommonTooltip(@NotNull List<MergeableLineMarkerInfo> infos) {
      return new Function<PsiElement, String>() {
        @Override
        public String fun(PsiElement element) {
          return "Multiple method overrides";
        }
      };
    }
  }
}
