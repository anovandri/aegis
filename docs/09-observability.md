# Observability

## Objectives

Observability must answer:

- Where is each project in the lifecycle?
- Which agent ran, with which inputs, model, prompt, tools, and sources?
- Why did a workflow pause, retry, fail, or return to a previous state?
- How much did each agent execution cost?
- How often do humans approve or correct AI output?
- Which knowledge sources are used and accepted?

## Telemetry Layers

### Workflow Telemetry

- Workflow instance ID.
- Workflow definition version.
- Current state.
- State transition events.
- Retry count.
- Wait duration.
- Failure code.
- Resume signal metadata.

### Agent Telemetry

- Execution ID.
- Agent and prompt version.
- Model and parameters.
- Input artifact versions.
- Output artifact version.
- Token usage and cost.
- Latency.
- Tool calls.
- Source citations.
- Validation errors.

### Human Review Telemetry

- Gate type.
- Pending duration.
- Decision.
- Correction count.
- Corrected artifact types.
- Reviewer role.

### Integration Telemetry

- External system.
- Operation.
- Idempotency key.
- Request/response status.
- Retry count.
- External correlation ID.

## Trace Correlation

Use a shared correlation model:

```text
tenantId
projectId
workflowInstanceId
workflowRunId
agentExecutionId
reviewId
requestId
traceId
```

## Dashboards

Initial dashboards:

- Active projects by workflow state.
- Projects waiting for human review.
- Agent execution success/failure rate.
- Agent cost by project and agent.
- Average review wait time.
- Jira publish success/failure.
- Most common requirement gaps.
- Most corrected agent fields.

## Evaluation Datasets

Human corrections should be converted into evaluation records:

```yaml
evaluationRecord:
  id: eval-001
  projectId: PRJ-001
  agentExecutionId: exec-001
  agentName: ArchitectureAgent
  promptVersion: architecture-agent@2026.09.12
  inputArtifactVersions:
    brd: 3
    requirementAnalysis: 2
  rejectedOutputRef: artifact-architecture-v4
  acceptedOutputRef: artifact-architecture-v5
  correctionType: EXISTING_CAPABILITY_MISSED
  reviewerRole: ARCHITECT
```

## Reproducibility

To replay or reproduce an agent decision, store:

- Input artifact versions and hashes.
- Prompt version.
- Agent version.
- Model identifier and parameters.
- Retrieval policy.
- Knowledge source IDs and versions.
- Tool call inputs and outputs or immutable references.
- Output hash.

Exact byte-for-byte reproduction may not always be possible with non-deterministic model services, but audit-grade reconstruction should be possible.
