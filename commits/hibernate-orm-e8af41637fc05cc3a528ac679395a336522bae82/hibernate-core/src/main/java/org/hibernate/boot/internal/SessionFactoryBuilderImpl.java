/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2014, Red Hat Inc. or third-party contributors as
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
package org.hibernate.boot.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.ConnectionReleaseMode;
import org.hibernate.CustomEntityDirtinessStrategy;
import org.hibernate.EmptyInterceptor;
import org.hibernate.EntityMode;
import org.hibernate.EntityNameResolver;
import org.hibernate.Interceptor;
import org.hibernate.MultiTenancyStrategy;
import org.hibernate.NullPrecedence;
import org.hibernate.SessionEventListener;
import org.hibernate.SessionFactory;
import org.hibernate.SessionFactoryObserver;
import org.hibernate.boot.SchemaAutoTooling;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.boot.spi.SessionFactoryBuilderImplementor;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.internal.StandardQueryCacheFactory;
import org.hibernate.cache.spi.QueryCacheFactory;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.BaselineSessionEventsListenerBuilder;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.dialect.function.SQLFunction;
import org.hibernate.engine.config.internal.ConfigurationServiceImpl;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.jdbc.env.spi.ExtractedDatabaseMetaData;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.transaction.spi.TransactionFactory;
import org.hibernate.hql.spi.MultiTableBulkIdStrategy;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.loader.BatchFetchStyle;
import org.hibernate.proxy.EntityNotFoundDelegate;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.tuple.entity.EntityTuplizer;
import org.hibernate.tuple.entity.EntityTuplizerFactory;

import org.jboss.logging.Logger;

import static org.hibernate.cfg.AvailableSettings.AUTO_CLOSE_SESSION;
import static org.hibernate.cfg.AvailableSettings.AUTO_EVICT_COLLECTION_CACHE;
import static org.hibernate.cfg.AvailableSettings.AUTO_SESSION_EVENTS_LISTENER;
import static org.hibernate.cfg.AvailableSettings.BATCH_FETCH_STYLE;
import static org.hibernate.cfg.AvailableSettings.BATCH_VERSIONED_DATA;
import static org.hibernate.cfg.AvailableSettings.CACHE_REGION_PREFIX;
import static org.hibernate.cfg.AvailableSettings.CHECK_NULLABILITY;
import static org.hibernate.cfg.AvailableSettings.CUSTOM_ENTITY_DIRTINESS_STRATEGY;
import static org.hibernate.cfg.AvailableSettings.DEFAULT_BATCH_FETCH_SIZE;
import static org.hibernate.cfg.AvailableSettings.DEFAULT_ENTITY_MODE;
import static org.hibernate.cfg.AvailableSettings.ENABLE_LAZY_LOAD_NO_TRANS;
import static org.hibernate.cfg.AvailableSettings.FLUSH_BEFORE_COMPLETION;
import static org.hibernate.cfg.AvailableSettings.GENERATE_STATISTICS;
import static org.hibernate.cfg.AvailableSettings.HQL_BULK_ID_STRATEGY;
import static org.hibernate.cfg.AvailableSettings.INTERCEPTOR;
import static org.hibernate.cfg.AvailableSettings.JPAQL_STRICT_COMPLIANCE;
import static org.hibernate.cfg.AvailableSettings.JTA_TRACK_BY_THREAD;
import static org.hibernate.cfg.AvailableSettings.LOG_SESSION_METRICS;
import static org.hibernate.cfg.AvailableSettings.MAX_FETCH_DEPTH;
import static org.hibernate.cfg.AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER;
import static org.hibernate.cfg.AvailableSettings.ORDER_INSERTS;
import static org.hibernate.cfg.AvailableSettings.ORDER_UPDATES;
import static org.hibernate.cfg.AvailableSettings.QUERY_CACHE_FACTORY;
import static org.hibernate.cfg.AvailableSettings.QUERY_STARTUP_CHECKING;
import static org.hibernate.cfg.AvailableSettings.QUERY_SUBSTITUTIONS;
import static org.hibernate.cfg.AvailableSettings.RELEASE_CONNECTIONS;
import static org.hibernate.cfg.AvailableSettings.SESSION_FACTORY_NAME;
import static org.hibernate.cfg.AvailableSettings.SESSION_FACTORY_NAME_IS_JNDI;
import static org.hibernate.cfg.AvailableSettings.STATEMENT_BATCH_SIZE;
import static org.hibernate.cfg.AvailableSettings.STATEMENT_FETCH_SIZE;
import static org.hibernate.cfg.AvailableSettings.USE_DIRECT_REFERENCE_CACHE_ENTRIES;
import static org.hibernate.cfg.AvailableSettings.USE_GET_GENERATED_KEYS;
import static org.hibernate.cfg.AvailableSettings.USE_IDENTIFIER_ROLLBACK;
import static org.hibernate.cfg.AvailableSettings.USE_MINIMAL_PUTS;
import static org.hibernate.cfg.AvailableSettings.USE_QUERY_CACHE;
import static org.hibernate.cfg.AvailableSettings.USE_SCROLLABLE_RESULTSET;
import static org.hibernate.cfg.AvailableSettings.USE_SECOND_LEVEL_CACHE;
import static org.hibernate.cfg.AvailableSettings.USE_SQL_COMMENTS;
import static org.hibernate.cfg.AvailableSettings.USE_STRUCTURED_CACHE;
import static org.hibernate.cfg.AvailableSettings.WRAP_RESULT_SETS;
import static org.hibernate.engine.config.spi.StandardConverters.BOOLEAN;

/**
 * @author Gail Badner
 * @author Steve Ebersole
 */
public class SessionFactoryBuilderImpl implements SessionFactoryBuilderImplementor, SessionFactoryOptionsState {
	private static final Logger log = Logger.getLogger( SessionFactoryBuilderImpl.class );

	private final MetadataImplementor metadata;
	private final StandardServiceRegistry serviceRegistry;

	// integration
	private Object beanManagerReference;
	private Object validatorFactoryReference;

	// SessionFactory behavior
	private String sessionFactoryName;
	private boolean sessionFactoryNameAlsoJndiName;

	// Session behavior
	private boolean flushBeforeCompletionEnabled;
	private boolean autoCloseSessionEnabled;

	// Statistics/Interceptor/observers
	private boolean statisticsEnabled;
	private Interceptor interceptor;
	private List<SessionFactoryObserver> sessionFactoryObserverList = new ArrayList<SessionFactoryObserver>();
	private BaselineSessionEventsListenerBuilder baselineSessionEventsListenerBuilder;	// not exposed on builder atm

	// persistence behavior
	private CustomEntityDirtinessStrategy customEntityDirtinessStrategy;
	private List<EntityNameResolver> entityNameResolvers = new ArrayList<EntityNameResolver>();
	private EntityNotFoundDelegate entityNotFoundDelegate;
	private boolean identifierRollbackEnabled;
	private EntityMode defaultEntityMode;
	private EntityTuplizerFactory entityTuplizerFactory = new EntityTuplizerFactory();
	private boolean checkNullability;
	private boolean initializeLazyStateOutsideTransactions;
	private MultiTableBulkIdStrategy multiTableBulkIdStrategy;
	private BatchFetchStyle batchFetchStyle;
	private int defaultBatchFetchSize;
	private Integer maximumFetchDepth;
	private NullPrecedence defaultNullPrecedence;
	private boolean orderUpdatesEnabled;
	private boolean orderInsertsEnabled;

	// multi-tenancy
	private MultiTenancyStrategy multiTenancyStrategy;
	private CurrentTenantIdentifierResolver currentTenantIdentifierResolver;

	// JTA timeout detection
	private boolean jtaTrackByThread;

	// Queries
	private Map querySubstitutions;
	private boolean strictJpaQueryLanguageCompliance;
	private boolean namedQueryStartupCheckingEnabled;

	// Caching
	private boolean secondLevelCacheEnabled;
	private boolean queryCacheEnabled;
	private QueryCacheFactory queryCacheFactory;
	private String cacheRegionPrefix;
	private boolean minimalPutsEnabled;
	private boolean structuredCacheEntriesEnabled;
	private boolean directReferenceCacheEntriesEnabled;
	private boolean autoEvictCollectionCache;

	// Schema tooling
	private SchemaAutoTooling schemaAutoTooling;

	// JDBC Handling
	private boolean dataDefinitionImplicitCommit;			// not exposed on builder atm
	private boolean dataDefinitionInTransactionSupported;	// not exposed on builder atm
	private boolean getGeneratedKeysEnabled;
	private int jdbcBatchSize;
	private boolean jdbcBatchVersionedData;
	private Integer jdbcFetchSize;
	private boolean scrollableResultSetsEnabled;
	private boolean commentsEnabled;
	private ConnectionReleaseMode connectionReleaseMode;
	private boolean wrapResultSetsEnabled;

	private Map<String, SQLFunction> sqlFunctions;

	SessionFactoryBuilderImpl(MetadataImplementor metadata) {
		this.metadata = metadata;
		this.serviceRegistry = metadata.getMetadataBuildingOptions().getServiceRegistry();

		initializeState();
	}

	private void initializeState() {
		final StrategySelector strategySelector = serviceRegistry.getService( StrategySelector.class );
		ConfigurationService cfgService = serviceRegistry.getService( ConfigurationService.class );
		final JdbcServices jdbcServices = serviceRegistry.getService( JdbcServices.class );

		final Map configurationSettings = new HashMap();
		//noinspection unchecked
		configurationSettings.putAll( cfgService.getSettings() );
		//noinspection unchecked
		configurationSettings.putAll( jdbcServices.getJdbcEnvironment().getDialect().getDefaultProperties() );
		cfgService = new ConfigurationServiceImpl( configurationSettings );
		( (ConfigurationServiceImpl) cfgService ).injectServices( (ServiceRegistryImplementor) serviceRegistry );

		this.beanManagerReference = configurationSettings.get( "javax.persistence.bean.manager" );
		this.validatorFactoryReference = configurationSettings.get( "javax.persistence.validation.factory" );

		this.sessionFactoryName = (String) configurationSettings.get( SESSION_FACTORY_NAME );
		this.sessionFactoryNameAlsoJndiName = cfgService.getSetting(
				SESSION_FACTORY_NAME_IS_JNDI,
				BOOLEAN,
				true
		);

		this.flushBeforeCompletionEnabled = cfgService.getSetting( FLUSH_BEFORE_COMPLETION, BOOLEAN, false );
		this.autoCloseSessionEnabled = cfgService.getSetting( AUTO_CLOSE_SESSION, BOOLEAN, false );

		this.statisticsEnabled = cfgService.getSetting( GENERATE_STATISTICS, BOOLEAN, false );
		this.interceptor = strategySelector.resolveDefaultableStrategy(
				Interceptor.class,
				configurationSettings.get( INTERCEPTOR ),
				EmptyInterceptor.INSTANCE
		);
		// todo : expose this from builder?
		final String autoSessionEventsListenerName = (String) configurationSettings.get(
				AUTO_SESSION_EVENTS_LISTENER
		);
		final Class<? extends SessionEventListener> autoSessionEventsListener = autoSessionEventsListenerName == null
				? null
				: strategySelector.selectStrategyImplementor( SessionEventListener.class, autoSessionEventsListenerName );

		final boolean logSessionMetrics = cfgService.getSetting( LOG_SESSION_METRICS, BOOLEAN, statisticsEnabled );
		this.baselineSessionEventsListenerBuilder = new BaselineSessionEventsListenerBuilder( logSessionMetrics, autoSessionEventsListener );

		this.customEntityDirtinessStrategy = strategySelector.resolveDefaultableStrategy(
				CustomEntityDirtinessStrategy.class,
				configurationSettings.get( CUSTOM_ENTITY_DIRTINESS_STRATEGY ),
				DefaultCustomEntityDirtinessStrategy.INSTANCE
		);

		this.entityNotFoundDelegate = StandardEntityNotFoundDelegate.INSTANCE;
		this.identifierRollbackEnabled = cfgService.getSetting( USE_IDENTIFIER_ROLLBACK, BOOLEAN, false );
		this.defaultEntityMode = EntityMode.parse( (String) configurationSettings.get( DEFAULT_ENTITY_MODE ) );
		this.checkNullability = cfgService.getSetting( CHECK_NULLABILITY, BOOLEAN, true );
		this.initializeLazyStateOutsideTransactions = cfgService.getSetting( ENABLE_LAZY_LOAD_NO_TRANS, BOOLEAN, false );

		this.multiTenancyStrategy = MultiTenancyStrategy.determineMultiTenancyStrategy( configurationSettings );
		this.currentTenantIdentifierResolver = strategySelector.resolveStrategy(
				CurrentTenantIdentifierResolver.class,
				configurationSettings.get( MULTI_TENANT_IDENTIFIER_RESOLVER )
		);

		this.multiTableBulkIdStrategy = strategySelector.resolveDefaultableStrategy(
				MultiTableBulkIdStrategy.class,
				configurationSettings.get( HQL_BULK_ID_STRATEGY ),
				jdbcServices.getJdbcEnvironment().getDialect().getDefaultMultiTableBulkIdStrategy()
		);

		this.batchFetchStyle = BatchFetchStyle.interpret( configurationSettings.get( BATCH_FETCH_STYLE ) );
		this.defaultBatchFetchSize = ConfigurationHelper.getInt( DEFAULT_BATCH_FETCH_SIZE, configurationSettings, -1 );
		this.maximumFetchDepth = ConfigurationHelper.getInteger( MAX_FETCH_DEPTH, configurationSettings );
		final String defaultNullPrecedence = ConfigurationHelper.getString(
				AvailableSettings.DEFAULT_NULL_ORDERING, configurationSettings, "none", "first", "last"
		);
		this.defaultNullPrecedence = NullPrecedence.parse( defaultNullPrecedence );
		this.orderUpdatesEnabled = ConfigurationHelper.getBoolean( ORDER_UPDATES, configurationSettings );
		this.orderInsertsEnabled = ConfigurationHelper.getBoolean( ORDER_INSERTS, configurationSettings );

		this.jtaTrackByThread = cfgService.getSetting( JTA_TRACK_BY_THREAD, BOOLEAN, true );

		this.querySubstitutions = ConfigurationHelper.toMap( QUERY_SUBSTITUTIONS, " ,=;:\n\t\r\f", configurationSettings );
		this.strictJpaQueryLanguageCompliance = cfgService.getSetting( JPAQL_STRICT_COMPLIANCE, BOOLEAN, false );
		this.namedQueryStartupCheckingEnabled = cfgService.getSetting( QUERY_STARTUP_CHECKING, BOOLEAN, true );

		this.secondLevelCacheEnabled = cfgService.getSetting( USE_SECOND_LEVEL_CACHE, BOOLEAN, true );
		this.queryCacheEnabled = cfgService.getSetting( USE_QUERY_CACHE, BOOLEAN, false );
		this.queryCacheFactory = strategySelector.resolveDefaultableStrategy(
				QueryCacheFactory.class,
				configurationSettings.get( QUERY_CACHE_FACTORY ),
				StandardQueryCacheFactory.INSTANCE
		);
		this.cacheRegionPrefix = ConfigurationHelper.extractPropertyValue(
				CACHE_REGION_PREFIX,
				configurationSettings
		);
		this.minimalPutsEnabled = cfgService.getSetting(
				USE_MINIMAL_PUTS,
				BOOLEAN,
				serviceRegistry.getService( RegionFactory.class ).isMinimalPutsEnabledByDefault()
		);
		this.structuredCacheEntriesEnabled = cfgService.getSetting( USE_STRUCTURED_CACHE, BOOLEAN, false );
		this.directReferenceCacheEntriesEnabled = cfgService.getSetting( USE_DIRECT_REFERENCE_CACHE_ENTRIES,BOOLEAN, false );
		this.autoEvictCollectionCache = cfgService.getSetting( AUTO_EVICT_COLLECTION_CACHE, BOOLEAN, false );

		try {
			this.schemaAutoTooling = SchemaAutoTooling.interpret( (String) configurationSettings.get( AvailableSettings.HBM2DDL_AUTO ) );
		}
		catch (Exception e) {
			log.warn( e.getMessage() + "  Ignoring" );
		}


		final ExtractedDatabaseMetaData meta = jdbcServices.getExtractedMetaDataSupport();
		this.dataDefinitionImplicitCommit = meta.doesDataDefinitionCauseTransactionCommit();
		this.dataDefinitionInTransactionSupported = meta.supportsDataDefinitionInTransaction();

		this.jdbcBatchSize = ConfigurationHelper.getInt( STATEMENT_BATCH_SIZE, configurationSettings, 0 );
		if ( !meta.supportsBatchUpdates() ) {
			this.jdbcBatchSize = 0;
		}

		this.jdbcBatchVersionedData = ConfigurationHelper.getBoolean( BATCH_VERSIONED_DATA, configurationSettings, false );
		this.scrollableResultSetsEnabled = ConfigurationHelper.getBoolean(
				USE_SCROLLABLE_RESULTSET,
				configurationSettings,
				meta.supportsScrollableResults()
		);
		this.wrapResultSetsEnabled = ConfigurationHelper.getBoolean(
				WRAP_RESULT_SETS,
				configurationSettings,
				false
		);
		this.getGeneratedKeysEnabled = ConfigurationHelper.getBoolean(
				USE_GET_GENERATED_KEYS,
				configurationSettings,
				meta.supportsGetGeneratedKeys()
		);
		this.jdbcFetchSize = ConfigurationHelper.getInteger( STATEMENT_FETCH_SIZE, configurationSettings );

		final String releaseModeName = ConfigurationHelper.getString( RELEASE_CONNECTIONS, configurationSettings, "auto" );
		if ( "auto".equals( releaseModeName ) ) {
			this.connectionReleaseMode = serviceRegistry.getService( TransactionFactory.class ).getDefaultReleaseMode();
		}
		else {
			connectionReleaseMode = ConnectionReleaseMode.parse( releaseModeName );
		}

		this.commentsEnabled = ConfigurationHelper.getBoolean( USE_SQL_COMMENTS, configurationSettings );

		if ( metadata.getSqlFunctionMap() != null ) {
			for ( Map.Entry<String, SQLFunction> sqlFunctionEntry : metadata.getSqlFunctionMap().entrySet() ) {
				applySqlFunction( sqlFunctionEntry.getKey(), sqlFunctionEntry.getValue() );
			}
		}
	}

	@Override
	public SessionFactoryBuilder applyValidatorFactory(Object validatorFactory) {
		this.validatorFactoryReference = validatorFactory;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyBeanManager(Object beanManager) {
		this.beanManagerReference = beanManager;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyName(String sessionFactoryName) {
		this.sessionFactoryName = sessionFactoryName;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyNameAsJndiName(boolean isJndiName) {
		this.sessionFactoryNameAlsoJndiName = isJndiName;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyAutoClosing(boolean enabled) {
		this.autoCloseSessionEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyAutoFlushing(boolean enabled) {
		this.flushBeforeCompletionEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyStatisticsSupport(boolean enabled) {
		this.statisticsEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder addSessionFactoryObservers(SessionFactoryObserver... observers) {
		this.sessionFactoryObserverList.addAll( Arrays.asList( observers ) );
		return this;
	}

	@Override
	public SessionFactoryBuilder applyInterceptor(Interceptor interceptor) {
		this.interceptor = interceptor;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyCustomEntityDirtinessStrategy(CustomEntityDirtinessStrategy strategy) {
		this.customEntityDirtinessStrategy = strategy;
		return this;
	}


	@Override
	public SessionFactoryBuilder addEntityNameResolver(EntityNameResolver... entityNameResolvers) {
		this.entityNameResolvers.addAll( Arrays.asList( entityNameResolvers ) );
		return this;
	}

	@Override
	public SessionFactoryBuilder applyEntityNotFoundDelegate(EntityNotFoundDelegate entityNotFoundDelegate) {
		this.entityNotFoundDelegate = entityNotFoundDelegate;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyIdentifierRollbackSupport(boolean enabled) {
		this.identifierRollbackEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyDefaultEntityMode(EntityMode entityMode) {
		this.defaultEntityMode = entityMode;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyNullabilityChecking(boolean enabled) {
		this.checkNullability = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyLazyInitializationOutsideTransaction(boolean enabled) {
		this.initializeLazyStateOutsideTransactions = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyEntityTuplizerFactory(EntityTuplizerFactory entityTuplizerFactory) {
		this.entityTuplizerFactory = entityTuplizerFactory;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyEntityTuplizer(
			EntityMode entityMode,
			Class<? extends EntityTuplizer> tuplizerClass) {
		this.entityTuplizerFactory.registerDefaultTuplizerClass( entityMode, tuplizerClass );
		return this;
	}

	@Override
	public SessionFactoryBuilder applyMultiTableBulkIdStrategy(MultiTableBulkIdStrategy strategy) {
		this.multiTableBulkIdStrategy = strategy;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyBatchFetchStyle(BatchFetchStyle style) {
		this.batchFetchStyle = style;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyDefaultBatchFetchSize(int size) {
		this.defaultBatchFetchSize = size;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyMaximumFetchDepth(int depth) {
		this.maximumFetchDepth = depth;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyDefaultNullPrecedence(NullPrecedence nullPrecedence) {
		this.defaultNullPrecedence = nullPrecedence;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyOrderingOfInserts(boolean enabled) {
		this.orderInsertsEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyOrderingOfUpdates(boolean enabled) {
		this.orderUpdatesEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyMultiTenancyStrategy(MultiTenancyStrategy strategy) {
		this.multiTenancyStrategy = strategy;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyCurrentTenantIdentifierResolver(CurrentTenantIdentifierResolver resolver) {
		this.currentTenantIdentifierResolver = resolver;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyJtaTrackingByThread(boolean enabled) {
		this.jtaTrackByThread = enabled;
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public SessionFactoryBuilder applyQuerySubstitutions(Map substitutions) {
		this.querySubstitutions.putAll( substitutions );
		return this;
	}

	@Override
	public SessionFactoryBuilder applyStrictJpaQueryLanguageCompliance(boolean enabled) {
		this.strictJpaQueryLanguageCompliance = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyNamedQueryCheckingOnStartup(boolean enabled) {
		this.namedQueryStartupCheckingEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applySecondLevelCacheSupport(boolean enabled) {
		this.secondLevelCacheEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyQueryCacheSupport(boolean enabled) {
		this.queryCacheEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyQueryCacheFactory(QueryCacheFactory factory) {
		this.queryCacheFactory = factory;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyCacheRegionPrefix(String prefix) {
		this.cacheRegionPrefix = prefix;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyMinimalPutsForCaching(boolean enabled) {
		this.minimalPutsEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyStructuredCacheEntries(boolean enabled) {
		this.structuredCacheEntriesEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyDirectReferenceCaching(boolean enabled) {
		this.directReferenceCacheEntriesEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyAutomaticEvictionOfCollectionCaches(boolean enabled) {
		this.autoEvictCollectionCache = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyJdbcBatchSize(int size) {
		this.jdbcBatchSize = size;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyJdbcBatchingForVersionedEntities(boolean enabled) {
		this.jdbcBatchVersionedData = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyScrollableResultsSupport(boolean enabled) {
		this.scrollableResultSetsEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyResultSetsWrapping(boolean enabled) {
		this.wrapResultSetsEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyGetGeneratedKeysSupport(boolean enabled) {
		this.getGeneratedKeysEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyJdbcFetchSize(int size) {
		this.jdbcFetchSize = size;
		return this;
	}

	@Override
	public SessionFactoryBuilder applyConnectionReleaseMode(ConnectionReleaseMode connectionReleaseMode) {
		this.connectionReleaseMode = connectionReleaseMode;
		return this;
	}

	@Override
	public SessionFactoryBuilder applySqlComments(boolean enabled) {
		this.commentsEnabled = enabled;
		return this;
	}

	@Override
	public SessionFactoryBuilder applySqlFunction(String registrationName, SQLFunction sqlFunction) {
		if ( this.sqlFunctions == null ) {
			this.sqlFunctions = new HashMap<String, SQLFunction>();
		}
		this.sqlFunctions.put( registrationName, sqlFunction );
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends SessionFactoryBuilder> T unwrap(Class<T> type) {
		return (T) this;
	}

	@Override
	public SessionFactory build() {
		metadata.validate();
		return new SessionFactoryImpl( metadata, buildSessionFactoryOptions() );
	}

	@Override
	public SessionFactoryOptions buildSessionFactoryOptions() {
		return new SessionFactoryOptionsImpl( this );
	}



	// SessionFactoryOptionsState impl ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public StandardServiceRegistry getServiceRegistry() {
		return serviceRegistry;
	}

	@Override
	public Object getBeanManagerReference() {
		return beanManagerReference;
	}

	@Override
	public Object getValidatorFactoryReference() {
		return validatorFactoryReference;
	}

	@Override
	public String getSessionFactoryName() {
		return sessionFactoryName;
	}

	@Override
	public boolean isSessionFactoryNameAlsoJndiName() {
		return sessionFactoryNameAlsoJndiName;
	}

	@Override
	public boolean isFlushBeforeCompletionEnabled() {
		return flushBeforeCompletionEnabled;
	}

	@Override
	public boolean isAutoCloseSessionEnabled() {
		return autoCloseSessionEnabled;
	}

	@Override
	public boolean isStatisticsEnabled() {
		return statisticsEnabled;
	}

	@Override
	public Interceptor getInterceptor() {
		return interceptor == null ? EmptyInterceptor.INSTANCE : interceptor;
	}

	@Override
	public SessionFactoryObserver[] getSessionFactoryObservers() {
		return sessionFactoryObserverList.toArray( new SessionFactoryObserver[ sessionFactoryObserverList.size() ] );
	}

	@Override
	public BaselineSessionEventsListenerBuilder getBaselineSessionEventsListenerBuilder() {
		return baselineSessionEventsListenerBuilder;
	}

	@Override
	public boolean isIdentifierRollbackEnabled() {
		return identifierRollbackEnabled;
	}

	@Override
	public EntityMode getDefaultEntityMode() {
		return defaultEntityMode;
	}

	@Override
	public EntityTuplizerFactory getEntityTuplizerFactory() {
		return entityTuplizerFactory;
	}

	@Override
	public boolean isCheckNullability() {
		return checkNullability;
	}

	@Override
	public boolean isInitializeLazyStateOutsideTransactionsEnabled() {
		return initializeLazyStateOutsideTransactions;
	}

	@Override
	public MultiTableBulkIdStrategy getMultiTableBulkIdStrategy() {
		return multiTableBulkIdStrategy;
	}

	@Override
	public BatchFetchStyle getBatchFetchStyle() {
		return batchFetchStyle;
	}

	@Override
	public int getDefaultBatchFetchSize() {
		return defaultBatchFetchSize;
	}

	@Override
	public Integer getMaximumFetchDepth() {
		return maximumFetchDepth;
	}

	@Override
	public NullPrecedence getDefaultNullPrecedence() {
		return defaultNullPrecedence;
	}

	@Override
	public boolean isOrderUpdatesEnabled() {
		return orderUpdatesEnabled;
	}

	@Override
	public boolean isOrderInsertsEnabled() {
		return orderInsertsEnabled;
	}

	@Override
	public MultiTenancyStrategy getMultiTenancyStrategy() {
		return multiTenancyStrategy;
	}

	@Override
	public CurrentTenantIdentifierResolver getCurrentTenantIdentifierResolver() {
		return currentTenantIdentifierResolver;
	}

	@Override
	public boolean isJtaTrackByThread() {
		return jtaTrackByThread;
	}

	@Override
	public Map getQuerySubstitutions() {
		return querySubstitutions;
	}

	@Override
	public boolean isStrictJpaQueryLanguageCompliance() {
		return strictJpaQueryLanguageCompliance;
	}

	@Override
	public boolean isNamedQueryStartupCheckingEnabled() {
		return namedQueryStartupCheckingEnabled;
	}

	@Override
	public boolean isSecondLevelCacheEnabled() {
		return secondLevelCacheEnabled;
	}

	@Override
	public boolean isQueryCacheEnabled() {
		return queryCacheEnabled;
	}

	@Override
	public QueryCacheFactory getQueryCacheFactory() {
		return queryCacheFactory;
	}

	@Override
	public String getCacheRegionPrefix() {
		return cacheRegionPrefix;
	}

	@Override
	public boolean isMinimalPutsEnabled() {
		return minimalPutsEnabled;
	}

	@Override
	public boolean isStructuredCacheEntriesEnabled() {
		return structuredCacheEntriesEnabled;
	}

	@Override
	public boolean isDirectReferenceCacheEntriesEnabled() {
		return directReferenceCacheEntriesEnabled;
	}

	@Override
	public boolean isAutoEvictCollectionCache() {
		return autoEvictCollectionCache;
	}

	@Override
	public SchemaAutoTooling getSchemaAutoTooling() {
		return schemaAutoTooling;
	}

	@Override
	public boolean isDataDefinitionImplicitCommit() {
		return dataDefinitionImplicitCommit;
	}

	@Override
	public boolean isDataDefinitionInTransactionSupported() {
		return dataDefinitionInTransactionSupported;
	}

	@Override
	public int getJdbcBatchSize() {
		return jdbcBatchSize;
	}

	@Override
	public boolean isJdbcBatchVersionedData() {
		return jdbcBatchVersionedData;
	}

	@Override
	public boolean isScrollableResultSetsEnabled() {
		return scrollableResultSetsEnabled;
	}

	@Override
	public boolean isWrapResultSetsEnabled() {
		return wrapResultSetsEnabled;
	}

	@Override
	public boolean isGetGeneratedKeysEnabled() {
		return getGeneratedKeysEnabled;
	}

	@Override
	public Integer getJdbcFetchSize() {
		return jdbcFetchSize;
	}

	@Override
	public ConnectionReleaseMode getConnectionReleaseMode() {
		return connectionReleaseMode;
	}

	@Override
	public boolean isCommentsEnabled() {
		return commentsEnabled;
	}

	@Override
	public CustomEntityDirtinessStrategy getCustomEntityDirtinessStrategy() {
		return customEntityDirtinessStrategy;
	}

	@Override
	public EntityNameResolver[] getEntityNameResolvers() {
		return entityNameResolvers.toArray( new EntityNameResolver[ entityNameResolvers.size() ] );
	}

	@Override
	public EntityNotFoundDelegate getEntityNotFoundDelegate() {
		return entityNotFoundDelegate;
	}

	@Override
	public Map<String, SQLFunction> getCustomSqlFunctionMap() {
		return sqlFunctions == null ? Collections.<String, SQLFunction>emptyMap() : sqlFunctions;
	}
}
