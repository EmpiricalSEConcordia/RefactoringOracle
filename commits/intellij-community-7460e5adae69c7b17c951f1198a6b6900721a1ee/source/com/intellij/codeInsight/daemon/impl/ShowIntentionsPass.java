package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.daemon.impl.quickfix.PostIntentionsQuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.IntentionActionComposite;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @fabrique
 */
public class ShowIntentionsPass extends TextEditorHighlightingPass {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.ShowIntentionsPass");

  private final Project myProject;
  private final Editor myEditor;
  private final IntentionAction[] myIntentionActions;

  private final PsiFile myFile;

  private boolean myIsSecondPass;
  private int myStartOffset;
  private int myEndOffset;


  ShowIntentionsPass(Project project, Editor editor, IntentionAction[] intentionActions, boolean isSecondPass) {
    super(editor.getDocument());
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    myIsSecondPass = isSecondPass;
    myProject = project;
    myEditor = editor;
    myIntentionActions = intentionActions;

    Rectangle visibleRect = myEditor.getScrollingModel().getVisibleArea();

    LogicalPosition startPosition = myEditor.xyToLogicalPosition(new Point(visibleRect.x, visibleRect.y));
    myStartOffset = myEditor.logicalPositionToOffset(startPosition);

    LogicalPosition endPosition = myEditor.xyToLogicalPosition(
      new Point(visibleRect.x + visibleRect.width, visibleRect.y + visibleRect.height));
    myEndOffset = myEditor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));

    myFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
  }

  public void doCollectInformation(ProgressIndicator progress) {
  }

  public void doApplyInformationToEditor() {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    if (!myEditor.getContentComponent().hasFocus()) return;

    HighlightInfo[] visibleHighlights = getVisibleHighlights(myStartOffset, myEndOffset);
    if (visibleHighlights == null) return;

    PsiElement[] elements = new PsiElement[visibleHighlights.length];
    for (int i = 0; i < visibleHighlights.length; i++) {
      ProgressManager.getInstance().checkCanceled();

      HighlightInfo highlight = visibleHighlights[i];
      elements[i] = myFile.findElementAt(highlight.startOffset);
    }

    int caretOffset = myEditor.getCaretModel().getOffset();
    for (int i = visibleHighlights.length - 1; i >= 0; i--) {
      HighlightInfo info = visibleHighlights[i];
      if (elements[i] == null) continue;
      if (info.startOffset <= caretOffset) {
        if (showAddImportHint(info, elements[i])) return;
      }
    }

    for (int i = 0; i < visibleHighlights.length; i++) {
      HighlightInfo info = visibleHighlights[i];
      if (elements[i] == null) continue;
      if (info.startOffset > caretOffset) {
        if (showAddImportHint(info, elements[i])) return;
      }
    }

    if (!(myFile instanceof PsiCodeFragment)) {
      TemplateState state = TemplateManagerImpl.getTemplateState(myEditor);
      if (state == null || state.isFinished()) {
        showIntentionActions();
      }
    }
  }

  public int getPassId() {
    return myIsSecondPass ? Pass.POPUP_HINTS2 : Pass.POPUP_HINTS;
  }

  private void showIntentionActions() {
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    if (myIsSecondPass) codeAnalyzer.setShowPostIntentions(true);
    if (LookupManager.getInstance(myProject).getActiveLookup() != null) return;

    // do not show intentions if caret is outside visible area
    LogicalPosition caretPos = myEditor.getCaretModel().getLogicalPosition();
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    Point xy = myEditor.logicalPositionToXY(caretPos);
    if (!visibleArea.contains(xy)) return;

    ArrayList<IntentionAction> intentionsToShow = new ArrayList<IntentionAction>();
    ArrayList<IntentionAction> fixesToShow = new ArrayList<IntentionAction>();
    for (int i = 0; i < myIntentionActions.length; i++) {
      IntentionAction action = myIntentionActions[i];
      if (action instanceof IntentionActionComposite) {
        if (action instanceof QuickFixAction ||
            action instanceof PostIntentionsQuickFixAction && codeAnalyzer.showPostIntentions()) {
          List<IntentionAction> availableActions = ((IntentionActionComposite)action).getAvailableActions(myEditor, myFile);

          int offset = myEditor.getCaretModel().getOffset();
          HighlightInfo info = codeAnalyzer.findHighlightByOffset(myEditor.getDocument(), offset, true);
          if (info == null || info.getSeverity() == HighlightInfo.ERROR) {
            fixesToShow.addAll(availableActions);
          }
          else {
            intentionsToShow.addAll(availableActions);
          }
        }
      }
      else if (action.isAvailable(myProject, myEditor, myFile)) {
        intentionsToShow.add(action);
      }
    }

    if (!intentionsToShow.isEmpty() || !fixesToShow.isEmpty()) {
      boolean showBulb = false;
      for (Iterator<IntentionAction> iterator = fixesToShow.iterator(); iterator.hasNext();) {
        IntentionAction action = iterator.next();
        if (IntentionManagerSettings.getInstance().isShowLightBulb(action)) {
          showBulb = true;
          break;
        }
      }
      if (!showBulb) {
        for (Iterator<IntentionAction> iterator = intentionsToShow.iterator(); iterator.hasNext();) {
          IntentionAction action = iterator.next();
          if (IntentionManagerSettings.getInstance().isShowLightBulb(action)) {
            showBulb = true;
            break;
          }
        }
      }

      if (showBulb) {
        if (myIsSecondPass) {
          IntentionHintComponent hintComponent = codeAnalyzer.getLastIntentionHint();
          if (hintComponent != null) {
            hintComponent.updateIfNotShowingPopup(fixesToShow, intentionsToShow);
          }
        }

        if (!HintManager.getInstance().hasShownHintsThatWillHideByOtherHint()) {
          IntentionHintComponent hintComponent = IntentionHintComponent.showIntentionHint(myProject, myEditor, intentionsToShow,
                                                                                          fixesToShow, false);
          if (!myIsSecondPass) {
            codeAnalyzer.setLastIntentionHint(hintComponent);
          }
        }
      }
    }
  }

  private HighlightInfo[] getVisibleHighlights(int startOffset, int endOffset) {
    HighlightInfo[] highlights = DaemonCodeAnalyzerImpl.getHighlights(myEditor.getDocument(), myProject);
    if (highlights == null) return null;

    ArrayList<HighlightInfo> array = new ArrayList<HighlightInfo>();
    for (int i = 0; i < highlights.length; i++) {
      HighlightInfo info = highlights[i];
      if (!canBeHint(info.type)) continue;
      if (startOffset <= info.startOffset && info.endOffset <= endOffset) {
        if (myEditor.getFoldingModel().isOffsetCollapsed(info.startOffset)) continue;
        array.add(info);
      }
    }
    return array.toArray(new HighlightInfo[array.size()]);
  }

  private boolean showAddImportHint(HighlightInfo info, PsiElement element) {
    if (!element.isWritable()) return false;
    if (!DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled()) return false;
    if (!DaemonCodeAnalyzer.getInstance(myProject).isImportHintsEnabled(myFile)) return false;

    element = element.getParent();
    if (!(element instanceof PsiJavaCodeReferenceElement)) return false;
    // [dsl]todo[cdr]: please review this
    //  if (element.getTextOffset() != info.startOffset || element.getTextRange().getEndOffset() != info.endOffset) return false;

    if (info.type == HighlightInfoType.WRONG_REF) {
      return showAddImportHint(myEditor, (PsiJavaCodeReferenceElement)element);
    }
    else if (info.type == HighlightInfoType.JAVADOC_WRONG_REF) {
      if (DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile().getErrorLevel(HighlightDisplayKey.JAVADOC_ERROR) ==
          HighlightDisplayLevel.ERROR) {
        return showAddImportHint(myEditor, (PsiJavaCodeReferenceElement)element);
      }
    }

    return false;
  }

  /*
   * @fabrique
   */
  public static boolean showAddImportHint(Editor editor, final PsiJavaCodeReferenceElement ref) {
    if (HintManager.getInstance().hasShownHintsThatWillHideByOtherHint()) return false;

    PsiManager manager = ref.getManager();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    PsiShortNamesCache cache = manager.getShortNamesCache();
    PsiElement refname = ref.getReferenceNameElement();
    if (!(refname instanceof PsiIdentifier)) {
      return false;
    }
    PsiElement refElement = ref.resolve();
    if (refElement != null) {
      return false;
    }
    String name = ref.getQualifiedName();
    if (manager.getResolveHelper().resolveReferencedClass(name, ref) != null) return false;

    GlobalSearchScope scope = ref.getResolveScope();
    PsiClass[] classes = cache.getClassesByName(name, scope);
    if (classes.length == 0) return false;

    try {
      Pattern pattern = Pattern.compile(DaemonCodeAnalyzerSettings.getInstance().NO_AUTO_IMPORT_PATTERN);
      Matcher matcher = pattern.matcher(name);
      if (matcher.matches()) return false;
    }
    catch (PatternSyntaxException e) {
    }

    List<PsiClass> availableClasses = new ArrayList<PsiClass>();
    boolean isAnnotationReference = ref.getParent() instanceof PsiAnnotation;
    for (int j = 0; j < classes.length; j++) {
      PsiClass aClass = classes[j];
      if (aClass.getParent() instanceof PsiDeclarationStatement) continue;
      PsiFile file = aClass.getContainingFile();
      if (!(file instanceof PsiJavaFile) || ((PsiJavaFile)file).getPackageName().length() == 0) continue;
      if (isAnnotationReference && !aClass.isAnnotationType()) continue;
      if (!aClass.hasModifierProperty(PsiModifier.PUBLIC)) continue;
      availableClasses.add(aClass);
    }
    if (availableClasses.size() == 0) return false;

    int refTypeArgsLength = ref.getParameterList().getTypeArguments().length;
    if (availableClasses.size() > 0 && refTypeArgsLength != 0) {
      List<PsiClass> typeArgMatched = new ArrayList<PsiClass>(availableClasses);
      // try to reduce suggestions based on type argument list
      for (int i = typeArgMatched.size() - 1; i >= 0; i--) {
        final PsiClass aClass = typeArgMatched.get(i);
        PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
        if (refTypeArgsLength != typeParameters.length) {
          typeArgMatched.remove(i);
        }
      }
      if (typeArgMatched.size() != 0) {
        availableClasses = typeArgMatched;
      }
    }
    classes = availableClasses.toArray(new PsiClass[availableClasses.size()]);
    CodeInsightUtil.sortIdenticalShortNameClasses(classes);
    String hintText = classes[0].getQualifiedName() + "? ";
    if (classes.length > 1) {
      hintText += "(multiple choices...) ";
    }

    hintText += KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));

    int offset1 = ref.getTextOffset();
    int offset2 = ref.getTextRange().getEndOffset();
    QuestionAction action = new AddImportAction(manager.getProject(), ref, classes, editor);

    if (classes.length == 1 && CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY) {
      action.execute();
      return false;
    }
    HintManager hintManager = HintManager.getInstance();
    hintManager.showQuestionHint(editor, hintText, offset1, offset2, action);
    return true;
  }

  private static boolean canBeHint(HighlightInfoType type) {
    return type == HighlightInfoType.WRONG_REF || type == HighlightInfoType.JAVADOC_WRONG_REF;
  }
}