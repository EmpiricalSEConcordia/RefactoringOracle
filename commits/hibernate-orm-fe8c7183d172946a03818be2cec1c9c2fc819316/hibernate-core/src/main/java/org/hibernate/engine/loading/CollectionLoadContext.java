/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, Red Hat Middleware LLC or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Middleware LLC.
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
 *
 */
package org.hibernate.engine.loading;
import java.io.Serializable;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.hibernate.CacheMode;
import org.hibernate.EntityMode;
import org.hibernate.HibernateException;
import org.hibernate.HibernateLogger;
import org.hibernate.cache.CacheKey;
import org.hibernate.cache.entry.CollectionCacheEntry;
import org.hibernate.collection.PersistentCollection;
import org.hibernate.engine.CollectionEntry;
import org.hibernate.engine.CollectionKey;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.engine.Status;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.pretty.MessageHelper;
import org.jboss.logging.Logger;

/**
 * Represents state associated with the processing of a given {@link ResultSet}
 * in regards to loading collections.
 * <p/>
 * Another implementation option to consider is to not expose {@link ResultSet}s
 * directly (in the JDBC redesign) but to always "wrap" them and apply a
 * [series of] context[s] to that wrapper.
 *
 * @author Steve Ebersole
 */
public class CollectionLoadContext {

    private static final HibernateLogger LOG = Logger.getMessageLogger(HibernateLogger.class, CollectionLoadContext.class.getName());

	private final LoadContexts loadContexts;
	private final ResultSet resultSet;
	private Set localLoadingCollectionKeys = new HashSet();

	/**
	 * Creates a collection load context for the given result set.
	 *
	 * @param loadContexts Callback to other collection load contexts.
	 * @param resultSet The result set this is "wrapping".
	 */
	public CollectionLoadContext(LoadContexts loadContexts, ResultSet resultSet) {
		this.loadContexts = loadContexts;
		this.resultSet = resultSet;
	}

	public ResultSet getResultSet() {
		return resultSet;
	}

	public LoadContexts getLoadContext() {
		return loadContexts;
	}

	/**
	 * Retrieve the collection that is being loaded as part of processing this
	 * result set.
	 * <p/>
	 * Basically, there are two valid return values from this method:<ul>
	 * <li>an instance of {@link PersistentCollection} which indicates to
	 * continue loading the result set row data into that returned collection
	 * instance; this may be either an instance already associated and in the
	 * midst of being loaded, or a newly instantiated instance as a matching
	 * associated collection was not found.</li>
	 * <li><i>null</i> indicates to ignore the corresponding result set row
	 * data relating to the requested collection; this indicates that either
	 * the collection was found to already be associated with the persistence
	 * context in a fully loaded state, or it was found in a loading state
	 * associated with another result set processing context.</li>
	 * </ul>
	 *
	 * @param persister The persister for the collection being requested.
	 * @param key The key of the collection being requested.
	 *
	 * @return The loading collection (see discussion above).
	 */
	public PersistentCollection getLoadingCollection(final CollectionPersister persister, final Serializable key) {
		final EntityMode em = loadContexts.getPersistenceContext().getSession().getEntityMode();
		final CollectionKey collectionKey = new CollectionKey( persister, key, em );
        if (LOG.isTraceEnabled()) LOG.trace("Starting attempt to find loading collection ["
                                            + MessageHelper.collectionInfoString(persister.getRole(), key) + "]");
		final LoadingCollectionEntry loadingCollectionEntry = loadContexts.locateLoadingCollectionEntry( collectionKey );
		if ( loadingCollectionEntry == null ) {
			// look for existing collection as part of the persistence context
			PersistentCollection collection = loadContexts.getPersistenceContext().getCollection( collectionKey );
			if ( collection != null ) {
				if ( collection.wasInitialized() ) {
                    LOG.trace("Collection already initialized; ignoring");
					return null; // ignore this row of results! Note the early exit
                }
                LOG.trace("Collection not yet initialized; initializing");
			}
			else {
				Object owner = loadContexts.getPersistenceContext().getCollectionOwner( key, persister );
				final boolean newlySavedEntity = owner != null
						&& loadContexts.getPersistenceContext().getEntry( owner ).getStatus() != Status.LOADING
						&& em != EntityMode.DOM4J;
				if ( newlySavedEntity ) {
					// important, to account for newly saved entities in query
					// todo : some kind of check for new status...
                    LOG.trace("Owning entity already loaded; ignoring");
					return null;
				}
                // create one
                LOG.trace("Instantiating new collection [key=" + key + ", rs=" + resultSet + "]");
                collection = persister.getCollectionType().instantiate(loadContexts.getPersistenceContext().getSession(),
                                                                       persister,
                                                                       key);
			}
			collection.beforeInitialize( persister, -1 );
			collection.beginRead();
			localLoadingCollectionKeys.add( collectionKey );
			loadContexts.registerLoadingCollectionXRef( collectionKey, new LoadingCollectionEntry( resultSet, persister, key, collection ) );
			return collection;
		}
        if (loadingCollectionEntry.getResultSet() == resultSet) {
            LOG.trace("Found loading collection bound to current result set processing; reading row");
            return loadingCollectionEntry.getCollection();
		}
        // ignore this row, the collection is in process of
        // being loaded somewhere further "up" the stack
        LOG.trace("Collection is already being initialized; ignoring row");
        return null;
	}

	/**
	 * Finish the process of collection-loading for this bound result set.  Mainly this
	 * involves cleaning up resources and notifying the collections that loading is
	 * complete.
	 *
	 * @param persister The persister for which to complete loading.
	 */
	public void endLoadingCollections(CollectionPersister persister) {
		SessionImplementor session = getLoadContext().getPersistenceContext().getSession();
		if ( !loadContexts.hasLoadingCollectionEntries()
				&& localLoadingCollectionKeys.isEmpty() ) {
			return;
		}

		// in an effort to avoid concurrent-modification-exceptions (from
		// potential recursive calls back through here as a result of the
		// eventual call to PersistentCollection#endRead), we scan the
		// internal loadingCollections map for matches and store those matches
		// in a temp collection.  the temp collection is then used to "drive"
		// the #endRead processing.
		List matches = null;
		Iterator iter = localLoadingCollectionKeys.iterator();
		while ( iter.hasNext() ) {
			final CollectionKey collectionKey = (CollectionKey) iter.next();
			final LoadingCollectionEntry lce = loadContexts.locateLoadingCollectionEntry( collectionKey );
            if (lce == null) LOG.loadingCollectionKeyNotFound(collectionKey);
			else if ( lce.getResultSet() == resultSet && lce.getPersister() == persister ) {
				if ( matches == null ) {
					matches = new ArrayList();
				}
				matches.add( lce );
				if ( lce.getCollection().getOwner() == null ) {
					session.getPersistenceContext().addUnownedCollection(
							new CollectionKey( persister, lce.getKey(), session.getEntityMode() ),
							lce.getCollection()
					);
				}
                LOG.trace("Removing collection load entry [" + lce + "]");

				// todo : i'd much rather have this done from #endLoadingCollection(CollectionPersister,LoadingCollectionEntry)...
				loadContexts.unregisterLoadingCollectionXRef( collectionKey );
				iter.remove();
			}
		}

		endLoadingCollections( persister, matches );
		if ( localLoadingCollectionKeys.isEmpty() ) {
			// todo : hack!!!
			// NOTE : here we cleanup the load context when we have no more local
			// LCE entries.  This "works" for the time being because really
			// only the collection load contexts are implemented.  Long term,
			// this cleanup should become part of the "close result set"
			// processing from the (sandbox/jdbc) jdbc-container code.
			loadContexts.cleanup( resultSet );
		}
	}

	private void endLoadingCollections(CollectionPersister persister, List matchedCollectionEntries) {
		if ( matchedCollectionEntries == null ) {
            LOG.debugf("No collections were found in result set for role: %s", persister.getRole());
			return;
		}

		final int count = matchedCollectionEntries.size();
        LOG.debugf("%s collections were found in result set for role: %s", count, persister.getRole());

		for ( int i = 0; i < count; i++ ) {
			LoadingCollectionEntry lce = ( LoadingCollectionEntry ) matchedCollectionEntries.get( i );
			endLoadingCollection( lce, persister );
		}

        LOG.debugf("%s collections initialized for role: %s", count, persister.getRole());
	}

	private void endLoadingCollection(LoadingCollectionEntry lce, CollectionPersister persister) {
        LOG.trace("Ending loading collection [" + lce + "]");
		final SessionImplementor session = getLoadContext().getPersistenceContext().getSession();
		final EntityMode em = session.getEntityMode();

		boolean hasNoQueuedAdds = lce.getCollection().endRead(); // warning: can cause a recursive calls! (proxy initialization)

		if ( persister.getCollectionType().hasHolder( em ) ) {
			getLoadContext().getPersistenceContext().addCollectionHolder( lce.getCollection() );
		}

		CollectionEntry ce = getLoadContext().getPersistenceContext().getCollectionEntry( lce.getCollection() );
		if ( ce == null ) {
			ce = getLoadContext().getPersistenceContext().addInitializedCollection( persister, lce.getCollection(), lce.getKey() );
		}
		else {
			ce.postInitialize( lce.getCollection() );
		}

		boolean addToCache = hasNoQueuedAdds && // there were no queued additions
				persister.hasCache() &&             // and the role has a cache
				session.getCacheMode().isPutEnabled() &&
				!ce.isDoremove();                   // and this is not a forced initialization during flush
        if (addToCache) addCollectionToCache(lce, persister);

        if (LOG.isDebugEnabled()) LOG.debugf("Collection fully initialized: %s",
                                             MessageHelper.collectionInfoString(persister, lce.getKey(), session.getFactory()));
        if (session.getFactory().getStatistics().isStatisticsEnabled()) session.getFactory().getStatisticsImplementor().loadCollection(persister.getRole());
	}

	/**
	 * Add the collection to the second-level cache
	 *
	 * @param lce The entry representing the collection to add
	 * @param persister The persister
	 */
	private void addCollectionToCache(LoadingCollectionEntry lce, CollectionPersister persister) {
		final SessionImplementor session = getLoadContext().getPersistenceContext().getSession();
		final SessionFactoryImplementor factory = session.getFactory();

        if (LOG.isDebugEnabled()) LOG.debugf("Caching collection: %s",
                                             MessageHelper.collectionInfoString(persister, lce.getKey(), factory));

		if ( !session.getEnabledFilters().isEmpty() && persister.isAffectedByEnabledFilters( session ) ) {
			// some filters affecting the collection are enabled on the session, so do not do the put into the cache.
            LOG.debugf("Refusing to add to cache due to enabled filters");
			// todo : add the notion of enabled filters to the CacheKey to differentiate filtered collections from non-filtered;
			//      but CacheKey is currently used for both collections and entities; would ideally need to define two seperate ones;
			//      currently this works in conjuction with the check on
			//      DefaultInitializeCollectionEventHandler.initializeCollectionFromCache() (which makes sure to not read from
			//      cache with enabled filters).
			return; // EARLY EXIT!!!!!
		}

		final Object version;
		if ( persister.isVersioned() ) {
			Object collectionOwner = getLoadContext().getPersistenceContext().getCollectionOwner( lce.getKey(), persister );
			if ( collectionOwner == null ) {
				// generally speaking this would be caused by the collection key being defined by a property-ref, thus
				// the collection key and the owner key would not match up.  In this case, try to use the key of the
				// owner instance associated with the collection itself, if one.  If the collection does already know
				// about its owner, that owner should be the same instance as associated with the PC, but we do the
				// resolution against the PC anyway just to be safe since the lookup should not be costly.
				if ( lce.getCollection() != null ) {
					Object linkedOwner = lce.getCollection().getOwner();
					if ( linkedOwner != null ) {
						final Serializable ownerKey = persister.getOwnerEntityPersister().getIdentifier( linkedOwner, session );
						collectionOwner = getLoadContext().getPersistenceContext().getCollectionOwner( ownerKey, persister );
					}
				}
				if ( collectionOwner == null ) {
					throw new HibernateException(
							"Unable to resolve owner of loading collection [" +
									MessageHelper.collectionInfoString( persister, lce.getKey(), factory ) +
									"] for second level caching"
					);
				}
			}
			version = getLoadContext().getPersistenceContext().getEntry( collectionOwner ).getVersion();
		}
		else {
			version = null;
		}

		CollectionCacheEntry entry = new CollectionCacheEntry( lce.getCollection(), persister );
		CacheKey cacheKey = new CacheKey(
				lce.getKey(),
				persister.getKeyType(),
				persister.getRole(),
				session.getEntityMode(),
				session.getFactory()
		);
		boolean put = persister.getCacheAccessStrategy().putFromLoad(
				cacheKey,
				persister.getCacheEntryStructure().structure(entry),
				session.getTimestamp(),
				version,
				factory.getSettings().isMinimalPutsEnabled() && session.getCacheMode()!= CacheMode.REFRESH
		);

		if ( put && factory.getStatistics().isStatisticsEnabled() ) {
			factory.getStatisticsImplementor().secondLevelCachePut( persister.getCacheAccessStrategy().getRegion().getName() );
		}
	}

	void cleanup() {
        if (!localLoadingCollectionKeys.isEmpty()) LOG.localLoadingCollectionKeysCount(localLoadingCollectionKeys.size());
		loadContexts.cleanupCollectionXRefs( localLoadingCollectionKeys );
		localLoadingCollectionKeys.clear();
	}


	@Override
    public String toString() {
		return super.toString() + "<rs=" + resultSet + ">";
	}
}
