package com.aegisflow.api.web;

import com.aegisflow.api.application.readmodel.ControlPanelReadModelService;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.AgentRunDetail;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.AgentRunSummary;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.AuditEvent;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.DashboardReadModel;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.JiraDraftDetail;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.JiraDraftSummary;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.OperationsHealth;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.ProjectRoom;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.ProjectSummary;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.ReviewQueueItem;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.WorkflowOperation;
import com.aegisflow.api.domain.AgentExecutionStatus;
import com.aegisflow.api.domain.ReviewStatus;
import com.aegisflow.api.domain.ReviewType;
import com.aegisflow.api.domain.WorkflowState;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
public class ControlPanelReadModelController {
    private final ControlPanelReadModelService readModelService;

    public ControlPanelReadModelController(ControlPanelReadModelService readModelService) {
        this.readModelService = readModelService;
    }

    @GetMapping("/api/dashboard")
    DashboardReadModel dashboard() {
        return readModelService.dashboard();
    }

    @GetMapping("/api/projects")
    List<ProjectSummary> projects(
            @RequestParam(required = false) WorkflowState state,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return readModelService.projects(
                Optional.ofNullable(state),
                Optional.ofNullable(owner),
                Optional.ofNullable(domain),
                boundedLimit(limit)
        );
    }

    @GetMapping("/api/projects/{projectId}/room")
    ProjectRoom projectRoom(@PathVariable UUID projectId) {
        return readModelService.projectRoom(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @GetMapping("/api/reviews/my-queue")
    List<ReviewQueueItem> myQueue(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) ReviewType reviewType,
            @RequestParam(required = false) ReviewStatus status
    ) {
        return readModelService.myReviewQueue(
                Optional.ofNullable(role),
                Optional.ofNullable(reviewType),
                Optional.ofNullable(status)
        );
    }

    @GetMapping("/api/agent-runs")
    List<AgentRunSummary> agentRuns(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) AgentExecutionStatus status,
            @RequestParam(required = false) WorkflowState workflowState,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return readModelService.agentRuns(
                Optional.ofNullable(projectId),
                Optional.ofNullable(agentName),
                Optional.ofNullable(status),
                Optional.ofNullable(workflowState),
                boundedLimit(limit)
        );
    }

    @GetMapping("/api/agent-runs/{executionId}")
    AgentRunDetail agentRunDetail(@PathVariable UUID executionId) {
        return readModelService.agentRunDetail(executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent run not found"));
    }

    @GetMapping("/api/jira-drafts")
    List<JiraDraftSummary> jiraDrafts() {
        return readModelService.jiraDrafts();
    }

    @GetMapping("/api/jira-drafts/{projectId}")
    JiraDraftDetail jiraDraft(@PathVariable UUID projectId) {
        return readModelService.jiraDraftDetail(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira draft not found"));
    }

    @GetMapping("/api/audit/events")
    List<AuditEvent> auditEvents(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return readModelService.auditEvents(Optional.ofNullable(projectId), boundedLimit(limit));
    }

    @GetMapping("/api/operations/health")
    OperationsHealth operationsHealth() {
        return readModelService.operationsHealth();
    }

    @GetMapping("/api/operations/workflows")
    List<WorkflowOperation> workflows(@RequestParam(defaultValue = "100") int limit) {
        return readModelService.workflows(boundedLimit(limit));
    }

    private int boundedLimit(int requestedLimit) {
        if (requestedLimit < 1) {
            return 1;
        }
        return Math.min(requestedLimit, 500);
    }
}
