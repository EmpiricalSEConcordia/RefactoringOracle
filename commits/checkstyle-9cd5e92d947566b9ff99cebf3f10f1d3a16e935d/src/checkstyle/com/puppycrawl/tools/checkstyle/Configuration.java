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

import com.puppycrawl.tools.checkstyle.api.Utils;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Represents the configuration that checkstyle uses when checking. The
 * configuration is Serializable, however the ClassLoader configuration is
 * lost.
 * @author <a href="mailto:oliver@puppycrawl.com">Oliver Burn</a>
 **/
public class Configuration
    implements Serializable
{
    ////////////////////////////////////////////////////////////////////////////
    // Constants
    ////////////////////////////////////////////////////////////////////////////

    /** pattern defaults **/
    private static final Map PATTERN_DEFAULTS = new HashMap();
    static {
        PATTERN_DEFAULTS.put(Defn.TODO_PATTERN_PROP, "TODO:");
        PATTERN_DEFAULTS.put(Defn.STATIC_PATTERN_PROP, "^[a-z][a-zA-Z0-9]*$");
        PATTERN_DEFAULTS.put(Defn.CONST_PATTERN_PROP, "^[A-Z](_?[A-Z0-9]+)*$");
        PATTERN_DEFAULTS.put(Defn.MEMBER_PATTERN_PROP, "^[a-z][a-zA-Z0-9]*$");
        PATTERN_DEFAULTS.put(Defn.PUBLIC_MEMBER_PATTERN_PROP,
                             "^f[A-Z][a-zA-Z0-9]*$");
        PATTERN_DEFAULTS.put(Defn.LOCAL_VAR_PATTERN_PROP,
                             "^[a-z][a-zA-Z0-9]*$");
        PATTERN_DEFAULTS.put(Defn.LOCAL_FINAL_VAR_PATTERN_PROP,
                             "^[a-z][a-zA-Z0-9]*$");
    }

    ////////////////////////////////////////////////////////////////////////////
    // Member variables
    ////////////////////////////////////////////////////////////////////////////

    /** visibility scope where Javadoc is checked **/
    private Scope mJavadocScope = Scope.PRIVATE;

    /**
     * class loader to resolve classes with. Needs to be transient as unable
     * to persist.
     **/
    private transient ClassLoader mLoader =
        Thread.currentThread().getContextClassLoader();

    /** how to wrap operators **/
    private WrapOpOption mWrapOpOption = WrapOpOption.NL;

    /** set of boolean properties **/
    private final Set mBooleanProps = new HashSet();

    /** map of int properties **/
    private final Map mIntProps = new HashMap();
    {
        mIntProps.put(Defn.MAX_CONSTRUCTOR_LENGTH_PROP, new Integer(150));
        mIntProps.put(Defn.TAB_WIDTH_PROP, new Integer(8));
    }


    /** map of all the pattern properties **/
    private final Map mPatterns = new HashMap();
    /** map of all the corresponding RE objects for pattern properties **/
    private transient Map mRegexps = new HashMap();

    /** map of all the BlockOption properties **/
    private final Map mBlockProps = new HashMap();
    {
        mBlockProps.put(Defn.TRY_BLOCK_PROP, BlockOption.STMT);
        mBlockProps.put(Defn.CATCH_BLOCK_PROP, BlockOption.TEXT);
        mBlockProps.put(Defn.FINALLY_BLOCK_PROP, BlockOption.STMT);
    }

    /** map of all String properties - by default are null **/
    private final Map mStringProps = new HashMap();
    {
        mStringProps.put(Defn.LOCALE_LANGUAGE_PROP,
                         Locale.getDefault().getLanguage());
        mStringProps.put(Defn.LOCALE_COUNTRY_PROP,
                         Locale.getDefault().getCountry());
    }

    ////////////////////////////////////////////////////////////////////////////
    // Constructors
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Creates a new <code>Configuration</code> instance.
     *
     * @param aProps where to extract configuration parameters from
     * @param aLog where to log errors to
     * @throws RESyntaxException if an error occurs
     * @throws FileNotFoundException if an error occurs
     * @throws IOException if an error occurs
     */
    public Configuration(Properties aProps, PrintStream aLog)
        throws RESyntaxException, FileNotFoundException, IOException
    {
        // Init the special properties
        setJavadocScope(
            Scope.getInstance(aProps.getProperty(Defn.JAVADOC_CHECKSCOPE_PROP,
                                                 Scope.PRIVATE.getName())));
        setWrapOpOption(getWrapOpOptionProperty(aProps,
                                                Defn.WRAP_OP_PROP,
                                                WrapOpOption.NL,
                                                aLog));

        // Initialise the general properties
        for (int i = 0; i < Defn.ALL_BOOLEAN_PROPS.length; i++) {
            setBooleanProperty(aProps, Defn.ALL_BOOLEAN_PROPS[i]);
        }

        for (int i = 0; i < Defn.ALL_PATTERN_PROPS.length; i++) {
            setPatternProperty(aProps, Defn.ALL_PATTERN_PROPS[i]);
        }

        for (int i = 0; i < Defn.ALL_INT_PROPS.length; i++) {
            setIntProperty(aProps, aLog, Defn.ALL_INT_PROPS[i]);
        }

        for (int i = 0; i < Defn.ALL_BLOCK_PROPS.length; i++) {
            setBlockOptionProperty(aProps, Defn.ALL_BLOCK_PROPS[i], aLog);
        }

        for (int i = 0; i < Defn.ALL_STRING_PROPS.length; i++) {
            setStringProperty(aProps, Defn.ALL_STRING_PROPS[i]);
        }
    }

    /**
     * Creates a new <code>Configuration</code> instance.
     * @throws IllegalStateException if an error occurs
     */
    public Configuration()
    {
        try {
            for (int i = 0; i < Defn.ALL_PATTERN_PROPS.length; i++) {
                setPatternProperty(
                    Defn.ALL_PATTERN_PROPS[i],
                    (String) PATTERN_DEFAULTS.get(Defn.ALL_PATTERN_PROPS[i]));
            }
        }
        catch (RESyntaxException ex) {
            ex.printStackTrace();
            throw new IllegalStateException(ex.getMessage());
        }
    }

    /**
     * Extend default deserialization to initialize the RE member variables.
     *
     * @param aStream the ObjectInputStream that contains the serialized data
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if the class of a serialized object
     *     could not be found
     */
    private void readObject(ObjectInputStream aStream)
        throws IOException, ClassNotFoundException
    {
        // initialize the serialized fields
        aStream.defaultReadObject();

        // initialize the transient fields
        mLoader = Thread.currentThread().getContextClassLoader();
        mRegexps = new HashMap();

        try {
            // Loop on the patterns creating the RE's
            final Iterator keys = mPatterns.keySet().iterator();
            while (keys.hasNext()) {
                final String k = (String) keys.next();
                mRegexps.put(k, new RE((String) mPatterns.get(k)));
            }
        }
        catch (RESyntaxException ex) {
            // This should never happen, as the serialized regexp patterns
            // somehow must have passed a setPattern() method.
            throw new InvalidObjectException(
                "invalid regular expression syntax");
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    // Setters
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Set the class loader for locating classes.
     * @param aLoader the class loader
     */
    public void setClassLoader(ClassLoader aLoader)
    {
        mLoader = aLoader;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Getters
    ////////////////////////////////////////////////////////////////////////////

    /** @return a Properties object representing the current configuration.
     * The returned object can be used to recreate the configuration.
     * Tip: used on a default object returns all the default objects. */
    public Properties getProperties()
    {
        final Properties retVal = new Properties();

        Utils.addObjectString(retVal, Defn.JAVADOC_CHECKSCOPE_PROP,
                              mJavadocScope.getName());
        Utils.addObjectString(retVal, Defn.WRAP_OP_PROP,
                              mWrapOpOption.toString());

        for (int i = 0; i < Defn.ALL_BOOLEAN_PROPS.length; i++) {
            final String key = Defn.ALL_BOOLEAN_PROPS[i];
            retVal.put(key, String.valueOf(getBooleanProperty(key)));
        }

        for (int i = 0; i < Defn.ALL_PATTERN_PROPS.length; i++) {
            final String key = Defn.ALL_PATTERN_PROPS[i];
            Utils.addObjectString(retVal, key, getPatternProperty(key));
        }

        for (int i = 0; i < Defn.ALL_INT_PROPS.length; i++) {
            final String key = Defn.ALL_INT_PROPS[i];
            Utils.addObjectString(retVal, key,
                                  Integer.toString(getIntProperty(key)));
        }

        for (int i = 0; i < Defn.ALL_BLOCK_PROPS.length; i++) {
            final String key = Defn.ALL_BLOCK_PROPS[i];
            Utils.addObjectString(retVal, key, getBlockOptionProperty(key));
        }

        for (int i = 0; i < Defn.ALL_STRING_PROPS.length; i++) {
            final String key = Defn.ALL_STRING_PROPS[i];
            Utils.addObjectString(retVal, key, getStringProperty(key));
        }

        return retVal;
    }

    /** @return the class loader **/
    ClassLoader getClassLoader()
    {
        return mLoader;
    }

    /** @return locale language to report messages  **/
    String getLocaleLanguage()
    {
        return getStringProperty(Defn.LOCALE_LANGUAGE_PROP);
    }

    /** @return locale country to report messages  **/
    String getLocaleCountry()
    {
        return getStringProperty(Defn.LOCALE_COUNTRY_PROP);
    }

    /** @return pattern to match to-do lines **/
    String getTodoPat()
    {
        return getPatternProperty(Defn.TODO_PATTERN_PROP);
    }

    /** @return regexp to match to-do lines **/
    RE getTodoRegexp()
    {
        return getRegexpProperty(Defn.TODO_PATTERN_PROP);
    }

    /** @return pattern to match static variables **/
    String getStaticPat()
    {
        return getPatternProperty(Defn.STATIC_PATTERN_PROP);
    }

    /** @return regexp to match static variables **/
    RE getStaticRegexp()
    {
        return getRegexpProperty(Defn.STATIC_PATTERN_PROP);
    }

    /** @return pattern to match static final variables **/
    String getStaticFinalPat()
    {
        return getPatternProperty(Defn.CONST_PATTERN_PROP);
    }

    /** @return regexp to match static final variables **/
    RE getStaticFinalRegexp()
    {
        return getRegexpProperty(Defn.CONST_PATTERN_PROP);
    }

    /** @return pattern to match member variables **/
    String getMemberPat()
    {
        return getPatternProperty(Defn.MEMBER_PATTERN_PROP);
    }

    /** @return regexp to match member variables **/
    RE getMemberRegexp()
    {
        return getRegexpProperty(Defn.MEMBER_PATTERN_PROP);
    }

    /** @return pattern to match public member variables **/
    String getPublicMemberPat()
    {
        return getPatternProperty(Defn.PUBLIC_MEMBER_PATTERN_PROP);
    }

    /** @return regexp to match public member variables **/
    RE getPublicMemberRegexp()
    {
        return getRegexpProperty(Defn.PUBLIC_MEMBER_PATTERN_PROP);
    }

    /** @return pattern to match local variables **/
    String getLocalVarPat()
    {
        return getPatternProperty(Defn.LOCAL_VAR_PATTERN_PROP);
    }

    /** @return regexp to match local variables **/
    RE getLocalVarRegexp()
    {
        return getRegexpProperty(Defn.LOCAL_VAR_PATTERN_PROP);
    }

    /** @return pattern to match local final variables **/
    String getLocalFinalVarPat()
    {
        return getPatternProperty(Defn.LOCAL_FINAL_VAR_PATTERN_PROP);
    }

    /** @return regexp to match local final variables **/
    RE getLocalFinalVarRegexp()
    {
        return getRegexpProperty(Defn.LOCAL_FINAL_VAR_PATTERN_PROP);
    }

    /** @return the maximum constructor length **/
    int getMaxConstructorLength()
    {
        return getIntProperty(Defn.MAX_CONSTRUCTOR_LENGTH_PROP);
    }

    /** @return distance between tab stops */
    int getTabWidth()
    {
        return getIntProperty(Defn.TAB_WIDTH_PROP);
    }

    /** @return whether to allow protected data **/
    boolean isAllowProtected()
    {
        return getBooleanProperty(Defn.ALLOW_PROTECTED_PROP);
    }

    /** @return whether to allow package data **/
    boolean isAllowPackage()
    {
        return getBooleanProperty(Defn.ALLOW_PACKAGE_PROP);
    }

    /** @return whether to allow having no author tag **/
    boolean isAllowNoAuthor()
    {
        return getBooleanProperty(Defn.ALLOW_NO_AUTHOR_PROP);
    }

    /** @return whether to require having version tag */
    boolean isRequireVersion()
    {
        return getBooleanProperty(Defn.REQUIRE_VERSION_PROP);
    }

    /** @return visibility scope where Javadoc is checked **/
    Scope getJavadocScope()
    {
        return mJavadocScope;
    }

    /** @return whether javadoc package documentation is required */
    boolean isRequirePackageHtml()
    {
        return getBooleanProperty(Defn.REQUIRE_PACKAGE_HTML_PROP);
    }

    /** @return whether to check unused @throws **/
    boolean isCheckUnusedThrows()
    {
        return getBooleanProperty(Defn.JAVADOC_CHECK_UNUSED_THROWS_PROP);
    }

    /** @return whether to ignore checks for whitespace **/
    boolean isIgnoreWhitespace()
    {
        return getBooleanProperty(Defn.IGNORE_WHITESPACE_PROP);
    }

    /** @return whether to ignore checks for whitespace after casts **/
    boolean isIgnoreCastWhitespace()
    {
        return getBooleanProperty(Defn.IGNORE_CAST_WHITESPACE_PROP);
    }

    /** @return whether to ignore checks for braces **/
    boolean isIgnoreBraces()
    {
        return getBooleanProperty(Defn.IGNORE_BRACES_PROP);
    }

    /** @return the File of the cache file **/
    String getCacheFile()
    {
        return getStringProperty(Defn.CACHE_FILE_PROP);
    }

    /**
     * @param aJavadocScope visibility scope where Javadoc is checked
     */
    private void setJavadocScope(Scope aJavadocScope)
    {
        mJavadocScope = aJavadocScope;
    }

    /**
     * Set the boolean property.
     * @param aName name of the property. Should be defined in Defn.
     * @param aTo the value to set
     */
    private void setBooleanProperty(String aName, boolean aTo)
    {
        if (aTo) {
            mBooleanProps.add(aName);
        }
        else {
            mBooleanProps.remove(aName);
        }
    }

    /**
     * Set the String property.
     * @param aName name of the property. Should be defined in Defn.
     * @param aTo the value to set
     */
    private void setStringProperty(String aName, String aTo)
    {
        mStringProps.put(aName, aTo);
    }

    /** @return the try block option **/
    BlockOption getTryBlock()
    {
        return getBlockOptionProperty(Defn.TRY_BLOCK_PROP);
    }

    /** @return the catch block option **/
    BlockOption getCatchBlock()
    {
        return getBlockOptionProperty(Defn.CATCH_BLOCK_PROP);
    }

    /** @return the finally block option **/
    BlockOption getFinallyBlock()
    {
        return getBlockOptionProperty(Defn.FINALLY_BLOCK_PROP);
    }

    /** @return the wrapping on operator option **/
    WrapOpOption getWrapOpOption()
    {
        return mWrapOpOption;
    }

    /** @param aTo set the wrap on operator option **/
    private void setWrapOpOption(WrapOpOption aTo)
    {
        mWrapOpOption = aTo;
    }

    /** @return the base directory **/
    String getBasedir()
    {
        return getStringProperty(Defn.BASEDIR_PROP);
    }

    /**
     * Set an integer property.
     * @param aName name of the property to set
     * @param aTo the value to set
     */
    private void setIntProperty(String aName, int aTo)
    {
        mIntProps.put(aName, new Integer(aTo));
    }

    /**
     * Set an pattern property.
     * @param aName name of the property to set
     * @param aPat the value to set
     * @throws RESyntaxException if an error occurs
     */
    private void setPatternProperty(String aName, String aPat)
        throws RESyntaxException
    {
        // Set the regexp first, incase cannot create the RE
        mRegexps.put(aName, new RE(aPat));
        mPatterns.put(aName, aPat);
    }

    /**
     * Set an BlockOption property.
     * @param aName name of the property to set
     * @param aTo the value to set
     */
    private void setBlockOptionProperty(String aName, BlockOption aTo)
    {
        mBlockProps.put(aName, aTo);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////////////////////////////////////

    /**
     * @return the BlockOption for a specified property.
     * @param aName name of the property to get
     */
    private BlockOption getBlockOptionProperty(String aName)
    {
        return (BlockOption) mBlockProps.get(aName);
    }

    /**
     * Set the value of an pattern property. If the property is not defined
     *    then a default value is used.
     * @param aProps the properties set to use
     * @param aName the name of the property to parse
     * @throws RESyntaxException if an error occurs
     */
    private void setPatternProperty(Properties aProps, String aName)
        throws RESyntaxException
    {
        setPatternProperty(
            aName,
            aProps.getProperty(aName, (String) PATTERN_DEFAULTS.get(aName)));
    }

    /**
     * @return the pattern for specified property
     * @param aName the name of the property
     */
    private String getPatternProperty(String aName)
    {
        return (String) mPatterns.get(aName);
    }

    /**
     * @return the regexp for specified property
     * @param aName the name of the property
     */
    private RE getRegexpProperty(String aName)
    {
        return (RE) mRegexps.get(aName);
    }

    /**
     * @return an integer property
     * @param aName the name of the integer property to get
     */
    private int getIntProperty(String aName)
    {
        return ((Integer) mIntProps.get(aName)).intValue();
    }

    /**
     * @return an String property
     * @param aName the name of the String property to get
     */
    private String getStringProperty(String aName)
    {
        return (String) mStringProps.get(aName);
    }
    /**
     * Set the value of an integer property. If the property is not defined
     *    or cannot be parsed, then a default value is used.
     * @param aProps the properties set to use
     * @param aLog where to log errors to
     * @param aName the name of the property to parse
     */
    private void setIntProperty(Properties aProps,
                                PrintStream aLog,
                                String aName)
    {
        final String strRep = aProps.getProperty(aName);
        if (strRep != null) {
            try {
                final int val = Integer.parseInt(strRep);
                setIntProperty(aName, val);
            }
            catch (NumberFormatException nfe) {
                aLog.println(
                    "Unable to parse "
                        + aName
                        + " property with value "
                        + strRep
                        + ", defaulting to "
                        + getIntProperty(aName)
                        + ".");
            }
        }
    }

    /**
     * Set the value of a BlockOption property.
     * @param aProps the properties set to use
     * @param aLog where to log errors to
     * @param aName the name of the property to parse
     */
    private void setBlockOptionProperty(Properties aProps,
                                        String aName,
                                        PrintStream aLog)
    {
        final String strRep = aProps.getProperty(aName);
        if (strRep != null) {
            final BlockOption opt = BlockOption.decode(strRep);
            if (opt == null) {
                aLog.println("Unable to parse " + aName
                             + " property with value " + strRep
                             + ", leaving as " + getBlockOptionProperty(aName)
                             + ".");
            }
            else {
                setBlockOptionProperty(aName, opt);
            }
        }
    }

    /**
     * @param aProps the properties set to use
     * @param aLog where to log errors to
     * @param aName the name of the property to parse
     * @param aDefault the default value to use.
     *
     * @return the value of a WrapOpOption property. If the property is not
     *    defined or cannot be decoded, then a default value is returned.
     */
    private static WrapOpOption getWrapOpOptionProperty(
        Properties aProps,
        String aName,
        WrapOpOption aDefault,
        PrintStream aLog)
    {
        WrapOpOption retVal = aDefault;
        final String strRep = aProps.getProperty(aName);
        if (strRep != null) {
            retVal = WrapOpOption.decode(strRep);
            if (retVal == null) {
                aLog.println("Unable to parse " + aName
                             + " property with value " + strRep
                             + ", defaulting to " + aDefault + ".");
            }
        }
        return retVal;
    }

    /**
     * @param aName name of the boolean property
     * @return return whether a specified property is set
     */
    private boolean getBooleanProperty(String aName)
    {
        return mBooleanProps.contains(aName);
    }

    /**
     * Set a boolean property from a property set.
     * @param aProps the properties set to extract property from
     * @param aName name of the property to extract
     */
    private void setBooleanProperty(Properties aProps, String aName)
    {
        String strRep = aProps.getProperty(aName);
        if (strRep != null) {
            strRep = strRep.toLowerCase().trim();
            if (strRep.equals("true") || strRep.equals("yes")
                || strRep.equals("on"))
            {
                setBooleanProperty(aName, true);
            }
        }
    }

    /**
     * Set a string property from a property set.
     * @param aProps the properties set to extract property from
     * @param aName name of the property to extract
     */
    private void setStringProperty(Properties aProps, String aName)
    {
        final String str = aProps.getProperty(aName);
        if (str != null) {
            setStringProperty(aName, aProps.getProperty(aName));
        }
    }
}
