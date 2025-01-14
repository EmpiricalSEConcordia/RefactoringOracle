//$Id$
package org.hibernate.search.test.query;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Date;

import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;

import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.SearchException;
import org.hibernate.search.test.SearchTestCase;

/**
 * Tests several aspects of projection queries.
 *
 * @author Emmanuel Bernard
 * @author John Griffin
 * @author Hardy Ferentschik
 */
public class ProjectionQueryTest extends SearchTestCase {

	/**
	 * HSEARCH-296
	 *
	 * @throws Exception in case the test fails.
	 */
	public void testClassProjection() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		prepEmployeeIndex( s );

		s.clear();
		Transaction tx = s.beginTransaction();
		QueryParser parser = new QueryParser( "dept", new StandardAnalyzer() );
		Query query = parser.parse( "dept:ITech" );
		org.hibernate.search.FullTextQuery hibQuery = s.createFullTextQuery( query, Employee.class );
		hibQuery.setProjection( FullTextQuery.OBJECT_CLASS );

		List result = hibQuery.list();
		assertNotNull( result );

		Object[] projection = ( Object[] ) result.get( 0 );
		assertNotNull( projection );
		assertEquals( "Wrong projected class", Employee.class, projection[0] );

		tx.commit();
		s.close();
	}

	public void testLuceneObjectsProjectionWithScroll() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		prepEmployeeIndex( s );

		Transaction tx;
		s.clear();
		tx = s.beginTransaction();
		QueryParser parser = new QueryParser( "dept", new StandardAnalyzer() );

		Query query = parser.parse( "dept:ITech" );
		org.hibernate.search.FullTextQuery hibQuery = s.createFullTextQuery( query, Employee.class );
		// Is the 'FullTextQuery.ID' value correct here? Do we want the Lucene internal document number?
		hibQuery.setProjection(
				"id",
				"lastname",
				"dept",
				FullTextQuery.THIS,
				FullTextQuery.SCORE,
				FullTextQuery.DOCUMENT,
				FullTextQuery.ID
		);

		ScrollableResults projections = hibQuery.scroll();

		// There are a lot of methods to check in ScrollableResultsImpl
		// so, we'll use methods to check each projection as needed.

		projections.beforeFirst();
		projections.next();
		Object[] projection = projections.get();
		checkProjectionFirst( projection, s );
		assertTrue( projections.isFirst() );

		projections.last();
		projection = projections.get();
		checkProjectionLast( projection, s );
		assertTrue( projections.isLast() );

		projections.next();
		projection = projections.get();
		assertNull( projection );

		projections.previous();
		projection = projections.get();
		checkProjectionLast( projection, s );

		projections.first();
		projection = projections.get();
		checkProjectionFirst( projection, s );

		projections.scroll( 2 );
		projection = projections.get();
		checkProjection2( projection, s );

		projections.scroll( -5 );
		projection = projections.get();
		assertNull( projection );

		//cleanup
		for ( Object element : s.createQuery( "from " + Employee.class.getName() ).list() ) {
			s.delete( element );
		}
		tx.commit();
		s.close();
	}

	public void testResultTransformToDelimString() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		prepEmployeeIndex( s );

		Transaction tx;
		s.clear();
		tx = s.beginTransaction();
		QueryParser parser = new QueryParser( "dept", new StandardAnalyzer() );

		Query query = parser.parse( "dept:ITech" );
		org.hibernate.search.FullTextQuery hibQuery = s.createFullTextQuery( query, Employee.class );
		hibQuery.setProjection( "id", "lastname", "dept", FullTextQuery.THIS, FullTextQuery.SCORE, FullTextQuery.ID );
		hibQuery.setResultTransformer( new ProjectionToDelimStringResultTransformer() );

		@SuppressWarnings("unchecked")
		List<String> result = hibQuery.list();
		assertTrue( "incorrect transformation", result.get( 0 ).startsWith( "1000, Griffin, ITech" ) );
		assertTrue( "incorrect transformation", result.get( 1 ).startsWith( "1002, Jimenez, ITech" ) );

		//cleanup
		for ( Object element : s.createQuery( "from " + Employee.class.getName() ).list() ) {
			s.delete( element );
		}
		tx.commit();
		s.close();
	}

	public void testResultTransformMap() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		prepEmployeeIndex( s );

		Transaction tx;
		s.clear();
		tx = s.beginTransaction();
		QueryParser parser = new QueryParser( "dept", new StandardAnalyzer() );

		Query query = parser.parse( "dept:ITech" );
		org.hibernate.search.FullTextQuery hibQuery = s.createFullTextQuery( query, Employee.class );
		hibQuery.setProjection(
				"id",
				"lastname",
				"dept",
				FullTextQuery.THIS,
				FullTextQuery.SCORE,
				FullTextQuery.DOCUMENT,
				FullTextQuery.ID
		);

		hibQuery.setResultTransformer( new ProjectionToMapResultTransformer() );

		List transforms = hibQuery.list();
		Map map = ( Map ) transforms.get( 1 );
		assertEquals( "incorrect transformation", "ITech", map.get( "dept" ) );
		assertEquals( "incorrect transformation", 1002, map.get( "id" ) );
		assertTrue( "incorrect transformation", map.get( FullTextQuery.DOCUMENT ) instanceof Document );
		assertEquals(
				"incorrect transformation", "1002", ( ( Document ) map.get( FullTextQuery.DOCUMENT ) ).get( "id" )
		);

		//cleanup
		for ( Object element : s.createQuery( "from " + Employee.class.getName() ).list() ) {
			s.delete( element );
		}
		tx.commit();
		s.close();
	}

	private void checkProjectionFirst(Object[] projection, Session s) {
		assertEquals( "id incorrect", 1000, projection[0] );
		assertEquals( "lastname incorrect", "Griffin", projection[1] );
		assertEquals( "dept incorrect", "ITech", projection[2] );
		assertEquals( "THIS incorrect", projection[3], s.get( Employee.class, ( Serializable ) projection[0] ) );
		assertTrue( "SCORE incorrect", projection[4] instanceof Float );
		assertTrue( "DOCUMENT incorrect", projection[5] instanceof Document );
		assertEquals( "DOCUMENT size incorrect", 4, ( ( Document ) projection[5] ).getFields().size() );
		assertEquals( "legacy ID incorrect", 1000, projection[6] );
	}

	private void checkProjectionLast(Object[] projection, Session s) {
		assertEquals( "id incorrect", 1004, projection[0] );
		assertEquals( "lastname incorrect", "Whetbrook", projection[1] );
		assertEquals( "dept incorrect", "ITech", projection[2] );
		assertEquals( "THIS incorrect", projection[3], s.get( Employee.class, ( Serializable ) projection[0] ) );
		assertTrue( "SCORE incorrect", projection[4] instanceof Float );
		assertTrue( "DOCUMENT incorrect", projection[5] instanceof Document );
		assertEquals( "DOCUMENT size incorrect", 4, ( ( Document ) projection[5] ).getFields().size() );
		assertEquals( "legacy ID incorrect", 1004, projection[6] );
	}

	private void checkProjection2(Object[] projection, Session s) {
		assertEquals( "id incorrect", 1003, projection[0] );
		assertEquals( "lastname incorrect", "Stejskal", projection[1] );
		assertEquals( "dept incorrect", "ITech", projection[2] );
		assertEquals( "THIS incorrect", projection[3], s.get( Employee.class, ( Serializable ) projection[0] ) );
		assertTrue( "SCORE incorrect", projection[4] instanceof Float );
		assertTrue( "DOCUMENT incorrect", projection[5] instanceof Document );
		assertEquals( "DOCUMENT size incorrect", 4, ( ( Document ) projection[5] ).getFields().size() );
		assertEquals( "legacy ID incorrect", 1003, projection[6] );
	}

	public void testLuceneObjectsProjectionWithIterate() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		prepEmployeeIndex( s );

		Transaction tx;
		s.clear();
		tx = s.beginTransaction();
		QueryParser parser = new QueryParser( "dept", new StandardAnalyzer() );

		Query query = parser.parse( "dept:ITech" );
		org.hibernate.search.FullTextQuery hibQuery = s.createFullTextQuery( query, Employee.class );
		hibQuery.setProjection(
				"id", "lastname", "dept", FullTextQuery.THIS, FullTextQuery.SCORE,
				FullTextQuery.DOCUMENT, FullTextQuery.ID
		);

		int counter = 0;

		for ( Iterator iter = hibQuery.iterate(); iter.hasNext(); ) {
			Object[] projection = ( Object[] ) iter.next();
			assertNotNull( projection );
			counter++;
			assertEquals( "dept incorrect", "ITech", projection[2] );
			assertEquals( "THIS incorrect", projection[3], s.get( Employee.class, ( Serializable ) projection[0] ) );
			assertTrue( "SCORE incorrect", projection[4] instanceof Float );
			assertTrue( "DOCUMENT incorrect", projection[5] instanceof Document );
			assertEquals( "DOCUMENT size incorrect", 4, ( ( Document ) projection[5] ).getFields().size() );
		}
		assertEquals( "incorrect number of results returned", 4, counter );

		//cleanup
		for ( Object element : s.createQuery( "from " + Employee.class.getName() ).list() ) {
			s.delete( element );
		}
		tx.commit();
		s.close();
	}

	public void testLuceneObjectsProjectionWithList() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		prepEmployeeIndex( s );

		Transaction tx;
		s.clear();
		tx = s.beginTransaction();
		QueryParser parser = new QueryParser( "dept", new StandardAnalyzer() );

		Query query = parser.parse( "dept:Accounting" );
		org.hibernate.search.FullTextQuery hibQuery = s.createFullTextQuery( query, Employee.class );
		hibQuery.setProjection(
				"id", "lastname", "dept", FullTextQuery.THIS, FullTextQuery.SCORE,
				FullTextQuery.DOCUMENT, FullTextQuery.ID, FullTextQuery.DOCUMENT_ID
		);

		List result = hibQuery.list();
		assertNotNull( result );

		Object[] projection = ( Object[] ) result.get( 0 );
		assertNotNull( projection );
		assertEquals( "id incorrect", 1001, projection[0] );
		assertEquals( "last name incorrect", "Jackson", projection[1] );
		assertEquals( "dept incorrect", "Accounting", projection[2] );
		assertEquals( "THIS incorrect", "Jackson", ( ( Employee ) projection[3] ).getLastname() );
		assertEquals( "THIS incorrect", projection[3], s.get( Employee.class, ( Serializable ) projection[0] ) );
		assertTrue( "SCORE incorrect", projection[4] instanceof Float );
		assertTrue( "DOCUMENT incorrect", projection[5] instanceof Document );
		assertEquals( "DOCUMENT size incorrect", 5, ( ( Document ) projection[5] ).getFields().size() );
		assertEquals( "ID incorrect", 1001, projection[6] );
		assertNotNull( "Lucene internal doc id", projection[7] );

		// Change the projection order and null one
		hibQuery.setProjection(
				FullTextQuery.DOCUMENT, FullTextQuery.THIS, FullTextQuery.SCORE, null, FullTextQuery.ID,
				"id", "lastname", "dept", "hireDate", FullTextQuery.DOCUMENT_ID
		);

		result = hibQuery.list();
		assertNotNull( result );

		projection = ( Object[] ) result.get( 0 );
		assertNotNull( projection );

		assertTrue( "DOCUMENT incorrect", projection[0] instanceof Document );
		assertEquals( "DOCUMENT size incorrect", 5, ( ( Document ) projection[0] ).getFields().size() );
		assertEquals( "THIS incorrect", projection[1], s.get( Employee.class, ( Serializable ) projection[4] ) );
		assertTrue( "SCORE incorrect", projection[2] instanceof Float );
		assertNull( "BOOST not removed", projection[3] );
		assertEquals( "ID incorrect", 1001, projection[4] );
		assertEquals( "id incorrect", 1001, projection[5] );
		assertEquals( "last name incorrect", "Jackson", projection[6] );
		assertEquals( "dept incorrect", "Accounting", projection[7] );
		assertNotNull( "Date", projection[8] );
		assertNotNull( "Lucene internal doc id", projection[9] );

		//cleanup
		for ( Object element : s.createQuery( "from " + Employee.class.getName() ).list() ) {
			s.delete( element );
		}
		tx.commit();
		s.close();
	}

	public void testNonLoadedFieldOptmization() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		prepEmployeeIndex( s );

		Transaction tx;
		s.clear();
		tx = s.beginTransaction();
		QueryParser parser = new QueryParser( "dept", new StandardAnalyzer() );

		Query query = parser.parse( "dept:Accounting" );
		org.hibernate.search.FullTextQuery hibQuery = s.createFullTextQuery( query, Employee.class );
		hibQuery.setProjection( FullTextQuery.ID, FullTextQuery.DOCUMENT );

		List result = hibQuery.list();
		assertNotNull( result );

		Object[] projection = ( Object[] ) result.get( 0 );
		assertNotNull( projection );
		assertEquals( "id field name not projected", 1001, projection[0] );
		assertEquals(
				"Document fields should not be lazy on DOCUMENT projection",
				"Jackson", ( ( Document ) projection[1] ).getField( "lastname" ).stringValue()
		);
		assertEquals( "DOCUMENT size incorrect", 5, ( ( Document ) projection[1] ).getFields().size() );

		// Change the projection order and null one
		hibQuery.setProjection( FullTextQuery.THIS, FullTextQuery.SCORE, null, "lastname" );

		result = hibQuery.list();
		assertNotNull( result );

		projection = ( Object[] ) result.get( 0 );
		assertNotNull( projection );

		assertTrue( "THIS incorrect", projection[0] instanceof Employee );
		assertTrue( "SCORE incorrect", projection[1] instanceof Float );
		assertEquals( "last name incorrect", "Jackson", projection[3] );

		//cleanup
		for ( Object element : s.createQuery( "from " + Employee.class.getName() ).list() ) {
			s.delete( element );
		}
		tx.commit();
		s.close();
	}

	private void prepEmployeeIndex(FullTextSession s) {
		Transaction tx = s.beginTransaction();
		Employee e1 = new Employee( 1000, "Griffin", "ITech" );
		s.save( e1 );
		Employee e2 = new Employee( 1001, "Jackson", "Accounting" );
		e2.setHireDate( new Date() );
		s.save( e2 );
		Employee e3 = new Employee( 1002, "Jimenez", "ITech" );
		s.save( e3 );
		Employee e4 = new Employee( 1003, "Stejskal", "ITech" );
		s.save( e4 );
		Employee e5 = new Employee( 1004, "Whetbrook", "ITech" );
		s.save( e5 );

		tx.commit();
	}

	public void testProjection() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		Transaction tx = s.beginTransaction();
		Book book = new Book(
				1,
				"La chute de la petite reine a travers les yeux de Festina",
				"La chute de la petite reine a travers les yeux de Festina, blahblah"
		);
		s.save( book );
		Book book2 = new Book( 2, "Sous les fleurs il n'y a rien", null );
		s.save( book2 );
		Author emmanuel = new Author();
		emmanuel.setName( "Emmanuel" );
		s.save( emmanuel );
		book.setMainAuthor( emmanuel );
		tx.commit();
		s.clear();
		tx = s.beginTransaction();
		QueryParser parser = new QueryParser( "title", new StopAnalyzer() );

		Query query = parser.parse( "summary:Festina" );
		org.hibernate.search.FullTextQuery hibQuery = s.createFullTextQuery( query, Book.class );
		hibQuery.setProjection( "id", "summary", "mainAuthor.name" );

		List result = hibQuery.list();
		assertNotNull( result );
		assertEquals( "Query with no explicit criteria", 1, result.size() );
		Object[] projection = ( Object[] ) result.get( 0 );
		assertEquals( "id", 1, projection[0] );
		assertEquals( "summary", "La chute de la petite reine a travers les yeux de Festina", projection[1] );
		assertEquals( "mainAuthor.name (embedded objects)", "Emmanuel", projection[2] );

		hibQuery = s.createFullTextQuery( query, Book.class );
		hibQuery.setProjection( "id", "body", "mainAuthor.name" );

		try {
			hibQuery.list();
			fail( "Projecting an unstored field should raise an exception" );
		}
		catch ( SearchException e ) {
			//success
		}


		hibQuery = s.createFullTextQuery( query, Book.class );
		hibQuery.setProjection();
		result = hibQuery.list();
		assertNotNull( result );
		assertEquals( 1, result.size() );
		assertTrue( "Should not trigger projection", result.get( 0 ) instanceof Book );

		hibQuery = s.createFullTextQuery( query, Book.class );
		hibQuery.setProjection( (String[]) null );
		result = hibQuery.list();
		assertNotNull( result );
		assertEquals( 1, result.size() );
		assertTrue( "Should not trigger projection", result.get( 0 ) instanceof Book );

		query = parser.parse( "summary:fleurs" );
		hibQuery = s.createFullTextQuery( query, Book.class );
		hibQuery.setProjection( "id", "summary", "mainAuthor.name" );
		result = hibQuery.list();
		assertEquals( 1, result.size() );
		projection = ( Object[] ) result.get( 0 );
		assertEquals( "mainAuthor.name", null, projection[2] );

		//cleanup
		for ( Object element : s.createQuery( "from " + Book.class.getName() ).list() ) {
			s.delete( element );
		}
		for ( Object element : s.createQuery( "from " + Author.class.getName() ).list() ) {
			s.delete( element );
		}
		tx.commit();
		s.close();
	}

	protected Class[] getMappings() {
		return new Class[] {
				Book.class,
				Author.class,
				Employee.class
		};
	}
}
