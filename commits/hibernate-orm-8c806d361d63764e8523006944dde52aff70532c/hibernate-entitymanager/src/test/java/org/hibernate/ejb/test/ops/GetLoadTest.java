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

//$Id$
package org.hibernate.ejb.test.ops;
import java.util.Map;
import javax.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.cfg.Environment;
import org.hibernate.ejb.EntityManagerFactoryImpl;
import org.hibernate.ejb.test.TestCase;

/**
 * @author Gavin King
 * @author Hardy Ferentschik
 */
public class GetLoadTest extends TestCase {

	public void testGetLoad() {
		clearCounts();

		EntityManager em = getOrCreateEntityManager();
		em.getTransaction().begin();
		Session s = ( Session ) em.getDelegate();

		Employer emp = new Employer();
		s.persist( emp );
		Node node = new Node( "foo" );
		Node parent = new Node( "bar" );
		parent.addChild( node );
		s.persist( parent );
		em.getTransaction().commit();
		em.close();

		em = getOrCreateEntityManager();
		em.getTransaction().begin();
		s = ( Session ) em.getDelegate();
		emp = ( Employer ) s.get( Employer.class, emp.getId() );
		assertTrue( Hibernate.isInitialized( emp ) );
		assertFalse( Hibernate.isInitialized( emp.getEmployees() ) );
		node = ( Node ) s.get( Node.class, node.getName() );
		assertTrue( Hibernate.isInitialized( node ) );
		assertFalse( Hibernate.isInitialized( node.getChildren() ) );
		assertFalse( Hibernate.isInitialized( node.getParent() ) );
		assertNull( s.get( Node.class, "xyz" ) );
		em.getTransaction().commit();
		em.close();

		em = getOrCreateEntityManager();
		em.getTransaction().begin();
		s = ( Session ) em.getDelegate();
		emp = ( Employer ) s.load( Employer.class, emp.getId() );
		emp.getId();
		assertFalse( Hibernate.isInitialized( emp ) );
		node = ( Node ) s.load( Node.class, node.getName() );
		assertEquals( node.getName(), "foo" );
		assertFalse( Hibernate.isInitialized( node ) );
		em.getTransaction().commit();
		em.close();

		em = getOrCreateEntityManager();
		em.getTransaction().begin();
		s = ( Session ) em.getDelegate();
		emp = ( Employer ) s.get( "org.hibernate.ejb.test.ops.Employer", emp.getId() );
		assertTrue( Hibernate.isInitialized( emp ) );
		node = ( Node ) s.get( "org.hibernate.ejb.test.ops.Node", node.getName() );
		assertTrue( Hibernate.isInitialized( node ) );
		em.getTransaction().commit();
		em.close();

		em = getOrCreateEntityManager();
		em.getTransaction().begin();
		s = ( Session ) em.getDelegate();
		emp = ( Employer ) s.load( "org.hibernate.ejb.test.ops.Employer", emp.getId() );
		emp.getId();
		assertFalse( Hibernate.isInitialized( emp ) );
		node = ( Node ) s.load( "org.hibernate.ejb.test.ops.Node", node.getName() );
		assertEquals( node.getName(), "foo" );
		assertFalse( Hibernate.isInitialized( node ) );
		em.getTransaction().commit();
		em.close();

		assertFetchCount( 0 );
	}

	private void clearCounts() {
		( ( EntityManagerFactoryImpl ) factory ).getSessionFactory().getStatistics().clear();
	}

	private void assertFetchCount(int count) {
		int fetches = ( int ) ( ( EntityManagerFactoryImpl ) factory ).getSessionFactory()
				.getStatistics()
				.getEntityFetchCount();
		assertEquals( count, fetches );
	}

	@Override
	protected void addConfigOptions(Map options) {
		options.put( Environment.GENERATE_STATISTICS, "true" );
		options.put( Environment.STATEMENT_BATCH_SIZE, "0" );
	}

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[0];
	}

	protected String[] getMappings() {
		return new String[] {
				"org/hibernate/ejb/test/ops/Node.hbm.xml",
				"org/hibernate/ejb/test/ops/Employer.hbm.xml"
		};
	}
}

