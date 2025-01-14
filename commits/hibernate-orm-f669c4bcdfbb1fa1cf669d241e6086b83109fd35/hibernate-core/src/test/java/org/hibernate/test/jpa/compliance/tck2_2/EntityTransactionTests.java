/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.test.jpa.compliance.tck2_2;

import org.hibernate.Transaction;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import org.hibernate.testing.junit4.BaseUnitTestCase;
import org.junit.Test;

import static org.hibernate.testing.transaction.TransactionUtil2.inSession;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * @author Steve Ebersole
 */
public class EntityTransactionTests extends BaseUnitTestCase {

	@Test
	public void testGetRollbackOnlyExpectations() {
		final StandardServiceRegistry ssr = new StandardServiceRegistryBuilder()
				.applySetting( AvailableSettings.JPA_TRANSACTION_COMPLIANCE, "true" )
				.build();

		try {
			final SessionFactoryImplementor sessionFactory = (SessionFactoryImplementor) new MetadataSources( ssr )
					.buildMetadata()
					.buildSessionFactory();

			try {
				inSession(
						sessionFactory,
						session -> {
							final Transaction transaction = session.getTransaction();
							assertFalse( transaction.isActive() );
							try {
								transaction.getRollbackOnly();
								fail( "Expecting failure #getRollbackOnly on non-active txn" );
							}
							catch (IllegalStateException expected) {
							}
						}
				);
			}
			finally {
				sessionFactory.close();
			}
		}
		finally {
			StandardServiceRegistryBuilder.destroy( ssr );
		}
	}

	@Test
	public void testSetRollbackOnlyExpectations() {
		final StandardServiceRegistry ssr = new StandardServiceRegistryBuilder()
				.applySetting( AvailableSettings.JPA_TRANSACTION_COMPLIANCE, "true" )
				.build();

		try {
			final SessionFactoryImplementor sessionFactory = (SessionFactoryImplementor) new MetadataSources( ssr )
					.buildMetadata()
					.buildSessionFactory();

			try {
				inSession(
						sessionFactory,
						session -> {
							final Transaction transaction = session.getTransaction();
							assertFalse( transaction.isActive() );
							try {
								transaction.setRollbackOnly();
								fail( "Expecting failure #setRollbackOnly on non-active txn" );
							}
							catch (IllegalStateException expected) {
							}
						}
				);
			}
			finally {
				sessionFactory.close();
			}
		}
		finally {
			StandardServiceRegistryBuilder.destroy( ssr );
		}
	}

	@Test
	public void testRollbackExpectations() {
		final StandardServiceRegistry ssr = new StandardServiceRegistryBuilder()
				.applySetting( AvailableSettings.JPA_TRANSACTION_COMPLIANCE, "true" )
				.build();

		try {
			final SessionFactoryImplementor sessionFactory = (SessionFactoryImplementor) new MetadataSources( ssr )
					.buildMetadata()
					.buildSessionFactory();

			try {
				inSession(
						sessionFactory,
						session -> {
							final Transaction transaction = session.getTransaction();
							assertFalse( transaction.isActive() );
							try {
								transaction.rollback();
								fail( "Expecting failure #rollback on non-active txn" );
							}
							catch (IllegalStateException expected) {
							}
						}
				);
			}
			finally {
				sessionFactory.close();
			}
		}
		finally {
			StandardServiceRegistryBuilder.destroy( ssr );
		}
	}
}
