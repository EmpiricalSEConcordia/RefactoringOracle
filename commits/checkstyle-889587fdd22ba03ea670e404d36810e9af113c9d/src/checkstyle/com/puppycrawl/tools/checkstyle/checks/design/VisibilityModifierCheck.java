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
package com.puppycrawl.tools.checkstyle.checks.design;

import java.util.HashSet;
import java.util.Set;

import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.api.Check;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.Utils;
import org.apache.commons.beanutils.ConversionException;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;
import antlr.collections.AST;

/**
 * Checks visibility of class members. Only static final members may be public,
 * other class members must be private unless allowProtected/Package is set.
 * <p>
 * Public members are not flagged if the name matches the public
 * member regular expression (contains "^serialVersionUID$" by
 * default).
 * </p>
 * Rationale: Enforce encapsulation.
 *
 * @author lkuehne
 */
public class VisibilityModifierCheck
    extends Check
{
    /** whether protected members are allowed */
    private boolean mProtectedAllowed = false;

    /** whether package visible members are allowed */
    private boolean mPackageAllowed = false;

    /**
     * pattern for public members that should be ignored.  Note:
     * Earlier versions of checkstyle used ^f[A-Z][a-zA-Z0-9]*$ as the
     * default to allow CMP for EJB 1.1 with the default settings.
     * With EJB 2.0 it is not longer necessary to have public access
     * for persistent fields.
     */
    private String mPublicMemberPattern = "^serialVersionUID$";

    /** regexp for public members that should be ignored */
    private RE mPublicMemberRE = null;

    /** Constructor */
    public VisibilityModifierCheck()
    {
        setPublicMemberPattern(mPublicMemberPattern);
    }

    /** @return whether protected members are allowed */
    public boolean isProtectedAllowed()
    {
        return mProtectedAllowed;
    }

    /**
     * Set whether protected members are allowed.
     * @param aProtectedAllowed whether protected members are allowed
     */
    public void setProtectedAllowed(boolean aProtectedAllowed)
    {
        mProtectedAllowed = aProtectedAllowed;
    }

    /** @return whether package visible members are allowed */
    public boolean isPackageAllowed()
    {
        return mPackageAllowed;
    }

    /**
     * Set whether package visible members are allowed
     * @param aPackageAllowed whether package visible members are allowed
     */
    public void setPackageAllowed(boolean aPackageAllowed)
    {
        mPackageAllowed = aPackageAllowed;
    }

    /**
     * Set the pattern for public members to ignore.
     * @param aPattern pattern for public members to ignore.
     */
    public void setPublicMemberPattern(String aPattern)
    {
        try {
            mPublicMemberRE = Utils.getRE(aPattern);
            mPublicMemberPattern = aPattern;
        }
        catch (RESyntaxException e) {
            throw new ConversionException("unable to parse " + aPattern, e);
        }
    }

    /**
     * @return the regexp for public members to ignore.
     */
    private RE getPublicMemberRegexp()
    {
        return mPublicMemberRE;
    }

    /** @see Check */
    public int[] getDefaultTokens()
    {
        return new int[] {TokenTypes.VARIABLE_DEF};
    }

    /** @see Check */
    public void visitToken(DetailAST aAST)
    {
        if (aAST.getType() != TokenTypes.VARIABLE_DEF
                || aAST.getParent().getType() != TokenTypes.OBJBLOCK)
        {
            return;
        }

        final DetailAST varNameAST = getVarNameAST(aAST);
        String varName = varNameAST.getText();
        boolean inInterfaceBlock = inInterfaceBlock(aAST);
        final Set mods = getModifiers(aAST);
        final String declaredScope = getVisibilityScope(mods);
        final String variableScope =
             inInterfaceBlock ? "public" : declaredScope;

        if (!("private".equals(variableScope)
                || inInterfaceBlock // implicitly static and final
                || mods.contains("static") && mods.contains("final")
                || "package".equals(variableScope) && isPackageAllowed()
                || "protected".equals(variableScope) && isProtectedAllowed()
                || "public".equals(variableScope)
                   && getPublicMemberRegexp().match(varName)))
        {
            log(varNameAST.getLineNo(), varNameAST.getColumnNo(),
                    "variable.notPrivate", varName);
        }
    }

    /**
     * Returns the variable name in a VARIABLE_DEF AST.
     * @param aVariableDefAST an AST where type == VARIABLE_DEF AST.
     * @return the variable name in aVariableDefAST
     */
    private DetailAST getVarNameAST(DetailAST aVariableDefAST)
    {
        AST ast = aVariableDefAST.getFirstChild();
        while (ast != null) {
            AST nextSibling = ast.getNextSibling();
            if (ast.getType() == TokenTypes.TYPE) {
                return (DetailAST) nextSibling;
            }
            ast = nextSibling;
        }
        return null;
    }

    /**
     * Returns whether an AST is in an interface block.
     * @param aAST the AST to check for
     * @return true iff aAST is in an interface def with no class def in between
     */
    private boolean inInterfaceBlock(DetailAST aAST)
    {
        DetailAST ast = aAST.getParent();
        while (ast != null) {
            switch (ast.getType()) {
            case TokenTypes.INTERFACE_DEF:
                return true;
            case TokenTypes.CLASS_DEF:
                return false;
            default:
                ast = ast.getParent();
            }
        }
        return false;
    }

    /**
     * Returns the set of modifier Strings for a VARIABLE_DEF AST.
     * @param aVariableDefAST AST for a vraiable definition
     * @return the set of modifier Strings for variableDefAST
     */
    private Set getModifiers(DetailAST aVariableDefAST)
    {
        final AST modifiersAST = aVariableDefAST.getFirstChild();
        if (modifiersAST.getType() != TokenTypes.MODIFIERS) {
            throw new IllegalStateException("Strange parse tree");
        }
        final Set retVal = new HashSet();
        AST modifier = modifiersAST.getFirstChild();
        while (modifier != null) {
            retVal.add(modifier.getText());
            modifier = modifier.getNextSibling();
        }
        return retVal;

    }

    /**
     * Returns the visibility scope specified with a set of modifiers.
     * @param aModifiers the set of modifier Strings
     * @return one of "public", "private", "protected", "package"
     */
    private String getVisibilityScope(Set aModifiers)
    {
        final String[] explicitModifiers = {"public", "private", "protected"};
        for (int i = 0; i < explicitModifiers.length; i++) {
            String candidate = explicitModifiers[i];
            if (aModifiers.contains(candidate)) {
                return candidate;
            }
        }
        return "package";
    }
}
