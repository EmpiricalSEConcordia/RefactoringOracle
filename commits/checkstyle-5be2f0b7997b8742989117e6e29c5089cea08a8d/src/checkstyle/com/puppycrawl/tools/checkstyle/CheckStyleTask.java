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
package com.puppycrawl.tools.checkstyle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Hashtable;
import java.util.ResourceBundle;
import java.net.URL;

import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.LogOutputStream;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;

/**
 * An implementation of a ANT task for calling checkstyle. See the documentation
 * of the task for usage.
 * @author <a href="mailto:oliver@puppycrawl.com">Oliver Burn</a>
 */
public class CheckStyleTask
    extends Task
{
    /** poor man's enum for an xml formatter */
    private static final String E_XML = "xml";
    /** poor man's enum for an plain formatter */
    private static final String E_PLAIN = "plain";

    /** class path to locate class files */
    private Path mClasspath;

    /** name of file to check */
    private String mFileName;

    /** config file containing configuration */
    private String mConfigLocation;

    /** contains package names */
    private File mPackageNamesFile = null;

    /** whether to fail build on violations */
    private boolean mFailOnViolation = true;

    /** property to set on violations */
    private String mFailureProperty = null;

    /** contains the filesets to process */
    private final List mFileSets = new ArrayList();

    /** contains the formatters to log to */
    private final List mFormatters = new ArrayList();

    /** contains the Properties to override */
    private final List mOverrideProps = new ArrayList();

    /** the name of the properties file */
    private File mPropertiesFile;

    /** custom listeners to add to Checker */
    private final List mCustomListeners = new ArrayList();

    ////////////////////////////////////////////////////////////////////////////
    // Setters for ANT specific attributes
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Tells this task to set the named property to "true" when there
     * is a violation.
     * @param aPropertyName the name of the property to set
     *                      in the event of an failure.
     */
    public void setFailureProperty(String aPropertyName)
    {
        mFailureProperty = aPropertyName;
    }

    /** @param aFail whether to fail if a violation is found */
    public void setFailOnViolation(boolean aFail)
    {
        mFailOnViolation = aFail;
    }

    /**
     * Adds a set of files (nested fileset attribute).
     * @param aFS the file set to add
     */
    public void addFileset(FileSet aFS)
    {
        mFileSets.add(aFS);
    }

    /**
     * Add a formatter
     * @param aFormatter the formatter to add for logging.
     */
    public void addFormatter(Formatter aFormatter)
    {
        mFormatters.add(aFormatter);
    }

    /**
     * Add an override property
     * @param aProperty the property to add
     */
    public void addProperty(Property aProperty)
    {
        mOverrideProps.add(aProperty);
    }

    /**
     * Add a custom listener
     * @param aListener the listener to add
     */
    public void addListener(Listener aListener)
    {
        mCustomListeners.add(aListener);
    }

    /**
     * Set the class path.
     * @param aClasspath the path to locate classes
     */
    public void setClasspath(Path aClasspath)
    {
        mClasspath = aClasspath;
    }

    /** @return a created path for locating classes */
    public Path createClasspath()
    {
        if (mClasspath == null) {
            mClasspath = new Path(getProject());
        }
        return mClasspath.createPath();
    }

    /** @param aFile the file to be checked */
    public void setFile(File aFile)
    {
        mFileName = aFile.getAbsolutePath();
    }

    /** @param aFile the configuration file to use */
    public void setConfig(File aFile)
    {
        setConfigLocation(aFile.getAbsolutePath());
    }

    /** @param aURL the URL of the configuration to use */
    public void setConfigURL(URL aURL)
    {
        setConfigLocation(aURL.toExternalForm());
    }

    /**
     * Sets the location of the configuration.
     * @param aLocation the location, which is either a
     */
    private void setConfigLocation(String aLocation)
    {
        if (mConfigLocation != null) {
            throw new BuildException(
                "Attributes 'config' and 'configURL' "
                + "must not be set at the same time");
        }
        mConfigLocation = aLocation;
    }

    /** @param aFile the package names file to use */
    public void setPackageNamesFile(File aFile)
    {
        mPackageNamesFile = aFile;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Setters for Checker configuration attributes
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Sets a properties file for use instead
     * of individually setting them.
     * @param aProps the properties File to use
     */
    public void setProperties(File aProps)
    {
        mPropertiesFile = aProps;
    }

    ////////////////////////////////////////////////////////////////////////////
    // The doers
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Actually checks the files specified. All errors are reported to
     * System.out. Will fail if any errors occurred.
     * @throws BuildException an error occurred
     */
    public void execute()
        throws BuildException
    {
        // output version info in debug mode
        final ResourceBundle compilationProperties =
            ResourceBundle.getBundle("checkstylecompilation");
        final String version =
            compilationProperties.getString("checkstyle.compile.version");
        final String compileTimestamp =
            compilationProperties.getString("checkstyle.compile.timestamp");
        log("checkstyle version " + version, Project.MSG_VERBOSE);
        log("compiled on " + compileTimestamp, Project.MSG_VERBOSE);

        // Check for no arguments
        if ((mFileName == null) && (mFileSets.size() == 0)) {
            throw new BuildException(
                "Must specify atleast one of 'file' or nested 'fileset'.",
                getLocation());
        }

        if (mConfigLocation == null) {
            throw new BuildException("Must specify 'config'.", getLocation());
        }

        // Create the checker
        Checker c = null;
        try {
            try {
                final Properties props = createOverridingProperties();
                final Configuration config =
                    ConfigurationLoader.loadConfiguration(
                        mConfigLocation, new PropertiesExpander(props));

                final DefaultContext context = new DefaultContext();
                final ClassLoader loader =
                    new AntClassLoader(getProject(), mClasspath);
                context.add("classloader", loader);

                c = new Checker();

                //load the set of package names
                if (mPackageNamesFile != null) {
                    final ModuleFactory moduleFactory =
                        PackageNamesLoader.loadModuleFactory(
                            mPackageNamesFile.getAbsolutePath());
                    c.setModuleFactory(moduleFactory);
                }
                c.contextualize(context);
                c.configure(config);

                // setup the listeners
                final AuditListener[] listeners = getListeners();
                for (int i = 0; i < listeners.length; i++) {
                    c.addListener(listeners[i]);
                }
            }
            catch (Exception e) {
                throw new BuildException(
                    "Unable to create a Checker: " + e.getMessage(), e);
            }

            // Process the files
            final File[] files = scanFileSets();
            final int numErrs = c.process(files);

            // Handle the return status
            if ((numErrs > 0) && mFailureProperty != null) {
                getProject().setProperty(mFailureProperty, "true");
            }

            if ((numErrs > 0) && mFailOnViolation) {
                throw new BuildException("Got " + numErrs + " errors.",
                                         getLocation());
            }
        }
        finally {
            if (c != null) {
                c.destroy();
            }
        }
    }

    /**
     * Create the Properties object based on the arguments specified
     * to the ANT task.
     * @return the properties for property expansion expansion
     * @throws BuildException if an error occurs
     */
    private Properties createOverridingProperties()
    {
        final Properties retVal = new Properties();

        // Load the properties file if specified
        if (mPropertiesFile != null) {
            FileInputStream inStream = null;
            try {
                inStream = new FileInputStream(mPropertiesFile);
                retVal.load(inStream);
            }
            catch (FileNotFoundException e) {
                throw new BuildException(
                    "Could not find Properties file '" + mPropertiesFile + "'",
                    e, getLocation());
            }
            catch (IOException e) {
                throw new BuildException(
                    "Error loading Properties file '" + mPropertiesFile + "'",
                    e, getLocation());
            }
            finally {
                try {
                    if (inStream != null) {
                        inStream.close();
                    }
                }
                catch (IOException e) {
                    throw new BuildException(
                        "Error closing Properties file '"
                        + mPropertiesFile + "'",
                        e, getLocation());
                }
            }
        }

        // override with Ant properties like ${basedir}
        Hashtable antProps = this.getProject().getProperties();
        for (Iterator it = antProps.keySet().iterator(); it.hasNext();) {
            final String key = (String) it.next();
            final String value = String.valueOf(antProps.get(key));
            retVal.put(key, value);
        }

        // override with properties specified in subelements
        for (Iterator it = mOverrideProps.iterator(); it.hasNext();) {
            final Property p = (Property) it.next();
            retVal.put(p.getKey(), p.getValue());
        }

        return retVal;
    }

    /**
     * Return the list of listeners set in this task.
     * @return the list of listeners.
     * @throws ClassNotFoundException if an error occurs
     * @throws InstantiationException if an error occurs
     * @throws IllegalAccessException if an error occurs
     * @throws IOException if an error occurs
     */
    protected AuditListener[] getListeners()
        throws ClassNotFoundException, InstantiationException,
        IllegalAccessException, IOException
    {
        final int formatterCount = Math.max(1, mFormatters.size());
        final int listenerCount = mCustomListeners.size();

        final AuditListener[] listeners =
            new AuditListener[formatterCount + listenerCount];

        // formatters
        if (mFormatters.size() == 0) {
            OutputStream debug = new LogOutputStream(this, Project.MSG_DEBUG);
            OutputStream err = new LogOutputStream(this, Project.MSG_ERR);
            listeners[0] = new DefaultLogger(debug, true, err, true);
        }
        else {
            for (int i = 0; i < formatterCount; i++) {
                final Formatter f = (Formatter) mFormatters.get(i);
                listeners[i] = f.createListener(this);
            }
        }
        //custom listeners
        for (int i = 0; i < listenerCount; i++) {
            final Listener listener = (Listener) mCustomListeners.get(i);
            listeners[formatterCount + i] = createCustomListener(listener);
        }
        return listeners;
    }
    /**
     * Creates an audit listener for a custom listener.
     * @param aListener custom listener
     * @return an audit listener
     */
    private AuditListener createCustomListener(Listener aListener)
    {
        final String classname = aListener.getClassname();

        // use the task classpath property
        final ClassLoader loader =
            new AntClassLoader(getClass().getClassLoader(), getProject(),
                 mClasspath, true);

        AuditListener listener = null;
        try {
            final Class listenerClass =
                Class.forName(classname, true, loader);
            listener =
                (AuditListener) listenerClass.newInstance();
        }
        catch (Exception e) {
            throw new BuildException("Unable to create listener '"
                + classname + "': " + e);
        }
        return listener;
    }

    /**
     * returns the list of files (full path name) to process.
     * @return the list of files included via the filesets.
     */
    protected File[] scanFileSets()
    {
        final ArrayList list = new ArrayList();
        if (mFileName != null) {
            // oops we've got an additional one to process, don't
            // forget it. No sweat, it's fully resolved via the setter.
            log("Adding standalone file for audit", Project.MSG_VERBOSE);
            list.add(new File(mFileName));
        }
        for (int i = 0; i < mFileSets.size(); i++) {
            final FileSet fs = (FileSet) mFileSets.get(i);
            final DirectoryScanner ds = fs.getDirectoryScanner(getProject());
            ds.scan();

            final String[] names = ds.getIncludedFiles();
            log(i + ") Adding " + names.length + " files from directory "
                + ds.getBasedir(),
                Project.MSG_VERBOSE);

            for (int j = 0; j < names.length; j++) {
                final String pathname =
                    ds.getBasedir() + File.separator + names[j];
                list.add(new File(pathname));
            }
        }

        return (File[]) list.toArray(new File[0]);
    }

    /**
     * Poor mans enumeration for the formatter types.
     * @author <a href="mailto:oliver@puppycrawl.com">Oliver Burn</a>
     */
    public static class FormatterType
        extends EnumeratedAttribute
    {
        /** my possible values */
        private static final String[] VALUES = {E_XML, E_PLAIN};

        /** @see EnumeratedAttribute */
        public String[] getValues()
        {
            return VALUES;
        }
    }

    /**
     * Details about a formatter to be used.
     * @author <a href="mailto:oliver@puppycrawl.com">Oliver Burn</a>
     */
    public static class Formatter
    {
        /** the formatter type */
        private FormatterType mFormatterType = null;
        /** the file to output to */
        private File mToFile = null;

        /**
         * Set the type of the formatter.
         * @param aType the type
         */
        public void setType(FormatterType aType)
        {
            final String val = aType.getValue();
            if (!E_XML.equals(val) && !E_PLAIN.equals(val)) {
                throw new BuildException("Invalid formatter type: " + val);
            }

            mFormatterType = aType;
        }

        /**
         * Set the file to output to.
         * @param aTo the file to output to
         */
        public void setTofile(File aTo)
        {
            mToFile = aTo;
        }

        /**
         * Creates a listener for the formatter.
         * @param aTask the task running
         * @return a listener
         * @throws IOException if an error occurs
         */
        public AuditListener createListener(Task aTask)
            throws IOException
        {
            if (mFormatterType != null
                && E_XML.equals(mFormatterType.getValue()))
            {
                return createXMLLogger(aTask);
            }
            else {
                return createDefaultLogger(aTask);
            }
        }

        /**
         * @return a DefaultLogger instance
         * @param aTask the task to possibly log to
         * @throws IOException if an error occurs
         */
        private AuditListener createDefaultLogger(Task aTask)
            throws IOException
        {
            if (mToFile == null) {
                return new DefaultLogger(
                    new LogOutputStream(aTask, Project.MSG_DEBUG), true,
                    new LogOutputStream(aTask, Project.MSG_ERR), true);
            }
            return new DefaultLogger(new FileOutputStream(mToFile), true);
        }

        /**
         * @return an XMLLogger instance
         * @param aTask the task to possibly log to
         * @throws IOException if an error occurs
         */
        private AuditListener createXMLLogger(Task aTask)
            throws IOException
        {
            if (mToFile == null) {
                return new XMLLogger(
                    new LogOutputStream(aTask, Project.MSG_INFO), true);
            }
            else {
                return new XMLLogger(new FileOutputStream(mToFile), true);
            }
        }
    }

    /**
     * Represents a property that consists of a key and value.
     */
    public static class Property
    {
        /** the property key */
        private String mKey;
        /** the property value */
        private String mValue;

        /** @return the property key */
        public String getKey()
        {
            return mKey;
        }

        /** @param aKey sets the property key */
        public void setKey(String aKey)
        {
            mKey = aKey;
        }

        /** @return the property value */
        public String getValue()
        {
            return mValue;
        }

        /** @param aValue set the property value */
        public void setValue(String aValue)
        {
            mValue = aValue;
        }

        /** @param aValue set the property value from a File */
        public void setFile(File aValue)
        {
            setValue(aValue.getAbsolutePath());
        }
    }

    /** Represents a custom listener */
    public static class Listener
    {
        /** classname of the listener class */
        private String mClassname;

        /** @return the classname */
        public String getClassname()
        {
            return mClassname;
        }

        /** @param aClassname set the classname */
        public void setClassname(String aClassname)
        {
            mClassname = aClassname;
        }
    }
}
