/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

/**
 * created at Jan 3, 2002
 * @author Jeka
 */
package com.intellij.compiler;

import com.intellij.CommonBundle;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompiler;
import com.intellij.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.compiler.impl.javaCompiler.javac.JavacEmbeddedCompiler;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.compiler.impl.javaCompiler.jikes.JikesCompiler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Options;
import org.apache.oro.text.regex.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

@State(
  name = "CompilerConfiguration",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class CompilerConfigurationImpl extends CompilerConfiguration implements PersistentStateComponent<Element>, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.CompilerConfiguration");
  @NonNls public static final String TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME = "tests.external.compiler.home";
  public static final int DEPENDENCY_FORMAT_VERSION = 50;
  @NonNls private static final String PROPERTY_IDEA_USE_EMBEDDED_JAVAC = "idea.use.embedded.javac";

  @SuppressWarnings({"WeakerAccess"}) public String DEFAULT_COMPILER;
  @NotNull private BackendCompiler myDefaultJavaCompiler;

  // extensions of the files considered as resource files
  private final List<Pattern> myRegexpResourcePaterns = new ArrayList<Pattern>(getDefaultRegexpPatterns());
  // extensions of the files considered as resource files. If present, Overrides patterns in old regexp format stored in myRegexpResourcePaterns
  private final List<String> myWildcardPatterns = new ArrayList<String>();
  private final List<Pattern> myWildcardCompiledPatterns = new ArrayList<Pattern>();
  private boolean myWildcardPatternsInitialized = false;
  private final Project myProject;
  private final ExcludedEntriesConfiguration myExcludedEntriesConfiguration;

  public int DEPLOY_AFTER_MAKE = Options.SHOW_DIALOG;

  private final Collection<BackendCompiler> myRegisteredCompilers = new ArrayList<BackendCompiler>();
  private BackendCompiler JAVAC_EXTERNAL_BACKEND;
  private BackendCompiler JAVAC_EMBEDDED_BACKEND;
  private final Perl5Matcher myPatternMatcher = new Perl5Matcher();

  {
    loadDefaultWildcardPatterns();
  }

  public CompilerConfigurationImpl(Project project) {
    myProject = project;
    myExcludedEntriesConfiguration = new ExcludedEntriesConfiguration();
  }

  public Element getState() {
    try {
      @NonNls final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  private void loadDefaultWildcardPatterns() {
    if (!myWildcardPatterns.isEmpty()) {
      removeWildcardPatterns();
    }
    try {
      addWildcardResourcePattern("?*.properties");
      addWildcardResourcePattern("?*.xml");
      addWildcardResourcePattern("?*.gif");
      addWildcardResourcePattern("?*.png");
      addWildcardResourcePattern("?*.jpeg");
      addWildcardResourcePattern("?*.jpg");
      addWildcardResourcePattern("?*.html");
      addWildcardResourcePattern("?*.dtd");
      addWildcardResourcePattern("?*.tld");
      addWildcardResourcePattern("?*.ftl");
    }
    catch (MalformedPatternException e) {
      LOG.error(e);
    }
  }

  private static List<Pattern> getDefaultRegexpPatterns() {
    try {
      return Arrays.asList(compilePattern(".+\\.(properties|xml|html|dtd|tld)"), compilePattern(".+\\.(gif|png|jpeg|jpg)"));
    }
    catch (MalformedPatternException e) {
      LOG.error(e);
    }
    return Collections.emptyList();
  }

  public static String getTestsExternalCompilerHome() {
    String compilerHome = System.getProperty(TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME, null);
    if (compilerHome == null) {
      if (SystemInfo.isMac) {
        compilerHome = new File(System.getProperty("java.home")).getAbsolutePath();
      }
      else {
        compilerHome = new File(System.getProperty("java.home")).getParentFile().getAbsolutePath();        
      }
    }
    return compilerHome;
  }

  private static Pattern compilePattern(@NonNls String s) throws MalformedPatternException {
    final PatternCompiler compiler = new Perl5Compiler();
    final Pattern pattern;
    try {
      if (SystemInfo.isFileSystemCaseSensitive) {
        pattern = compiler.compile(s);
      }
      else {
        pattern = compiler.compile(s, Perl5Compiler.CASE_INSENSITIVE_MASK);
      }
    } catch (org.apache.oro.text.regex.MalformedPatternException ex) {
      throw new MalformedPatternException(ex);
    }
    return pattern;
  }

  public void disposeComponent() {
    Disposer.dispose(myExcludedEntriesConfiguration);
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public BackendCompiler getJavacCompiler() {
    return JAVAC_EXTERNAL_BACKEND;
  }

  public void projectOpened() {
    createCompilers();
  }

  private void createCompilers() {
    JAVAC_EXTERNAL_BACKEND = new JavacCompiler(myProject);
    myRegisteredCompilers.add(JAVAC_EXTERNAL_BACKEND);
    JAVAC_EMBEDDED_BACKEND = new JavacEmbeddedCompiler(myProject);
    //myRegisteredCompilers.add(JAVAC_EMBEDDED_BACKEND);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      final BackendCompiler JIKES_BACKEND = new JikesCompiler(myProject);
      myRegisteredCompilers.add(JIKES_BACKEND);

      final EclipseCompiler eclipse = new EclipseCompiler(myProject);
      if (eclipse.isInitialized()) {
        myRegisteredCompilers.add(eclipse);
      }
      //try {
      //  final EclipseEmbeddedCompiler eclipseEmbedded = new EclipseEmbeddedCompiler(myProject);
      //  myRegisteredCompilers.add(eclipseEmbedded);
      //}
      //catch (NoClassDefFoundError e) {
      //  // eclipse jar must be not in the classpath
      //}
    }

    myDefaultJavaCompiler = JAVAC_EXTERNAL_BACKEND;
    for (BackendCompiler compiler : myRegisteredCompilers) {
      if (compiler.getId().equals(DEFAULT_COMPILER)) {
        myDefaultJavaCompiler = compiler;
        break;
      }
    }
    DEFAULT_COMPILER = myDefaultJavaCompiler.getId();
  }

  public Collection<BackendCompiler> getRegisteredJavaCompilers() {
    return myRegisteredCompilers;
  }

  public String[] getResourceFilePatterns() {
    return getWildcardPatterns();
  }

  private String[] getRegexpPatterns() {
    String[] patterns = ArrayUtil.newStringArray(myRegexpResourcePaterns.size());
    int index = 0;
    for (final Pattern myRegexpResourcePatern : myRegexpResourcePaterns) {
      patterns[index++] = myRegexpResourcePatern.getPattern();
    }
    return patterns;
  }

  private String[] getWildcardPatterns() {
    return ArrayUtil.toStringArray(myWildcardPatterns);
  }

  public void addResourceFilePattern(String namePattern) throws MalformedPatternException {
    addWildcardResourcePattern(namePattern);
  }

  // need this method only for handling patterns in old regexp format
  private void addRegexpPattern(String namePattern) throws MalformedPatternException {
    Pattern pattern = compilePattern(namePattern);
    if (pattern != null) {
      myRegexpResourcePaterns.add(pattern);
    }
  }

  public ExcludedEntriesConfiguration getExcludedEntriesConfiguration() {
    return myExcludedEntriesConfiguration;
  }

  public boolean isExcludedFromCompilation(final VirtualFile virtualFile) {
    return myExcludedEntriesConfiguration.isExcluded(virtualFile);
  }

  private void addWildcardResourcePattern(@NonNls final String wildcardPattern) throws MalformedPatternException {
    final Pattern pattern = compilePattern(convertToRegexp(wildcardPattern));
    if (pattern != null) {
      myWildcardPatterns.add(wildcardPattern);
      myWildcardCompiledPatterns.add(pattern);
    }
  }

  public void removeResourceFilePatterns() {
    removeWildcardPatterns();
  }

  private void removeRegexpPatterns() {
    myRegexpResourcePaterns.clear();
  }

  private void removeWildcardPatterns() {
    myWildcardPatterns.clear();
    myWildcardCompiledPatterns.clear();
  }

  private static String convertToRegexp(String wildcardPattern) {
    if (isPatternNegated(wildcardPattern)) {
      wildcardPattern = wildcardPattern.substring(1);
    }
    return wildcardPattern.
      replaceAll("\\\\!", "!").
      replaceAll("\\.", "\\\\.").
      replaceAll("\\*\\?", ".+").
      replaceAll("\\?\\*", ".+").
      replaceAll("\\*", ".*").
      replaceAll("\\?", ".").
      replaceAll("(?:\\.\\*)+", ".*")  // optimization
    ;
  }

  public static boolean isPatternNegated(String wildcardPattern) {
    return wildcardPattern.length() > 1 && wildcardPattern.charAt(0) == '!';
  }

  public boolean isResourceFile(String name) {
    for (int i = 0; i < myWildcardCompiledPatterns.size(); i++) {
      Pattern pattern = myWildcardCompiledPatterns.get(i);
      final String wildcard = myWildcardPatterns.get(i);
      try {
        final boolean matches;
        synchronized (myPatternMatcher) {
          matches = myPatternMatcher.matches(name, pattern);
        }
        if (isPatternNegated(wildcard) ? !matches : matches) {
          return true;
        }
      }
      catch (Exception e) {
        LOG.error("Exception matching file name \"" + name + "\" against the pattern \"" + pattern.getPattern() + "\"", e);
      }
    }
    return false;
  }

  // property names
  @NonNls private static final String EXCLUDE_FROM_COMPILE = "excludeFromCompile";
  @NonNls private static final String RESOURCE_EXTENSIONS = "resourceExtensions";
  @NonNls private static final String WILDCARD_RESOURCE_PATTERNS = "wildcardResourcePatterns";
  @NonNls private static final String ENTRY = "entry";
  @NonNls private static final String NAME = "name";

  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);

    Element node = parentNode.getChild(EXCLUDE_FROM_COMPILE);
    if (node != null) {
      myExcludedEntriesConfiguration.readExternal(node);
    }

    try {
      removeRegexpPatterns();
      node = parentNode.getChild(RESOURCE_EXTENSIONS);
      if (node != null) {
        for (final Object o : node.getChildren(ENTRY)) {
          Element element = (Element)o;
          String pattern = element.getAttributeValue(NAME);
          if (pattern != null && !"".equals(pattern)) {
            addRegexpPattern(pattern);
          }
        }
      }

      removeWildcardPatterns();
      node = parentNode.getChild(WILDCARD_RESOURCE_PATTERNS);
      if (node != null) {
        myWildcardPatternsInitialized = true;
        for (final Object o : node.getChildren(ENTRY)) {
          final Element element = (Element)o;
          String pattern = element.getAttributeValue(NAME);
          if (pattern != null && !"".equals(pattern)) {
            addWildcardResourcePattern(pattern);
          }
        }
      }
    }
    catch (MalformedPatternException e) {
      throw new InvalidDataException(e);
    }

  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);

    if(myExcludedEntriesConfiguration.getExcludeEntryDescriptions().length > 0) {
      Element newChild = new Element(EXCLUDE_FROM_COMPILE);
      myExcludedEntriesConfiguration.writeExternal(newChild);
      parentNode.addContent(newChild);
    }

    String[] patterns = getRegexpPatterns();
    final Element newChild = new Element(RESOURCE_EXTENSIONS);
    for (final String pattern : patterns) {
      final Element entry = new Element(ENTRY);
      entry.setAttribute(NAME, pattern);
      newChild.addContent(entry);
    }
    parentNode.addContent(newChild);

    if (myWildcardPatternsInitialized || !myWildcardPatterns.isEmpty()) {
      final Element wildcardPatterns = new Element(WILDCARD_RESOURCE_PATTERNS);
      for (final String wildcardPattern : myWildcardPatterns) {
        final Element entry = new Element(ENTRY);
        entry.setAttribute(NAME, wildcardPattern);
        wildcardPatterns.addContent(entry);
      }
      parentNode.addContent(wildcardPatterns);
    }
  }

  @NotNull @NonNls
  public String getComponentName() {
    return "CompilerConfiguration";
  }

  public BackendCompiler getDefaultCompiler() {
    if (JAVAC_EXTERNAL_BACKEND == null) {
      createCompilers();
    }
    if (myDefaultJavaCompiler != JAVAC_EXTERNAL_BACKEND) return myDefaultJavaCompiler;
    boolean runEmbedded = ApplicationManager.getApplication().isUnitTestMode()
                          ? !JavacSettings.getInstance(myProject).isTestsUseExternalCompiler()
                          : Boolean.parseBoolean(System.getProperty(PROPERTY_IDEA_USE_EMBEDDED_JAVAC));
    return runEmbedded ? JAVAC_EMBEDDED_BACKEND : JAVAC_EXTERNAL_BACKEND;
  }

  public void setDefaultCompiler(BackendCompiler defaultCompiler) {
    myDefaultJavaCompiler = defaultCompiler;
    DEFAULT_COMPILER = defaultCompiler.getId();
  }

  public void convertPatterns() {
    if (!needPatternConversion()) {
      return;
    }
    try {
      boolean ok;
      try {
        ok = doConvertPatterns();
      }
      catch (MalformedPatternException e) {
        ok = false;
      }
      if (!ok) {
        final String initialPatternString = patternsToString(getRegexpPatterns());
        final String message = CompilerBundle.message(
          "message.resource.patterns.format.changed",
          ApplicationNamesInfo.getInstance().getProductName(),
          initialPatternString,
          CommonBundle.getOkButtonText(),
          CommonBundle.getCancelButtonText()
        );
        final String wildcardPatterns = Messages.showInputDialog(
          myProject, message, CompilerBundle.message("pattern.conversion.dialog.title"), Messages.getWarningIcon(), initialPatternString, new InputValidator() {
          public boolean checkInput(String inputString) {
            return true;
          }
          public boolean canClose(String inputString) {
            final StringTokenizer tokenizer = new StringTokenizer(inputString, ";", false);
            StringBuilder malformedPatterns = new StringBuilder();

            while (tokenizer.hasMoreTokens()) {
              String pattern = tokenizer.nextToken();
              try {
                addWildcardResourcePattern(pattern);
              }
              catch (MalformedPatternException e) {
                malformedPatterns.append("\n\n");
                malformedPatterns.append(pattern);
                malformedPatterns.append(": ");
                malformedPatterns.append(e.getMessage());
              }
            }

            if (malformedPatterns.length() > 0) {
              Messages.showErrorDialog(CompilerBundle.message("error.bad.resource.patterns", malformedPatterns.toString()),
                                       CompilerBundle.message("bad.resource.patterns.dialog.title"));
              removeWildcardPatterns();
              return false;
            }
            return true;
          }
        });
        if (wildcardPatterns == null) { // cancel pressed
          loadDefaultWildcardPatterns();
        }
      }
    }
    finally {
      myWildcardPatternsInitialized = true;
    }
  }

  private boolean needPatternConversion() {
    return !myWildcardPatternsInitialized && !myRegexpResourcePaterns.isEmpty();
  }

  private boolean doConvertPatterns() throws MalformedPatternException {
    final String[] regexpPatterns = getRegexpPatterns();
    final List<String> converted = new ArrayList<String>();
    final Pattern multipleExtensionsPatternPattern = compilePattern("\\.\\+\\\\\\.\\((\\w+(?:\\|\\w+)*)\\)");
    final Pattern singleExtensionPatternPattern = compilePattern("\\.\\+\\\\\\.(\\w+)");
    final Perl5Matcher matcher = new Perl5Matcher();
    for (final String regexpPattern : regexpPatterns) {
      //final Matcher multipleExtensionsMatcher = multipleExtensionsPatternPattern.matcher(regexpPattern);
      if (matcher.matches(regexpPattern, multipleExtensionsPatternPattern)) {
        final MatchResult match = matcher.getMatch();
        final StringTokenizer tokenizer = new StringTokenizer(match.group(1), "|", false);
        while (tokenizer.hasMoreTokens()) {
          converted.add("?*." + tokenizer.nextToken());
        }
      }
      else {
        //final Matcher singleExtensionMatcher = singleExtensionPatternPattern.matcher(regexpPattern);
        if (matcher.matches(regexpPattern, singleExtensionPatternPattern)) {
          final MatchResult match = matcher.getMatch();
          converted.add("?*." + match.group(1));
        }
        else {
          return false;
        }
      }
    }
    for (final String aConverted : converted) {
      addWildcardResourcePattern(aConverted);
    }
    return true;
  }

  private static String patternsToString(final String[] patterns) {
    final StringBuilder extensionsString = new StringBuilder();
    for (int idx = 0; idx < patterns.length; idx++) {
      if (idx > 0) {
        extensionsString.append(";");
      }
      extensionsString.append(patterns[idx]);
    }
    return extensionsString.toString();
  }

}
