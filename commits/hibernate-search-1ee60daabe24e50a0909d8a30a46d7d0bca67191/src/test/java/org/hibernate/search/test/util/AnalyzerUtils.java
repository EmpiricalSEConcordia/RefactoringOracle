// $Id$
package org.hibernate.search.test.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.slf4j.Logger;

import org.hibernate.search.util.LoggerFactory;

/**
 * Helper class to test analyzers. Taken and modified from <i>Lucene in Action</i>.
 *
 * @author Hardy Ferentschik
 */
public class AnalyzerUtils {

	public static final Logger log = LoggerFactory.make();

	public static Token[] tokensFromAnalysis(Analyzer analyzer, String field, String text) throws IOException {
		TokenStream stream = analyzer.tokenStream( field, new StringReader( text ) );
		List<Token> tokenList = new ArrayList<Token>();
		Token reusableToken = new Token();
		while ( true ) {

			Token token = stream.next( reusableToken );
			if ( token == null ) {
				break;
			}

			tokenList.add( ( Token ) token.clone() );
		}

		return tokenList.toArray( new Token[tokenList.size()] );
	}

	public static void displayTokens(Analyzer analyzer, String field, String text) throws IOException {
		Token[] tokens = tokensFromAnalysis( analyzer, field, text );

		for ( Token token : tokens ) {
			log.debug( "[" + getTermText( token ) + "] " );
		}
	}

	public static void displayTokensWithPositions(Analyzer analyzer, String field, String text) throws IOException {
		Token[] tokens = tokensFromAnalysis( analyzer, field, text );

		int position = 0;

		for ( Token token : tokens ) {
			int increment = token.getPositionIncrement();

			if ( increment > 0 ) {
				position = position + increment;
				System.out.println();
				System.out.print( position + ": " );
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

	public static void assertTokensEqual(Token[] tokens, String[] strings) {
		Assert.assertEquals( strings.length, tokens.length );

		for ( int i = 0; i < tokens.length; i++ ) {
			Assert.assertEquals( "index " + i, strings[i], getTermText( tokens[i] ) );
		}
	}

	public static String getTermText(Token token) {
		return new String( token.termBuffer(), 0, token.termLength() );
	}
}
