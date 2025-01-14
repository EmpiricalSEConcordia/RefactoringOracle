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
import java.sql.Blob;
import java.sql.Clob;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.cfg.Mappings;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.service.jdbc.connections.internal.ConnectionProviderInitiator;
import org.hibernate.service.spi.ServicesRegistry;
import org.hibernate.test.common.ServiceRegistryHolder;

/**
 * {@inheritDoc}
 *
 * @author Steve Ebersole
 */
public class ExecutionEnvironment {

	public static final Dialect DIALECT = Dialect.getDialect();

	private final ExecutionEnvironment.Settings settings;

	private Map conectionProviderInjectionProperties;
	private ServiceRegistryHolder serviceRegistryHolder;
	private Configuration configuration;
	private SessionFactory sessionFactory;
	private boolean allowRebuild;

	public ExecutionEnvironment(ExecutionEnvironment.Settings settings) {
		this.settings = settings;
	}

	public boolean isAllowRebuild() {
		return allowRebuild;
	}

	public void setAllowRebuild(boolean allowRebuild) {
		this.allowRebuild = allowRebuild;
	}

	public Dialect getDialect() {
		return DIALECT;
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public ServicesRegistry getServiceRegistry() {
		return serviceRegistryHolder.getServiceRegistry();
	}

	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	public void initialize(Map conectionProviderInjectionProperties) {
		if ( sessionFactory != null ) {
			throw new IllegalStateException( "attempt to initialize already initialized ExecutionEnvironment" );
		}
		if ( ! settings.appliesTo( getDialect() ) ) {
			return;
		}

		this.conectionProviderInjectionProperties = conectionProviderInjectionProperties;
		Configuration configuration = new Configuration();
		configuration.setProperty( Environment.CACHE_PROVIDER, "org.hibernate.cache.HashtableCacheProvider" );

		settings.configure( configuration );

		if ( settings.createSchema() ) {
			configuration.setProperty( Environment.HBM2DDL_AUTO, "create-drop" );
		}

		// make sure we use the same dialect...
		configuration.setProperty( Environment.DIALECT, getDialect().getClass().getName() );

		applyMappings( configuration );
		configuration.buildMappings();

		applyCacheSettings( configuration );
		settings.afterConfigurationBuilt( configuration.createMappings(), getDialect() );

		this.configuration = configuration;

		serviceRegistryHolder = new ServiceRegistryHolder( getServiceRegistryProperties() );
		sessionFactory = configuration.buildSessionFactory( serviceRegistryHolder.getServiceRegistry() );

		settings.afterSessionFactoryBuilt( ( SessionFactoryImplementor ) sessionFactory );
	}

	private Map getServiceRegistryProperties() {
		Map serviceRegistryProperties = configuration.getProperties();
		if ( conectionProviderInjectionProperties != null && conectionProviderInjectionProperties.size() > 0 ) {
			serviceRegistryProperties = new HashMap(
					configuration.getProperties().size() + conectionProviderInjectionProperties.size()
			);
			serviceRegistryProperties.putAll( configuration.getProperties() );
			serviceRegistryProperties.put(
					ConnectionProviderInitiator.INJECTION_DATA, conectionProviderInjectionProperties
			);
		}
		return serviceRegistryProperties;
	}

	private void applyMappings(Configuration configuration) {
		String[] mappings = settings.getMappings();
		for ( String mapping : mappings ) {
			configuration.addResource(
					settings.getBaseForMappings() + mapping,
					ExecutionEnvironment.class.getClassLoader()
			);
		}
	}

	private void applyCacheSettings(Configuration configuration) {
		if ( settings.getCacheConcurrencyStrategy() != null ) {
			Iterator iter = configuration.getClassMappings();
			while ( iter.hasNext() ) {
				PersistentClass clazz = (PersistentClass) iter.next();
				Iterator props = clazz.getPropertyClosureIterator();
				boolean hasLob = false;
				while ( props.hasNext() ) {
					Property prop = (Property) props.next();
					if ( prop.getValue().isSimpleValue() ) {
						String type = ( ( SimpleValue ) prop.getValue() ).getTypeName();
						if ( "blob".equals(type) || "clob".equals(type) ) {
							hasLob = true;
						}
						if ( Blob.class.getName().equals(type) || Clob.class.getName().equals(type) ) {
							hasLob = true;
						}
					}
				}
				if ( !hasLob && !clazz.isInherited() && settings.overrideCacheStrategy() ) {
					configuration.setCacheConcurrencyStrategy( clazz.getEntityName(), settings.getCacheConcurrencyStrategy() );
				}
			}
			iter = configuration.getCollectionMappings();
			while ( iter.hasNext() ) {
				Collection coll = (Collection) iter.next();
				configuration.setCollectionCacheConcurrencyStrategy( coll.getRole(), settings.getCacheConcurrencyStrategy() );
			}
		}
	}

	public void rebuild() {
		if ( !allowRebuild ) {
			return;
		}
		if ( sessionFactory != null ) {
			sessionFactory.close();
			sessionFactory = null;
		}
		if ( serviceRegistryHolder != null ) {
			serviceRegistryHolder.destroy();
			serviceRegistryHolder = null;
		}
		serviceRegistryHolder = new ServiceRegistryHolder( getServiceRegistryProperties() );
		sessionFactory = configuration.buildSessionFactory( serviceRegistryHolder.getServiceRegistry() );
		settings.afterSessionFactoryBuilt( ( SessionFactoryImplementor ) sessionFactory );
	}

	public void complete() {
		if ( sessionFactory != null ) {
			sessionFactory.close();
			sessionFactory = null;
		}
		if ( serviceRegistryHolder != null ) {
			serviceRegistryHolder.destroy();
			serviceRegistryHolder = null;
		}
		configuration = null;
	}

	public static interface Settings {
		public String[] getMappings();
		public String getBaseForMappings();
		public boolean createSchema();
		public boolean recreateSchemaAfterFailure();
		public void configure(Configuration cfg);
		public boolean overrideCacheStrategy();
		public String getCacheConcurrencyStrategy();
		public void afterSessionFactoryBuilt(SessionFactoryImplementor sfi);
		public void afterConfigurationBuilt(Mappings mappings, Dialect dialect);
		public boolean appliesTo(Dialect dialect);
	}
}
