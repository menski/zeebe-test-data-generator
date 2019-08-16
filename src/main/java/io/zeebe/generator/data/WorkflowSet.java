package io.zeebe.generator.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Data;

@Data
public class WorkflowSet {

  private final String name;
  private final List<Workflow> workflows;

  public void sort() {
    workflows.sort(Comparator.comparingInt(Workflow::getVersion));
  }

  public String getVersion() {
    if (workflows.size() > 1) {
      return "Multiple Versions";
    }
    else {
      return "Version " + workflows.get(0).getVersion();
    }
  }

  public int getLastVersion() {
    return workflows.get(workflows.size() -1 ).getVersion();
  }
}
