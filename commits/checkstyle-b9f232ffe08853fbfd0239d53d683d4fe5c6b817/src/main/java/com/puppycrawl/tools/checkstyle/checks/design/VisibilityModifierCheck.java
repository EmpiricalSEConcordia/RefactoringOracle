////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2015 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////
package com.puppycrawl.tools.checkstyle.checks.design;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.beanutils.ConversionException;

import antlr.collections.AST;

import com.google.common.collect.ImmutableList;
import com.puppycrawl.tools.checkstyle.api.AnnotationUtility;
import com.puppycrawl.tools.checkstyle.api.Check;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.ScopeUtils;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.api.Utils;

/**
 * Checks visibility of class members. Only static final, immutable or annotated
 * by specified annotation members may be public,
 * other class members must be private unless allowProtected/Package is set.
 * <p>
 * Public members are not flagged if the name matches the public
 * member regular expression (contains "^serialVersionUID$" by
 * default).
 * </p>
 * Rationale: Enforce encapsulation.
 * <p>
 * Check also has options making it less strict:
 * </p>
 * <p>
 * <b>ignoreAnnotationCanonicalNames</b> - the list of annotations canonical names
 * which ignore variables in consideration, if user will provide short annotation name
 * that type will match to any named the same type without consideration of package,
 * list by default:
 * <ul>
 * <li>org.junit.Rule</li>
 * <li>com.google.common.annotations.VisibleForTesting</li>
 * </ul>
 * </p>
 * <p>
 * For example such public field will be skipped by default value of list above:
 * </p>
 * <p>
 * <code>
 * <pre> @org.junit.Rule
 * public TemporaryFolder publicJUnitRule = new TemporaryFolder();
 * </pre>
 * </code>
 * </p>
 * <p>
 * <b>allowPublicImmutableFields</b> - which allows immutable fields be
 * declared as public if defined in final class. Default value is <b>true</b>
 * </p>
 * <p>
 * Field is known to be immutable if:
 * <ul>
 * <li>It's declared as final</li>
 * <li>Has either a primitive type or instance of class user defined to be immutable
 * (such as String, ImmutableCollection from Guava and etc)</li>
 * </ul>
 * </p>
 * <p>
 * Classes known to be immutable are listed in <b>immutableClassCanonicalNames</b> by their
 * <b>canonical</b> names. List by default:
 * <ul>
 * <li>java.lang.String</li>
 * <li>java.lang.Integer</li>
 * <li>java.lang.Byte</li>
 * <li>java.lang.Character</li>
 * <li>java.lang.Short</li>
 * <li>java.lang.Boolean</li>
 * <li>java.lang.Long</li>
 * <li>java.lang.Double</li>
 * <li>java.lang.Float</li>
 * <li>java.lang.StackTraceElement</li>
 * <li>java.lang.BigInteger</li>
 * <li>java.lang.BigDecimal</li>
 * <li>java.io.File</li>
 * <li>java.util.Locale</li>
 * <li>java.util.UUID</li>
 * <li>java.net.URL</li>
 * <li>java.net.URI</li>
 * <li>java.net.Inet4Address</li>
 * <li>java.net.Inet6Address</li>
 * <li>java.net.InetSocketAddress</li>
 * </ul>
 * User can override this list via adding <b>canonical</b> class names to
 * <b>immutableClassCanonicalNames</b>, if user will provide short class name all
 * that type will match to any named the same type without consideration of package.
 * </p>
 * <p>
 * <b>Rationale</b>: Forcing all fields of class to have private modified by default is good
 * in most cases, but in some cases it drawbacks in too much boilerplate get/set code.
 * One of such cases are immutable classes.
 * </p>
 * <p>
 * <b>Restriction</b>: Check doesn't check if class is immutable, there's no checking
 * if accessory methods are missing and all fields are immutable, we only check
 * <b>if current field is immutable by matching a name to user defined list of immutable classes
 * and defined in final class</b>
 * </p>
 * <p>
 * Star imports are out of scope of this Check. So if one of type imported via <b>star import</b>
 * collides with user specified one by its short name - there won't be Check's violation.
 * </p>
 * Examples:
 * <p>
 * Default Check's configuration will pass the code below:
 * </p>
 * <p>
 * <pre>
 * <code>
 * public final class ImmutableClass
 * {
 *     public final int intValue; // No warning
 *     public final java.lang.String notes; // No warning
 *     public final BigDecimal value; // No warning
 *
 *     public ImmutableClass(int intValue, BigDecimal value, String notes)
 *     {
 *         this.includes = ImmutableSet.copyOf(includes);
 *         this.excludes = ImmutableSet.copyOf(excludes);
 *         this.value = value;
 *         this.notes = notes;
 *     }
 * }
 * </code>
 * </pre>
 * <p>
 * To configure the Check passing fields of type com.google.common.collect.ImmutableSet and
 * java.util.List:
 * </p>
 * <p>
 * &lt;module name=&quot;VisibilityModifier&quot;&gt;
 *   &lt;property name=&quot;immutableClassCanonicalNames&quot; value=&quot;java.util.List,
 *   com.google.common.collect.ImmutableSet&quot;/&gt;
 * &lt;/module&gt;
 * </p>
 * <p>
 * <pre>
 * <code>
 * public final class ImmutableClass
 * {
 *     public final ImmutableSet&lt;String&gt; includes; // No warning
 *     public final ImmutableSet&lt;String&gt; excludes; // No warning
 *     public final BigDecimal value; // Warning here, type BigDecimal isn't specified as immutable
 *
 *     public ImmutableClass(Collection&lt;String&gt; includes, Collection&lt;String&gt; excludes,
 *                  BigDecimal value)
 *     {
 *         this.includes = ImmutableSet.copyOf(includes);
 *         this.excludes = ImmutableSet.copyOf(excludes);
 *         this.value = value;
 *         this.notes = notes;
 *     }
 * }
 * </code>
 * </pre>
 * </p>
 * <p>
 * To configure the Check passing fields annotated with
 * <pre>@com.annotation.CustomAnnotation</pre>:
 * </p>
 * <p>
 * &lt;module name=&quot;VisibilityModifier&quot;&gt;
 *   &lt;property name=&quot;ignoreAnnotationCanonicalNames&quot; value=&quot;
 *   com.annotation.CustomAnnotation&quot;/&gt;
 * &lt;/module&gt;
 * </p>
 * <p>
 * <code>
 * <pre> @com.annotation.CustomAnnotation
 * String customAnnotated; // No warning
 * </pre>
 * <pre> @CustomAnnotation
 * String shortCustomAnnotated; // No warning
 * </pre>
 * </code>
 * </p>
 * <p>
 * To configure the Check passing fields annotated with short annotation name
 * <pre>@CustomAnnotation</pre>:
 * </p>
 * <p>
 * &lt;module name=&quot;VisibilityModifier&quot;&gt;
 *   &lt;property name=&quot;ignoreAnnotationCanonicalNames&quot;
 *   value=&quot;CustomAnnotation&quot;/&gt;
 * &lt;/module&gt;
 * </p>
 * <p>
 * <code>
 * <pre> @CustomAnnotation
 * String customAnnotated; // No warning
 * </pre>
 * <pre> @com.annotation.CustomAnnotation
 * String customAnnotated1; // No warning
 * </pre>
 * <pre> @mypackage.annotation.CustomAnnotation
 * String customAnnotatedAnotherPackage; // another package but short name matches
 *                                       // so no violation
 * </pre>
 * </code>
 * </p>
 *
 * @author <a href="mailto:nesterenko-aleksey@list.ru">Aleksey Nesterenko</a>
 */
public class VisibilityModifierCheck
    extends Check
{

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_KEY = "variable.notPrivate";

    /** whether protected members are allowed */
    private boolean protectedAllowed;

    /** whether package visible members are allowed */
    private boolean packageAllowed;

    /**
     * pattern for public members that should be ignored.  Note:
     * Earlier versions of checkstyle used ^f[A-Z][a-zA-Z0-9]*$ as the
     * default to allow CMP for EJB 1.1 with the default settings.
     * With EJB 2.0 it is not longer necessary to have public access
     * for persistent fields.
     */
    private String publicMemberFormat = "^serialVersionUID$";

    /** regexp for public members that should be ignored */
    private Pattern publicMemberPattern = Pattern.compile(publicMemberFormat);

    /** List of ignore annotations canonical names. */
    private List<String> ignoreAnnotationCanonicalNames =
            new ArrayList<>(DEFAULT_IGNORE_ANNOTATIONS);

    /** List of ignore annotations short names. */
    private List<String> ignoreAnnotationShortNames =
            getClassShortNames(DEFAULT_IGNORE_ANNOTATIONS);

    /** Allows immutable fields to be declared as public. */
    private boolean allowPublicImmutableFields = true;

    /** List of immutable classes canonical names. */
    private List<String> immutableClassCanonicalNames = new ArrayList<>(DEFAULT_IMMUTABLE_TYPES);

    /** List of immutable classes short names. */
    private final List<String> immutableClassShortNames =
            getClassShortNames(DEFAULT_IMMUTABLE_TYPES);

    /** Default immutable types canonical names. */
    private static final List<String> DEFAULT_IMMUTABLE_TYPES = ImmutableList.of(
        "java.lang.String",
        "java.lang.Integer",
        "java.lang.Byte",
        "java.lang.Character",
        "java.lang.Short",
        "java.lang.Boolean",
        "java.lang.Long",
        "java.lang.Double",
        "java.lang.Float",
        "java.lang.StackTraceElement",
        "java.math.BigInteger",
        "java.math.BigDecimal",
        "java.io.File",
        "java.util.Locale",
        "java.util.UUID",
        "java.net.URL",
        "java.net.URI",
        "java.net.Inet4Address",
        "java.net.Inet6Address",
        "java.net.InetSocketAddress"
    );

    /** Default ignore annotations canonical names. */
    private static final List<String> DEFAULT_IGNORE_ANNOTATIONS = ImmutableList.of(
        "org.junit.Rule",
        "com.google.common.annotations.VisibleForTesting"
    );

    /** contains explicit access modifiers. */
    private static final String[] EXPLICIT_MODS = {"public", "private", "protected"};

    /** @return whether protected members are allowed */
    public boolean isProtectedAllowed()
    {
        return protectedAllowed;
    }

    /**
     * Set the list of ignore annotations.
     * @param annotationNames array of ignore annotations canonical names.
     */
    public void setIgnoreAnnotationCanonicalNames(String[] annotationNames)
    {
        ignoreAnnotationCanonicalNames = Arrays.asList(annotationNames);
    }

    /**
     * Set whether protected members are allowed.
     * @param protectedAllowed whether protected members are allowed
     */
    public void setProtectedAllowed(boolean protectedAllowed)
    {
        this.protectedAllowed = protectedAllowed;
    }

    /** @return whether package visible members are allowed */
    public boolean isPackageAllowed()
    {
        return packageAllowed;
    }

    /**
     * Set whether package visible members are allowed.
     * @param packageAllowed whether package visible members are allowed
     */
    public void setPackageAllowed(boolean packageAllowed)
    {
        this.packageAllowed = packageAllowed;
    }

    /**
     * Set the pattern for public members to ignore.
     * @param pattern pattern for public members to ignore.
     */
    public void setPublicMemberPattern(String pattern)
    {
        try {
            publicMemberPattern = Utils.getPattern(pattern);
            publicMemberFormat = pattern;
        }
        catch (final PatternSyntaxException e) {
            throw new ConversionException("unable to parse " + pattern, e);
        }
    }

    /**
     * @return the regexp for public members to ignore.
     */
    private Pattern getPublicMemberRegexp()
    {
        return publicMemberPattern;
    }

    /**
     * Sets whether public immutable are allowed.
     * @param allow user's value.
     */
    public void setAllowPublicImmutableFields(boolean allow)
    {
        this.allowPublicImmutableFields = allow;
    }

    /**
     * Set the list of immutable classes types names.
     * @param classNames array of immutable types canonical names.
     */
    public void setImmutableClassCanonicalNames(String[] classNames)
    {
        immutableClassCanonicalNames = Arrays.asList(classNames);
    }

    @Override
    public int[] getDefaultTokens()
    {
        return new int[] {
            TokenTypes.VARIABLE_DEF,
            TokenTypes.IMPORT,
        };
    }

    @Override
    public int[] getAcceptableTokens()
    {
        return new int[] {
            TokenTypes.VARIABLE_DEF,
            TokenTypes.OBJBLOCK,
            TokenTypes.IMPORT,
        };
    }

    @Override
    public void beginTree(DetailAST rootAst)
    {
        immutableClassShortNames.clear();
        final List<String> classShortNames =
                getClassShortNames(immutableClassCanonicalNames);
        immutableClassShortNames.addAll(classShortNames);

        ignoreAnnotationShortNames.clear();
        final List<String> annotationShortNames =
                getClassShortNames(ignoreAnnotationCanonicalNames);
        ignoreAnnotationShortNames.addAll(annotationShortNames);
    }

    @Override
    public void visitToken(DetailAST ast)
    {
        switch (ast.getType()) {
            case TokenTypes.VARIABLE_DEF:
                if (!isAnonymousClassVariable(ast)) {
                    visitVariableDef(ast);
                }
                break;
            case TokenTypes.IMPORT:
                visitImport(ast);
                break;
            default:
                final String exceptionMsg = "Unexpected token type: " + ast.getText();
                throw new IllegalArgumentException(exceptionMsg);
        }
    }

    /**
     * Checks if current variable definition is definition of an anonymous class.
     * @param variableDef {@link TokenTypes#VARIABLE_DEF VARIABLE_DEF}
     * @return true if current variable definition is definition of an anonymous class.
     */
    private static boolean isAnonymousClassVariable(DetailAST variableDef)
    {
        return variableDef.getParent().getType() != TokenTypes.OBJBLOCK;
    }

    /**
     * Checks access modifier of given variable.
     * If it is not proper according to Check - puts violation on it.
     * @param variableDef variable to check.
     */
    private void visitVariableDef(DetailAST variableDef)
    {
        final boolean inInterfaceOrAnnotationBlock =
                ScopeUtils.inInterfaceOrAnnotationBlock(variableDef);

        if (!inInterfaceOrAnnotationBlock && !hasIgnoreAnnotation(variableDef)) {
            final DetailAST varNameAST = getVarNameAST(variableDef);
            final String varName = varNameAST.getText();
            if (!hasProperAccessModifier(variableDef, varName)) {
                log(varNameAST.getLineNo(), varNameAST.getColumnNo(),
                        MSG_KEY, varName);
            }
        }
    }

    /**
     * Checks if variable def has ignore annotation.
     * @param variableDef {@link TokenTypes#VARIABLE_DEF VARIABLE_DEF}
     * @return true if variable def has ignore annotation.
     */
    private boolean hasIgnoreAnnotation(DetailAST variableDef)
    {
        final DetailAST firstIgnoreAnnotation =
                 containsMatchingAnnotation(variableDef);
        return firstIgnoreAnnotation != null;
    }

    /**
     * Checks imported type. If type's canonical name was not specified in
     * <b>immutableClassCanonicalNames</b>, but it's short name collides with one from
     * <b>immutableClassShortNames</b> - removes it from the last one.
     * @param importAst {@link TokenTypes#IMPORT Import}
     */
    private void visitImport(DetailAST importAst)
    {
        if (!isStarImport(importAst)) {
            final DetailAST type = importAst.getFirstChild();
            final String canonicalName = getCanonicalName(type);
            final String shortName = getClassShortName(canonicalName);

            // If imported canonical class name is not specified as allowed immutable class,
            // but its short name collides with one of specified class - removes the short name
            // from list to avoid names collision
            if (!immutableClassCanonicalNames.contains(canonicalName)
                     && immutableClassShortNames.contains(shortName))
            {
                immutableClassShortNames.remove(shortName);
            }
            if (!ignoreAnnotationCanonicalNames.contains(canonicalName)
                     && ignoreAnnotationShortNames.contains(shortName))
            {
                ignoreAnnotationShortNames.remove(shortName);
            }
        }
    }

    /**
     * Checks if current import is star import. E.g.:
     * <p>
     * <code>
     * import java.util.*;
     * </code>
     * </p>
     * @param importAst {@link TokenTypes#IMPORT Import}
     * @return true if it is star import
     */
    private static boolean isStarImport(DetailAST importAst)
    {
        boolean result = false;
        DetailAST toVisit = importAst;
        while (toVisit != null) {
            toVisit = getNextSubTreeNode(toVisit, importAst);
            if (toVisit != null && toVisit.getType() == TokenTypes.STAR) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * Checks if current variable has proper access modifier according to Check's options.
     * @param variableDef Variable definition node.
     * @param variableName Variable's name.
     * @return true if variable has proper access modifier.
     */
    private boolean hasProperAccessModifier(DetailAST variableDef, String variableName)
    {
        boolean result = true;

        final Set<String> mods = getModifiers(variableDef);
        final String variableScope = getVisibilityScope(mods);

        if (!"private".equals(variableScope)) {
            final DetailAST classDef = variableDef.getParent().getParent();
            final Set<String> classModifiers = getModifiers(classDef);

            result =
                (mods.contains("static") && mods.contains("final"))
                || (isPackageAllowed() && "package".equals(variableScope))
                || (isProtectedAllowed() && "protected".equals(variableScope))
                || ("public".equals(variableScope)
                   && getPublicMemberRegexp().matcher(variableName).find()
                   || (allowPublicImmutableFields
                      && classModifiers.contains("final") && isImmutableField(variableDef)));
        }

        return result;
    }

    /**
     * Returns the variable name in a VARIABLE_DEF AST.
     * @param variableDefAST an AST where type == VARIABLE_DEF AST.
     * @return the variable name in variableDefAST
     */
    private static DetailAST getVarNameAST(DetailAST variableDefAST)
    {
        DetailAST ast = variableDefAST.getFirstChild();
        DetailAST varNameAst = null;
        while (ast != null) {
            final DetailAST nextSibling = ast.getNextSibling();
            if (ast.getType() == TokenTypes.TYPE) {
                varNameAst = nextSibling;
                break;
            }
            ast = nextSibling;
        }
        return varNameAst;
    }

    /**
     * Returns the set of modifier Strings for a VARIABLE_DEF or CLASS_DEF AST.
     * @param defAST AST for a variable or class definition.
     * @return the set of modifier Strings for defAST.
     */
    private static Set<String> getModifiers(DetailAST defAST)
    {
        final AST modifiersAST = defAST.findFirstToken(TokenTypes.MODIFIERS);
        final Set<String> modifiersSet = new HashSet<>();
        if (modifiersAST != null) {
            AST modifier = modifiersAST.getFirstChild();
            while (modifier != null) {
                modifiersSet.add(modifier.getText());
                modifier = modifier.getNextSibling();
            }
        }
        return modifiersSet;

    }

    /**
     * Returns the visibility scope specified with a set of modifiers.
     * @param modifiers the set of modifier Strings
     * @return one of "public", "private", "protected", "package"
     */
    private static String getVisibilityScope(Set<String> modifiers)
    {
        String accessModifier = "package";
        for (final String modifier : EXPLICIT_MODS) {
            if (modifiers.contains(modifier)) {
                accessModifier = modifier;
                break;
            }
        }
        return accessModifier;
    }

    /**
     * Checks if current field is immutable:
     * has final modifier and either a primitive type or instance of class
     * known to be immutable (such as String, ImmutableCollection from Guava and etc).
     * Classes known to be immutable are listed in
     * {@link VisibilityModifierCheck#immutableClassCanonicalNames}
     * @param variableDef Field in consideration.
     * @return true if field is immutable.
     */
    private boolean isImmutableField(DetailAST variableDef)
    {
        boolean result = false;

        final DetailAST modifiers = variableDef.findFirstToken(TokenTypes.MODIFIERS);
        final boolean isFinal = modifiers.branchContains(TokenTypes.FINAL);
        if (isFinal) {
            final DetailAST type = variableDef.findFirstToken(TokenTypes.TYPE);
            final boolean isCanonicalName = type.getFirstChild().getType() == TokenTypes.DOT;
            final String typeName = getTypeName(type, isCanonicalName);

            result = (!isCanonicalName && isPrimitive(type))
                     || immutableClassShortNames.contains(typeName)
                     || (isCanonicalName && immutableClassCanonicalNames.contains(typeName));
        }
        return result;
    }

    /**
     * Gets the name of type from given ast {@link TokenTypes#TYPE TYPE} node.
     * If type is specified via its canonical name - canonical name will be returned,
     * else - short type's name.
     * @param type {@link TokenTypes#TYPE TYPE} node.
     * @param isCanonicalName is given name canonical.
     * @return String representation of given type's name.
     */
    private static String getTypeName(DetailAST type, boolean isCanonicalName)
    {
        String typeName = "";
        if (isCanonicalName) {
            typeName = getCanonicalName(type);
        }
        else {
            typeName = type.getFirstChild().getText();
        }
        return typeName;
    }

    /**
     * Checks if current type is primitive type (int, short, float, boolean, double, etc.).
     * As primitive types have special tokens for each one, such as:
     * LITERAL_INT, LITERAL_BOOLEAN, etc.
     * So, if type's identifier differs from {@link TokenTypes#IDENT IDENT} token - it's a
     * primitive type.
     * @param type Ast {@link TokenTypes#TYPE TYPE} node.
     * @return true if current type is primitive type.
     */
    private static boolean isPrimitive(DetailAST type)
    {
        return type.getFirstChild().getType() != TokenTypes.IDENT;
    }

    /**
     * Gets canonical type's name from given {@link TokenTypes#TYPE TYPE} node.
     * @param type DetailAST {@link TokenTypes#TYPE TYPE} node.
     * @return canonical type's name
     */
    private static String getCanonicalName(DetailAST type)
    {
        final StringBuilder canonicalNameBuilder = new StringBuilder();
        DetailAST toVisit = type.getFirstChild();
        while (toVisit != null) {
            toVisit = getNextSubTreeNode(toVisit, type);
            if (toVisit != null && toVisit.getType() == TokenTypes.IDENT) {
                canonicalNameBuilder.append(toVisit.getText());
                final DetailAST nextSubTreeNode = getNextSubTreeNode(toVisit,
                         type);
                if (nextSubTreeNode != null && nextSubTreeNode.getType() != TokenTypes.SEMI) {
                    canonicalNameBuilder.append('.');
                }
            }
        }
        return canonicalNameBuilder.toString();
    }

    /**
     * Gets the next node of a syntactical tree (child of a current node or
     * sibling of a current node, or sibling of a parent of a current node)
     * @param currentNodeAst Current node in considering
     * @param subTreeRootAst SubTree root
     * @return Current node after bypassing, if current node reached the root of a subtree
     *        method returns null
     */
    private static DetailAST
        getNextSubTreeNode(DetailAST currentNodeAst, DetailAST subTreeRootAst)
    {
        DetailAST currentNode = currentNodeAst;
        DetailAST toVisitAst = currentNode.getFirstChild();
        while (toVisitAst == null) {
            toVisitAst = currentNode.getNextSibling();
            if (toVisitAst == null) {
                if (currentNode.getParent().equals(subTreeRootAst)
                         && currentNode.getParent().getColumnNo() == subTreeRootAst.getColumnNo())
                {
                    break;
                }
                currentNode = currentNode.getParent();
            }
        }
        currentNode = toVisitAst;
        return currentNode;
    }

    /**
     * Gets the list with short names classes.
     * These names are taken from array of classes canonical names.
     * @param canonicalClassNames canonical class names.
     * @return the list of short names of classes.
     */
    private static List<String> getClassShortNames(List<String> canonicalClassNames)
    {
        final List<String> shortNames = new ArrayList<>();
        for (String canonicalClassName : canonicalClassNames) {
            final String shortClassName = canonicalClassName
                    .substring(canonicalClassName.lastIndexOf(".") + 1,
                    canonicalClassName.length());
            shortNames.add(shortClassName);
        }
        return shortNames;
    }

    /**
     * Gets the short class name from given canonical name.
     * @param canonicalClassName canonical class name.
     * @return short name of class.
     */
    private static String getClassShortName(String canonicalClassName)
    {
        final String shortClassName = canonicalClassName
                .substring(canonicalClassName.lastIndexOf(".") + 1,
                canonicalClassName.length());
        return shortClassName;
    }

    /**
     * Checks whether the AST is annotated with
     * an annotation containing the passed in regular
     * expression and return the AST representing that
     * annotation.
     *
     * <p>
     * This method will not look for imports or package
     * statements to detect the passed in annotation.
     * </p>
     *
     * <p>
     * To check if an AST contains a passed in annotation
     * taking into account fully-qualified names
     * (ex: java.lang.Override, Override)
     * this method will need to be called twice. Once for each
     * name given.
     * </p>
     *
     * @param variableDef {@link TokenTypes#VARIABLE_DEF variable def node}.
     * @return the AST representing the first such annotation or null if
     *         no such annotation was found
     */
    private DetailAST containsMatchingAnnotation(DetailAST variableDef)
    {
        DetailAST matchingAnnotation = null;

        final DetailAST holder = AnnotationUtility.getAnnotationHolder(variableDef);

        for (DetailAST child = holder.getFirstChild();
            child != null; child = child.getNextSibling())
        {
            if (child.getType() == TokenTypes.ANNOTATION) {
                final DetailAST at = child.getFirstChild();
                final String name =
                    FullIdent.createFullIdent(at.getNextSibling()).getText();
                if (ignoreAnnotationCanonicalNames.contains(name)
                         || ignoreAnnotationShortNames.contains(name))
                {
                    matchingAnnotation = child;
                    break;
                }
            }
        }

        return matchingAnnotation;
    }
}
