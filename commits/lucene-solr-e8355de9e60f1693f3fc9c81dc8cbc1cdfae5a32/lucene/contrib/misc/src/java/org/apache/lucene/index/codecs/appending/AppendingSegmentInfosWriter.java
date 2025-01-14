package org.apache.lucene.index.codecs.appending;

/*
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

import java.io.IOException;

import org.apache.lucene.index.IOContext;
import org.apache.lucene.index.codecs.DefaultSegmentInfosWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexOutput;

public class AppendingSegmentInfosWriter extends DefaultSegmentInfosWriter {

  @Override
  protected IndexOutput createOutput(Directory dir, String segmentsFileName, IOContext context)
          throws IOException {
    return dir.createOutput(segmentsFileName, context);
  }

  @Override
  public void finishCommit(IndexOutput out) throws IOException {
    out.close();
  }

  @Override
  public void prepareCommit(IndexOutput segmentOutput) throws IOException {
    // noop
  }

}
