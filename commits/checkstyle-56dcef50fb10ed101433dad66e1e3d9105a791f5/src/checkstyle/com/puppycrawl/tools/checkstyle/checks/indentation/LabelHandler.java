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
 * Handler for labels.
 *
 * @author jrichard
 */
public class LabelHandler extends ExpressionHandler
{
    /**
     * The types of expressions that are children of a label.
     */
    private final int[] mLabelChildren = new int[] {
        TokenTypes.IDENT,
    };

    /**
     * Construct an instance of this handler with the given indentation check,
     * abstract syntax tree, and parent handler.
     *
     * @param aIndentCheck   the indentation check
     * @param aExpr          the abstract syntax tree
     * @param aParent        the parent handler
     */
    public LabelHandler(IndentationCheck aIndentCheck,
        DetailAST aExpr, ExpressionHandler aParent)
    {
        super(aIndentCheck, "label", aExpr, aParent);
    }

    /**
     * Compute the indentation amount for this handler.
     *
     * @return the expected indentation amount
     */
    public int getLevelImpl()
    {
        return super.getLevelImpl()
            - getIndentCheck().getBasicOffset();
    }

    /**
     * Check the indentation of the label.
     */
    private void checkLabel()
    {
        checkChildren(getMainAst(), mLabelChildren,
                      new IndentLevel(getLevel()), true, false);
    }

    /**
     * Check the indentation of the expression we are handling.
     */
    public void checkIndentation()
    {
        checkLabel();
        // need to check children (like 'block' parents do)
        DetailAST parent = (DetailAST)
            getMainAst().getFirstChild().getNextSibling();
        checkExpressionSubtree(parent,
                               getLevel() + getIndentCheck().getBasicOffset(),
                               true,
                               false);
    }
}
