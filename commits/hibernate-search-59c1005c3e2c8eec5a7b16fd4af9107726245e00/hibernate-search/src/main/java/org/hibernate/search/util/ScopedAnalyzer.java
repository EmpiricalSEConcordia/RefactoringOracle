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
package org.hibernate.search.util;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;

/**
 * A <code>ScopedAnalyzer</code> is a wrapper class containing all analyzers for a given class.
 * <code>ScopedAnalyzer</code> behaves similar to <code>PerFieldAnalyzerWrapper</code> by delegating requests for
 * <code>TokenStream</code>s to the underlying <code>Analyzer</code> depending on the requested field name.
 * 
 * @author Emmanuel Bernard
 */
public final class ScopedAnalyzer extends Analyzer {
	private Analyzer globalAnalyzer;
	private Map<String, Analyzer> scopedAnalyzers = new HashMap<String, Analyzer>();

	public ScopedAnalyzer() {
	}

	private ScopedAnalyzer( Analyzer globalAnalyzer, Map<String, Analyzer> scopedAnalyzers) {
		this.globalAnalyzer = globalAnalyzer;
		for ( Map.Entry<String, Analyzer> entry : scopedAnalyzers.entrySet() ) {
			addScopedAnalyzer( entry.getKey(), entry.getValue() );
		}
	}

	public void setGlobalAnalyzer( Analyzer globalAnalyzer ) {
		this.globalAnalyzer = globalAnalyzer;
	}

	public void addScopedAnalyzer( String scope, Analyzer scopedAnalyzer ) {
		scopedAnalyzers.put(scope, scopedAnalyzer);
	}

	public TokenStream tokenStream( String fieldName, Reader reader ) {
		return getAnalyzer(fieldName).tokenStream(fieldName, reader);
	}

	public int getPositionIncrementGap( String fieldName ) {
		return getAnalyzer(fieldName).getPositionIncrementGap(fieldName);
	}

	private Analyzer getAnalyzer( String fieldName ) {
		Analyzer analyzer = scopedAnalyzers.get(fieldName);
		if ( analyzer == null ) {
			analyzer = globalAnalyzer;
		}
		return analyzer;
	}

	public ScopedAnalyzer clone() {
		ScopedAnalyzer clone = new ScopedAnalyzer( globalAnalyzer, scopedAnalyzers );
		return clone;
	}
}
