package io.zeebe.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.generator.data.Workflow;
import io.zeebe.generator.data.WorkflowStatistics;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ElasticsearchService {

  @Autowired private RestHighLevelClient client;

  @Autowired private ObjectMapper objectMapper;

  public List<Workflow> getWorkflows() throws IOException {
    final SearchRequest searchRequest = new SearchRequest("operate-workflow_");
    final SearchSourceBuilder query = new SearchSourceBuilder().from(0).size(10_000);
    searchRequest.source(query);
    final SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

    return Arrays.stream(response.getHits().getHits())
        .map(
            hit -> {
              try {
                return objectMapper.readValue(hit.getSourceAsString(), Workflow.class);
              } catch (IOException e) {
                e.printStackTrace();
                return null;
              }
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private Workflow getWorkflow(final String bpmnProcessId, final int version) throws IOException {
    SearchResponse response;
    SearchHit[] hits;
    do {
      final SearchRequest searchRequest = new SearchRequest("operate-workflow_alias");
      final SearchSourceBuilder query =
          new SearchSourceBuilder()
              .query(
                  QueryBuilders.boolQuery()
                      .must(QueryBuilders.termQuery("bpmnProcessId", bpmnProcessId))
                      .must(QueryBuilders.termQuery("version", version)))
              .from(0)
              .size(10_000);
      searchRequest.source(query);
      response = client.search(searchRequest, RequestOptions.DEFAULT);
      hits = response.getHits().getHits();
    } while (hits.length < 1);
    final String source = hits[0].getSourceAsString();
    return objectMapper.readValue(source, Workflow.class);
  }

  public List<Integer> getVersions(final String bpmnProcessId) throws IOException {
    final SearchRequest searchRequest = new SearchRequest("operate-workflow_alias");
    final SearchSourceBuilder query =
        new SearchSourceBuilder()
            .query(QueryBuilders.termQuery("bpmnProcessId", bpmnProcessId))
            .aggregation(AggregationBuilders.terms("version").field("version"))
            .from(0)
            .size(10_000);
    searchRequest.source(query);
    final SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
    return ((ParsedLongTerms) response.getAggregations().getAsMap().get("version"))
        .getBuckets().stream().map(b -> b.getKeyAsNumber().intValue()).collect(Collectors.toList());
  }

  public Workflow getWorkflowStatistics(final String bpmnProcessId, final int version)
      throws IOException {
    final Workflow workflow = getWorkflow(bpmnProcessId, version);

    final WorkflowStatistics statistics = workflowStatistics(workflow.getKey());
    workflow.setStatistics(statistics);
    return workflow;
  }

  public WorkflowStatistics getWorkflowStatistics() throws IOException {
    final SearchResponse response =
        sendRequest(
            "zeebe-record-workflow-instance",
            0,
                QueryBuilders.termQuery("value.bpmnElementType", "PROCESS"),
            AggregationBuilders.terms("intent").field("intent"));
    final ParsedStringTerms terms = response.getAggregations().get("intent");

    final WorkflowStatistics statistics = new WorkflowStatistics();
    for (final Bucket bucket : terms.getBuckets()) {
      switch (bucket.getKeyAsString()) {
        case "ELEMENT_ACTIVATED":
          statistics.recordActive(bucket.getDocCount());
          break;
        case "ELEMENT_COMPLETED":
          statistics.recordCompleted(bucket.getDocCount());
          break;
        case "ELEMENT_TERMINATED":
          statistics.recordCanceled(bucket.getDocCount());
          break;
      }
    }

    statistics.recordIncidents(openIncidentsCount());

    return statistics;
  }

  private WorkflowStatistics workflowStatistics(final long workflowKey) throws IOException {
    final SearchResponse response =
        sendRequest(
            "zeebe-record-workflow-instance",
            0,
            QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("value.workflowKey", workflowKey))
                .must(QueryBuilders.termQuery("value.bpmnElementType", "PROCESS")),
            AggregationBuilders.terms("intent").field("intent"));
    final ParsedStringTerms terms = response.getAggregations().get("intent");

    final WorkflowStatistics statistics = new WorkflowStatistics();
    for (final Bucket bucket : terms.getBuckets()) {
      switch (bucket.getKeyAsString()) {
        case "ELEMENT_ACTIVATED":
          statistics.recordActive(bucket.getDocCount());
          break;
        case "ELEMENT_COMPLETED":
          statistics.recordCompleted(bucket.getDocCount());
          break;
        case "ELEMENT_TERMINATED":
          statistics.recordCanceled(bucket.getDocCount());
          break;
      }
    }

    statistics.recordIncidents(openIncidentsCount(workflowKey));

    return statistics;
  }

  private long openIncidentsCount() throws IOException {
    final SearchResponse response =
        sendRequest(
            "zeebe-record-incident",
            0,
            QueryBuilders.matchAllQuery(),
            AggregationBuilders.terms("instances")
                .field("value.workflowInstanceKey")
                .size(10_000)
                .subAggregation(AggregationBuilders.terms("intent").field("intent")));
    final ParsedLongTerms instances = response.getAggregations().get("instances");

    long incidents = 0;

    for (final Bucket instancesBuckets : instances.getBuckets()) {
      final ParsedStringTerms intents = instancesBuckets.getAggregations().get("intent");
      long created = 0;
      long resolved = 0;
      for (final Bucket bucket : intents.getBuckets()) {
        switch (bucket.getKeyAsString()) {
          case "CREATED":
            created = bucket.getDocCount();
            break;
          case "RESOLVED":
            resolved = bucket.getDocCount();
            break;
        }

        if (created > resolved) {
          incidents++;
        }
      }
    }

    return incidents;
  }

  private long openIncidentsCount(final long workflowKey) throws IOException {
    final SearchResponse response =
        sendRequest(
            "zeebe-record-incident",
            0,
            QueryBuilders.termQuery("value.workflowKey", workflowKey),
            AggregationBuilders.terms("instances")
                .field("value.workflowInstanceKey")
                .size(10_000)
                .subAggregation(AggregationBuilders.terms("intent").field("intent")));
    final ParsedLongTerms instances = response.getAggregations().get("instances");

    long incidents = 0;

    for (final Bucket instancesBuckets : instances.getBuckets()) {
      final ParsedStringTerms intents = instancesBuckets.getAggregations().get("intent");
      long created = 0;
      long resolved = 0;
      for (final Bucket bucket : intents.getBuckets()) {
        switch (bucket.getKeyAsString()) {
          case "CREATED":
            created = bucket.getDocCount();
            break;
          case "RESOLVED":
            resolved = bucket.getDocCount();
            break;
        }

        if (created > resolved) {
          incidents++;
        }
      }
    }

    return incidents;
  }

  private SearchResponse sendRequest(
      final String index, final int size, final QueryBuilder queryBuilder) throws IOException {
    return sendRequest(index, size, queryBuilder, null);
  }

  private SearchResponse sendRequest(
      final String index,
      final int size,
      final QueryBuilder queryBuilder,
      final AggregationBuilder aggregationBuilder)
      throws IOException {
    final SearchSourceBuilder builder = new SearchSourceBuilder().query(queryBuilder);

    if (aggregationBuilder != null) {
      builder.aggregation(aggregationBuilder);
    }

    final SearchRequest searchRequest = new SearchRequest(index).source(builder);
    return sendRequest(searchRequest);
  }

  private SearchResponse sendRequest(final SearchRequest searchRequest) throws IOException {
    return client.search(searchRequest, RequestOptions.DEFAULT);
  }

  List<Long> getWorkflowKeys() throws IOException {
    final SearchResponse response = sendRequest("operate-workflow_alias", 10_000,
        QueryBuilders.matchAllQuery());

    return Arrays.stream(response.getHits().getHits()).map(h -> (Long) h.getSourceAsMap().get("key"))
        .collect(Collectors.toList());
  }
}
