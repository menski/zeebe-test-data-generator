package io.zeebe.generator.data;

public enum FlowNodeTypes {
  SERVICE_TASK("Service Task"),
  EXCLUSIVE_GATEWAY("Exclusive Gateway"),
  PARALLEL_GATEWAY("Parallel Gateway"),
  SUBPROCESS("Subprocess"),
  ;

  private final String displayName;

  FlowNodeTypes(final String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
