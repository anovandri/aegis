# MVP Scope

## Included

The first implementation should cover:

- Project creation and BRD submission.
- BRD artifact versioning.
- Deterministic workflow through the MVP lifecycle.
- BRD Intake Agent.
- Requirement Analyst Agent.
- Architecture Agent.
- System Analyst Agent.
- Technical Review gate.
- Estimation Agent.
- Jira Planning Agent.
- PM Review gate.
- Jira ticket creation after approval.
- Agent execution audit records.
- Artifact versioning.
- Basic knowledge retrieval facade with manual or read-only sources.
- Observability for workflow, agent, review, and Jira integration.

## Excluded

- Development Agent.
- QA Agent.
- CI/CD automation.
- UAT automation.
- Automatic code generation.
- Automatic deployment.
- Autonomous agent delegation loops.
- Kafka unless required by existing enterprise platform constraints.
- Full enterprise knowledge integration across all catalogs.
- Multi-tenant self-service administration beyond basic tenant isolation.

## Recommended MVP Repository Structure

```text
aegisflow/
  apps/
    web/
  services/
    api/
    worker/
  domain/
    project/
    artifact/
    workflow/
    review/
    estimation/
    jira-planning/
  agents/
    brd-intake/
    requirement-analyst/
    architecture/
    system-analyst/
    estimation/
    jira-planning/
    shared/
  workflows/
    project-lifecycle/
  integrations/
    jira/
    knowledge/
    source-repository/
  knowledge/
    ingestion/
    retrieval/
    schemas/
  infrastructure/
    database/
    temporal/
    deployment/
  docs/
  tests/
    contract/
    workflow/
    integration/
    evaluation/
```

## Modular Monolith vs Microservices

Start with a modular monolith plus separate workflow worker process.

Reasons:

- The domain model is tightly connected.
- Transactional consistency matters for artifacts, reviews, and workflow state.
- Team velocity is higher with fewer deployment units.
- Observability and security are simpler.
- Service boundaries can be extracted later after usage patterns stabilize.

Potential future service splits:

- Agent runtime service.
- Knowledge ingestion/retrieval service.
- Integration gateway.
- Evaluation/analytics service.

## Backend Language

Java/Kotlin and Go are both viable.

Recommendation for MVP: Kotlin or Java if the enterprise already has JVM maturity, Temporal JVM experience, Spring Boot standards, and Jira/catalog integrations. Choose Go if the platform team favors smaller services, simpler binaries, and Temporal Go expertise.

Avoid making language choice an architecture blocker. The stronger decision is the separation between domain services, workflow activities, agent adapters, and integration ports.

## Infrastructure Needed For MVP

Required:

- PostgreSQL.
- Workflow engine, preferably Temporal.
- AI provider integration.
- Review portal/API.
- Jira integration after approval.
- Centralized logging and metrics.

Optional initially:

- pgvector, if curated retrieval is in MVP.
- Object storage, if BRDs and raw outputs are large.
- Kafka.
- Separate microservices.

## Implementation Readiness Checklist

- State machine approved.
- Artifact schemas versioned.
- Human approval semantics approved.
- Agent execution audit schema approved.
- Jira mapping policy approved.
- Initial prompt versioning strategy approved.
- Knowledge authority policy approved.
- Security model reviewed.
