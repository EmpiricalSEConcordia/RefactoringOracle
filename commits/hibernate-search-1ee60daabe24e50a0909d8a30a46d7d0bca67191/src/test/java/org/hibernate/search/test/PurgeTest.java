// $Id$
package org.hibernate.search.test;

import java.util.List;

import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;
import org.hibernate.Transaction;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.test.query.AlternateBook;
import org.hibernate.search.test.query.Author;
import org.hibernate.search.test.query.Book;
import org.hibernate.search.test.query.Clock;
import org.hibernate.search.test.query.Employee;

/**
 * Test the PURGE and PURGE_ALL functionality.
 *
 * @author John Griffin
 */
public class PurgeTest extends SearchTestCase {

	public void testPurge() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		Transaction tx = s.beginTransaction();
		org.hibernate.search.test.query.Clock clock = new Clock( 1, "Seiko" );
		s.save( clock );
		clock = new Clock( 2, "Festina" );
		s.save( clock );
		Book book = new Book( 1, "La chute de la petite reine a travers les yeux de Festina", "La chute de la petite reine a travers les yeux de Festina, blahblah" );
		s.save( book );
		book = new Book( 2, "La gloire de mon p�re", "Les deboires de mon p�re en v�lo" );
		s.save( book );
		tx.commit();
		s.clear();

		tx = s.beginTransaction();
		QueryParser parser = new QueryParser( "brand", new StopAnalyzer() );

		Query query = parser.parse( "brand:Seiko" );
		org.hibernate.Query hibQuery = s.createFullTextQuery( query, Clock.class, Book.class );
		List results = hibQuery.list();
		assertEquals("incorrect test record", 1, results.size());
		assertEquals("incorrect test record", 1, ((Clock)results.get( 0 )).getId().intValue());

		s.purge( Clock.class, ((Clock)results.get( 0 )).getId());

		tx.commit();

		tx = s.beginTransaction();

		query = parser.parse( "brand:Festina or brand:Seiko" );
		hibQuery = s.createFullTextQuery( query, Clock.class, Book.class );
		results = hibQuery.list();
		assertEquals("incorrect test record count", 1, results.size());
		assertEquals("incorrect test record", 2, ((Clock)results.get( 0 )).getId().intValue());

		for (Object element : s.createQuery( "from java.lang.Object" ).list()) s.delete( element );
		tx.commit();
		s.close();
	}

	public void testPurgeAll() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		Transaction tx = s.beginTransaction();
		org.hibernate.search.test.query.Clock clock = new Clock( 1, "Seiko" );
		s.save( clock );
		clock = new Clock( 2, "Festina" );
		s.save( clock );
		clock = new Clock( 3, "Longine" );
		s.save( clock );
		clock = new Clock( 4, "Rolex" );
		s.save( clock );
		Book book = new Book( 1, "La chute de la petite reine a travers les yeux de Festina", "La chute de la petite reine a travers les yeux de Festina, blahblah" );
		s.save( book );
		book = new Book( 2, "La gloire de mon p�re", "Les deboires de mon p�re en v�lo" );
		s.save( book );
		tx.commit();
		s.clear();

		tx = s.beginTransaction();
		QueryParser parser = new QueryParser( "brand", new StopAnalyzer() );
		                     tx = s.beginTransaction();
		s.purgeAll( Clock.class);

		tx.commit();

		tx = s.beginTransaction();

		Query query = parser.parse( "brand:Festina or brand:Seiko or brand:Longine or brand:Rolex" );
		org.hibernate.Query hibQuery = s.createFullTextQuery( query, Clock.class, Book.class );
		List results = hibQuery.list();
		assertEquals("class not completely purged", 0, results.size());

		query = parser.parse( "summary:Festina or summary:gloire" );
		hibQuery = s.createFullTextQuery( query, Clock.class, Book.class );
		results = hibQuery.list();
		assertEquals("incorrect class purged", 2, results.size());

		for (Object element : s.createQuery( "from java.lang.Object" ).list()) s.delete( element );
		tx.commit();
		s.close();
	}

	protected Class[] getMappings() {
		return new Class[] {
				Book.class,
				AlternateBook.class,
				Clock.class,
				Author.class,
				Employee.class
		};
	}
}
