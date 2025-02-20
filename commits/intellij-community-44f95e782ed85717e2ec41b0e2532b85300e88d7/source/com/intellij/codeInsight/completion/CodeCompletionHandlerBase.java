
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.impl.idCache.IdTableBuilding;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @see #notifyAll()
 */
abstract class CodeCompletionHandlerBase implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CodeCompletionHandlerBase");
  private static final Key<Class> COMPLETION_HANDLER_CLASS_KEY = Key.create("COMPLETION_HANDLER_CLASS_KEY");

  private LookupItemPreferencePolicy myPreferencePolicy = null;

  public final void invoke(final Project project, final Editor editor, final PsiFile file) {
    if (!file.isWritable()){
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(editor.getDocument(), project)){
        return;
      }
    }

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();

    Lookup activeLookup = LookupManager.getInstance(project).getActiveLookup();
    if (activeLookup != null){
      Class handlerClass = activeLookup.getUserData(COMPLETION_HANDLER_CLASS_KEY);
      if (handlerClass == null){
        handlerClass = CodeCompletionHandler.class;
      }
      if (handlerClass.equals(this.getClass())){
        if (!isAutocompleteCommonPrefixOnInvocation() || activeLookup.fillInCommonPrefix(true)) {
          return;
        }
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        EditorUtil.fillVirtualSpaceUntil(editor, editor.getCaretModel().getLogicalPosition().column, editor.getCaretModel().getLogicalPosition().line);
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      }
    });

    final int offset1 = editor.getSelectionModel().hasSelection()
      ? editor.getSelectionModel().getSelectionStart()
      : editor.getCaretModel().getOffset();
    final int offset2 = editor.getSelectionModel().hasSelection()
      ? editor.getSelectionModel().getSelectionEnd()
      : offset1;
    final CompletionContext context = new CompletionContext(project, editor, file, offset1, offset2);

    final LookupData data = getLookupData(context);
    final LookupItem[] items = data.items;
    String prefix = data.prefix;
    context.prefix = data.prefix;
    if (items.length == 0){
      handleEmptyLookup(context, data);
      return;
    }
    final int startOffset = offset1 - prefix.length();

    String uniqueText = null;
    LookupItem item = null;
    boolean doNotAutocomplete = false;
    boolean signatureSencetive = false;

    for(int i = 0; i < items.length; i++){
      final LookupItem item1 = items[i];
      if (item1.getAttribute(LookupItem.DO_NOT_AUTOCOMPLETE_ATTR) != null){
        item = null;
        doNotAutocomplete = true;
        break;
      }

      if (item1.getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null){
        signatureSencetive = true;
      }
      if (uniqueText == null){
        uniqueText = item1.getLookupString();
        item = item1;
      }
      else{
        if (!uniqueText.equals(item1.getLookupString())){
          item = null;
          break;
        }
        if (item.getObject() instanceof PsiMethod && item1.getObject() instanceof PsiMethod){
          if(!signatureSencetive){
            final PsiParameter[] parms = ((PsiMethod)item1.getObject()).getParameterList().getParameters();
            if (parms.length > 0){
              item = item1;
            }
          }
          else{
            item = null;
            break;
          }
        }
        else{
          item = null;
          break;
        }
      }
    }

    if (item != null){
      if (!isAutocompleteOnInvocation() && item.getAttribute(LookupItem.DO_AUTOCOMPLETE_ATTR) == null){
        item = null;
      }
    }
    if (item != null && context.identifierEndOffset != context.selectionEndOffset){ // give a chance to use Tab
      if (item.getAttribute(LookupItem.DO_AUTOCOMPLETE_ATTR) == null){
        item = null;
      }
    }

    if (item != null) {
      if(item.getObject() instanceof DeferredUserLookupValue) {
        if(!((DeferredUserLookupValue)item.getObject()).handleUserSelection(item,context.project)) {
          return;
        }

        uniqueText = item.getLookupString(); // text may be not ready yet
        context.startOffset -= prefix.length();
        data.prefix = context.prefix = ""; // prefix may be of no interest
      }

      EditorModificationUtil.deleteSelectedText(editor);
      editor.getDocument().replaceString(offset1 - prefix.length(), offset1, uniqueText);
      final int offset = offset1 - prefix.length() + uniqueText.length();
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();

      lookupItemSelected(context, startOffset, data, item, false, (char)0);
    }
    else{
      if (isAutocompleteCommonPrefixOnInvocation() && !doNotAutocomplete){
        final String newPrefix = fillInCommonPrefix(items, prefix, editor);

        if (!newPrefix.equals(prefix)) {
          final int shift = newPrefix.length() - prefix.length();
          context.prefix = newPrefix;
          prefix = newPrefix;
          context.shiftOffsets(shift);
          //context.offset1 += shift;
          editor.getCaretModel().moveToOffset(context.startOffset + shift);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
      }

      PsiDocumentManager.getInstance(project).commitAllDocuments();
      final RangeMarker startOffsetMarker = editor.getDocument().createRangeMarker(startOffset, startOffset);
      final Lookup lookup = showLookup(project, editor, items, prefix, data, file);

      if (lookup != null) {
        lookup.putUserData(COMPLETION_HANDLER_CLASS_KEY, this.getClass());

        lookup.addLookupListener(
          new LookupAdapter() {
            public void itemSelected(LookupEvent event) {
              int shift = startOffsetMarker.getStartOffset() - startOffset;
              context.shiftOffsets(shift);
              context.startOffset += shift;
              lookupItemSelected(
                  context,
                  startOffsetMarker.getStartOffset(),
                  data,
                  event.getItem(),
                  settings.SHOW_SIGNATURES_IN_LOOKUPS || event.getItem().getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null,
                  event.getCompletionChar()
              );
            }
          }
        );
      }
    }
  }

  private static String fillInCommonPrefix(LookupItem[] items, final String prefix, final Editor editor) {
    String commonPrefix = null;
    boolean isStrict = false;

    for(int i = 0; i < items.length; i++){
      final LookupItem item = items[i];
      final String lookupString = item.getLookupString();
      if (!lookupString.toLowerCase().startsWith(prefix.toLowerCase())){
        throw new RuntimeException("Hmm... Some lookup items have other than $prefix prefix.");
      }

      //final String afterPrefix = lookupString.substring(prefix.length());

      if (commonPrefix != null){
        int matchingRegLength = lookupString.length();
        while(!lookupString.regionMatches(0, commonPrefix, 0, matchingRegLength--));
        commonPrefix = lookupString.substring(0, matchingRegLength + 1);
        if(commonPrefix.length() < lookupString.length())
          isStrict = true;
        if(commonPrefix.length() <= prefix.length())
          return prefix;
      }
      else {
        commonPrefix = lookupString;
      }
    }

    if (!isStrict) return prefix;

    int offset = editor.getSelectionModel().hasSelection()
      ? editor.getSelectionModel().getSelectionStart()
      : editor.getCaretModel().getOffset();
    int lookupStart = offset - prefix.length();

    editor.getDocument().replaceString(lookupStart, lookupStart + prefix.length(), commonPrefix);

    return commonPrefix;
  }

  protected abstract CompletionData getCompletionData(CompletionContext context, PsiElement element);

  final private void complete(CompletionContext context, PsiElement lastElement,
                             CompletionData completionData, LinkedHashSet lookupSet){
    if(lastElement == null)
      return;
    final PsiReference ref = lastElement.getContainingFile().findReferenceAt(context.offset);
    if (ref != null) {
      completionData.completeReference(ref, lookupSet, context, lastElement);
      return;
    }

    if (lastElement instanceof PsiIdentifier) {
      final PsiElement parent = lastElement.getParent();
      if (parent instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)parent;
        if (lastElement.equals(variable.getNameIdentifier())) {
          myPreferencePolicy = completionData.completeVariableName(lookupSet, context, variable);
        }
      }
      else if (parent instanceof PsiClass) {
        final PsiClass psiClass = (PsiClass)parent;
        if (lastElement.equals(psiClass.getNameIdentifier())) {
          myPreferencePolicy = completionData.completeClassName(lookupSet, context, psiClass);
        }
      }
      else if (parent instanceof PsiMethod) {
        final PsiMethod psiMethod = (PsiMethod)parent;
        if (lastElement.equals(psiMethod.getNameIdentifier())) {
          myPreferencePolicy = completionData.completeMethodName(lookupSet, context, psiMethod);
        }
      }
      return;
    }
    return;
  }


  private void fillWordCompletionSet (final PsiFile file, final LinkedHashSet lookupSet, final String prefix) {
    final char [] chars = file.getText().toCharArray();
    IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor(){
      public void run(final char[] chars, final int start, final int end) {
        final int len = end - start;
        if (len > prefix.length ()) {
          final String word = String.valueOf(chars, start, len);
          if (word.startsWith(prefix)) {
            LookupItemUtil.addLookupItem(lookupSet, word, prefix); // TODO
          }
        }
      }
    }, chars, 0, chars.length);
  }


  protected LookupData getLookupData(CompletionContext context) {
    final LinkedHashSet<LookupItem> lookupSet = new LinkedHashSet<LookupItem>();
    final PsiFile file = context.file;
    final PsiManager manager = file.getManager();
    final PsiElement lastElement = file.findElementAt(context.startOffset - 1);
    final Set keywordVariants = new HashSet();

    final PsiElement insertedElement = insertDummyIdentifier(context);
    CompletionData completionData = getCompletionData(context, lastElement);

    context.prefix = findPrefix(insertedElement, context.startOffset, CompletionUtil.DUMMY_IDENTIFIER, completionData);
    if (completionData == null) {
      // some completion data may depend on prefix
      completionData = getCompletionData(context, lastElement);
    }

    // Trim for file names
    // TODO: remove this shit
    final int dirEndIndex = Math.max(context.prefix.lastIndexOf('/'), context.prefix.lastIndexOf('\\'));
    if (dirEndIndex >= 0) {
      final int protocolIndex = context.prefix.indexOf("://");
      if (protocolIndex==-1 || dirEndIndex > protocolIndex+2 ) {
        context.prefix = context.prefix.substring(dirEndIndex + 1);
      }
    }

    if(completionData == null) return new LookupData(new LookupItem[0], context.prefix);

    complete(context, insertedElement, completionData, lookupSet);
    insertedElement.putUserData(CompletionUtil.COMPLETION_PREFIX, context.prefix);
    completionData.addKeywordVariants(keywordVariants, context, insertedElement);
    CompletionData.completeKeywordsBySet(lookupSet, keywordVariants, context, insertedElement);
    CompletionUtil.highlightMembersOfContainer(lookupSet);

    final LookupItem[] items = lookupSet.toArray(new LookupItem[lookupSet.size()]);
    final LookupData data = new LookupData(items, context.prefix);
    if (myPreferencePolicy == null){
      myPreferencePolicy = new CompletionPreferencePolicy(manager, items, null, context.prefix);
    }
    data.itemPreferencePolicy = myPreferencePolicy;
    myPreferencePolicy = null;
    return data;
  }

  protected PsiElement insertDummyIdentifier(final CompletionContext context){
    final PsiFile fileCopy = createCopy(context);
    try {
      context.project.getComponent(BlockSupport.class).reparseRange(
          fileCopy, context.startOffset,
          context.startOffset,
          CompletionUtil.DUMMY_IDENTIFIER
      );
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    context.offset = context.startOffset;
    return fileCopy.findElementAt(context.startOffset);
  }


  protected Lookup showLookup(Project project,
                              final Editor editor,
                              final LookupItem[] items,
                              String prefix,
                              final LookupData data,
                              PsiFile file) {
    return LookupManager.getInstance(project).showLookup(editor, items, prefix, data.itemPreferencePolicy, new DefaultCharFilter(file){
      public int accept(char c){
        switch (c){
          case '<':
          case '>':
          case '[':
            return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;
          default:
            return super.accept(c);
        }
      }
    });
  }

  protected abstract boolean isAutocompleteOnInvocation();

  protected abstract boolean isAutocompleteCommonPrefixOnInvocation();

  protected abstract void analyseItem(LookupItem item, PsiElement place, CompletionContext context);

  protected void handleEmptyLookup(CompletionContext context, LookupData lookupData) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    Project project = context.project;
    Editor editor = context.editor;

    LOG.assertTrue(lookupData.items.length == 0);
    if (lookupData.prefix == null) {
//      Toolkit.getDefaultToolkit().beep();
    } else {
      HintManager.getInstance().showErrorHint(editor, "No suggestions");
    }
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    if (codeAnalyzer != null) {
      codeAnalyzer.updateVisibleHighlighters(editor);
    }
  }

  private void lookupItemSelected(final CompletionContext context,
                                  final int startOffset, final LookupData data, final LookupItem item,
                                  final boolean signatureSelected, final char completionChar) {

    final InsertHandler handler = item.getInsertHandler() != null ? item.getInsertHandler() : new DefaultInsertHandler();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          PsiDocumentManager.getInstance(context.project).commitAllDocuments();
          context.prefix = data.prefix;
          final PsiElement position = context.file.findElementAt(context.startOffset - context.prefix.length() + item.getLookupString().length() - 1);
          analyseItem(item, position, context);
          handler.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
        }
      });
  }

  public String findPrefix(PsiElement insertedElement, int offset, String dummyIdentifier, CompletionData completionData){
    final String result = (completionData!=null)?
      completionData.findPrefix(insertedElement, offset):
      CompletionData.findPrefixStatic(insertedElement,offset);

    if(result.endsWith(dummyIdentifier))
      return result.substring(0, result.length() - dummyIdentifier.length());
    return result;
  }

  public boolean startInWriteAction() {
    return true;
  }

  protected PsiFile createCopy(final CompletionContext context) {
    final PsiElementVisitor visitor = new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      public void visitClass(PsiClass aClass) {
        aClass.putCopyableUserData(CompletionUtil.ORIGINAL_KEY, aClass);
        super.visitClass(aClass);
      }

      public void visitVariable(PsiVariable variable) {
        variable.putCopyableUserData(CompletionUtil.ORIGINAL_KEY, variable);
        super.visitVariable(variable);
      }

      public void visitMethod(PsiMethod method) {
        method.putCopyableUserData(CompletionUtil.ORIGINAL_KEY, method);
        super.visitMethod(method);
      }

      public void visitXmlTag(XmlTag tag) {
        tag.putCopyableUserData(CompletionUtil.ORIGINAL_KEY, tag);
        super.visitXmlTag(tag);
      }
    };

    visitor.visitFile(context.file);
    final PsiFile fileCopy = (PsiFile)context.file.copy();

    final PsiElementVisitor copyVisitor = new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      public void visitClass(PsiClass aClass) {
        final PsiElement originalElement = aClass.getCopyableUserData(CompletionUtil.ORIGINAL_KEY);
        if (originalElement != null){
          originalElement.putCopyableUserData(CompletionUtil.ORIGINAL_KEY, null);
          originalElement.putUserData(CompletionUtil.COPY_KEY, aClass);
          aClass.putCopyableUserData(CompletionUtil.ORIGINAL_KEY, null);
          aClass.putUserData(CompletionUtil.ORIGINAL_KEY, originalElement);
        }
        super.visitClass(aClass);
      }
    };
    copyVisitor.visitFile(fileCopy);
    return fileCopy;
  }
}

