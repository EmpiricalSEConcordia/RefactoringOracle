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
package com.puppycrawl.tools.checkstyle;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of the Configuration interface.
 * @author lkuehne
 */
public final class DefaultConfiguration implements Configuration
{
    /** The name of this configuration */
    private final String mName;

    /** the list of child Configurations */
    private final List<Configuration> mChildren =
        Lists.newArrayList();

    /** the map from attribute names to attribute values */
    private final Map<String, String> mAttributeMap = Maps.newHashMap();

    /**
     * Instantiates a DefaultConfiguration.
     * @param aName the name for this DefaultConfiguration.
     */
    public DefaultConfiguration(String aName)
    {
        mName = aName;
    }

    /** {@inheritDoc} */
    public String[] getAttributeNames()
    {
        final Set<String> keySet = mAttributeMap.keySet();
        return keySet.toArray(new String[keySet.size()]);
    }

    /** {@inheritDoc} */
    public String getAttribute(String aName) throws CheckstyleException
    {
        if (!mAttributeMap.containsKey(aName)) {
            // TODO: i18n
            throw new CheckstyleException(
                    "missing key '" + aName + "' in " + getName());
        }
        return mAttributeMap.get(aName);
    }

    /** {@inheritDoc} */
    public Configuration[] getChildren()
    {
        return mChildren.toArray(
            new Configuration[mChildren.size()]);
    }

    /** {@inheritDoc} */
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
     * Removes a child of this configuration.
     * @param aConfiguration the child configuration to remove.
     */
    public void removeChild(final Configuration aConfiguration)
    {
        mChildren.remove(aConfiguration);
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
