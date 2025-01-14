package org.apache.solr.search.grouping.distributed.shardresultserializer;

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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.document.StoredDocument;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.grouping.GroupDocs;
import org.apache.lucene.search.grouping.TopGroups;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.UnicodeUtil;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.ShardDoc;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.grouping.Command;
import org.apache.solr.search.grouping.distributed.command.QueryCommand;
import org.apache.solr.search.grouping.distributed.command.QueryCommandResult;
import org.apache.solr.search.grouping.distributed.command.TopGroupsFieldCommand;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation for transforming {@link TopGroups} and {@link TopDocs} into a {@link NamedList} structure and
 * visa versa.
 */
public class TopGroupsResultTransformer implements ShardResultTransformer<List<Command>, Map<String, ?>> {

  private final ResponseBuilder rb;

  public TopGroupsResultTransformer(ResponseBuilder rb) {
    this.rb = rb;
  }

  /**
   * {@inheritDoc}
   */
  public NamedList transform(List<Command> data) throws IOException {
    NamedList<NamedList> result = new NamedList<NamedList>();
    for (Command command : data) {
      NamedList commandResult;
      if (TopGroupsFieldCommand.class.isInstance(command)) {
        TopGroupsFieldCommand fieldCommand = (TopGroupsFieldCommand) command;
        SchemaField groupField = rb.req.getSearcher().getSchema().getField(fieldCommand.getKey());
        commandResult = serializeTopGroups(fieldCommand.result(), groupField);
      } else if (QueryCommand.class.isInstance(command)) {
        QueryCommand queryCommand = (QueryCommand) command;
        commandResult = serializeTopDocs(queryCommand.result());
      } else {
        commandResult = null;
      }

      result.add(command.getKey(), commandResult);
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  public Map<String, ?> transformToNative(NamedList<NamedList> shardResponse, Sort groupSort, Sort sortWithinGroup, String shard) {
    Map<String, Object> result = new HashMap<String, Object>();

    for (Map.Entry<String, NamedList> entry : shardResponse) {
      String key = entry.getKey();
      NamedList commandResult = entry.getValue();
      Integer totalGroupedHitCount = (Integer) commandResult.get("totalGroupedHitCount");
      Integer totalHits = (Integer) commandResult.get("totalHits");
      if (totalHits != null) {
        Integer matches = (Integer) commandResult.get("matches");
        Float maxScore = (Float) commandResult.get("maxScore");
        if (maxScore == null) {
          maxScore = Float.NaN;
        }

        @SuppressWarnings("unchecked")
        List<NamedList<Object>> documents = (List<NamedList<Object>>) commandResult.get("documents");
        ScoreDoc[] scoreDocs = new ScoreDoc[documents.size()];
        int j = 0;
        for (NamedList<Object> document : documents) {
          Object uniqueId = document.get("id").toString();
          Float score = (Float) document.get("score");
          if (score == null) {
            score = Float.NaN;
          }
          Object[] sortValues = ((List) document.get("sortValues")).toArray();
          scoreDocs[j++] = new ShardDoc(score, sortValues, uniqueId, shard);
        }
        result.put(key, new QueryCommandResult(new TopDocs(totalHits, scoreDocs, maxScore), matches));
        continue;
      }

      Integer totalHitCount = (Integer) commandResult.get("totalHitCount");

      List<GroupDocs<BytesRef>> groupDocs = new ArrayList<GroupDocs<BytesRef>>();
      for (int i = 2; i < commandResult.size(); i++) {
        String groupValue = commandResult.getName(i);
        @SuppressWarnings("unchecked")
        NamedList<Object> groupResult = (NamedList<Object>) commandResult.getVal(i);
        Integer totalGroupHits = (Integer) groupResult.get("totalHits");
        Float maxScore = (Float) groupResult.get("maxScore");
        if (maxScore == null) {
          maxScore = Float.NaN;
        }

        @SuppressWarnings("unchecked")
        List<NamedList<Object>> documents = (List<NamedList<Object>>) groupResult.get("documents");
        ScoreDoc[] scoreDocs = new ScoreDoc[documents.size()];
        int j = 0;
        for (NamedList<Object> document : documents) {
          Object uniqueId = document.get("id").toString();
          Float score = (Float) document.get("score");
          if (score == null) {
            score = Float.NaN;
          }
          Object[] sortValues = ((List) document.get("sortValues")).toArray();
          scoreDocs[j++] = new ShardDoc(score, sortValues, uniqueId, shard);
        }

        BytesRef groupValueRef = groupValue != null ? new BytesRef(groupValue) : null;
        groupDocs.add(new GroupDocs<BytesRef>(Float.NaN, maxScore, totalGroupHits, scoreDocs, groupValueRef, null));
      }

      @SuppressWarnings("unchecked")
      GroupDocs<BytesRef>[] groupDocsArr = groupDocs.toArray(new GroupDocs[groupDocs.size()]);
      TopGroups<BytesRef> topGroups = new TopGroups<BytesRef>(
           groupSort.getSort(), sortWithinGroup.getSort(), totalHitCount, totalGroupedHitCount, groupDocsArr, Float.NaN
      );

      result.put(key, topGroups);
    }

    return result;
  }

  protected NamedList serializeTopGroups(TopGroups<BytesRef> data, SchemaField groupField) throws IOException {
    NamedList<Object> result = new NamedList<Object>();
    result.add("totalGroupedHitCount", data.totalGroupedHitCount);
    result.add("totalHitCount", data.totalHitCount);
    if (data.totalGroupCount != null) {
      result.add("totalGroupCount", data.totalGroupCount);
    }
    CharsRef spare = new CharsRef();

    SchemaField uniqueField = rb.req.getSearcher().getSchema().getUniqueKeyField();
    for (GroupDocs<BytesRef> searchGroup : data.groups) {
      NamedList<Object> groupResult = new NamedList<Object>();
      groupResult.add("totalHits", searchGroup.totalHits);
      if (!Float.isNaN(searchGroup.maxScore)) {
        groupResult.add("maxScore", searchGroup.maxScore);
      }

      List<NamedList<Object>> documents = new ArrayList<NamedList<Object>>();
      for (int i = 0; i < searchGroup.scoreDocs.length; i++) {
        NamedList<Object> document = new NamedList<Object>();
        documents.add(document);

        StoredDocument doc = retrieveDocument(uniqueField, searchGroup.scoreDocs[i].doc);
        document.add("id", uniqueField.getType().toExternal(doc.getField(uniqueField.getName())));
        if (!Float.isNaN(searchGroup.scoreDocs[i].score))  {
          document.add("score", searchGroup.scoreDocs[i].score);
        }
        if (!(searchGroup.scoreDocs[i] instanceof FieldDoc)) {
          continue;
        }

        FieldDoc fieldDoc = (FieldDoc) searchGroup.scoreDocs[i];
        Object[] convertedSortValues  = new Object[fieldDoc.fields.length];
        for (int j = 0; j < fieldDoc.fields.length; j++) {
          Object sortValue  = fieldDoc.fields[j];
          Sort sortWithinGroup = rb.getGroupingSpec().getSortWithinGroup();
          SchemaField field = sortWithinGroup.getSort()[j].getField() != null ? rb.req.getSearcher().getSchema().getFieldOrNull(sortWithinGroup.getSort()[j].getField()) : null;
          if (field != null) {
            FieldType fieldType = field.getType();
            if (sortValue instanceof BytesRef) {
              UnicodeUtil.UTF8toUTF16((BytesRef)sortValue, spare);
              String indexedValue = spare.toString();
              sortValue = fieldType.toObject(field.createField(fieldType.indexedToReadable(indexedValue), 1.0f));
            } else if (sortValue instanceof String) {
              sortValue = fieldType.toObject(field.createField(fieldType.indexedToReadable((String) sortValue), 1.0f));
            }
          }
          convertedSortValues[j] = sortValue;
        }
        document.add("sortValues", convertedSortValues);
      }
      groupResult.add("documents", documents);
      String groupValue = searchGroup.groupValue != null ? groupField.getType().indexedToReadable(searchGroup.groupValue.utf8ToString()): null;
      result.add(groupValue, groupResult);
    }

    return result;
  }

  protected NamedList serializeTopDocs(QueryCommandResult result) throws IOException {
    NamedList<Object> queryResult = new NamedList<Object>();
    queryResult.add("matches", result.getMatches());
    queryResult.add("totalHits", result.getTopDocs().totalHits);
    if (rb.getGroupingSpec().isNeedScore()) {
      queryResult.add("maxScore", result.getTopDocs().getMaxScore());
    }
    List<NamedList> documents = new ArrayList<NamedList>();
    queryResult.add("documents", documents);

    SchemaField uniqueField = rb.req.getSearcher().getSchema().getUniqueKeyField();
    CharsRef spare = new CharsRef();
    for (ScoreDoc scoreDoc : result.getTopDocs().scoreDocs) {
      NamedList<Object> document = new NamedList<Object>();
      documents.add(document);

      StoredDocument doc = retrieveDocument(uniqueField, scoreDoc.doc);
      document.add("id", uniqueField.getType().toExternal(doc.getField(uniqueField.getName())));
      if (rb.getGroupingSpec().isNeedScore())  {
        document.add("score", scoreDoc.score);
      }
      if (!FieldDoc.class.isInstance(scoreDoc)) {
        continue;
      }

      FieldDoc fieldDoc = (FieldDoc) scoreDoc;
      Object[] convertedSortValues  = new Object[fieldDoc.fields.length];
      for (int j = 0; j < fieldDoc.fields.length; j++) {
        Object sortValue  = fieldDoc.fields[j];
        Sort groupSort = rb.getGroupingSpec().getGroupSort();
        SchemaField field = groupSort.getSort()[j].getField() != null ? rb.req.getSearcher().getSchema().getFieldOrNull(groupSort.getSort()[j].getField()) : null;
        if (field != null) {
          FieldType fieldType = field.getType();
          if (sortValue instanceof BytesRef) {
            UnicodeUtil.UTF8toUTF16((BytesRef)sortValue, spare);
            String indexedValue = spare.toString();
            sortValue = fieldType.toObject(field.createField(fieldType.indexedToReadable(indexedValue), 1.0f));
          } else if (sortValue instanceof String) {
            sortValue = fieldType.toObject(field.createField(fieldType.indexedToReadable((String) sortValue), 1.0f));
          }
        }
        convertedSortValues[j] = sortValue;
      }
      document.add("sortValues", convertedSortValues);
    }

    return queryResult;
  }

  private StoredDocument retrieveDocument(final SchemaField uniqueField, int doc) throws IOException {
    DocumentStoredFieldVisitor visitor = new DocumentStoredFieldVisitor(uniqueField.getName());
    rb.req.getSearcher().doc(doc, visitor);
    return visitor.getDocument();
  }

}
