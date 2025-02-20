/**
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.hadoop.yarn.security.client;

import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience.Public;
import org.apache.hadoop.classification.InterfaceStability.Stable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.security.token.TokenSelector;

@Public
@Stable
public class ClientTokenSelector implements
    TokenSelector<ClientTokenIdentifier> {

  private static final Log LOG = LogFactory
      .getLog(ClientTokenSelector.class);

  @SuppressWarnings("unchecked")
  public Token<ClientTokenIdentifier> selectToken(Text service,
      Collection<Token<? extends TokenIdentifier>> tokens) {
    if (service == null) {
      return null;
    }
    LOG.debug("Looking for a token with service " + service.toString());
    for (Token<? extends TokenIdentifier> token : tokens) {
      LOG.debug("Token kind is " + token.getKind().toString()
          + " and the token's service name is " + token.getService());
      if (ClientTokenIdentifier.KIND_NAME.equals(token.getKind())
          && service.equals(token.getService())) {
        return (Token<ClientTokenIdentifier>) token;
      }
    }
    return null;
  }

}
