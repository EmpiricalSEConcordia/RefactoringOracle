/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2010, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.testing.junit.functional;

import static org.hibernate.aTestLogger.LOG;
import java.util.Collections;
import java.util.Map;
import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

/**
 * A specialized {@link junit.framework.TestSuite} implementation intended
 * for use as an aggregate for a single test class specifically for the purpose
 * of maintaing a single {@link org.hibernate.SessionFactory} for executings all
 * tests defined as part of the given functional test class.
 *
 * @author Steve Ebersole
 */
public class FunctionalTestClassTestSuite extends TestSuite {

	private ExecutionEnvironment.Settings settings;
	private ExecutionEnvironment environment;
	private Throwable environmentSetupError;
	private int testCount;
	private int testPosition;

	public FunctionalTestClassTestSuite(Class testClass, String name) {
		super( testClass, name );
	}

	public FunctionalTestClassTestSuite(Class testClass) {
		this( testClass, testClass.getName() );
	}


	@Override
    public void addTest(Test test) {
        LOG.trace("adding test [" + test + "]");
		if ( settings == null ) {
			if ( test instanceof ExecutionEnvironment.Settings ) {
				settings = ( ExecutionEnvironment.Settings ) test;
				// todo : we could also centralize the skipping of "database specific" tests here
				// instead of duplicating this notion in AllTests and DatabaseSpecificFunctionalTestCase.
				// as a test gets added, simply check to see if we should really add it via
				// DatabaseSpecificFunctionalTestCase.appliesTo( ExecutionEnvironment.DIALECT )...
			}
		}
		testCount++;
		super.addTest( test );
	}

	@Override
    public void run(TestResult testResult) {
		if ( testCount == 0 ) {
			// might be zero if database-specific...
			return;
		}
		try {
            LOG.info("Starting test-suite [" + getName() + "]");
			setUp();
			testPosition = 0;
			super.run( testResult );
		}
		finally {
			try {
				tearDown();
			}
			catch( Throwable ignore ) {
			}
            LOG.info("Completed test-suite [" + getName() + "]");
		}
	}

	@Override
    public void runTest(Test test, TestResult testResult) {
		testPosition++;
		if ( environmentSetupError != null ) {
			testResult.startTest( test );
			testResult.addError( test, environmentSetupError );
			testResult.endTest( test );
			return;
		}
		if ( ! ( test instanceof FunctionalTestCase ) ) {
			super.runTest( test, testResult );
		}
		else {
			FunctionalTestCase functionalTest = ( ( FunctionalTestCase ) test );
			try {
				// disallow rebuilding the schema because this is the last test
				// in this suite, thus it is about to get dropped immediately
				// afterwards anyway...
				environment.setAllowRebuild( testPosition < testCount );
				functionalTest.setEnvironment( environment );
				super.runTest( functionalTest, testResult );
			}
			finally {
				functionalTest.setEnvironment( null );
			}
		}
	}

	protected void setUp() {
		if ( settings == null ) {
			return;
		}
        LOG.info("Building aggregated execution environment");
		try {
			environment = new ExecutionEnvironment( settings );
			environment.initialize( getConnectionProviderInjectionProperties() );
		}
		catch( Throwable t ) {
			environmentSetupError = t;
		}
	}

	protected Map getConnectionProviderInjectionProperties() {
		return Collections.EMPTY_MAP;
	}

	protected void tearDown() {
		if ( environment != null ) {
            LOG.info("Destroying aggregated execution environment");
			environment.complete();
			this.environment = null;
		}
	}
}
