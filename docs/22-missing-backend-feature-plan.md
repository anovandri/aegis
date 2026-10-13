# Missing Backend Feature Development Plan

## Purpose

This document turns the current architecture docs and `design/aegis.pen` screens into a backend implementation plan for features that are required by the product direction but are not fully implemented yet.

It should be used as a reference before starting the next development phase.

## Reviewed Inputs

- Architecture docs in `docs/01-vision.md` through `docs/21-knowledge-docker-integration-tests.md`
- ADRs in `docs/adr/`
- UX and data-flow docs in `design/`
- Current `design/aegis.pen` screens, including:
  - Login
  - Portfolio Dashboard
  - Projects
  - Project Command Room
  - PM Approval and Jira Draft
  - Agent Runs
  - Agent Execution Detail
  - Review Queue and review action drawers
  - Knowledge screens
  - Audit
  - Settings
  - User Management and RBAC
  - Integration Credentials and Adapters
  - Platform Health and Workflow Operations

## Current Backend Baseline

The backend already has these foundations:

| Area | Current status |
|---|---|
| Project creation and BRD upload | Implemented via multipart BRD submission |
| BRD artifact versioning | Implemented with object storage abstraction and PostgreSQL metadata |
| MinIO-compatible document storage | Implemented |
| PostgreSQL persistence | Implemented |
| Temporal lifecycle | Implemented for the main MVP state flow |
| BRD Intake Agent | Implemented with local/OpenAI variants |
| Requirement Analysis Agent | Implemented with local/OpenAI variants and knowledge evidence |
| Knowledge document management | Implemented |
| Knowledge source registry and sync | Implemented with GitLab and Google Drive adapter patterns |
| pgvector-style knowledge indexing | Implemented for knowledge chunks |
| Tree-sitter Go source parsing | Implemented for source knowledge enrichment |
| Human review queue | Implemented |
| Review comments and attachments | Implemented |
| Temporal review decision signals | Implemented |
| Local user management | Implemented |
| Spring Security RBAC | Implemented |

The remaining work should build on these foundations instead of replacing them.

## Main Gaps

### 1. UI Read Models For Dashboard, Projects, Project Rooms, Agent Runs, Jira Drafts, Audit, And Operations

The UI designs are read-model driven, but the backend currently exposes mostly domain-oriented endpoints.

Missing backend read APIs:

```text
GET /api/dashboard
GET /api/projects
GET /api/projects/{projectId}/room
GET /api/reviews/my-queue
GET /api/jira-drafts
GET /api/jira-drafts/{projectId}
GET /api/agent-runs
GET /api/agent-runs/{executionId}
GET /api/audit/events
GET /api/operations/health
GET /api/operations/workflows
```

Recommended approach:

- Create purpose-built read model services.
- Keep them separate from workflow activities and command services.
- Read from PostgreSQL as the canonical state source.
- Include permission filtering in each read model.

Priority: High.

Why: the frontend cannot cleanly implement the Pencil screens without stable aggregate read models.

Initial implementation status:

- Added `GET /api/dashboard`.
- Added `GET /api/projects`.
- Added `GET /api/projects/{projectId}/room`.
- Added `GET /api/reviews/my-queue`.
- Added `GET /api/jira-drafts`.
- Added `GET /api/jira-drafts/{projectId}`.
- Added `GET /api/agent-runs`.
- Added `GET /api/agent-runs/{executionId}`.
- Added `GET /api/audit/events` as a synthesized audit timeline from existing project, artifact, agent, and review tables.
- Added `GET /api/operations/health`.
- Added `GET /api/operations/workflows`.

Remaining hardening for this item:

- Replace synthesized audit events with the dedicated append-only audit table from item #5.
- Add project membership and tenant-aware filtering.
- Add richer staleness, risk, SLA, and cost rollups after the supporting domain tables exist.

### 2. Project List And Project Command Room

Current backend supports project creation, project lookup, context lookup, and workflow progress lookup. It does not yet support the full project list and command-room read model shown in the UI.

Required capabilities:

- Search and filter projects by state, owner, domain, risk, stale artifact status, and review status.
- Return project lifecycle timeline.
- Return artifact ledger with versions and staleness.
- Return pending review summary.
- Return latest agent outputs.
- Return knowledge citations used by the project.
- Return Jira draft/publish status.
- Return anchored comments and decision history.

Recommended endpoints:

```text
GET /api/projects?state=&owner=&domain=&risk=&stale=
GET /api/projects/{projectId}/room
GET /api/projects/{projectId}/artifacts
GET /api/projects/{projectId}/activity
POST /api/projects/{projectId}/messages
```

Additional persistence needed:

- `project_messages`
- `project_activity_events`
- artifact staleness flags or derived staleness read model
- optional `project_memberships` for project-level authorization

Priority: High.

### 3. Login With Enterprise Identity Providers

The new login screen includes:

- Local MVP login.
- Google Workspace login.
- GitHub Enterprise login.
- Enterprise SSO option.

Current backend supports local Spring Security users only.

Required capabilities:

- Add OAuth2/OIDC client support.
- Support Google Workspace OIDC.
- Support GitHub or GitHub Enterprise OAuth login.
- Prepare generic OIDC provider configuration for Okta, Entra ID, Keycloak, or enterprise SSO.
- Resolve external identity into internal AegisFlow user and roles.
- Enforce domain/org/team allowlists.
- Audit login success, login failure, and role resolution.

Recommended endpoint shape:

```text
GET  /oauth2/authorization/google
GET  /oauth2/authorization/github
GET  /oauth2/authorization/enterprise
GET  /api/auth/me
POST /api/auth/logout
```

Recommended model additions:

- `external_identities`
- `identity_provider_configs`
- `role_mapping_rules`
- `auth_audit_events`

Important rule:

External identity proves who the user is. AegisFlow roles still decide whether the user can approve reviews, publish Jira tickets, attach evidence, or administer the platform.

Priority: High, after local RBAC stabilizes.

### 4. User Management And RBAC Hardening

The Settings and User Management screens need richer backend support than the current local user APIs.

Missing capabilities:

- Role catalog endpoint.
- Role-to-permission matrix endpoint.
- User detail endpoint.
- Password status metadata.
- Login status and last login timestamp.
- Disable reason.
- Force password reset flag.
- Project or tenant scoped roles.
- Audit events for all admin actions.

Recommended endpoints:

```text
GET   /api/auth/roles
GET   /api/auth/permissions
GET   /api/auth/users/{username}
PATCH /api/auth/users/{username}/roles
PATCH /api/auth/users/{username}/enabled
PUT   /api/auth/users/{username}/password
```

Recommended model additions:

- `roles`
- `permissions`
- `role_permissions`
- `user_login_events`
- `user_admin_events`
- optional `tenant_user_roles`
- optional `project_user_roles`

Priority: High.

### 5. Audit Log As A First-Class Backend Feature

The docs require append-only audit records, but current audit capability is partial and scattered through domain tables.

Required capabilities:

- Central audit table.
- Append-only write path.
- Actor type: human, agent, workflow, system, integration.
- Correlation IDs.
- Before/after metadata hashes.
- External system IDs.
- Authorization outcome.
- Export endpoint for evidence packages.

Recommended endpoints:

```text
GET  /api/audit/events?projectId=&actor=&action=&from=&to=
GET  /api/audit/events/{eventId}
POST /api/audit/exports
GET  /api/audit/exports/{exportId}
```

Recommended model additions:

- `audit_events`
- `audit_exports`
- `audit_event_links`

Priority: High.

Why: auditability is one of the top architecture characteristics and is required for enterprise governance.

### 6. Agent Runs And Agent Execution Detail

Agent execution records exist, but the UI needs richer list/detail APIs.

Missing capabilities:

- Agent run listing with filters.
- Agent execution detail payload.
- Prompt version display.
- Input artifact versions.
- Output artifact versions.
- Knowledge citations.
- Tool calls.
- token/cost detail.
- retry/failure detail.
- human correction linkage.

Recommended endpoints:

```text
GET /api/agent-runs?projectId=&agentName=&status=&workflowState=
GET /api/agent-runs/{executionId}
GET /api/agent-runs/{executionId}/sources
GET /api/agent-runs/{executionId}/tool-calls
```

Recommended model additions:

- extend `agent_executions`
- `agent_execution_inputs`
- `agent_execution_outputs`
- `agent_tool_calls`
- `agent_source_citations`
- `agent_cost_records`

Priority: High.

### 7. Architecture, System Analysis, Estimation, And Jira Planning Agents

The workflow currently creates deterministic placeholder artifacts for Architecture Analysis, System Analysis, Estimation, Jira Planning, and Jira Publish.

Required capabilities:

- Implement Architecture Agent with governed knowledge retrieval.
- Implement System Analyst Agent with structured API/data/error-flow outputs.
- Implement Estimation Agent with advisory complexity and unknowns.
- Implement Jira Planning Agent with Epic/Story/Task hierarchy.
- Validate each agent output with a schema before persistence.
- Persist prompt version, model, citations, confidence, token use, and cost.

Recommended implementation order:

1. Define artifact payload schemas.
2. Implement local deterministic fallback agents.
3. Implement OpenAI-backed agents.
4. Add schema validation.
5. Add tests for invalid output and retry behavior.

Priority: High.

### 8. Jira Draft And Publish Flow

The PM Approval and Jira Draft screen requires backend support for viewing and publishing approved Jira plans.

Current status:

- Jira plan artifact is generated as a placeholder.
- Jira publish activity is a placeholder.

Missing capabilities:

- Jira draft read model.
- Jira project/issue type mapping.
- Jira field mapping policy.
- Draft edit/correction support before approval.
- Idempotent Jira issue creation.
- Store Jira issue keys and links.
- Retry and duplicate prevention.
- Jira failure and partial-publish handling.

Recommended endpoints:

```text
GET  /api/jira-drafts
GET  /api/jira-drafts/{projectId}
PATCH /api/jira-drafts/{projectId}
POST /api/jira-drafts/{projectId}/approval
POST /api/jira-drafts/{projectId}/publish
GET  /api/jira-drafts/{projectId}/publish-result
```

Recommended model additions:

- `jira_drafts`
- `jira_draft_items`
- `jira_field_mappings`
- `jira_publish_results`
- `jira_issue_links`
- `jira_idempotency_keys`

Priority: High.

### 9. Review Queue Read Model Refinement

The ReviewController is functionally strong, but the UI needs a role-scoped queue endpoint and richer item metadata.

Missing capabilities:

- `my queue` endpoint.
- reviewed artifact version locks.
- stale review indicator.
- aging/due-time calculation.
- risk flags.
- decision availability by role.
- review bundle read model.

Recommended endpoints:

```text
GET /api/reviews/my-queue
GET /api/reviews/{reviewId}/bundle
```

Recommended model/read model fields:

- `reviewedArtifactVersions`
- `isStale`
- `decisionActions`
- `riskFlags`
- `waitingDuration`
- `requiredRole`
- `currentAssignee`

Priority: Medium-high.

### 10. Settings, Integration Credentials, And Adapter Operations

Knowledge source adapters exist, but the Settings and Integration screens need a broader integration management backend.

Missing capabilities:

- Integration catalog across Jira, GitLab, Google Drive, MinIO, Temporal, AI provider.
- Connection test endpoint for all integration types.
- Credential reference registration.
- Credential rotation metadata.
- Adapter health and last run status.
- Integration audit events.

Recommended endpoints:

```text
GET  /api/settings/integrations
GET  /api/settings/integrations/{integrationId}
POST /api/settings/integrations
PATCH /api/settings/integrations/{integrationId}
POST /api/settings/integrations/{integrationId}/check
POST /api/settings/integrations/{integrationId}/rotate-credential
```

Recommended model additions:

- `integration_configs`
- `integration_credentials`
- `integration_health_checks`
- `integration_audit_events`

Security requirement:

Never store raw credentials in normal application tables. Store only references to environment variables, vault paths, or secret manager IDs.

Priority: Medium-high.

### 11. Platform Health And Workflow Operations

The Platform Health screen requires operational APIs that are separate from business audit.

Missing capabilities:

- workflow status rollups.
- stuck workflow detection.
- retry queue visibility.
- Temporal workflow reconciliation.
- agent latency metrics.
- integration incident summaries.
- cost and token budget summaries.

Recommended endpoints:

```text
GET  /api/operations/health
GET  /api/operations/workflows
GET  /api/operations/workflows/{workflowId}
POST /api/operations/workflows/{workflowId}/retry
POST /api/operations/reconciliation/run
GET  /api/operations/incidents
GET  /api/operations/costs
```

Recommended model additions:

- `workflow_reconciliation_runs`
- `operational_incidents`
- `agent_latency_metrics`
- `agent_cost_daily_rollups`

Priority: Medium.

### 12. Global Search

The dashboard design includes global search results across projects, artifacts, reviews, and agent runs.

Missing capabilities:

- Unified search endpoint.
- Permission-aware search.
- Ranking by entity type and recency.
- Entity-specific result actions.

Recommended endpoint:

```text
GET /api/search?q=&types=PROJECT,ARTIFACT,REVIEW,AGENT_RUN,KNOWLEDGE
```

Priority: Medium.

### 13. BRD Revision Handling And Staleness

BRD version upload exists, but downstream staleness and workflow impact are not complete.

Missing capabilities:

- Mark downstream artifacts stale when BRD changes.
- Prevent approval of stale reviews.
- Invalidate or supersede pending review bundles.
- Signal Temporal workflow without restarting the entire lifecycle.
- Provide UI-visible stale indicators.

Recommended endpoints/commands:

```text
POST /api/projects/{projectId}/brd-versions
GET  /api/projects/{projectId}/staleness
POST /api/projects/{projectId}/reanalysis
```

Recommended model additions:

- artifact dependency table or derived dependency graph.
- `artifact_staleness`
- `review_artifact_locks`

Priority: Medium-high.

### 14. Knowledge Policy And Retrieval Snapshots

Knowledge ingestion is relatively advanced, but the docs require governed retrieval policies and replayable snapshots.

Missing capabilities:

- Retrieval policy CRUD.
- required source enforcement per agent/workflow state.
- retrieval budget by agent.
- source authority/freshness ranking explanation.
- retrieval snapshot persistence for replay.
- citation acceptance metrics.

Recommended endpoints:

```text
GET  /api/knowledge/policies
POST /api/knowledge/policies
GET  /api/knowledge/retrieval-snapshots/{snapshotId}
GET  /api/knowledge/citations?projectId=&executionId=
```

Recommended model additions:

- `knowledge_retrieval_policies`
- `knowledge_retrieval_snapshots`
- `knowledge_retrieval_snapshot_items`
- `knowledge_citations`

Priority: Medium.

### 15. Evaluation Dataset From Human Corrections

The roadmap requires using human corrections as future evaluation datasets.

Missing capabilities:

- Extract human corrections into evaluation cases.
- Link corrections to agent execution, prompt version, model, and artifact versions.
- Track approval and correction rates.
- Export evaluation dataset.

Recommended endpoints:

```text
GET  /api/evaluations/correction-cases
POST /api/evaluations/datasets
GET  /api/evaluations/metrics
```

Recommended model additions:

- `evaluation_cases`
- `evaluation_datasets`
- `agent_quality_metrics`

Priority: Medium-low for MVP, high for continuous improvement.

## Recommended Implementation Sequence

### Phase 1: UI Read Model Backbone

1. Add dashboard read model.
2. Add project list read model.
3. Add project command-room read model.
4. Add review queue `my queue` and review bundle read models.
5. Add agent run list/detail read models.

Deliverable:

The existing screens can render from stable backend contracts without frontend data stitching.

### Phase 2: Audit And Operational Truth

1. Add central `audit_events` table.
2. Add audit write service.
3. Wire project, review, auth, knowledge, and Jira actions into audit.
4. Add audit query/export APIs.
5. Add operations health and workflow reconciliation APIs.

Deliverable:

AegisFlow has an auditable control plane instead of scattered event history.

### Phase 3: Production Authentication And RBAC

1. Add external identity model.
2. Add Google Workspace OIDC.
3. Add GitHub Enterprise OAuth.
4. Add generic enterprise OIDC provider support.
5. Add role mapping rules.
6. Add login audit.
7. Extend user/RBAC admin APIs.

Deliverable:

The login and user management screens match enterprise production identity expectations.

### Phase 4: Agent Completion

1. Finalize artifact schemas.
2. Implement Architecture Agent.
3. Implement System Analyst Agent.
4. Implement Estimation Agent.
5. Implement Jira Planning Agent.
6. Add schema validation and failure handling.
7. Add retrieval snapshots and citations for agents that use knowledge.

Deliverable:

The workflow produces real governed artifacts, not placeholder JSON.

### Phase 5: Jira Draft And Publish

1. Add Jira draft read/write model.
2. Add Jira mapping configuration.
3. Add Jira client adapter.
4. Add idempotent publish.
5. Add publish result persistence.
6. Wire PM approval to publish.

Deliverable:

The lifecycle can complete through `JIRA_CREATED` with real Jira keys.

### Phase 6: Staleness And Reanalysis

1. Add artifact dependency tracking.
2. Add stale artifact detection.
3. Disable stale review decisions.
4. Signal workflow for BRD revisions.
5. Add reanalysis command.

Deliverable:

BRD changes no longer create hidden inconsistencies.

### Phase 7: Knowledge Governance Maturity

1. Add retrieval policy management.
2. Add source authority enforcement.
3. Add retrieval snapshot replay.
4. Add citation usage metrics.
5. Add source freshness and quarantine behavior.

Deliverable:

Knowledge becomes governed evidence, not generic document search.

## Suggested Package Structure

Keep the modular monolith, but separate feature modules clearly:

```text
com.aegisflow.api
  application
    dashboard
    projectroom
    jira
    audit
    operations
    identity
    evaluation
  domain
    audit
    identity
    jira
    operations
    evaluation
  infrastructure
    audit
    identity
    jira
    operations
  web
    DashboardController
    ProjectRoomController
    AgentRunController
    JiraDraftController
    AuditController
    OperationsController
    SettingsController
```

Avoid turning this into microservices until the read models, audit model, and Jira workflow are stable.

## Testing Strategy

Each feature should include:

- Controller unit tests for RBAC and validation.
- Service tests for domain behavior.
- Repository tests for PostgreSQL persistence.
- Docker integration tests for PostgreSQL, pgvector, MinIO, Temporal, and adapter simulators where relevant.
- Temporal workflow tests for pause/resume/retry paths.
- Security tests for denied actions and spoofed actor prevention.

Critical Docker integration scenarios:

1. Login and user role mapping persists to PostgreSQL.
2. Project creation writes BRD to MinIO and metadata to PostgreSQL.
3. Workflow reaches technical review and pauses.
4. Review decision signals Temporal and resumes workflow.
5. PM approval publishes Jira using an idempotency key.
6. Audit log records every state-changing command.
7. Knowledge sync stores raw source version and indexed chunks.

## Non-Goals For The Next Backend Phase

Do not implement these yet unless explicitly prioritized:

- Development Agent.
- QA Agent.
- CI/CD automation.
- UAT automation.
- autonomous agent-to-agent delegation.
- Kafka-based event architecture.
- microservice extraction.

These are still roadmap items, but the backend should first complete the governed BRD-to-Jira control plane.

## Immediate Next Sprint Recommendation

Start with:

1. `GET /api/dashboard`
2. `GET /api/projects`
3. `GET /api/projects/{projectId}/room`
4. `GET /api/reviews/my-queue`
5. central `audit_events`
6. `GET /api/agent-runs`
7. `GET /api/agent-runs/{executionId}`

Reason:

These unlock the biggest portion of the existing UI designs without forcing Jira, external identity, or the remaining agents to be complete first.

