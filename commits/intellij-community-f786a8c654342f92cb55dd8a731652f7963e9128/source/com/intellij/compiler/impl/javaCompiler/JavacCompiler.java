package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.JavacOutputParser;
import com.intellij.compiler.JavacSettings;
import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.projectRoots.impl.MockJdkWrapper;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.java.LanguageLevel;

import java.io.*;
import java.util.*;
import java.nio.charset.Charset;

class JavacCompiler implements BackendCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.JavacCompiler");
  private Project myProject;
  private final List<File> myTempFiles = new ArrayList<File>();

  public boolean checkCompiler() {
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    final Set<ProjectJdk> checkedJdks = new HashSet<ProjectJdk>();
    for (final Module module : modules) {
      final ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();
      if (jdk == null) {
        continue;
      }
      if (checkedJdks.contains(jdk)) {
        continue;
      }
      final VirtualFile homeDirectory = jdk.getHomeDirectory();
      if (homeDirectory == null) {
        Messages.showMessageDialog(myProject, CompilerBundle.message("javac.error.jdk.home.missing", jdk.getName()),
                                   CompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
        return false;
      }
      final String vmExecutablePath = jdk.getVMExecutablePath();
      if (vmExecutablePath == null) {
        Messages.showMessageDialog(myProject,
                                   CompilerBundle.message("javac.error.vm.executable.missing", jdk.getName()),
                                   CompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
        return false;
      }
      final String toolsJarPath = jdk.getToolsPath();
      if (toolsJarPath == null) {
        Messages.showMessageDialog(myProject,
                                   CompilerBundle.message("javac.error.tools.jar.missing", jdk.getName()), CompilerBundle.message("compiler.javac.name"),
                                   Messages.getErrorIcon());
        return false;
      }
      final String versionString = jdk.getVersionString();
      if (versionString == null) {
        Messages.showMessageDialog(myProject, CompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()),
                                   CompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
        return false;
      }
      if (isOfVersion(versionString, "1.0")) {
        Messages.showMessageDialog(myProject, CompilerBundle.message("javac.error.1_0_compilation.not.supported"), CompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
        return false;
      }
      checkedJdks.add(jdk);
    }

    return true;
  }

  public JavacCompiler(Project project) {
    myProject = project;
  }

  public OutputParser createOutputParser() {
    return new JavacOutputParser(myProject);
  }

  public String[] createStartupCommand(final ModuleChunk chunk, final CompileContext context, final String outputPath)
    throws IOException, IllegalArgumentException {

    final ArrayList<String> commandLine = new ArrayList<String>();

    final Exception[] ex = new Exception[]{null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          _createStartupCommand(chunk, commandLine, outputPath);
        }
        catch (IllegalArgumentException e) {
          ex[0] = e;
        }
        catch (IOException e) {
          ex[0] = e;
        }
      }
    });
    if (ex[0] != null) {
      if (ex[0] instanceof IOException) {
        throw (IOException)ex[0];
      }
      else if (ex[0] instanceof IllegalArgumentException) {
        throw (IllegalArgumentException)ex[0];
      }
      else {
        LOG.error(ex[0]);
      }
    }
    return commandLine.toArray(new String[commandLine.size()]);
  }

  private void _createStartupCommand(final ModuleChunk chunk, final ArrayList<String> commandLine, final String outputPath) throws IOException {
    final ProjectJdk jdk = getJdkForStartupCommand(chunk);
    final String versionString = jdk.getVersionString();
    if (versionString == null || "".equals(versionString)) {
      throw new IllegalArgumentException(CompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()));
    }
    final boolean isVersion1_0 = isOfVersion(versionString, "1.0");
    final boolean isVersion1_1 = isOfVersion(versionString, "1.1");
    final boolean isVersion1_2 = isOfVersion(versionString, "1.2");
    final boolean isVersion1_3 = isOfVersion(versionString, "1.3");
    final boolean isVersion1_4 = isOfVersion(versionString, "1.4");
    final boolean isVersion1_5 = isOfVersion(versionString, "1.5") || isOfVersion(versionString, "5.0");

    final JavacSettings javacSettings = JavacSettings.getInstance(myProject);

    final String vmExePath = jdk.getVMExecutablePath();

    commandLine.add(vmExePath);
    if (isVersion1_1 || isVersion1_0) {
      //noinspection HardCodedStringLiteral
      commandLine.add("-mx" + javacSettings.MAXIMUM_HEAP_SIZE + "m");
    }
    else {
      //noinspection HardCodedStringLiteral
      commandLine.add("-Xmx" + javacSettings.MAXIMUM_HEAP_SIZE + "m");
    }
    // need to specify default encoding so that javac outputs messages in 'correct' language
    //noinspection HardCodedStringLiteral
    commandLine.add("-D" + CharsetToolkit.FILE_ENCODING_PROPERTY + "=" + CharsetToolkit.getDefaultSystemCharset().name());
    // javac's VM should use the same default locale that IDEA uses in order for javac to print messages in 'correct' language
    //noinspection HardCodedStringLiteral
    final String lang = System.getProperty("user.language");
    if (lang != null) {
      //noinspection HardCodedStringLiteral
      commandLine.add("-Duser.language=" + lang);
    }
    //noinspection HardCodedStringLiteral
    final String country = System.getProperty("user.country");
    if (country != null) {
      //noinspection HardCodedStringLiteral
      commandLine.add("-Duser.country" + country);
    }
    //noinspection HardCodedStringLiteral
    final String region = System.getProperty("user.region");
    if (region != null) {
      //noinspection HardCodedStringLiteral
      commandLine.add("-Duser.region" + region);
    }
    //noinspection HardCodedStringLiteral
    commandLine.add("-classpath");

    if (isVersion1_0) {
      commandLine.add(jdk.getToolsPath()); //  do not use JavacRunner for jdk 1.0
    }
    else {
      commandLine.add(jdk.getToolsPath() + File.pathSeparator + PathUtilEx.getIdeaRtJarPath());
      commandLine.add(com.intellij.rt.compiler.JavacRunner.class.getName());
      commandLine.add("\"" + versionString + "\"");
    }

    if (isVersion1_2 || isVersion1_1 || isVersion1_0) {
      commandLine.add("sun.tools.javac.Main");
    }
    else {
      commandLine.add("com.sun.tools.javac.Main");
    }

    final LanguageLevel applicableLanguageLevel = getApplicableLanguageLevel(versionString);
    if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_5)) {
      //noinspection HardCodedStringLiteral
      commandLine.add("-source");
      commandLine.add("1.5");
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_4)) {
      //noinspection HardCodedStringLiteral
      commandLine.add("-source");
      commandLine.add("1.4");
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_3)) {
      if (isVersion1_4 || isVersion1_5) {
        //noinspection HardCodedStringLiteral
        commandLine.add("-source");
        commandLine.add("1.3");
      }
    }

    //noinspection HardCodedStringLiteral
    commandLine.add("-verbose");

    final String cp = chunk.getCompilationClasspath();
    final String bootCp = chunk.getCompilationBootClasspath();

    final String classPath;
    if (isVersion1_0 || isVersion1_1) {
      classPath = bootCp + File.pathSeparator + cp;
    }
    else {
      classPath = cp;

      //noinspection HardCodedStringLiteral
      commandLine.add("-bootclasspath");
      // important: need to quote boot classpath if path to jdk contain spaces
      //noinspection HardCodedStringLiteral
      addClassPathValue(jdk, false, commandLine, CompilerUtil.quotePath(bootCp), "javac_bootcp");
    }

    //noinspection HardCodedStringLiteral
    commandLine.add("-classpath");
    //noinspection HardCodedStringLiteral
    addClassPathValue(jdk, isVersion1_0, commandLine, classPath, "javac_cp");

    if (!isVersion1_1 && !isVersion1_0) {
      //noinspection HardCodedStringLiteral
      commandLine.add("-sourcepath");
      // this way we tell the compiler that the sourcepath is "empty". However, javac thinks that sourcepath is 'new File("")'
      // this may cause problems if we have java code in IDEA working directory
      commandLine.add("\"\"");
    }

    //noinspection HardCodedStringLiteral
    commandLine.add("-d");
    commandLine.add(outputPath.replace('/', File.separatorChar));

    StringTokenizer tokenizer = new StringTokenizer(javacSettings.getOptionsString(), " ");
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if (isVersion1_0) {
        //noinspection HardCodedStringLiteral
        if ("-deprecation".equals(token)) {
          continue; // not supported for this version
        }
      }
      if (isVersion1_0 || isVersion1_1 || isVersion1_2 || isVersion1_3 || isVersion1_4) {
        //noinspection HardCodedStringLiteral
        if ("-Xlint".equals(token)) {
          continue; // not supported in these versions
        }
      }
      commandLine.add(token);
    }

    final VirtualFile[] files = chunk.getFilesToCompile();

    if (isVersion1_0) {
      for (VirtualFile file : files) {
        String path = file.getPath();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Adding path for compilation " + path);
        }
        commandLine.add(CompilerUtil.quotePath(path));
      }
    }
    else {
      //noinspection HardCodedStringLiteral
      File sourcesFile = FileUtil.createTempFile("javac", ".tmp");
      sourcesFile.deleteOnExit();
      myTempFiles.add(sourcesFile);
      final PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(sourcesFile)));
      try {
        for (final VirtualFile file : files) {
          // Important: should use "/" slashes!
          // but not for JDK 1.5 - see SCR 36673
          final String path = isVersion1_5 ? file.getPath().replace('/', File.separatorChar) : file.getPath();
          if (LOG.isDebugEnabled()) {
            LOG.debug("Adding path for compilation " + path);
          }
          writer.println(isVersion1_1 ? path : CompilerUtil.quotePath(path));
        }
      }
      finally {
        writer.close();
      }
      commandLine.add("@" + sourcesFile.getAbsolutePath());
    }
  }

  private void addClassPathValue(final ProjectJdk jdk,
                                 final boolean isVersion1_0,
                                 final ArrayList<String> commandLine,
                                 final String cpString,
                                 final String tempFileName) throws IOException {
    // must include output path to classpath, otherwise javac will compile all dependent files no matter were they compiled before or not
    if (isVersion1_0) {
      commandLine.add(jdk.getToolsPath() + File.pathSeparator + cpString);
    }
    else {
      //noinspection HardCodedStringLiteral
      File cpFile = FileUtil.createTempFile(tempFileName, ".tmp");
      cpFile.deleteOnExit();
      myTempFiles.add(cpFile);
      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(cpFile)));
      writer.println(cpString);
      writer.close();
      commandLine.add("@" + cpFile.getAbsolutePath());
    }
  }

  private ProjectJdk getJdkForStartupCommand(final ModuleChunk chunk) {
    final ProjectJdk jdk = chunk.getJdk();
    if (ApplicationManager.getApplication().isUnitTestMode() && JavacSettings.getInstance(myProject).isTestsUseExternalCompiler()) {
      final String jdkHomePath = System.getProperty(CompilerConfiguration.TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME, null);
      if (jdkHomePath == null) {
        //noinspection HardCodedStringLiteral
        throw new IllegalArgumentException("[TEST-MODE] Cannot determine home directory for JDK to use javac from");
      }
      // when running under Mock JDK use VM executable from the JDK on which the tests run
      return new MockJdkWrapper(jdkHomePath, jdk);
    }
    return jdk;
  }

  private LanguageLevel getApplicableLanguageLevel(String versionString) {
    LanguageLevel languageLevel = ProjectRootManagerEx.getInstanceEx(myProject).getLanguageLevel();

    final boolean isVersion1_5 = isOfVersion(versionString, "1.5") || isOfVersion(versionString, "5.0");
    if (LanguageLevel.JDK_1_5.equals(languageLevel)) {
      if (!isVersion1_5) {
        languageLevel = LanguageLevel.JDK_1_4;
      }
    }

    if (LanguageLevel.JDK_1_4.equals(languageLevel)) {
      if (!isOfVersion(versionString, "1.4") && !isVersion1_5) {
        languageLevel = LanguageLevel.JDK_1_3;
      }
    }

    return languageLevel;
  }

  private static boolean isOfVersion(String versionString, String checkedVersion) {
    return versionString.indexOf(checkedVersion) > -1;
  }

  public void processTerminated() {
    if (myTempFiles.size() > 0) {
      for (Iterator it = myTempFiles.iterator(); it.hasNext();) {
        FileUtil.delete((File)it.next());
      }
      myTempFiles.clear();
    }
  }
}
