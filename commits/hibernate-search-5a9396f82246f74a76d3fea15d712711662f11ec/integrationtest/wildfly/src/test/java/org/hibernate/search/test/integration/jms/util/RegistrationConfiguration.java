/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.test.integration.jms.util;

import java.io.File;

import org.hibernate.search.util.logging.impl.Log;

/**
 * @author Davide D'Alto
 * @author Sanne Grinovero
 * @author Hardy Ferentschik
 */
public final class RegistrationConfiguration {

	private static final Log log = org.hibernate.search.util.logging.impl.LoggerFactory.make();
	public static final String DESTINATION_QUEUE = "jms/queue/hsearch";
	private static final int MAX_ATTEMPTS = 3;

	private RegistrationConfiguration() {
		//not allowed
	}

	public static File createTempDir() {
		int attempts = 0;
		File baseDir = new File( System.getProperty( "java.io.tmpdir" ) );
		do {
			attempts++;
			String baseName = System.currentTimeMillis() + "_" + attempts;
			File tempDir = new File( baseDir, baseName );
			if ( tempDir.mkdir() ) {
				log.info( "Created tmp directory: " + tempDir.getAbsolutePath() );
				tempDir.deleteOnExit(); // delete the JVM exit, this way we don't have to bother about it
				return tempDir;
			}
		} while ( attempts < MAX_ATTEMPTS );

		throw new RuntimeException( "Impossible to create folder directory for indexes" );
	}
}
