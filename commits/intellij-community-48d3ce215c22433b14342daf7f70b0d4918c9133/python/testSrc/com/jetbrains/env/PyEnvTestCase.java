package com.jetbrains.env;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.TestEnv;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageUtil;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author traff
 * <p>
 * All inhertors must be in {@link com.jetbrains.env}.*
 * <p>
 * See "community/python/setup-test-environment/build.gradle"
 */
public abstract class PyEnvTestCase {
  private static final Logger LOG = Logger.getInstance(PyEnvTestCase.class.getName());

  private static final String TAGS_FILE = "tags.txt";

  @NotNull
  protected static final PyEnvTestSettings SETTINGS = new PyEnvTestSettings();


  /**
   * Logger to be used with {@link #startMessagesCapture()}
   */
  private PyTestMessagesLogger myLogger;

  /**
   * Tags that should exist between all tags, available on all interpreters for test to run.
   * See {@link #PyEnvTestCase(String...)}
   */
  @Nullable
  private final String[] myRequiredTags;


  private boolean myStaging = false;
  /**
   * TODO: Move to {@link EnvTestTagsRequired} as well?
   */

  @Rule public TestName myTestName = new TestName();

  @Rule public final TestWatcher myWatcher = new TestWatcher() {
    @Override
    protected void starting(Description description) {
      myStaging = isStaging(description);
    }
  };

  static {
    LOG.warn("Using following config\n" + SETTINGS.reportConfiguration());
  }

  /**
   * Escape test output to prevent python test be processed as test result
   */
  public static String escapeTestMessage(@NotNull final String message) {
    return message.replace("##", "from test: \\[sharp][sharp]");
  }

  protected boolean isStaging(Description description) {
    try {
      if (description.getTestClass().isAnnotationPresent(Staging.class)) {
        return true;
      }
      if (description.getTestClass().getMethod(description.getMethodName()).isAnnotationPresent(Staging.class)) {
        return true;
      }
      else {
        for (StagingOn so : description.getTestClass().getMethod(description.getMethodName()).getAnnotationsByType(StagingOn.class)) {
          if (so.os() == TestEnv.WINDOWS && SystemInfo.isWindows ||
              so.os() == TestEnv.LINUX && SystemInfo.isLinux ||
              so.os() == TestEnv.MAC && SystemInfo.isMac) {
            return true;
          }
        }
        return false;
      }
    }
    catch (NoSuchMethodException e) {
      return false;
    }
  }

  /**
   * @param requiredTags tags that should exist on some interpreter for this test to run.
   *                     if some of these tags do not exist on any interpreter, all test methods would be skipped using
   *                     {@link org.junit.Assume}.
   *                     See <a href="http://junit.sourceforge.net/javadoc/org/junit/Assume.html">Assume manual</a>.
   *                     Check [IDEA-122939] and [TW-25043] as well.
   */
  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  protected PyEnvTestCase(@NotNull final String... requiredTags) {
    myRequiredTags = requiredTags.length > 0 ? requiredTags.clone() : null;
  }

  @Nullable
  public static PyPackage getInstalledDjango(@NotNull final Sdk sdk) throws ExecutionException {
    return PyPackageUtil.findPackage(PyPackageManager.getInstance(sdk).refreshAndGetPackages(false), "django");
  }

  public static String norm(String testDataPath) {
    return FileUtil.toSystemIndependentName(testDataPath);
  }

  @Before
  public void setUp() {
    if (myRequiredTags != null) { // Ensure all tags exist between available interpreters
      Assume.assumeThat(
        "Can't find some tags between all available interpreter, test (all methods) will be skipped",
        getAvailableTags(),
        Matchers.hasItems(myRequiredTags)
      );
    }
  }


  /**
   * @return all tags available between all interpreters
   */
  @NotNull
  private static Collection<String> getAvailableTags() {
    final Collection<String> allAvailableTags = new HashSet<>();
    for (final String pythonRoot : getPythonRoots()) {
      allAvailableTags.addAll(loadEnvTags(pythonRoot));
    }
    return allAvailableTags;
  }

  protected void invokeTestRunnable(@NotNull final Runnable runnable) {
    if (runInWriteAction()) {
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> ApplicationManager.getApplication().runWriteAction(runnable));
    }
    else {
      runnable.run();
    }
  }


  protected boolean runInDispatchThread() {
    return false;
  }

  protected boolean runInWriteAction() {
    return false;
  }

  public void runPythonTest(final PyTestTask testTask) {
    runTest(testTask, getTestName(false));
  }

  protected String getTestName(boolean lowercaseFirstLetter) {
    return UsefulTestCase.getTestName(myTestName.getMethodName(), lowercaseFirstLetter);
  }

  public void runTest(@NotNull PyTestTask testTask, @NotNull String testName) {
    Assume.assumeFalse("Running under teamcity but not by Env configuration. Test seems to be launched by accident, skip it.",
                       UsefulTestCase.IS_UNDER_TEAMCITY && !SETTINGS.isEnvConfiguration());
    checkStaging();

    List<String> roots = getPythonRoots();

    /**
     * <p>
     * {@link org.junit.AssumptionViolatedException} here means this test must be <strong>skipped</strong>.
     * TeamCity supports this (if not you should create and issue about that).
     * Idea does not support it for JUnit 3, while JUnit 4 must be supported.
     * </p>
     *<p>
     * It this error brakes your test, please <strong>do not</strong> revert. Instead, do the following:
     * <ol>
     *   <li>Make sure {@link com.jetbrains.env.python} tests are <strong>excluded</strong> from your configuration (unless you are
     *   PyCharm developer)</li>
     *   <li>Check that your environment supports {@link AssumptionViolatedException}.
     *   JUnit 4 was created about 10 years ago, so fixing environment is much better approach than hacky "return;" here.
     *   </li>
     * </ol>
     *</p>
     */
    Assume.assumeFalse(testName +
                       ": environments are not defined. Skipping. \nChecks logs for settings that lead to this situation",
                       roots.isEmpty());
    doRunTests(testTask, testName, roots);
  }

  protected final void checkStaging() {
    if (!SETTINGS.isUnderTeamCity()) {
      return; // Its ok to run staging tests locally
    }
    Assume.assumeTrue("Test is annotated as Staging and should only run on staging environment",
                      myStaging == SETTINGS.isStagingMode());
  }

  protected void doRunTests(PyTestTask testTask, String testName, List<String> roots) {
    Assume.assumeFalse("Tests launched in remote SDK mode, and this test is not remote", SETTINGS.useRemoteSdk());

    PyEnvTaskRunner taskRunner = new PyEnvTaskRunner(roots);

    final EnvTestTagsRequired classAnnotation = getClass().getAnnotation(EnvTestTagsRequired.class);
    EnvTestTagsRequired methodAnnotation = null;
    try {
      final Method method = getClass().getMethod(myTestName.getMethodName());
      methodAnnotation = method.getAnnotation(EnvTestTagsRequired.class);
    }
    catch (final NoSuchMethodException e) {
      throw new AssertionError("No such method", e);
    }
    final String[] classTags = getTags(classAnnotation);
    final String[] methodTags = getTags(methodAnnotation);

    taskRunner.runTask(testTask, testName, ArrayUtil.mergeArrays(methodTags, classTags));
  }

  @NotNull
  private static String[] getTags(@Nullable final EnvTestTagsRequired tagsRequiredAnnotation) {
    if (tagsRequiredAnnotation != null) {
      return tagsRequiredAnnotation.tags();
    }
    else {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }

  public static List<String> getPythonRoots() {
    return SETTINGS.getPythons().stream().map(File::getAbsolutePath).collect(Collectors.toList());
  }


  public static List<String> loadEnvTags(String env) {
    List<String> envTags;

    try {
      File parent = new File(env);
      if (parent.isFile()) {
        parent = parent.getParentFile();
      }
      envTags = com.intellij.openapi.util.io.FileUtil.loadLines(new File(parent, TAGS_FILE));
    }
    catch (IOException e) {
      envTags = Lists.newArrayList();
    }
    return envTags;
  }

  public static String joinStrings(Collection<String> roots, String rootsName) {
    return roots.size() > 0 ? rootsName + StringUtil.join(roots, ", ") + "\n" : "";
  }

  /**
   * Capture all messages and error logs and store them to be obtained with {@link #getCapturesMessages()}
   * and stopped with {@link #stopMessageCapture()}
   */
  protected final void startMessagesCapture() {
    myLogger = new PyTestMessagesLogger();
    LoggedErrorProcessor.setNewInstance(myLogger);
    Messages.setTestDialog(myLogger);
  }

  /**
   * @return captures messages (first start with {@link #startMessagesCapture()}).
   * Logged exceptions -- list of messages to be displayed (never null)
   */
  @NotNull
  protected final Pair<List<Throwable>, List<String>> getCapturesMessages() {
    assert myLogger != null : "Capturing not enabled";
    return Pair.create(Collections.unmodifiableList(myLogger.myExceptions), Collections.unmodifiableList(myLogger.myMessages));
  }

  /**
   * Stop message capturing started with {@link #startMessagesCapture()}
   */
  protected final void stopMessageCapture() {
    LoggedErrorProcessor.restoreDefaultProcessor();
    Messages.setTestDialog(TestDialog.DEFAULT);
    myLogger = null;
  }

  private final Disposable myDisposable = Disposer.newDisposable();

  public Disposable getTestRootDisposable() {
    return myDisposable;
  }

  /**
   * Always call parrent when overwrite
   */
  @After
  public void tearDown() {
    // We can stop message capturing even if it was not started as cleanup process.
    stopMessageCapture();
    Disposer.dispose(myDisposable);
  }

  /**
   * Logger to be used with {@link #startMessagesCapture()}
   */
  private static final class PyTestMessagesLogger extends LoggedErrorProcessor implements TestDialog {

    private final List<String> myMessages = new ArrayList<>();
    private final List<Throwable> myExceptions = new ArrayList<>();

    @Override
    public int show(final String message) {
      myMessages.add(message);
      return 0;
    }

    @Override
    public void processWarn(final String message, final Throwable t, @NotNull final org.apache.log4j.Logger logger) {
      if (t != null) {
        myExceptions.add(t);
      }
    }

    @Override
    public void processError(final String message,
                             final Throwable t,
                             final String[] details,
                             @NotNull final org.apache.log4j.Logger logger) {
      if (t != null) {
        myExceptions.add(t);
      }
    }
  }
}

