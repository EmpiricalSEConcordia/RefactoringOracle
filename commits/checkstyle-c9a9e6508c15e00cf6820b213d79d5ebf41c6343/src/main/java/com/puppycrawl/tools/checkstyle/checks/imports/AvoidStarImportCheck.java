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
package com.puppycrawl.tools.checkstyle.checks.imports;

import java.util.List;
import com.google.common.collect.Lists;
import com.puppycrawl.tools.checkstyle.api.Check;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * <p>
 * Check that finds import statements that use the * notation.
 * </p>
 * <p>
 * Rationale: Importing all classes from a package or static
 * members from a class leads to tight coupling between packages
 * or classes and might lead to problems when a new version of a
 * library introduces name clashes.
 * </p>
 * <p>
 * An example of how to configure the check is:
 * </p>
 * <pre>
 * &lt;module name="AvoidStarImport"&gt;
 *   &lt;property name="excludes" value="java.io,java.net,java.lang.Math"/&gt;
 *   &lt;property name="allowClassImports" value="false"/&gt;
 *   &lt;property name="allowStaticMemberImports" value="false"/&gt;
 * &lt;/module&gt;
 * </pre>
 *
 * The optional "excludes" property allows for certain packages like
 * java.io or java.net to be exempted from the rule. It also is used to
 * allow certain classes like java.lang.Math or java.io.File to be
 * excluded in order to support static member imports.
 *
 * The optional "allowClassImports" when set to true, will allow starred
 * class imports but will not affect static member imports.
 *
 * The optional "allowStaticMemberImports" when set to true will allow
 * starred static member imports but will not affect class imports.
 *
 * @author Oliver Burn
 * @author <a href="bschneider@vecna.com">Bill Schneider</a>
 * @author Travis Schneeberger
 * @version 2.0
 */
public class AvoidStarImportCheck
    extends Check
{
    /** the packages/classes to exempt from this check. */
    private final List<String> excludes = Lists.newArrayList();

    /** whether to allow all class imports */
    private boolean allowClassImports;

    /** whether to allow all static member imports */
    private boolean allowStaticMemberImports;

    @Override
    public int[] getDefaultTokens()
    {
        return new int[] {TokenTypes.IMPORT, TokenTypes.STATIC_IMPORT};
    }

    @Override
    public int[] getAcceptableTokens()
    {
        return new int[] {TokenTypes.IMPORT, TokenTypes.STATIC_IMPORT};
    }

    /**
     * Sets the list of packages or classes to be exempt from the check.
     * The excludes can contain a .* or not.
     * @param excludesParam a list of package names/fully-qualifies class names
     * where star imports are ok
     */
    public void setExcludes(String[] excludesParam)
    {
        excludes.clear();
        for (final String exclude : excludesParam) {
            excludes.add(exclude.endsWith(".*") ? exclude : exclude + ".*");
        }
    }

    /**
     * Sets whether or not to allow all non-static class imports.
     * @param allow true to allow false to disallow
     */
    public void setAllowClassImports(boolean allow)
    {
        allowClassImports = allow;
    }

    /**
     * Sets whether or not to allow all static member imports.
     * @param allow true to allow false to disallow
     */
    public void setAllowStaticMemberImports(boolean allow)
    {
        allowStaticMemberImports = allow;
    }

    @Override
    public void visitToken(final DetailAST ast)
    {
        if (!allowClassImports && (TokenTypes.IMPORT == ast.getType())) {
            final DetailAST startingDot = ast.getFirstChild();
            logsStarredImportViolation(startingDot);
        }
        else if (!allowStaticMemberImports
            && (TokenTypes.STATIC_IMPORT == ast.getType()))
        {
            // must navigate past the static keyword
            final DetailAST startingDot = ast.getFirstChild().getNextSibling();
            logsStarredImportViolation(startingDot);
        }
    }

    /**
     * Gets the full import identifier.  If the import is a starred import and
     * it's not excluded then a violation is logged.
     * @param startingDot the starting dot for the import statement
     */
    private void logsStarredImportViolation(DetailAST startingDot)
    {
        final FullIdent name = FullIdent.createFullIdent(startingDot);
        if (isStaredImport(name) && !excludes.contains(name.getText())) {
            log(startingDot.getLineNo(), "import.avoidStar", name.getText());
        }
    }

    /**
     * Checks is an import is a stared import.
     * @param importIdent the full import identifier
     * @return true if a start import false if not
     */
    private boolean isStaredImport(FullIdent importIdent)
    {
        return (null != importIdent) && importIdent.getText().endsWith(".*");
    }
}
