/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.util;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import org.hibernate.search.util.logging.impl.Log;
import org.hibernate.search.util.logging.impl.LoggerFactory;

/**
 * Helper class to hide boilerplate code when using Lucene Analyzers.
 *
 * <p>Taken and modified from <i>Lucene in Action</i>.
 *
 * @author Hardy Ferentschik
 */
public final class AnalyzerUtils {

	private AnalyzerUtils() {
		//not allowed
	}

	public static final Log log = LoggerFactory.make( MethodHandles.lookup() );

	public static List<String> tokenizedTermValues(Analyzer analyzer, String field, String text) throws IOException {
		final List<String> tokenList = new ArrayList<String>();
		final TokenStream stream = analyzer.tokenStream( field, new StringReader( text ) );
		try {
			CharTermAttribute term = stream.addAttribute( CharTermAttribute.class );
			stream.reset();
			while ( stream.incrementToken() ) {
				String s = new String( term.buffer(), 0, term.length() );
				tokenList.add( s );
			}
			stream.end();
		}
		finally {
			stream.close();
		}
		return tokenList;
	}

	public static Token[] tokensFromAnalysis(Analyzer analyzer, String field, String text) throws IOException {
		final List<Token> tokenList = new ArrayList<Token>();
		final TokenStream stream = analyzer.tokenStream( field, new StringReader( text ) );
		try {
			CharTermAttribute term = stream.addAttribute( CharTermAttribute.class );
			stream.reset();
			while ( stream.incrementToken() ) {
				Token token = new Token();
				token.copyBuffer( term.buffer(), 0, term.length() );
				tokenList.add( token );
			}
			stream.end();
		}
		finally {
			stream.close();
		}
		return tokenList.toArray( new Token[tokenList.size()] );
	}

	public static void displayTokens(Analyzer analyzer, String field, String text) throws IOException {
		Token[] tokens = tokensFromAnalysis( analyzer, field, text );

		for ( Token token : tokens ) {
			log.debug( "[" + getTermText( token ) + "] " );
		}
	}

	/**
	 * Utility to print out the tokens generated by a specific Analyzer on an example text.
	 * You have to specify the field name as well, as some Analyzer(s) might have a different
	 * configuration for each field.
	 * This implementation is not suited for top performance and is not used by Hibernate Search
	 * during automatic indexing: this method is only made available to help understanding
	 * and debugging the analyzer chain.
	 * @param analyzer the Analyzer to use
	 * @param field the name of the field: might affect the Analyzer behaviour
	 * @param text some sample input
	 * @param printTo Human readable text will be printed to this output. Passing {@code System.out} might be a good idea.
	 * @throws IOException if an I/O error occurs
	 */
	public static void displayTokensWithPositions(Analyzer analyzer, String field, String text, PrintStream printTo) throws IOException {
		Token[] tokens = tokensFromAnalysis( analyzer, field, text );

		int position = 0;

		for ( Token token : tokens ) {
			int increment = token.getPositionIncrement();

			if ( increment > 0 ) {
				position = position + increment;
				printTo.println();
				printTo.print( position + ": " );
			}

			log.debug( "[" + getTermText( token ) + "] " );
		}
	}

	public static void displayTokensWithFullDetails(Analyzer analyzer, String field, String text) throws IOException {
		Token[] tokens = tokensFromAnalysis( analyzer, field, text );
		StringBuilder builder = new StringBuilder();
		int position = 0;

		for ( Token token : tokens ) {
			int increment = token.getPositionIncrement();

			if ( increment > 0 ) {
				position = position + increment;
				builder.append( "\n" ).append( position ).append( ": " );
			}

			builder.append( "[" )
					.append( getTermText( token ) )
					.append( ":" )
					.append( token.startOffset() )
					.append( "->" )
					.append(
							token.endOffset()
					)
					.append( ":" )
					.append( token.type() )
					.append( "] " );
			log.debug( builder.toString() );
		}
	}

	public static String getTermText(Token token) {
		return new String( token.buffer(), 0, token.length() );
	}
}
