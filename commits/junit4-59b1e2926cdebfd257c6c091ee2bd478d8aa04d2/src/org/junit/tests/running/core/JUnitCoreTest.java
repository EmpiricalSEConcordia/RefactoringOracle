package org.junit.tests.running.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class JUnitCoreTest {
	
	static public class Fail {
		@Test public void kaboom() {
			fail();
		}
	}
	
	@Test public void failureCausesExitCodeOf1() throws Exception {
		runClass("org.junit.tests.JUnitCoreTest$Fail", 1);
	}

	@Test public void missingClassCausesExitCodeOf1() throws Exception {
		runClass("Foo", 1);
	}

	static public class Succeed {
		@Test public void peacefulSilence() {
		}
	}
	
	@Test public void successCausesExitCodeOf0() throws Exception {
		runClass("org.junit.tests.running.core.JUnitCoreTest$Succeed", 0);
	}

	private void runClass(String className, int returnCode) throws IOException, InterruptedException {
		String java= System.getProperty("java.home")+File.separator+"bin"+File.separator+"java";
		String classPath= getClass().getClassLoader().getResource(".").getFile() + File.pathSeparator + System.getProperty("java.class.path");
		String [] cmd= { java, "-cp", classPath, "org.junit.runner.JUnitCore", className}; 
		Process process= Runtime.getRuntime().exec(cmd);
		InputStream input= process.getInputStream();
		while((input.read()) != -1); 
		assertEquals(returnCode, process.waitFor());
	}
}
