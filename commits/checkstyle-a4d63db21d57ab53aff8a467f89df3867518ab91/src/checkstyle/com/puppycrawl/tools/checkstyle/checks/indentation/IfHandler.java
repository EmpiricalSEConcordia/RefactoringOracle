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
import com.puppycrawl.tools.checkstyle.checks.IndentationCheck;

/**
 * Handler for if statements.
 *
 * @author jrichard
 */
public class IfHandler extends BlockParentHandler
{
    /**
     * Construct an instance of this handler with the given indentation check,
     * abstract syntax tree, and parent handler.
     *
     * @param aIndentCheck   the indentation check
     * @param aAst           the abstract syntax tree
     * @param aParent        the parent handler
     */
    public IfHandler(IndentationCheck aIndentCheck,
        DetailAST aAst, ExpressionHandler aParent)
    {
        super(aIndentCheck, "if", aAst, aParent);
    }

    /**
     * Indentation level suggested for a child element. Children don't have
     * to respect this, but most do.
     *
     * @param aChild  child AST (so suggestion level can differ based on child
     *                  type)
     *
     * @return suggested indentation for child
     */
    public int suggestedChildLevel(ExpressionHandler aChild)
    {
        if (aChild instanceof ElseHandler) {
            return getLevel();
        }
        else {
            return super.suggestedChildLevel(aChild);
        }
    }

    /**
     * Compute the indentation amount for this handler.
     *
     * @return the expected indentation amount
     */
    public int getLevelImpl()
    {
        if (isElseIf()) {
            return getParent().getLevel();
        }
        else {
            return super.getLevelImpl();
        }
    }

    /**
     * Determines if this 'if' statement is part of an 'else' clause.
     *
     * @return true if this 'if' is part of an 'else', false otherwise
     */
    private boolean isElseIf()
    {
        // check if there is an 'else' and an 'if' on the same line
        DetailAST parent = getMainAst().getParent();
        return parent.getType() == TokenTypes.LITERAL_ELSE
            && parent.getLineNo() == getMainAst().getLineNo();
    }

    /**
     * Check the indentation of the top level token.
     */
    protected void checkToplevelToken()
    {
        if (isElseIf()) {
            return;
        }

        super.checkToplevelToken();
    }

    /**
     * Check the indentation of the conditional expression.
     */
    private void checkCondExpr()
    {
        DetailAST condAst = (DetailAST)
            getMainAst().findFirstToken(TokenTypes.LPAREN).getNextSibling();
        int expectedLevel =
            getLevel() + getIndentCheck().getIndentationAmount();
        checkExpressionSubtree(condAst, expectedLevel, false, false);
    }

    /**
     * Check the indentation of the expression we are handling.
     */
    public void checkIndentation()
    {
        super.checkIndentation();
        checkCondExpr();
    }
}
