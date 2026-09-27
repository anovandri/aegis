# ADR-003: ProjectContext

## Status

Proposed

## Context

Agents need shared context about the project, workflow state, artifact versions, governance requirements, and knowledge access policy. Conversation history is not reliable or auditable enough to act as a system of record.

## Decision

Create a canonical `ProjectContext` model assembled by the Project Context Service from persisted domain records. Every agent execution receives a `ProjectContext` projection with explicit artifact versions.

`ProjectContext` includes:

- Project identity and tenant.
- Workflow state.
- Current artifact versions and statuses.
- Governance requirements.
- Knowledge retrieval policy.
- Risk and classification metadata.

## Rationale

This makes agent inputs explicit, reproducible, and auditable. It also prevents agents from silently depending on hidden conversation state or stale local memory.

## Consequences

- Every artifact dependency must be tracked.
- Project context projections must be stable and hashable.
- Schema changes require versioning.
- Human corrections create new artifact versions and therefore new context snapshots.

## Alternatives Considered

### Conversation history as context

Rejected. It is incomplete, mutable, and not suitable for audit.

### Per-agent private memory as context

Rejected for authoritative decisions. Private memory may help usability later, but not lifecycle governance.

### Fully normalized context only

Deferred. JSONB artifacts give flexibility while schemas mature.
