# Workflow Design

## Workflow Ownership

AegisFlow has two complementary state systems:

- Temporal or another durable workflow engine owns execution progress, timers, retries, pauses, and resume signals.
- PostgreSQL owns canonical business state, artifacts, approvals, audit records, and integration results.

This avoids making workflow history the only way to answer business questions while still gaining durable execution.

## MVP Lifecycle

```text
BRD_SUBMITTED
  -> BRD_ANALYSIS
  -> REQUIREMENT_ANALYSIS
  -> ARCHITECTURE_ANALYSIS
  -> SYSTEM_ANALYSIS
  -> HUMAN_TECHNICAL_REVIEW
  -> ESTIMATION
  -> JIRA_DRAFT
  -> HUMAN_PM_APPROVAL
  -> JIRA_CREATED
```

## Container Architecture

```mermaid
flowchart TB
    subgraph Client[Human Channels]
        Web[Review Portal]
    end

    subgraph App[AegisFlow Modular Monolith]
        API[Application API]
        Auth[AuthZ/AuthN Module]
        ProjectSvc[Project Context Service]
        ArtifactSvc[Artifact Service]
        ReviewSvc[Human Review Service]
        AgentSvc[Agent Invocation Service]
        EstimationSvc[Estimation Policy Service]
        JiraSvc[Jira Draft and Publish Service]
        KnowledgeSvc[Knowledge Facade]
        Outbox[Outbox Publisher]
    end

    subgraph Workflow[Workflow Runtime]
        Temporal[Temporal Cluster]
        Workers[Workflow Workers]
    end

    subgraph Data[Persistence]
        PG[(PostgreSQL)]
        Vec[(pgvector)]
        Audit[(Audit Tables)]
    end

    AI[AI Provider]
    Jira[Jira]
    Enterprise[Enterprise Knowledge Systems]

    Web --> API
    API --> Auth
    API --> ProjectSvc
    API --> ReviewSvc
    Workers --> ProjectSvc
    Workers --> AgentSvc
    AgentSvc --> AI
    AgentSvc --> KnowledgeSvc
    KnowledgeSvc --> Enterprise
    KnowledgeSvc --> Vec
    ProjectSvc --> PG
    ArtifactSvc --> PG
    ReviewSvc --> PG
    JiraSvc --> Jira
    JiraSvc --> PG
    App --> Audit
    Outbox --> Enterprise
    Temporal --> Workers
```

## Workflow Step Contract

Each workflow state must declare:

| Field | Meaning |
|---|---|
| `state` | Current lifecycle enum |
| `entryCriteria` | Required artifact versions and statuses |
| `executor` | Agent, deterministic service, human gate, or external integration |
| `input` | Validated payload or `ProjectContext` projection |
| `outputSchema` | JSON schema to validate result |
| `transitionRules` | Allowed next states based on structured output |
| `retryPolicy` | Retry count, backoff, timeout, non-retryable failures |
| `failurePath` | Wait, escalate, rollback, or return state |
| `auditRequirements` | Records that must be persisted before transition |

## State Responsibilities

| State | Executor | Output |
|---|---|---|
| `BRD_SUBMITTED` | Deterministic service | BRD artifact version accepted |
| `BRD_ANALYSIS` | BRD Intake Agent | Structured BRD extraction |
| `REQUIREMENT_ANALYSIS` | Requirement Analyst Agent | Completeness and clarification analysis |
| `ARCHITECTURE_ANALYSIS` | Architecture Agent | Proposed architecture and risks |
| `SYSTEM_ANALYSIS` | System Analyst Agent | API, data, event, and scenario analysis |
| `HUMAN_TECHNICAL_REVIEW` | Human approval | Approved, changed, rejected, or clarification decision |
| `ESTIMATION` | Estimation Agent | Advisory estimate |
| `JIRA_DRAFT` | Jira Planning Agent | Draft epic/story/task hierarchy |
| `HUMAN_PM_APPROVAL` | Human approval | Approved, changed, or rejected plan |
| `JIRA_CREATED` | Jira integration service | Jira ticket keys and publish result |

## BRD To Jira End-To-End Sequence

```mermaid
sequenceDiagram
    actor Business
    actor Architect
    actor PM
    participant API as AegisFlow API
    participant WF as Workflow Engine
    participant Agent as Agent Runtime
    participant DB as PostgreSQL
    participant Knowledge as Knowledge Facade
    participant Jira as Jira

    Business->>API: Submit BRD
    API->>DB: Store BRD v1
    API->>WF: Start project workflow
    WF->>Agent: Run BRD Intake with ProjectContext
    Agent->>DB: Persist intake artifact
    WF->>Agent: Run Requirement Analyst
    Agent->>DB: Persist requirement analysis
    WF->>Agent: Run Architecture Agent
    Agent->>Knowledge: Query approved enterprise sources
    Knowledge-->>Agent: Cited sources
    Agent->>DB: Persist architecture artifact
    WF->>Agent: Run System Analyst
    Agent->>DB: Persist system analysis
    WF->>API: Create technical review task
    Architect->>API: Approve or correct
    API->>DB: Store HumanReview and corrected versions
    API->>WF: Resume workflow
    WF->>Agent: Run Estimation Agent
    Agent->>DB: Persist estimation
    WF->>Agent: Run Jira Planning Agent
    Agent->>DB: Persist Jira draft
    WF->>API: Create PM review task
    PM->>API: Approve or correct
    API->>DB: Store PM decision
    API->>WF: Resume workflow
    WF->>Jira: Create approved tickets idempotently
    Jira-->>WF: Jira keys
    WF->>DB: Store Jira creation result
```

## Human Wait States

Human review states pause indefinitely until a valid decision is submitted. The workflow must store the pending review ID and expose it through the review portal. Resume signals include the decision ID, reviewed artifact versions, and correction version IDs.

## Idempotency

- Workflow start idempotency key: `tenantId + projectId + brdVersion`.
- Agent execution idempotency key: `projectId + workflowState + agentVersion + promptVersion + inputArtifactVersionHash`.
- Jira creation idempotency key: `projectId + jiraPlanningVersion + targetJiraProject`.
- Human decision idempotency key: `reviewId + reviewerId + submittedAtBucket`.

## Workflow Versioning

Workflow definitions must be versioned. Existing workflow instances should either complete on their original workflow version or pass through explicit migration states. Changes that reorder durable workflow commands require engine-specific versioning controls.

## Why Not Pure Database Polling?

Database polling is simpler but weaker for long wait states, retries, and operational visibility. A workflow engine gives durable timers, explicit waits, and retry semantics. The application database still remains the canonical project record.
