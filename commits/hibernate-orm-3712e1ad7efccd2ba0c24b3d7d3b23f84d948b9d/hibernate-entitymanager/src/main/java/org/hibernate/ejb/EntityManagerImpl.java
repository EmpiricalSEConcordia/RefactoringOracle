/*
 * Copyright (c) 2009, Red Hat Middleware LLC or third-party contributors as
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
 */
package org.hibernate.ejb;

import java.util.Map;
import javax.persistence.PersistenceContextType;
import javax.persistence.PersistenceException;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.transaction.Synchronization;
import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.annotations.common.util.ReflectHelper;
import org.hibernate.cfg.Environment;
import org.hibernate.engine.SessionImplementor;

/**
 * Hibernate implementation of {@link javax.persistence.EntityManager}.
 *
 * @author Gavin King
 */
public class EntityManagerImpl extends AbstractEntityManagerImpl {

    public static final EntityManagerLogger LOG = org.jboss.logging.Logger.getMessageLogger(EntityManagerLogger.class,
                                                                                            EntityManagerImpl.class.getPackage().getName());

	protected Session session;
	protected boolean open;
	protected boolean discardOnClose;
	private Class sessionInterceptorClass;

	public EntityManagerImpl(
			EntityManagerFactoryImpl entityManagerFactory,
			PersistenceContextType pcType,
			PersistenceUnitTransactionType transactionType,
			boolean discardOnClose,
			Class sessionInterceptorClass,
			Map properties) {
		super( entityManagerFactory, pcType, transactionType, properties );
		this.open = true;
		this.discardOnClose = discardOnClose;
		Object localSessionInterceptor = null;
		if (properties != null) {
			localSessionInterceptor = properties.get( AvailableSettings.SESSION_INTERCEPTOR );
		}
		if ( localSessionInterceptor != null ) {
			if (localSessionInterceptor instanceof Class) {
				sessionInterceptorClass = (Class) localSessionInterceptor;
			}
			else if (localSessionInterceptor instanceof String) {
				try {
					sessionInterceptorClass =
							ReflectHelper.classForName( (String) localSessionInterceptor, EntityManagerImpl.class );
				}
				catch (ClassNotFoundException e) {
					throw new PersistenceException("Unable to instanciate interceptor: " + localSessionInterceptor, e);
				}
			}
			else {
				throw new PersistenceException("Unable to instanciate interceptor: " + localSessionInterceptor);
			}
		}
		this.sessionInterceptorClass = sessionInterceptorClass;
		postInit();
	}

	@Override
    public Session getSession() {
		if ( !open ) {
			throw new IllegalStateException( "EntityManager is closed" );
		}
		return getRawSession();
	}

	@Override
    protected Session getRawSession() {
		if ( session == null ) {
			Interceptor interceptor = null;
			if (sessionInterceptorClass != null) {
				try {
					interceptor = (Interceptor) sessionInterceptorClass.newInstance();
				}
				catch (InstantiationException e) {
					throw new PersistenceException("Unable to instanciate session interceptor: " + sessionInterceptorClass, e);
				}
				catch (IllegalAccessException e) {
					throw new PersistenceException("Unable to instanciate session interceptor: " + sessionInterceptorClass, e);
				}
				catch (ClassCastException e) {
					throw new PersistenceException("Session interceptor does not implement Interceptor: " + sessionInterceptorClass, e);
				}
			}
			session = getEntityManagerFactory().getSessionFactory().openSession( interceptor );
			if ( persistenceContextType == PersistenceContextType.TRANSACTION ) {
				( (SessionImplementor) session ).setAutoClear( true );
			}
		}
		return session;
	}

	public void close() {
		if ( !open ) {
			throw new IllegalStateException( "EntityManager is closed" );
		}
		if ( !discardOnClose && isTransactionInProgress() ) {
			//delay the closing till the end of the enlisted transaction
            getSession().getTransaction().registerSynchronization(new Synchronization() {
                public void beforeCompletion() {
                    // nothing to do
                }

				public void afterCompletion( int i ) {
                    if (session != null) if (session.isOpen()) {
                        LOG.debug("Closing entity manager after transaction completion");
                        session.close();
                    } else LOG.entityManagerClosedBySomeoneElse(Environment.AUTO_CLOSE_SESSION);
                    // TODO session == null should not happen
                }
            });
		}
		else {
			//close right now
			if ( session != null ) {
				session.close();
			}
		}
		open = false;
	}

	public boolean isOpen() {
		//adjustFlushMode(); //don't adjust, can't be done on closed EM
		try {
			if ( open ) {
				getSession().isOpen(); //to force enlistment in tx
			}
			return open;
		}
		catch (HibernateException he) {
			throwPersistenceException( he );
			return false;
		}
	}

}
