/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.spi;

import java.io.Serializable;

import org.hibernate.HibernateException;
import org.hibernate.Incubating;
import org.hibernate.SessionFactory;
import org.hibernate.SessionFactoryObserver;
import org.hibernate.boot.cfgxml.spi.CfgXmlAccessService;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.internal.SessionFactoryRegistry;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.TypeFactory;
import org.hibernate.type.TypeResolver;

import org.jboss.logging.Logger;

import static org.hibernate.internal.CoreLogging.logger;
import static org.hibernate.internal.CoreLogging.messageLogger;

/**
 * Defines a set of available Type instances as isolated from other configurations.  The
 * isolation is defined by each instance of a TypeConfiguration.
 * <p/>
 * Note that each Type is inherently "scoped" to a TypeConfiguration.  We only ever access
 * a Type through a TypeConfiguration - specifically the TypeConfiguration in effect for
 * the current persistence unit.
 * <p/>
 * Even though each Type instance is scoped to a TypeConfiguration, Types do not inherently
 * have access to that TypeConfiguration (mainly because Type is an extension contract - meaning
 * that Hibernate does not manage the full set of Types available in ever TypeConfiguration).
 * However Types will often want access to the TypeConfiguration, which can be achieved by the
 * Type simply implementing the {@link TypeConfigurationAware} interface.
 *
 * @author Steve Ebersole
 *
 * @since 6.0
 */
@Incubating
public class TypeConfiguration implements SessionFactoryObserver, Serializable {
	private static final CoreMessageLogger log = messageLogger( Scope.class );

	// todo : (
	private final Scope scope;
	private final TypeFactory typeFactory;
	private boolean initialized = false;

	private final BasicTypeRegistry basicTypeRegistry;

	// temporarily needed to support deprecations
	private final TypeResolver typeResolver;

	public TypeConfiguration() {
		this.scope = new Scope( );
		basicTypeRegistry = new BasicTypeRegistry();
		typeFactory = new TypeFactory( this );
		typeResolver = new TypeResolver( this, typeFactory );
		this.initialized = true;
	}

	/**
	 * Temporarily needed to support deprecations
	 * @deprecated
	 */
	public TypeResolver getTypeResolver(){
		return typeResolver;
	}

	public BasicTypeRegistry getBasicTypeRegistry() {
		return basicTypeRegistry;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Scoping

	/**
	 * Obtain the MetadataBuildingContext currently scoping the
	 * TypeConfiguration.
	 *
	 * @apiNote This will throw an exception if the SessionFactory is not yet
	 * bound here.  See {@link Scope} for more details regarding the stages
	 * a TypeConfiguration goes through
	 *
	 * @return
	 */
	public MetadataBuildingContext getMetadataBuildingContext() {
		return scope.getMetadataBuildingContext();
	}

	public void scope(MetadataBuildingContext metadataBuildingContext) {
		log.debugf( "Scoping TypeConfiguration [%s] to MetadataBuildingContext [%s]", this, metadataBuildingContext );
		scope.setMetadataBuildingContext( metadataBuildingContext );
	}

	public void scope(SessionFactoryImpl sessionFactory) {
		log.debugf( "Scoping TypeConfiguration [%s] to SessionFactoryImpl [%s]", this, sessionFactory );
		scope.setSessionFactory( sessionFactory );
		typeFactory.injectSessionFactory( sessionFactory );
	}

	/**
	 * Obtain the SessionFactory currently scoping the TypeConfiguration.
	 *
	 * @apiNote This will throw an exception if the SessionFactory is not yet
	 * bound here.  See {@link Scope} for more details regarding the stages
	 * a TypeConfiguration goes through (this is "runtime stage")
	 *
	 * @return The SessionFactory
	 *
	 * @throws IllegalStateException if the TypeConfiguration is currently not
	 * associated with a SessionFactory (in "runtime stage").
	 */
	public SessionFactoryImplementor getSessionFactory() {
		return scope.getSessionFactory();
	}

	@Override
	public void sessionFactoryCreated(SessionFactory factory) {
		// Instead of allowing scope#setSessionFactory to influence this, we use the SessionFactoryObserver callback
		// to handle this, allowing any SessionFactory constructor code to be able to continue to have access to the
		// MetadataBuildingContext through TypeConfiguration until this callback is fired.
		log.tracef( "Handling #sessionFactoryCreated from [%s] for TypeConfiguration", factory );
		scope.setMetadataBuildingContext( null );
	}

	@Override
	public void sessionFactoryClosed(SessionFactory factory) {
		log.tracef( "Handling #sessionFactoryClosed from [%s] for TypeConfiguration", factory );
		scope.unsetSessionFactory( factory );

		// todo (6.0) : finish this
		//		release Database, descriptor Maps, etc... things that are only
		// 		valid while the TypeConfiguration is scoped to SessionFactory
	}

	/**
	 * Encapsulation of lifecycle concerns for a TypeConfiguration, mainly in
	 * regards to eventually being associated with a SessionFactory.  Goes
	 * 3 "lifecycle" stages, pertaining to {@link #getMetadataBuildingContext()}
	 * and {@link #getSessionFactory()}:
	 *
	 * 		* "Initialization" is where the {@link TypeConfiguration} is first
	 * 			built as the "boot model" ({@link org.hibernate.boot.model}) of
	 * 			the user's domain model is converted into the "runtime model"
	 * 			({@link org.hibernate.metamodel.model}).  During this phase,
	 * 			{@link #getMetadataBuildingContext()} will be accessible but
	 * 			{@link #getSessionFactory} will throw an exception.
	 * 		* "Runtime" is where the "runtime model" is accessible while the
	 * 			SessionFactory is still unclosed.  During this phase
	 * 			{@link #getSessionFactory()} is accessible while
	 * 			{@link #getMetadataBuildingContext()} will now throw an
	 * 			exception
	 * 		* "Sunset" is after the SessionFactory has been closed.  During this
	 * 			phase both {@link #getSessionFactory()} and
	 * 			{@link #getMetadataBuildingContext()} will now throw an exception
	 *
	 * Each stage or phase is consider a "scope" for the TypeConfiguration.
	 */
	private static class Scope implements Serializable {

		// todo (6.0) : consider a proper contract implemented by both SessionFactory (or its metamodel) and boot's MetadataImplementor
		//		1) type-related info from MetadataBuildingOptions
		//		2) ServiceRegistry
		private transient MetadataBuildingContext metadataBuildingContext;
		private transient SessionFactoryImplementor sessionFactory;

		private String sessionFactoryName;
		private String sessionFactoryUuid;

		public MetadataBuildingContext getMetadataBuildingContext() {
			if ( metadataBuildingContext == null ) {
				throw new HibernateException( "TypeConfiguration is not currently scoped to MetadataBuildingContext" );
			}
			return metadataBuildingContext;
		}

		public void setMetadataBuildingContext(MetadataBuildingContext metadataBuildingContext) {
			this.metadataBuildingContext = metadataBuildingContext;
		}

		public SessionFactoryImplementor getSessionFactory() {
			if ( sessionFactory == null ) {
				if ( sessionFactoryName == null && sessionFactoryUuid == null ) {
					throw new HibernateException( "TypeConfiguration was not yet scoped to SessionFactory" );
				}
				sessionFactory = (SessionFactoryImplementor) SessionFactoryRegistry.INSTANCE.findSessionFactory(
						sessionFactoryUuid,
						sessionFactoryName
				);
				if ( sessionFactory == null ) {
					throw new HibernateException(
							"Could not find a SessionFactory [uuid=" + sessionFactoryUuid + ",name=" + sessionFactoryName + "]"
					);
				}

			}

			return sessionFactory;
		}

		/**
		 * Used by TypeFactory scoping.
		 *
		 * @param factory The SessionFactory that the TypeFactory is being bound to
		 */
		void setSessionFactory(SessionFactoryImplementor factory) {
			if ( this.sessionFactory != null ) {
				log.scopingTypesToSessionFactoryAfterAlreadyScoped( this.sessionFactory, factory );
			}
			else {
				this.sessionFactoryUuid = factory.getUuid();
				String sfName = factory.getSessionFactoryOptions().getSessionFactoryName();
				if ( sfName == null ) {
					final CfgXmlAccessService cfgXmlAccessService = factory.getServiceRegistry()
							.getService( CfgXmlAccessService.class );
					if ( cfgXmlAccessService.getAggregatedConfig() != null ) {
						sfName = cfgXmlAccessService.getAggregatedConfig().getSessionFactoryName();
					}
				}
				this.sessionFactoryName = sfName;
			}
			this.sessionFactory = factory;
		}

		public void unsetSessionFactory(SessionFactory factory) {
			log.debugf( "Un-scoping TypeConfiguration [%s] from SessionFactory [%s]", this, factory );
			this.sessionFactory = null;
		}
	}
}
