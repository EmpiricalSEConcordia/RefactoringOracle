/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.boot.loader.tools;

import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.loader.tools.sample.ClassWithMainMethod;
import org.springframework.boot.loader.tools.sample.ClassWithoutMainMethod;
import org.springframework.util.FileCopyUtils;
import org.zeroturnaround.zip.ZipUtil;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link Repackager}.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 */
public class RepackagerTests {

	private static final Libraries NO_LIBRARIES = new Libraries() {
		@Override
		public void doWithLibraries(LibraryCallback callback) throws IOException {
		}
	};

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private TestJarFile testJarFile;

	@Before
	public void setup() throws IOException {
		this.testJarFile = new TestJarFile(this.temporaryFolder);
	}

	@Test
	public void nullSource() throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		new Repackager(null);
	}

	@Test
	public void missingSource() throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		new Repackager(new File("missing"));
	}

	@Test
	public void directorySource() throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		new Repackager(this.temporaryFolder.getRoot());
	}

	@Test
	public void specificMainClass() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithoutMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		repackager.setMainClass("a.b.C");
		repackager.repackage(NO_LIBRARIES);
		Manifest actualManifest = getManifest(file);
		assertThat(actualManifest.getMainAttributes().getValue("Main-Class"),
				equalTo("org.springframework.boot.loader.JarLauncher"));
		assertThat(actualManifest.getMainAttributes().getValue("Start-Class"),
				equalTo("a.b.C"));
		assertThat(hasLauncherClasses(file), equalTo(true));
	}

	@Test
	public void mainClassFromManifest() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithoutMainMethod.class);
		Manifest manifest = new Manifest();
		manifest = new Manifest();
		manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
		manifest.getMainAttributes().putValue("Main-Class", "a.b.C");
		this.testJarFile.addManifest(manifest);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		repackager.repackage(NO_LIBRARIES);
		Manifest actualManifest = getManifest(file);
		assertThat(actualManifest.getMainAttributes().getValue("Main-Class"),
				equalTo("org.springframework.boot.loader.JarLauncher"));
		assertThat(actualManifest.getMainAttributes().getValue("Start-Class"),
				equalTo("a.b.C"));
		assertThat(hasLauncherClasses(file), equalTo(true));
	}

	@Test
	public void mainClassFound() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		repackager.repackage(NO_LIBRARIES);
		Manifest actualManifest = getManifest(file);
		assertThat(actualManifest.getMainAttributes().getValue("Main-Class"),
				equalTo("org.springframework.boot.loader.JarLauncher"));
		assertThat(actualManifest.getMainAttributes().getValue("Start-Class"),
				equalTo("a.b.C"));
		assertThat(hasLauncherClasses(file), equalTo(true));
	}

	@Test
	public void jarIsOnlyRepackagedOnce() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		repackager.repackage(NO_LIBRARIES);
		repackager.repackage(NO_LIBRARIES);
		Manifest actualManifest = getManifest(file);
		assertThat(actualManifest.getMainAttributes().getValue("Main-Class"),
				equalTo("org.springframework.boot.loader.JarLauncher"));
		assertThat(actualManifest.getMainAttributes().getValue("Start-Class"),
				equalTo("a.b.C"));
		assertThat(hasLauncherClasses(file), equalTo(true));
	}

	@Test
	public void multipleMainClassFound() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		this.testJarFile.addClass("a/b/D.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		this.thrown.expect(IllegalStateException.class);
		this.thrown.expectMessage("Unable to find a single main class "
				+ "from the following candidates [a.b.C, a.b.D]");
		repackager.repackage(NO_LIBRARIES);
	}

	@Test
	public void noMainClass() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithoutMainMethod.class);
		this.thrown.expect(IllegalStateException.class);
		this.thrown.expectMessage("Unable to find main class");
		new Repackager(this.testJarFile.getFile()).repackage(NO_LIBRARIES);
	}

	@Test
	public void noMainClassAndLayoutIsNone() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		repackager.setLayout(new Layouts.None());
		repackager.repackage(file, NO_LIBRARIES);
		Manifest actualManifest = getManifest(file);
		assertThat(actualManifest.getMainAttributes().getValue("Main-Class"),
				equalTo("a.b.C"));
		assertThat(hasLauncherClasses(file), equalTo(false));
	}

	@Test
	public void noMainClassAndLayoutIsNoneWithNoMain() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithoutMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		repackager.setLayout(new Layouts.None());
		repackager.repackage(file, NO_LIBRARIES);
		Manifest actualManifest = getManifest(file);
		assertThat(actualManifest.getMainAttributes().getValue("Main-Class"),
				equalTo(null));
		assertThat(hasLauncherClasses(file), equalTo(false));
	}

	@Test
	public void sameSourceAndDestinationWithBackup() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		repackager.repackage(NO_LIBRARIES);
		assertThat(new File(file.getParent(), file.getName() + ".original").exists(),
				equalTo(true));
		assertThat(hasLauncherClasses(file), equalTo(true));
	}

	@Test
	public void sameSourceAndDestinationWithoutBackup() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		repackager.setBackupSource(false);
		repackager.repackage(NO_LIBRARIES);
		assertThat(new File(file.getParent(), file.getName() + ".original").exists(),
				equalTo(false));
		assertThat(hasLauncherClasses(file), equalTo(true));
	}

	@Test
	public void differentDestination() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		File source = this.testJarFile.getFile();
		File dest = this.temporaryFolder.newFile("different.jar");
		Repackager repackager = new Repackager(source);
		repackager.repackage(dest, NO_LIBRARIES);
		assertThat(new File(source.getParent(), source.getName() + ".original").exists(),
				equalTo(false));
		assertThat(hasLauncherClasses(source), equalTo(false));
		assertThat(hasLauncherClasses(dest), equalTo(true));

	}

	@Test
	public void nullDestination() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		Repackager repackager = new Repackager(this.testJarFile.getFile());
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("Invalid destination");
		repackager.repackage(null, NO_LIBRARIES);
	}

	@Test
	public void destinationIsDirectory() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		Repackager repackager = new Repackager(this.testJarFile.getFile());
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("Invalid destination");
		repackager.repackage(this.temporaryFolder.getRoot(), NO_LIBRARIES);
	}

	@Test
	public void overwriteDestination() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		Repackager repackager = new Repackager(this.testJarFile.getFile());
		File dest = this.temporaryFolder.newFile("dest.jar");
		dest.createNewFile();
		repackager.repackage(dest, NO_LIBRARIES);
		assertThat(hasLauncherClasses(dest), equalTo(true));
	}

	@Test
	public void nullLibraries() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("Libraries must not be null");
		repackager.repackage(file, null);
	}

	@Test
	public void libraries() throws Exception {
		TestJarFile libJar = new TestJarFile(this.temporaryFolder);
		libJar.addClass("a/b/C.class", ClassWithoutMainMethod.class);
		final File libJarFile = libJar.getFile();
		final File libJarFileToUnpack = libJar.getFile();
		final File libNonJarFile = this.temporaryFolder.newFile();
		FileCopyUtils.copy(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 }, libNonJarFile);
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		this.testJarFile.addFile("lib/" + libJarFileToUnpack.getName(),
				libJarFileToUnpack);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		repackager.repackage(new Libraries() {
			@Override
			public void doWithLibraries(LibraryCallback callback) throws IOException {
				callback.library(new Library(libJarFile, LibraryScope.COMPILE));
				callback.library(new Library(libJarFileToUnpack, LibraryScope.COMPILE,
						true));
				callback.library(new Library(libNonJarFile, LibraryScope.COMPILE));
			}
		});
		assertThat(hasEntry(file, "lib/" + libJarFile.getName()), equalTo(true));
		assertThat(hasEntry(file, "lib/" + libJarFileToUnpack.getName()), equalTo(true));
		assertThat(hasEntry(file, "lib/" + libNonJarFile.getName()), equalTo(false));
		JarEntry entry = getEntry(file, "lib/" + libJarFileToUnpack.getName());
		assertThat(entry.getComment(), startsWith("UNPACK:"));
		assertThat(entry.getComment().length(), equalTo(47));
	}

	@Test
	public void duplicateLibraries() throws Exception {
		TestJarFile libJar = new TestJarFile(this.temporaryFolder);
		libJar.addClass("a/b/C.class", ClassWithoutMainMethod.class);
		final File libJarFile = libJar.getFile();
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		this.thrown.expect(IllegalStateException.class);
		this.thrown.expectMessage("Duplicate library");
		repackager.repackage(new Libraries() {
			@Override
			public void doWithLibraries(LibraryCallback callback) throws IOException {
				callback.library(new Library(libJarFile, LibraryScope.COMPILE, false));
				callback.library(new Library(libJarFile, LibraryScope.COMPILE, false));
			}
		});
	}

	@Test
	public void customLayout() throws Exception {
		TestJarFile libJar = new TestJarFile(this.temporaryFolder);
		libJar.addClass("a/b/C.class", ClassWithoutMainMethod.class);
		final File libJarFile = libJar.getFile();
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		Layout layout = mock(Layout.class);
		final LibraryScope scope = mock(LibraryScope.class);
		given(layout.getLauncherClassName()).willReturn("testLauncher");
		given(layout.getLibraryDestination(anyString(), eq(scope))).willReturn("test/");
		repackager.setLayout(layout);
		repackager.repackage(new Libraries() {
			@Override
			public void doWithLibraries(LibraryCallback callback) throws IOException {
				callback.library(new Library(libJarFile, scope));
			}
		});
		assertThat(hasEntry(file, "test/" + libJarFile.getName()), equalTo(true));
		assertThat(getManifest(file).getMainAttributes().getValue("Main-Class"),
				equalTo("testLauncher"));
	}

	@Test
	public void springBootVersion() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		repackager.repackage(NO_LIBRARIES);
		Manifest actualManifest = getManifest(file);
		assertThat(
				actualManifest.getMainAttributes().containsKey(
						new Attributes.Name("Spring-Boot-Version")), equalTo(true));
	}

	@Test
	public void nullCustomLayout() throws Exception {
		this.testJarFile.addClass("a/b/C.class", ClassWithoutMainMethod.class);
		Repackager repackager = new Repackager(this.testJarFile.getFile());
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("Layout must not be null");
		repackager.setLayout(null);
	}

	@Test
	public void dontRecompressZips() throws Exception {
		TestJarFile nested = new TestJarFile(this.temporaryFolder);
		nested.addClass("a/b/C.class", ClassWithoutMainMethod.class);
		final File nestedFile = nested.getFile();
		this.testJarFile.addFile("test/nested.jar", nestedFile);
		this.testJarFile.addClass("A.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		repackager.repackage(new Libraries() {
			@Override
			public void doWithLibraries(LibraryCallback callback) throws IOException {
				callback.library(new Library(nestedFile, LibraryScope.COMPILE));
			}
		});

		JarFile jarFile = new JarFile(file);
		try {
			assertThat(jarFile.getEntry("lib/" + nestedFile.getName()).getMethod(),
					equalTo(ZipEntry.STORED));
			assertThat(jarFile.getEntry("test/nested.jar").getMethod(),
					equalTo(ZipEntry.STORED));
		}
		finally {
			jarFile.close();
		}
	}

	@Test
	public void unpackLibrariesTakePrecedenceOverExistingSourceEntries() throws Exception {
		TestJarFile nested = new TestJarFile(this.temporaryFolder);
		nested.addClass("a/b/C.class", ClassWithoutMainMethod.class);
		final File nestedFile = nested.getFile();
		this.testJarFile.addFile("lib/" + nestedFile.getName(), nested.getFile());
		this.testJarFile.addClass("A.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		repackager.repackage(new Libraries() {

			@Override
			public void doWithLibraries(LibraryCallback callback) throws IOException {
				callback.library(new Library(nestedFile, LibraryScope.COMPILE, true));
			}

		});
		JarFile jarFile = new JarFile(file);
		try {
			assertThat(jarFile.getEntry("lib/" + nestedFile.getName()).getComment(),
					startsWith("UNPACK:"));
		}
		finally {
			jarFile.close();
		}
	}

	@Test
	public void existingSourceEntriesTakePrecedenceOverStandardLibraries()
			throws Exception {
		TestJarFile nested = new TestJarFile(this.temporaryFolder);
		nested.addClass("a/b/C.class", ClassWithoutMainMethod.class);
		final File nestedFile = nested.getFile();
		this.testJarFile.addFile("lib/" + nestedFile.getName(), nested.getFile());
		this.testJarFile.addClass("A.class", ClassWithMainMethod.class);
		File file = this.testJarFile.getFile();
		Repackager repackager = new Repackager(file);
		long sourceLength = nestedFile.length();
		repackager.repackage(new Libraries() {

			@Override
			public void doWithLibraries(LibraryCallback callback) throws IOException {
				nestedFile.delete();
				File toZip = RepackagerTests.this.temporaryFolder.newFile();
				ZipUtil.packEntry(toZip, nestedFile);
				callback.library(new Library(nestedFile, LibraryScope.COMPILE));
			}

		});
		JarFile jarFile = new JarFile(file);
		try {
			assertThat(jarFile.getEntry("lib/" + nestedFile.getName()).getSize(),
					equalTo(sourceLength));
		}
		finally {
			jarFile.close();
		}
	}

	private boolean hasLauncherClasses(File file) throws IOException {
		return hasEntry(file, "org/springframework/boot/")
				&& hasEntry(file, "org/springframework/boot/loader/JarLauncher.class");
	}

	private boolean hasEntry(File file, String name) throws IOException {
		return getEntry(file, name) != null;
	}

	private JarEntry getEntry(File file, String name) throws IOException {
		JarFile jarFile = new JarFile(file);
		try {
			return jarFile.getJarEntry(name);
		}
		finally {
			jarFile.close();
		}
	}

	private Manifest getManifest(File file) throws IOException {
		JarFile jarFile = new JarFile(file);
		try {
			return jarFile.getManifest();
		}
		finally {
			jarFile.close();
		}
	}

}
