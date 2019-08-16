package io.zeebe.generator.data;

import lombok.Data;

@Data
public class CreateInstances {
  private String bpmnProcessId;
  private int version;
  private int active;
  private int incident;
}
