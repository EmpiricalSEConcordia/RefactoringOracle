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

import java.util.Map;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;

/**
 * @deprecated Use {@link LowerCaseFilterFactory} instead which has the
 *             same functionality.
 */
@Deprecated
public class RussianLowerCaseFilterFactory extends BaseTokenFilterFactory {

	@Override
	public void init(Map<String, String> args) {
		super.init( args );
		if ( args.containsKey( "charset" ) ) {
			throw new SolrException(
					ErrorCode.SERVER_ERROR,
					"The charset parameter is no longer supported.  "
							+ "Please process your documents as Unicode instead."
			);
		}
	}

	public TokenFilter create(TokenStream in) {
		// hardcode the version to give exactly the old behavior
		return new LowerCaseFilter( in );
	}
}

