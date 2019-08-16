package io.zeebe.generator.controller;

import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.generator.data.ComplexityTypes;
import io.zeebe.generator.data.CreateInstances;
import io.zeebe.generator.data.FlowNodeTypes;
import io.zeebe.generator.data.Workflow;
import io.zeebe.generator.data.WorkflowSet;
import io.zeebe.generator.data.WorkflowStatistics;
import io.zeebe.generator.service.ElasticsearchService;
import io.zeebe.generator.service.ZeebeService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class AddDataController {

  @Autowired private ElasticsearchService elasticsearchService;
  @Autowired private ZeebeService zeebeService;

  @GetMapping("/add-data")
  public String addData() {
    return "add-data";
  }

  @GetMapping("/distribute-instances")
  public String distributedInstances(final Model model) throws IOException {
    final WorkflowStatistics workflowStatistics = elasticsearchService.getWorkflowStatistics();
    model.addAttribute("statistics", workflowStatistics);
    return "distribute-instances";
  }

  @PostMapping(
      value = "/distribute-instances",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public String distributedInstances(final CreateInstances createInstances) throws IOException {
    final String uuid = zeebeService.distributeInstances(createInstances);

    return "redirect:/distribute-instances?future=" + uuid;
  }

  @GetMapping("/add-instances")
  public String addInstances(final Model model) throws IOException {
    final Map<String, WorkflowSet> workflowSet = new HashMap<>();

    elasticsearchService
        .getWorkflows()
        .forEach(
            w -> {
              final WorkflowSet set =
                  workflowSet.computeIfAbsent(
                      w.getBpmnProcessId(), x -> new WorkflowSet(w.getName(), new ArrayList<>()));
              set.getWorkflows().add(w);
              set.sort();
            });

    model.addAttribute("workflows", workflowSet);
    return "add-instances";
  }

  @GetMapping("/add-instance")
  private String addInstance(
      @RequestParam(name = "id") final String bpmnProcessId,
      @RequestParam(name = "version") final int version,
      final Model model)
      throws IOException {
    final List<Integer> versions = elasticsearchService.getVersions(bpmnProcessId);
    final Workflow workflow = elasticsearchService.getWorkflowStatistics(bpmnProcessId, version);
    model.addAttribute("versions", versions);
    model.addAttribute("workflow", workflow);
    model.addAttribute("operate", "http://localhost:8080/#/instances?filter=%7B%22active%22%3Atrue%2C%22incidents%22%3Atrue%2C%22workflow%22%3A%22" + workflow.getBpmnProcessId() + "%22%2C%22version%22%3A%22" + workflow.getVersion() + "%22%2C%22completed%22%3Atrue%2C%22canceled%22%3Atrue%7D");
    return "add-instance";
  }

  @PostMapping(value = "/create-instances", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public String createInstances(final CreateInstances createInstances, final Model model)
      throws IOException {

    final String uuid = zeebeService.createInstances(createInstances);

    return "redirect:/add-instance?id="
        + createInstances.getBpmnProcessId()
        + "&version="
        + createInstances.getVersion()
        + "&future="
        + uuid;
  }

  @GetMapping("/create-workflow")
  public String createWorkflow(final Model model) {
    model.addAttribute("flowNodes", FlowNodeTypes.values());
    model.addAttribute("complexities", ComplexityTypes.values());
    return "create-workflow";
  }

  @PostMapping(value = "/deploy-workflow", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public String deployWorkflow(@RequestParam final Map<String, String> body) {
    final List<FlowNodeTypes> flowNodes =
        Arrays.stream(FlowNodeTypes.values())
            .filter(t -> body.containsKey(t.name()))
            .flatMap(
                t -> {
                  return IntStream.range(0, Integer.parseInt(body.get(t.name()))).mapToObj(i -> t);
                })
            .collect(Collectors.toList());
    final List<ComplexityTypes> complexities =
        Arrays.stream(ComplexityTypes.values())
            .filter(t -> body.containsKey(t.name()))
            .collect(Collectors.toList());

    final io.zeebe.client.api.response.Workflow workflow =
        zeebeService.deployWorkflow(body.get("workflowName"), flowNodes, complexities);
    return "redirect:/add-instance?id="
        + workflow.getBpmnProcessId()
        + "&version="
        + workflow.getVersion();
  }

  @GetMapping("/create-errors")
  public String createErrorsForm(final Model model) {
    final io.zeebe.client.api.response.Workflow workflow =
        zeebeService
            .getClient()
            .newDeployCommand()
            .addResourceFromClasspath("errorCases.bpmn")
            .send()
            .join()
            .getWorkflows()
            .get(0);

    model.addAttribute("workflowName", workflow.getBpmnProcessId());
    model.addAttribute("workflowVersion", workflow.getVersion());
    model.addAttribute("workflowId", workflow.getWorkflowKey());
    return "create-errors";
  }

  @PostMapping("/create-errors")
  public String createErrors(@RequestParam final Map<String, String> body) {
    final Map<String, Object> variables = new HashMap<>();
    if (body.containsKey("noMoreRetries")) {
      variables.put("noMoreRetries", true);
      final String error = body.get("noMoreRetriesError");
      if (error != null && !error.trim().isEmpty()) {
        variables.put("noMoreRetriesError", error);
      }
    }

    if (!body.containsKey("ioMapping")) {
      variables.put("inputSource", 12);
      variables.put("outputSource", 13);
    }

    if (body.containsKey("extractValue")) {
      if ("wrongType".equals(body.get("extractValueVariable"))) {
        variables.put("uuid", true);
      }
    } else {
      variables.put("uuid", "correlation");
    }

    if (body.containsKey("condition")) {
      if ("wrongType".equals(body.get("conditionVariable"))) {
        variables.put("condition", "wrong");
      }
    } else {
      variables.put("condition", 3);
    }
    final WorkflowInstanceEvent workflowInstanceEvent =
        zeebeService
            .getClient()
            .newCreateInstanceCommand()
            .workflowKey(Long.parseLong(body.get("workflowId")))
            .variables(variables)
            .send()
            .join();

    return "redirect:/add-instance?id="
        + workflowInstanceEvent.getBpmnProcessId()
        + "&version="
        + workflowInstanceEvent.getVersion();
  }

  @PostMapping("/deploy")
  public String deploy(final MultipartFile bpmnFile) throws IOException {
    io.zeebe.client.api.response.Workflow workflow = zeebeService.getClient()
        .newDeployCommand()
        .addResourceBytes(bpmnFile.getBytes(), bpmnFile.getOriginalFilename())
        .send().join().getWorkflows().get(0);
    return "redirect:/add-instance?id="
        + workflow.getBpmnProcessId()
        + "&version="
        + workflow.getVersion();
  }
}
