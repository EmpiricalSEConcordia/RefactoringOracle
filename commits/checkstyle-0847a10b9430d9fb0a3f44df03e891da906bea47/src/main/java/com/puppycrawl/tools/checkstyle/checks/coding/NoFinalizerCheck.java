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

package com.puppycrawl.tools.checkstyle.checks.coding;

import com.puppycrawl.tools.checkstyle.StatelessCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * Checks that no method having zero parameters is defined
 * using the name <em>finalize</em>.
 *
 * @author fqian@google.com (Feng Qian)
 * @author smckay@google.com (Steve McKay)
 * @author lkuehne
 */
@StatelessCheck
public class NoFinalizerCheck extends AbstractCheck {

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_KEY = "avoid.finalizer.method";

    @Override
    public int[] getDefaultTokens() {
        return getAcceptableTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[] {TokenTypes.METHOD_DEF};
    }

    @Override
    public int[] getRequiredTokens() {
        return getAcceptableTokens();
    }

    @Override
    public void visitToken(DetailAST aAST) {
        final DetailAST mid = aAST.findFirstToken(TokenTypes.IDENT);
        final String name = mid.getText();

        if ("finalize".equals(name)) {

            final DetailAST params = aAST.findFirstToken(TokenTypes.PARAMETERS);
            final boolean hasEmptyParamList =
                params.findFirstToken(TokenTypes.PARAMETER_DEF) == null;

            if (hasEmptyParamList) {
                log(aAST.getLineNo(), MSG_KEY);
            }
        }
    }

}
