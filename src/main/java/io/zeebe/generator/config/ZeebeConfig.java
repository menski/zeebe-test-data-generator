package io.zeebe.generator.config;

import io.zeebe.client.ZeebeClient;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZeebeConfig {

  @Value("${zeebe.host}")
  private String zeebeHost;

  @Value("${zeebe.port}")
  private int zeebePort;

  @Bean(destroyMethod = "close")
  public ZeebeClient zeebeClient() {
    final ZeebeClient client = ZeebeClient.newClientBuilder()
        .brokerContactPoint(zeebeHost + ":" + zeebePort)
        .usePlaintext()
        .build();

    client.newWorker()
        .jobType("demoTask")
        .handler((c, j) -> {
          String alwaysSuccess = j.getCustomHeaders().get("alwaysSuccess");
          Map<String, Object> variables = j.getVariablesAsMap();
          if ((alwaysSuccess == null || !alwaysSuccess.equals("true")) && variables.containsKey("noMoreRetries")) {
            String error = (String) variables.get("noMoreRetriesError");
            if (error != null && !error.trim().isEmpty())  {
              c.newFailCommand(j.getKey()).retries(0).errorMessage(error).send().join();
            }
            else {

              c.newFailCommand(j.getKey()).retries(0).send().join();
            }
          }
          else {
            c.newCompleteCommand(j.getKey()).send().join();
          }
        }).open();

    return client;
  }
}
