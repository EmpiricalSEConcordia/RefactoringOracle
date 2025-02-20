////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2008  Oliver Burn
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
package com.puppycrawl.tools.checkstyle.checks.j2ee;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * Root class for bean method checks.
 * @author Rick Giles
 */
public abstract class BeanMethodChecker
    extends MethodChecker
{
    /**
     * Constructs a BeanMethodChecker for a bean check.
     * @param aCheck the bean check.
     */
    public BeanMethodChecker(AbstractBeanCheck aCheck)
    {
        super(aCheck);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkMethod(DetailAST aMethodAST)
    {
        // every kind of a bean has ejbCreate<METHOD>(...) requirements
        final DetailAST nameAST = aMethodAST.findFirstToken(TokenTypes.IDENT);
        final String name = nameAST.getText();
        if (name.startsWith("ejbCreate")) {
            checkCreateMethod(aMethodAST);
        }
    }

    /**
      * Checks whether an ejbCreate&lt;METHOD&gt;(...) method of a
      * bean satisfies requirements.
      * @param aMethodAST the AST for the method definition.
      */
    protected void checkCreateMethod(DetailAST aMethodAST)
    {
        // the method must not be final
        checkMethod(aMethodAST, false);
    }
}
