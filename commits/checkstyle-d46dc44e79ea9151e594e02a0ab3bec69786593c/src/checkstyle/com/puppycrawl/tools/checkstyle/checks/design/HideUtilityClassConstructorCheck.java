////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2005  Oliver Burn
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

import com.puppycrawl.tools.checkstyle.api.Check;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.api.DetailAST;

/**
 * Make sure that utility classes (classes that contain only static methods)
 * do not have a public constructor.
 * <p>
 * Rationale: Instantiating utility classes does not make sense.
 * A common mistake is forgetting to hide the default constructor.
 * </p>
 *
 * @author lkuehne
 * @version $Revision: 1.9 $
 */
public class HideUtilityClassConstructorCheck extends Check
{
    /** @see Check */
    public int[] getDefaultTokens()
    {
        return new int[] {TokenTypes.CLASS_DEF};
    }

    /** @see Check */
    public void visitToken(DetailAST aAST)
    {
        final DetailAST objBlock = aAST.findFirstToken(TokenTypes.OBJBLOCK);
        DetailAST child = (DetailAST) objBlock.getFirstChild();
        boolean hasMethod = false;
        boolean hasNonStaticMethod = false;
        boolean hasDefaultCtor = true;
        boolean hasPublicCtor = false;

        while (child != null) {
            if (child.getType() == TokenTypes.METHOD_DEF) {
                hasMethod = true;
                final DetailAST modifiers =
                    child.findFirstToken(TokenTypes.MODIFIERS);
                if (!modifiers.branchContains(TokenTypes.LITERAL_STATIC)) {
                    hasNonStaticMethod = true;
                }
            }
            if (child.getType() == TokenTypes.CTOR_DEF) {
                hasDefaultCtor = false;
                final DetailAST modifiers =
                    child.findFirstToken(TokenTypes.MODIFIERS);
                if (!modifiers.branchContains(TokenTypes.LITERAL_PRIVATE)
                    && !modifiers.branchContains(TokenTypes.LITERAL_PROTECTED))
                {
                    // treat package visible as public
                    // for the purpose of this Check
                    hasPublicCtor = true;
                }

            }
            child = (DetailAST) child.getNextSibling();
        }

        final boolean hasAccessibleCtor = (hasDefaultCtor || hasPublicCtor);

        // figure out if class extends java.lang.object directly
        // keep it simple for now and get a 99% solution
        // TODO: check for "extends java.lang.Object" and "extends Object"
        // consider "import org.omg.CORBA.*"
        final boolean extendsJLO = // J.Lo even made it into in our sources :-)
            aAST.findFirstToken(TokenTypes.EXTENDS_CLAUSE) == null;

        if (extendsJLO
                && hasMethod && !hasNonStaticMethod && hasAccessibleCtor)
        {
            log(aAST.getLineNo(), aAST.getColumnNo(), "hide.utility.class");
        }
    }
}
