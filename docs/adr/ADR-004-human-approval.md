# ADR-004: Human Approval

## Status

Proposed

## Context

AegisFlow must retain human authority over technical decisions and delivery commitment. Human intervention must pause and resume workflows without restarting the entire lifecycle.

## Decision

Implement human approval as first-class workflow states and persisted domain records.

MVP gates:

- Technical Review after Requirement, Architecture, and System Analysis.
- PM Review after Estimation and Jira Draft.

Technical Review decisions:

- `APPROVE`
- `APPROVE_WITH_CHANGE`
- `REJECT`
- `REQUEST_CLARIFICATION`

PM Review decisions:

- `APPROVE`
- `APPROVE_WITH_CHANGE`
- `REJECT`

## Rationale

Explicit approval states make governance observable, resumable, and auditable. Corrections become new artifact versions, enabling evaluation datasets and future model improvement.

## Consequences

- Review records must reference exact artifact versions.
- Pending reviews become stale when reviewed artifacts are superseded.
- Workflow resume signals must validate review IDs and decisions.
- UI design must prioritize efficient review and correction workflows.

## Alternatives Considered

### Inline approval inside agent chat

Rejected. It is hard to audit and risks mixing user conversation with lifecycle state.

### Automatic approval based on confidence

Rejected. Confidence may inform review priority but cannot replace governance.
