/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.cache.ehcache.internal.regions;

import java.util.Properties;

import net.sf.ehcache.Ehcache;

import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.ehcache.internal.strategy.EhcacheAccessStrategyFactory;
import org.hibernate.cache.spi.access.AccessType;

/**
 * A collection region specific wrapper around an Ehcache instance.
 * <p/>
 * This implementation returns Ehcache specific access strategy instances for all the non-transactional access types. Transactional access
 * is not supported.
 *
 * @author Chris Dennis
 * @author Abhishek Sanoujam
 * @author Alex Snaps
 */
public class EhcacheCollectionRegion extends EhcacheTransactionalDataRegion implements CollectionRegion {
	/**
	 * Constructs an EhcacheCollectionRegion around the given underlying cache.
	 *
	 * @param accessStrategyFactory The factory for building needed CollectionRegionAccessStrategy instance
	 * @param underlyingCache The ehcache cache instance
	 * @param settings The Hibernate settings
	 * @param metadata Metadata about the data to be cached in this region
	 * @param properties Any additional[ properties
	 */
	public EhcacheCollectionRegion(
			EhcacheAccessStrategyFactory accessStrategyFactory,
			Ehcache underlyingCache,
			SessionFactoryOptions settings,
			CacheDataDescription metadata,
			Properties properties) {
		super( accessStrategyFactory, underlyingCache, settings, metadata, properties );
	}

	@Override
	public CollectionRegionAccessStrategy buildAccessStrategy(AccessType accessType) throws CacheException {
		return getAccessStrategyFactory().createCollectionRegionAccessStrategy( this, accessType );
	}
}
