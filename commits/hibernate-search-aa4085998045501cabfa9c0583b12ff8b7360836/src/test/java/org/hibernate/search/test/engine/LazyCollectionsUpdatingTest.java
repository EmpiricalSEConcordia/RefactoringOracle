/* $Id$
 * 
 * Hibernate, Relational Persistence for Idiomatic Java
 * 
 * Copyright (c) 2009, Red Hat, Inc. and/or its affiliates or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat, Inc.
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
package org.hibernate.search.test.engine;

import java.util.List;

import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.hibernate.Transaction;
import org.hibernate.search.Environment;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.store.RAMDirectoryProvider;
import org.hibernate.search.test.SearchTestCase;

/**
 * TestCase for HSEARCH-178 (Search hitting HHH-2763)
 * Verifies that it's possible to index lazy loaded collections from
 * indexed entities even when no transactions are used.
 *
 * @author Sanne Grinovero
 */
public class LazyCollectionsUpdatingTest extends SearchTestCase {
	
	public void testUpdatingInTransaction() {
		assertFindsByRoadName( "buonarroti" );
		FullTextSession fullTextSession = Search.getFullTextSession( sessions.openSession() );
		try {
			Transaction tx = fullTextSession.beginTransaction();
			BusStop busStop = (BusStop) fullTextSession.get( BusStop.class, 1L );
			busStop.setRoadName( "new road" );
			tx.commit();
		}
		catch (org.hibernate.AssertionFailure ass) {
			fail( ass.getMessage() );
		}
		finally {
			fullTextSession.close();
		}
		assertFindsByRoadName( "new" );
	}
	
	public void testUpdatingOutOfTransaction() {
		assertFindsByRoadName( "buonarroti" );
		FullTextSession fullTextSession = Search.getFullTextSession( sessions.openSession() );
		try {
			BusStop busStop = (BusStop) fullTextSession.get( BusStop.class, 1L );
			busStop.setRoadName( "new road" );
			fullTextSession.flush();
		}
		catch (org.hibernate.AssertionFailure ass) {
			fail( ass.getMessage() );
		}
		finally {
			fullTextSession.close();
		}
		assertFindsByRoadName( "new" );
	}
	
	public void assertFindsByRoadName(String analyzedRoadname) {
		FullTextSession fullTextSession = Search.getFullTextSession( sessions.openSession() );
		Transaction tx = fullTextSession.beginTransaction();
		TermQuery ftQuery = new TermQuery( new Term( "stops.roadName", analyzedRoadname ) );
		FullTextQuery query = fullTextSession.createFullTextQuery( ftQuery, BusLine.class );
		query.setProjection( "busLineName" );
		assertEquals( 1, query.list().size() );
		List results = query.list();
		String resultName = (String) ((Object[])results.get(0))[0];
		assertEquals( "Linea 64", resultName );
		tx.commit();
		fullTextSession.close();
	}
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		openSession();
		Transaction tx = null;
		try {
			tx = session.beginTransaction();
			BusLine bus = new BusLine();
			bus.setBusLineName( "Linea 64" );
			addBusStop( bus, "Stazione Termini" );
			addBusStop( bus, "via Gregorio VII" );
			addBusStop( bus, "via Alessandro III" );
			addBusStop( bus, "via M.Buonarroti" );
			session.persist( bus );
			tx.commit();
		} catch (Throwable t) {
			if ( tx != null )
				tx.rollback();
		} finally {
			session.close();
		}
	}
	
	private void addBusStop(BusLine bus, String roadName) {
		BusStop stop = new BusStop();
		stop.setRoadName( roadName );
		bus.getStops().add( stop );
		stop.getBusses().add( bus );
	}

	// Test setup options - Entities
	@Override
	protected Class[] getMappings() {
		return new Class[] { BusLine.class, BusStop.class };
	}
	
	// Test setup options - SessionFactory Properties
	@Override
	protected void configure(org.hibernate.cfg.Configuration configuration) {
		super.configure( configuration );
		cfg.setProperty( "hibernate.search.default.directory_provider", RAMDirectoryProvider.class.getName() );
		cfg.setProperty( Environment.ANALYZER_CLASS, SimpleAnalyzer.class.getName() );
	}

}
