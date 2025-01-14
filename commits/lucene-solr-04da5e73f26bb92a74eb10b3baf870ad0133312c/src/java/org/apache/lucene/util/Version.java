package org.apache.lucene.util;

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


/**
 * Use by certain classes to match version compatibility
 * across releases of Lucene.
 */
public enum Version {

  /** Match settings and bugs in Lucene's 2.0 release. */
  LUCENE_20,

  /** Match settings and bugs in Lucene's 2.1 release. */
  LUCENE_21,

  /** Match settings and bugs in Lucene's 2.2 release. */
  LUCENE_22,

  /** Match settings and bugs in Lucene's 2.3 release. */
  LUCENE_23,

  /** Match settings and bugs in Lucene's 2.4 release. */
  LUCENE_24,

  /** Match settings and bugs in Lucene's 2.9 release. */
  LUCENE_29,

  /** Match settings and bugs in Lucene's 3.0 release. */
  LUCENE_30,
  
  /* Add new constants for later versions **here** to respect order! */

  /** Use this to get the latest &amp; greatest settings, bug
   *  fixes, etc, for Lucene.
   *
   * <p><b>WARNING</b>: if you use this setting, and then
   * upgrade to a newer release of Lucene, sizable changes
   * may happen.  If precise back compatibility is important
   * then you should instead explicitly specify an actual
   * version.
   */
  LUCENE_CURRENT;

  public boolean onOrAfter(Version other) {
    return compareTo(other) >= 0;
  }
}