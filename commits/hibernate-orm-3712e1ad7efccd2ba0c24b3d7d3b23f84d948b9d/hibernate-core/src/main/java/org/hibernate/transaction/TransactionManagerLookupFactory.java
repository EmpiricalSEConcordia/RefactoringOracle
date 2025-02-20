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
package org.hibernate.transaction;

import java.util.Properties;
import javax.transaction.TransactionManager;
import org.hibernate.HibernateException;
import org.hibernate.Logger;
import org.hibernate.cfg.Environment;
import org.hibernate.util.ReflectHelper;

/**
 * Helper for generating {@link TransactionManagerLookup} instances.
 *
 * @author Gavin King
 */
public final class TransactionManagerLookupFactory {

    private static final Logger LOG = org.jboss.logging.Logger.getMessageLogger(Logger.class,
                                                                                TransactionManagerLookupFactory.class.getPackage().getName());

	/**
	 * Disallow instantiation
	 */
	private TransactionManagerLookupFactory() {
	}

	/**
	 * Convenience method for locating the JTA {@link TransactionManager} from the
	 * given platform config.
	 * <p/>
	 * Same as calling {@link #getTransactionManager}.getTransactionManager( props )
	 *
	 * @param props The properties representing the platform config
	 * @return The located {@link TransactionManager}
	 * @throws HibernateException Indicates a problem either (a) generatng the
	 * {@link TransactionManagerLookup} or (b) asking it to locate the {@link TransactionManager}.
	 */
	public static TransactionManager getTransactionManager(Properties props) throws HibernateException {
        LOG.obtainingTransactionManager();
		return getTransactionManagerLookup( props ).getTransactionManager( props );
	}

	/**
	 * Generate the appropriate {@link TransactionManagerLookup} given the
	 * config settings being passed.
	 *
	 * @param props The config settings
	 * @return The appropriate {@link TransactionManagerLookup}
	 * @throws HibernateException Indicates problem generating {@link TransactionManagerLookup}
	 */
	public static TransactionManagerLookup getTransactionManagerLookup(Properties props) throws HibernateException {
		String tmLookupClass = props.getProperty( Environment.TRANSACTION_MANAGER_STRATEGY );
		if ( tmLookupClass == null ) {
            LOG.transactionManagerLookupNotConfigured();
			return null;
		}
        LOG.instantiatingTransactionManagerLookup(tmLookupClass);
        try {
            TransactionManagerLookup lookup = (TransactionManagerLookup)ReflectHelper.classForName(tmLookupClass).newInstance();
            LOG.instantiatedTransactionManagerLookup();
            return lookup;
        } catch (Exception e) {
            LOG.error(LOG.unableToInstantiateTransactionManagerLookup(), e);
            throw new HibernateException(LOG.unableToInstantiateTransactionManagerLookup(tmLookupClass));
		}
	}
}
