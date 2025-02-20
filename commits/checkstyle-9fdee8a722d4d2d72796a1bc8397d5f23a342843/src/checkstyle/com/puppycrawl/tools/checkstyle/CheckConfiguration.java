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
package com.puppycrawl.tools.checkstyle;

import com.puppycrawl.tools.checkstyle.api.Check;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * Represents the configuration for a check.
 *
 * @author <a href="mailto:checkstyle@puppycrawl.com">Oliver Burn</a>
 * @version 1.0
 */
class CheckConfiguration
{
    /** the classname for the check */
    private String mClassname;
    /** the tokens the check is interested in */
    private final Set mTokens = new HashSet();
    /** the properties for the check */
    private final Map mProperties = new HashMap();


    /**
     * Set the classname of the check.
     * @param aClassname the classname for the check
     */
    void setClassname(String aClassname)
    {
        mClassname = aClassname;
    }

    /**
     * Adds a set of tokens the check is interested in. The string is a comma
     * separated list of token names.
     * @param aStrRep the string representation of the tokens interested in
     */
    void addTokens(String aStrRep)
    {
        final String trimmed = aStrRep.trim();
        if (trimmed.length() == 0) {
            return;
        }

        final StringTokenizer st = new StringTokenizer(trimmed, ",");
        while (st.hasMoreTokens()) {
            mTokens.add(st.nextToken().trim());
        }
    }

    /**
     * Returns the tokens registered for the check.
     * @return the set of token names
     */
    Set getTokens()
    {
        return mTokens;
    }

    /**
     * Adds a property for the check.
     * @param aName name of the property
     * @param aValue value of the property
     */
    void addProperty(String aName, String aValue)
    {
        mProperties.put(aName, aValue);
    }

    /**
     * Create an instance of the check that is properly initialised.
     *
     * @return the created check
     * @throws ClassNotFoundException if an error occurs
     * @throws InstantiationException if an error occurs
     * @throws IllegalAccessException if an error occurs
     */
    Check createInstance(ClassLoader aLoader)
        throws ClassNotFoundException, InstantiationException,
        IllegalAccessException
    {
        final Class clazz = Class.forName(mClassname, true, aLoader);
        final Check check = (Check) clazz.newInstance();
        // TODO: need to set the properties
        return check;
    }
}
