////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2003  Oliver Burn
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
package com.puppycrawl.tools.checkstyle.checks;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * Contains utility methods for the checks.
 *
 * @author Oliver Burn
 * @author <a href="mailto:simon@redhillconsulting.com.au">Simon Harris</a>
 * @author o_sukhodolsky
 */
public final class CheckUtils
{
    /** prevent instances */
    private CheckUtils()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Tests whether a method definition AST defines an equals covariant.
     * @param aAST the method definition AST to test.
     * Precondition: aAST is a TokenTypes.METHOD_DEF node.
     * @return true if aAST defines an equals covariant.
     */
    public static boolean isEqualsMethod(DetailAST aAST)
    {
        if (aAST.getType() != TokenTypes.METHOD_DEF) {
            throw new IllegalArgumentException("A node must be method def");
        }

        // non-static, non-abstract?
        final DetailAST modifiers = aAST.findFirstToken(TokenTypes.MODIFIERS);
        if (modifiers.branchContains(TokenTypes.LITERAL_STATIC)
            || modifiers.branchContains(TokenTypes.ABSTRACT))
        {
            return false;
        }

        // named "equals"?
        final DetailAST nameNode = aAST.findFirstToken(TokenTypes.IDENT);
        final String name = nameNode.getText();
        if (!"equals".equals(name)) {
            return false;
        }

        // one parameter?
        final DetailAST paramsNode = aAST.findFirstToken(TokenTypes.PARAMETERS);
        return (paramsNode.getChildCount() == 1);
    }

//    public static boolean isFinal(DetailAST detailAST) {
//        DetailAST modifiersAST =
    //detailAST.findFirstToken(TokenTypes.MODIFIERS);
//
//        return modifiersAST.findFirstToken(TokenTypes.FINAL) != null;
//    }
//
//    public static boolean isInObjBlock(DetailAST detailAST) {
//        return detailAST.getParent().getType() == TokenTypes.OBJBLOCK;
//    }
//
//    public static String getIdentText(DetailAST detailAST) {
//        return detailAST.findFirstToken(TokenTypes.IDENT).getText();
//    }

    /**
     * Returns whether a token represents an ELSE as part of an ELSE / IF set.
     * @param aAST the token to check
     * @return whether it is
     */
    public static boolean isElseIf(DetailAST aAST)
    {
        final DetailAST parentAST = aAST.getParent();

        return (aAST.getType() == TokenTypes.LITERAL_IF)
            && (isElse(parentAST) || isElseWithCurlyBraces(parentAST));
    }

    /**
     * Returns whether a token represents an ELSE.
     * @param aAST the token to check
     * @return whether the token represents an ELSE
     */
    private static boolean isElse(DetailAST aAST)
    {
        return aAST.getType() == TokenTypes.LITERAL_ELSE;
    }

    /**
     * Returns whether a token represents an SLIST as part of an ELSE
     * statement.
     * @param aAST the token to check
     * @return whether the toke does represent an SLIST as part of an ELSE
     */
    private static boolean isElseWithCurlyBraces(DetailAST aAST)
    {
        return (aAST.getType() == TokenTypes.SLIST)
            && (aAST.getChildCount() == 2)
            && isElse(aAST.getParent());
    }

    /**
     * Creates <code>FullIdent</code> for given type node.
     * @param aTypeAST a type node.
     * @return <code>FullIdent</code> for given type.
     */
    public static FullIdent createFullType(DetailAST aTypeAST)
    {
        DetailAST arrayDeclAST =
            aTypeAST.findFirstToken(TokenTypes.ARRAY_DECLARATOR);

        return createFullTypeNoArrays(arrayDeclAST == null ? aTypeAST
                                                           : arrayDeclAST);
    }

    /**
     * @param aTypeAST a type node (no array)
     * @return <code>FullIdent</code> for given type.
     */
    private static FullIdent createFullTypeNoArrays(DetailAST aTypeAST)
    {
        return FullIdent.createFullIdent((DetailAST) aTypeAST.getFirstChild());
    }

    // constants for parseFloat()
    /** octal radix */
    private static final int BASE_8 = 8;

    /** decimal radix */
    private static final int BASE_10 = 10;

    /** hex radix */
    private static final int BASE_16 = 16;

    /**
     * Returns the value represented by the specified string of the specified
     * type. Returns 0 for types other than float, double, int, and long.
     * @param aText the string to be parsed.
     * @param aType the token type of the text. Should be a constant of
     * {@link com.puppycrawl.tools.checkstyle.api.TokenTypes}.
     * @return the float value represented by the string argument.
     */
    public static float parseFloat(String aText, int aType)
    {
        float result = 0;
        switch (aType) {
        case TokenTypes.NUM_FLOAT:
        case TokenTypes.NUM_DOUBLE:
            result = (float) Double.parseDouble(aText);
            break;
        case TokenTypes.NUM_INT:
        case TokenTypes.NUM_LONG:
            int radix = BASE_10;
            if (aText.startsWith("0x") || aText.startsWith("0X")) {
                radix = BASE_16;
                aText = aText.substring(2);
            }
            else if (aText.charAt(0) == '0') {
                radix = BASE_8;
                aText = aText.substring(1);
            }
            // Long.parseLong requires that the text ends with neither 'L'
            // nor 'l'.
            if ((aText.endsWith("L")) || (aText.endsWith("l"))) {
                aText = aText.substring(0, aText.length() - 1);
            }
            if (aText.length() > 0) {
                result = (float) Long.parseLong(aText, radix);
            }
            break;
        default:
            break;
        }
        return result;
    }


    /**
     * Finds sub-node for given node minimal (line, column) pair.
     * @param aNode the root of tree for search.
     * @return sub-node with minimal (line, column) pair.
     */
    public static DetailAST getFirstNode(final DetailAST aNode)
    {
        DetailAST currentNode = aNode;
        DetailAST child = (DetailAST) aNode.getFirstChild();
        while (child != null) {
            final DetailAST newNode = getFirstNode(child);
            if (newNode.getLineNo() < currentNode.getLineNo()
                || (newNode.getLineNo() == currentNode.getLineNo()
                    && newNode.getColumnNo() < currentNode.getColumnNo()))
            {
                currentNode = newNode;
            }
            child = (DetailAST) child.getNextSibling();
        }

        return currentNode;
    }
}
