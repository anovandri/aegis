# Knowledge Sync Run

## Purpose

A knowledge sync-run is the audit record of an attempt to refresh a knowledge source.

In the Knowledge screen, the `Sync Source` action should not silently mutate source data. It should produce a traceable execution record showing:

- which source was refreshed;
- when the refresh started and completed;
- whether it succeeded or failed;
- how many records changed;
- any error message;
- how the source freshness state changed.

This lets administrators and reviewers answer a practical governance question:

```text
Can agents safely use this source right now?
```

## Current Implementation

The current MVP implementation has the sync-run model, API, and first adapter-backed ingestion path.

Current endpoint:

```text
POST /api/knowledge/sources/{sourceId}/sync
```

Current behavior for connector-backed sources:

1. Looks up the knowledge source.
2. Loads registered source connections.
3. Resolves the adapter by `adapterType`.
4. Checks connector reachability and credential resolution.
5. Discovers source resources.
6. Fetches resource payloads.
7. Maps each external resource to a stable AegisFlow document using `knowledge_synced_resource_documents`.
8. Creates a new knowledge document for a new resource.
9. Appends a new document version when content changed.
10. Skips unchanged resources.
11. Extracts text, chunks, embeds, and indexes changed resources.
12. Creates a `KnowledgeSyncRun` with `SUCCEEDED`, `PARTIAL`, or `FAILED`.
13. Updates source freshness fields.
14. Writes knowledge audit events.

Manual-upload sources without registered connections still support a metadata-only freshness refresh. Non-manual sources without registered connections fail with `No registered source connections`.

## Data Model

The sync-run table is created in:

```text
app/apps/api/src/main/resources/db/migration/V5__knowledge_source_registry.sql
app/apps/api/src/main/resources/db/migration/V7__knowledge_synced_resource_documents.sql
```

```text
knowledge_sync_runs

sync_run_id
source_id
status
started_at
completed_at
records_changed
error_message
```

```text
knowledge_synced_resource_documents

resource_key
source_id
connection_id
external_id
document_id
last_content_hash
updated_at
```

Current Java model:

```text
KnowledgeSyncRun

syncRunId
sourceId
status
startedAt
completedAt
recordsChanged
errorMessage
```

## Related Source Fields

The sync-run itself is historical. The current source state lives on `knowledge_sources`.

Relevant fields:

```text
status
last_synced_at
review_due_at
freshness_sla_hours
sync_mode
enabled
```

The Knowledge UI should render source state from `knowledge_sources`, and render sync history from `knowledge_sync_runs`.

## API Usage

Trigger a sync:

```bash
curl -X POST http://localhost:8080/api/knowledge/sources/{sourceId}/sync
```

Inspect sync history:

```bash
curl http://localhost:8080/api/knowledge/sources/{sourceId}/sync-runs
```

Inspect source state:

```bash
curl http://localhost:8080/api/knowledge/sources/{sourceId}
```

Inspect audit events:

```bash
curl "http://localhost:8080/api/knowledge/audit?sourceId={sourceId}"
```

## UI Interpretation

The Knowledge screen should use sync-run data like this:

| UI Element | Backend Field |
|---|---|
| Fresh / Stale / Review due | `knowledge_sources.status` |
| Fresh 8m ago | `knowledge_sources.lastSyncedAt` |
| Review due | `knowledge_sources.reviewDueAt` |
| Sync Source button result | latest `knowledge_sync_runs.status` |
| Sync error details | latest `knowledge_sync_runs.errorMessage` |
| Audit Trail | `knowledge_audit_events` |

For connector-backed sources, `recordsChanged` is the number of newly created or newly versioned knowledge documents. Unchanged external resources are skipped.

## Adapter Behavior

A sync-run is now the execution envelope around registered adapters.

Example flow:

```text
POST /api/knowledge/sources/service-catalog/sync
  -> load source connections
  -> check adapter connection
  -> discover resources
  -> fetch resource payloads
  -> store file snapshots in MinIO
  -> create document versions
  -> chunk and embed text
  -> update source freshness
  -> save sync-run SUCCEEDED, PARTIAL, or FAILED
  -> write audit event
```

Current statuses:

```text
SUCCEEDED
PARTIAL
FAILED
```

## Failure Behavior

Recommended behavior for real adapters:

| Failure | Behavior |
|---|---|
| Source unavailable | Mark sync-run `FAILED`; keep previous indexed knowledge; set source `STALE` or `NEEDS_REVIEW` |
| Schema validation failure | Mark sync-run `FAILED`; quarantine invalid records |
| Some records invalid | Mark sync-run `PARTIAL`; index valid records; audit invalid ones |
| Secret or PII detected | Quarantine record; do not index it |
| Adapter timeout | Retry if configured; otherwise `FAILED` |
| Unauthorized source access | `FAILED`; audit access denial |

Agents should not automatically trust a failed or stale source. Retrieval policy should decide whether to continue, warn, block, or escalate.

## Relation To Temporal

The current sync-run is synchronous and controller-driven.

For larger real integrations, sync should likely become a Temporal workflow or activity because source ingestion may be slow, retryable, and partially recoverable.

Recommended future shape:

```text
KnowledgeSourceSyncWorkflow
  -> ValidateSourceConfigurationActivity
  -> FetchSourceRecordsActivity
  -> ValidateAndClassifyRecordsActivity
  -> StoreSourceSnapshotsActivity
  -> ChunkAndEmbedActivity
  -> PublishKnowledgeIndexActivity
  -> CompleteSyncRunActivity
```

This keeps sync resumable and auditable without blocking the HTTP request.

## Current Limitations

- Sync is synchronous and controller-driven.
- Sync currently supports GitLab and Google Drive adapters; Jira remains a registration/discovery stub.
- Sync does not yet quarantine failed records in a dedicated quarantine table.
- Sync does not yet persist citation usage from agent executions.
- Sync does not yet enforce approval before authoritative source changes.

The current implementation is useful because it connects registered sources to versioned knowledge documents while preserving source governance, auditability, and idempotency.
