/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, Red Hat Middleware LLC or third-party contributors as
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
package org.hibernate.loader.criteria;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.LockOptions;
import org.hibernate.engine.CascadeStyle;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.engine.LoadQueryInfluencers;
import org.hibernate.impl.CriteriaImpl;
import org.hibernate.loader.AbstractEntityJoinWalker;
import org.hibernate.loader.PropertyPath;
import org.hibernate.persister.entity.Joinable;
import org.hibernate.persister.entity.OuterJoinLoadable;
import org.hibernate.persister.entity.Queryable;
import org.hibernate.type.AssociationType;
import org.hibernate.type.Type;
import org.hibernate.internal.util.collections.ArrayHelper;

/**
 * A <tt>JoinWalker</tt> for <tt>Criteria</tt> queries.
 *
 * @see CriteriaLoader
 * @author Gavin King
 */
public class CriteriaJoinWalker extends AbstractEntityJoinWalker {

	//TODO: add a CriteriaImplementor interface
	//      this class depends directly upon CriteriaImpl in the impl package...

	private final CriteriaQueryTranslator translator;
	private final Set querySpaces;
	private final Type[] resultTypes;
	private final boolean[] includeInResultRow;

	//the user visible aliases, which are unknown to the superclass,
	//these are not the actual "physical" SQL aliases
	private final String[] userAliases;
	private final List userAliasList = new ArrayList();
	private final List resultTypeList = new ArrayList();
	private final List includeInResultRowList = new ArrayList();

	public Type[] getResultTypes() {
		return resultTypes;
	}

	public String[] getUserAliases() {
		return userAliases;
	}

	public boolean[] includeInResultRow() {
		return includeInResultRow;
	}

	public CriteriaJoinWalker(
			final OuterJoinLoadable persister, 
			final CriteriaQueryTranslator translator,
			final SessionFactoryImplementor factory, 
			final CriteriaImpl criteria, 
			final String rootEntityName,
			final LoadQueryInfluencers loadQueryInfluencers) {
		this( persister, translator, factory, criteria, rootEntityName, loadQueryInfluencers, null );
	}

	public CriteriaJoinWalker(
			final OuterJoinLoadable persister,
			final CriteriaQueryTranslator translator,
			final SessionFactoryImplementor factory,
			final CriteriaImpl criteria,
			final String rootEntityName,
			final LoadQueryInfluencers loadQueryInfluencers,
			final String alias) {
		super( persister, factory, loadQueryInfluencers, alias );

		this.translator = translator;

		querySpaces = translator.getQuerySpaces();

		if ( translator.hasProjection() ) {			
			initProjection(
					translator.getSelect(), 
					translator.getWhereCondition(), 
					translator.getOrderBy(),
					translator.getGroupBy(),
					LockOptions.NONE  
				);
			resultTypes = translator.getProjectedTypes();
			userAliases = translator.getProjectedAliases();
			includeInResultRow = new boolean[ resultTypes.length ];
			Arrays.fill( includeInResultRow, true );
		}
		else {
			initAll( translator.getWhereCondition(), translator.getOrderBy(), LockOptions.NONE );
			// root entity comes last
			userAliasList.add( criteria.getAlias() ); //root entity comes *last*
			resultTypeList.add( translator.getResultType( criteria ) );
			includeInResultRowList.add( true );
			userAliases = ArrayHelper.toStringArray( userAliasList );
			resultTypes = ArrayHelper.toTypeArray( resultTypeList );
			includeInResultRow = ArrayHelper.toBooleanArray( includeInResultRowList );
		}
	}

	protected int getJoinType(
			OuterJoinLoadable persister,
			final PropertyPath path,
			int propertyNumber,
			AssociationType associationType,
			FetchMode metadataFetchMode,
			CascadeStyle metadataCascadeStyle,
			String lhsTable,
			String[] lhsColumns,
			final boolean nullable,
			final int currentDepth) throws MappingException {
		if ( translator.isJoin( path.getFullPath() ) ) {
			return translator.getJoinType( path.getFullPath() );
		}
		else {
			if ( translator.hasProjection() ) {
				return -1;
			}
			else {
				FetchMode fetchMode = translator.getRootCriteria().getFetchMode( path.getFullPath() );
				if ( isDefaultFetchMode( fetchMode ) ) {
					if ( isJoinFetchEnabledByProfile( persister, path, propertyNumber ) ) {
						return getJoinType( nullable, currentDepth );
					}
					else {
						return super.getJoinType(
								persister,
								path,
								propertyNumber,
								associationType,
								metadataFetchMode,
								metadataCascadeStyle,
								lhsTable,
								lhsColumns,
								nullable,
								currentDepth
						);
					}
				}
				else {
					if ( fetchMode == FetchMode.JOIN ) {
						isDuplicateAssociation( lhsTable, lhsColumns, associationType ); //deliberately ignore return value!
						return getJoinType( nullable, currentDepth );
					}
					else {
						return -1;
					}
				}
			}
		}
	}

	protected int getJoinType(
			AssociationType associationType,
			FetchMode config,
			PropertyPath path,
			String lhsTable,
			String[] lhsColumns,
			boolean nullable,
			int currentDepth,
			CascadeStyle cascadeStyle) throws MappingException {
		return ( translator.isJoin( path.getFullPath() ) ?
				translator.getJoinType( path.getFullPath() ) :
				super.getJoinType(
						associationType,
						config,
						path,
						lhsTable,
						lhsColumns,
						nullable,
						currentDepth,
						cascadeStyle
				)
		);
	}

	private static boolean isDefaultFetchMode(FetchMode fetchMode) {
		return fetchMode==null || fetchMode==FetchMode.DEFAULT;
	}

	/**
	 * Use the discriminator, to narrow the select to instances
	 * of the queried subclass, also applying any filters.
	 */
	protected String getWhereFragment() throws MappingException {
		return super.getWhereFragment() +
			( (Queryable) getPersister() ).filterFragment( getAlias(), getLoadQueryInfluencers().getEnabledFilters() );
	}
	
	protected String generateTableAlias(int n, PropertyPath path, Joinable joinable) {
		// TODO: deal with side-effects (changes to includeInSelectList, userAliasList, resultTypeList)!!!
		if ( joinable.consumesEntityAlias() ) {
			final Criteria subcriteria = translator.getCriteria( path.getFullPath() );
			String sqlAlias = subcriteria==null ? null : translator.getSQLAlias(subcriteria);
			if (sqlAlias!=null) {
				if ( ! translator.hasProjection() ) {
					includeInResultRowList.add( subcriteria.getAlias() != null );
					if ( subcriteria.getAlias() != null ) {
						userAliasList.add( subcriteria.getAlias() ); //alias may be null
						resultTypeList.add( translator.getResultType( subcriteria ) );
					}
				}
				return sqlAlias; //EARLY EXIT
			}
			else {
				if ( ! translator.hasProjection() ) {
					includeInResultRowList.add( false );
				}
			}
		}
		return super.generateTableAlias( n + translator.getSQLAliasCount(), path, joinable );
	}

	protected String generateRootAlias(String tableName) {
		return CriteriaQueryTranslator.ROOT_SQL_ALIAS;
	}

	public Set getQuerySpaces() {
		return querySpaces;
	}
	
	public String getComment() {
		return "criteria query";
	}

	protected String getWithClause(PropertyPath path) {
		return translator.getWithClause( path.getFullPath() );
	}
	
}
