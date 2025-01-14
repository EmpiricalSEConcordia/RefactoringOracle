package org.junit.tests.experimental.rules;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.rules.MethodRule;
import org.junit.experimental.rules.Rule;
import org.junit.experimental.rules.Timeout;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

public class TimeoutRuleTest {
	public static class HasGlobalTimeout {
		public static String log;
		
		@Rule public MethodRule globalTimeout = new Timeout(20);
		
		@Test public void testInfiniteLoop1() {
			log+= "ran1";
			for(;;) {}
		}
		
		@Test public void testInfiniteLoop2() {
			log+= "ran2";
			for(;;) {}
		}
	}
	
	@Ignore("For gump, for now")
	@Test(timeout=100) public void globalTimeoutAvoidsInfiniteLoop() {
		HasGlobalTimeout.log = "";
		Result result= JUnitCore.runClasses(HasGlobalTimeout.class);
		assertEquals(2, result.getFailureCount());
		assertThat(HasGlobalTimeout.log, containsString("ran1"));
		assertThat(HasGlobalTimeout.log, containsString("ran2"));
	}
}
