/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.internal;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.Query;
import javax.persistence.SynchronizationType;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.spi.PersistenceUnitTransactionType;

import org.hibernate.AssertionFailure;
import org.hibernate.ConnectionAcquisitionMode;
import org.hibernate.ConnectionReleaseMode;
import org.hibernate.CustomEntityDirtinessStrategy;
import org.hibernate.EmptyInterceptor;
import org.hibernate.EntityNameResolver;
import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.MappingException;
import org.hibernate.MultiTenancyStrategy;
import org.hibernate.Session;
import org.hibernate.SessionBuilder;
import org.hibernate.SessionEventListener;
import org.hibernate.SessionFactory;
import org.hibernate.SessionFactoryObserver;
import org.hibernate.StatelessSession;
import org.hibernate.StatelessSessionBuilder;
import org.hibernate.TypeHelper;
import org.hibernate.boot.cfgxml.spi.CfgXmlAccessService;
import org.hibernate.boot.cfgxml.spi.LoadedConfig;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cfg.Environment;
import org.hibernate.cfg.Settings;
import org.hibernate.context.internal.JTASessionContext;
import org.hibernate.context.internal.ManagedSessionContext;
import org.hibernate.context.internal.ThreadLocalSessionContext;
import org.hibernate.context.spi.CurrentSessionContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.dialect.function.SQLFunctionRegistry;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.JdbcConnectionAccess;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.jndi.spi.JndiService;
import org.hibernate.engine.profile.Association;
import org.hibernate.engine.profile.Fetch;
import org.hibernate.engine.profile.FetchProfile;
import org.hibernate.engine.query.spi.QueryPlanCache;
import org.hibernate.engine.query.spi.ReturnMetadata;
import org.hibernate.engine.spi.CacheImplementor;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.engine.spi.SessionBuilderImplementor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionOwner;
import org.hibernate.engine.transaction.jta.platform.spi.JtaPlatform;
import org.hibernate.event.service.spi.EventListenerGroup;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.graph.spi.EntityGraphImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.UUIDGenerator;
import org.hibernate.id.factory.IdentifierGeneratorFactory;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.integrator.spi.IntegratorService;
import org.hibernate.internal.util.config.ConfigurationException;
import org.hibernate.jpa.internal.AfterCompletionActionLegacyJpaImpl;
import org.hibernate.jpa.internal.ExceptionMapperLegacyJpaImpl;
import org.hibernate.jpa.internal.ManagedFlushCheckerLegacyJpaImpl;
import org.hibernate.jpa.internal.PersistenceUnitUtilImpl;
import org.hibernate.mapping.RootClass;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.metadata.CollectionMetadata;
import org.hibernate.metamodel.internal.MetamodelImpl;
import org.hibernate.metamodel.spi.MetamodelImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.Loadable;
import org.hibernate.proxy.EntityNotFoundDelegate;
import org.hibernate.query.criteria.internal.CriteriaBuilderImpl;
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.resource.transaction.backend.jta.internal.synchronization.AfterCompletionAction;
import org.hibernate.resource.transaction.backend.jta.internal.synchronization.ExceptionMapper;
import org.hibernate.resource.transaction.backend.jta.internal.synchronization.ManagedFlushChecker;
import org.hibernate.secure.spi.GrantedPermission;
import org.hibernate.secure.spi.JaccPermissionDeclarations;
import org.hibernate.secure.spi.JaccService;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;
import org.hibernate.service.spi.SessionFactoryServiceRegistryFactory;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.hibernate.tool.schema.spi.DelayedDropAction;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.hibernate.tuple.entity.EntityTuplizer;
import org.hibernate.type.Type;
import org.hibernate.type.TypeResolver;

import org.jboss.logging.Logger;

import static org.hibernate.metamodel.internal.JpaMetaModelPopulationSetting.determineJpaMetaModelPopulationSetting;


/**
 * Concrete implementation of the <tt>SessionFactory</tt> interface. Has the following
 * responsibilities
 * <ul>
 * <li>caches configuration settings (immutably)
 * <li>caches "compiled" mappings ie. <tt>EntityPersister</tt>s and
 *     <tt>CollectionPersister</tt>s (immutable)
 * <li>caches "compiled" queries (memory sensitive cache)
 * <li>manages <tt>PreparedStatement</tt>s
 * <li> delegates JDBC <tt>Connection</tt> management to the <tt>ConnectionProvider</tt>
 * <li>factory for instances of <tt>SessionImpl</tt>
 * </ul>
 * This class must appear immutable to clients, even if it does all kinds of caching
 * and pooling under the covers. It is crucial that the class is not only thread
 * safe, but also highly concurrent. Synchronization must be used extremely sparingly.
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public final class SessionFactoryImpl implements SessionFactoryImplementor {
	private static final CoreMessageLogger LOG = CoreLogging.messageLogger( SessionFactoryImpl.class );

	private static final IdentifierGenerator UUID_GENERATOR = UUIDGenerator.buildSessionFactoryUniqueIdentifierGenerator();

	private final String name;
	private final String uuid;
	private transient boolean isClosed;

	private final transient SessionFactoryObserverChain observer = new SessionFactoryObserverChain();

	private final transient SessionFactoryOptions sessionFactoryOptions;
	private final transient Settings settings;
	private final transient Map<String,Object> properties;

	private final transient SessionFactoryServiceRegistry serviceRegistry;
	private transient JdbcServices jdbcServices;

	private final transient SQLFunctionRegistry sqlFunctionRegistry;

	// todo : org.hibernate.jpa.boot.spi.PersistenceUnitDescriptor too?

	private final transient MetamodelImpl metamodel;
	private final transient CriteriaBuilderImpl criteriaBuilder;
	private final transient PersistenceUnitUtilImpl persistenceUnitUtil;
	private final transient CacheImplementor cacheAccess;
	private final transient org.hibernate.query.spi.NamedQueryRepository namedQueryRepository;
	private final transient QueryPlanCache queryPlanCache;

	private final transient CurrentSessionContext currentSessionContext;

	private DelayedDropAction delayedDropAction;

	// todo : move to MetamodelImpl
	private final transient Map<String,IdentifierGenerator> identifierGenerators;
	private final transient Map<String, FilterDefinition> filters;
	private final transient Map<String, FetchProfile> fetchProfiles;
	private final transient ConcurrentMap<EntityNameResolver,Object> entityNameResolvers = new ConcurrentHashMap<>();

	private final transient TypeResolver typeResolver;
	private final transient TypeHelper typeHelper;


	public SessionFactoryImpl(final MetadataImplementor metadata, SessionFactoryOptions options) {
		LOG.debug( "Building session factory" );

		this.sessionFactoryOptions = options;
		this.settings = new Settings( options, metadata );

		this.serviceRegistry = options.getServiceRegistry()
				.getService( SessionFactoryServiceRegistryFactory.class )
				.buildServiceRegistry( this, options );

		final CfgXmlAccessService cfgXmlAccessService = serviceRegistry.getService( CfgXmlAccessService.class );

		String sfName = settings.getSessionFactoryName();
		if ( cfgXmlAccessService.getAggregatedConfig() != null ) {
			if ( sfName == null ) {
				sfName = cfgXmlAccessService.getAggregatedConfig().getSessionFactoryName();
			}
			applyCfgXmlValues( cfgXmlAccessService.getAggregatedConfig(), serviceRegistry );
		}

		this.name = sfName;
		try {
			uuid = (String) UUID_GENERATOR.generate(null, null);
		}
		catch (Exception e) {
			throw new AssertionFailure("Could not generate UUID");
		}

		final JdbcServices jdbcServices = serviceRegistry.getService( JdbcServices.class );

		this.properties = new HashMap<>();
		this.properties.putAll( serviceRegistry.getService( ConfigurationService.class ).getSettings() );

		this.sqlFunctionRegistry = new SQLFunctionRegistry( jdbcServices.getJdbcEnvironment().getDialect(), options.getCustomSqlFunctionMap() );
		this.cacheAccess = this.serviceRegistry.getService( CacheImplementor.class );
		this.metamodel = MetamodelImpl.buildMetamodel( metadata, this, determineJpaMetaModelPopulationSetting( properties ) );
		this.criteriaBuilder = new CriteriaBuilderImpl( this );
		this.persistenceUnitUtil = new PersistenceUnitUtilImpl( this );

		for ( SessionFactoryObserver sessionFactoryObserver : options.getSessionFactoryObservers() ) {
			this.observer.addObserver( sessionFactoryObserver );
		}

		this.typeResolver = metadata.getTypeResolver().scope( this );
		this.typeHelper = new TypeLocatorImpl( typeResolver );

		this.filters = new HashMap<>();
		this.filters.putAll( metadata.getFilterDefinitions() );

		LOG.debugf( "Session factory constructed with filter configurations : %s", filters );
		LOG.debugf( "Instantiating session factory with properties: %s", properties );

		this.queryPlanCache = new QueryPlanCache( this );

		class IntegratorObserver implements SessionFactoryObserver {
			private ArrayList<Integrator> integrators = new ArrayList<>();

			@Override
			public void sessionFactoryCreated(SessionFactory factory) {
			}

			@Override
			public void sessionFactoryClosed(SessionFactory factory) {
				for ( Integrator integrator : integrators ) {
					integrator.disintegrate( SessionFactoryImpl.this, SessionFactoryImpl.this.serviceRegistry );
				}
				integrators.clear();
			}
		}
		final IntegratorObserver integratorObserver = new IntegratorObserver();
		this.observer.addObserver( integratorObserver );
		for ( Integrator integrator : serviceRegistry.getService( IntegratorService.class ).getIntegrators() ) {
			integrator.integrate( metadata, this, this.serviceRegistry );
			integratorObserver.integrators.add( integrator );
		}

		//Generators:

		this.identifierGenerators = new HashMap<>();
		metadata.getEntityBindings().stream().filter( model -> !model.isInherited() ).forEach( model -> {
			IdentifierGenerator generator = model.getIdentifier().createIdentifierGenerator(
					metadata.getIdentifierGeneratorFactory(),
					jdbcServices.getJdbcEnvironment().getDialect(),
					settings.getDefaultCatalogName(),
					settings.getDefaultSchemaName(),
					(RootClass) model
			);
			identifierGenerators.put( model.getEntityName(), generator );
		} );


		//Named Queries:
		this.namedQueryRepository = metadata.buildNamedQueryRepository( this );


		LOG.debug( "Instantiated session factory" );

		settings.getMultiTableBulkIdStrategy().prepare(
				jdbcServices,
				buildLocalConnectionAccess(),
				metadata,
				sessionFactoryOptions
		);

		SchemaManagementToolCoordinator.process(
				metadata,
				serviceRegistry,
				properties,
				action -> SessionFactoryImpl.this.delayedDropAction = action
		);

		currentSessionContext = buildCurrentSessionContext();

		//checking for named queries
		if ( settings.isNamedQueryStartupCheckingEnabled() ) {
			final Map<String,HibernateException> errors = checkNamedQueries();
			if ( ! errors.isEmpty() ) {
				StringBuilder failingQueries = new StringBuilder( "Errors in named queries: " );
				String sep = "";
				for ( Map.Entry<String,HibernateException> entry : errors.entrySet() ) {
					LOG.namedQueryError( entry.getKey(), entry.getValue() );
					failingQueries.append( sep ).append( entry.getKey() );
					sep = ", ";
				}
				throw new HibernateException( failingQueries.toString() );
			}
		}

		// this needs to happen afterQuery persisters are all ready to go...
		this.fetchProfiles = new HashMap<>();
		for ( org.hibernate.mapping.FetchProfile mappingProfile : metadata.getFetchProfiles() ) {
			final FetchProfile fetchProfile = new FetchProfile( mappingProfile.getName() );
			for ( org.hibernate.mapping.FetchProfile.Fetch mappingFetch : mappingProfile.getFetches() ) {
				// resolve the persister owning the fetch
				final String entityName = metamodel.getImportedClassName( mappingFetch.getEntity() );
				final EntityPersister owner = entityName == null
						? null
						: metamodel.entityPersister( entityName );
				if ( owner == null ) {
					throw new HibernateException(
							"Unable to resolve entity reference [" + mappingFetch.getEntity()
									+ "] in fetch profile [" + fetchProfile.getName() + "]"
					);
				}

				// validate the specified association fetch
				Type associationType = owner.getPropertyType( mappingFetch.getAssociation() );
				if ( associationType == null || !associationType.isAssociationType() ) {
					throw new HibernateException( "Fetch profile [" + fetchProfile.getName() + "] specified an invalid association" );
				}

				// resolve the style
				final Fetch.Style fetchStyle = Fetch.Style.parse( mappingFetch.getStyle() );

				// then construct the fetch instance...
				fetchProfile.addFetch( new Association( owner, mappingFetch.getAssociation() ), fetchStyle );
				((Loadable) owner).registerAffectingFetchProfile( fetchProfile.getName() );
			}
			fetchProfiles.put( fetchProfile.getName(), fetchProfile );
		}

		this.observer.sessionFactoryCreated( this );

		SessionFactoryRegistry.INSTANCE.addSessionFactory(
				uuid,
				name,
				settings.isSessionFactoryNameAlsoJndiName(),
				this,
				serviceRegistry.getService( JndiService.class )
		);
	}

	private void applyCfgXmlValues(LoadedConfig aggregatedConfig, SessionFactoryServiceRegistry serviceRegistry) {
		final JaccService jaccService = serviceRegistry.getService( JaccService.class );
		if ( jaccService.getContextId() != null ) {
			final JaccPermissionDeclarations permissions = aggregatedConfig.getJaccPermissions( jaccService.getContextId() );
			if ( permissions != null ) {
				for ( GrantedPermission grantedPermission : permissions.getPermissionDeclarations() ) {
					jaccService.addPermission( grantedPermission );
				}
			}
		}

		if ( aggregatedConfig.getEventListenerMap() != null ) {
			final ClassLoaderService cls = serviceRegistry.getService( ClassLoaderService.class );
			final EventListenerRegistry eventListenerRegistry = serviceRegistry.getService( EventListenerRegistry.class );
			for ( Map.Entry<EventType, Set<String>> entry : aggregatedConfig.getEventListenerMap().entrySet() ) {
				final EventListenerGroup group = eventListenerRegistry.getEventListenerGroup( entry.getKey() );
				for ( String listenerClassName : entry.getValue() ) {
					try {
						group.appendListener( cls.classForName( listenerClassName ).newInstance() );
					}
					catch (Exception e) {
						throw new ConfigurationException( "Unable to instantiate event listener class : " + listenerClassName, e );
					}
				}
			}
		}
	}

	private JdbcConnectionAccess buildLocalConnectionAccess() {
		return new JdbcConnectionAccess() {
			@Override
			public Connection obtainConnection() throws SQLException {
				return settings.getMultiTenancyStrategy() == MultiTenancyStrategy.NONE
						? serviceRegistry.getService( ConnectionProvider.class ).getConnection()
						: serviceRegistry.getService( MultiTenantConnectionProvider.class ).getAnyConnection();
			}

			@Override
			public void releaseConnection(Connection connection) throws SQLException {
				if ( settings.getMultiTenancyStrategy() == MultiTenancyStrategy.NONE ) {
					serviceRegistry.getService( ConnectionProvider.class ).closeConnection( connection );
				}
				else {
					serviceRegistry.getService( MultiTenantConnectionProvider.class ).releaseAnyConnection( connection );
				}
			}

			@Override
			public boolean supportsAggressiveRelease() {
				return false;
			}
		};
	}

	public Session openSession() throws HibernateException {
		return withOptions().openSession();
	}

	public Session openTemporarySession() throws HibernateException {
		return withOptions()
				.autoClose( false )
				.flushBeforeCompletion( false )
				.connectionReleaseMode( ConnectionReleaseMode.AFTER_STATEMENT )
				.openSession();
	}

	public Session getCurrentSession() throws HibernateException {
		if ( currentSessionContext == null ) {
			throw new HibernateException( "No CurrentSessionContext configured!" );
		}
		return currentSessionContext.currentSession();
	}

	@Override
	public SessionBuilderImplementor withOptions() {
		return new SessionBuilderImpl( this );
	}

	@Override
	public StatelessSessionBuilder withStatelessOptions() {
		return new StatelessSessionBuilderImpl( this );
	}

	public StatelessSession openStatelessSession() {
		return withStatelessOptions().openStatelessSession();
	}

	public StatelessSession openStatelessSession(Connection connection) {
		return withStatelessOptions().connection( connection ).openStatelessSession();
	}

	@Override
	public void addObserver(SessionFactoryObserver observer) {
		this.observer.addObserver( observer );
	}

	@Override
	public Map<String, Object> getProperties() {
		validateNotClosed();
		return properties;
	}

	protected void validateNotClosed() {
		if ( isClosed ) {
			throw new IllegalStateException( "EntityManagerFactory is closed" );
		}
	}

	@Override
	public String getUuid() {
		return uuid;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public JdbcServices getJdbcServices() {
		if ( jdbcServices == null ) {
			jdbcServices = getServiceRegistry().getService( JdbcServices.class );
		}
		return jdbcServices;
	}

	public IdentifierGeneratorFactory getIdentifierGeneratorFactory() {
		return null;
	}

	public TypeResolver getTypeResolver() {
		return typeResolver;
	}

	private void registerEntityNameResolvers(EntityPersister persister) {
		if ( persister.getEntityMetamodel() == null || persister.getEntityMetamodel().getTuplizer() == null ) {
			return;
		}
		registerEntityNameResolvers( persister.getEntityMetamodel().getTuplizer() );
	}

	private void registerEntityNameResolvers(EntityTuplizer tuplizer) {
		EntityNameResolver[] resolvers = tuplizer.getEntityNameResolvers();
		if ( resolvers == null ) {
			return;
		}

		for ( EntityNameResolver resolver : resolvers ) {
			registerEntityNameResolver( resolver );
		}
	}

	private static final Object ENTITY_NAME_RESOLVER_MAP_VALUE = new Object();

	public void registerEntityNameResolver(EntityNameResolver resolver) {
		entityNameResolvers.put( resolver, ENTITY_NAME_RESOLVER_MAP_VALUE );
	}

	@Override
	public Iterable<EntityNameResolver> iterateEntityNameResolvers() {
		return entityNameResolvers.keySet();
	}

	public QueryPlanCache getQueryPlanCache() {
		return queryPlanCache;
	}

	private Map<String,HibernateException> checkNamedQueries() throws HibernateException {
		return namedQueryRepository.checkNamedQueries( queryPlanCache );
	}

	@Override
	public DeserializationResolver getDeserializationResolver() {
		return new DeserializationResolver() {
			@Override
			public SessionFactoryImplementor resolve() {
				return (SessionFactoryImplementor) SessionFactoryRegistry.INSTANCE.findSessionFactory( uuid, name );
			}
		};
	}

	@SuppressWarnings("deprecation")
	public Settings getSettings() {
		return settings;
	}

	@Override
	public <T> List<EntityGraph<? super T>> findEntityGraphsByType(Class<T> entityClass) {
		return null;
	}

	@Override
	public EntityManager createEntityManager() {
		return null;
	}

	@Override
	public EntityManager createEntityManager(Map map) {
		return null;
	}

	@Override
	public EntityManager createEntityManager(SynchronizationType synchronizationType) {
		return null;
	}

	@Override
	public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
		return null;
	}

	@Override
	public CriteriaBuilder getCriteriaBuilder() {
		return criteriaBuilder;
	}

	@Override
	public MetamodelImplementor getMetamodel() {
		return metamodel;
	}

	@Override
	public boolean isOpen() {
		return !isClosed;
	}

	@Override
	public EntityGraphImplementor findEntityGraphByName(String name) {
		return null;
	}

	@Override
	public Map getAllSecondLevelCacheRegions() {
		return null;
	}

	@Override
	public SessionFactoryOptions getSessionFactoryOptions() {
		return sessionFactoryOptions;
	}

	public Interceptor getInterceptor() {
		return sessionFactoryOptions.getInterceptor();
	}

	@Override
	public Reference getReference() {
		// from javax.naming.Referenceable
		LOG.debug( "Returning a Reference to the SessionFactory" );
		return new Reference(
				SessionFactoryImpl.class.getName(),
				new StringRefAddr("uuid", getUuid()),
				SessionFactoryRegistry.ObjectFactoryImpl.class.getName(),
				null
		);
	}

	@Override
	public org.hibernate.query.spi.NamedQueryRepository getNamedQueryRepository() {
		return namedQueryRepository;
	}


	public Type getIdentifierType(String className) throws MappingException {
		return getMetamodel().entityPersister( className ).getIdentifierType();
	}
	public String getIdentifierPropertyName(String className) throws MappingException {
		return getMetamodel().entityPersister( className ).getIdentifierPropertyName();
	}

	public Type[] getReturnTypes(String queryString) throws HibernateException {
		final ReturnMetadata metadata = queryPlanCache.getHQLQueryPlan( queryString, false, Collections.EMPTY_MAP )
				.getReturnMetadata();
		return metadata == null ? null : metadata.getReturnTypes();
	}

	public String[] getReturnAliases(String queryString) throws HibernateException {
		final ReturnMetadata metadata = queryPlanCache.getHQLQueryPlan( queryString, false, Collections.EMPTY_MAP )
				.getReturnMetadata();
		return metadata == null ? null : metadata.getReturnAliases();
	}

	public ClassMetadata getClassMetadata(Class persistentClass) throws HibernateException {
		return getClassMetadata( persistentClass.getName() );
	}

	public CollectionMetadata getCollectionMetadata(String roleName) throws HibernateException {
//		return collectionMetadata.get( roleName );
		return null;
	}

	public ClassMetadata getClassMetadata(String entityName) throws HibernateException {
//		return classMetadata.get( entityName );
		return null;
	}



	public Map<String,ClassMetadata> getAllClassMetadata() throws HibernateException {
//		return classMetadata;
		return null;
	}

	public Map getAllCollectionMetadata() throws HibernateException {
//		return collectionMetadata;
		return null;
	}

	public Type getReferencedPropertyType(String className, String propertyName)
		throws MappingException {
		return getMetamodel().entityPersister( className ).getPropertyType( propertyName );
	}

	/**
	 * Closes the session factory, releasing all held resources.
	 *
	 * <ol>
	 * <li>cleans up used cache regions and "stops" the cache provider.
	 * <li>close the JDBC connection
	 * <li>remove the JNDI binding
	 * </ol>
	 *
	 * Note: Be aware that the sessionFactory instance still can
	 * be a "heavy" object memory wise afterQuery close() has been called.  Thus
	 * it is important to not keep referencing the instance to let the garbage
	 * collector release the memory.
	 * @throws HibernateException
	 */
	public void close() throws HibernateException {
		if ( isClosed ) {
			LOG.trace( "Already closed" );
			return;
		}

		LOG.closing();

		isClosed = true;

		settings.getMultiTableBulkIdStrategy().release( serviceRegistry.getService( JdbcServices.class ), buildLocalConnectionAccess() );

		cacheAccess.close();
		metamodel.close();

		cacheAccess.close();

		queryPlanCache.cleanup();

		if ( delayedDropAction != null ) {
			delayedDropAction.perform( serviceRegistry );
		}

		SessionFactoryRegistry.INSTANCE.removeSessionFactory(
				uuid,
				name,
				settings.isSessionFactoryNameAlsoJndiName(),
				serviceRegistry.getService( JndiService.class )
		);

		observer.sessionFactoryClosed( this );
		serviceRegistry.destroy();
	}

	public CacheImplementor getCache() {
		return cacheAccess;
	}

	@Override
	public PersistenceUnitUtil getPersistenceUnitUtil() {
		return null;
	}

	@Override
	public void addNamedQuery(String name, Query query) {

	}

	@Override
	public <T> T unwrap(Class<T> cls) {
		return null;
	}

	@Override
	public <T> void addNamedEntityGraph(String graphName, EntityGraph<T> entityGraph) {

	}

	public boolean isClosed() {
		return isClosed;
	}

	public StatisticsImplementor getStatistics() {
		return serviceRegistry.getService( StatisticsImplementor.class );
	}

	public FilterDefinition getFilterDefinition(String filterName) throws HibernateException {
		FilterDefinition def = filters.get( filterName );
		if ( def == null ) {
			throw new HibernateException( "No such filter configured [" + filterName + "]" );
		}
		return def;
	}

	public boolean containsFetchProfileDefinition(String name) {
		return fetchProfiles.containsKey( name );
	}

	public Set getDefinedFilterNames() {
		return filters.keySet();
	}

	public IdentifierGenerator getIdentifierGenerator(String rootEntityName) {
		return identifierGenerators.get(rootEntityName);
	}

	private boolean canAccessTransactionManager() {
		try {
			return serviceRegistry.getService( JtaPlatform.class ).retrieveTransactionManager() != null;
		}
		catch (Exception e) {
			return false;
		}
	}

	private CurrentSessionContext buildCurrentSessionContext() {
		String impl = (String) properties.get( Environment.CURRENT_SESSION_CONTEXT_CLASS );
		// for backward-compatibility
		if ( impl == null ) {
			if ( canAccessTransactionManager() ) {
				impl = "jta";
			}
			else {
				return null;
			}
		}

		if ( "jta".equals( impl ) ) {
//			if ( ! transactionFactory().compatibleWithJtaSynchronization() ) {
//				LOG.autoFlushWillNotWork();
//			}
			return new JTASessionContext( this );
		}
		else if ( "thread".equals( impl ) ) {
			return new ThreadLocalSessionContext( this );
		}
		else if ( "managed".equals( impl ) ) {
			return new ManagedSessionContext( this );
		}
		else {
			try {
				Class implClass = serviceRegistry.getService( ClassLoaderService.class ).classForName( impl );
				return (CurrentSessionContext)
						implClass.getConstructor( new Class[] { SessionFactoryImplementor.class } )
						.newInstance( this );
			}
			catch( Throwable t ) {
				LOG.unableToConstructCurrentSessionContext( impl, t );
				return null;
			}
		}
	}

	@Override
	public ServiceRegistryImplementor getServiceRegistry() {
		return serviceRegistry;
	}

	@Override
	public EntityNotFoundDelegate getEntityNotFoundDelegate() {
		return sessionFactoryOptions.getEntityNotFoundDelegate();
	}

	public SQLFunctionRegistry getSqlFunctionRegistry() {
		return sqlFunctionRegistry;
	}

	public FetchProfile getFetchProfile(String name) {
		return fetchProfiles.get( name );
	}

	public TypeHelper getTypeHelper() {
		return typeHelper;
	}

	static class SessionBuilderImpl<T extends SessionBuilder> implements SessionBuilderImplementor<T>, SessionCreationOptions {
		private static final Logger log = CoreLogging.logger( SessionBuilderImpl.class );

		private final SessionFactoryImpl sessionFactory;
		private SessionOwner sessionOwner;
		private Interceptor interceptor;
		private StatementInspector statementInspector;
		private Connection connection;
		private PhysicalConnectionHandlingMode connectionHandlingMode;
		private boolean autoClose;
		private boolean autoJoinTransactions = true;
		private boolean flushBeforeCompletion;
		private String tenantIdentifier;
		private List<SessionEventListener> listeners;

		//todo : expose setting
		private SessionOwnerBehavior sessionOwnerBehavior = SessionOwnerBehavior.LEGACY_NATIVE;
		private PersistenceUnitTransactionType persistenceUnitTransactionType;

		SessionBuilderImpl(SessionFactoryImpl sessionFactory) {
			this.sessionFactory = sessionFactory;
			this.sessionOwner = null;

			// set up default builder values...
			this.statementInspector = sessionFactory.getSessionFactoryOptions().getStatementInspector();
			this.connectionHandlingMode = sessionFactory.getSessionFactoryOptions().getPhysicalConnectionHandlingMode();
			this.autoClose = sessionFactory.getSessionFactoryOptions().isAutoCloseSessionEnabled();
			this.flushBeforeCompletion = sessionFactory.getSessionFactoryOptions().isFlushBeforeCompletionEnabled();

			if ( sessionFactory.getCurrentTenantIdentifierResolver() != null ) {
				tenantIdentifier = sessionFactory.getCurrentTenantIdentifierResolver().resolveCurrentTenantIdentifier();
			}

			listeners = sessionFactory.getSessionFactoryOptions().getBaselineSessionEventsListenerBuilder().buildBaselineList();
		}


		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// SessionCreationOptions

		@Override
		public SessionOwner getSessionOwner() {
			return sessionOwner;
		}

		@Override
		public ExceptionMapper getExceptionMapper() {
			if ( sessionOwner != null ) {
				return sessionOwner.getExceptionMapper();
			}
			else {
				return sessionOwnerBehavior == SessionOwnerBehavior.LEGACY_JPA
						? ExceptionMapperLegacyJpaImpl.INSTANCE
						: null;
			}
		}

		@Override
		public AfterCompletionAction getAfterCompletionAction() {
			if ( sessionOwner != null ) {
				return sessionOwner.getAfterCompletionAction();
			}
			return sessionOwnerBehavior == SessionOwnerBehavior.LEGACY_JPA
					? AfterCompletionActionLegacyJpaImpl.INSTANCE
					: null;
		}

		@Override
		public ManagedFlushChecker getManagedFlushChecker() {
			if ( sessionOwner != null ) {
				return sessionOwner.getManagedFlushChecker();
			}
			return sessionOwnerBehavior == SessionOwnerBehavior.LEGACY_JPA
					? ManagedFlushCheckerLegacyJpaImpl.INSTANCE
					: null;
		}

		@Override
		public Connection getConnection() {
			return connection;
		}

		@Override
		public Interceptor getInterceptor() {
			if ( interceptor != null && interceptor != EmptyInterceptor.INSTANCE ) {
				return interceptor;
			}

			// prefer the SF-scoped interceptor, prefer that to any Session-scoped interceptor prototype
			if ( sessionFactory.getSessionFactoryOptions().getInterceptor() != null ) {
				return sessionFactory.getSessionFactoryOptions().getInterceptor();
			}

			if ( sessionFactory.getSessionFactoryOptions().getStatelessInterceptorImplementor() != null ) {
				try {
					return sessionFactory.getSessionFactoryOptions().getStatelessInterceptorImplementor().newInstance();
				}
				catch (InstantiationException | IllegalAccessException e) {
					throw new HibernateException( "Could not instantiate session-scoped SessionFactory Interceptor", e );
				}
			}

			return null;
		}

		@Override
		public StatementInspector getStatementInspector() {
			return statementInspector;
		}

		@Override
		public PhysicalConnectionHandlingMode getPhysicalConnectionHandlingMode() {
			return connectionHandlingMode;
		}

		@Override
		public String getTenantIdentifier() {
			return tenantIdentifier;
		}

		@Override
		public PersistenceUnitTransactionType getPersistenceUnitTransactionType() {
			return persistenceUnitTransactionType;
		}

		@Override
		public SynchronizationType getSynchronizationType() {
			return autoJoinTransactions ? SynchronizationType.SYNCHRONIZED : SynchronizationType.UNSYNCHRONIZED;
		}

		@Override
		public boolean isClearStateOnCloseEnabled() {
			return autoClose;
		}

		@Override
		public boolean isFlushBeforeCompletionEnabled() {
			return flushBeforeCompletion;
		}


		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// SessionBuilder

		@Override
		public Session openSession() {
			log.tracef( "Opening Hibernate Session.  tenant=%s, owner=%s", tenantIdentifier, sessionOwner );
			final SessionImpl session = new SessionImpl( sessionFactory, this );

			for ( SessionEventListener listener : listeners ) {
				session.getEventListenerManager().addListener( listener );
			}

			return session;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T owner(SessionOwner sessionOwner) {
			this.sessionOwner = sessionOwner;
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T interceptor(Interceptor interceptor) {
			this.interceptor = interceptor;
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T noInterceptor() {
			this.interceptor = EmptyInterceptor.INSTANCE;
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T statementInspector(StatementInspector statementInspector) {
			this.statementInspector = statementInspector;
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T connection(Connection connection) {
			this.connection = connection;
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T connectionReleaseMode(ConnectionReleaseMode connectionReleaseMode) {
			// NOTE : Legacy behavior (when only ConnectionReleaseMode was exposed) was to always acquire a
			// Connection using ConnectionAcquisitionMode.AS_NEEDED..

			final PhysicalConnectionHandlingMode handlingMode = PhysicalConnectionHandlingMode.interpret(
					ConnectionAcquisitionMode.AS_NEEDED,
					connectionReleaseMode
			);
			connectionHandlingMode( handlingMode );
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T connectionHandlingMode(PhysicalConnectionHandlingMode connectionHandlingMode) {
			this.connectionHandlingMode = connectionHandlingMode;
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T autoJoinTransactions(boolean autoJoinTransactions) {
			this.autoJoinTransactions = autoJoinTransactions;
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T autoClose(boolean autoClose) {
			this.autoClose = autoClose;
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T flushBeforeCompletion(boolean flushBeforeCompletion) {
			this.flushBeforeCompletion = flushBeforeCompletion;
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T tenantIdentifier(String tenantIdentifier) {
			this.tenantIdentifier = tenantIdentifier;
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T eventListeners(SessionEventListener... listeners) {
			Collections.addAll( this.listeners, listeners );
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T clearEventListeners() {
			listeners.clear();
			return (T) this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T persistenceUnitTransactionType(PersistenceUnitTransactionType persistenceUnitTransactionType) {
			this.persistenceUnitTransactionType = persistenceUnitTransactionType;
			return (T) this;
		}
	}

	public static class StatelessSessionBuilderImpl implements StatelessSessionBuilder, SessionCreationOptions {
		private final SessionFactoryImpl sessionFactory;
		private Connection connection;
		private String tenantIdentifier;

		public StatelessSessionBuilderImpl(SessionFactoryImpl sessionFactory) {
			this.sessionFactory = sessionFactory;

			if ( sessionFactory.getCurrentTenantIdentifierResolver() != null ) {
				tenantIdentifier = sessionFactory.getCurrentTenantIdentifierResolver().resolveCurrentTenantIdentifier();
			}
		}

		@Override
		public StatelessSession openStatelessSession() {
			return new StatelessSessionImpl( sessionFactory, this );
		}

		@Override
		public StatelessSessionBuilder connection(Connection connection) {
			this.connection = connection;
			return this;
		}

		@Override
		public StatelessSessionBuilder tenantIdentifier(String tenantIdentifier) {
			this.tenantIdentifier = tenantIdentifier;
			return this;
		}

		@Override
		public SessionOwner getSessionOwner() {
			return null;
		}

		@Override
		public ExceptionMapper getExceptionMapper() {
			return null;
		}

		@Override
		public AfterCompletionAction getAfterCompletionAction() {
			return null;
		}

		@Override
		public ManagedFlushChecker getManagedFlushChecker() {
			return null;
		}

		@Override
		public Connection getConnection() {
			return connection;
		}

		@Override
		public Interceptor getInterceptor() {
			// prefer the SF-scoped interceptor, prefer that to any Session-scoped interceptor prototype
			if ( sessionFactory.getSessionFactoryOptions().getInterceptor() != null ) {
				return sessionFactory.getSessionFactoryOptions().getInterceptor();
			}

			if ( sessionFactory.getSessionFactoryOptions().getStatelessInterceptorImplementor() != null ) {
				try {
					return sessionFactory.getSessionFactoryOptions().getStatelessInterceptorImplementor().newInstance();
				}
				catch (InstantiationException | IllegalAccessException e) {
					throw new HibernateException( "Could not instantiate session-scoped SessionFactory Interceptor", e );
				}
			}

			return null;
		}

		@Override
		public StatementInspector getStatementInspector() {
			return null;
		}

		@Override
		public PhysicalConnectionHandlingMode getPhysicalConnectionHandlingMode() {
			return null;
		}

		@Override
		public String getTenantIdentifier() {
			return tenantIdentifier;
		}

		@Override
		public PersistenceUnitTransactionType getPersistenceUnitTransactionType() {
			return null;
		}

		@Override
		public SynchronizationType getSynchronizationType() {
			return null;
		}

		@Override
		public boolean isClearStateOnCloseEnabled() {
			return false;
		}

		@Override
		public boolean isFlushBeforeCompletionEnabled() {
			return false;
		}
	}

	@Override
	public CustomEntityDirtinessStrategy getCustomEntityDirtinessStrategy() {
		return getSessionFactoryOptions().getCustomEntityDirtinessStrategy();
	}

	@Override
	public CurrentTenantIdentifierResolver getCurrentTenantIdentifierResolver() {
		return getSessionFactoryOptions().getCurrentTenantIdentifierResolver();
	}


	// Serialization handling ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Custom serialization hook defined by Java spec.  Used when the factory is directly serialized
	 *
	 * @param out The stream into which the object is being serialized.
	 *
	 * @throws IOException Can be thrown by the stream
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
		LOG.debugf( "Serializing: %s", uuid );
		out.defaultWriteObject();
		LOG.trace( "Serialized" );
	}

	/**
	 * Custom serialization hook defined by Java spec.  Used when the factory is directly deserialized
	 *
	 * @param in The stream from which the object is being deserialized.
	 *
	 * @throws IOException Can be thrown by the stream
	 * @throws ClassNotFoundException Again, can be thrown by the stream
	 */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		LOG.trace( "Deserializing" );
		in.defaultReadObject();
		LOG.debugf( "Deserialized: %s", uuid );
	}

	/**
	 * Custom serialization hook defined by Java spec.  Used when the factory is directly deserialized.
	 * Here we resolve the uuid/name read from the stream previously to resolve the SessionFactory
	 * instance to use based on the registrations with the {@link SessionFactoryRegistry}
	 *
	 * @return The resolved factory to use.
	 *
	 * @throws InvalidObjectException Thrown if we could not resolve the factory by uuid/name.
	 */
	private Object readResolve() throws InvalidObjectException {
		LOG.trace( "Resolving serialized SessionFactory" );
		return locateSessionFactoryOnDeserialization( uuid, name );
	}

	private static SessionFactory locateSessionFactoryOnDeserialization(String uuid, String name) throws InvalidObjectException{
		final SessionFactory uuidResult = SessionFactoryRegistry.INSTANCE.getSessionFactory( uuid );
		if ( uuidResult != null ) {
			LOG.debugf( "Resolved SessionFactory by UUID [%s]", uuid );
			return uuidResult;
		}

		// in case we were deserialized in a different JVM, look for an instance with the same name
		// (provided we were given a name)
		if ( name != null ) {
			final SessionFactory namedResult = SessionFactoryRegistry.INSTANCE.getNamedSessionFactory( name );
			if ( namedResult != null ) {
				LOG.debugf( "Resolved SessionFactory by name [%s]", name );
				return namedResult;
			}
		}

		throw new InvalidObjectException( "Could not find a SessionFactory [uuid=" + uuid + ",name=" + name + "]" );
	}

	/**
	 * Custom serialization hook used during Session serialization.
	 *
	 * @param oos The stream to which to write the factory
	 * @throws IOException Indicates problems writing out the serial data stream
	 */
	void serialize(ObjectOutputStream oos) throws IOException {
		oos.writeUTF( uuid );
		oos.writeBoolean( name != null );
		if ( name != null ) {
			oos.writeUTF( name );
		}
	}

	/**
	 * Custom deserialization hook used during Session deserialization.
	 *
	 * @param ois The stream from which to "read" the factory
	 * @return The deserialized factory
	 * @throws IOException indicates problems reading back serial data stream
	 * @throws ClassNotFoundException indicates problems reading back serial data stream
	 */
	static SessionFactoryImpl deserialize(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		LOG.trace( "Deserializing SessionFactory from Session" );
		final String uuid = ois.readUTF();
		boolean isNamed = ois.readBoolean();
		final String name = isNamed ? ois.readUTF() : null;
		return (SessionFactoryImpl) locateSessionFactoryOnDeserialization( uuid, name );
	}
}
