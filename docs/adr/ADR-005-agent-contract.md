# ADR-005: Agent Contract

## Status

Proposed

## Context

Agents generate analysis that can influence enterprise planning. Natural language alone is too ambiguous for workflow control, persistence, validation, evaluation, and audit.

## Decision

All agents must use versioned structured input and output contracts. Workflow transitions may depend only on validated structured fields, never arbitrary generated prose.

The common output envelope includes:

- `status`
- `confidence`
- `summary`
- `findings`
- `questions`
- `assumptions`
- `recommendations`
- `artifactPayload`

Allowed common statuses:

- `COMPLETE`
- `NEED_CLARIFICATION`
- `BLOCKED`
- `INVALID_INPUT`

## Rationale

Structured contracts enable validation, deterministic workflow rules, metrics, comparisons across prompt versions, and controlled evolution.

## Consequences

- Schemas must be versioned and migration-aware.
- Invalid outputs require retry or human escalation.
- Prompt design must target schema completion.
- Artifact payloads can evolve independently per agent.

## Alternatives Considered

### Natural-language-only artifacts

Rejected. They are acceptable for human summaries but not workflow control.

### Letting agents choose next state

Rejected. Agents can recommend, but workflow rules decide.

### One universal payload for all agents

Rejected. A shared envelope is useful, but agent-specific payload schemas are necessary for useful validation.
