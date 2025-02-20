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
package org.hibernate.search.test.session;

import java.sql.Statement;
import java.sql.SQLException;
import java.util.List;
import java.util.Iterator;

import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.search.Environment;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.impl.FullTextSessionImpl;
import org.hibernate.search.test.SearchTestCase;

/**
 * @author Emmanuel Bernard
 */
public class MassIndexTest extends SearchTestCase {

	public void testBatchSize() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		Transaction tx = s.beginTransaction();
		int loop = 14;
		for (int i = 0; i < loop; i++) {
			Statement statmt = s.connection().createStatement();
			statmt.executeUpdate( "insert into Domain(id, name) values( + "
					+ ( i + 1 ) + ", 'sponge" + i + "')" );
			statmt.executeUpdate( "insert into Email(id, title, body, header, domain_id) values( + "
					+ ( i + 1 ) + ", 'Bob Sponge', 'Meet the guys who create the software', 'nope', " + ( i + 1 ) +")" );
			statmt.close();
		}
		tx.commit();
		s.close();

		//check non created object does get found!!1
		s = new FullTextSessionImpl( openSession() );
		tx = s.beginTransaction();
		ScrollableResults results = s.createCriteria( Email.class ).scroll( ScrollMode.FORWARD_ONLY );
		int index = 0;
		while ( results.next() ) {
			index++;
			s.index( results.get( 0 ) );
			if ( index % 5 == 0 ) s.clear();
		}
		tx.commit();
		s.clear();
		tx = s.beginTransaction();
		QueryParser parser = new QueryParser( "id", new StopAnalyzer() );
		List result = s.createFullTextQuery( parser.parse( "body:create" ) ).list();
		assertEquals( 14, result.size() );
		for (Object object : result) {
			s.delete( object );
		}
		tx.commit();
		s.close();
	}


	public void testTransactional() throws Exception {
		FullTextSession s = Search.getFullTextSession( openSession() );
		Transaction tx = s.beginTransaction();
		int loop = 4;
		for (int i = 0; i < loop; i++) {
			Email email = new Email();
			email.setId( (long) i + 1 );
			email.setTitle( "JBoss World Berlin" );
			email.setBody( "Meet the guys who wrote the software" );
			s.persist( email );
		}
		tx.commit();
		s.close();

		//check non created object does get found!!1
		s = new FullTextSessionImpl( openSession() );
		tx = s.beginTransaction();
		QueryParser parser = new QueryParser( "id", new StopAnalyzer() );
		List result = s.createFullTextQuery( parser.parse( "body:create" ) ).list();
		assertEquals( 0, result.size() );
		tx.commit();
		s.close();

		s = new FullTextSessionImpl( openSession() );
		s.getTransaction().begin();
		Statement stmt = s.connection().createStatement();
		stmt.executeUpdate( "update Email set body='Meet the guys who write the software'" );
		stmt.close();
		//insert an object never indexed
		stmt = s.connection().createStatement();
		stmt.executeUpdate( "insert into Email(id, title, body, header) values( + "
				+ ( loop + 1 ) + ", 'Bob Sponge', 'Meet the guys who create the software', 'nope')" );
		stmt.close();
		s.getTransaction().commit();
		s.close();

		s = new FullTextSessionImpl( openSession() );
		tx = s.beginTransaction();
		parser = new QueryParser( "id", new StopAnalyzer() );
		result = s.createFullTextQuery( parser.parse( "body:write" ) ).list();
		assertEquals( 0, result.size() );
		result = s.createCriteria( Email.class ).list();
		for (int i = 0; i < loop / 2; i++)
			s.index( result.get( i ) );
		tx.commit(); //do the process
		s.index( result.get( loop / 2 ) ); //do the process out of tx
		tx = s.beginTransaction();
		for (int i = loop / 2 + 1; i < loop; i++)
			s.index( result.get( i ) );
		tx.commit(); //do the process
		s.close();

		s = Search.getFullTextSession( openSession() );
		tx = s.beginTransaction();
		//object never indexed
		Email email = (Email) s.get( Email.class, Long.valueOf( loop + 1 ) );
		s.index( email );
		tx.commit();
		s.close();

		//check non indexed object get indexed by s.index
		s = new FullTextSessionImpl( openSession() );
		tx = s.beginTransaction();
		result = s.createFullTextQuery( parser.parse( "body:create" ) ).list();
		assertEquals( 1, result.size() );
		tx.commit();
		s.close();
	}

	public void testLazyLoading() throws Exception {
		Categorie cat = new Categorie( "Livre" );
		Entite ent = new Entite( "Le temple des songes", cat );
		Session s = openSession();
		Transaction tx = s.beginTransaction();
		s.persist( cat );
		s.persist( ent );
		tx.commit();
		s.close();

		s = getSessionWithAutoCommit();
		FullTextSession session = Search.getFullTextSession( s );
		Query luceneQuery = new TermQuery( new Term( "categorie.nom", "livre" ) );
		List result = session.createFullTextQuery( luceneQuery, Entite.class ).list();
		assertEquals( 1, result.size() );
		s.close();

		s = getSessionWithAutoCommit();
		ent = (Entite) s.get( Entite.class, ent.getId() );
		session = Search.getFullTextSession( s );
		session.index( ent );
		s.close();

		s = getSessionWithAutoCommit();
		session = Search.getFullTextSession( s );
		luceneQuery = new TermQuery( new Term( "categorie.nom", "livre" ) );
		result = session.createFullTextQuery( luceneQuery, Entite.class ).list();
		assertEquals( "test lazy loading and indexing", 1, result.size() );
		s.close();

		s = getSessionWithAutoCommit();
		Iterator it = s.createQuery( "from Entite where id = :id").setParameter( "id", ent.getId() ).iterate();
		session = Search.getFullTextSession( s );
		while ( it.hasNext() ) {
			ent = (Entite) it.next();
			session.index( ent );
		}
		s.close();

		s = getSessionWithAutoCommit();
		session = Search.getFullTextSession( s );
		luceneQuery = new TermQuery( new Term( "categorie.nom", "livre" ) );
		result = session.createFullTextQuery( luceneQuery, Entite.class ).list();
		assertEquals( "test lazy loading and indexing", 1, result.size() );
		s.close();
	}

	private Session getSessionWithAutoCommit() throws SQLException {
		Session s;
		s = openSession();
		s.connection().setAutoCommit( true );
		return s;
	}

	protected void configure(org.hibernate.cfg.Configuration cfg) {
		super.configure( cfg );
		cfg.setProperty( "hibernate.search.worker.batch_size", "5" );
		cfg.setProperty( Environment.ANALYZER_CLASS, StopAnalyzer.class.getName() );
	}

	protected Class[] getMappings() {
		return new Class[] {
				Email.class,
				Entite.class,
				Categorie.class,
				Domain.class
		};
	}
}
