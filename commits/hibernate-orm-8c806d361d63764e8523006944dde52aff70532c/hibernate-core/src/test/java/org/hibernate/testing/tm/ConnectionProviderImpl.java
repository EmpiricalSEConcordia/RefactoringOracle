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
package org.hibernate.testing.tm;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import org.hibernate.HibernateException;
import org.hibernate.service.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.spi.Stoppable;
import org.hibernate.test.common.ConnectionProviderBuilder;

/**
 * A {@link ConnectionProvider} implementation adding JTA-style transactionality
 * around the returned connections using the {@link SimpleJtaTransactionManagerImpl}.
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public class ConnectionProviderImpl implements ConnectionProvider {
	private static ConnectionProvider actualConnectionProvider = ConnectionProviderBuilder.buildConnectionProvider();

	private boolean isTransactional;

	public static ConnectionProvider getActualConnectionProvider() {
		return actualConnectionProvider;
	}

	public void configure(Properties props) throws HibernateException {
	}

	public Connection getConnection() throws SQLException {
		SimpleJtaTransactionImpl currentTransaction = SimpleJtaTransactionManagerImpl.getInstance().getCurrentTransaction();
		if ( currentTransaction == null ) {
			isTransactional = false;
			return actualConnectionProvider.getConnection();
		}
		else {
			isTransactional = true;
			Connection connection = currentTransaction.getEnlistedConnection();
			if ( connection == null ) {
				connection = actualConnectionProvider.getConnection();
				currentTransaction.enlistConnection( connection );
			}
			return connection;
		}
	}

	public void closeConnection(Connection conn) throws SQLException {
		if ( !isTransactional ) {
			conn.close();
		}
	}

	public void close() throws HibernateException {
		if ( actualConnectionProvider instanceof Stoppable ) {
			( ( Stoppable ) actualConnectionProvider ).stop();
		}
	}

	public boolean supportsAggressiveRelease() {
		return true;
	}
}
