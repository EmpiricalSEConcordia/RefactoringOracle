/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
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
package org.hibernate.engine.jdbc.internal;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import org.hibernate.HibernateException;
import org.hibernate.HibernateLogger;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.engine.jdbc.batch.spi.Batch;
import org.hibernate.engine.jdbc.batch.spi.BatchBuilder;
import org.hibernate.engine.jdbc.batch.spi.BatchKey;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.jdbc.spi.LogicalConnectionImplementor;
import org.hibernate.engine.jdbc.spi.SqlExceptionHelper;
import org.hibernate.engine.jdbc.spi.StatementPreparer;
import org.hibernate.engine.transaction.internal.TransactionCoordinatorImpl;
import org.hibernate.engine.transaction.spi.TransactionContext;
import org.hibernate.engine.transaction.spi.TransactionCoordinator;
import org.hibernate.engine.transaction.spi.TransactionEnvironment;
import org.hibernate.jdbc.WorkExecutorVisitable;
import org.hibernate.jdbc.WorkExecutor;

import org.jboss.logging.Logger;

/**
 * Standard Hibernate implementation of {@link JdbcCoordinator}
 * <p/>
 * IMPL NOTE : Custom serialization handling!
 *
 * @author Steve Ebersole
 */
public class JdbcCoordinatorImpl implements JdbcCoordinator {

    private static final HibernateLogger LOG = Logger.getMessageLogger(HibernateLogger.class, JdbcCoordinatorImpl.class.getName());

	private transient TransactionCoordinatorImpl transactionCoordinator;

	private final transient LogicalConnectionImpl logicalConnection;

	private transient Batch currentBatch;

	public JdbcCoordinatorImpl(
			Connection userSuppliedConnection,
			TransactionCoordinatorImpl transactionCoordinator) {
		this.transactionCoordinator = transactionCoordinator;
		this.logicalConnection = new LogicalConnectionImpl(
				userSuppliedConnection,
				transactionCoordinator.getTransactionContext().getConnectionReleaseMode(),
				transactionCoordinator.getTransactionContext().getTransactionEnvironment().getJdbcServices(),
				transactionCoordinator.getTransactionContext().getJdbcConnectionAccess()
		);
	}

	private JdbcCoordinatorImpl(LogicalConnectionImpl logicalConnection) {
		this.logicalConnection = logicalConnection;
	}

	@Override
	public TransactionCoordinator getTransactionCoordinator() {
		return transactionCoordinator;
	}

	@Override
	public LogicalConnectionImplementor getLogicalConnection() {
		return logicalConnection;
	}

	protected TransactionEnvironment transactionEnvironment() {
		return getTransactionCoordinator().getTransactionContext().getTransactionEnvironment();
	}

	protected SessionFactoryImplementor sessionFactory() {
		return transactionEnvironment().getSessionFactory();
	}

	protected BatchBuilder batchBuilder() {
		return sessionFactory().getServiceRegistry().getService( BatchBuilder.class );
	}

	private SqlExceptionHelper sqlExceptionHelper() {
		return transactionEnvironment().getJdbcServices().getSqlExceptionHelper();
	}


	private int flushDepth = 0;

	@Override
	public void flushBeginning() {
		if ( flushDepth == 0 ) {
			logicalConnection.disableReleases();
		}
		flushDepth++;
	}

	@Override
	public void flushEnding() {
		flushDepth--;
		if ( flushDepth < 0 ) {
			throw new HibernateException( "Mismatched flush handling" );
		}
		if ( flushDepth == 0 ) {
			logicalConnection.enableReleases();
		}
	}

	@Override
	public Connection close() {
		if ( currentBatch != null ) {
            LOG.closingUnreleasedBatch();
			currentBatch.release();
		}
		return logicalConnection.close();
	}

	@Override
	public Batch getBatch(BatchKey key) {
		if ( currentBatch != null ) {
			if ( currentBatch.getKey().equals( key ) ) {
				return currentBatch;
			}
			else {
				currentBatch.execute();
				currentBatch.release();
			}
		}
		currentBatch = batchBuilder().buildBatch( key, this );
		return currentBatch;
	}

	@Override
	public void abortBatch() {
		if ( currentBatch != null ) {
			currentBatch.release();
		}
	}

	private transient StatementPreparer statementPreparer;

	@Override
	public StatementPreparer getStatementPreparer() {
		if ( statementPreparer == null ) {
			statementPreparer = new StatementPreparerImpl( this );
		}
		return statementPreparer;
	}

	@Override
	public void setTransactionTimeOut(int timeOut) {
		getStatementPreparer().setTransactionTimeOut( timeOut );
	}

	/**
	 * To be called after local transaction completion.  Used to conditionally
	 * release the JDBC connection aggressively if the configured release mode
	 * indicates.
	 */
	public void afterTransaction() {
		logicalConnection.afterTransaction();
		if ( statementPreparer != null ) {
			statementPreparer.unsetTransactionTimeOut();
		}
	}

	@Override
	public <T> T coordinateWork(WorkExecutorVisitable<T> work) {
		Connection connection = getLogicalConnection().getDistinctConnectionProxy();
		try {
			T result = work.accept( new WorkExecutor<T>(), connection );
			getLogicalConnection().afterStatementExecution();
			return result;
		}
		catch ( SQLException e ) {
			throw sqlExceptionHelper().convert( e, "error executing work" );
		}
		finally {
			try {
				if ( ! connection.isClosed() ) {
					connection.close();
				}
			}
			catch (SQLException e) {
                LOG.debug("Error closing connection proxy", e);
			}
		}
	}

	public void executeBatch() {
		if ( currentBatch != null ) {
			currentBatch.execute();
			currentBatch.release(); // needed?
		}
	}

	@Override
	public void cancelLastQuery() {
		logicalConnection.getResourceRegistry().cancelLastQuery();
	}


	public void serialize(ObjectOutputStream oos) throws IOException {
		if ( ! logicalConnection.isReadyForSerialization() ) {
			throw new HibernateException( "Cannot serialize Session while connected" );
		}
		logicalConnection.serialize( oos );
	}

	public static JdbcCoordinatorImpl deserialize(
			ObjectInputStream ois,
			TransactionContext transactionContext) throws IOException, ClassNotFoundException {
		return new JdbcCoordinatorImpl( LogicalConnectionImpl.deserialize( ois, transactionContext ) );
 	}

	public void afterDeserialize(TransactionCoordinatorImpl transactionCoordinator) {
		this.transactionCoordinator = transactionCoordinator;
	}
}
