/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd;

import java.util.Collections;

import net.sourceforge.pmd.sourcetypehandlers.Java13Handler;
import net.sourceforge.pmd.sourcetypehandlers.Java14Handler;
import net.sourceforge.pmd.sourcetypehandlers.Java15Handler;
import net.sourceforge.pmd.sourcetypehandlers.Java16Handler;
import net.sourceforge.pmd.sourcetypehandlers.Java17Handler;
import net.sourceforge.pmd.sourcetypehandlers.JspTypeHandler;
import net.sourceforge.pmd.sourcetypehandlers.SourceTypeHandler;

/**
 * This is an enumeration of the Language versions of which PMD is aware.  The
 * primary use of a LanguageVersion is for Rules, but they are also used by
 * utilities such as CPD.
 * <p>
 * The following are key components of a LanguageVersion in PMD:
 * <ul>
 * 	<li>Language - The Language with which this version is associated</li>
 * 	<li>Short name - The common short form of the Language</li>
 * 	<li>Terse name - The shortest and simplest possible form of the Language
 * 		name, generally used for Rule configuration</li>
 * 	<li>Extensions - File extensions associated with the Language</li>
 * 	<li>Rule Chain Visitor - The RuleChainVisitor implementation used for this
 * 		Language</li>
 * 	<li>Versions - The LanguageVersions associated with the Language</li>
 * </ul>
 *
 * @see LanguageVersion
 * @see LanguageVersionDiscoverer
 */
public enum LanguageVersion {

    //ANY(Language.ANY, "", null, true),
    //UNKNOWN(Language.UNKNOWN, "", null, true),
    CPP(Language.CPP, "", null, true), FORTRAN(Language.FORTRAN, "", null, true), EMCASCRIPT(Language.EMCASCRIPT, "",
	    null, true), JAVA_13(Language.JAVA, "1.3", new Java13Handler(), false), JAVA_14(Language.JAVA, "1.4",
	    new Java14Handler(), false), JAVA_15(Language.JAVA, "1.5", new Java15Handler(), true), JAVA_16(
	    Language.JAVA, "1.6", new Java16Handler(), false), JAVA_17(Language.JAVA, "1.7", new Java17Handler(), false), JSP(
	    Language.JSP, "", new JspTypeHandler(), true), PHP(Language.PHP, "", null, true), RUBY(Language.RUBY, "",
	    null, true);

    private final Language language;
    private final String version;
    private final SourceTypeHandler languageVersionHandler;
    private final boolean defaultVersion;

    /**
     * LanguageVersion constructor.  The LanguageVersion will add itself as a
     * version of its Language.
     * 
     * @param language The Language of this LanguageVersion.
     * @param version The version String for this LanguageVersion.
     * Must not be <code>null</code>, but may be an empty String.
     * @param languageVersionHandler The LanguageVersionHandler for this
     * LanguageVersion.   May be <code>null</code>.
     * @param defaultVersion If <code>true</code> then this is the default
     * version for the Language, otherwise this is not the default version.
     */
    private LanguageVersion(Language language, String version, SourceTypeHandler languageVersionHandler,
	    boolean defaultVersion) {
	if (language == null) {
	    throw new IllegalArgumentException("Language must not be null.");
	}
	if (version == null) {
	    throw new IllegalArgumentException("Version must not be null.");
	}
	this.language = language;
	this.version = version;
	this.languageVersionHandler = languageVersionHandler;
	this.defaultVersion = defaultVersion;

	// Sanity check: There can only be a single default version per Language
	if (defaultVersion) {
	    for (LanguageVersion languageVersion : language.getVersions()) {
		if (languageVersion.isDefaultVersion()) {
		    throw new IllegalArgumentException(languageVersion.getLanguage() + " already has default "
			    + languageVersion + ", not " + version);
		}
	    }
	}
	language.getVersions().add(this);
	// Make sure they are sorted (likely already are due to enum initialization order, but just in case)
	Collections.sort(language.getVersions());
    }

    /**
     * Get the Language for this LanguageVersion.
     * @return The Language for this LanguageVersion.
     */
    public Language getLanguage() {
	return language;
    }

    /**
     * Get the version String for this LanguageVersion.
     * @return The version String for this LanguageVersion.
     */
    public String getVersion() {
	return version;
    }

    /**
     * Get the name of this LanguageVersion.  This is Language name
     * appended with the LanguageVersion version if not an empty String.
     * @return The name of this LanguageVersion.
     */
    public String getName() {
	return version.length() > 0 ? language.getName() + " " + version : language.getName();
    }

    /**
     * Get the short name of this LanguageVersion.  This is Language short name
     * appended with the LanguageVersion version if not an empty String.
     * @return The short name of this LanguageVersion.
     */
    public String getShortName() {
	return version.length() > 0 ? language.getShortName() + " " + version : language.getShortName();
    }

    /**
     * Get the terse name of this LanguageVersion.  This is Language terse name
     * appended with the LanguageVersion version if not an empty String.
     * @return The terse name of this LanguageVersion.
     */
    public String getTerseName() {
	return version.length() > 0 ? language.getTerseName() + " " + version : language.getTerseName();
    }

    /**
     * Get the LanguageVersionHandler for this LanguageVersion.
     * @return The LanguageVersionHandler for this LanguageVersion.
     */
    public SourceTypeHandler getLanguageVersionHandler() {
	return languageVersionHandler;
    }

    /**
     * Returns if this LanguageVersion is the default version for the Language.
     * @return <code>true<code> if this is the default version for the Language,
     * <code>false</code> otherwise.
     */
    public boolean isDefaultVersion() {
	return defaultVersion;
    }

    /**
     * A friendly String form of the LanguageVersion.
     */
    @Override
    public String toString() {
	return "LanguageVersion[" + language.getName() + " " + version + "]";
    }

    /**
     * A utility method to find the LanguageVersion associated with the given
     * terse name.
     * @param terseName The LanguageVersion terse name.
     * @return The LanguageVersion with this terse name, <code>null</code> if there is
     * no Language with this terse name.
     */
    public static LanguageVersion findByTerseName(String terseName) {
	for (LanguageVersion languageVersion : LanguageVersion.values()) {
	    if (terseName.equals(languageVersion.getTerseName())) {
		return languageVersion;
	    }
	}
	return null;
    }
}
