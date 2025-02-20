/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package test.net.sourceforge.pmd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import junit.framework.JUnit4TestAdapter;
import net.sourceforge.pmd.Configuration;
import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.RulePriority;
import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.LanguageVersionDiscoverer;
import net.sourceforge.pmd.renderers.CSVRenderer;
import net.sourceforge.pmd.renderers.Renderer;
import net.sourceforge.pmd.util.ClasspathClassLoader;

import org.junit.Test;

public class ConfigurationTest {

    @Test
    public void testSuppressMarker() {
	Configuration configuration = new Configuration();
	assertEquals("Default suppress marker", PMD.SUPPRESS_MARKER, configuration.getSuppressMarker());
	configuration.setSuppressMarker("CUSTOM_MARKER");
	assertEquals("Changed suppress marker", "CUSTOM_MARKER", configuration.getSuppressMarker());
    }

    @Test
    public void testThreads() {
	Configuration configuration = new Configuration();
	assertEquals("Default threads", Runtime.getRuntime().availableProcessors(), configuration.getThreads());
	configuration.setThreads(0);
	assertEquals("Changed threads", 0, configuration.getThreads());
    }

    @Test
    public void testClassLoader() throws IOException {
	Configuration configuration = new Configuration();
	assertEquals("Default ClassLoader", Configuration.class.getClassLoader(), configuration.getClassLoader());
	configuration.prependClasspath("some.jar");
	assertEquals("Prepended ClassLoader class", ClasspathClassLoader.class, configuration.getClassLoader()
		.getClass());
	URL[] urls = ((ClasspathClassLoader) configuration.getClassLoader()).getURLs();
	assertEquals("urls length", 1, urls.length);
	assertTrue("url[0]", urls[0].toString().endsWith("/some.jar"));
	assertEquals("parent classLoader", Configuration.class.getClassLoader(), configuration.getClassLoader()
		.getParent());
	configuration.setClassLoader(null);
	assertEquals("Revert to default ClassLoader", Configuration.class.getClassLoader(), configuration
		.getClassLoader());
    }

    @Test
    public void testLanguageVersionDiscoverer() {
	Configuration configuration = new Configuration();
	LanguageVersionDiscoverer languageVersionDiscoverer = configuration.getLanguageVersionDiscoverer();
	assertEquals("Default Java version", LanguageVersion.JAVA_17, languageVersionDiscoverer
		.getDefaultLanguageVersion(Language.JAVA));
	configuration.setDefaultLanguageVersion(LanguageVersion.JAVA_15);
	assertEquals("Modified Java version", LanguageVersion.JAVA_15, languageVersionDiscoverer
		.getDefaultLanguageVersion(Language.JAVA));
    }

    @Test
    public void testRuleSets() {
	Configuration configuration = new Configuration();
	assertEquals("Default RuleSets", null, configuration.getRuleSets());
	configuration.setRuleSets("/rulesets/basic.xml");
	assertEquals("Changed RuleSets", "/rulesets/basic.xml", configuration.getRuleSets());
    }

    @Test
    public void testMinimumPriority() {
	Configuration configuration = new Configuration();
	assertEquals("Default minimum priority", RulePriority.LOW, configuration.getMinimumPriority());
	configuration.setMinimumPriority(RulePriority.HIGH);
	assertEquals("Changed minimum priority", RulePriority.HIGH, configuration.getMinimumPriority());
    }

    @Test
    public void testSourceEncoding() {
	Configuration configuration = new Configuration();
	assertEquals("Default source encoding", System.getProperty("file.encoding"), configuration.getSourceEncoding());
	configuration.setSourceEncoding("some_other_encoding");
	assertEquals("Changed source encoding", "some_other_encoding", configuration.getSourceEncoding());
    }

    @Test
    public void testInputPaths() {
	Configuration configuration = new Configuration();
	assertEquals("Default input paths", null, configuration.getInputPaths());
	configuration.setInputPaths("a,b,c");
	assertEquals("Changed input paths", "a,b,c", configuration.getInputPaths());
    }

    @Test
    public void testReportShortNames() {
	Configuration configuration = new Configuration();
	assertEquals("Default report short names", false, configuration.isReportShortNames());
	configuration.setReportShortNames(true);
	assertEquals("Changed report short names", true, configuration.isReportShortNames());
    }

    @Test
    public void testReportFormat() {
	Configuration configuration = new Configuration();
	assertEquals("Default report format", null, configuration.getReportFormat());
	configuration.setReportFormat("csv");
	assertEquals("Changed report format", "csv", configuration.getReportFormat());
    }

    @Test
    public void testCreateRenderer() {
	Configuration configuration = new Configuration();
	configuration.setReportFormat("csv");
	Renderer renderer = configuration.createRenderer();
	assertEquals("Renderer class", CSVRenderer.class, renderer.getClass());
	assertEquals("Default renderer show suppressed violations", false, renderer.isShowSuppressedViolations());

	configuration.setShowSuppressedViolations(true);
	renderer = configuration.createRenderer();
	assertEquals("Renderer class", CSVRenderer.class, renderer.getClass());
	assertEquals("Changed renderer show suppressed violations", true, renderer.isShowSuppressedViolations());
    }

    @Test
    public void testReportFile() {
	Configuration configuration = new Configuration();
	assertEquals("Default report file", null, configuration.getReportFile());
	configuration.setReportFile("somefile");
	assertEquals("Changed report file", "somefile", configuration.getReportFile());
    }

    @Test
    public void testShowSuppressedViolations() {
	Configuration configuration = new Configuration();
	assertEquals("Default show suppressed violations", false, configuration.isShowSuppressedViolations());
	configuration.setShowSuppressedViolations(true);
	assertEquals("Changed show suppressed violations", true, configuration.isShowSuppressedViolations());
    }

    @Test
    public void testReportProperties() {
	Configuration configuration = new Configuration();
	assertEquals("Default report properties size", 0, configuration.getReportProperties().size());
	configuration.getReportProperties().put("key", "value");
	assertEquals("Changed report properties size", 1, configuration.getReportProperties().size());
	assertEquals("Changed report properties value", "value", configuration.getReportProperties().get("key"));
	configuration.setReportProperties(new Properties());
	assertEquals("Replaced report properties size", 0, configuration.getReportProperties().size());
    }

    @Test
    public void testDebug() {
	Configuration configuration = new Configuration();
	assertEquals("Default debug", false, configuration.isDebug());
	configuration.setDebug(true);
	assertEquals("Changed debug", true, configuration.isDebug());
    }

    @Test
    public void testStressTest() {
	Configuration configuration = new Configuration();
	assertEquals("Default stress test", false, configuration.isStressTest());
	configuration.setStressTest(true);
	assertEquals("Changed stress test", true, configuration.isStressTest());
    }

    @Test
    public void testBenchmark() {
	Configuration configuration = new Configuration();
	assertEquals("Default benchmark", false, configuration.isBenchmark());
	configuration.setBenchmark(true);
	assertEquals("Changed benchmark", true, configuration.isBenchmark());
    }

    public static junit.framework.Test suite() {
	return new JUnit4TestAdapter(ConfigurationTest.class);
    }
}
