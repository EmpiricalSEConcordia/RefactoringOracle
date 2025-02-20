package com.intellij.refactoring.util;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RefactoringUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.RefactoringUtil");
  public static final int EXPR_COPY_SAFE = 0;
  public static final int EXPR_COPY_UNSAFE = 1;
  public static final int EXPR_COPY_PROHIBITED = 2;

  private RefactoringUtil() {
  }

  public static boolean isSourceRoot(final PsiDirectory directory) {
    if (directory.getManager() == null) return false;
    final Project project = directory.getProject();
    final VirtualFile virtualFile = directory.getVirtualFile();
    final VirtualFile sourceRootForFile = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(virtualFile);
    return Comparing.equal(virtualFile, sourceRootForFile);
  }

  public static boolean isInStaticContext(PsiElement element, final PsiClass aClass) {
    return PsiUtil.getEnclosingStaticElement(element, aClass) != null;
  }

  public static boolean isResolvableType(PsiType type) {
    return type.accept(new PsiTypeVisitor<Boolean>() {
      public Boolean visitPrimitiveType(PsiPrimitiveType primitiveType) {
        return Boolean.TRUE;
      }

      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      public Boolean visitClassType(PsiClassType classType) {
        if (classType.resolve() == null) return Boolean.FALSE;
        PsiType[] parameters = classType.getParameters();
        for (PsiType parameter : parameters) {
          if (parameter != null && !parameter.accept(this).booleanValue()) return Boolean.FALSE;
        }

        return Boolean.TRUE;
      }

      public Boolean visitWildcardType(PsiWildcardType wildcardType) {
        if (wildcardType.getBound() != null) return wildcardType.getBound().accept(this);
        return Boolean.TRUE;
      }
    }).booleanValue();
  }

  public static PsiElement replaceOccurenceWithFieldRef(PsiExpression occurrence, PsiField newField, PsiClass destinationClass, final PsiFile file)
    throws IncorrectOperationException {
    final PsiManager manager = occurrence.getManager();
    final String fieldName = newField.getName();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiElement element = occurrence.getUserData(ElementToWorkOn.PARENT);
    final PsiVariable psiVariable = facade.getResolveHelper().resolveReferencedVariable(fieldName, element != null ? element : occurrence);
    final PsiElementFactory factory = facade.getElementFactory();
    if (psiVariable != null && psiVariable.equals(newField)) {
      return IntroduceVariableBase.replace(occurrence, factory.createExpressionFromText(fieldName, null), manager.getProject());
    }
    else {
      final PsiReferenceExpression ref = (PsiReferenceExpression)factory.createExpressionFromText("this." + fieldName, null);
      if (newField.hasModifierProperty(PsiModifier.STATIC)) {
        ref.setQualifierExpression(factory.createReferenceExpression(destinationClass));
      }
      return IntroduceVariableBase.replace(occurrence, ref, manager.getProject());
    }
  }

  /**
   * @see com.intellij.psi.codeStyle.CodeStyleManager#suggestUniqueVariableName(String,com.intellij.psi.PsiElement,boolean)
   *      Cannot use method from code style manager: a collision with fieldToReplace is not a collision
   */
  public static String suggestUniqueVariableName(String baseName, PsiElement place, PsiField fieldToReplace) {
    int index = 0;
    while (true) {
      final String name = index > 0 ? baseName + index : baseName;
      index++;
      final PsiManager manager = place.getManager();
      PsiResolveHelper helper = JavaPsiFacade.getInstance(manager.getProject()).getResolveHelper();
      PsiVariable refVar = helper.resolveReferencedVariable(name, place);
      if (refVar != null && !manager.areElementsEquivalent(refVar, fieldToReplace)) continue;
      class CancelException extends RuntimeException {
      }

      try {
        place.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override public void visitClass(PsiClass aClass) {

          }

          @Override public void visitVariable(PsiVariable variable) {
            if (name.equals(variable.getName())) {
              throw new CancelException();
            }
          }
        });
      }
      catch (CancelException e) {
        continue;
      }

      return name;
    }
  }

  //order of usages accross different files is irrelevant
  public static void sortDepthFirstRightLeftOrder(final UsageInfo[] usages) {
    Arrays.sort(usages, new Comparator<UsageInfo>() {
      public int compare(final UsageInfo usage1, final UsageInfo usage2) {
        final PsiElement element1 = usage1.getElement();
        final PsiElement element2 = usage2.getElement();
        if (element1 == null || element2 == null) return 0;
        return element2.getTextRange().getStartOffset() - element1.getTextRange().getStartOffset();
      }
    });
  }

  @Nullable
  public static String suggestNewOverriderName(String oldOverriderName, String oldBaseName, String newBaseName) {
    if (oldOverriderName.equals(oldBaseName)) {
      return newBaseName;
    }
    int i;
    if (oldOverriderName.startsWith(oldBaseName)) {
      i = 0;
    }
    else {
      i = StringUtil.indexOfIgnoreCase(oldOverriderName, oldBaseName, 0);
    }
    if (i >= 0) {
      String newOverriderName = oldOverriderName.substring(0, i);
      if (Character.isUpperCase(oldOverriderName.charAt(i))) {
        newOverriderName += StringUtil.capitalize(newBaseName);
      }
      else {
        newOverriderName += newBaseName;
      }
      final int j = i + oldBaseName.length();
      if (j < oldOverriderName.length()) {
        newOverriderName += oldOverriderName.substring(j);
      }

      return newOverriderName;
    }
    return null;
  }

  public static boolean hasOnDemandStaticImport(final PsiElement element, final PsiClass aClass) {
    if (element.getContainingFile() instanceof PsiJavaFile) {
      final PsiImportList importList = ((PsiJavaFile)element.getContainingFile()).getImportList();
      if (importList != null) {
        final PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
        for(PsiImportStaticStatement stmt: importStaticStatements) {
          if (stmt.isOnDemand() && stmt.resolveTargetClass() == aClass) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static PsiElement replaceElementsWithMap(PsiElement replaceIn, final Map<PsiElement, PsiElement> elementsToReplace) throws IncorrectOperationException {
    for(Map.Entry<PsiElement, PsiElement> e: elementsToReplace.entrySet()) {
      if (e.getKey() == replaceIn) {
        return e.getKey().replace(e.getValue());
      }
      e.getKey().replace(e.getValue());
    }
    return replaceIn;
  }

  public static PsiElement getVariableScope(PsiLocalVariable localVar) {
    if (!(localVar instanceof ImplicitVariable)) {
      return localVar.getParent().getParent();
    }
    else {
      return ((ImplicitVariable)localVar).getDeclarationScope();
    }
  }

  public static PsiReturnStatement[] findReturnStatements(PsiMethod method) {
    ArrayList<PsiReturnStatement> vector = new ArrayList<PsiReturnStatement>();
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      addReturnStatements(vector, body);
    }
    return vector.toArray(new PsiReturnStatement[vector.size()]);
  }

  private static void addReturnStatements(ArrayList<PsiReturnStatement> vector, PsiElement element) {
    if (element instanceof PsiReturnStatement) {
      vector.add((PsiReturnStatement)element);
    }
    else if (!(element instanceof PsiClass)) {
      PsiElement[] children = element.getChildren();
      for (PsiElement child : children) {
        addReturnStatements(vector, child);
      }
    }
  }


  public static PsiElement getParentStatement(PsiElement place, boolean skipScopingStatements) {
    PsiElement parent = place;
    while (true) {
      if (parent instanceof PsiStatement) break;
      parent = parent.getParent();
      if (parent == null) return null;
    }
    PsiElement parentStatement = parent;
    parent = parentStatement instanceof PsiStatement ? parentStatement : parentStatement.getParent();
    while (parent instanceof PsiStatement) {
      if (!skipScopingStatements && ((parent instanceof PsiForStatement && parentStatement == ((PsiForStatement)parent).getBody()) || (
        parent instanceof PsiForeachStatement && parentStatement == ((PsiForeachStatement)parent).getBody()) || (
        parent instanceof PsiWhileStatement && parentStatement == ((PsiWhileStatement)parent).getBody()) || (
        parent instanceof PsiIfStatement &&
        (parentStatement == ((PsiIfStatement)parent).getThenBranch() || parentStatement == ((PsiIfStatement)parent).getElseBranch())))) {
        return parentStatement;
      }
      parentStatement = parent;
      parent = parent.getParent();
    }
    return parentStatement;
  }


  public static PsiElement getParentExpressionAnchorElement(PsiElement place) {
    PsiElement parent = place.getUserData(ElementToWorkOn.PARENT);
    if (parent == null) parent = place;
    while (true) {
      if (isExpressionAnchorElement(parent)) return parent;
      parent = parent.getParent();
      if (parent == null) return null;
    }
  }


  public static boolean isExpressionAnchorElement(PsiElement element) {
    return element instanceof PsiStatement || element instanceof PsiClassInitializer || element instanceof PsiField ||
           element instanceof PsiMethod;
  }

  /**
   * @param expression
   * @return loop body if expression is part of some loop's condition or for loop's increment part
   *         null otherwise
   */
  public static PsiElement getLoopForLoopCondition(PsiExpression expression) {
    PsiExpression outermost = expression;
    while (outermost.getParent() instanceof PsiExpression) {
      outermost = (PsiExpression)outermost.getParent();
    }
    if (outermost.getParent() instanceof PsiForStatement) {
      final PsiForStatement forStatement = (PsiForStatement)outermost.getParent();
      if (forStatement.getCondition() == outermost) {
        return forStatement;
      }
      else {
        return null;
      }
    }
    if (outermost.getParent() instanceof PsiExpressionStatement && outermost.getParent().getParent() instanceof PsiForStatement) {
      final PsiForStatement forStatement = (PsiForStatement)outermost.getParent().getParent();
      if (forStatement.getUpdate() == outermost.getParent()) {
        return forStatement;
      }
      else {
        return null;
      }
    }
    if (outermost.getParent() instanceof PsiWhileStatement) {
      return outermost.getParent();
    }
    if (outermost.getParent() instanceof PsiDoWhileStatement) {
      return outermost.getParent();
    }
    return null;
  }

  public static PsiClass getThisClass(PsiElement place) {
    PsiElement parent = place.getContext();
    if (parent == null) return null;
    PsiElement prev = null;
    while (true) {
      if (parent instanceof PsiClass) {
        if (!(parent instanceof PsiAnonymousClass && ((PsiAnonymousClass)parent).getArgumentList() == prev)) {
          return (PsiClass)parent;
        }
      }
      prev = parent;
      parent = parent.getContext();
      if (parent == null) return null;
    }
  }

  public static PsiClass getThisResolveClass(final PsiReferenceExpression place) {
    final JavaResolveResult resolveResult = place.advancedResolve(false);
    final PsiElement scope = resolveResult.getCurrentFileResolveScope();
    if (scope instanceof PsiClass) {
      return (PsiClass)scope;
    }
    return null;
  }

  public static PsiCall getEnclosingConstructorCall(PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    if (ref instanceof PsiReferenceExpression && parent instanceof PsiMethodCallExpression) return (PsiCall)parent;

    if (parent instanceof PsiAnonymousClass) {
      parent = parent.getParent();
    }

    return parent instanceof PsiNewExpression ? (PsiNewExpression)parent : null;
  }

  public static PsiMethod getEnclosingMethod(PsiElement element) {
    final PsiElement container = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class);
    return container instanceof PsiMethod ? (PsiMethod)container : null;
  }

  public static void renameVariableReferences(PsiVariable variable, String newName, SearchScope scope) throws IncorrectOperationException {
    for (PsiReference reference : ReferencesSearch.search(variable, scope)) {
      reference.handleElementRename(newName);
    }
  }

  public static boolean canBeDeclaredFinal(PsiVariable variable) {
    LOG.assertTrue(variable instanceof PsiLocalVariable || variable instanceof PsiParameter);
    final boolean isReassigned = HighlightControlFlowUtil
      .isReassigned(variable, new THashMap<PsiElement, Collection<ControlFlowUtil.VariableInfo>>(), new THashMap<PsiParameter, Boolean>());
    return !isReassigned;
  }

  public static PsiThisExpression createThisExpression(PsiManager manager, PsiClass qualifierClass) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    if (qualifierClass != null) {
      PsiThisExpression qualifiedThis = (PsiThisExpression)factory.createExpressionFromText("q.this", null);
      qualifiedThis = (PsiThisExpression)CodeStyleManager.getInstance(manager.getProject()).reformat(qualifiedThis);
      PsiJavaCodeReferenceElement thisQualifier = qualifiedThis.getQualifier();
      LOG.assertTrue(thisQualifier != null);
      thisQualifier.bindToElement(qualifierClass);
      return qualifiedThis;
    }
    else {
      return (PsiThisExpression)factory.createExpressionFromText("this", null);
    }
  }

  /**
   * removes a reference to the specified class from the reference list given
   *
   * @return if removed  - a reference to the class or null if there were no references to this class in the reference list
   */
  public static PsiJavaCodeReferenceElement removeFromReferenceList(PsiReferenceList refList, PsiClass aClass)
    throws IncorrectOperationException {
    PsiJavaCodeReferenceElement[] refs = refList.getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      if (ref.isReferenceTo(aClass)) {
        PsiJavaCodeReferenceElement refCopy = (PsiJavaCodeReferenceElement)ref.copy();
        ref.delete();
        return refCopy;
      }
    }
    return null;
  }

  public static PsiJavaCodeReferenceElement findReferenceToClass(PsiReferenceList refList, PsiClass aClass) {
    PsiJavaCodeReferenceElement[] refs = refList.getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      if (ref.isReferenceTo(aClass)) {
        return ref;
      }
    }
    return null;
  }

  public static PsiType getTypeByExpressionWithExpectedType(PsiExpression expr) {
    PsiType type = getTypeByExpression(expr);
    if (type != null) return type;
    ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getInstance(expr.getProject()).getExpectedTypes(expr, false);
    if (expectedTypes.length == 1) {
      type = expectedTypes[0].getType();
      if (!type.equalsToText("java.lang.Object")) return type;
    }
    return null;
  }

  public static PsiType getTypeByExpression(PsiExpression expr) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
    return getTypeByExpression(expr, factory);
  }

  private static PsiType getTypeByExpression(PsiExpression expr, final PsiElementFactory factory) {
    PsiType type = expr.getType();
    if (type == null) {
      if (expr instanceof PsiArrayInitializerExpression) {
        PsiExpression[] initializers = ((PsiArrayInitializerExpression)expr).getInitializers();
        if (initializers.length > 0) {
          PsiType initType = getTypeByExpression(initializers[0]);
          if (initType == null) return null;
          return initType.createArrayType();
        }
      }
      return null;
    }
    PsiClass refClass = PsiUtil.resolveClassInType(type);
    if (refClass instanceof PsiAnonymousClass) {
      type = ((PsiAnonymousClass)refClass).getBaseClassType();
    }
    if (type == PsiType.NULL) {
      ExpectedTypeInfo[] infos = ExpectedTypesProvider.getInstance(expr.getProject()).getExpectedTypes(expr, false);
      if (infos.length == 1) {
        type = infos[0].getType();
      }
      else {
        type = factory.createTypeByFQClassName("java.lang.Object", expr.getResolveScope());
      }
    }

    return GenericsUtil.getVariableTypeByExpressionType(type);
  }

  public static boolean isAssignmentLHS(PsiElement element) {
    PsiElement parent = element.getParent();

    return parent instanceof PsiAssignmentExpression && element.equals(((PsiAssignmentExpression)parent).getLExpression()) ||
           isPlusPlusOrMinusMinus(parent);
  }

  public static boolean isPlusPlusOrMinusMinus(PsiElement element) {
    if (element instanceof PsiPrefixExpression) {
      PsiJavaToken operandSign = ((PsiPrefixExpression)element).getOperationSign();
      return operandSign.getTokenType() == JavaTokenType.PLUSPLUS || operandSign.getTokenType() == JavaTokenType.MINUSMINUS;
    }
    else if (element instanceof PsiPostfixExpression) {
      IElementType operandTokenType = ((PsiPostfixExpression)element).getOperationTokenType();
      return operandTokenType == JavaTokenType.PLUSPLUS || operandTokenType == JavaTokenType.MINUSMINUS;
    }
    else {
      return false;
    }
  }

  private static void removeFinalParameters(PsiMethod method) throws IncorrectOperationException {
    // Remove final parameters
    PsiParameterList paramList = method.getParameterList();
    PsiParameter[] params = paramList.getParameters();

    for (PsiParameter param : params) {
      if (param.hasModifierProperty(PsiModifier.FINAL)) {
        param.getModifierList().setModifierProperty(PsiModifier.FINAL, false);
      }
    }
  }

  public static PsiElement getAnchorElementForMultipleExpressions(PsiExpression[] occurrences, PsiElement scope) {
    PsiElement anchor = null;
    for (PsiExpression occurrence : occurrences) {
      if (scope != null && !PsiTreeUtil.isAncestor(scope, occurrence, false)) {
        continue;
      }
      PsiElement anchor1 = getParentExpressionAnchorElement(occurrence);
      if (anchor1 == null) return null;

      if (anchor == null) {
        anchor = anchor1;
      }
      else {
        PsiElement commonParent = PsiTreeUtil.findCommonParent(anchor, anchor1);
        if (commonParent == null || anchor.getTextRange() == null || anchor1.getTextRange() == null) return null;
        PsiElement firstAnchor = anchor.getTextRange().getStartOffset() < anchor1.getTextRange().getStartOffset() ? anchor : anchor1;
        if (commonParent.equals(firstAnchor)) {
          anchor = firstAnchor;
        }
        else {
          if (commonParent instanceof PsiStatement) {
            anchor = commonParent;
          }
          else {
            PsiElement parent = firstAnchor;
            while (!parent.getParent().equals(commonParent)) {
              parent = parent.getParent();
            }
            final PsiElement newAnchor = getParentExpressionAnchorElement(parent);
            if (newAnchor != null) {
              anchor = newAnchor;
            }
            else {
              anchor = parent;
            }
          }
        }
      }
    }

    if (occurrences.length > 1 && anchor.getParent().getParent() instanceof PsiSwitchStatement) {
      PsiSwitchStatement switchStatement = (PsiSwitchStatement)anchor.getParent().getParent();
      if (switchStatement.getBody().equals(anchor.getParent())) {
        int startOffset = occurrences[0].getTextRange().getStartOffset();
        int endOffset = occurrences[occurrences.length - 1].getTextRange().getEndOffset();
        PsiStatement[] statements = switchStatement.getBody().getStatements();
        boolean isInDifferentCases = false;
        for (PsiStatement statement : statements) {
          if (statement instanceof PsiSwitchLabelStatement) {
            int caseOffset = statement.getTextRange().getStartOffset();
            if (startOffset < caseOffset && caseOffset < endOffset) {
              isInDifferentCases = true;
              break;
            }
          }
        }
        if (isInDifferentCases) {
          anchor = switchStatement;
        }
      }
    }

    return anchor;
  }

  public static void setVisibility(PsiModifierList modifierList, @Modifier String newVisibility) throws IncorrectOperationException {
    modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
    modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
    modifierList.setModifierProperty(PsiModifier.PROTECTED, false);
    modifierList.setModifierProperty(newVisibility, true);
  }

  public static boolean isMethodUsage(PsiElement element) {
    if (!(element instanceof PsiJavaCodeReferenceElement)) return false;
    PsiElement parent = element.getParent();
    if (parent instanceof PsiCall) {
      return true;
    }
    else if (parent instanceof PsiAnonymousClass) {
      return element.equals(((PsiAnonymousClass)parent).getBaseClassReference());
    }
    return false;
  }

  public static PsiExpressionList getArgumentListByMethodReference(PsiElement ref) {
    if (ref instanceof PsiEnumConstant) return ((PsiEnumConstant)ref).getArgumentList();
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiCall) {
      return ((PsiCall)parent).getArgumentList();
    }
    else if (parent instanceof PsiAnonymousClass) {
      return ((PsiNewExpression)parent.getParent()).getArgumentList();
    }
    LOG.assertTrue(false);
    return null;
  }

  public static PsiCallExpression getCallExpressionByMethodReference(PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiMethodCallExpression) {
      return (PsiMethodCallExpression)parent;
    }
    else if (parent instanceof PsiNewExpression) {
      return (PsiNewExpression)parent;
    }
    else if (parent instanceof PsiAnonymousClass) {
      return (PsiNewExpression)parent.getParent();
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  /**
   * @return List of highlighters
   */
  public static List<RangeHighlighter> highlightAllOccurences(Project project, PsiElement[] occurences, Editor editor) {
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    if (occurences.length > 1) {
      for (PsiElement occurrence : occurences) {
        final RangeMarker rangeMarker = occurrence.getUserData(ElementToWorkOn.TEXT_RANGE);
        if (rangeMarker != null) {
          highlightManager
            .addRangeHighlight(editor, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), attributes, true, highlighters);
        }
        else {
          final TextRange textRange = occurrence.getTextRange();
          highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), attributes, true, highlighters);
        }
      }
    }
    return highlighters;
  }

  public static String createTempVar(PsiExpression expr, PsiElement context, boolean declareFinal) throws IncorrectOperationException {
    PsiElement anchorStatement = getParentStatement(context, true);
    LOG.assertTrue(anchorStatement != null && anchorStatement.getParent() != null);

    Project project = expr.getProject();
    String[] suggestedNames =
      JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expr, null).names;
    final String prefix = suggestedNames[0];
    final String id = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(prefix, context, true);

    PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();

    if (expr instanceof PsiParenthesizedExpression) {
      PsiExpression expr1 = ((PsiParenthesizedExpression)expr).getExpression();
      if (expr1 != null) {
        expr = expr1;
      }
    }
    PsiDeclarationStatement decl = factory.createVariableDeclarationStatement(id, expr.getType(), expr);
    if (declareFinal) {
      ((PsiLocalVariable)decl.getDeclaredElements()[0]).getModifierList().setModifierProperty(PsiModifier.FINAL, true);
    }
    anchorStatement.getParent().addBefore(decl, anchorStatement);

    return id;
  }

  public static int verifySafeCopyExpression(PsiElement expr) {
    return verifySafeCopyExpressionSubElement(expr);

  }

  private static int verifySafeCopyExpressionSubElement(PsiElement element) {
    int result = EXPR_COPY_SAFE;
    if (element == null) return result;

    if (element instanceof PsiThisExpression || element instanceof PsiSuperExpression || element instanceof PsiIdentifier) {
      return EXPR_COPY_SAFE;
    }

    if (element instanceof PsiMethodCallExpression) {
      result = EXPR_COPY_UNSAFE;
    }

    if (element instanceof PsiNewExpression) {
      return EXPR_COPY_PROHIBITED;
    }

    if (element instanceof PsiAssignmentExpression) {
      return EXPR_COPY_PROHIBITED;
    }

    if (isPlusPlusOrMinusMinus(element)) {
      return EXPR_COPY_PROHIBITED;
    }

    PsiElement[] children = element.getChildren();

    for (PsiElement child : children) {
      int childResult = verifySafeCopyExpressionSubElement(child);
      result = Math.max(result, childResult);
    }
    return result;
  }

  public static PsiExpression convertInitializerToNormalExpression(PsiExpression expression, PsiType forcedReturnType)
    throws IncorrectOperationException {
    if (expression instanceof PsiArrayInitializerExpression) {
      return createNewExpressionFromArrayInitializer((PsiArrayInitializerExpression)expression, forcedReturnType);
    }
    return expression;
  }

  private static PsiExpression createNewExpressionFromArrayInitializer(PsiArrayInitializerExpression initializer, PsiType forcedType)
    throws IncorrectOperationException {
    PsiType initializerType = null;
    if (initializer != null) {
//        initializerType = myExpresssion.getType();
      if (forcedType != null) {
        initializerType = forcedType;
      }
      else {
        initializerType = getTypeByExpression(initializer);
      }
    }
    if (initializerType == null) {
      return initializer;
    }
    LOG.assertTrue(initializerType instanceof PsiArrayType);
    PsiElementFactory factory = JavaPsiFacade.getInstance(initializer.getProject()).getElementFactory();
    PsiNewExpression result =
      (PsiNewExpression)factory.createExpressionFromText("new " + initializerType.getPresentableText() + "{}", null);
    result = (PsiNewExpression)CodeStyleManager.getInstance(initializer.getProject()).reformat(result);
    PsiArrayInitializerExpression arrayInitializer = result.getArrayInitializer();
    LOG.assertTrue(arrayInitializer != null);
    arrayInitializer.replace(initializer);
    return result;
  }

  public static void abstractizeMethod(PsiClass targetClass, PsiMethod method) throws IncorrectOperationException {
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      body.delete();
    }

    method.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
    method.getModifierList().setModifierProperty(PsiModifier.FINAL, false);
    method.getModifierList().setModifierProperty(PsiModifier.SYNCHRONIZED, false);
    method.getModifierList().setModifierProperty(PsiModifier.NATIVE, false);

    if (!targetClass.isInterface()) {
      targetClass.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
    }

    removeFinalParameters(method);
  }

  public static boolean isInsideAnonymous(PsiElement element, PsiElement upTo) {
    for (PsiElement current = element; current != null && current != upTo; current = current.getParent()) {
      if (current instanceof PsiAnonymousClass) return true;
    }
    return false;
  }

  public static PsiExpression unparenthesizeExpression(PsiExpression expression) {
    while (expression instanceof PsiParenthesizedExpression) {
      final PsiExpression innerExpression = ((PsiParenthesizedExpression)expression).getExpression();
      if (innerExpression == null) return expression;
      expression = innerExpression;
    }
    return expression;
  }

  public static PsiExpression outermostParenthesizedExpression(PsiExpression expression) {
    while (expression.getParent() instanceof PsiParenthesizedExpression) {
      expression = (PsiParenthesizedExpression)expression.getParent();
    }
    return expression;
  }

  public static String getNewInnerClassName(PsiClass aClass, String oldInnerClassName, String newName) {
    if (!oldInnerClassName.endsWith(aClass.getName())) return newName;
    StringBuilder buffer = new StringBuilder(oldInnerClassName);
    buffer.replace(buffer.length() - aClass.getName().length(), buffer.length(), newName);
    return buffer.toString();
  }

  public static boolean isSuperOrThisCall(PsiStatement statement, boolean testForSuper, boolean testForThis) {
    if (!(statement instanceof PsiExpressionStatement)) return false;
    PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expression).getMethodExpression();
    if (testForSuper) {
      if ("super".equals(methodExpression.getText())) return true;
    }
    if (testForThis) {
      if ("this".equals(methodExpression.getText())) return true;
    }

    return false;
  }

  public static void visitImplicitSuperConstructorUsages(PsiClass subClass,
                                                         final ImplicitConstructorUsageVisitor implicitConstructorUsageVistor,
                                                         PsiClass superClass) {
    final PsiMethod baseDefaultConstructor = findDefaultConstructor(superClass);
    final PsiMethod[] constructors = subClass.getConstructors();
    if (constructors.length > 0) {
      for (PsiMethod constructor : constructors) {
        final PsiStatement[] statements = constructor.getBody().getStatements();
        if (statements.length < 1 || !isSuperOrThisCall(statements[0], true, true)) {
          implicitConstructorUsageVistor.visitConstructor(constructor, baseDefaultConstructor);
        }
      }
    }
    else {
      implicitConstructorUsageVistor.visitClassWithoutConstructors(subClass);
    }
  }

  private static PsiMethod findDefaultConstructor(final PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().getParametersCount() == 0) return constructor;
    }

    return null;
  }

  public static interface ImplicitConstructorUsageVisitor {
    void visitConstructor(PsiMethod constructor, PsiMethod baseConstructor);

    void visitClassWithoutConstructors(PsiClass aClass);
  }

  public interface Graph<T> {
    Set<T> getVertices();

    Set<T> getTargets(T source);
  }

  /**
   * Returns subset of <code>graph.getVertices()</code> that is a tranistive closure (by <code>graph.getTargets()<code>)
   * of the following property: initialRelation.value() of vertex or <code>graph.getTargets(vertex)</code> is true.
   * <p/>
   * Note that <code>graph.getTargets()</code> is not neccesrily a subset of <code>graph.getVertex()</code>
   *
   * @param graph
   * @param initialRelation
   * @return subset of graph.getVertices()
   */
  public static <T> Set<T> transitiveClosure(Graph<T> graph, Condition<T> initialRelation) {
    Set<T> result = new HashSet<T>();

    final Set<T> vertices = graph.getVertices();
    boolean anyChanged;
    do {
      anyChanged = false;
      for (T currentVertex : vertices) {
        if (!result.contains(currentVertex)) {
          if (!initialRelation.value(currentVertex)) {
            Set<T> targets = graph.getTargets(currentVertex);
            for (T currentTarget : targets) {
              if (result.contains(currentTarget) || initialRelation.value(currentTarget)) {
                result.add(currentVertex);
                anyChanged = true;
                break;
              }
            }
          }
          else {
            result.add(currentVertex);
          }
        }
      }
    }
    while (anyChanged);
    return result;
  }

  public static boolean equivalentTypes(PsiType t1, PsiType t2, PsiManager manager) {
    while (t1 instanceof PsiArrayType) {
      if (!(t2 instanceof PsiArrayType)) return false;
      t1 = ((PsiArrayType)t1).getComponentType();
      t2 = ((PsiArrayType)t2).getComponentType();
    }

    if (t1 instanceof PsiPrimitiveType) {
      return t2 instanceof PsiPrimitiveType && t1.equals(t2);
    }

    return manager.areElementsEquivalent(PsiUtil.resolveClassInType(t1), PsiUtil.resolveClassInType(t2));
  }

  public static List<PsiVariable> collectReferencedVariables(PsiElement scope) {
    final List<PsiVariable> result = new ArrayList<PsiVariable>();
    scope.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiElement element = expression.resolve();
        if (element instanceof PsiVariable) {
          result.add((PsiVariable)element);
        }
        final PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier != null) {
          qualifier.accept(this);
        }
      }
    });
    return result;
  }

  public static boolean isModifiedInScope(PsiVariable variable, PsiElement scope) {
    for (PsiReference reference : ReferencesSearch.search(variable, new LocalSearchScope(scope), false)) {
      if (isAssignmentLHS(reference.getElement())) return true;
    }
    return false;
  }

  private static String getNameOfReferencedParameter(PsiDocTag tag) {
    LOG.assertTrue("param".equals(tag.getName()));
    final PsiElement[] dataElements = tag.getDataElements();
    if (dataElements.length < 1) return null;
    return dataElements[0].getText();
  }

  public static void fixJavadocsForParams(PsiMethod method, Set<PsiParameter> newParameters) throws IncorrectOperationException {
    final PsiDocComment docComment = method.getDocComment();
    if (docComment == null) return;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiDocTag[] paramTags = docComment.findTagsByName("param");
    if (parameters.length > 0 && newParameters.size() < parameters.length && paramTags.length == 0) return;
    Map<PsiParameter, PsiDocTag> tagForParam = new HashMap<PsiParameter, PsiDocTag>();
    for (PsiParameter parameter : parameters) {
      boolean found = false;
      for (PsiDocTag paramTag : paramTags) {
        if (parameter.getName().equals(getNameOfReferencedParameter(paramTag))) {
          tagForParam.put(parameter, paramTag);
          found = true;
          break;
        }
      }
      if (!found && !newParameters.contains(parameter)) {
        tagForParam.put(parameter, null);
      }
    }

    List<PsiDocTag> newTags = new ArrayList<PsiDocTag>();
    for (PsiParameter parameter : parameters) {
      if (tagForParam.containsKey(parameter)) {
        final PsiDocTag psiDocTag = tagForParam.get(parameter);
        if (psiDocTag != null) {
          newTags.add((PsiDocTag)psiDocTag.copy());
        }
      }
      else {
        newTags.add(JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createParamTag(parameter.getName(), ""));
      }
    }
    PsiDocTag anchor = paramTags.length > 0 ? paramTags[paramTags.length - 1] : null;
    for (PsiDocTag psiDocTag : newTags) {
      anchor = (PsiDocTag)docComment.addAfter(psiDocTag, anchor);
    }
    for (PsiDocTag paramTag : paramTags) {
      paramTag.delete();
    }
  }

  public static PsiDirectory createPackageDirectoryInSourceRoot(PackageWrapper aPackage, final VirtualFile sourceRoot)
    throws IncorrectOperationException {
    final PsiDirectory[] directories = aPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      if (VfsUtil.isAncestor(sourceRoot, directory.getVirtualFile(), false)) {
        return directory;
      }
    }
    String qNameToCreate = qNameToCreateInSourceRoot(aPackage, sourceRoot);
    final String[] shortNames = qNameToCreate.split("\\.");
    PsiDirectory current = aPackage.getManager().findDirectory(sourceRoot);
    LOG.assertTrue(current != null);
    for (String shortName : shortNames) {
      PsiDirectory subdirectory = current.findSubdirectory(shortName);
      if (subdirectory == null) {
        subdirectory = current.createSubdirectory(shortName);
      }
      current = subdirectory;
    }
    return current;
  }

  public static String qNameToCreateInSourceRoot(PackageWrapper aPackage, final VirtualFile sourceRoot) throws IncorrectOperationException {
    String targetQName = aPackage.getQualifiedName();
    String sourceRootPackage =
      ProjectRootManager.getInstance(aPackage.getManager().getProject()).getFileIndex().getPackageNameByDirectory(sourceRoot);
    if (!canCreateInSourceRoot(sourceRootPackage, targetQName)) {
      throw new IncorrectOperationException(
        "Cannot create package '" + targetQName + "' in source folder " + sourceRoot.getPresentableUrl());
    }
    String result = targetQName.substring(sourceRootPackage.length());
    if (StringUtil.startsWithChar(result, '.')) result = result.substring(1);  // remove initial '.'
    return result;
  }

  public static boolean canCreateInSourceRoot(final String sourceRootPackage, final String targetQName) {
    if (sourceRootPackage == null || !targetQName.startsWith(sourceRootPackage)) return false;
    if (sourceRootPackage.length() == 0 || targetQName.length() == sourceRootPackage.length()) return true;
    return targetQName.charAt(sourceRootPackage.length()) == '.';
  }


  @Nullable
  public static PsiDirectory findPackageDirectoryInSourceRoot(PackageWrapper aPackage, final VirtualFile sourceRoot) {
    final PsiDirectory[] directories = aPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      if (VfsUtil.isAncestor(sourceRoot, directory.getVirtualFile(), false)) {
        return directory;
      }
    }
    String qNameToCreate;
    try {
      qNameToCreate = qNameToCreateInSourceRoot(aPackage, sourceRoot);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
    final String[] shortNames = qNameToCreate.split("\\.");
    PsiDirectory current = aPackage.getManager().findDirectory(sourceRoot);
    LOG.assertTrue(current != null);
    for (String shortName : shortNames) {
      PsiDirectory subdirectory = current.findSubdirectory(shortName);
      if (subdirectory == null) {
        return null;
      }
      current = subdirectory;
    }
    return current;
  }

  public static class ConditionCache<T> implements Condition<T> {
    private final Condition<T> myCondition;
    private final HashSet<T> myProcessedSet = new HashSet<T>();
    private final HashSet<T> myTrueSet = new HashSet<T>();

    public ConditionCache(Condition<T> condition) {
      myCondition = condition;
    }

    public boolean value(T object) {
      if (!myProcessedSet.contains(object)) {
        myProcessedSet.add(object);
        final boolean value = myCondition.value(object);
        if (value) {
          myTrueSet.add(object);
          return true;
        }
        return false;
      }
      return myTrueSet.contains(object);
    }
  }

  public static class IsDescendantOf implements Condition<PsiClass> {
    private final PsiClass myClass;
    private final ConditionCache<PsiClass> myConditionCache;

    public IsDescendantOf(PsiClass aClass) {
      myClass = aClass;
      myConditionCache = new ConditionCache<PsiClass>(new Condition<PsiClass>() {
        public boolean value(PsiClass aClass) {
          return InheritanceUtil.isInheritorOrSelf(aClass, myClass, true);
        }
      });
    }

    public boolean value(PsiClass aClass) {
      return myConditionCache.value(aClass);
    }
  }

  public static void analyzeModuleConflicts(Project project,
                                            Collection<? extends PsiElement> scope,
                                            final UsageInfo[] usages,
                                            PsiElement target,
                                            final Collection<String> conflicts) {
    if (scope == null) return;
    final VirtualFile vFile = PsiUtilBase.getVirtualFile(target);
    if (vFile == null) return;
    analyzeModuleConflicts(project, scope, usages, vFile, conflicts);
  }

  public static void analyzeModuleConflicts(Project project,
                                            final Collection<? extends PsiElement> scopes,
                                            final UsageInfo[] usages,
                                            final VirtualFile vFile,
                                            final Collection<String> conflicts) {
    if (scopes == null) return;

    for (final PsiElement scope : scopes) {
      if (scope instanceof PsiPackage || scope instanceof PsiDirectory) return;
    }

    final Module targetModule = ModuleUtil.findModuleForFile(vFile, project);
    if (targetModule == null) return;
    final GlobalSearchScope resolveScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(targetModule);
    final HashSet<PsiElement> reported = new HashSet<PsiElement>();
    for (final PsiElement scope : scopes) {
      scope.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          super.visitReferenceElement(reference);
          final PsiElement resolved = reference.resolve();
          if (resolved != null && !reported.contains(resolved) && !isAncestor(resolved, scopes) &&
              !PsiSearchScopeUtil.isInScope(resolveScope, resolved)) {
            final String scopeDescription =
              CommonRefactoringUtil.htmlEmphasize(RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(reference), true));
            final String message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.in.module.2",
                                                             ConflictsUtil.capitalize(CommonRefactoringUtil.htmlEmphasize(
                                                               RefactoringUIUtil.getDescription(resolved, true))), scopeDescription,
                                                                                                               CommonRefactoringUtil.htmlEmphasize(
                                                                                                                 targetModule.getName()));
            conflicts.add(message);
            reported.add(resolved);
          }
        }
      });
    }

    boolean isInTestSources = ModuleRootManager.getInstance(targetModule).getFileIndex().isInTestSourceContent(vFile);
    NextUsage:
    for (UsageInfo usage : usages) {
      if (usage instanceof MoveRenameUsageInfo) {
        final MoveRenameUsageInfo moveRenameUsageInfo = (MoveRenameUsageInfo)usage;
        final PsiElement element = usage.getElement();
        if (element != null && PsiTreeUtil.getParentOfType(element, PsiImportStatement.class, false) == null) {

          for (PsiElement scope : scopes) {
            if (PsiTreeUtil.isAncestor(scope, element, false)) continue NextUsage;
          }

          final GlobalSearchScope resolveScope1 = element.getResolveScope();
          if (!resolveScope1.isSearchInModuleContent(targetModule, isInTestSources)) {
            final PsiFile usageFile = element.getContainingFile();
            PsiElement container;
            if (usageFile instanceof PsiJavaFile) {
              container = ConflictsUtil.getContainer(element);
              if (container == null) container = usageFile;
            }
            else {
              container = usageFile;
            }
            final String scopeDescription = CommonRefactoringUtil.htmlEmphasize(RefactoringUIUtil.getDescription(container, true));
            final VirtualFile usageVFile = usageFile.getVirtualFile();
            if (usageVFile != null) {
              Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(usageVFile);
              if (module != null) {
                final String message;
                if (module == targetModule && isInTestSources) {
                  message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.from.production.of.module.2",
                                                      ConflictsUtil.capitalize(CommonRefactoringUtil.htmlEmphasize(
                                                        RefactoringUIUtil.getDescription(moveRenameUsageInfo.getReferencedElement(), true))),
                                                      scopeDescription,
                                                      CommonRefactoringUtil.htmlEmphasize(module.getName()));
                }
                else {
                  message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.from.module.2", 
                                                      ConflictsUtil.capitalize(CommonRefactoringUtil.htmlEmphasize(
                                                        RefactoringUIUtil.getDescription(moveRenameUsageInfo.getReferencedElement(), true))),
                                                      scopeDescription,
                                                      CommonRefactoringUtil.htmlEmphasize(module.getName()));
                }
                conflicts.add(message);
              }
            }
          }
        }
      }
    }
  }

  private static boolean isAncestor(final PsiElement resolved, final Collection<? extends PsiElement> scopes) {
    for (final PsiElement scope : scopes) {
      if (PsiTreeUtil.isAncestor(scope, resolved, false)) return true;
    }
    return false;
  }

  @Nullable
  public static PsiTypeParameterList createTypeParameterListWithUsedTypeParameters(@NotNull final PsiElement... elements) {
    return createTypeParameterListWithUsedTypeParameters(null, elements);
  }

  @Nullable
  public static PsiTypeParameterList createTypeParameterListWithUsedTypeParameters(final PsiTypeParameterList fromList, @NotNull final PsiElement... elements) {
    if (elements.length == 0) return null;
    final Set<PsiTypeParameter> used = new HashSet<PsiTypeParameter>();
    for (final PsiElement element : elements) {
      if (element == null) continue;
      element.accept(new JavaRecursiveElementWalkingVisitor() {

        @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          super.visitReferenceElement(reference);
          if (!reference.isQualified()) {
            final PsiElement resolved = reference.resolve();
            if (resolved instanceof PsiTypeParameter) {
              final PsiTypeParameter typeParameter = (PsiTypeParameter)resolved;
              if (PsiTreeUtil.isAncestor(typeParameter.getOwner(), element, true)) {
                used.add(typeParameter);
              }
            }
          }
        }

        @Override
        public void visitExpression(final PsiExpression expression) {
          super.visitExpression(expression);
          final PsiType type = expression.getType();
          final PsiClass resolved = PsiUtil.resolveClassInType(type);
          if (resolved instanceof PsiTypeParameter && PsiTreeUtil.isAncestor(((PsiTypeParameter)resolved).getOwner(), element, true)){
            used.add((PsiTypeParameter)resolved);
          }
        }
      });
    }

    if (fromList != null) {
      used.retainAll(Arrays.asList(fromList.getTypeParameters()));
    }

    PsiTypeParameter[] typeParameters = used.toArray(new PsiTypeParameter[used.size()]);

    Arrays.sort(typeParameters, new Comparator<PsiTypeParameter>() {
      public int compare(final PsiTypeParameter tp1, final PsiTypeParameter tp2) {
        return tp1.getTextRange().getStartOffset() - tp2.getTextRange().getStartOffset();
      }
    });

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(elements[0].getProject()).getElementFactory();
    try {
      final PsiClass aClass = elementFactory.createClassFromText("class A {}", null);
      PsiTypeParameterList list = aClass.getTypeParameterList();
      assert list != null;
      for (final PsiTypeParameter typeParameter : typeParameters) {
        list.add(typeParameter);
      }
      return list;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      assert false;
      return null;
    }
  }
}
