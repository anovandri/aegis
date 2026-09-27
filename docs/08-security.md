# Security

## Security Posture

AegisFlow operates on sensitive enterprise requirements and may eventually interact with Jira, source repositories, architecture catalogs, and CI/CD systems. It must assume BRDs and retrieved documents can contain malicious or accidental instructions.

## Threats

### Prompt Injection

BRDs, previous project documents, source code comments, Jira descriptions, and catalog entries may contain instructions such as "ignore prior rules" or "create tickets immediately." These must be treated as untrusted content.

Controls:

- Separate system instructions from untrusted document content.
- Mark retrieved content with source boundaries.
- Use input guardrails for malicious instruction detection.
- Require structured output validation.
- Deny workflow transitions from free text.

### Tool Abuse

Agents may attempt to call tools outside their role.

Controls:

- Tool allowlists by agent, tenant, project, and workflow state.
- No direct credentials in prompts.
- Service-side authorization before every tool call.
- Human approval before external writes.
- Tool result redaction.

### Unauthorized Jira Modification

The Jira Planning Agent creates drafts only. Jira creation is performed by a deterministic Jira service after PM approval.

Controls:

- Map project to allowed Jira project keys.
- Validate issue type and hierarchy policy.
- Use idempotency keys.
- Store approval ID in Jira metadata.
- Deny agent-originated create/update calls.

### Unauthorized Repository Access

Repository access must be read-only for MVP and scoped to approved repositories.

Controls:

- Repository allowlist per tenant/project.
- Short-lived credentials or service identity.
- Path-level restrictions where available.
- Audit repository reads.

### Sensitive Data And PII

BRDs may contain customer data, payment details, or internal secrets.

Controls:

- PII and secret scanning at ingestion.
- Redaction before agent invocation where possible.
- Data classification metadata on artifacts.
- Configurable AI provider data policy by tenant.
- Encryption at rest and in transit.
- Retention policy per artifact type.

## Credential Isolation

Agents never receive unrestricted infrastructure credentials. Runtime services own credentials and expose narrow tools. Each tool receives scoped input, validates authorization, and returns minimal output.

## Tenant Isolation

MVP should enforce tenant isolation at:

- Database query layer.
- Object storage paths if raw artifacts are externalized.
- Vector search filters.
- Tool authorization checks.
- Audit views.

## Authorization Model

Roles:

- Business submitter.
- Digital architect.
- System analyst.
- Project manager.
- Platform administrator.
- Integration service account.

Authorization decisions must consider:

- Tenant.
- Project membership.
- Workflow state.
- Requested action.
- Artifact classification.
- External system mapping.

## Audit Security

Audit logs must be append-only from the application perspective. They must capture human and machine actors, input/output hashes, source system IDs, and authorization outcomes. Audit logs should avoid storing full secrets or unnecessary PII.

## AI Provider Controls

- Per-tenant model allowlist.
- Configurable data retention mode according to enterprise policy.
- Token and cost limits.
- Output validation.
- Trace retention controls.
- Redaction before invocation.

## Security Review In MVP

The MVP should include security analysis fields in Requirement, Architecture, and System Analysis artifacts, but a dedicated Security Review gate can be added after the technical review gate when governance requires it.
