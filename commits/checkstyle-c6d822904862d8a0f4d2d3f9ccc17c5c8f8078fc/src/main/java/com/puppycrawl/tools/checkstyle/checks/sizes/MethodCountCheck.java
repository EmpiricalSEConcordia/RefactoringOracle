////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2018 the original author or authors.
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

package com.puppycrawl.tools.checkstyle.checks.sizes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

import com.puppycrawl.tools.checkstyle.FileStatefulCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.Scope;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.utils.ScopeUtils;

/**
 * Checks the number of methods declared in each type declaration by access
 * modifier or total count.
 * @author Alexander Jesse
 * @author Oliver Burn
 */
@FileStatefulCheck
public final class MethodCountCheck extends AbstractCheck {

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_PRIVATE_METHODS = "too.many.privateMethods";

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_PACKAGE_METHODS = "too.many.packageMethods";

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_PROTECTED_METHODS = "too.many.protectedMethods";

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_PUBLIC_METHODS = "too.many.publicMethods";

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_MANY_METHODS = "too.many.methods";

    /** Default maximum number of methods. */
    private static final int DEFAULT_MAX_METHODS = 100;

    /** Maintains stack of counters, to support inner types. */
    private final Deque<MethodCounter> counters = new ArrayDeque<>();

    /** Maximum private methods. */
    private int maxPrivate = DEFAULT_MAX_METHODS;
    /** Maximum package methods. */
    private int maxPackage = DEFAULT_MAX_METHODS;
    /** Maximum protected methods. */
    private int maxProtected = DEFAULT_MAX_METHODS;
    /** Maximum public methods. */
    private int maxPublic = DEFAULT_MAX_METHODS;
    /** Maximum total number of methods. */
    private int maxTotal = DEFAULT_MAX_METHODS;

    @Override
    public int[] getDefaultTokens() {
        return getAcceptableTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[] {
            TokenTypes.CLASS_DEF,
            TokenTypes.ENUM_CONSTANT_DEF,
            TokenTypes.ENUM_DEF,
            TokenTypes.INTERFACE_DEF,
            TokenTypes.ANNOTATION_DEF,
            TokenTypes.METHOD_DEF,
        };
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] {TokenTypes.METHOD_DEF};
    }

    @Override
    public void visitToken(DetailAST ast) {
        if (ast.getType() == TokenTypes.METHOD_DEF) {
            if (isInLatestScopeDefinition(ast)) {
                raiseCounter(ast);
            }
        }
        else {
            counters.push(new MethodCounter(ast));
        }
    }

    @Override
    public void leaveToken(DetailAST ast) {
        if (ast.getType() != TokenTypes.METHOD_DEF) {
            final MethodCounter counter = counters.pop();

            checkCounters(counter, ast);
        }
    }

    /**
     * Checks if there is a scope definition to check and that the method is found inside that scope
     * (class, enum, etc.).
     * @param methodDef
     *        The method to analyze.
     * @return {@code true} if the method is part of the latest scope definition and should be
     *         counted.
     */
    private boolean isInLatestScopeDefinition(DetailAST methodDef) {
        boolean result = false;

        if (!counters.isEmpty()) {
            final DetailAST latestDefinition = counters.peek().getScopeDefinition();

            result = latestDefinition == methodDef.getParent().getParent();
        }

        return result;
    }

    /**
     * Determine the visibility modifier and raise the corresponding counter.
     * @param method
     *            The method-subtree from the AbstractSyntaxTree.
     */
    private void raiseCounter(DetailAST method) {
        final MethodCounter actualCounter = counters.peek();
        final DetailAST temp = method.findFirstToken(TokenTypes.MODIFIERS);
        final Scope scope = ScopeUtils.getScopeFromMods(temp);
        actualCounter.increment(scope);
    }

    /**
     * Check the counters and report violations.
     * @param counter the method counters to check
     * @param ast to report errors against.
     */
    private void checkCounters(MethodCounter counter, DetailAST ast) {
        checkMax(maxPrivate, counter.value(Scope.PRIVATE),
                 MSG_PRIVATE_METHODS, ast);
        checkMax(maxPackage, counter.value(Scope.PACKAGE),
                 MSG_PACKAGE_METHODS, ast);
        checkMax(maxProtected, counter.value(Scope.PROTECTED),
                 MSG_PROTECTED_METHODS, ast);
        checkMax(maxPublic, counter.value(Scope.PUBLIC),
                 MSG_PUBLIC_METHODS, ast);
        checkMax(maxTotal, counter.getTotal(), MSG_MANY_METHODS, ast);
    }

    /**
     * Utility for reporting if a maximum has been exceeded.
     * @param max the maximum allowed value
     * @param value the actual value
     * @param msg the message to log. Takes two arguments of value and maximum.
     * @param ast the AST to associate with the message.
     */
    private void checkMax(int max, int value, String msg, DetailAST ast) {
        if (max < value) {
            log(ast.getLineNo(), msg, value, max);
        }
    }

    /**
     * Sets the maximum allowed {@code private} methods per type.
     * @param value the maximum allowed.
     */
    public void setMaxPrivate(int value) {
        maxPrivate = value;
    }

    /**
     * Sets the maximum allowed {@code package} methods per type.
     * @param value the maximum allowed.
     */
    public void setMaxPackage(int value) {
        maxPackage = value;
    }

    /**
     * Sets the maximum allowed {@code protected} methods per type.
     * @param value the maximum allowed.
     */
    public void setMaxProtected(int value) {
        maxProtected = value;
    }

    /**
     * Sets the maximum allowed {@code public} methods per type.
     * @param value the maximum allowed.
     */
    public void setMaxPublic(int value) {
        maxPublic = value;
    }

    /**
     * Sets the maximum total methods per type.
     * @param value the maximum allowed.
     */
    public void setMaxTotal(int value) {
        maxTotal = value;
    }

    /**
     * Marker class used to collect data about the number of methods per
     * class. Objects of this class are used on the Stack to count the
     * methods for each class and layer.
     */
    private static class MethodCounter {

        /** Maintains the counts. */
        private final Map<Scope, Integer> counts = new EnumMap<>(Scope.class);
        /** Indicated is an interface, in which case all methods are public. */
        private final boolean inInterface;
        /**
         * The surrounding scope definition (class, enum, etc.) which the method counts are connect
         * to.
         */
        private final DetailAST scopeDefinition;
        /** Tracks the total. */
        private int total;

        /**
         * Creates an interface.
         * @param scopeDefinition
         *        The surrounding scope definition (class, enum, etc.) which to count all methods
         *        for.
         */
        MethodCounter(DetailAST scopeDefinition) {
            this.scopeDefinition = scopeDefinition;
            inInterface = scopeDefinition.getType() == TokenTypes.INTERFACE_DEF;
        }

        /**
         * Increments to counter by one for the supplied scope.
         * @param scope the scope counter to increment.
         */
        private void increment(Scope scope) {
            total++;
            if (inInterface) {
                counts.put(Scope.PUBLIC, 1 + value(Scope.PUBLIC));
            }
            else {
                counts.put(scope, 1 + value(scope));
            }
        }

        /**
         * Gets the value of a scope counter.
         * @param scope the scope counter to get the value of
         * @return the value of a scope counter
         */
        private int value(Scope scope) {
            Integer value = counts.get(scope);
            if (value == null) {
                value = 0;
            }
            return value;
        }

        private DetailAST getScopeDefinition() {
            return scopeDefinition;
        }

        /**
         * Fetches total number of methods.
         * @return the total number of methods.
         */
        private int getTotal() {
            return total;
        }
    }
}
