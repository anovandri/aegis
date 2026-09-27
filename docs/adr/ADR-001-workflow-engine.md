# ADR-001: Workflow Engine

## Status

Proposed

## Context

AegisFlow must orchestrate long-running SDLC workflows with deterministic state transitions, retries, wait states, human approvals, auditability, and resumability. Agent execution, external integrations, and human reviews may take seconds, hours, days, or weeks.

The system must not rely on autonomous agent loops to decide lifecycle progression.

## Decision

Use a durable workflow engine for execution orchestration, with Temporal as the preferred candidate for MVP validation.

Temporal should own:

- Durable workflow execution history.
- Timers and retries.
- Waiting for human approval signals.
- Activity orchestration.
- Operational workflow visibility.

The application database should own:

- Canonical project lifecycle state.
- Artifact versions.
- Human reviews.
- Audit records.
- Integration outcomes.

## Rationale

Temporal is well aligned with long-running, resumable workflows and retryable external work. It also enforces deterministic workflow design, which supports replay and durable execution. Non-deterministic operations such as AI calls, database queries, and external API calls should be implemented as Activities rather than inside workflow logic.

The database remains canonical because business users, auditors, and integrations need queryable project state independent of workflow engine internals.

## Consequences

- Workflow workers must be designed with deterministic workflow code.
- Activity implementations must be idempotent.
- Application state and workflow state require reconciliation checks.
- The team must learn Temporal operational patterns.

## Alternatives Considered

### Database-only workflow

Simpler infrastructure, but weaker durability, retry semantics, timers, and human wait handling.

### Agent graph as workflow engine

Rejected for MVP. Agent graph runtimes are useful for reasoning flows but should not own enterprise governance lifecycle state.

### Kafka-driven choreography

Rejected for MVP. It increases operational complexity and spreads lifecycle state across consumers.
