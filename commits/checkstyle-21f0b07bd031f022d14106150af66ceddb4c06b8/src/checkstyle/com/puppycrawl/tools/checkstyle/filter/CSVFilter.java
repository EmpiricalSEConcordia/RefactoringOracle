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
package com.puppycrawl.tools.checkstyle.filter;

import java.util.StringTokenizer;

import com.puppycrawl.tools.checkstyle.api.FilterChain;

/**
 * <p>
 * This filter accepts an integer that matches a CSV value, or
 * is in a CSV range. It is neutral on other Objects.
 * </p>
 * @author Rick Giles
 */
public class CSVFilter
    extends FilterChain
{
    /**
     * Contructs a <code>CSVFilter</code> from a CSV, Comma-Separated Values,
     * string. Each value is an integer, or a range of integers. A range of
     * integers is of the form integer-integer, such as 1-10.
     * @param aPattern the CSV string.
     * @throws NumberFormatException if a component substring does not
     * contain a parsable integer.
     */
    public CSVFilter(String aPattern)
        throws NumberFormatException
    {
        final StringTokenizer tokenizer = new StringTokenizer(aPattern, ",");
        while (tokenizer.hasMoreTokens()) {
            final String token = tokenizer.nextToken().trim();
            final int index = token.indexOf("-");
            if (index == -1) {
                final int matchValue = Integer.parseInt(token);
                addFilter(new IntMatchFilter(matchValue));
            }
            else {
                final int lowerBound =
                    Integer.parseInt(token.substring(0, index));
                final int upperBound =
                    Integer.parseInt(token.substring(index + 1));
                addFilter(new IntRangeFilter(lowerBound, upperBound));
            }
        }
    }
}
