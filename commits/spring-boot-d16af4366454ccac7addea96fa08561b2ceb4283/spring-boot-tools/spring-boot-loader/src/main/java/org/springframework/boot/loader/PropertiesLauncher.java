/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.Archive.Entry;
import org.springframework.boot.loader.archive.Archive.EntryFilter;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.util.SystemPropertyUtils;

/**
 * {@link Launcher} for archives with user-configured classpath and main class via a
 * properties file. This model is often more flexible and more amenable to creating
 * well-behaved OS-level services than a model based on executable jars.
 * <p>
 * Looks in various places for a properties file to extract loader settings, defaulting to
 * {@code loader.properties} either on the current classpath or in the current working
 * directory. The name of the properties file can be changed by setting a System property
 * {@code loader.config.name} (e.g. {@code -Dloader.config.name=foo} will look for
 * {@code foo.properties}. If that file doesn't exist then tries
 * {@code loader.config.location} (with allowed prefixes {@code classpath:} and
 * {@code file:} or any valid URL). Once that file is located turns it into Properties and
 * extracts optional values (which can also be provided overridden as System properties in
 * case the file doesn't exist):
 * <ul>
 * <li>{@code loader.path}: a comma-separated list of directories (containing file
 * resources and/or nested archives in *.jar or *.zip or archives) or archives to append
 * to the classpath. {@code BOOT-INF/classes,BOOT-INF/lib} in the application archive are
 * always used</li>
 * <li>{@code loader.main}: the main method to delegate execution to once the class loader
 * is set up. No default, but will fall back to looking for a {@code Start-Class} in a
 * {@code MANIFEST.MF}, if there is one in <code>${loader.home}/META-INF</code>.</li>
 * </ul>
 *
 * @author Dave Syer
 * @author Janne Valkealahti
 * @author Andy Wilkinson
 */
public class PropertiesLauncher extends Launcher {

	private static final String DEBUG = "loader.debug";

	/**
	 * Properties key for main class. As a manifest entry can also be specified as
	 * {@code Start-Class}.
	 */
	public static final String MAIN = "loader.main";

	/**
	 * Properties key for classpath entries (directories possibly containing jars or
	 * jars). Multiple entries can be specified using a comma-separated list. {@code
	 * BOOT-INF/classes,BOOT-INF/lib} in the application archive are always used.
	 */
	public static final String PATH = "loader.path";

	/**
	 * Properties key for home directory. This is the location of external configuration
	 * if not on classpath, and also the base path for any relative paths in the
	 * {@link #PATH loader path}. Defaults to current working directory (
	 * <code>${user.dir}</code>).
	 */
	public static final String HOME = "loader.home";

	/**
	 * Properties key for default command line arguments. These arguments (if present) are
	 * prepended to the main method arguments before launching.
	 */
	public static final String ARGS = "loader.args";

	/**
	 * Properties key for name of external configuration file (excluding suffix). Defaults
	 * to "application". Ignored if {@link #CONFIG_LOCATION loader config location} is
	 * provided instead.
	 */
	public static final String CONFIG_NAME = "loader.config.name";

	/**
	 * Properties key for config file location (including optional classpath:, file: or
	 * URL prefix).
	 */
	public static final String CONFIG_LOCATION = "loader.config.location";

	/**
	 * Properties key for boolean flag (default false) which if set will cause the
	 * external configuration properties to be copied to System properties (assuming that
	 * is allowed by Java security).
	 */
	public static final String SET_SYSTEM_PROPERTIES = "loader.system";

	private static final Pattern WORD_SEPARATOR = Pattern.compile("\\W+");

	private final File home;

	private List<String> paths = new ArrayList<>();

	private final Properties properties = new Properties();

	private Archive parent;

	public PropertiesLauncher() {
		try {
			this.home = getHomeDirectory();
			initializeProperties();
			initializePaths();
			this.parent = createArchive();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	protected File getHomeDirectory() {
		try {
			return new File(getPropertyWithDefault(HOME, "${user.dir}"));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private void initializeProperties() throws Exception, IOException {
		List<String> configs = new ArrayList<>();
		if (getProperty(CONFIG_LOCATION) != null) {
			configs.add(getProperty(CONFIG_LOCATION));
		}
		else {
			String[] names = getPropertyWithDefault(CONFIG_NAME, "loader").split(",");
			for (String name : names) {
				configs.add("file:" + getHomeDirectory() + "/" + name + ".properties");
				configs.add("classpath:" + name + ".properties");
				configs.add("classpath:BOOT-INF/classes/" + name + ".properties");
			}
		}
		for (String config : configs) {
			try (InputStream resource = getResource(config)) {
				if (resource != null) {
					debug("Found: " + config);
					loadResource(resource);
					// Load the first one we find
					return;
				}
				else {
					debug("Not found: " + config);
				}
			}
		}
	}

	private void loadResource(InputStream resource) throws IOException, Exception {
		this.properties.load(resource);
		for (Object key : Collections.list(this.properties.propertyNames())) {
			String text = this.properties.getProperty((String) key);
			String value = SystemPropertyUtils.resolvePlaceholders(this.properties, text);
			if (value != null) {
				this.properties.put(key, value);
			}
		}
		if ("true".equals(getProperty(SET_SYSTEM_PROPERTIES))) {
			debug("Adding resolved properties to System properties");
			for (Object key : Collections.list(this.properties.propertyNames())) {
				String value = this.properties.getProperty((String) key);
				System.setProperty((String) key, value);
			}
		}
	}

	private InputStream getResource(String config) throws Exception {
		if (config.startsWith("classpath:")) {
			return getClasspathResource(config.substring("classpath:".length()));
		}
		config = stripFileUrlPrefix(config);
		if (isUrl(config)) {
			return getURLResource(config);
		}
		return getFileResource(config);
	}

	private String stripFileUrlPrefix(String config) {
		if (config.startsWith("file:")) {
			config = config.substring("file:".length());
			if (config.startsWith("//")) {
				config = config.substring(2);
			}
		}
		return config;
	}

	private boolean isUrl(String config) {
		return config.contains("://");
	}

	private InputStream getClasspathResource(String config) {
		while (config.startsWith("/")) {
			config = config.substring(1);
		}
		config = "/" + config;
		debug("Trying classpath: " + config);
		return getClass().getResourceAsStream(config);
	}

	private InputStream getFileResource(String config) throws Exception {
		File file = new File(config);
		debug("Trying file: " + config);
		if (file.canRead()) {
			return new FileInputStream(file);
		}
		return null;
	}

	private InputStream getURLResource(String config) throws Exception {
		URL url = new URL(config);
		if (exists(url)) {
			URLConnection con = url.openConnection();
			try {
				return con.getInputStream();
			}
			catch (IOException ex) {
				// Close the HTTP connection (if applicable).
				if (con instanceof HttpURLConnection) {
					((HttpURLConnection) con).disconnect();
				}
				throw ex;
			}
		}
		return null;
	}

	private boolean exists(URL url) throws IOException {
		// Try a URL connection content-length header...
		URLConnection connection = url.openConnection();
		try {
			connection.setUseCaches(
					connection.getClass().getSimpleName().startsWith("JNLP"));
			if (connection instanceof HttpURLConnection) {
				HttpURLConnection httpConnection = (HttpURLConnection) connection;
				httpConnection.setRequestMethod("HEAD");
				int responseCode = httpConnection.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_OK) {
					return true;
				}
				else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
					return false;
				}
			}
			return (connection.getContentLength() >= 0);
		}
		finally {
			if (connection instanceof HttpURLConnection) {
				((HttpURLConnection) connection).disconnect();
			}
		}
	}

	private void initializePaths() throws Exception {
		String path = getProperty(PATH);
		if (path != null) {
			this.paths = parsePathsProperty(path);
		}
		debug("Nested archive paths: " + this.paths);
	}

	private List<String> parsePathsProperty(String commaSeparatedPaths) {
		List<String> paths = new ArrayList<>();
		for (String path : commaSeparatedPaths.split(",")) {
			path = cleanupPath(path);
			// "" means the user wants root of archive but not current directory
			path = ("".equals(path) ? "/" : path);
			paths.add(path);
		}
		if (paths.isEmpty()) {
			paths.add("lib");
		}
		return paths;
	}

	protected String[] getArgs(String... args) throws Exception {
		String loaderArgs = getProperty(ARGS);
		if (loaderArgs != null) {
			String[] defaultArgs = loaderArgs.split("\\s+");
			String[] additionalArgs = args;
			args = new String[defaultArgs.length + additionalArgs.length];
			System.arraycopy(defaultArgs, 0, args, 0, defaultArgs.length);
			System.arraycopy(additionalArgs, 0, args, defaultArgs.length,
					additionalArgs.length);
		}
		return args;
	}

	@Override
	protected String getMainClass() throws Exception {
		String mainClass = getProperty(MAIN, "Start-Class");
		if (mainClass == null) {
			throw new IllegalStateException(
					"No '" + MAIN + "' or 'Start-Class' specified");
		}
		return mainClass;
	}

	@Override
	protected ClassLoader createClassLoader(List<Archive> archives) throws Exception {
		Set<URL> urls = new LinkedHashSet<>(archives.size());
		for (Archive archive : archives) {
			urls.add(archive.getUrl());
		}
		ClassLoader loader = new LaunchedURLClassLoader(urls.toArray(new URL[0]),
				getClass().getClassLoader());
		debug("Classpath: " + urls);
		String customLoaderClassName = getProperty("loader.classLoader");
		if (customLoaderClassName != null) {
			loader = wrapWithCustomClassLoader(loader, customLoaderClassName);
			debug("Using custom class loader: " + customLoaderClassName);
		}
		return loader;
	}

	@SuppressWarnings("unchecked")
	private ClassLoader wrapWithCustomClassLoader(ClassLoader parent,
			String loaderClassName) throws Exception {
		Class<ClassLoader> loaderClass = (Class<ClassLoader>) Class
				.forName(loaderClassName, true, parent);

		try {
			return loaderClass.getConstructor(ClassLoader.class).newInstance(parent);
		}
		catch (NoSuchMethodException ex) {
			// Ignore and try with URLs
		}
		try {
			return loaderClass.getConstructor(URL[].class, ClassLoader.class)
					.newInstance(new URL[0], parent);
		}
		catch (NoSuchMethodException ex) {
			// Ignore and try without any arguments
		}
		return loaderClass.newInstance();
	}

	private String getProperty(String propertyKey) throws Exception {
		return getProperty(propertyKey, null, null);
	}

	private String getProperty(String propertyKey, String manifestKey) throws Exception {
		return getProperty(propertyKey, manifestKey, null);
	}

	private String getPropertyWithDefault(String propertyKey, String defaultValue)
			throws Exception {
		return getProperty(propertyKey, null, defaultValue);
	}

	private String getProperty(String propertyKey, String manifestKey,
			String defaultValue) throws Exception {
		if (manifestKey == null) {
			manifestKey = propertyKey.replace('.', '-');
			manifestKey = toCamelCase(manifestKey);
		}
		String property = SystemPropertyUtils.getProperty(propertyKey);
		if (property != null) {
			String value = SystemPropertyUtils.resolvePlaceholders(this.properties,
					property);
			debug("Property '" + propertyKey + "' from environment: " + value);
			return value;
		}
		if (this.properties.containsKey(propertyKey)) {
			String value = SystemPropertyUtils.resolvePlaceholders(this.properties,
					this.properties.getProperty(propertyKey));
			debug("Property '" + propertyKey + "' from properties: " + value);
			return value;
		}
		try {
			if (this.home != null) {
				// Prefer home dir for MANIFEST if there is one
				Manifest manifest = new ExplodedArchive(this.home, false).getManifest();
				if (manifest != null) {
					String value = manifest.getMainAttributes().getValue(manifestKey);
					if (value != null) {
						debug("Property '" + manifestKey
								+ "' from home directory manifest: " + value);
						return SystemPropertyUtils.resolvePlaceholders(this.properties,
								value);
					}
				}
			}
		}
		catch (IllegalStateException ex) {
			// Ignore
		}
		// Otherwise try the parent archive
		Manifest manifest = createArchive().getManifest();
		if (manifest != null) {
			String value = manifest.getMainAttributes().getValue(manifestKey);
			if (value != null) {
				debug("Property '" + manifestKey + "' from archive manifest: " + value);
				return SystemPropertyUtils.resolvePlaceholders(this.properties, value);
			}
		}
		return defaultValue == null ? defaultValue
				: SystemPropertyUtils.resolvePlaceholders(this.properties, defaultValue);
	}

	@Override
	protected List<Archive> getClassPathArchives() throws Exception {
		List<Archive> lib = new ArrayList<>();
		for (String path : this.paths) {
			for (Archive archive : getClassPathArchives(path)) {
				if (archive instanceof ExplodedArchive) {
					List<Archive> nested = new ArrayList<>(
							archive.getNestedArchives(new ArchiveEntryFilter()));
					nested.add(0, archive);
					lib.addAll(nested);
				}
				else {
					lib.add(archive);
				}
			}
		}
		addNestedEntries(lib);
		return lib;
	}

	private List<Archive> getClassPathArchives(String path) throws Exception {
		String root = cleanupPath(stripFileUrlPrefix(path));
		List<Archive> lib = new ArrayList<>();
		File file = new File(root);
		if (!"/".equals(root)) {
			if (!isAbsolutePath(root)) {
				file = new File(this.home, root);
			}
			if (file.isDirectory()) {
				debug("Adding classpath entries from " + file);
				Archive archive = new ExplodedArchive(file, false);
				lib.add(archive);
			}
		}
		Archive archive = getArchive(file);
		if (archive != null) {
			debug("Adding classpath entries from archive " + archive.getUrl() + root);
			lib.add(archive);
		}
		List<Archive> nestedArchives = getNestedArchives(root);
		if (nestedArchives != null) {
			debug("Adding classpath entries from nested " + root);
			lib.addAll(nestedArchives);
		}
		return lib;
	}

	private boolean isAbsolutePath(String root) {
		// Windows contains ":" others start with "/"
		return root.contains(":") || root.startsWith("/");
	}

	private Archive getArchive(File file) throws IOException {
		String name = file.getName().toLowerCase();
		if (name.endsWith(".jar") || name.endsWith(".zip")) {
			return new JarFileArchive(file);
		}
		return null;
	}

	private List<Archive> getNestedArchives(String path) throws Exception {
		Archive parent = this.parent;
		String root = path;
		if (!root.equals("/") && root.startsWith("/")
				|| parent.getUrl().equals(this.home.toURI().toURL())) {
			// If home dir is same as parent archive, no need to add it twice.
			return null;
		}
		if (root.contains("!")) {
			int index = root.indexOf("!");
			File file = new File(this.home, root.substring(0, index));
			if (root.startsWith("jar:file:")) {
				file = new File(root.substring("jar:file:".length(), index));
			}
			parent = new JarFileArchive(file);
			root = root.substring(index + 1, root.length());
			while (root.startsWith("/")) {
				root = root.substring(1);
			}
		}
		if (root.endsWith(".jar")) {
			File file = new File(this.home, root);
			if (file.exists()) {
				parent = new JarFileArchive(file);
				root = "";
			}
		}
		if (root.equals("/") || root.equals("./") || root.equals(".")) {
			// The prefix for nested jars is actually empty if it's at the root
			root = "";
		}
		EntryFilter filter = new PrefixMatchingArchiveFilter(root);
		List<Archive> archives = new ArrayList<>(parent.getNestedArchives(filter));
		if (("".equals(root) || ".".equals(root)) && !path.endsWith(".jar")
				&& parent != this.parent) {
			// You can't find the root with an entry filter so it has to be added
			// explicitly. But don't add the root of the parent archive.
			archives.add(parent);
		}
		return archives;
	}

	private void addNestedEntries(List<Archive> lib) {
		// The parent archive might have "BOOT-INF/lib/" and "BOOT-INF/classes/"
		// directories, meaning we are running from an executable JAR. We add nested
		// entries from there with low priority (i.e. at end).
		try {
			lib.addAll(this.parent.getNestedArchives(new EntryFilter() {

				@Override
				public boolean matches(Entry entry) {
					if (entry.isDirectory()) {
						return entry.getName().equals(JarLauncher.BOOT_INF_CLASSES);
					}
					return entry.getName().startsWith(JarLauncher.BOOT_INF_LIB);
				}

			}));
		}
		catch (IOException ex) {
			// Ignore
		}
	}

	private String cleanupPath(String path) {
		path = path.trim();
		// No need for current dir path
		if (path.startsWith("./")) {
			path = path.substring(2);
		}
		if (path.toLowerCase().endsWith(".jar") || path.toLowerCase().endsWith(".zip")) {
			return path;
		}
		if (path.endsWith("/*")) {
			path = path.substring(0, path.length() - 1);
		}
		else {
			// It's a directory
			if (!path.endsWith("/") && !path.equals(".")) {
				path = path + "/";
			}
		}
		return path;
	}

	public static void main(String[] args) throws Exception {
		PropertiesLauncher launcher = new PropertiesLauncher();
		args = launcher.getArgs(args);
		launcher.launch(args);
	}

	public static String toCamelCase(CharSequence string) {
		if (string == null) {
			return null;
		}
		StringBuilder builder = new StringBuilder();
		Matcher matcher = WORD_SEPARATOR.matcher(string);
		int pos = 0;
		while (matcher.find()) {
			builder.append(capitalize(string.subSequence(pos, matcher.end()).toString()));
			pos = matcher.end();
		}
		builder.append(capitalize(string.subSequence(pos, string.length()).toString()));
		return builder.toString();
	}

	private static String capitalize(String str) {
		return Character.toUpperCase(str.charAt(0)) + str.substring(1);
	}

	private void debug(String message) {
		if (Boolean.getBoolean(DEBUG)) {
			System.out.println(message);
		}
	}

	/**
	 * Convenience class for finding nested archives that have a prefix in their file path
	 * (e.g. "lib/").
	 */
	private static final class PrefixMatchingArchiveFilter implements EntryFilter {

		private final String prefix;

		private final ArchiveEntryFilter filter = new ArchiveEntryFilter();

		private PrefixMatchingArchiveFilter(String prefix) {
			this.prefix = prefix;
		}

		@Override
		public boolean matches(Entry entry) {
			if (entry.isDirectory()) {
				return entry.getName().equals(this.prefix);
			}
			return entry.getName().startsWith(this.prefix) && this.filter.matches(entry);
		}

	}

	/**
	 * Convenience class for finding nested archives (archive entries that can be
	 * classpath entries).
	 */
	private static final class ArchiveEntryFilter implements EntryFilter {

		private static final String DOT_JAR = ".jar";

		private static final String DOT_ZIP = ".zip";

		@Override
		public boolean matches(Entry entry) {
			return entry.getName().endsWith(DOT_JAR) || entry.getName().endsWith(DOT_ZIP);
		}

	}

}
