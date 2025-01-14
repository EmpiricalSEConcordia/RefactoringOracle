/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 8, 2002
 * Time: 5:55:01 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiMatcherImpl;
import com.intellij.psi.util.PsiUtil;

import java.util.*;

public class HighlightControlFlowUtil {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();
  //@top
  public static HighlightInfo checkMissingReturnStatement(PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body == null
        || method.getReturnType() == null
        || PsiType.VOID == method.getReturnType()) {
      return null;
    }
    // do not compute constant expressions for if() statement condition
    // see JLS 14.20 Unreachable Statements
    try {
      final ControlFlow controlFlow = ControlFlowFactory.getControlFlow(body,
                                                                        LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(),
                                                                        false);
      if (!ControlFlowUtil.returnPresent(controlFlow)) {
        final PsiElement context = body.getRBrace() == null
                                   ? body.getLastChild()
                                   : body.getRBrace();
        final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
            HighlightInfoType.ERROR,
            context,
            JavaErrorMessages.message("missing.return.statement"));
        QuickFixAction.registerQuickFixAction(highlightInfo, new AddReturnFix(method), null);
        IntentionAction fix = QUICK_FIX_FACTORY.createMethodReturnFix(method, PsiType.VOID, false);
        QuickFixAction.registerQuickFixAction(highlightInfo, fix, null);
        return highlightInfo;
      }
    }
    catch (AnalysisCanceledException e) {
      // incomplete code
    }
    return null;

  }

  //@top
  public static HighlightInfo checkUnreachableStatement(PsiCodeBlock codeBlock) {
    if (codeBlock == null) return null;
    // do not compute constant expressions for if() statement condition
    // see JLS 14.20 Unreachable Statements
    try {
      final ControlFlow controlFlow = ControlFlowFactory.getControlFlow(codeBlock,
                                                                        LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(),
                                                                        false);
      final PsiElement unreachableStatement = ControlFlowUtil.getUnreachableStatement(controlFlow);
      if (unreachableStatement != null) {
        return HighlightInfo.createHighlightInfo(
            HighlightInfoType.ERROR,
            unreachableStatement,
            JavaErrorMessages.message("unreachable.statement"));
      }
    }
    catch (AnalysisCanceledException e) {
      // incomplete code
    }
    return null;
  }

  private static boolean isFinalFieldInitialized(PsiField field) {
    if (field.hasInitializer()) return true;
    final boolean isFieldStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final PsiClass aClass = field.getContainingClass();
    if (aClass != null) {
      // field might be assigned in the other field initializers
      if (isFieldInitializedInOtherFieldInitializer(aClass, field, isFieldStatic)) return true;
    }
    final PsiClassInitializer[] initializers;
    if (aClass != null) {
      initializers = aClass.getInitializers();
    }
    else {
      return false;
    }
    for (PsiClassInitializer initializer : initializers) {
      if (initializer.hasModifierProperty(PsiModifier.STATIC) == isFieldStatic
          && variableDefinitelyAssignedIn(field, initializer.getBody())) {
        return true;
      }
    }
    if (isFieldStatic) {
      return false;
    }
    else {
      // instance field should be initialized at the end of the each constructor
      final PsiMethod[] constructors = aClass.getConstructors();

      if (constructors.length == 0) return false;
      nextConstructor:
      for (PsiMethod constructor : constructors) {
        PsiCodeBlock ctrBody = constructor.getBody();
        if (ctrBody == null) return false;
        final List<PsiMethod> redirectedConstructors = getRedirectedConstructors(constructor);
        for (int j = 0; redirectedConstructors != null && j < redirectedConstructors.size(); j++) {
          PsiMethod redirectedConstructor = redirectedConstructors.get(j);
          final PsiCodeBlock body = redirectedConstructor.getBody();
          if (body != null && variableDefinitelyAssignedIn(field, body)) continue nextConstructor;
        }
        if (!ctrBody.isValid() || variableDefinitelyAssignedIn(field, ctrBody)) {
          continue;
        }
        return false;
      }
      return true;
    }
  }

  private static boolean isFieldInitializedInOtherFieldInitializer(final PsiClass aClass,
                                                                   PsiField field,
                                                                   final boolean fieldStatic) {
    PsiField[] fields = aClass.getFields();
    for (PsiField psiField : fields) {
      if (psiField != field
          && psiField.hasModifierProperty(PsiModifier.STATIC) == fieldStatic
          && variableDefinitelyAssignedIn(field, psiField)) {
        return true;
      }
    }
    return false;
  }

  /**
   * return all constructors which are referred from this constructor by
   *  this (...) at the beginning of the constructor body
   * @return referring constructor
   */
  public static List<PsiMethod> getRedirectedConstructors(PsiMethod constructor) {
    final ConstructorVisitorInfo info = new ConstructorVisitorInfo();
    visitConstructorChain(constructor, info);
    if (info.visitedConstructors != null) info.visitedConstructors.remove(constructor);
    return info.visitedConstructors;
  }

  public static boolean isRecursivelyCalledConstructor(PsiMethod constructor) {
    final ConstructorVisitorInfo info = new ConstructorVisitorInfo();
    visitConstructorChain(constructor, info);
    if (info.recursivelyCalledConstructor == null) return false;
    // our constructor is reached from some other constructor by constructor chain
    return info.visitedConstructors.indexOf(info.recursivelyCalledConstructor) <=
           info.visitedConstructors.indexOf(constructor);
  }

  private static class ConstructorVisitorInfo {
    List<PsiMethod> visitedConstructors;
    PsiMethod recursivelyCalledConstructor;
  }

  private static void visitConstructorChain(PsiMethod constructor, ConstructorVisitorInfo info) {
    while (true) {
      if (constructor == null || constructor.getBody() == null) return;
      final PsiCodeBlock body = constructor.getBody();
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) return;
      final PsiStatement statement = statements[0];
      final PsiElement element = new PsiMatcherImpl(statement)
          .dot(PsiMatcherImpl.hasClass(PsiExpressionStatement.class))
          .firstChild(PsiMatcherImpl.hasClass(PsiMethodCallExpression.class))
          .firstChild(PsiMatcherImpl.hasClass(PsiReferenceExpression.class))
          .firstChild(PsiMatcherImpl.hasClass(PsiKeyword.class))
          .dot(PsiMatcherImpl.hasText(PsiKeyword.THIS))
          .parent(null)
          .parent(null)
          .getElement();
      if (element == null) return;
      PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;
      PsiMethod method = methodCall.resolveMethod();
      if (method == null) return;
      if (info.visitedConstructors != null && info.visitedConstructors.contains(method)) {
        info.recursivelyCalledConstructor = method;
        return;
      }
      if (info.visitedConstructors == null) info.visitedConstructors = new ArrayList<PsiMethod>(5);
      info.visitedConstructors.add(method);
      constructor = method;
    }
  }

  /**
   * see JLS chapter 16
   * @return true if variable assigned (maybe more than once)
   */
  private static boolean variableDefinitelyAssignedIn(PsiVariable variable, PsiElement context) {
    try {
      ControlFlow controlFlow = ControlFlowFactory.getControlFlow(context, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
      return ControlFlowUtil.isVariableDefinitelyAssigned(variable, controlFlow);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
  }

  private static boolean variableDefinitelyNotAssignedIn(PsiVariable variable, PsiElement context) {
    try {
      ControlFlow controlFlow = ControlFlowFactory.getControlFlow(context, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
      return ControlFlowUtil.isVariableDefinitelyNotAssigned(variable, controlFlow);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
  }

  //@top
  public static HighlightInfo checkFinalFieldInitialized(PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.FINAL)) return null;
    boolean isInitialized = isFinalFieldInitialized(field);
    if (!isInitialized) {
      String description = JavaErrorMessages.message("variable.not.initialized", field.getName());
      int start = field.getModifierList().getTextRange().getStartOffset();
      int end = field.getNameIdentifier().getTextRange().getEndOffset();
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
          HighlightInfoType.ERROR,
          start, end,
          description);
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass != null && !containingClass.isInterface()) {
        IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(field.getModifierList(), PsiModifier.FINAL, false, false);
        QuickFixAction.registerQuickFixAction(highlightInfo, fix, null);
      }
      QuickFixAction.registerQuickFixAction(highlightInfo, new AddVariableInitializerFix(field),null);
      return highlightInfo;
    }
    return null;
  }

  //@top
  public static HighlightInfo checkVariableInitializedBeforeUsage(PsiReferenceExpression expression,
                                                                  PsiElement element,
                                                                  Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems) {
    if (!(element instanceof PsiVariable) || element instanceof ImplicitVariable) return null;
    PsiVariable variable = (PsiVariable)element;
    if (!PsiUtil.isAccessedForReading(expression)) return null;
    final int startOffset = expression.getTextRange().getStartOffset();
    final PsiElement topBlock;
    if (variable.hasInitializer()) {
      topBlock = PsiUtil.getVariableCodeBlock(variable, variable);
      if (topBlock == null) return null;
    }
    else {
      final PsiElement scope = variable instanceof PsiField
                               ? variable.getParent()
                               : variable.getParent() != null ? variable.getParent().getParent() : null;
      topBlock = scope instanceof JspFile ? scope : PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
      if (variable instanceof PsiField) {
        // non final field already initalized with default value
        if (!variable.hasModifierProperty(PsiModifier.FINAL)) return null;
        // final field may be initialized in ctor or class initializer only
        // if we're inside non-ctr method, skip it
        if (PsiUtil.findEnclosingConstructorOrInitializer(expression) == null
            && HighlightUtil.findEnclosingFieldInitializer(expression) == null) {
          return null;
        }
        // access to final fields from inner classes always allowed
        if (inInnerClass(expression, ((PsiField)variable).getContainingClass())) return null;
        if (topBlock == null) return null;
        final PsiElement parent = topBlock.getParent();
        final PsiCodeBlock block;
        final PsiClass aClass;
        if (parent instanceof PsiMethod) {
          PsiMethod constructor = (PsiMethod)parent;
          if (!parent.getManager().areElementsEquivalent(constructor.getContainingClass(), ((PsiField)variable).getContainingClass())) return null;
          // static variables already initalized in class initalizers
          if (variable.hasModifierProperty(PsiModifier.STATIC)) return null;
          // as a last chance, field may be initalized in this() call
          final List<PsiMethod> redirectedConstructors = getRedirectedConstructors(constructor);
          for (int j = 0; redirectedConstructors != null && j < redirectedConstructors.size(); j++) {
            PsiMethod redirectedConstructor = redirectedConstructors.get(j);
            // variable must be initialized before its usage
            //???
            //if (startOffset < redirectedConstructor.getTextRange().getStartOffset()) continue;
            if (redirectedConstructor.getBody() != null
                && variableDefinitelyAssignedIn(variable, redirectedConstructor.getBody())) {
              return null;
            }
          }
          block = constructor.getBody();
          aClass = constructor.getContainingClass();
        }
        else if (parent instanceof PsiClassInitializer) {
          final PsiClassInitializer classInitializer = (PsiClassInitializer)parent;
          if (!parent.getManager().areElementsEquivalent(classInitializer.getContainingClass(), ((PsiField)variable).getContainingClass())) return null;
          block = classInitializer.getBody();
          aClass = classInitializer.getContainingClass();
        }
        else {
          // field reference outside codeblock
          // check variable initialized before its usage
          final PsiField field = (PsiField)variable;

          aClass = field.getContainingClass();
          if (aClass == null || isFieldInitializedInOtherFieldInitializer(aClass, field, field.hasModifierProperty(PsiModifier.STATIC))) {
            return null;
          }
          block = null;
          // initializers will be checked later
          final PsiMethod[] constructors = aClass.getConstructors();
          for (PsiMethod constructor : constructors) {
            // variable must be initialized before its usage
            if (startOffset < constructor.getTextRange().getStartOffset()) continue;
            if (constructor.getBody() != null
                && variableDefinitelyAssignedIn(variable, constructor.getBody())) {
              return null;
            }
            // as a last chance, field may be initalized in this() call
            final List<PsiMethod> redirectedConstructors = getRedirectedConstructors(constructor);
            for (int j = 0; redirectedConstructors != null && j < redirectedConstructors.size(); j++) {
              PsiMethod redirectedConstructor = redirectedConstructors.get(j);
              // variable must be initialized before its usage
              if (startOffset < redirectedConstructor.getTextRange().getStartOffset()) continue;
              if (redirectedConstructor.getBody() != null
                  && variableDefinitelyAssignedIn(variable, redirectedConstructor.getBody())) {
                return null;
              }
            }
          }
        }

        if (aClass != null) {
          // field may be initialized in class initalizer
          final PsiClassInitializer[] initializers = aClass.getInitializers();
          for (PsiClassInitializer initializer : initializers) {
            PsiCodeBlock body = initializer.getBody();
            if (body == block) break;
            // variable referenced in initializer must be initialized in initializer preceding assignment
            // varaibel refernced in field initializer or in class initializier
            boolean shouldCheckInitializerOrder = block == null || block.getParent() instanceof PsiClassInitializer;
            if (shouldCheckInitializerOrder && startOffset < initializer.getTextRange().getStartOffset()) continue;
            if (initializer.hasModifierProperty(PsiModifier.STATIC)
                == variable.hasModifierProperty(PsiModifier.STATIC)) {
              if (variableDefinitelyAssignedIn(variable, body)) return null;
            }
          }
        }
      }
    }
    if (topBlock == null) return null;
    Collection<PsiReferenceExpression> codeBlockProblems = uninitializedVarProblems.get(topBlock);
    if (codeBlockProblems == null) {
      try {
        final ControlFlow controlFlow = ControlFlowFactory.getControlFlow(topBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
        codeBlockProblems = ControlFlowUtil.getReadBeforeWrite(controlFlow);
      }
      catch (AnalysisCanceledException e) {
        codeBlockProblems = Collections.EMPTY_LIST;
      }
      uninitializedVarProblems.put(topBlock, codeBlockProblems);
    }
    if (codeBlockProblems.contains(expression)) {
      final String name = expression.getElement().getText();
      String description = JavaErrorMessages.message("variable.not.initialized", name);
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
        HighlightInfoType.ERROR,
        expression,
        description);
      QuickFixAction.registerQuickFixAction(highlightInfo, new AddVariableInitializerFix(variable),null);
      return highlightInfo;
    }

    return null;
  }

  private static boolean inInnerClass(PsiElement element, PsiClass containingClass) {
    while (element != null) {
      if (element instanceof PsiClass) return !element.getManager().areElementsEquivalent(element, containingClass);
      element = element.getParent();
    }
    return false;
  }

  public static boolean isReassigned(PsiVariable variable,
                                  Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems,
                                  Map<PsiParameter, Boolean> parameterIsReassigned) {
    if (variable instanceof PsiLocalVariable) {
      final PsiElement parent = variable.getParent();
      if (parent == null) return false;
      final PsiElement declarationScope = parent.getParent();
      Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = getFinalVariableProblemsInBlock(finalVarProblems, declarationScope);
      return codeBlockProblems.contains(new ControlFlowUtil.VariableInfo(variable, null));
    }
    else if (variable instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)variable;
      final Boolean isReassigned = parameterIsReassigned.get(parameter);
      if (isReassigned != null) return isReassigned.booleanValue();
      boolean isAssigned = isAssigned(parameter);
      parameterIsReassigned.put(parameter, Boolean.valueOf(isAssigned));
      return isAssigned;
    }
    else {
      return false;
    }
  }

  private static boolean isAssigned(final PsiParameter parameter) {
    class MyProcessor implements PsiReferenceProcessor {
      boolean myIsWriteRefFound = false;
      public boolean execute(PsiReference reference) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiReferenceExpression) {
          myIsWriteRefFound |= PsiUtil.isAccessedForWriting((PsiExpression)element);
        }
        return !myIsWriteRefFound;
      }

      public boolean isWriteRefFound() {
        return myIsWriteRefFound;
      }
    }

    MyProcessor processor = new MyProcessor();
    parameter.getManager().getSearchHelper().processReferences(processor, parameter, new LocalSearchScope(parameter.getDeclarationScope()), true);
    return processor.isWriteRefFound();
  }

  //@top
  public static HighlightInfo checkFinalVariableMightAlreadyHaveBeenAssignedTo(PsiVariable variable,
                                                                               PsiReferenceExpression expression,
                                                                               Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems) {
    if (!PsiUtil.isAccessedForWriting(expression)) return null;

    final PsiElement scope = variable instanceof PsiField ? variable.getParent() :
                             variable.getParent() == null ? null : variable.getParent().getParent();
    PsiElement codeBlock = PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
    if (codeBlock == null) return null;
    Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = getFinalVariableProblemsInBlock(finalVarProblems, codeBlock);

    boolean alreadyAssigned = false;
    for (ControlFlowUtil.VariableInfo variableInfo : codeBlockProblems) {
      if (variableInfo.expression == expression) {
        alreadyAssigned = true;
        break;
      }
    }

    if (!alreadyAssigned) {
      if (!(variable instanceof PsiField)) return null;
      final PsiField field = (PsiField)variable;
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) return null;
      // field can get assigned in other field inititializers
      final PsiField[] fields = aClass.getFields();
      boolean isFieldStatic = field.hasModifierProperty(PsiModifier.STATIC);
      for (PsiField psiField : fields) {
        PsiExpression initializer = psiField.getInitializer();
        if (psiField != field
            && psiField.hasModifierProperty(PsiModifier.STATIC) == isFieldStatic
            && initializer != null
            && initializer != codeBlock
            && !variableDefinitelyNotAssignedIn(field, initializer)) {
          alreadyAssigned = true;
          break;
        }
      }

      if (!alreadyAssigned) {
        // field can get assigned in class initializers
        final PsiMember enclosingConstructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);
        if (enclosingConstructorOrInitializer == null
            || !aClass.getManager().areElementsEquivalent(enclosingConstructorOrInitializer.getParent(), aClass)) {
          return null;
        }
        final PsiClassInitializer[] initializers = aClass.getInitializers();
        for (PsiClassInitializer initializer : initializers) {
          if (initializer.hasModifierProperty(PsiModifier.STATIC)
              == field.hasModifierProperty(PsiModifier.STATIC)) {
            final PsiCodeBlock body = initializer.getBody();
            if (body == codeBlock) return null;
            try {
              final ControlFlow controlFlow = ControlFlowFactory.getControlFlow(body,
                                                                                LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
              if (!ControlFlowUtil.isVariableDefinitelyNotAssigned(field, controlFlow)) {
                alreadyAssigned = true;
                break;
              }
            }
            catch (AnalysisCanceledException e) {
              // incomplete code
              return null;
            }
          }
        }
      }

      if (!alreadyAssigned
          && !field.hasModifierProperty(PsiModifier.STATIC)) {
        // then check if instance field already assigned in other constructor
        final PsiMethod ctr = codeBlock.getParent() instanceof PsiMethod ?
                              (PsiMethod)codeBlock.getParent() : null;
        // assignment to final field in several constructors threatens us only if these are linked (there is this() call in the beginning)
        final List<PsiMethod> redirectedConstructors = ctr != null && ctr.isConstructor() ? getRedirectedConstructors(ctr) : null;
        for (int j = 0; redirectedConstructors != null && j < redirectedConstructors.size(); j++) {
          PsiMethod redirectedConstructor = redirectedConstructors.get(j);
          if (redirectedConstructor.getBody() != null &&
              variableDefinitelyAssignedIn(variable, redirectedConstructor.getBody())) {
            alreadyAssigned = true;
            break;
          }
        }
      }
    }

    if (alreadyAssigned) {
      String description = JavaErrorMessages.message("variable.already.assigned", variable.getName());
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
          HighlightInfoType.ERROR,
          expression,
          description);
      IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(variable.getModifierList(), PsiModifier.FINAL, false, false);
      QuickFixAction.registerQuickFixAction(highlightInfo, fix, null);
      QuickFixAction.registerQuickFixAction(highlightInfo, new DeferFinalAssignmentFix(variable, expression), null);
      return highlightInfo;
    }

    return null;
  }

  private static Collection<ControlFlowUtil.VariableInfo> getFinalVariableProblemsInBlock(Map<PsiElement,Collection<ControlFlowUtil.VariableInfo>> finalVarProblems, PsiElement codeBlock) {
    Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = finalVarProblems.get(codeBlock);
    if (codeBlockProblems == null) {
      try {
        final ControlFlow controlFlow = ControlFlowFactory.getControlFlow(codeBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
        codeBlockProblems = ControlFlowUtil.getInitializedTwice(controlFlow);
      }
      catch (AnalysisCanceledException e) {
        codeBlockProblems = Collections.EMPTY_LIST;
      }
      finalVarProblems.put(codeBlock, codeBlockProblems);
    }
    return codeBlockProblems;
  }

  //@top
  public static HighlightInfo checkFinalVariableInitalizedInLoop(PsiReferenceExpression expression, PsiElement resolved) {
    if (ControlFlowUtil.isVariableAssignedInLoop(expression, resolved)) {
      String description = JavaErrorMessages.message("variable.assigned.in.loop", ((PsiVariable)resolved).getName());
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
          HighlightInfoType.ERROR,
          expression,
          description);
      IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(((PsiVariable)resolved).getModifierList(), PsiModifier.FINAL, false, false);
      QuickFixAction.registerQuickFixAction(highlightInfo, fix, null);
      return highlightInfo;
    }
    return null;
  }

  //@top
  public static HighlightInfo checkCannotWriteToFinal(PsiExpression expression) {
    PsiReferenceExpression reference = null;
    if (expression instanceof PsiAssignmentExpression) {
      final PsiExpression left = ((PsiAssignmentExpression)expression).getLExpression();
      if (left instanceof PsiReferenceExpression) {
        reference = (PsiReferenceExpression)left;
      }
    }
    else if (expression instanceof PsiPostfixExpression) {
      final PsiExpression operand = ((PsiPostfixExpression)expression).getOperand();
      final IElementType sign = ((PsiPostfixExpression)expression).getOperationSign().getTokenType();
      if (operand instanceof PsiReferenceExpression && (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS)) {
        reference = (PsiReferenceExpression)operand;
      }
    }
    else if (expression instanceof PsiPrefixExpression) {
      final PsiExpression operand = ((PsiPrefixExpression)expression).getOperand();
      final IElementType sign = ((PsiPrefixExpression)expression).getOperationSign().getTokenType();
      if (operand instanceof PsiReferenceExpression && (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS)) {
        reference = (PsiReferenceExpression)operand;
      }
    }
    final PsiElement resolved = reference == null ? null : reference.resolve();
    PsiVariable variable = resolved instanceof PsiVariable ? (PsiVariable)resolved : null;
    if (variable == null || !variable.hasModifierProperty(PsiModifier.FINAL)) return null;
    if (!canWriteToFinal(variable, expression, reference)) {
      final String name = variable.getName();
      String description = JavaErrorMessages.message("assignment.to.final.variable", name);
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
          HighlightInfoType.ERROR,
          expression,
          description);
      final PsiClass innerClass = getInnerClassVariableReferencedFrom(variable, expression);
      if (innerClass == null || variable instanceof PsiField) {
        IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(variable.getModifierList(), PsiModifier.FINAL, false, false);
        QuickFixAction.registerQuickFixAction(highlightInfo, fix, null);
      }
      else {
        QuickFixAction.registerQuickFixAction(highlightInfo, new VariableAccessFromInnerClassFix(variable, innerClass), null);
      }
      return highlightInfo;
    }

    return null;
  }

  private static boolean canWriteToFinal(PsiVariable variable, PsiExpression expression, final PsiReferenceExpression reference) {
    if (variable.hasInitializer()) return false;
    if (variable instanceof PsiParameter) return false;
    PsiClass innerClass = getInnerClassVariableReferencedFrom(variable, expression);
    if (variable instanceof PsiField) {
      // if inside some field initializer
      if (HighlightUtil.findEnclosingFieldInitializer(expression) != null) return true;
      // assignment from within inner class is illegal always
      PsiField field = (PsiField)variable;
      if (innerClass != null && !innerClass.getManager().areElementsEquivalent(innerClass, field.getContainingClass())) return false;
      final PsiMember enclosingCtrOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);
      return enclosingCtrOrInitializer != null && isSameField(variable, enclosingCtrOrInitializer, field, reference);
    }
    if (variable instanceof PsiLocalVariable) {
      boolean isAccessedFromOtherClass = innerClass != null;
      if (isAccessedFromOtherClass) {
        return false;
      }
    }
    return true;
  }

  private static boolean isSameField(final PsiVariable variable,
                                     final PsiMember enclosingCtrOrInitializer,
                                     final PsiField field,
                                     final PsiReferenceExpression reference) {

    if (!variable.getManager().areElementsEquivalent(enclosingCtrOrInitializer.getParent(), field.getContainingClass())) return false;
    PsiExpression qualifierExpression = reference.getQualifierExpression();
    return qualifierExpression == null || qualifierExpression instanceof PsiThisExpression;
  }

  //@top
  static HighlightInfo checkVariableMustBeFinal(PsiVariable variable, PsiJavaCodeReferenceElement context) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) return null;
    final PsiClass innerClass = getInnerClassVariableReferencedFrom(variable, context);
    if (innerClass != null) {
      String description = JavaErrorMessages.message("variable.must.be.final", context.getText());

      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, context, description);
      QuickFixAction.registerQuickFixAction(highlightInfo, new VariableAccessFromInnerClassFix(variable, innerClass), null);
      return highlightInfo;
    }
    return null;
  }

  public static PsiClass getInnerClassVariableReferencedFrom(PsiVariable variable, PsiElement context) {
    PsiElement scope;
    if (variable instanceof PsiLocalVariable) {
      scope = variable.getParent().getParent(); // code block or for statement
    } else if (variable instanceof PsiParameter) {
      scope = ((PsiParameter)variable).getDeclarationScope();
    } else {
      scope = variable.getParent();
    }
    if (scope.getContainingFile() != context.getContainingFile()) return null;

    PsiElement parent = context.getParent();
    PsiElement prevParent = context;
    while (parent != null) {
      if (parent.equals(scope)) break;
      if (parent instanceof PsiClass) {
        if (!(prevParent instanceof PsiExpressionList && parent instanceof PsiAnonymousClass)) {
          return (PsiClass)parent;
        }
      }
      prevParent = parent;
      parent = parent.getParent();
    }
    return null;
  }

  //@top
  public static HighlightInfo checkInitializerCompleteNormally(PsiClassInitializer initializer) {
    final PsiCodeBlock body = initializer.getBody();
    // unhandled exceptions already reported
    try {
      final ControlFlow controlFlow = ControlFlowFactory.getControlFlow(body,
                                                                        LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(),
                                                                        false);
      final int completionReasons = ControlFlowUtil.getCompletionReasons(controlFlow, 0, controlFlow.getSize());
      if ((completionReasons & ControlFlowUtil.NORMAL_COMPLETION_REASON) == 0) {
        return HighlightInfo.createHighlightInfo(
            HighlightInfoType.ERROR,
            body,
            JavaErrorMessages.message("initializer.must.be.able.to.complete.normally"));
      }
    }
    catch (AnalysisCanceledException e) {
      // incomplete code
    }
    return null;
  }
}
