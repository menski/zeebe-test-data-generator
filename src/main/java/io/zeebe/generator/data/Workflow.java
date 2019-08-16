package io.zeebe.generator.data;

import lombok.Data;

@Data
public class Workflow {
  private int version;
  private long workflowId;
  private long key;
  private String name;
  private String bpmnProcessId;
  private WorkflowStatistics statistics = new WorkflowStatistics();

  public String getName() {
    if (name != null) {
      return name;
    }
    else {
      return bpmnProcessId;
    }
  }

  public long getWorkflowId() {
    return key;
  }
}
