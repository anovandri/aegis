# Knowledge Docker Integration Tests

This document explains the Docker-backed integration tests for AegisFlow knowledge management.

## Purpose

`KnowledgeControllerDockerIT` verifies the knowledge APIs against real infrastructure dependencies, not only in-memory or H2 substitutes.

The test simulates the controller calling through the application/service/repository/storage layers into:

- PostgreSQL
- pgvector
- MinIO-compatible object storage
- Flyway migrations
- knowledge source adapters

This gives higher confidence that document ingestion, indexing, source sync, and object storage work as they will in a deployed environment.

## Test Class

```text
app/apps/api/src/test/java/com/aegisflow/api/web/KnowledgeControllerDockerIT.java
```

The test uses Testcontainers to start Docker containers during the test run.

## Containers Started

### PostgreSQL + pgvector

Image:

```text
pgvector/pgvector:pg17
```

Used to verify:

- real PostgreSQL persistence
- Flyway migrations
- PostgreSQL vendor migration
- `vector` extension creation
- `knowledge_chunks.embedding_vector` column population

### MinIO

Image:

```text
bitnamilegacy/minio:latest
```

Used to verify:

- bucket creation
- object upload
- object versioning
- object retrieval
- stored file content

The application still uses the existing `MinioDocumentStorageAdapter`; the test only supplies a Docker-backed MinIO endpoint dynamically.

## Runtime Configuration

The test overrides Spring properties with `@DynamicPropertySource`.

Key overrides:

```text
spring.datasource.url
spring.datasource.username
spring.datasource.password
spring.flyway.locations
aegisflow.storage.provider=minio
aegisflow.storage.bucket=aegisflow-docker-it
aegisflow.storage.endpoint=<testcontainer-minio-url>
aegisflow.temporal.enabled=false
aegisflow.llm.provider=local
aegisflow.knowledge.embedding.provider=local
```

This keeps the test deterministic while still using real external dependencies.

## Scenarios Covered

### 1. Manual Knowledge Document Upload

The test calls:

```http
POST /api/knowledge/documents
```

Then asserts:

- API response contains the document and version metadata
- row exists in `knowledge_documents`
- row exists in `knowledge_document_versions`
- chunks exist in `knowledge_chunks`
- pgvector extension exists
- `embedding_vector` is populated
- MinIO bucket exists
- MinIO object exists
- MinIO object content matches the uploaded file
- uploaded knowledge is discoverable through `/api/knowledge/search`

### 2. Source Connector Discovery and Sync

The test calls:

```http
POST /api/knowledge/sources
POST /api/knowledge/sources/{sourceId}/connections
POST /api/knowledge/connections/{connectionId}/check
POST /api/knowledge/connections/{connectionId}/discover
GET  /api/knowledge/connections/{connectionId}/resources
GET  /api/knowledge/resources/{resourceId}/content
POST /api/knowledge/sources/{sourceId}/sync
GET  /api/knowledge/sources/{sourceId}/sync-runs
GET  /api/knowledge/audit
GET  /api/knowledge/summary
GET  /api/knowledge/search
```

The test uses a deterministic in-test adapter named:

```text
DOCKER_SYNC
```

This adapter avoids calling real GitLab, Google Drive, or Jira, but it still exercises the complete AegisFlow source sync pipeline.

It verifies:

- source registration
- adapter discovery
- connection registration
- connection health check
- resource discovery
- resource content fetch
- source sync
- idempotent re-sync when content has not changed
- sync run persistence
- audit event persistence
- synced resource document mapping
- indexed chunks
- MinIO storage for synced documents

## Bug Caught by the Test

The sync integration test exposed a real modeling issue.

Originally, syncing multiple resources under the same source attempted to create multiple `knowledge_documents` rows with the same `source_id`.

That violated:

```sql
knowledge_documents.source_id UNIQUE
```

The fix:

- each synced resource gets a stable resource-level document source ID
- parent source aggregation uses `knowledge_synced_resource_documents`
- source-level counts still represent all documents synced from that source

This is exactly the kind of issue Docker-backed integration testing is intended to catch.

## How To Run

From the repository root:

```bash
cd app
npm run api:test
```

This runs all API tests, including the Docker-backed integration test.

To run only the Docker integration test:

```bash
cd app
./gradlew :apps:api:test --tests com.aegisflow.api.web.KnowledgeControllerDockerIT
```

## Requirements

You need:

- Docker running locally
- network access to pull Docker images the first time
- Java 21
- Node/npm for the Nx wrapper command

Images used:

```text
pgvector/pgvector:pg17
bitnamilegacy/minio:latest
testcontainers/ryuk
```

The first run may take longer because Docker images must be downloaded.

## When To Use This Test

Run this test when changing:

- `KnowledgeController`
- `KnowledgeService`
- knowledge repositories
- Flyway migrations
- pgvector integration
- MinIO storage adapter
- source sync logic
- knowledge adapter contracts
- document extraction and indexing

For quick local feedback during small unit-level changes, the regular non-Docker tests are faster. For persistence and infrastructure confidence, use this Docker-backed test.

## Future Adapter Simulation

For real upstream adapter testing:

- GitLab can be tested with a Dockerized GitLab CE instance, but it is heavy.
- Google Drive does not have a practical official local emulator.
- Jira is also heavy to run as a real local dependency.
- WireMock or MockServer containers are recommended for adapter contract tests.

Because GitLab and Google Drive adapters already accept configurable `baseUrl`, they can be pointed at a mock HTTP server for deterministic adapter tests.
