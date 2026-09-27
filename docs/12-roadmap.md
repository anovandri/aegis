# Roadmap

## Phase 0: Architecture Foundation

- Approve architecture documentation and ADRs.
- Validate MVP scope with Digital Architecture, System Analyst, PM, Security, and Engineering.
- Define initial artifact schemas.
- Define approval policies and role mappings.

## Phase 1: MVP Platform

- Build modular monolith API and review portal.
- Set up PostgreSQL schema and migrations.
- Set up workflow engine and project lifecycle workflow.
- Implement artifact versioning.
- Implement `ProjectContext`.
- Implement agent invocation adapter and schema validation.
- Implement BRD Intake, Requirement Analyst, Architecture, System Analyst, Estimation, and Jira Planning agents.
- Implement Technical Review and PM Review gates.
- Implement Jira draft-to-create flow after approval.
- Implement audit and baseline observability.

## Phase 2: Knowledge Maturity

- Add service catalog adapter.
- Add API catalog adapter.
- Add architecture standards and security standards ingestion.
- Add ADR repository ingestion.
- Add pgvector retrieval for curated knowledge chunks.
- Add knowledge source authority scoring and freshness indicators.

## Phase 3: Evaluation And Continuous Improvement

- Convert human corrections into evaluation datasets.
- Track approval and correction metrics by agent/prompt/model version.
- Build regression tests for prompts and schemas.
- Add cost forecasting and per-project budgets.
- Add model routing policies.

## Phase 4: Delivery Automation Preparation

- Add read-only source repository analysis.
- Add dependency analysis from code.
- Add implementation planning refinements.
- Add branch and repository authorization model.
- Add security review gate if required.

## Phase 5: Development Agent

- Introduce Development Agent after approved Jira planning.
- Require branch isolation, code owner policies, and pull request-only output.
- Add mandatory human code review.
- Add sandboxed execution and repository-scoped credentials.
- Add static analysis and test execution gates.

## Phase 6: QA Agent

- Generate test scenarios from approved requirements and system analysis.
- Produce integration and E2E test drafts.
- Integrate with test management tools.
- Support QA approval and defect creation.

## Phase 7: CI/CD And UAT Automation

- Integrate CI/CD status.
- Add deployment readiness checks.
- Add UAT planning and business approval gates.
- Automate evidence collection, not production approval.

## Architecture Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Agents produce plausible but wrong analysis | Bad architecture or tickets | Human gates, citations, schema validation, evaluations |
| Workflow and DB state diverge | Confusing operations | Transactional state updates and reconciliation jobs |
| Knowledge sources are stale | Wrong reuse recommendations | Source freshness metadata and authority policy |
| Token costs grow unpredictably | Budget pressure | Context budgets, retrieval limits, model routing |
| Jira drafts do not match team conventions | PM correction overhead | Configurable Jira templates and project policies |
| Too many services too early | Slower delivery | Modular monolith for MVP |
| Human review becomes a bottleneck | Workflow delays | Dashboards, reminders, focused review bundles |
| Prompt changes break output quality | Regression | Prompt versioning and evaluation datasets |

## Open Questions

- Which enterprise identity provider and role model should be used?
- Which Jira projects and issue type schemes are in MVP?
- Are BRDs submitted manually, from document repositories, or via existing workflow systems?
- Which enterprise knowledge source is authoritative for service/API catalog?
- What AI provider data retention mode is required?
- What audit retention period is required?
- Should technical review require one approver or multiple role-based approvers?
- What estimation unit should be used: story points, ideal days, person-days, or team capacity buckets?
- Are payment or regulatory projects subject to mandatory security/data governance gates?

## Recommended Implementation Order

1. Domain enums, artifact schema registry, and `ProjectContext`.
2. PostgreSQL schema for project, artifact, workflow, review, and audit.
3. Workflow skeleton with durable pause/resume.
4. Review portal/API for human gates.
5. Agent invocation adapter with schema validation and audit.
6. Initial agents and prompt versioning.
7. Knowledge facade with manual/read-only sources.
8. Jira draft and approved publish flow.
9. Observability dashboards and evaluation records.
10. Security hardening and enterprise integration expansion.
