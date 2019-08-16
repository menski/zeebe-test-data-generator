package io.zeebe.generator.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {
  @Value("${elasticsearch.host}")
  private String elasticsearchHost;

  @Value("${elasticsearch.port}")
  private int elasticsearchPort;

  @Bean(destroyMethod = "close")
  public RestHighLevelClient elasticsearchClient() {
    return new RestHighLevelClient(
        RestClient.builder(new HttpHost(elasticsearchHost, elasticsearchPort)));
  }
}
