////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2004  Oliver Burn
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

import java.util.Properties;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;

/**
 * Resolves external properties from an
 * underlying <code>Properties</code> object.
 *
 * @author lkuehne
 */
public final class PropertiesExpander
    implements PropertyResolver
{
    /** the underlying Properties object. */
    private final Properties mProperties;

    /**
     * Creates a new PropertiesExpander.
     * @param aProperties the underlying properties to use for
     * property resolution.
     * @throws IllegalArgumentException indicates null was passed
     */
    public PropertiesExpander(Properties aProperties)
    {
        if (aProperties == null) {
            throw new IllegalArgumentException("cannot pass null");
        }
        mProperties = aProperties;
    }

    /**
     * Resolves a property through the underlying Properties object.
     * @see PropertyResolver#resolve
     */
    public String resolve(String aPropertyName)
        throws CheckstyleException
    {
        if (!mProperties.containsKey(aPropertyName)) {
            throw new CheckstyleException(
                "Property ${" + aPropertyName + "} has not been set");
        }

        return mProperties.getProperty(aPropertyName);
    }
}
