# ADR-002: Agent Orchestration

## Status

Proposed

## Context

AegisFlow uses specialized agents for BRD intake, requirement analysis, architecture analysis, system analysis, estimation, and Jira planning. The platform must prevent uncontrolled agent-to-agent execution and must retain deterministic lifecycle control.

## Decision

Use an agent runtime adapter to invoke agents as controlled workflow activities. OpenAI Agents SDK is the preferred initial runtime candidate, but it must not own the enterprise lifecycle workflow.

Agents may use:

- Structured prompts.
- Structured outputs.
- Tool calling through allowlisted AegisFlow tools.
- Guardrails.
- Runtime traces.

Agents may not:

- Create Jira tickets.
- Change workflow state directly.
- Call each other indefinitely.
- Access unrestricted tools or credentials.
- Treat conversation history as canonical context.

## Rationale

The OpenAI Agents SDK provides useful runtime capabilities for tools, guardrails, structured interaction, human-in-the-loop mechanisms, and tracing. AegisFlow needs those capabilities inside bounded execution steps, while Temporal and domain services retain lifecycle control.

LangGraph is deferred for MVP because it overlaps with workflow concerns and could create two sources of orchestration truth. It may be introduced later inside a single complex agent if internal reasoning graphs become necessary.

## Consequences

- Agent contracts must be strict and versioned.
- Agent outputs must be validated before persistence.
- Tool calls must be brokered through AegisFlow services.
- Agent runtime can be replaced if the adapter boundary remains stable.

## Alternatives Considered

### Free multi-agent collaboration

Rejected. It violates deterministic control and auditability requirements.

### Direct Responses API without agent runtime

Possible for early prototypes. It gives maximum control but would require more custom tool and guardrail infrastructure.

### LangGraph as primary orchestration layer

Rejected for MVP. It is better suited for stateful agent flows than enterprise SDLC lifecycle governance.
