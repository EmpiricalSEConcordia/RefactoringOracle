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
package org.hibernate.search.impl;

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.hibernate.annotations.common.annotationfactory.AnnotationDescriptor;
import org.hibernate.annotations.common.annotationfactory.AnnotationFactory;
import org.hibernate.annotations.common.reflection.AnnotationReader;
import org.hibernate.annotations.common.reflection.Filter;
import org.hibernate.annotations.common.reflection.MetadataProvider;
import org.hibernate.annotations.common.reflection.ReflectionUtil;
import org.hibernate.search.annotations.Analyzer;
import org.hibernate.search.annotations.AnalyzerDef;
import org.hibernate.search.annotations.AnalyzerDefs;
import org.hibernate.search.annotations.AnalyzerDiscriminator;
import org.hibernate.search.annotations.Boost;
import org.hibernate.search.annotations.CalendarBridge;
import org.hibernate.search.annotations.ClassBridge;
import org.hibernate.search.annotations.ClassBridges;
import org.hibernate.search.annotations.ContainedIn;
import org.hibernate.search.annotations.DateBridge;
import org.hibernate.search.annotations.DocumentId;
import org.hibernate.search.annotations.DynamicBoost;
import org.hibernate.search.annotations.FieldBridge;
import org.hibernate.search.annotations.Fields;
import org.hibernate.search.annotations.FullTextFilterDef;
import org.hibernate.search.annotations.FullTextFilterDefs;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.Parameter;
import org.hibernate.search.annotations.ProvidedId;
import org.hibernate.search.annotations.Similarity;
import org.hibernate.search.annotations.TokenFilterDef;
import org.hibernate.search.annotations.TokenizerDef;
import org.hibernate.search.cfg.EntityDescriptor;
import org.hibernate.search.cfg.PropertyDescriptor;
import org.hibernate.search.cfg.SearchMapping;

/**
 * @author Emmanuel Bernard
 */
public class MappingModelMetadataProvider implements MetadataProvider {

	private static final Filter FILTER = new Filter() {
		public boolean returnStatic() {
			return false;
		}

		public boolean returnTransient() {
			return true;
		}
	};

	private final MetadataProvider delegate;
	private final SearchMapping mapping;
	private final Map<AnnotatedElement, AnnotationReader> cache = new HashMap<AnnotatedElement, AnnotationReader>(100);
	private Map<Object, Object> defaults;

	public MappingModelMetadataProvider(MetadataProvider delegate, SearchMapping mapping) {
		this.delegate = delegate;
		this.mapping = mapping;
	}

	public Map<Object, Object> getDefaults() {
		if (defaults == null) {
			final Map<Object, Object> delegateDefaults = delegate.getDefaults();
			defaults = delegateDefaults == null ?
					new HashMap<Object, Object>() :
					new HashMap<Object, Object>(delegateDefaults);
			defaults.put( AnalyzerDefs.class, createAnalyzerDefArray() );
			if (!mapping.getFullTextFilerDefs().isEmpty()) {
				defaults.put(FullTextFilterDefs.class, createFullTextFilterDefsForMapping());
			}
		}
		return defaults;
	}

	

	public AnnotationReader getAnnotationReader(AnnotatedElement annotatedElement) {
		AnnotationReader reader = cache.get(annotatedElement);
		if (reader == null) {
			reader = new MappingModelAnnotationReader( mapping, delegate, annotatedElement);
			cache.put( annotatedElement, reader );
		}
		return reader;
	}

	private AnalyzerDef[] createAnalyzerDefArray() {
		AnalyzerDef[] defs = new AnalyzerDef[ mapping.getAnalyzerDefs().size() ];
		int index = 0;
		for ( Map<String, Object> analyzerDef : mapping.getAnalyzerDefs() ) {
			defs[index] = createAnalyzerDef( analyzerDef );
			index++;
		}
		return defs;
	}
	
	private FullTextFilterDef[] createFullTextFilterDefsForMapping() {
		Set<Map<String, Object>> fullTextFilterDefs = mapping.getFullTextFilerDefs();
		FullTextFilterDef[] filters = new FullTextFilterDef[fullTextFilterDefs.size()];
		int index = 0;
		for(Map<String,Object> filterDef : fullTextFilterDefs) {
			filters[index] = createFullTextFilterDef(filterDef);
			index++;
		}
		return filters;
	}
	
	private static FullTextFilterDef createFullTextFilterDef(Map<String,Object> filterDef) {
		AnnotationDescriptor fullTextFilterDefAnnotation = new AnnotationDescriptor( FullTextFilterDef.class );
		for (Entry<String, Object> entry : filterDef.entrySet()) {
			fullTextFilterDefAnnotation.setValue(entry.getKey(), entry.getValue());
		}
		
		return AnnotationFactory.create( fullTextFilterDefAnnotation );
	}

	private static FullTextFilterDef[] createFullTextFilterDefArray(Set<Map<String, Object>> fullTextFilterDefs) {
		FullTextFilterDef[] filters = new FullTextFilterDef[fullTextFilterDefs.size()];
		int index = 0;
		for(Map<String,Object> filterDef : fullTextFilterDefs) {
			filters[index] = createFullTextFilterDef(filterDef);
			index++;
		}
		return filters;
	}
	
	private AnalyzerDef createAnalyzerDef(Map<String, Object> analyzerDef) {
		AnnotationDescriptor analyzerDefAnnotation = new AnnotationDescriptor( AnalyzerDef.class );
		for ( Map.Entry<String, Object> entry : analyzerDef.entrySet() ) {
			if ( entry.getKey().equals( "tokenizer" ) ) {
				AnnotationDescriptor tokenizerAnnotation = new AnnotationDescriptor( TokenizerDef.class );
				@SuppressWarnings( "unchecked" )
				Map<String, Object> tokenizer = (Map<String, Object>) entry.getValue();
				for( Map.Entry<String, Object> tokenizerEntry : tokenizer.entrySet() ) {
					if ( tokenizerEntry.getKey().equals( "params" ) ) {
						addParamsToAnnotation( tokenizerAnnotation, tokenizerEntry );
					}
					else {
						tokenizerAnnotation.setValue( tokenizerEntry.getKey(), tokenizerEntry.getValue() );
					}
				}
				analyzerDefAnnotation.setValue( "tokenizer", AnnotationFactory.create( tokenizerAnnotation ) );
			}
			else if ( entry.getKey().equals( "filters" ) ) {
				@SuppressWarnings("unchecked") TokenFilterDef[] filtersArray = createFilters( (List<Map<String, Object>>) entry.getValue() );
				analyzerDefAnnotation.setValue( "filters", filtersArray );
			}
			else {
				analyzerDefAnnotation.setValue( entry.getKey(), entry.getValue() );
			}
		}
		return AnnotationFactory.create( analyzerDefAnnotation );
	}

	static private void addParamsToAnnotation(AnnotationDescriptor annotationDescriptor, Map.Entry<String, Object> entry) {
		@SuppressWarnings("unchecked") Parameter[] paramsArray = createParams( ( List<Map<String, Object>> ) entry.getValue() );
		annotationDescriptor.setValue( "params", paramsArray );
	}

	private TokenFilterDef[] createFilters(List<Map<String, Object>> filters) {
		TokenFilterDef[] filtersArray = new TokenFilterDef[filters.size()];
		int index = 0;
		for (Map<String, Object> filter : filters) {
			AnnotationDescriptor filterAnn = new AnnotationDescriptor( TokenFilterDef.class );
			for ( Map.Entry<String, Object> filterEntry : filter.entrySet() ) {
				if ( filterEntry.getKey().equals( "params" ) ) {
					addParamsToAnnotation( filterAnn, filterEntry );
				}
				else {
					filterAnn.setValue( filterEntry.getKey(), filterEntry.getValue() );
				}
			}
			filtersArray[index] = AnnotationFactory.create( filterAnn );
			index++;
		}
		return filtersArray;
	}

	private static Parameter[] createParams(List<Map<String, Object>> params) {
		Parameter[] paramArray = new Parameter[ params.size() ];
		int index = 0;
		for ( Map<String, Object> entry : params) {
			AnnotationDescriptor paramAnnotation = new AnnotationDescriptor( Parameter.class );
			paramAnnotation.setValue( "name", entry.get("name") );
			paramAnnotation.setValue( "value", entry.get("value") );
			paramArray[index] = AnnotationFactory.create( paramAnnotation );
			index++;
		}
		return paramArray;
	}

	private static class MappingModelAnnotationReader implements AnnotationReader {
		private AnnotationReader delegate;
		private SearchMapping mapping;
		private transient Annotation[] annotationsArray;
		private transient Map<Class<? extends Annotation>, Annotation> annotations;
		private Class<?> entityType;
		private ElementType elementType;
		private String propertyName;

		public MappingModelAnnotationReader(SearchMapping mapping, MetadataProvider delegate, AnnotatedElement el) {
			this.delegate = delegate.getAnnotationReader( el );
			this.mapping = mapping;
			if ( el instanceof Class ) {
				entityType = (Class<?>) el;
			}
			else if ( el instanceof Field ) {
				Field field = (Field) el;
				entityType = field.getDeclaringClass();
				propertyName = field.getName();
				elementType = ElementType.FIELD;
			}
			else if ( el instanceof Method ) {
				Method method = (Method) el;
				entityType = method.getDeclaringClass();
				propertyName = method.getName();
				if ( ReflectionUtil.isProperty(
						method,
						null, //this is yukky!! we'd rather get the TypeEnvironment()
						FILTER
				) ) {
					if ( propertyName.startsWith( "get" ) ) {
						propertyName = Introspector.decapitalize( propertyName.substring( "get".length() ) );
					}
					else if ( propertyName.startsWith( "is" ) ) {
						propertyName = Introspector.decapitalize( propertyName.substring( "is".length() ) );
					}
					else {
						throw new RuntimeException( "Method " + propertyName + " is not a property getter" );
					}
					elementType = ElementType.METHOD;
				}
				else {
					//this is a non getter method, so let it go and delegate
					entityType = null;
					propertyName = null;
				}
			}
			else {
				//this is a non supported element, so let it go and delegate
				entityType = null;
				propertyName = null;
			}
		}

		/**
		 * Consider the class to be free of Hibernate Search annotations. Does nto attempt to merge
		 * data.
		 * TODO do merge data? or safe-guard against errors
		 */
		private void initAnnotations() {
			if ( annotationsArray == null ) {
				annotations = new HashMap<Class<? extends Annotation>, Annotation>();
				delegatesAnnotationReading();
				if (entityType != null) {
					final EntityDescriptor entity = mapping.getEntityDescriptor( entityType );
					if (entity != null) {
						if (propertyName == null) {
							//entityType overriding
							createIndexed( entity );
						}
						else {
							final PropertyDescriptor property = entity.getPropertyDescriptor( propertyName, elementType );
							if (property != null) {
								// property name overriding
								createDocumentId( property );
								createAnalyzerDiscriminator( property );
								createFields( property );
								createIndexEmbedded(property);
								createContainedIn(property);
								
							}
						}
					}
				}
				else {
					delegatesAnnotationReading();
				}

				populateAnnotationArray();
			}
		}
		

		private void createDateBridge(PropertyDescriptor property) {
			Map<String, Object> map = property.getDateBridge();	
			for(Map.Entry<String, Object> entry: map.entrySet()) {
					AnnotationDescriptor dateBrigeAnnotation = new AnnotationDescriptor( DateBridge.class );
					dateBrigeAnnotation.setValue(entry.getKey(), entry.getValue());
					annotations.put( DateBridge.class, AnnotationFactory.create( dateBrigeAnnotation ) );	
			}
		}

		private void createCalendarBridge(PropertyDescriptor property) {
			Map<String, Object> map = property.getCalendarBridge();
			for(Map.Entry<String, Object> entry: map.entrySet()) {
				AnnotationDescriptor calendarBrigeAnnotation = new AnnotationDescriptor( CalendarBridge.class );
				calendarBrigeAnnotation.setValue(entry.getKey(), entry.getValue());
				annotations.put( CalendarBridge.class, AnnotationFactory.create( calendarBrigeAnnotation ) );	
			}
		}
		
		private void createDocumentId(PropertyDescriptor property) {
			Map<String, Object> documentId = property.getDocumentId();
			if (documentId != null) {
				AnnotationDescriptor documentIdAnnotation = new AnnotationDescriptor( DocumentId.class );
				for ( Map.Entry<String, Object> entry : documentId.entrySet() ) {
					documentIdAnnotation.setValue( entry.getKey(), entry.getValue() );
				}
				annotations.put( DocumentId.class, AnnotationFactory.create( documentIdAnnotation ) );	
			}
		}
		
		private void createAnalyzerDiscriminator(PropertyDescriptor property) {
			Map<String, Object> analyzerDiscriminator = property.getAnalyzerDiscriminator();
			if (analyzerDiscriminator != null) {
				AnnotationDescriptor analyzerDiscriminatorAnn = new AnnotationDescriptor( AnalyzerDiscriminator.class );
				for ( Map.Entry<String, Object> entry : analyzerDiscriminator.entrySet() ) {
					analyzerDiscriminatorAnn.setValue( entry.getKey(), entry.getValue() );
				}
				annotations.put( AnalyzerDiscriminator.class, AnnotationFactory.create( analyzerDiscriminatorAnn ) );
			}
		}

		
		private void createFields(PropertyDescriptor property) {
			final Collection<Map<String,Object>> fields = property.getFields();
			List<org.hibernate.search.annotations.Field> fieldAnnotations =
					new ArrayList<org.hibernate.search.annotations.Field>( fields.size() );
			for(Map<String, Object> field : fields) {
				AnnotationDescriptor fieldAnnotation = new AnnotationDescriptor( org.hibernate.search.annotations.Field.class );
				for ( Map.Entry<String, Object> entry : field.entrySet() ) {
					if ( entry.getKey().equals( "analyzer" ) ) {
						AnnotationDescriptor analyzerAnnotation = new AnnotationDescriptor( Analyzer.class );
						@SuppressWarnings( "unchecked" )
						Map<String, Object> analyzer = (Map<String, Object>) entry.getValue();
						for( Map.Entry<String, Object> analyzerEntry : analyzer.entrySet() ) {
							analyzerAnnotation.setValue( analyzerEntry.getKey(), analyzerEntry.getValue() );
						}
						fieldAnnotation.setValue( "analyzer", AnnotationFactory.create( analyzerAnnotation ) );
					}
					else if ( entry.getKey().equals( "boost" ) ) {
						AnnotationDescriptor boostAnnotation = new AnnotationDescriptor( Boost.class );
						@SuppressWarnings( "unchecked" )
						Map<String, Object> boost = (Map<String, Object>) entry.getValue();
						for( Map.Entry<String, Object> boostEntry : boost.entrySet() ) {
							boostAnnotation.setValue( boostEntry.getKey(), boostEntry.getValue() );
						}
						fieldAnnotation.setValue( "boost", AnnotationFactory.create( boostAnnotation ) );
					}
					else if ( entry.getKey().equals( "bridge" ) ) {
						AnnotationDescriptor bridgeAnnotation = new AnnotationDescriptor( FieldBridge.class );
						@SuppressWarnings( "unchecked" )
						Map<String, Object> bridge = (Map<String, Object>) entry.getValue();
						for( Map.Entry<String, Object> bridgeEntry : bridge.entrySet() ) {
							if ( bridgeEntry.getKey().equals( "params" ) ) {
								addParamsToAnnotation( bridgeAnnotation, bridgeEntry );
							}
							else {
								bridgeAnnotation.setValue( bridgeEntry.getKey(), bridgeEntry.getValue() );
							}
						}
						fieldAnnotation.setValue( "bridge", AnnotationFactory.create( bridgeAnnotation ) );
					} 
					else {
						fieldAnnotation.setValue( entry.getKey(), entry.getValue() );
					}
				}
				fieldAnnotations.add( (org.hibernate.search.annotations.Field) AnnotationFactory.create( fieldAnnotation ) );
			}
			AnnotationDescriptor fieldsAnnotation = new AnnotationDescriptor( Fields.class );

			final org.hibernate.search.annotations.Field[] fieldArray =
					new org.hibernate.search.annotations.Field[fieldAnnotations.size()];
			final org.hibernate.search.annotations.Field[] fieldAsArray = fieldAnnotations.toArray( fieldArray );
	
			fieldsAnnotation.setValue( "value", fieldAsArray );
			annotations.put( Fields.class, AnnotationFactory.create( fieldsAnnotation ) );
			createDateBridge(property);
			createCalendarBridge(property);
			createDynamicBoost(property);
			
		}

		private void createDynamicBoost(PropertyDescriptor property) {
			if (property.getDynamicBoost() != null) {
				AnnotationDescriptor dynamicBoostAnn = new AnnotationDescriptor( DynamicBoost.class );
				Set<Entry<String,Object>> entrySet = property.getDynamicBoost().entrySet();
				for (Entry<String, Object> entry : entrySet) {
					dynamicBoostAnn.setValue(entry.getKey(), entry.getValue());
				}
				annotations.put(DynamicBoost.class, AnnotationFactory.create( dynamicBoostAnn ));
			}
		}
		private void createContainedIn(PropertyDescriptor property) {
			if (property.getContainedIn() != null) {
				Map<String, Object> containedIn = property.getContainedIn();
				AnnotationDescriptor containedInAnn = new AnnotationDescriptor( ContainedIn.class );
				Set<Entry<String,Object>> entrySet = containedIn.entrySet();
				for (Entry<String, Object> entry : entrySet) {
					containedInAnn.setValue(entry.getKey(), entry.getValue());
				}
				annotations.put(ContainedIn.class,AnnotationFactory.create(containedInAnn));
			}
		}

		private void createIndexEmbedded(PropertyDescriptor property) {
			Map<String, Object> indexEmbedded = property.getIndexEmbedded();
			if (indexEmbedded != null) {
				AnnotationDescriptor indexEmbeddedAnn = new AnnotationDescriptor(IndexedEmbedded.class);
				Set<Entry<String,Object>> entrySet = indexEmbedded.entrySet();
				for (Entry<String, Object> entry : entrySet) {
					indexEmbeddedAnn.setValue(entry.getKey(), entry.getValue());
				}
				annotations.put(IndexedEmbedded.class, AnnotationFactory.create(indexEmbeddedAnn));
			}
		}

		private void createIndexed(EntityDescriptor entity) {
			Class<? extends Annotation> annotationType = Indexed.class;
			AnnotationDescriptor annotation = new AnnotationDescriptor( annotationType );
			if (entity.getIndexed() != null) {
				for ( Map.Entry<String, Object> entry : entity.getIndexed().entrySet() ) {
					annotation.setValue( entry.getKey(), entry.getValue() );
				}
				annotations.put( annotationType, AnnotationFactory.create( annotation ) );
			}

			if ( entity.getSimilarity() != null ) {
				annotation = new AnnotationDescriptor( Similarity.class );
				for ( Map.Entry<String, Object> entry : entity.getSimilarity().entrySet() ) {
					annotation.setValue( entry.getKey(), entry.getValue() );
				}
				annotations.put( Similarity.class, AnnotationFactory.create( annotation ) );
			}

			if ( entity.getBoost() != null ) {
				annotation = new AnnotationDescriptor( Boost.class );
				for ( Map.Entry<String, Object> entry : entity.getBoost().entrySet() ) {
					annotation.setValue( entry.getKey(), entry.getValue() );
				}
				annotations.put( Boost.class, AnnotationFactory.create( annotation ) );
			}

			if ( entity.getAnalyzerDiscriminator() != null ) {
				annotation = new AnnotationDescriptor( AnalyzerDiscriminator.class );
				for ( Map.Entry<String, Object> entry : entity.getAnalyzerDiscriminator().entrySet() ) {
					annotation.setValue( entry.getKey(), entry.getValue() );
				}
				annotations.put( AnalyzerDiscriminator.class, AnnotationFactory.create( annotation ) );
			}
			if (entity.getFullTextFilterDefs().size() > 0)  {
				AnnotationDescriptor fullTextFilterDefsAnnotation = new AnnotationDescriptor( FullTextFilterDefs.class );
				FullTextFilterDef[] fullTextFilterDefArray = createFullTextFilterDefArray(entity.getFullTextFilterDefs());
				fullTextFilterDefsAnnotation.setValue("value", fullTextFilterDefArray);
				annotations.put( FullTextFilterDefs.class, AnnotationFactory.create( fullTextFilterDefsAnnotation ) );
			}
			if (entity.getProvidedId() != null) {
				createProvidedId(entity);
			}
			
			if (entity.getClassBridgeDefs().size() > 0) {
				AnnotationDescriptor classBridgesAnn = new AnnotationDescriptor( ClassBridges.class );
				ClassBridge[] classBridesDefArray  = createClassBridgesDefArray(entity.getClassBridgeDefs());
				classBridgesAnn.setValue("value", classBridesDefArray);
				annotations.put(ClassBridges.class, AnnotationFactory.create( classBridgesAnn ));
			}
			
			if (entity.getDynamicBoost() != null) {
				AnnotationDescriptor dynamicBoostAnn = new AnnotationDescriptor( DynamicBoost.class );
				Set<Entry<String,Object>> entrySet = entity.getDynamicBoost().entrySet();
				for (Entry<String, Object> entry : entrySet) {
					dynamicBoostAnn.setValue(entry.getKey(), entry.getValue());
				}
				annotations.put(DynamicBoost.class, AnnotationFactory.create( dynamicBoostAnn ));
			}
			
		}

		private ClassBridge[] createClassBridgesDefArray(Set<Map<String, Object>> classBridgeDefs) {
			ClassBridge[] classBridgeDefArray = new ClassBridge[classBridgeDefs.size()];
			int index = 0;
			for(Map<String,Object> classBridgeDef : classBridgeDefs) {
				classBridgeDefArray[index] = createClassBridge(classBridgeDef);
				index++;
			}
			
			return classBridgeDefArray;
		}

		
		private ClassBridge createClassBridge(Map<String, Object> classBridgeDef) {
			AnnotationDescriptor annotation	= new AnnotationDescriptor( ClassBridge.class );
			Set<Entry<String,Object>> entrySet = classBridgeDef.entrySet();
			for (Entry<String, Object> entry : entrySet) {
				if (entry.getKey().equals("params")) {
					addParamsToAnnotation(annotation, entry);
				} else {
					annotation.setValue(entry.getKey(), entry.getValue());
				}
			}
			return AnnotationFactory.create( annotation );
		}

		private void createProvidedId(EntityDescriptor entity) {
			AnnotationDescriptor annotation	= new AnnotationDescriptor( ProvidedId.class );
			Set<Entry<String,Object>> entrySet = entity.getProvidedId().entrySet();
			for (Entry<String, Object> entry : entrySet) {
				if (entry.getKey().equals("bridge")) {
					AnnotationDescriptor bridgeAnnotation = new AnnotationDescriptor( FieldBridge.class );
					@SuppressWarnings("unchecked")
					Map<String, Object> bridge = (Map<String, Object>) entry.getValue();
					for( Map.Entry<String, Object> bridgeEntry : bridge.entrySet() ) {
						if ( bridgeEntry.getKey().equals( "params" ) ) {
							addParamsToAnnotation( bridgeAnnotation, bridgeEntry );
						}
						else {
							bridgeAnnotation.setValue( bridgeEntry.getKey(), bridgeEntry.getValue() );
						}
					}
					annotation.setValue( "bridge", AnnotationFactory.create( bridgeAnnotation ) );
				} else {
					annotation.setValue(entry.getKey(), entry.getValue());
				}
			}
			annotations.put( ProvidedId.class, AnnotationFactory.create( annotation ) );
		}
		
		private void populateAnnotationArray() {
			annotationsArray = new Annotation[ annotations.size() ];
			int index = 0;
			for( Annotation ann: annotations.values() ) {
				annotationsArray[index] = ann;
				index++;
			}
		}

		private void delegatesAnnotationReading() {
			for ( Annotation a : delegate.getAnnotations() ) {
				annotations.put( a.annotationType(), a );
			}
		}

		@SuppressWarnings( "unchecked" )
		public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
			initAnnotations();
			return (T) annotations.get( annotationType );
		}

		@SuppressWarnings( "unchecked" )
		public <T extends Annotation> boolean isAnnotationPresent(Class<T> annotationType) {
			initAnnotations();
			return (T) annotations.get( annotationType ) != null;
		}

		public Annotation[] getAnnotations() {
			initAnnotations();
			return new Annotation[0];  //To change body of implemented methods use File | Settings | File Templates.
		}
	}
}
