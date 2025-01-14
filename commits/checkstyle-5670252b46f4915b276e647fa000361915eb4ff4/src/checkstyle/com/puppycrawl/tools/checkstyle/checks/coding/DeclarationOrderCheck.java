////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2004  Oliver Burn
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
package com.puppycrawl.tools.checkstyle.checks.coding;

import com.puppycrawl.tools.checkstyle.api.Check;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.Scope;
import com.puppycrawl.tools.checkstyle.api.ScopeUtils;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Stack;

/**
 * <p>
 * According to
 * <a
 * href="http://java.sun.com/docs/codeconv/html/CodeConventions.doc2.html#1852">
 * Code Conventions for the Java Programming Language</a>
 * , the parts of a class or interface declaration should appear in the
 * following order
 *
 * <li> Class (static) variables. First the public class variables, then
 *      the protected, then package level (no access modifier), and then
 *      the private. </li>
 * <li> Instance variables. First the public class variables, then
 *      the protected, then package level (no access modifier), and then
 *      the private. </li>
 * <li> Constructors </li>
 * <li> Methods </li>
 * </p>
 * <p>
 * An example of how to configure the check is:
 * </p>
 * <pre>
 * &lt;module name="DeclarationOrder"/&gt;
 * </pre>
 *
 * @author r_auckenthaler
 */
public class DeclarationOrderCheck extends Check
{
    /** State for the VARIABLE_DEF */
    private static final int STATE_STATIC_VARIABLE_DEF = 1;

    /** State for the VARIABLE_DEF */
    private static final int STATE_INSTANCE_VARIABLE_DEF = 2;

    /** State for the CTOR_DEF */
    private static final int STATE_CTOR_DEF = 3;

    /** State for the METHOD_DEF */
    private static final int STATE_METHOD_DEF = 4;

    /**
     * List of Declaration States. This is necessary due to
     * inner classes that have their own state
     */
    private Stack mScopeStates = new Stack();

    /**
     * private class to encapsulate the state
     */
    private class ScopeState
    {
        /** The state the check is in */
        private int mScopeState = STATE_STATIC_VARIABLE_DEF;

        /** The sub-state the check is in */
        private Scope mDeclarationAccess = Scope.PUBLIC;
    }

    /** @see Check#getDefaultTokens() */
    public int[] getDefaultTokens()
    {
        return new int[] {
            TokenTypes.CTOR_DEF,
            TokenTypes.METHOD_DEF,
            TokenTypes.MODIFIERS,
            TokenTypes.OBJBLOCK,
        };
    }

    /** @see Check#visitToken(DetailAST) */
    public void visitToken(DetailAST aAST)
    {
        final int parentType = aAST.getParent().getType();
        ScopeState state;

        switch(aAST.getType()) {
        case TokenTypes.OBJBLOCK:
            mScopeStates.push(new ScopeState());
            break;

        case TokenTypes.CTOR_DEF:
            if (parentType != TokenTypes.OBJBLOCK) {
                return;
            }

            state = (ScopeState) mScopeStates.peek();
            if (state.mScopeState > STATE_CTOR_DEF) {
                log(aAST, "declaration.order.constructor");
            }
            else {
                state.mScopeState = STATE_CTOR_DEF;
            }
            break;

        case TokenTypes.METHOD_DEF:
            state = (ScopeState) mScopeStates.peek();
            if (parentType != TokenTypes.OBJBLOCK) {
                return;
            }

            if (state.mScopeState > STATE_METHOD_DEF) {
                log(aAST, "declaration.order.method");
            }
            else {
                state.mScopeState = STATE_METHOD_DEF;
            }
            break;

        case TokenTypes.MODIFIERS:
            if ((parentType != TokenTypes.VARIABLE_DEF)
                || (aAST.getParent().getParent().getType()
                    != TokenTypes.OBJBLOCK))
            {
                return;
            }

            state = (ScopeState) mScopeStates.peek();
            if (aAST.findFirstToken(TokenTypes.LITERAL_STATIC) != null) {
                if (state.mScopeState > STATE_STATIC_VARIABLE_DEF) {
                    log(aAST, "declaration.order.static");
                }
                else {
                    state.mScopeState = STATE_STATIC_VARIABLE_DEF;
                }
            }
            else {
                if (state.mScopeState > STATE_INSTANCE_VARIABLE_DEF) {
                    log(aAST, "declaration.order.instance");
                }
                else if (state.mScopeState == STATE_STATIC_VARIABLE_DEF) {
                    state.mDeclarationAccess = Scope.PUBLIC;
                    state.mScopeState = STATE_INSTANCE_VARIABLE_DEF;
                }
            }

            final Scope access = ScopeUtils.getScopeFromMods(aAST);
            if (state.mDeclarationAccess.compareTo(access) > 0) {
                log(aAST, "declaration.order.access");
            }
            else {
                state.mDeclarationAccess = access;
            }
            break;

        default:
        }
    }

    /** @see Check#leaveToken(DetailAST) */
    public void leaveToken(DetailAST aAST)
    {
        switch(aAST.getType()) {
        case TokenTypes.OBJBLOCK:
            mScopeStates.pop();
            break;

        default:
        }
    }
}
