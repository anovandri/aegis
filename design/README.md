# AegisFlow Design Package

This folder contains UX design artifacts for the AegisFlow dashboard/control panel.

## Pencil Document

- `aegis.pen` is the intended Pencil design file.
- The `.pen` file must only be read or modified through Pencil MCP tools.
- The current Pencil document contains the enterprise architecture canvas, primary control-panel screens, menu-item screens, dashboard interaction states, and an interaction behavior map.

## Current Artifacts

- `control-panel-ux.md`: product UX, information architecture, roles, screens, and interaction model.
- `control-panel-wireframe.html`: static visual wireframe for the web dashboard/control panel.
- `control-panel-data-flow.md`: screen-level data flow, read models, command flow, staleness handling, and audit propagation.
- `control-panel-interactions.md`: click-by-click behavior for buttons, links, rows, filters, approval actions, exports, and error handling.

## Design Intent

AegisFlow should feel like an enterprise SDLC command center, not a generic chatbot UI. It is the single source of truth for:

- Project lifecycle state.
- Artifact versions.
- Agent outputs and evidence.
- Human approvals and corrections.
- Stakeholder decisions and communication.
- Jira readiness and publish status.
