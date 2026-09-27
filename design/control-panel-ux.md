# AegisFlow Control Panel UX

## Product Position

The AegisFlow dashboard is the operating room for AI-assisted SDLC governance. It must show what is true, what is pending, who owns the next decision, and why the platform is recommending a path.

The UI should not present agents as autonomous teammates chatting freely. Agents appear as auditable producers of artifacts, findings, questions, recommendations, and drafts.

## Primary Users

| Role | Primary Needs |
|---|---|
| Business Team | Submit BRD, answer clarification questions, track status |
| Digital Architect | Review architecture recommendations, confirm reuse, approve technical package |
| System Analyst | Validate API/data/error flows, correct scenarios and contracts |
| Project Manager | Review estimation, shape Jira hierarchy, approve ticket creation |
| Platform/Admin | Monitor workflow health, cost, audit trail, integration status |

## UX Principles

1. Show lifecycle truth before AI opinion.
2. Anchor every discussion to a project, artifact, state, or decision.
3. Make pending human action impossible to miss.
4. Preserve artifact version history visibly.
5. Separate generated recommendations from approved decisions.
6. Make source evidence and confidence available without overwhelming the review flow.
7. Let stakeholders communicate in context instead of copying notes into chat/email.

## Navigation Model

```text
AegisFlow
  Dashboard
  Projects
  Review Queue
  Project Rooms
  Knowledge
  Agent Runs
  Jira Drafts
  Audit
  Settings
```

## Main Surfaces

### 1. Portfolio Dashboard

Purpose: single control panel for all active initiatives.

Key elements:

- Pipeline counts by workflow state.
- Review bottlenecks.
- SLA/aging indicators.
- Projects blocked by clarification.
- Agent cost and latency summary.
- Jira creation readiness.
- High-risk initiatives.

Primary actions:

- Open project room.
- Open my review queue.
- Submit new BRD.
- View blocked items.

### 2. Review Queue

Purpose: role-specific action inbox.

Filters:

- Technical Review.
- PM Review.
- Clarification requested.
- Stale review.
- High-risk findings.
- Due date.

Each queue item should show:

- Project name.
- Current state.
- Gate type.
- Waiting time.
- Reviewed artifact versions.
- Required reviewer role.
- Risk flags.

### 3. Project Command Room

Purpose: canonical single source of truth for one initiative.

Recommended layout:

```text
Left rail: Project context and artifact ledger
Center: Workflow timeline, current state, review bundle
Right rail: Contextual communication and decisions
Bottom/secondary: Agent runs, sources, audit trail
```

Key widgets:

- Current lifecycle state.
- Workflow timeline.
- Artifact versions.
- Pending human decision.
- Open questions.
- Risks and assumptions.
- Agent outputs.
- Source citations.
- Decision log.
- Stakeholder discussion.
- Jira draft preview.

### 4. Technical Review Workspace

Purpose: focused approval surface for architecture and system analysis.

Sections:

- Requirement issues.
- Architecture recommendation.
- Existing capability investigation checklist.
- Impacted systems.
- API/event/data flows.
- Risks.
- Source citations.
- Human correction editor.

Actions:

- Approve.
- Approve with change.
- Request clarification.
- Reject.

### 5. PM Approval Workspace

Purpose: review estimation and Jira draft before ticket creation.

Sections:

- Scope decomposition.
- Complexity drivers.
- Unknowns affecting estimation.
- Discipline estimates.
- Epic/story/task preview.
- Jira field mapping.
- Publishing target.

Actions:

- Approve.
- Approve with change.
- Reject.

### 6. Communication Channel

Communication should be contextual, not a detached global chat.

Thread anchors:

- Project.
- Artifact version.
- Finding.
- Clarification question.
- Human review.
- Jira draft item.
- Agent execution.

Message types:

- Comment.
- Clarification answer.
- Decision note.
- Mention.
- Correction request.
- System event.

Important rule: comments can inform humans, but comments alone do not change workflow state.

## Project Room Information Architecture

```text
Project Header
  Name, ID, BRD version, owner, current state, risk flags

Workflow Panel
  State timeline, active state, owner, waiting duration, next allowed transitions

Artifact Ledger
  BRD
  BRD Intake
  Requirement Analysis
  Architecture Analysis
  System Analysis
  Estimation
  Jira Planning

Review Panel
  Pending gate
  Decision buttons
  Correction editor
  Reviewed versions

Communication Panel
  Decision thread
  Clarification thread
  Mentions
  System events

Evidence Panel
  Agent runs
  Source citations
  Tool calls
  Confidence and validation status
```

## Key States In UI

| State | UI Emphasis |
|---|---|
| `BRD_SUBMITTED` | BRD accepted, waiting for intake |
| `BRD_ANALYSIS` | Extraction in progress |
| `REQUIREMENT_ANALYSIS` | Completeness and clarification checks |
| `ARCHITECTURE_ANALYSIS` | Reuse and architecture recommendation |
| `SYSTEM_ANALYSIS` | Contracts, flows, dependencies |
| `HUMAN_TECHNICAL_REVIEW` | Technical decision required |
| `ESTIMATION` | Advisory estimate generation |
| `JIRA_DRAFT` | Draft ticket hierarchy |
| `HUMAN_PM_APPROVAL` | PM approval required |
| `JIRA_CREATED` | Jira issue keys published |

## Recommended Dashboard Widgets

- Active Projects by State.
- My Pending Reviews.
- Blocked by Clarification.
- High-Risk Findings.
- Aging Reviews.
- Agent Reliability.
- Estimated Jira Readiness.
- Recent Decisions.
- Knowledge Source Health.
- Integration Status.

## Visual Direction

Use a restrained enterprise palette:

- Background: `#f7f8fa`
- Surface: `#ffffff`
- Primary text: `#172033`
- Muted text: `#667085`
- Accent blue: `#2764e7`
- Approval green: `#168a4a`
- Warning amber: `#b7791f`
- Rejection red: `#c2413b`
- Border: `#d9dee8`

Avoid a chatbot-first UI. The strongest visual object should be the project state and approval responsibility, not a message composer.

## MVP Screen Set

1. Portfolio Dashboard.
2. Review Queue.
3. Project Command Room.
4. Technical Review Workspace.
5. PM Approval Workspace.
6. Agent Execution Detail.
7. Artifact Version Compare.
8. Jira Draft Preview.

## Open UX Questions

- Should technical approval require both Architect and System Analyst approval in MVP?
- Should business clarification happen inside AegisFlow or sync back to the BRD source system?
- Should PMs edit Jira draft hierarchy inline or through a structured correction request?
- How much agent trace detail should default reviewers see before it becomes noise?
- Should review SLA be configured per project type, business unit, or risk level?
