////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2002  Oliver Burn
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
package com.puppycrawl.tools.checkstyle.checks.indentation;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * Handler for method definitions.
 *
 * @author jrichard
 */
public class MethodDefHandler extends BlockParentHandler
{
    /**
     * Construct an instance of this handler with the given indentation check,
     * abstract syntax tree, and parent handler.
     *
     * @param aIndentCheck   the indentation check
     * @param aAst           the abstract syntax tree
     * @param aParent        the parent handler
     */
    public MethodDefHandler(IndentationCheck aIndentCheck,
        DetailAST aAst, ExpressionHandler aParent)
    {
        super(aIndentCheck, (aAst.getType() == TokenTypes.CTOR_DEF)
            ? "ctor def" : "method def", aAst, aParent);
    }

    /**
     * There is no top level expression for this handler.
     *
     * @return null
     */
    protected DetailAST getToplevelAST()
    {
        // we check this stuff ourselves below
        return null;
    }

    /**
     * Check the indentation of the method name.
     */
    private void checkIdent()
    {
        DetailAST ident = getMainAst().findFirstToken(TokenTypes.IDENT);
        int columnNo = expandedTabsColumnNo(ident);
        if (startsLine(ident) && columnNo != getLevel()) {
            logError(ident, "", columnNo);
        }
    }

    /**
     * Check the indentation of the throws clause.
     */
    private void checkThrows()
    {
        DetailAST throwsAst =
            getMainAst().findFirstToken(TokenTypes.LITERAL_THROWS);
        if (throwsAst == null) {
            return;
        }

        int columnNo = expandedTabsColumnNo(throwsAst);
        int expectedColumnNo =
            getLevel() + getIndentCheck().getBasicOffset();

        if (startsLine(throwsAst)
            && columnNo != expectedColumnNo)
        {
            logError(throwsAst, "throws", columnNo, expectedColumnNo);
        }
    }

    /**
     * Check the indentation of the method type.
     */
    private void checkType()
    {
        DetailAST ident = getMainAst().findFirstToken(TokenTypes.TYPE);
        int columnNo = expandedTabsColumnNo(ident);
        if (startsLine(ident) && columnNo != getLevel()) {
            logError(ident, "return type", columnNo);
        }
    }

    /**
     * Check the indentation of the method parameters.
     */
    private void checkParameters()
    {
        DetailAST params = getMainAst().findFirstToken(TokenTypes.PARAMETERS);
        checkExpressionSubtree(params, getLevel(), false, false);
    }

    /**
     * Check the indentation of the expression we are handling.
     */
    public void checkIndentation()
    {
        checkModifiers();
        checkIdent();
        checkThrows();
        if (getMainAst().getType() != TokenTypes.CTOR_DEF) {
            checkType();
        }
        checkParameters();

        if (getLCurly() == null) {
            // asbtract method def -- no body
            return;
        }
        super.checkIndentation();
    }
}
