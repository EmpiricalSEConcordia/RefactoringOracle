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
package com.puppycrawl.tools.checkstyle.api;

/**
 * Serves as an abstract base class for all modules that report inspection
 * findings. Such modules have a Severity level which is used for the
 * {@link LocalizedMessage localized messages} that are created by the module.
 *
 * @author lkuehne
 */
public abstract class AbstractViolationReporter
    extends AutomaticBean
{
    /** resuable constant for message formating */
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    /** the severity level of any violations found */
    private SeverityLevel mSeverityLevel = SeverityLevel.ERROR;

    /** the identifier of the reporter */
    private String mId;

    /**
     * Returns the severity level of the messages generated by this module.
     * @return the severity level
     * @see SeverityLevel
     * @see LocalizedMessage#getSeverityLevel
     */
    public final SeverityLevel getSeverityLevel()
    {
        return mSeverityLevel;
    }

    /**
     * Sets the severity level.  The string should be one of the names
     * defined in the <code>SeverityLevel</code> class.
     *
     * @param aSeverity  The new severity level
     * @see SeverityLevel
     */
    public final void setSeverity(String aSeverity)
    {
        mSeverityLevel = SeverityLevel.getInstance(aSeverity);
    }

    /**
     *  Get the severity level's name.
     *
     *  @return  the check's severity level name.
     */
    public final String getSeverity()
    {
        return mSeverityLevel.getName();
    }

    /**
     * Returns the identifier of the reporter. Can be null.
     * @return the id
     */
    public final String getId()
    {
        return mId;
    }

    /**
     * Sets the identifer of the reporter. Can be null.
     * @param aId the id
     */
    public final void setId(final String aId)
    {
        mId = aId;
    }

    /**
     * Log a message.
     *
     * @param aLine the line number where the error was found
     * @param aKey the message that describes the error
     */
    protected final void log(int aLine, String aKey)
    {
        log(aLine, aKey, EMPTY_OBJECT_ARRAY);
    }

    /**
     * Helper method to log a LocalizedMessage. Column defaults to 0.
     *
     * @param aLineNo line number to associate with the message
     * @param aKey key to locale message format
     * @param aArg0 first argument
     */
    protected final void log(int aLineNo, String aKey, Object aArg0)
    {
        log(aLineNo, aKey, new Object[] {aArg0});
    }

    /**
     * Helper method to log a LocalizedMessage. Column defaults to 0.
     *
     * @param aLineNo line number to associate with the message
     * @param aKey key to locale message format
     * @param aArg0 first argument
     * @param aArg1 second argument
     */
    protected final void log(int aLineNo, String aKey,
                             Object aArg0, Object aArg1)
    {
        log(aLineNo, aKey, new Object[] {aArg0, aArg1});
    }

    /**
     * Helper method to log a LocalizedMessage.
     *
     * @param aLineNo line number to associate with the message
     * @param aColNo column number to associate with the message
     * @param aKey key to locale message format
     */
    protected final void log(int aLineNo, int aColNo, String aKey)
    {
        log(aLineNo, aColNo, aKey, EMPTY_OBJECT_ARRAY);
    }

    /**
     * Helper method to log a LocalizedMessage.
     *
     * @param aAST a node to get line and column numbers associated
     *             with the message
     * @param aKey key to locale message format
     */
    protected final void log(DetailAST aAST, String aKey)
    {
        log(aAST.getLineNo(), aAST.getColumnNo(), aKey);
    }

    /**
     * Helper method to log a LocalizedMessage.
     *
     * @param aLineNo line number to associate with the message
     * @param aColNo column number to associate with the message
     * @param aKey key to locale message format
     * @param aArg0 an <code>Object</code> value
     */
    protected final void log(int aLineNo, int aColNo, String aKey,
                    Object aArg0)
    {
        log(aLineNo, aColNo, aKey, new Object[] {aArg0});
    }

    /**
     * Helper method to log a LocalizedMessage.
     *
     * @param aAST a node to get line and column numbers associated
     *             with the message
     * @param aKey key to locale message format
     * @param aArg0 an <code>Object</code> value
     */
    protected final void log(DetailAST aAST, String aKey, Object aArg0)
    {
        log(aAST.getLineNo(), aAST.getColumnNo(), aKey, aArg0);
    }
    /**
     * Helper method to log a LocalizedMessage.
     *
     * @param aLineNo line number to associate with the message
     * @param aColNo column number to associate with the message
     * @param aKey key to locale message format
     * @param aArg0 an <code>Object</code> value
     * @param aArg1 an <code>Object</code> value
     */
    protected final void log(int aLineNo, int aColNo, String aKey,
                    Object aArg0, Object aArg1)
    {
        log(aLineNo, aColNo, aKey, new Object[] {aArg0, aArg1});
    }

    /**
     * Helper method to log a LocalizedMessage.
     *
     * @param aAST a node to get line and column numbers associated
     *             with the message
     * @param aKey key to locale message format
     * @param aArg0 an <code>Object</code> value
     * @param aArg1 an <code>Object</code> value
     */
    protected final void log(DetailAST aAST, String aKey,
                             Object aArg0, Object aArg1)
    {
        log(aAST.getLineNo(), aAST.getColumnNo(), aKey, aArg0, aArg1);
    }

    /**
     * Returns the message bundle name resourcebundle that contains the messages
     * used by this module.
     * <p>
     * The default implementation expects the resource files to be named
     * messages.properties, messages_de.properties, etc. The file must
     * be placed in the same package as the module implementation.
     * </p>
     * <p>
     * Example: If you write com/foo/MyCoolCheck, create resource files
     * com/foo/messages.properties, com/foo/messages_de.properties, etc.
     * </p>
     *
     * @return name of a resource bundle that contains the messages
     * used by this module.
     */
    protected String getMessageBundle()
    {
        final String className = this.getClass().getName();
        return getMessageBundle(className);
    }

    /**
     * for unit tests, especially with a class with no package name.
     * @param aClassName class name of the module.
     * @return name of a resource bundle that contains the messages
     * used by the module.
     */
    String getMessageBundle(final String aClassName)
    {
        final int endIndex = aClassName.lastIndexOf('.');
        final String messages = "messages";
        if (endIndex < 0) {
            return messages;
        }
        final String packageName = aClassName.substring(0, endIndex);
        return packageName + "." + messages;
    }

    /**
     * Log a message that has no column information.
     *
     * @param aLine the line number where the error was found
     * @param aKey the message that describes the error
     * @param aArgs the details of the message
     *
     * @see java.text.MessageFormat
     */
    protected abstract void log(int aLine, String aKey, Object aArgs[]);

    /**
     * Log a message that has column information.
     *
     * @param aLine the line number where the error was found
     * @param aCol the column number where the error was found
     * @param aKey the message that describes the error
     * @param aArgs the details of the message
     *
     * @see java.text.MessageFormat
     */
    protected abstract void log(int aLine,
                                int aCol,
                                String aKey,
                                Object[] aArgs);

}
