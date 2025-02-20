//$Id$
package org.hibernate.test.annotations.cid;

import java.util.Date;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.test.annotations.TestCase;

/**
 * test some composite id functionalities
 *
 * @author Emmanuel Bernard
 */
public class CompositeIdTest extends TestCase {
	public CompositeIdTest(String x) {
		super( x );
	}

	public void testOneToOneInCompositePk() throws Exception {
		Session s;
		Transaction tx;
		s = openSession();
		tx = s.beginTransaction();
		B b = new B();
		C c = new C();
		s.persist( b );
		s.persist( c );
		A a = new A();
		a.setAId( new AId() );
		a.getAId().setB( b );
		a.getAId().setC( c );
		s.persist( a );
		s.flush();
		s.clear();

		a = (A) s.get(A.class, a.getAId() );
		assertEquals( b.getId(), a.getAId().getB().getId() );

		tx.rollback();
		s.close();
	}


	/**
	 * This feature is not supported by the EJB3
	 * this is an hibernate extension
	 */
	public void testManyToOneInCompositePk() throws Exception {
		Session s;
		Transaction tx;
		s = openSession();
		tx = s.beginTransaction();
		ParentPk ppk = new ParentPk();
		ppk.setFirstName( "Emmanuel" );
		ppk.setLastName( "Bernard" );
		Parent p = new Parent();
		p.id = ppk;
		s.persist( p );
		ChildPk cpk = new ChildPk();
		cpk.parent = p;
		cpk.nthChild = 1;
		Child c = new Child();
		c.id = cpk;
		s.persist( c );
		tx.commit();
		s.close();

		s = openSession();
		tx = s.beginTransaction();
		Query q = s.createQuery( "select c from Child c where c.id.nthChild = :nth" );
		q.setInteger( "nth", 1 );
		List results = q.list();
		assertEquals( 1, results.size() );
		c = (Child) results.get( 0 );
		assertNotNull( c );
		assertNotNull( c.id.parent );
		//FIXME mke it work in unambigious cases
		//		assertNotNull(c.id.parent.id);
		//		assertEquals(p.id.getFirstName(), c.id.parent.id.getFirstName());
		s.delete( c );
		s.delete( c.id.parent );
		tx.commit();
		s.close();
	}

	/**
	 * This feature is not supported by the EJB3
	 * this is an hibernate extension
	 */
	public void testManyToOneInCompositePkAndSubclass() throws Exception {
		Session s;
		Transaction tx;
		s = openSession();
		tx = s.beginTransaction();
		ParentPk ppk = new ParentPk();
		ppk.setFirstName( "Emmanuel" );
		ppk.setLastName( "Bernard" );
		Parent p = new Parent();
		p.id = ppk;
		s.persist( p );
		ChildPk cpk = new ChildPk();
		cpk.parent = p;
		cpk.nthChild = 1;
		LittleGenius c = new LittleGenius();
		c.particularSkill = "Human Annotation parser";
		c.id = cpk;
		s.persist( c );
		tx.commit();
		s.close();

		s = openSession();
		tx = s.beginTransaction();
		Query q = s.createQuery( "select c from Child c where c.id.nthChild = :nth" );
		q.setInteger( "nth", 1 );
		List results = q.list();
		assertEquals( 1, results.size() );
		c = (LittleGenius) results.get( 0 );
		assertNotNull( c );
		assertNotNull( c.id.parent );
		//FIXME mke it work in unambigious cases
//		assertNotNull(c.id.parent.id);
//		assertEquals(p.id.getFirstName(), c.id.parent.id.getFirstName());
		s.delete( c );
		s.delete( c.id.parent );
		tx.commit();
		s.close();
	}

	public void testManyToOneInCompositeId() throws Exception {
		Session s = openSession();
		Transaction tx = s.beginTransaction();
		Channel channel = new Channel();
		s.persist( channel );
		Presenter pres = new Presenter();
		pres.name = "Casimir";
		s.persist( pres );
		TvMagazinPk pk = new TvMagazinPk();
		TvMagazin mag = new TvMagazin();
		mag.time = new Date();
		mag.id = pk;
		//pk.name = "Trax";
		pk.channel = channel;
		pk.presenter = pres;
		s.persist( mag );
		tx.commit();
		s.clear();
		tx = s.beginTransaction();
		mag = (TvMagazin) s.createQuery( "from TvMagazin mag" ) // where mag.id.name = :name")
				//.setParameter( "name", "Trax" )
				.uniqueResult();
		assertNotNull( mag.id );
		assertNotNull( mag.id.channel );
		assertEquals( channel.id, mag.id.channel.id );
		assertNotNull( mag.id.presenter );
		assertEquals( pres.name, mag.id.presenter.name );
		s.delete( mag );
		s.delete( mag.id.channel );
		s.delete( mag.id.presenter );
		tx.commit();
		s.close();
	}

	public void testManyToOneInCompositeIdClass() throws Exception {
		Session s = openSession();
		Transaction tx = s.beginTransaction();
		Order order = new Order();
		s.persist( order );
		Product product = new Product();
		product.name = "small car";
		s.persist( product );
		OrderLinePk pk = new OrderLinePk();
		OrderLine orderLine = new OrderLine();
		orderLine.order = order;
		orderLine.product = product;
		s.persist( orderLine );
		s.flush();
		s.clear();

		orderLine = (OrderLine) s.createQuery( "select ol from OrderLine ol" ).uniqueResult();
		assertNotNull( orderLine.order );
		assertEquals( order.id, orderLine.order.id );
		assertNotNull( orderLine.product );
		assertEquals( product.name, orderLine.product.name );

		tx.rollback();
		s.close();
	}

	public void testSecondaryTableWithCompositeId() throws Exception {
		Session s = openSession();
		Transaction tx = s.beginTransaction();
		Channel channel = new Channel();
		s.persist( channel );
		Presenter pres = new Presenter();
		pres.name = "Tim Russet";
		s.persist( pres );
		TvMagazinPk pk = new TvMagazinPk();
		TvProgram program = new TvProgram();
		program.time = new Date();
		program.id = pk;
		program.text = "Award Winning Programming";
		//pk.name = "Trax";
		pk.channel = channel;
		pk.presenter = pres;
		s.persist( program );
		tx.commit();
		s.clear();
		tx = s.beginTransaction();
		program = (TvProgram) s.createQuery( "from TvProgram pr" ) // where mag.id.name = :name")
				//.setParameter( "name", "Trax" )
				.uniqueResult();
		assertNotNull( program.id );
		assertNotNull( program.id.channel );
		assertEquals( channel.id, program.id.channel.id );
		assertNotNull( program.id.presenter );
		assertNotNull( program.text );
		assertEquals( pres.name, program.id.presenter.name );
		s.delete( program );
		s.delete( program.id.channel );
		s.delete( program.id.presenter );
		tx.commit();
		s.close();
	}

	public void testSecondaryTableWithIdClass() throws Exception {
		Session s = openSession();
		Transaction tx = s.beginTransaction();
		Channel channel = new Channel();
		s.persist( channel );
		Presenter pres = new Presenter();
		pres.name = "Bob";
		s.persist( pres );
		TvProgramIdClass program = new TvProgramIdClass();
		program.time = new Date();
		program.channel = channel;
		program.presenter = pres;
		program.text = "Jump the shark programming";
		//pk.name = "Trax";
		s.persist( program );
		tx.commit();
		s.clear();
		tx = s.beginTransaction();
		program = (TvProgramIdClass) s.createQuery( "from TvProgramIdClass pr" ) // where mag.id.name = :name")
				//.setParameter( "name", "Trax" )
				.uniqueResult();
		assertNotNull( program.channel );
		assertEquals( channel.id, program.channel.id );
		assertNotNull( program.presenter );
		assertNotNull( program.text );
		assertEquals( pres.name, program.presenter.name );
		s.delete( program );
		s.delete( program.channel );
		s.delete( program.presenter );
		tx.commit();
		s.close();
	}

	protected Class[] getMappings() {
		return new Class[] {
				Parent.class,
				Child.class,
				Channel.class,
				TvMagazin.class,
				TvProgramIdClass.class,
				TvProgram.class,
				Presenter.class,
				Order.class,
				Product.class,
				OrderLine.class,
				OrderLinePk.class,
				LittleGenius.class,
				A.class,
				B.class,
				C.class
		};
	}
}
