/*
 * Copyright 2012-2016 the original author or authors.
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

package org.springframework.boot.devtools.tests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit {@link TestRule} that launched a JVM and redirects its output to a test
 * method-specific location.
 *
 * @author Andy Wilkinson
 */
class JvmLauncher implements TestRule {

	private File outputDirectory;

	@Override
	public Statement apply(Statement base, Description description) {
		this.outputDirectory = new File("target/output/"
				+ description.getMethodName().replaceAll("[^A-Za-z]+", ""));
		this.outputDirectory.mkdirs();
		return base;
	}

	LaunchedJvm launch(String name, String classpath, String... args) throws IOException {
		List<String> command = new ArrayList<>(Arrays
				.asList(System.getProperty("java.home") + "/bin/java", "-cp", classpath));
		command.addAll(Arrays.asList(args));
		File standardOut = new File(this.outputDirectory, name + ".out");
		Process process = new ProcessBuilder(command.toArray(new String[command.size()]))
				.redirectError(new File(this.outputDirectory, name + ".err"))
				.redirectOutput(standardOut).start();
		return new LaunchedJvm(process, standardOut);
	}

	static class LaunchedJvm {

		private final Process process;

		private final File standardOut;

		LaunchedJvm(Process process, File standardOut) {
			this.process = process;
			this.standardOut = standardOut;
		}

		Process getProcess() {
			return this.process;
		}

		File getStandardOut() {
			return this.standardOut;
		}

	}

}
