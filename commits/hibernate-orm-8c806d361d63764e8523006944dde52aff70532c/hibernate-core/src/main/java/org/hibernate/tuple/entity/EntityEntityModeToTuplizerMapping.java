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
package org.hibernate.tuple.entity;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.hibernate.EntityMode;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.tuple.EntityModeToTuplizerMapping;
import org.hibernate.tuple.Tuplizer;

/**
 * Handles mapping {@link EntityMode}s to {@link EntityTuplizer}s.
 * <p/>
 * Most of the handling is really in the super class; here we just create
 * the tuplizers and add them to the superclass
 *
 * @author Steve Ebersole
 */
public class EntityEntityModeToTuplizerMapping extends EntityModeToTuplizerMapping implements Serializable {

	/**
	 * Instantiates a EntityEntityModeToTuplizerMapping based on the given
	 * entity mapping and metamodel definitions.
	 *
	 * @param mappedEntity The entity mapping definition.
	 * @param em The entity metamodel definition.
	 */
	public EntityEntityModeToTuplizerMapping(PersistentClass mappedEntity, EntityMetamodel em) {
		final EntityTuplizerFactory entityTuplizerFactory = em.getSessionFactory()
				.getSettings()
				.getEntityTuplizerFactory();

		// create our own copy of the user-supplied tuplizer impl map
		Map userSuppliedTuplizerImpls = new HashMap();
		if ( mappedEntity.getTuplizerMap() != null ) {
			userSuppliedTuplizerImpls.putAll( mappedEntity.getTuplizerMap() );
		}

		// Build the dynamic-map tuplizer...
		Tuplizer dynamicMapTuplizer;
		String tuplizerImplClassName = ( String ) userSuppliedTuplizerImpls.remove( EntityMode.MAP );
		if ( tuplizerImplClassName == null ) {
			dynamicMapTuplizer = entityTuplizerFactory.constructDefaultTuplizer( EntityMode.MAP, em, mappedEntity );
		}
		else {
			dynamicMapTuplizer = entityTuplizerFactory.constructTuplizer( tuplizerImplClassName, em, mappedEntity );
		}

		// then the pojo tuplizer, using the dynamic-map tuplizer if no pojo representation is available
		Tuplizer pojoTuplizer;
		tuplizerImplClassName = ( String ) userSuppliedTuplizerImpls.remove( EntityMode.POJO );
		if ( mappedEntity.hasPojoRepresentation() ) {
			if ( tuplizerImplClassName == null ) {
				pojoTuplizer = entityTuplizerFactory.constructDefaultTuplizer( EntityMode.POJO, em, mappedEntity );
			}
			else {
				pojoTuplizer = entityTuplizerFactory.constructTuplizer( tuplizerImplClassName, em, mappedEntity );
			}
		}
		else {
			pojoTuplizer = dynamicMapTuplizer;
		}

		// then dom4j tuplizer, if dom4j representation is available
		Tuplizer dom4jTuplizer;
		tuplizerImplClassName = ( String ) userSuppliedTuplizerImpls.remove( EntityMode.DOM4J );
		if ( mappedEntity.hasDom4jRepresentation() ) {
			if ( tuplizerImplClassName == null ) {
				dom4jTuplizer = entityTuplizerFactory.constructDefaultTuplizer( EntityMode.DOM4J, em, mappedEntity );
			}
			else {
				dom4jTuplizer = entityTuplizerFactory.constructTuplizer( tuplizerImplClassName, em, mappedEntity );
			}
		}
		else {
			dom4jTuplizer = null;
		}

		// put the "standard" tuplizers into the tuplizer map first
		if ( pojoTuplizer != null ) {
			addTuplizer( EntityMode.POJO, pojoTuplizer );
		}
		if ( dynamicMapTuplizer != null ) {
			addTuplizer( EntityMode.MAP, dynamicMapTuplizer );
		}
		if ( dom4jTuplizer != null ) {
			addTuplizer( EntityMode.DOM4J, dom4jTuplizer );
		}

		// then handle any user-defined entity modes...
		if ( !userSuppliedTuplizerImpls.isEmpty() ) {
			Iterator itr = userSuppliedTuplizerImpls.entrySet().iterator();
			while ( itr.hasNext() ) {
				final Map.Entry entry = ( Map.Entry ) itr.next();
				final EntityMode entityMode = ( EntityMode ) entry.getKey();
				final String tuplizerClassName = ( String ) entry.getValue();
				final EntityTuplizer tuplizer = entityTuplizerFactory.constructTuplizer( tuplizerClassName, em, mappedEntity );
				addTuplizer( entityMode, tuplizer );
			}
		}
	}
}
