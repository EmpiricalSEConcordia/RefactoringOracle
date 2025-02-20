/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.hcore.util.impl;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.search.engine.spi.SearchFactoryImplementor;
import org.hibernate.search.event.impl.FullTextIndexEventListener;

/**
 * Static helper class to retrieve the instance of the current {@code SearchFactory} / {@code SearchFactoryImplementor}.
 *
 * <p>
 * <b>Note</b>:<br/>
 * The use of this class is discouraged. If possible should {@link org.hibernate.search.FullTextSession#getSearchFactory()}
 * be used. However, this is not always possible, for example in FullTextSessionImpl itself.
 * </p>
 *
 * @author Emmanuel Bernard
 */
public class ContextHelper {

	private ContextHelper() {
	}

	public static SearchFactoryImplementor getSearchFactory(Session session) {
		return getSearchFactoryBySessionImplementor( (SessionImplementor) session );
	}

	public static SearchFactoryImplementor getSearchFactoryBySessionImplementor(SessionImplementor session) {
		return getSearchFactoryBySFI( session.getFactory() );
	}

	public static SearchFactoryImplementor getSearchFactoryBySFI(SessionFactoryImplementor sfi) {
		final EventListenerRegistry service = sfi
				.getServiceRegistry()
				.getService( EventListenerRegistry.class );
		final Iterable<PostInsertEventListener> listeners = service.getEventListenerGroup( EventType.POST_INSERT )
				.listeners();
		FullTextIndexEventListener listener = null;
		//FIXME this sucks since we mandate the event listener use
		for ( PostInsertEventListener candidate : listeners ) {
			if ( candidate instanceof FullTextIndexEventListener ) {
				listener = (FullTextIndexEventListener) candidate;
				break;
			}
		}
		if ( listener == null ) {
			throw new HibernateException(
					"Hibernate Search Event listeners not configured, please check the reference documentation and the " +
							"application's hibernate.cfg.xml"
			);
		}
		return listener.getSearchFactoryImplementor();
	}
}
