# Integration Architecture

## Integration Approach

AegisFlow should expose integrations through ports and adapters. Agents call only AegisFlow tools, never enterprise systems directly. Deterministic services enforce authorization, idempotency, and audit before external reads or writes.

## Integration Ports

```text
KnowledgeSearchPort
ServiceCatalogPort
ApiCatalogPort
DatabaseCatalogPort
EventCatalogPort
ArchitectureStandardsPort
SecurityStandardsPort
ADRRepositoryPort
PreviousProjectPort
JiraPort
SourceRepositoryPort
ObservabilityPort
```

## Adapter Maturity Levels

| Level | Description | MVP Position |
|---|---|---|
| Manual upload | Human uploads/export files | Acceptable for BRD and standards |
| Read-only API | System queries authoritative catalog | Preferred for service/API catalog |
| Cached ingestion | Scheduled snapshots into AegisFlow | Good for previous projects and ADRs |
| Write integration | AegisFlow modifies external system | Only Jira after PM approval in MVP |
| Event-driven sync | External changes publish events | Future unless enterprise already has events |

## Enterprise Knowledge Layer

The knowledge layer has two responsibilities:

1. Retrieve candidate context for agents.
2. Explain authority, freshness, ownership, and provenance.

Retrieval must return:

- Source ID.
- Source type.
- Owning system.
- Authority level.
- Version or effective date.
- Excerpt reference.
- Access decision.
- Retrieval timestamp.

## Knowledge Source Authority

| Source Type | Default Authority | Notes |
|---|---|---|
| Architecture Standards | Authoritative | If owned by architecture governance |
| Security Standards | Authoritative | Security review may override agent recommendation |
| API Catalog | Authoritative | If source is enterprise API registry |
| Service Catalog | Authoritative | Needed before proposing new services |
| Database Catalog | Authoritative | Needed for data ownership analysis |
| Event Catalog | Authoritative | Needed for async integration analysis |
| ADRs | Authoritative within scope | Must check superseded status |
| Previous Projects | Supporting | Useful precedent, not binding |
| BRDs | Supporting | Must not override current BRD |
| Source Code | Supporting or authoritative | Depends on repo ownership and freshness |
| Jira | Supporting | Actual delivery history may be useful for estimation |
| Observability | Supporting | Useful for runtime capacity and reliability assumptions |

## Jira Integration

MVP Jira integration should be approval-gated and idempotent.

Flow:

1. Jira Planning Agent creates draft plan artifact.
2. PM reviews and approves exact draft version.
3. Jira service maps draft to Jira project, issue types, labels, components, and links.
4. Jira service creates tickets using idempotency metadata.
5. Jira keys are persisted in `JIRA_CREATED` result.

The Jira Planning Agent must not receive credentials that can create issues. It can only generate draft payloads.

## Source Repository Integration

Source repository integration is read-only in MVP and optional. The Architecture Agent may use it only through a controlled knowledge adapter to inspect existing APIs, service boundaries, or coding standards. Future Development Agent support will require stronger sandboxing, branch policies, and code owner integration.

## Messaging

Kafka is not needed for MVP by default. Use:

- Database transactions for artifact and state persistence.
- Transactional outbox for integration events.
- Workflow engine timers and retries for orchestration.

Introduce Kafka later if:

- Enterprise systems already require Kafka integration.
- AegisFlow needs high-volume event ingestion.
- Multiple independent services consume lifecycle events.
- Audit and analytics streams exceed database/outbox needs.

## External System Failure Boundary

All external calls should pass through adapters that provide:

- Timeout policy.
- Retry policy.
- Idempotency key.
- Circuit breaker.
- Error classification.
- Audit logging.
- Tenant and project authorization checks.
