/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2010, Red Hat Middleware LLC or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Middleware LLC.
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
 *
 */
package org.hibernate.test.querycache;
import junit.framework.Test;
import org.hibernate.CacheMode;
import org.hibernate.testing.junit.functional.FunctionalTestClassTestSuite;

/**
 * @author Gail Badner
 */
public class HqlQueryCacheIgnoreResultTransformerTest extends AbstractQueryCacheResultTransformerTest {

	public HqlQueryCacheIgnoreResultTransformerTest(String str) {
		super( str );
	}

	public static Test suite() {
		return new FunctionalTestClassTestSuite( HqlQueryCacheIgnoreResultTransformerTest.class );
	}

	protected CacheMode getQueryCacheMode() {
		return CacheMode.IGNORE;
	}

	protected void runTest(HqlExecutor hqlExecutor, CriteriaExecutor criteriaExecutor, ResultChecker checker, boolean isSingleResult)
		throws Exception {
		createData();
		if ( hqlExecutor != null ) {
			runTest( hqlExecutor, checker, isSingleResult );
		}
		deleteData();
	}

	public void testAliasToEntityMapNoProjectionList() throws Exception {
		reportSkip( "known to fail using HQL", "HQL query using Transformers.ALIAS_TO_ENTITY_MAP with no projection" );
	}
	public void testAliasToEntityMapNoProjectionListFailureExpected() throws Exception {
		super.testAliasToEntityMapNoProjectionList();
	}

	public void testAliasToEntityMapNoProjectionMultiAndNullList() throws Exception {
		reportSkip( "known to fail using HQL", "HQL query using Transformers.ALIAS_TO_ENTITY_MAP with no projection" );
	}
	public void testAliasToEntityMapNoProjectionMultiAndNullListFailureExpected() throws Exception {
		super.testAliasToEntityMapNoProjectionMultiAndNullList();
	}

	public void testAliasToEntityMapNoProjectionNullAndNonNullAliasList() throws Exception {
		reportSkip( "known to fail using HQL", "HQL query using Transformers.ALIAS_TO_ENTITY_MAP with no projection" );
	}
	public void testAliasToEntityMapNoProjectionNullAndNonNullAliasListFailureExpected() throws Exception {
		super.testAliasToEntityMapNoProjectionNullAndNonNullAliasList();
	}

	// fails due to HHH-3345
	public void testMultiSelectNewMapUsingAliasesWithFetchJoinList() throws Exception {
		reportSkip( "known to fail using HQL", "HQL query using 'select new' and 'join fetch'" );
	}
	public void testMultiSelectNewMapUsingAliasesWithFetchJoinListFailureExpected() throws Exception {
		super.testMultiSelectNewMapUsingAliasesWithFetchJoinList();
	}

}
