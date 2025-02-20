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
package com.puppycrawl.tools.checkstyle;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;

/**
 * Default implementation of the Configuration interface.
 * @author lkuehne
 */
public final class DefaultConfiguration implements Configuration
{
    /** The name of this configuration */
    private final String mName;

    /** the list of child Configurations */
    private final ArrayList mChildren = new ArrayList();

    /** the map from attribute names to attribute values */
    private final Map mAttributeMap = new HashMap();

    /**
     * Instantiates a DefaultConfiguration.
     * @param aName the name for this DefaultConfiguration.
     */
    public DefaultConfiguration(String aName)
    {
        mName = aName;
    }

    /** @see Configuration */
    public String[] getAttributeNames()
    {
        final Set keySet = mAttributeMap.keySet();
        return (String[]) keySet.toArray(new String[keySet.size()]);
    }

    /** @see Configuration */
    public String getAttribute(String aName) throws CheckstyleException
    {
        if (!mAttributeMap.containsKey(aName)) {
            // TODO: i18n
            throw new CheckstyleException(
                    "missing key '" + aName + "' in " + getName());
        }
        return (String) mAttributeMap.get(aName);
    }

    /** @see Configuration */
    public Configuration[] getChildren()
    {
        return (Configuration[]) mChildren.toArray(
            new Configuration[mChildren.size()]);
    }

    /** @see Configuration */
    public String getName()
    {
        return mName;
    }

    /**
     * Makes a configuration a child of this configuration.
     * @param aConfiguration the child configuration.
     */
    public void addChild(Configuration aConfiguration)
    {
        mChildren.add(aConfiguration);
    }

    /**
     * Adds an attribute to this configuration.
     * @param aName the name of the attribute.
     * @param aValue the value of the attribute.
     */
    public void addAttribute(String aName, String aValue)
    {
        mAttributeMap.put(aName, aValue);
    }

}
