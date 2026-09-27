# AegisFlow Control Panel Interaction Specification

## Purpose

This document explains what happens when users click buttons, links, rows, filters, and approval actions in the AegisFlow UI designs.

The rule is simple: navigation clicks load read models; state-changing clicks submit commands. Commands must pass authorization, workflow-state validation, artifact-version validation, idempotency, and audit requirements before changing anything authoritative.

## Global Interaction Rules

| Interaction Type | UI Result | Backend Result | Audit |
|---|---|---|---|
| Menu click | Navigate to screen and load read model | `GET` read endpoint | Optional page-view audit by policy |
| Row/card click | Open detail screen or side panel | `GET` entity detail read model | Optional viewed event for sensitive data |
| Filter click | Update local query state and reload list | `GET` list endpoint with filters | No audit unless export/report |
| Search submit | Open global search results grouped by entity type | `GET /search` | No audit by default |
| State-changing button | Show confirmation/drawer if needed, then submit command | `POST` command endpoint | Required |
| Approval button | Validate exact reviewed artifact versions | `POST /reviews/{reviewId}/decision` | Required |
| External publish button | Requires approved artifact version and idempotency key | Jira publish command | Required |
| Comment submit | Adds anchored message | `POST /projects/{projectId}/messages` | Required if decision-related |
| Export button | Opens export options, then produces evidence package | `POST /audit/exports` | Required |

## Navigation Menu

| Menu Item | Click Behavior |
|---|---|
| Dashboard | Opens Portfolio Control Panel and reloads `/dashboard`. |
| Projects | Opens project table and reloads `/projects` with remembered filters. |
| Review Queue | Opens role-scoped review inbox from `/reviews/my-queue`. |
| Project Rooms | Opens recent/active project rooms. Selecting a room loads `/projects/{projectId}/room`. |
| Jira Drafts | Opens Jira drafts requiring PM attention. Selecting a draft loads PM Approval workspace. |
| Agent Runs | Opens agent execution list from `/agent-runs`. |
| Knowledge | Opens knowledge source catalog and retrieval policy view. |
| Audit | Opens audit timeline and integrity controls. |

## App Screen 01: Portfolio Control Panel

### Submit BRD

Click behavior:

1. Opens `Submit BRD` drawer or modal.
2. User enters project name, business owner, domain, target business unit, and uploads BRD.
3. UI performs client-side checks for required fields and supported file type.
4. On submit, backend creates or updates project and stores a new BRD artifact version.
5. Workflow is started or signaled.
6. UI redirects to Project Command Room.

Backend command:

```text
POST /projects
POST /projects/{projectId}/brd-versions
```

State effect:

```text
New project -> BRD_SUBMITTED -> BRD_ANALYSIS
Existing project with revised BRD -> downstream artifacts marked stale
```

UI feedback:

- Loading state while upload is processed.
- Success toast with project ID.
- Error state for invalid file, missing metadata, duplicate submission, or permission denial.

Audit:

- `BRD_SUBMITTED`
- `BRD_VERSION_CREATED`
- `WORKFLOW_STARTED` or `WORKFLOW_SIGNALED`

### Review Queue Button

Click behavior:

- Navigates to `App Screen 06 - Review Queue`.
- Applies `assignedToMe=true` filter.

Backend read:

```text
GET /reviews/my-queue
```

### Dashboard Metric Cards

Click behavior:

| Card | Destination |
|---|---|
| Active Projects | Projects screen with active-state filter |
| Technical Reviews | Review Queue filtered to `TECHNICAL_REVIEW` |
| PM Approvals | Review Queue filtered to `PM_REVIEW` |
| Agent Cost | Agent Runs filtered to current month |

### Lifecycle Pipeline Rows

Click behavior:

- Navigates to Projects screen filtered by selected workflow state.

Example:

```text
Click HUMAN_TECHNICAL_REVIEW -> GET /projects?state=HUMAN_TECHNICAL_REVIEW
```

### Project Room Activity Message

Click behavior:

- Opens Project Command Room and scrolls/focuses to the anchored item.

Anchor examples:

- Artifact version.
- Review decision.
- Clarification question.
- Agent execution.

### Dashboard Interaction State Frames

The Pencil document includes Dashboard-derived UI states for the highest-priority interactions:

| Pencil Frame | Trigger From Dashboard | Resulting UI |
|---|---|---|
| `App Screen 01A - Dashboard Submit BRD Drawer` | `Submit BRD` button | Right-side drawer with project metadata, BRD upload, validation checklist, cancel, and start-analysis action. |
| `App Screen 01B - Dashboard Search Results` | Global search field | Search popover grouped by Projects, Artifacts, Agent Runs, and Reviews. |
| `App Screen 01C - Dashboard Portfolio Drilldown` | Metric card, lifecycle row, or `View all projects` link | Filtered portfolio drawer with chips, project rows, export, and open-projects action. |
| `App Screen 01D - Dashboard Review Decision Drawer` | Review queue item | Technical review drawer with artifact-version summary, reviewer comment, and decision buttons. |
| `App Screen 01E - Dashboard Communication Thread` | Communication activity message | Anchored project-room thread with state tags, reply composer, attachment action, and send reply. |

## App Screen 05: Projects

### Submit BRD

Same behavior as Dashboard `Submit BRD`.

### Filter Pills

Click behavior:

- Toggle one filter.
- Reload table with filter query.
- Preserve filters in URL/query state.

Backend read:

```text
GET /projects?state=&risk=&owner=&stale=
```

### Project Row

Click behavior:

- Opens Project Command Room for selected project.

Backend read:

```text
GET /projects/{projectId}/room
```

UI feedback:

- If project has stale artifacts, show warning banner in Project Command Room.
- If user lacks permission, show restricted state with request-access action.

### Portfolio Insight Card

Click behavior:

| Card | Destination |
|---|---|
| Review bottleneck | Review Queue filtered by oldest pending reviews |
| Stale artifacts | Projects filtered by `hasStaleArtifacts=true` |
| Jira ready | Jira Drafts filtered by `readyForPmApproval=true` |
| Knowledge gaps | Agent Runs filtered by `sourceAuthority=SUPPORTING_ONLY` |

## App Screen 06: Review Queue

### Review Queue Card

Click behavior:

1. Selects the review.
2. Loads review detail panel.
3. Locks reviewed artifact versions in the UI.

Backend read:

```text
GET /reviews/{reviewId}
```

Validation:

- If review is stale, decision buttons are disabled.
- If user role does not match gate role, decision buttons are hidden or disabled.

### Approve

Click behavior:

1. Opens lightweight confirmation.
2. Shows exact artifact versions being approved.
3. Submits review decision.
4. Workflow resumes from the waiting state.

Backend command:

```text
POST /reviews/{reviewId}/decision
```

Payload:

```json
{
  "decision": "APPROVE",
  "reviewedArtifactVersions": {
    "requirementAnalysis": 2,
    "architecture": 4,
    "systemAnalysis": 3
  }
}
```

State effect:

```text
HUMAN_TECHNICAL_REVIEW -> ESTIMATION
```

Audit:

- `HUMAN_REVIEW_SUBMITTED`
- `WORKFLOW_RESUMED`

### Approve With Change

Click behavior:

1. Opens correction editor.
2. User edits structured correction fields.
3. UI previews which artifact version will be superseded.
4. Submit creates corrected artifact version.
5. Workflow resumes using corrected versions.

Backend command:

```text
POST /reviews/{reviewId}/decision
```

Payload:

```json
{
  "decision": "APPROVE_WITH_CHANGE",
  "corrections": [
    {
      "artifactType": "ARCHITECTURE_ANALYSIS",
      "field": "recommendation",
      "oldValueRef": "architecture-v4",
      "newValue": "Reuse Payment Orchestration Service as primary option."
    }
  ]
}
```

State effect:

```text
Create corrected artifact version -> HUMAN_TECHNICAL_REVIEW -> ESTIMATION
```

Audit:

- `HUMAN_CORRECTION_CREATED`
- `ARTIFACT_VERSION_CREATED`
- `HUMAN_REVIEW_SUBMITTED`
- `WORKFLOW_RESUMED`

### Request Clarification

Click behavior:

1. Opens clarification form.
2. User chooses target role, question, blocking flag, and related artifact/finding.
3. Review remains open or transitions to clarification state according to policy.

State effect:

```text
HUMAN_TECHNICAL_REVIEW -> REQUIREMENT_ANALYSIS
```

or:

```text
HUMAN_TECHNICAL_REVIEW -> BRD_SUBMITTED
```

Audit:

- `CLARIFICATION_REQUESTED`
- `WORKFLOW_RETURNED_TO_PREVIOUS_STATE`

### Reject

Click behavior:

1. Opens rejection confirmation.
2. Requires structured rejection reason.
3. User may optionally add correction guidance.
4. Workflow returns to policy-defined state or project is terminated.

Common reasons:

- `BRD_INCOMPLETE`
- `ARCHITECTURE_UNACCEPTABLE`
- `EXISTING_CAPABILITY_MISSED`
- `SECURITY_RISK`
- `OTHER`

Audit:

- `HUMAN_REVIEW_REJECTED`
- `WORKFLOW_RETURNED_TO_PREVIOUS_STATE`

## App Screen 02: Project Command Room

### Artifact Ledger Row

Click behavior:

- Opens artifact detail drawer.
- Shows payload summary, version history, dependencies, producer, schema validation, and hash.

Backend read:

```text
GET /projects/{projectId}/artifacts/{artifactType}/versions/{version}
```

Links inside drawer:

- `Compare versions` opens artifact compare view.
- `View producer` opens Agent Execution Detail or Human Correction record.
- `View dependencies` shows upstream/downstream artifact lineage.

### Decision Buttons

Same command behavior as Review Queue decision buttons, but scoped to the current Project Command Room pending review.

Additional UI behavior:

- Decision banner disappears after success.
- Workflow panel advances or returns to previous state.
- Communication panel receives system event.

### Review Summary Card

Click behavior:

- Opens focused detail for requirement issues, architecture recommendation, or system analysis.
- Highlights source citations and related messages.

### Agent Evidence Box

Click behavior:

- Opens Agent Execution Detail for the related execution.

### Project Room Composer

Click behavior:

1. User writes message.
2. User optionally selects anchor: project, artifact, finding, question, review, Jira draft item, or agent execution.
3. Submit stores message.
4. Message appears in room activity.

Backend command:

```text
POST /projects/{projectId}/messages
```

Important:

- A comment does not change workflow state.
- A clarification answer must use an explicit `clarificationAnswer` command if it should unblock workflow.

## App Screen 03: PM Approval And Jira Draft

### Approve And Create Jira

Click behavior:

1. Opens final confirmation.
2. Displays target Jira project, issue count, issue types, and reviewed artifact versions.
3. Submits PM approval.
4. Workflow resumes.
5. Jira Publish Service creates issues idempotently.
6. UI shows publish progress.
7. On success, ticket keys appear in the Jira Draft screen and Project Command Room.

Backend command:

```text
POST /jira-drafts/{projectId}/approval
```

State effect:

```text
HUMAN_PM_APPROVAL -> JIRA_CREATED
```

Audit:

- `HUMAN_REVIEW_SUBMITTED`
- `JIRA_PUBLISH_REQUESTED`
- `JIRA_ISSUES_CREATED`

Failure behavior:

| Failure | UI Behavior |
|---|---|
| Jira API unavailable | Show waiting/retry state, keep approval recorded |
| Duplicate publish | Show existing Jira keys from idempotent result |
| Permission denied | Show policy error and escalation action |
| Partial create | Show created keys, failed issues, and retry action |

### Approve With Change

Click behavior:

1. Opens structured Jira draft editor.
2. PM edits story titles, task split, points, labels, or target component.
3. UI validates required Jira fields.
4. Submit creates corrected Jira Planning artifact version.
5. Workflow proceeds to Jira creation if policy allows immediate publish after change.

State effect:

```text
Create Jira Planning vN+1 -> HUMAN_PM_APPROVAL -> JIRA_CREATED
```

### Reject Plan

Click behavior:

1. Opens rejection reason modal.
2. PM selects whether rejection applies to estimation or Jira draft.
3. Workflow returns to selected state.

State effect:

```text
HUMAN_PM_APPROVAL -> ESTIMATION
```

or:

```text
HUMAN_PM_APPROVAL -> JIRA_DRAFT
```

### Jira Draft Story Row

Click behavior:

- Opens story detail drawer.
- Shows acceptance criteria, generated tasks, dependencies, labels, component, and Jira field mapping.

## App Screen 07: Agent Runs

### Filter Runs

Click behavior:

- Opens filter panel for agent name, workflow state, status, prompt version, model, cost range, date range, and human outcome.

Backend read:

```text
GET /agent-runs?agent=&state=&status=&promptVersion=&humanOutcome=
```

### Agent Run Row

Click behavior:

- Opens Agent Execution Detail.

Backend read:

```text
GET /agent-runs/{executionId}
```

### KPI Card

Click behavior:

| KPI | Filter Applied |
|---|---|
| Success rate | Current month successful executions |
| Invalid output | Failed schema validation executions |
| Avg latency | Slowest executions |
| Month cost | Executions sorted by cost |

## App Screen 04: Agent Execution Detail

### Source Row

Click behavior:

- Opens source detail drawer.
- Shows source authority, freshness, retrieval timestamp, excerpt, owning system, and access decision.

### Output Artifact Link

Click behavior:

- Opens artifact detail drawer for the generated version.

### Prompt Version Link

Click behavior:

- Opens prompt metadata: prompt version, agent version, schema version, deployment date, and evaluation status.

### Human Correction Dataset Notice

Click behavior:

- Opens evaluation record if corrections exist.
- If no correction exists yet, explains how future human changes become evaluation data.

## App Screen 08: Knowledge

### Manage Sources

Click behavior:

- Opens source administration panel.
- Admin can configure source enabled state, authority level, ingestion frequency, owner, and allowed agents.

Backend commands:

```text
POST /knowledge/sources
PATCH /knowledge/sources/{sourceId}
POST /knowledge/sources/{sourceId}/reindex
```

Permission:

- Platform admin only.

### Knowledge Source Row

Click behavior:

- Opens source detail.
- Shows freshness, document count, failed ingestions, last indexed time, retrieval usage, and agent citations.

### Retrieval Policy Card

Click behavior:

- Opens policy detail view for the selected agent.
- Shows required sources, allowed tools, citation requirements, and fallback behavior.

## App Screen 09: Audit

### Export

Click behavior:

1. Opens export configuration modal.
2. User selects project, date range, event types, and evidence package format.
3. Backend creates export and records export event.
4. UI shows download link when ready.

Backend command:

```text
POST /audit/exports
```

Audit:

- `AUDIT_EXPORT_CREATED`

### Audit Timeline Event

Click behavior:

- Opens audit event detail drawer.
- Shows actor, action, entity, before/after hashes, request ID, trace ID, timestamp, and related records.

### Integrity Control Card

Click behavior:

- Opens documentation or policy detail for that control.

## App Screen 10: Interaction Behavior Map

This screen is a design aid, not an end-user product screen. It summarizes how clicks behave across the application.

Click behavior:

- In implementation, each card can link to the detailed specification section or screen prototype.
- It should not appear in the production navigation.

## Confirmation And Error Patterns

### Confirmation Required

Require confirmation for:

- Approving technical review.
- Approving with change.
- Rejecting technical review.
- Approving and creating Jira.
- Rejecting PM plan.
- Exporting audit evidence.
- Reindexing knowledge sources.

### Inline Validation

Show inline validation for:

- Missing BRD metadata.
- Unsupported BRD file type.
- Empty correction reason.
- Missing rejection reason.
- Jira required field missing.
- Stale reviewed artifact version.

### Permission Denied

If permission is denied:

1. Keep user on current screen.
2. Disable unavailable controls.
3. Show reason and required role.
4. Offer request-access flow if supported.

### Optimistic Updates

Avoid optimistic updates for governance actions. Approval, rejection, Jira creation, and artifact correction should update only after the backend confirms the command was accepted.

Messages may appear optimistically if clearly marked as sending and retried safely.

## State-Changing Interaction Checklist

Every implementation of a state-changing click must define:

- Command endpoint.
- Required role.
- Required workflow state.
- Required artifact versions.
- Idempotency key.
- Success state.
- Failure state.
- Audit event.
- UI loading state.
- UI success feedback.
- UI error feedback.

## Design Update

The Pencil document includes `App Screen 10 - Interaction Behavior Map`, which visually summarizes these click behaviors for design review.
