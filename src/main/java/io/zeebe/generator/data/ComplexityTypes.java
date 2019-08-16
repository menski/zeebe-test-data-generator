package io.zeebe.generator.data;

public enum ComplexityTypes {
  MULTI_INSTANCE("Multi-Instance"),
  NESTED_SUBPROCESS("Nested Subprocess"),
  MESSAGE_BOUNDARY_EVENT("Message Boundary Event"),
  PETER_CASE("Peter Case")
  ;

  private final String displayName;

  ComplexityTypes(final String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
