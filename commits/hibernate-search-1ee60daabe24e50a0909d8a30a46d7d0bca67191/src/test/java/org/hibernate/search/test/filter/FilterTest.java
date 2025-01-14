// $Id$
package org.hibernate.search.test.filter;

import java.util.Calendar;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.RangeFilter;
import org.apache.lucene.search.TermQuery;
import org.hibernate.Session;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.test.SearchTestCase;

/**
 * @author Emmanuel Bernard
 * @author Hardy Ferentschik
 */
public class FilterTest extends SearchTestCase {

	public void testNamedFilters() {
		createData();
		FullTextSession s = Search.getFullTextSession( openSession( ) );
		s.getTransaction().begin();
		BooleanQuery query = new BooleanQuery();
		query.add( new TermQuery( new Term("teacher", "andre") ), BooleanClause.Occur.SHOULD );
		query.add( new TermQuery( new Term("teacher", "max") ), BooleanClause.Occur.SHOULD );
		query.add( new TermQuery( new Term("teacher", "aaron") ), BooleanClause.Occur.SHOULD );
		FullTextQuery ftQuery = s.createFullTextQuery( query, Driver.class );
		assertEquals("No filter should happen", 3, ftQuery.getResultSize() );

		ftQuery = s.createFullTextQuery( query, Driver.class );
		ftQuery.disableFullTextFilter( "bestDriver" ); //was not enabled, but should be harmless
		ftQuery.enableFullTextFilter( "bestDriver" );
		assertEquals("Should filter out Gavin", 2, ftQuery.getResultSize() );

		ftQuery = s.createFullTextQuery( query, Driver.class );
		ftQuery.enableFullTextFilter( "bestDriver");
		ftQuery.enableFullTextFilter( "security").setParameter( "login", "andre" );
		assertEquals("Should filter to limit to Emmanuel", 1, ftQuery.getResultSize() );

		ftQuery = s.createFullTextQuery( query, Driver.class );
		ftQuery.enableFullTextFilter( "bestDriver");
		ftQuery.enableFullTextFilter( "security").setParameter( "login", "andre" );
		ftQuery.disableFullTextFilter( "security");
		ftQuery.disableFullTextFilter( "bestDriver");
		assertEquals("Should not filter anymore", 3, ftQuery.getResultSize() );

		s.getTransaction().commit();
		s.close();
		deleteData();
	}

	public void testCache() {
		createData();
		FullTextSession s = Search.getFullTextSession( openSession( ) );
		s.getTransaction().begin();
		BooleanQuery query = new BooleanQuery();
		query.add( new TermQuery( new Term("teacher", "andre") ), BooleanClause.Occur.SHOULD );
		query.add( new TermQuery( new Term("teacher", "max") ), BooleanClause.Occur.SHOULD );
		query.add( new TermQuery( new Term("teacher", "aaron") ), BooleanClause.Occur.SHOULD );
		FullTextQuery ftQuery = s.createFullTextQuery( query, Driver.class );
		assertEquals("No filter should happen", 3, ftQuery.getResultSize() );

		ftQuery = s.createFullTextQuery( query, Driver.class );
		ftQuery.enableFullTextFilter( "cacheresultstest");
		assertEquals("Should filter out all", 0, ftQuery.getResultSize() );
		
		// HSEARCH-174 - we call System.gc() to force a garbage collection.
		// Prior to the fix for HSEARCH-174 this would cause the filter to be 
		// garbage collected since Lucene used weak references.
		System.gc();

		ftQuery = s.createFullTextQuery( query, Driver.class );
		ftQuery.enableFullTextFilter( "cacheresultstest");
		try {
			ftQuery.getResultSize();
		}
		catch (IllegalStateException e) {
			fail("Cache results does not work");
		}

		ftQuery = s.createFullTextQuery( query, Driver.class );
		ftQuery.enableFullTextFilter( "cacheinstancetest");
		assertEquals("Should filter out all", 0, ftQuery.getResultSize() );

		ftQuery = s.createFullTextQuery( query, Driver.class );
		ftQuery.enableFullTextFilter( "cacheinstancetest");
		try {
			ftQuery.getResultSize();
			fail("Cache instance does not work");
		}
		catch (IllegalStateException e) {
			//success
		}

		s.getTransaction().commit();
		s.close();
		deleteData();
	}

	public void testStraightFilters() {
		createData();
		FullTextSession s = Search.getFullTextSession( openSession( ) );
		s.getTransaction().begin();
		BooleanQuery query = new BooleanQuery();
		query.add( new TermQuery( new Term("teacher", "andre") ), BooleanClause.Occur.SHOULD );
		query.add( new TermQuery( new Term("teacher", "max") ), BooleanClause.Occur.SHOULD );
		query.add( new TermQuery( new Term("teacher", "aaron") ), BooleanClause.Occur.SHOULD );
		FullTextQuery ftQuery;

		ftQuery = s.createFullTextQuery( query, Driver.class );
		ftQuery.enableFullTextFilter( "bestDriver");
		Filter dateFilter = new RangeFilter("delivery", "2001", "2005",
				true, true);
		ftQuery.setFilter( dateFilter );
		assertEquals("Should select only liz", 1, ftQuery.getResultSize() );

		ftQuery = s.createFullTextQuery( query, Driver.class );
		ftQuery.setFilter( dateFilter );
		ftQuery.enableFullTextFilter( "bestDriver");
		ftQuery.enableFullTextFilter( "security").setParameter( "login", "andre" );
		ftQuery.disableFullTextFilter( "security");
		ftQuery.disableFullTextFilter( "bestDriver");
		ftQuery.setFilter( null );
		assertEquals("Should not filter anymore", 3, ftQuery.getResultSize() );

		s.getTransaction().commit();
		s.close();
		deleteData();
	}


	private void deleteData() {
		Session s = openSession( );
		s.getTransaction().begin();
		s.createQuery( "delete " + Driver.class.getName() + " t").executeUpdate();
		s.getTransaction().commit();
		s.close();
	}

	private void createData() {
		Session s = openSession( );
		s.getTransaction().begin();
		Calendar cal = Calendar.getInstance();
		cal.set( 2006, 10, 11);
		Driver driver = new Driver();
		driver.setDelivery( cal.getTime() );
		driver.setId( 1 );
		driver.setName( "Emmanuel" );
		driver.setScore( 5 );
		driver.setTeacher( "andre" );
		s.persist( driver );

		cal.set( 2007, 10, 11);
		driver = new Driver();
		driver.setDelivery( cal.getTime() );
		driver.setId( 2 );
		driver.setName( "Gavin" );
		driver.setScore( 3 );
		driver.setTeacher( "aaron" );
		s.persist( driver );

		cal.set( 2004, 10, 11);
		driver = new Driver();
		driver.setDelivery( cal.getTime() );
		driver.setId( 3 );
		driver.setName( "Liz" );
		driver.setScore( 5 );
		driver.setTeacher( "max" );
		s.persist( driver );
		s.getTransaction().commit();
		s.close();
	}

	@SuppressWarnings("unchecked")
	protected Class[] getMappings() {
		return new Class[] {
				Driver.class,
				Soap.class
		};
	}
	
	protected void configure(org.hibernate.cfg.Configuration cfg) {
		super.configure(cfg);
		cfg.setProperty( "hibernate.search.filter.cache_docidresults.size", "10" );
	}	
}
