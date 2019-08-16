package io.zeebe.generator.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorServiceConfig {
  @Bean
  public ExecutorService executorService() {
    return Executors.newCachedThreadPool();
  }
}
