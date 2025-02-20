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
package org.hibernate.action.internal;

import java.io.Serializable;

import org.hibernate.AssertionFailure;
import org.hibernate.HibernateException;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.CacheKey;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.hibernate.engine.internal.Versioning;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.event.EventType;
import org.hibernate.event.PostUpdateEvent;
import org.hibernate.event.PostUpdateEventListener;
import org.hibernate.event.PreUpdateEvent;
import org.hibernate.event.PreUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.event.service.spi.EventListenerGroup;
import org.hibernate.type.TypeHelper;

public final class EntityUpdateAction extends EntityAction {
	private final Object[] state;
	private final Object[] previousState;
	private final Object previousVersion;
	private final int[] dirtyFields;
	private final boolean hasDirtyCollection;
	private final Object rowId;
	private Object nextVersion;
	private Object cacheEntry;
	private SoftLock lock;

	public EntityUpdateAction(
	        final Serializable id,
	        final Object[] state,
	        final int[] dirtyProperties,
	        final boolean hasDirtyCollection,
	        final Object[] previousState,
	        final Object previousVersion,
	        final Object nextVersion,
	        final Object instance,
	        final Object rowId,
	        final EntityPersister persister,
	        final SessionImplementor session) throws HibernateException {
		super( session, id, instance, persister );
		this.state = state;
		this.previousState = previousState;
		this.previousVersion = previousVersion;
		this.nextVersion = nextVersion;
		this.dirtyFields = dirtyProperties;
		this.hasDirtyCollection = hasDirtyCollection;
		this.rowId = rowId;
	}

	@Override
	public void execute() throws HibernateException {
		Serializable id = getId();
		EntityPersister persister = getPersister();
		SessionImplementor session = getSession();
		Object instance = getInstance();

		boolean veto = preUpdate();

		final SessionFactoryImplementor factory = getSession().getFactory();
		Object previousVersion = this.previousVersion;
		if ( persister.isVersionPropertyGenerated() ) {
			// we need to grab the version value from the entity, otherwise
			// we have issues with generated-version entities that may have
			// multiple actions queued during the same flush
			previousVersion = persister.getVersion( instance, session.getEntityMode() );
		}
		
		final CacheKey ck;
		if ( persister.hasCache() ) {
			ck = session.generateCacheKey(
					id, 
					persister.getIdentifierType(), 
					persister.getRootEntityName()
			);
			lock = persister.getCacheAccessStrategy().lockItem( ck, previousVersion );
		}
		else {
			ck = null;
		}

		if ( !veto ) {
			persister.update( 
					id, 
					state, 
					dirtyFields, 
					hasDirtyCollection, 
					previousState, 
					previousVersion, 
					instance, 
					rowId, 
					session 
			);
		}

		EntityEntry entry = getSession().getPersistenceContext().getEntry( instance );
		if ( entry == null ) {
			throw new AssertionFailure( "possible nonthreadsafe access to session" );
		}
		
		if ( entry.getStatus()==Status.MANAGED || persister.isVersionPropertyGenerated() ) {
			// get the updated snapshot of the entity state by cloning current state;
			// it is safe to copy in place, since by this time no-one else (should have)
			// has a reference  to the array
			TypeHelper.deepCopy(
					state,
					persister.getPropertyTypes(),
					persister.getPropertyCheckability(),
					state,
					session
			);
			if ( persister.hasUpdateGeneratedProperties() ) {
				// this entity defines proeprty generation, so process those generated
				// values...
				persister.processUpdateGeneratedProperties( id, instance, state, session );
				if ( persister.isVersionPropertyGenerated() ) {
					nextVersion = Versioning.getVersion( state, persister );
				}
			}
			// have the entity entry doAfterTransactionCompletion post-update processing, passing it the
			// update state and the new version (if one).
			entry.postUpdate( instance, state, nextVersion );
		}

		if ( persister.hasCache() ) {
			if ( persister.isCacheInvalidationRequired() || entry.getStatus()!= Status.MANAGED ) {
				persister.getCacheAccessStrategy().remove( ck );
			}
			else {
				//TODO: inefficient if that cache is just going to ignore the updated state!
				CacheEntry ce = new CacheEntry(
						state, 
						persister, 
						persister.hasUninitializedLazyProperties( instance, session.getEntityMode() ), 
						nextVersion,
						getSession(),
						instance
				);
				cacheEntry = persister.getCacheEntryStructure().structure( ce );
				boolean put = persister.getCacheAccessStrategy().update( ck, cacheEntry, nextVersion, previousVersion );
				if ( put && factory.getStatistics().isStatisticsEnabled() ) {
					factory.getStatisticsImplementor().secondLevelCachePut( getPersister().getCacheAccessStrategy().getRegion().getName() );
				}
			}
		}

		postUpdate();

		if ( factory.getStatistics().isStatisticsEnabled() && !veto ) {
			factory.getStatisticsImplementor()
					.updateEntity( getPersister().getEntityName() );
		}
	}

	private boolean preUpdate() {
		boolean veto = false;
		EventListenerGroup<PreUpdateEventListener> listenerGroup = listenerGroup( EventType.PRE_UPDATE );
		if ( listenerGroup.isEmpty() ) {
			return veto;
		}
		final PreUpdateEvent event = new PreUpdateEvent(
				getInstance(),
				getId(),
				state,
				previousState,
				getPersister(),
				eventSource()
		);
		for ( PreUpdateEventListener listener : listenerGroup.listeners() ) {
			veto |= listener.onPreUpdate( event );
		}
		return veto;
	}

	private void postUpdate() {
		EventListenerGroup<PostUpdateEventListener> listenerGroup = listenerGroup( EventType.POST_UPDATE );
		if ( listenerGroup.isEmpty() ) {
			return;
		}
		final PostUpdateEvent event = new PostUpdateEvent(
				getInstance(),
				getId(),
				state,
				previousState,
				dirtyFields,
				getPersister(),
				eventSource()
		);
		for ( PostUpdateEventListener listener : listenerGroup.listeners() ) {
			listener.onPostUpdate( event );
		}
	}

	private void postCommitUpdate() {
		EventListenerGroup<PostUpdateEventListener> listenerGroup = listenerGroup( EventType.POST_COMMIT_UPDATE );
		if ( listenerGroup.isEmpty() ) {
			return;
		}
		final PostUpdateEvent event = new PostUpdateEvent(
				getInstance(),
				getId(),
				state,
				previousState,
				dirtyFields,
				getPersister(),
				eventSource()
		);
		for ( PostUpdateEventListener listener : listenerGroup.listeners() ) {
			listener.onPostUpdate( event );
		}
	}

	@Override
	protected boolean hasPostCommitEventListeners() {
		return ! listenerGroup( EventType.POST_COMMIT_UPDATE ).isEmpty();
	}

	@Override
	public void doAfterTransactionCompletion(boolean success, SessionImplementor session) throws CacheException {
		EntityPersister persister = getPersister();
		if ( persister.hasCache() ) {
			
			final CacheKey ck = getSession().generateCacheKey(
					getId(), 
					persister.getIdentifierType(), 
					persister.getRootEntityName()
				);
			
			if ( success && cacheEntry!=null /*!persister.isCacheInvalidationRequired()*/ ) {
				boolean put = persister.getCacheAccessStrategy().afterUpdate( ck, cacheEntry, nextVersion, previousVersion, lock );
				
				if ( put && getSession().getFactory().getStatistics().isStatisticsEnabled() ) {
					getSession().getFactory().getStatisticsImplementor().secondLevelCachePut( getPersister().getCacheAccessStrategy().getRegion().getName() );
				}
			}
			else {
				persister.getCacheAccessStrategy().unlockItem( ck, lock );
			}
		}
		postCommitUpdate();
	}

}

