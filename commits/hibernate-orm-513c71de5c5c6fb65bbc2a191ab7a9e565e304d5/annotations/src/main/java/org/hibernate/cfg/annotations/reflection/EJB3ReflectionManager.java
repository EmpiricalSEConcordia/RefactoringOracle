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
 */
package org.hibernate.cfg.annotations.reflection;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityListeners;
import javax.persistence.NamedNativeQuery;
import javax.persistence.NamedQuery;
import javax.persistence.SequenceGenerator;
import javax.persistence.SqlResultSetMapping;
import javax.persistence.TableGenerator;

import org.dom4j.Element;
import org.hibernate.annotations.common.reflection.AnnotationReader;
import org.hibernate.annotations.common.reflection.java.JavaReflectionManager;
import org.hibernate.util.ReflectHelper;

public class EJB3ReflectionManager extends JavaReflectionManager {

	private XMLContext xmlContext = new XMLContext();
	private HashMap defaults = null;

	public AnnotationReader buildAnnotationReader(AnnotatedElement annotatedElement) {
		if ( xmlContext.hasContext() ) {
			return new EJB3OverridenAnnotationReader( annotatedElement, xmlContext );
		}
		else {
			return super.buildAnnotationReader( annotatedElement );
		}
	}

	public Map getDefaults() {
		if ( defaults == null ) {
			defaults = new HashMap();
			XMLContext.Default xmlDefaults = xmlContext.getDefault( null );
			List<Class> entityListeners = new ArrayList<Class>();
			for (String className : xmlContext.getDefaultEntityListeners()) {
				try {
					entityListeners.add( ReflectHelper.classForName( className, this.getClass() ) );
				}
				catch (ClassNotFoundException e) {
					throw new IllegalStateException( "Default entity listener class not found: " + className );
				}
			}
			defaults.put( EntityListeners.class, entityListeners );
			for (Element element : xmlContext.getAllDocuments()) {

				List<Element> elements = element.elements( "sequence-generator" );
				List<SequenceGenerator> sequenceGenerators = (List<SequenceGenerator>) defaults.get( SequenceGenerator.class );
				if ( sequenceGenerators == null ) {
					sequenceGenerators = new ArrayList<SequenceGenerator>();
					defaults.put( SequenceGenerator.class, sequenceGenerators );
				}
				for (Element subelement : elements) {
					sequenceGenerators.add( EJB3OverridenAnnotationReader.buildSequenceGeneratorAnnotation( subelement ) );
				}

				elements = element.elements( "table-generator" );
				List<TableGenerator> tableGenerators = (List<TableGenerator>) defaults.get( TableGenerator.class );
				if ( tableGenerators == null ) {
					tableGenerators = new ArrayList<TableGenerator>();
					defaults.put( TableGenerator.class, tableGenerators );
				}
				for (Element subelement : elements) {
					tableGenerators.add( EJB3OverridenAnnotationReader.buildTableGeneratorAnnotation( subelement, xmlDefaults ) );
				}

				List<NamedQuery> namedQueries = (List<NamedQuery>) defaults.get( NamedQuery.class );
				if ( namedQueries == null ) {
					namedQueries = new ArrayList<NamedQuery>();
					defaults.put( NamedQuery.class, namedQueries );
				}
				List<NamedQuery> currentNamedQueries = EJB3OverridenAnnotationReader.buildNamedQueries( element, false, xmlDefaults );
				namedQueries.addAll( currentNamedQueries );

				List<NamedNativeQuery> namedNativeQueries = (List<NamedNativeQuery>) defaults.get( NamedNativeQuery.class );
				if ( namedNativeQueries == null ) {
					namedNativeQueries = new ArrayList<NamedNativeQuery>();
					defaults.put( NamedNativeQuery.class, namedNativeQueries );
				}
				List<NamedNativeQuery> currentNamedNativeQueries = EJB3OverridenAnnotationReader.buildNamedQueries( element, true, xmlDefaults );
				namedNativeQueries.addAll( currentNamedNativeQueries );

				List<SqlResultSetMapping> sqlResultSetMappings = (List<SqlResultSetMapping>) defaults.get( SqlResultSetMapping.class );
				if ( sqlResultSetMappings == null ) {
					sqlResultSetMappings = new ArrayList<SqlResultSetMapping>();
					defaults.put( SqlResultSetMapping.class, sqlResultSetMappings );
				}
				List<SqlResultSetMapping> currentSqlResultSetMappings = EJB3OverridenAnnotationReader.buildSqlResultsetMappings( element, xmlDefaults );
				sqlResultSetMappings.addAll( currentSqlResultSetMappings );
			}
		}
		return defaults;
	}

	public XMLContext getXMLContext() {
		return xmlContext;
	}
}
