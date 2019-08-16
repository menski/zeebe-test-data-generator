package io.zeebe.generator.data;

import lombok.Data;

@Data
public class WorkflowStatistics {

  private long active;
  private long incident;
  private long completed;
  private long canceled;

  public void increaseActive() {
    active++;
  }

  public void recordActive(final long docCount) {
    this.active += docCount;
  }

  public void recordCompleted(final long docCount) {
    this.completed += docCount;
    this.active -= docCount;
  }

  public void recordCanceled(long docCount) {
    this.canceled += docCount;
    this.active -= docCount;
  }

  public void recordIncidents(final long incident) {
    this.incident = incident;
    this.active -= incident;
  }
}
