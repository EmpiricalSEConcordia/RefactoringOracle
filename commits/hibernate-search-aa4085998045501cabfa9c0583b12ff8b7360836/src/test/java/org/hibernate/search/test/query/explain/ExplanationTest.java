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
package org.hibernate.search.test.query.explain;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

import org.hibernate.search.test.SearchTestCase;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.FullTextQuery;
import org.hibernate.Transaction;
import org.apache.lucene.search.Query;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

/**
 * @author Emmanuel Bernard
 */
public class ExplanationTest extends SearchTestCase {
	public void testExplanation() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		Transaction tx = s.beginTransaction();
		Dvd dvd = new Dvd("The dark knight", "Batman returns with it best enomy the Jocker. The dark side of this movies shows up pretty quickly");
		s.persist( dvd );
		dvd = new Dvd("Wall-e", "The tiny little robot comes to Eartch after the dark times and tries to clean it");
		s.persist( dvd );
		tx.commit();
		s.clear();

		tx = s.beginTransaction();
		Map<String, Float> boosts = new HashMap<String, Float>(2);
		boosts.put( "title", new Float(4) );
		boosts.put( "description", new Float(1) );
		MultiFieldQueryParser parser = new MultiFieldQueryParser(new String[] {"title", "description"}, new StandardAnalyzer(), boosts);
		Query luceneQuery = parser.parse( "dark" );
		FullTextQuery ftQuery = s.createFullTextQuery( luceneQuery, Dvd.class )
				.setProjection( FullTextQuery.DOCUMENT_ID, FullTextQuery.EXPLANATION, FullTextQuery.THIS );
		@SuppressWarnings("unchecked") List<Object[]> results = ftQuery.list();
		assertEquals( 2, results.size() );
		for (Object[] result : results) {
			assertEquals( ftQuery.explain( (Integer) result[0] ).toString(), result[1].toString() );
			s.delete( result[2] );
		}
		tx.commit();
		s.close();

	}
	protected Class[] getMappings() {
		return new Class[] {
				Dvd.class
		};
	}
}
