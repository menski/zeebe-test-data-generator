package io.zeebe.generator.service;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.Workflow;
import io.zeebe.generator.data.ComplexityTypes;
import io.zeebe.generator.data.CreateInstances;
import io.zeebe.generator.data.FlowNodeTypes;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ZeebeService {

  @Autowired private ExecutorService executorService;
  @Autowired private ZeebeClient client;
  @Autowired private FutureService futureService;
  @Autowired private ElasticsearchService elasticsearchService;

  private static String normalize(final String name) {
    return name.toLowerCase().replaceAll(" ", "-");
  }

  private static AbstractFlowNodeBuilder addServiceTask(
      final AbstractFlowNodeBuilder builder, final int id) {
    return builder.serviceTask("task" + id).zeebeTaskType("demoTask").name("Task " + id);
  }

  private static AbstractFlowNodeBuilder addSubProcess(
      AbstractFlowNodeBuilder builder,
      final int id,
      final Function<AbstractFlowNodeBuilder, AbstractFlowNodeBuilder> embedded) {
    builder = builder.subProcess().name("subprocess" + id).embeddedSubProcess().startEvent();
    return embedded.apply(builder).endEvent().subProcessDone();
  }

  private static AbstractFlowNodeBuilder addPeterCase(
      final AbstractFlowNodeBuilder builder, final int id) {
    final String forkId = "peterFork" + id;
    final String joinId = "peterJoin" + id;

    return builder
        .parallelGateway(forkId)
        .exclusiveGateway(joinId)
        .moveToNode(forkId)
        .connectTo(joinId)
        .moveToNode(forkId)
        .connectTo(joinId);
  }

  private static AbstractFlowNodeBuilder addMultiInstance(
      AbstractFlowNodeBuilder builder,
      final int id,
      final Function<AbstractFlowNodeBuilder, AbstractFlowNodeBuilder> embedded) {
    builder =
        builder
            .subProcess()
            .name("subprocess" + id)
            .multiInstance(
                c -> c.zeebeInputCollection("items").zeebeInputElement("item").parallel())
            .embeddedSubProcess()
            .startEvent();
    return embedded.apply(builder).endEvent().subProcessDone();
  }

  private static AbstractFlowNodeBuilder addMessageBoundaryEvent(
      final AbstractFlowNodeBuilder builder, final int id) {
    final String taskId = "task" + id;
    return builder
        .serviceTask(taskId)
        .name("Task " + id)
        .zeebeTaskType("demoTask")
        .boundaryEvent()
        .name("Boundary Event " + id)
        .message(c -> c.name("demoMessage").zeebeCorrelationKey("uuid"))
        .endEvent()
        .moveToNode(taskId);
  }

  public String distributeInstances(final CreateInstances createInstances) throws IOException {
    final List<Long> workflowKeys = elasticsearchService.getWorkflowKeys();
    final Future<Void> future =
        executorService.submit(
            () -> {
              int idx = 0;
              final int size = workflowKeys.size();

              for (int i = 0; i < createInstances.getActive(); i++) {
                final Map<String, Object> variables = new HashMap<>();
                variables.put("items", new int[] {1, 2, 3});
                variables.put("uuid", UUID.randomUUID().toString());
                variables.put("branch", 10);
                client
                    .newCreateInstanceCommand()
                    .workflowKey(workflowKeys.get(idx++ % size))
                    .variables(variables)
                    .send()
                    .join();
              }
              for (int i = 0; i < createInstances.getIncident(); i++) {
                final Map<String, Object> variables = new HashMap<>();
                variables.put("noMoreRetries", true);
                client
                    .newCreateInstanceCommand()
                    .workflowKey(workflowKeys.get(idx++ % size))
                    .variables(variables)
                    .send()
                    .join();
              }
              return null;
            });

    return futureService.putFuture(future);
  }

  public String createInstances(final CreateInstances createInstances) {
    final Future<Void> future =
        executorService.submit(
            () -> {
              final String bpmnProcessId = createInstances.getBpmnProcessId();
              final int version = createInstances.getVersion();
              for (int i = 0; i < createInstances.getActive(); i++) {
                final Map<String, Object> variables = new HashMap<>();
                variables.put("items", new int[] {1, 2, 3});
                variables.put("uuid", UUID.randomUUID().toString());
                variables.put("branch", 10);
                client
                    .newCreateInstanceCommand()
                    .bpmnProcessId(bpmnProcessId)
                    .version(version)
                    .variables(variables)
                    .send()
                    .join();
              }
              for (int i = 0; i < createInstances.getIncident(); i++) {
                final Map<String, Object> variables = new HashMap<>();
                variables.put("noMoreRetries", true);
                client
                    .newCreateInstanceCommand()
                    .bpmnProcessId(bpmnProcessId)
                    .version(version)
                    .variables(variables)
                    .send()
                    .join();
              }
              return null;
            });

    return futureService.putFuture(future);
  }

  public Workflow deployWorkflow(
      final String name,
      final List<FlowNodeTypes> flowNodes,
      final List<ComplexityTypes> complexities) {
    Collections.shuffle(flowNodes);
    final String normalizedName = normalize(name);
    AbstractFlowNodeBuilder builder =
        Bpmn.createExecutableProcess(normalizedName).name(name).startEvent();

    final AtomicInteger counter = new AtomicInteger(1);
    int gatewayId;

    if (complexities.contains(ComplexityTypes.PETER_CASE)) {
      builder = addPeterCase(builder, counter.getAndIncrement());
    }
    if (complexities.contains(ComplexityTypes.MULTI_INSTANCE)) {
      builder =
          addMultiInstance(
              builder,
              counter.getAndIncrement(),
              b2 -> addServiceTask(b2, counter.getAndIncrement()));
    }
    if (complexities.contains(ComplexityTypes.NESTED_SUBPROCESS)) {
      builder =
          addSubProcess(
              builder,
              counter.getAndIncrement(),
              b ->
                  addSubProcess(
                      b,
                      counter.getAndIncrement(),
                      b2 -> addServiceTask(b2, counter.getAndIncrement())));
    }
    if (complexities.contains(ComplexityTypes.MESSAGE_BOUNDARY_EVENT)) {
      builder = addMessageBoundaryEvent(builder, counter.getAndIncrement());
    }

    for (final FlowNodeTypes flowNode : flowNodes) {
      switch (flowNode) {
        case SERVICE_TASK:
          builder = addServiceTask(builder, counter.getAndIncrement());
          break;
        case EXCLUSIVE_GATEWAY:
          gatewayId = counter.getAndIncrement();
          builder =
              addServiceTask(
                      builder
                          .exclusiveGateway("gateway" + gatewayId)
                          .name("Gateway " + gatewayId)
                          .condition("branch > 1"),
                      counter.getAndIncrement())
                  .endEvent()
                  .moveToLastExclusiveGateway()
                  .defaultFlow();
          break;
        case PARALLEL_GATEWAY:
          gatewayId = counter.getAndIncrement();
          builder =
              addServiceTask(
                      builder.parallelGateway("gateway" + gatewayId).name("Gateway " + gatewayId),
                      counter.getAndIncrement())
                  .endEvent()
                  .moveToLastGateway();
          break;
        case SUBPROCESS:
          builder =
              addServiceTask(
                      builder
                          .subProcess()
                          .name("SubProcess " + counter.getAndIncrement())
                          .embeddedSubProcess()
                          .startEvent(),
                      counter.getAndIncrement())
                  .endEvent()
                  .subProcessDone();
          break;
      }
    }

    final BpmnModelInstance modelInstance = builder.endEvent().done();

    return client
        .newDeployCommand()
        .addWorkflowModel(modelInstance, normalizedName + ".bpmn")
        .send()
        .join()
        .getWorkflows()
        .get(0);
  }

  public ZeebeClient getClient() {
    return client;
  }
}
