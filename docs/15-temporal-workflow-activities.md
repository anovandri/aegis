# Temporal Workflow Activities

## Purpose

This document defines the Temporal workflow and activity boundary for the AegisFlow MVP lifecycle.

Temporal owns durable execution, retries, worker dispatch, and long-running workflow state. AegisFlow domain services still own authoritative project, artifact, review, and audit records.

## Workflow

```text
ProjectLifecycleWorkflow
```

Workflow ID format:

```text
project-lifecycle-{projectId}
```

Task queue:

```text
aegisflow-project-lifecycle
```

## Trigger

The workflow starts after a successful BRD upload:

```text
POST /api/projects
  -> store BRD ArtifactVersion v1
  -> start ProjectLifecycleWorkflow
  -> run BRD Intake activity
```

## Activity List

| Order | Activity | Workflow State | MVP Status | Purpose |
|---:|---|---|---|---|
| 1 | `runBrdIntake` | `BRD_ANALYSIS` | Implemented | Parse the submitted BRD artifact and prepare structured BRD analysis. |
| 2 | `runRequirementAnalysis` | `REQUIREMENT_ANALYSIS` | Implemented | Validate completeness, ambiguity, contradictions, questions, acceptance criteria, and edge cases. |
| 3 | `runArchitectureAnalysis` | `ARCHITECTURE_ANALYSIS` | Defined only | Investigate existing services/APIs/databases/events before proposing architecture. |
| 4 | `runSystemAnalysis` | `SYSTEM_ANALYSIS` | Defined only | Produce API flows, data flows, contracts, error handling, and integration scenarios. |
| 5 | `createTechnicalReview` | `HUMAN_TECHNICAL_REVIEW` | Defined only | Create the technical review gate and pause the workflow until human decision. |
| 6 | `runEstimation` | `ESTIMATION` | Defined only | Decompose scope and produce advisory implementation estimate. |
| 7 | `runJiraPlanning` | `JIRA_DRAFT` | Defined only | Generate Epic, Story, Task draft hierarchy. |
| 8 | `createPmReview` | `HUMAN_PM_APPROVAL` | Defined only | Create PM approval gate and pause workflow until human decision. |
| 9 | `publishJira` | `JIRA_CREATED` | Defined only | Create approved Jira issues idempotently. |

## Retry Policy

Activities are intentionally invoked through separate Temporal activity stubs so each step can retry independently.

| Activity | Timeout | Attempts | Retry Backoff | Do Not Retry |
|---|---:|---:|---|---|
| `runBrdIntake` | 3 minutes | 3 | 2s initial, 30s max, coefficient 2.0 | `IllegalArgumentException`, `UnsupportedOperationException` |
| `runRequirementAnalysis` | 5 minutes | 4 | 5s initial, 1m max, coefficient 2.0 | `IllegalArgumentException`, `UnsupportedOperationException` |

Rationale:

- BRD Intake should fail fast for unsupported document formats or invalid project input.
- Requirement Analysis gets one extra attempt because LLM/schema/network failures are more likely to be transient.
- Later activities must define their own retry policies before implementation.

## First Implemented Activity

`runBrdIntake` currently performs the first real BRD analysis slice:

1. Transitions the project to `BRD_ANALYSIS`.
2. Verifies a BRD artifact exists.
3. Retrieves the BRD bytes from `DocumentStoragePort`.
4. Extracts text for supported text, markdown, and JSON documents.
5. Invokes the configured BRD Intake Agent.
6. Persists the structured result as a `BRD_ANALYSIS` artifact version.
7. Records an in-memory `AgentExecution`.
8. Transitions the project to `REQUIREMENT_ANALYSIS`.

The default local agent is deterministic and useful for tests. A configurable OpenAI-backed adapter is available through `aegisflow.llm.provider=openai`.

Unsupported binary files, such as PDF and DOCX, intentionally fail until a real document extraction layer is added.

## Requirement Analysis Knowledge

Requirement Analysis now uses a minimal governed local knowledge store.

Phase 3 storage:

```text
MinIO or in-memory object storage
  -> versioned knowledge file bytes
PostgreSQL or local H2 database
  -> source, authority, allowed agents, workflow states, tags, versions, chunks, embeddings
Flyway migration
  -> knowledge_documents, knowledge_document_versions, knowledge_chunks
PostgreSQL pgvector migration
  -> embedding_vector vector(64), HNSW cosine index
Seed file
  -> app/apps/api/src/main/resources/knowledge/requirement-analysis-seed.json
```

The seed file is loaded into the same database-backed knowledge service used by uploaded knowledge documents. This keeps the runtime retrieval path consistent while making metadata durable across application restarts when PostgreSQL is used.

Knowledge documents store:

- `sourceId`
- `sourceTitle`
- `sourceType`
- `authority`
- `allowedAgents`
- `workflowStates`
- `tags`
- immutable file versions with storage bucket, object key, storage version id, content hash, and extracted text

The workflow activity retrieves citations through `KnowledgeSearchPort` before invoking the Requirement Analyst Agent. The agent receives only allowed citations for:

```text
agent = Requirement Analyst Agent
workflowState = REQUIREMENT_ANALYSIS
```

This is now the first Requirement Analysis RAG slice. It is still governed retrieval, not open-ended chat with documents:

- only latest-version chunks are searched;
- chunks are filtered by allowed agent and workflow state;
- citations include source identity, chunk/version context, and embedding model;
- the resulting `REQUIREMENT_ANALYSIS` artifact persists `sourceRefs` so reviewers can see which knowledge influenced the output.

## Knowledge Management API

Phase 3 keeps the manual knowledge management API for the frontend:

```text
POST /api/knowledge/documents
GET  /api/knowledge/documents
GET  /api/knowledge/documents/{documentId}
POST /api/knowledge/documents/{documentId}/versions
GET  /api/knowledge/documents/{documentId}/versions
```

The create endpoint accepts multipart form data:

```text
sourceTitle      required
sourceId         optional; defaults to document id
sourceType       required, for example PREVIOUS_PROJECT or REQUIREMENT_STANDARD
authority        required, for example AUTHORITATIVE or SUPPORTING
allowedAgents    required CSV, for example Requirement Analyst Agent
workflowStates   required CSV, for example REQUIREMENT_ANALYSIS
tags             optional CSV
file             required
```

Only text, markdown, and JSON files are supported in this phase. PDF and DOCX extraction should be added through a dedicated extraction activity later so unsupported files fail explicitly instead of producing weak citations.

The default embedding provider is local and deterministic so tests and local development do not depend on an external embedding API. PostgreSQL deployments use the `pgvector/pgvector:pg17` Docker image and receive a pgvector column plus HNSW cosine index. The next knowledge phase should add external embedding provider support, retrieval snapshots, source freshness scoring, and persisted citation records tied to `agent_executions`.

## Monitoring API

Users can monitor workflow progress through:

```text
GET /api/projects/{projectId}/workflow
```

The response contains:

- `projectId`
- `workflowId`
- `runId`
- `currentState`
- `status`
- `completedSteps`
- `pendingSteps`

## Local Temporal

Start local Temporal:

```bash
cd app
docker compose up -d temporal
```

Temporal endpoints:

- gRPC: `localhost:7233`
- UI: `http://localhost:8233`

Run the API with Temporal enabled:

```bash
cd app
AEGISFLOW_TEMPORAL_ENABLED=true npm run api:serve
```

## Current Limitation

After `runRequirementAnalysis`, the workflow intentionally parks at `ARCHITECTURE_ANALYSIS` with status:

```text
WAITING_FOR_NEXT_ACTIVITY_IMPLEMENTATION
```

This keeps the workflow queryable while later activities are added incrementally.
