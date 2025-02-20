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
 * Handler for class definitions.
 *
 * @author jrichard
 */
public class ClassDefHandler extends BlockParentHandler
{
    /**
     * Construct an instance of this handler with the given indentation check,
     * abstract syntax tree, and parent handler.
     *
     * @param aIndentCheck   the indentation check
     * @param aAst           the abstract syntax tree
     * @param aParent        the parent handler
     */
    public ClassDefHandler(IndentationCheck aIndentCheck,
        DetailAST aAst, ExpressionHandler aParent)
    {
        super(aIndentCheck, aAst.getType() == TokenTypes.CLASS_DEF
            ? "class def" : "interface def", aAst, aParent);
    }

    /**
     * Get the left curly brace portion of the expression we are handling.
     *
     * @return the left curly brace expression
     */
    protected DetailAST getLCurly()
    {
        return getMainAst().findFirstToken(TokenTypes.OBJBLOCK)
            .findFirstToken(TokenTypes.LCURLY);
    }

    /**
     * Get the right curly brace portion of the expression we are handling.
     *
     * @return the right curly brace expression
     */
    protected DetailAST getRCurly()
    {
        return getMainAst().findFirstToken(TokenTypes.OBJBLOCK)
            .findFirstToken(TokenTypes.RCURLY);
    }

    /**
     * There is no top level expression for this handler.
     *
     * @return null
     */
    protected DetailAST getToplevelAST()
    {
        return null;
        // note: ident checked by hand in check indentation;
    }

    /**
     * Get the child element representing the list of statements.
     *
     * @return the statement list child
     */
    protected DetailAST getListChild()
    {
        return getMainAst().findFirstToken(TokenTypes.OBJBLOCK);
    }

    /**
     * Check the indentation of the expression we are handling.
     */
    public void checkIndentation()
    {
        // TODO: still need to better deal with the modifiers and "class"
        checkModifiers();

        LineSet lines = new LineSet();

        // checks that line with class name starts at correct indentation,
        //  and following lines (in implements and extends clauses) are
        //  indented at least one level
        DetailAST ident = getMainAst().findFirstToken(TokenTypes.IDENT);
        int lineStart = getLineStart(ident);
        if (lineStart != getLevel()) {
            logError(ident, "ident", lineStart);
        }

        lines.addLineAndCol(new Integer(ident.getLineNo()), lineStart);

        DetailAST impl = getMainAst().findFirstToken(
            TokenTypes.IMPLEMENTS_CLAUSE);
        if (impl != null && impl.getFirstChild() != null) {
            findSubtreeLines(lines, impl, false);
        }

        DetailAST ext = getMainAst().findFirstToken(TokenTypes.EXTENDS_CLAUSE);
        if (ext != null && ext.getFirstChild() != null) {
            findSubtreeLines(lines, ext, false);
        }

        checkLinesIndent(ident.getLineNo(), lines.lastLine(), getLevel());

        super.checkIndentation();
    }
}
