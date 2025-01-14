package org.apache.lucene.benchmark.byTask.tasks;

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

import org.apache.lucene.benchmark.byTask.PerfRunData;
import org.apache.lucene.benchmark.byTask.utils.Config;
import org.apache.lucene.index.IndexDeletionPolicy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MergeScheduler;
import org.apache.lucene.index.MergePolicy;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Create an index. <br>
 * Other side effects: index writer object in perfRunData is set. <br>
 * Relevant properties: <code>merge.factor, max.buffered,
 *  max.field.length, ram.flush.mb [default 0],
 *  [default true]</code>.
 * <p>
 * This task also supports a "writer.info.stream" property with the following
 * values:
 * <ul>
 * <li>SystemOut - sets {@link IndexWriter#setInfoStream(java.io.PrintStream)}
 * to {@link System#out}.
 * <li>SystemErr - sets {@link IndexWriter#setInfoStream(java.io.PrintStream)}
 * to {@link System#err}.
 * <li>&lt;file_name&gt; - attempts to create a file given that name and sets
 * {@link IndexWriter#setInfoStream(java.io.PrintStream)} to that file. If this
 * denotes an invalid file name, or some error occurs, an exception will be
 * thrown.
 * </ul>
 */
public class CreateIndexTask extends PerfTask {

  public CreateIndexTask(PerfRunData runData) {
    super(runData);
  }

  public static void setIndexWriterConfig(IndexWriter writer, Config config) throws IOException {

    final String mergeScheduler = config.get("merge.scheduler",
                                             "org.apache.lucene.index.ConcurrentMergeScheduler");
    try {
      writer.setMergeScheduler(Class.forName(mergeScheduler).asSubclass(MergeScheduler.class).newInstance());
    } catch (Exception e) {
      throw new RuntimeException("unable to instantiate class '" + mergeScheduler + "' as merge scheduler", e);
    }

    final String mergePolicy = config.get("merge.policy",
                                          "org.apache.lucene.index.LogByteSizeMergePolicy");
    try {
      writer.setMergePolicy(Class.forName(mergePolicy).asSubclass(MergePolicy.class).getConstructor(IndexWriter.class).newInstance(writer));
    } catch (Exception e) {
      throw new RuntimeException("unable to instantiate class '" + mergePolicy + "' as merge policy", e);
    }

    writer.setUseCompoundFile(config.get("compound",true));
    writer.setMergeFactor(config.get("merge.factor",OpenIndexTask.DEFAULT_MERGE_PFACTOR));
    writer.setMaxFieldLength(config.get("max.field.length",OpenIndexTask.DEFAULT_MAX_FIELD_LENGTH));

    final double ramBuffer = config.get("ram.flush.mb",OpenIndexTask.DEFAULT_RAM_FLUSH_MB);
    final int maxBuffered = config.get("max.buffered",OpenIndexTask.DEFAULT_MAX_BUFFERED);
    if (maxBuffered == IndexWriter.DISABLE_AUTO_FLUSH) {
      writer.setRAMBufferSizeMB(ramBuffer);
      writer.setMaxBufferedDocs(maxBuffered);
    } else {
      writer.setMaxBufferedDocs(maxBuffered);
      writer.setRAMBufferSizeMB(ramBuffer);
    }
    
    String infoStreamVal = config.get("writer.info.stream", null);
    if (infoStreamVal != null) {
      if (infoStreamVal.equals("SystemOut")) {
        writer.setInfoStream(System.out);
      } else if (infoStreamVal.equals("SystemErr")) {
        writer.setInfoStream(System.err);
      } else {
        File f = new File(infoStreamVal).getAbsoluteFile();
        writer.setInfoStream(new PrintStream(new BufferedOutputStream(new FileOutputStream(f))));
      }
    }
  }
  
  public static IndexDeletionPolicy getIndexDeletionPolicy(Config config) {
    String deletionPolicyName = config.get("deletion.policy", "org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy");
    IndexDeletionPolicy indexDeletionPolicy = null;
    RuntimeException err = null;
    try {
      indexDeletionPolicy = Class.forName(deletionPolicyName).asSubclass(IndexDeletionPolicy.class).newInstance();
    } catch (IllegalAccessException iae) {
      err = new RuntimeException("unable to instantiate class '" + deletionPolicyName + "' as IndexDeletionPolicy");
      err.initCause(iae);
    } catch (InstantiationException ie) {
      err = new RuntimeException("unable to instantiate class '" + deletionPolicyName + "' as IndexDeletionPolicy");
      err.initCause(ie);
    } catch (ClassNotFoundException cnfe) {
      err = new RuntimeException("unable to load class '" + deletionPolicyName + "' as IndexDeletionPolicy");
      err.initCause(cnfe);
    }
    if (err != null)
      throw err;
    return indexDeletionPolicy;
  }
  
  public int doLogic() throws IOException {
    PerfRunData runData = getRunData();
    Config config = runData.getConfig();
    
    IndexDeletionPolicy indexDeletionPolicy = getIndexDeletionPolicy(config);
    
    IndexWriter writer = new IndexWriter(runData.getDirectory(),
                                         runData.getAnalyzer(),
                                         true, indexDeletionPolicy,
                                         IndexWriter.MaxFieldLength.LIMITED);
    setIndexWriterConfig(writer, config);
    runData.setIndexWriter(writer);
    return 1;
  }
}
