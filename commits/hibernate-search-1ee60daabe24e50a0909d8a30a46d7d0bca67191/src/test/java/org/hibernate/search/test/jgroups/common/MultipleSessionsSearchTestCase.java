// $Id$
package org.hibernate.search.test.jgroups.common;

import java.io.InputStream;

import org.slf4j.Logger;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Configuration;
import org.hibernate.classic.Session;
import org.hibernate.dialect.Dialect;
import org.hibernate.search.test.SearchTestCase;
import org.hibernate.search.util.FileHelper;

/**
 * Test class to simulate clustered environment (one master, and one slave node)
 *
 * @author Lukasz Moren
 */
public abstract class MultipleSessionsSearchTestCase extends SearchTestCase {

	private static final Logger log = org.hibernate.search.util.LoggerFactory.make();

	private String masterCopy = "/master/copy";

	/**
	 * The lucene index directory which is specific to the master node.
	 */
	private String masterMain = "/master/main";

	/**
	 * The lucene index directory which is specific to the slave node.
	 */
	private String slave = "/slave";


	protected static SessionFactory slaveSessionFactory;

	/**
	 * Common configuration for all slave nodes
	 */
	private Configuration commonCfg;

	@Override
	protected void configure(Configuration cfg) {
		super.configure( cfg );

		//master
		cfg.setProperty( "hibernate.search.default.sourceBase", getIndexDir().getAbsolutePath() + masterCopy );
		cfg.setProperty( "hibernate.search.default.indexBase", getIndexDir().getAbsolutePath() + masterMain );
		cfg.setProperty( "hibernate.search.default.refresh", "1" );
		cfg.setProperty(
				"hibernate.search.default.directory_provider", "org.hibernate.search.store.FSMasterDirectoryProvider"
		);
	}

	protected void commonConfigure(Configuration cfg) {
		super.configure( cfg );

		//slave(s)
		cfg.setProperty( "hibernate.search.default.sourceBase", getIndexDir().getAbsolutePath() + masterCopy );
		cfg.setProperty( "hibernate.search.default.indexBase", getIndexDir().getAbsolutePath() + slave );
		cfg.setProperty( "hibernate.search.default.refresh", "1" );
		cfg.setProperty(
				"hibernate.search.default.directory_provider", "org.hibernate.search.store.FSSlaveDirectoryProvider"
		);
	}

	@Override
	protected void setUp() throws Exception {
		if ( getIndexDir().exists() ) {
			FileHelper.delete( getIndexDir() );
		}
		super.setUp();
		buildCommonSessionFactory( getCommonMappings(), getCommonAnnotatedPackages(), getCommonXmlFiles() );
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();

		//close session factories and clean index files
		if ( slaveSessionFactory != null ) {
			slaveSessionFactory.close();
		}
		if ( getSessions() != null ) {
			getSessions().close();
		}
		log.info( "Deleting test directory {} ", getIndexDir().getAbsolutePath() );
		FileHelper.delete( getIndexDir() );
	}

	private void buildCommonSessionFactory(Class<?>[] classes, String[] packages, String[] xmlFiles) throws Exception {
		try {
			if ( getSlaveSessionFactory() != null ) {
				getSlaveSessionFactory().close();
			}

			setCommonCfg( new AnnotationConfiguration() );
			commonConfigure( commonCfg );
			if ( recreateSchema() ) {
				commonCfg.setProperty( org.hibernate.cfg.Environment.HBM2DDL_AUTO, "create-drop" );
			}
			for ( String aPackage : packages ) {
				( ( AnnotationConfiguration ) getCommonConfiguration() ).addPackage( aPackage );
			}
			for ( Class<?> aClass : classes ) {
				( ( AnnotationConfiguration ) getCommonConfiguration() ).addAnnotatedClass( aClass );
			}
			for ( String xmlFile : xmlFiles ) {
				InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream( xmlFile );
				getCommonConfiguration().addInputStream( is );
			}
			setDialect( Dialect.getDialect() );
			slaveSessionFactory = getCommonConfiguration().buildSessionFactory();
		}
		catch ( Exception e ) {
			e.printStackTrace();
			throw e;
		}
	}

	private void setCommonCfg(Configuration configuration) {
		this.commonCfg = configuration;
	}

	protected Configuration getCommonConfiguration() {
		return commonCfg;
	}

	protected Session getSlaveSession() {
		return slaveSessionFactory.openSession();
	}

	protected static SessionFactory getSlaveSessionFactory() {
		return slaveSessionFactory;
	}

	private String[] getCommonAnnotatedPackages() {
		return new String[] { };
	}

	private String[] getCommonXmlFiles() {
		return new String[] { };
	}

	protected abstract Class<?>[] getMappings();

	protected abstract Class<?>[] getCommonMappings();
}
