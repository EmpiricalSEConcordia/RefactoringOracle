/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.analysis;

import java.io.IOException;
import java.nio.CharBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

/**
 * A TokenFilter which applies a Pattern to each token in the stream,
 * replacing match occurances with the specified replacement string.
 * <p/>
 * <p>
 * <b>Note:</b> Depending on the input and the pattern used and the input
 * TokenStream, this TokenFilter may produce Tokens whose text is the empty
 * string.
 * </p>
 *
 * @version $Id:$
 * @see Pattern
 */
public final class PatternReplaceFilter extends TokenFilter {
	private final Pattern p;
	private final String replacement;
	private final boolean all;
	private final TermAttribute termAtt;

	/**
	 * Constructs an instance to replace either the first, or all occurances
	 *
	 * @param in the TokenStream to process
	 * @param p the patterm to apply to each Token
	 * @param replacement the "replacement string" to substitute, if null a
	 * blank string will be used. Note that this is not the literal
	 * string that will be used, '$' and '\' have special meaning.
	 * @param all if true, all matches will be replaced otherwise just the first match.
	 *
	 * @see Matcher#quoteReplacement
	 */
	public PatternReplaceFilter(TokenStream in,
								Pattern p,
								String replacement,
								boolean all) {
		super( in );
		this.p = p;
		this.replacement = ( null == replacement ) ? "" : replacement;
		this.all = all;
		this.termAtt = ( TermAttribute ) addAttribute( TermAttribute.class );
	}

	@Override
	public boolean incrementToken() throws IOException {
		if ( !input.incrementToken() ) {
			return false;
		}

		CharSequence text = CharBuffer.wrap( termAtt.termBuffer(), 0, termAtt.termLength() );
		Matcher m = p.matcher( text );

		if ( all ) {
			termAtt.setTermBuffer( m.replaceAll( replacement ) );
		}
		else {
			termAtt.setTermBuffer( m.replaceFirst( replacement ) );
		}

		return true;
	}

}
