# AegisFlow App

This folder is the implementation workspace for AegisFlow.

It starts as a small Nx monorepo with a Java Spring Boot modular monolith API.

## Current Shape

```text
app/
  apps/
    api/                  Spring Boot API
      src/main/java/
        com/aegisflow/api/
          application/    Use cases and application services
          domain/         Domain enums and records
          infrastructure/ Infrastructure adapters
          ports/          External capability interfaces
          web/            REST controllers
  nx.json
  package.json
  docker-compose.yml      Local PostgreSQL + pgvector, MinIO, and Temporal services
  settings.gradle
  build.gradle
```

## Commands

Install Nx dependencies:

```bash
npm install
```

Run the API:

```bash
npm run api:serve
```

Run tests:

```bash
npm run api:test
```

Build:

```bash
npm run api:build
```

## MVP Boundary

The current implementation intentionally covers the first slice from `docs/14-implementation-process-flow.md`:

- domain lifecycle enums;
- multipart BRD file submission API;
- immutable BRD artifact version append API;
- PostgreSQL-backed project and artifact version metadata storage;
- document storage port with in-memory and MinIO adapters;
- Temporal workflow contract and local wiring;
- `ProjectContext` projection;
- Phase 3 knowledge chunking and embedding retrieval for Requirement Analysis.

It does not yet implement human review, Jira, external embedding providers, or full enterprise knowledge ingestion.
Temporal is wired. BRD Intake and Requirement Analysis have concrete behavior.
BRD Intake currently supports text, markdown, and JSON BRD files. PDF/DOCX extraction is intentionally not implemented yet.

## Local Database

The API uses Flyway migrations for knowledge metadata.

By default, the local API is configured for PostgreSQL with pgvector support:

```text
jdbc:postgresql://localhost:5432/aegisflow
```

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Run the API against PostgreSQL:

```bash
AEGISFLOW_DATABASE_URL=jdbc:postgresql://localhost:5432/aegisflow \
AEGISFLOW_DATABASE_USERNAME=aegisflow \
AEGISFLOW_DATABASE_PASSWORD=aegisflow-secret \
npm run api:serve
```

Tests use an isolated H2 database from `src/test/resources/application.yml`, so they do not require Docker.

## Local Object Storage

AegisFlow stores document bytes behind `DocumentStoragePort`.

For normal tests, the API uses the in-memory adapter:

```yaml
aegisflow:
  storage:
    provider: in-memory
```

For local runtime, the default storage provider is MinIO. Start MinIO:

```bash
docker compose up -d minio
```

MinIO endpoints:

- S3 API: `http://localhost:9000`
- Console: `http://localhost:9001`
- Access key: `aegisflow`
- Secret key: `aegisflow-secret`

Run the API with MinIO storage:

```bash
AEGISFLOW_STORAGE_PROVIDER=minio npm run api:serve
```

Each BRD upload creates an immutable AegisFlow `ArtifactVersion` with:

- version number;
- file metadata;
- content hash;
- storage bucket;
- storage object key;
- optional S3 backend version id.

The AegisFlow artifact version remains the canonical document version. MinIO is the object storage backend.

## Local Temporal Workflow

Start Temporal:

```bash
docker compose up -d temporal
```

Run the API with Temporal enabled:

```bash
AEGISFLOW_TEMPORAL_ENABLED=true npm run api:serve
```

After uploading a BRD, monitor progress:

```bash
curl http://localhost:8080/api/projects/{projectId}/workflow
```

## BRD Intake Agent

By default, BRD Intake uses a deterministic local completeness analyzer:

```yaml
aegisflow:
  llm:
    provider: local
```

To use the OpenAI-backed adapter:

```bash
OPENAI_API_KEY=... \
AEGISFLOW_LLM_PROVIDER=openai \
AEGISFLOW_TEMPORAL_ENABLED=true \
npm run api:serve
```

The OpenAI adapter requests structured JSON output for the BRD Intake schema. The workflow activity still validates and persists the result as an AegisFlow artifact; the model does not directly control workflow state.

Requirement Analysis also has local and OpenAI-backed adapters. It consumes the original BRD, the persisted `BRD_ANALYSIS` artifact, and a static requirements checklist, then persists a `REQUIREMENT_ANALYSIS` artifact before the workflow moves to `ARCHITECTURE_ANALYSIS`.

Requirement Analysis also retrieves governed local knowledge from uploaded knowledge documents and the seeded baseline:

```text
apps/api/src/main/resources/knowledge/requirement-analysis-seed.json
```

The seed contains curated previous-project lessons, incident patterns, UAT lessons, and requirement standards. At startup, those records are loaded into the same knowledge service used by manually uploaded knowledge files, split into searchable chunks, embedded, and indexed for Requirement Analysis retrieval. Retrieved citations are passed into the Requirement Analyst Agent and persisted as `sourceRefs` in the generated `REQUIREMENT_ANALYSIS` artifact.

## Knowledge Management API

Knowledge source catalog endpoints for the Knowledge screen:

```bash
curl http://localhost:8080/api/knowledge/summary
curl http://localhost:8080/api/knowledge/sources
curl http://localhost:8080/api/knowledge/sources?status=FRESH
curl http://localhost:8080/api/knowledge/sources/{sourceId}
curl -X POST http://localhost:8080/api/knowledge/sources/{sourceId}/sync
curl http://localhost:8080/api/knowledge/sources/{sourceId}/sync-runs
curl http://localhost:8080/api/knowledge/audit
curl "http://localhost:8080/api/knowledge/search?q=QRIS%20payment%20reversal&agentName=Requirement%20Analyst%20Agent&workflowState=REQUIREMENT_ANALYSIS"
```

Knowledge ingestion connector endpoints for the `Register Source` flow:

```bash
curl http://localhost:8080/api/knowledge/adapters

curl -X POST http://localhost:8080/api/knowledge/sources/{sourceId}/connections \
  -H "Content-Type: application/json" \
  -d '{
    "adapterType": "JIRA",
    "connectionName": "PMO Jira historical delivery filter",
    "resourceLocator": "project = PAY AND type in (Epic, Story)",
    "authType": "PAT",
    "credentialRef": "vault://knowledge/jira/pmo-readonly",
    "configJson": "{\"baseUrl\":\"https://jira.example.test\",\"jql\":\"project = PAY AND type in (Epic, Story)\"}"
  }'

curl http://localhost:8080/api/knowledge/sources/{sourceId}/connections
curl -X POST http://localhost:8080/api/knowledge/connections/{connectionId}/check
curl -X POST http://localhost:8080/api/knowledge/connections/{connectionId}/discover
curl http://localhost:8080/api/knowledge/connections/{connectionId}/resources
curl http://localhost:8080/api/knowledge/resources/{resourceId}/content
```

`credentialRef` is a pointer to an external secrets manager entry. AegisFlow must not store raw Google Drive, Jira, GitLab, or repository credentials in its knowledge tables.
The current local resolver supports `env://VARIABLE_NAME`; production vault references need a secrets manager adapter.

GitLab connector example:

```bash
curl -X POST http://localhost:8080/api/knowledge/sources/{sourceId}/connections \
  -H "Content-Type: application/json" \
  -d '{
    "adapterType": "GITLAB",
    "connectionName": "Platform documentation repository",
    "resourceLocator": "group/platform-docs",
    "authType": "PAT",
    "credentialRef": "env://AEGISFLOW_GITLAB_TOKEN",
    "configJson": "{\"baseUrl\":\"https://gitlab.example.com\",\"projectPath\":\"group/platform-docs\",\"ref\":\"main\",\"path\":\"docs\",\"maxResources\":\"50\"}"
  }'
```

Google Drive connector example:

```bash
curl -X POST http://localhost:8080/api/knowledge/sources/{sourceId}/connections \
  -H "Content-Type: application/json" \
  -d '{
    "adapterType": "GOOGLE_DRIVE",
    "connectionName": "Architecture standards Drive folder",
    "resourceLocator": "1abcDriveFolderId",
    "authType": "OAUTH",
    "credentialRef": "env://AEGISFLOW_GOOGLE_DRIVE_TOKEN",
    "configJson": "{\"folderId\":\"1abcDriveFolderId\",\"query\":\"mimeType != '\''application/vnd.google-apps.folder'\''\",\"maxResources\":\"50\"}"
  }'
```

Create a source without uploading a document yet:

```bash
curl -X POST http://localhost:8080/api/knowledge/sources \
  -H "Content-Type: application/json" \
  -d '{
    "sourceId": "service-catalog",
    "name": "Service Catalog",
    "sourceType": "SERVICE_CATALOG",
    "authority": "AUTHORITATIVE",
    "ownerTeam": "Digital Architecture",
    "freshnessSlaHours": 24,
    "syncMode": "MANUAL_UPLOAD",
    "sensitivityPolicy": "INTERNAL",
    "allowedAgents": ["Requirement Analyst Agent", "Architecture Agent"],
    "workflowStates": ["REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS"],
    "tags": ["service-catalog", "reuse", "ownership"]
  }'
```

Create a knowledge document:

```bash
curl -X POST http://localhost:8080/api/knowledge/documents \
  -F "sourceTitle=QRIS Previous Project Lessons" \
  -F "sourceId=qris-previous-project-lessons" \
  -F "sourceType=PREVIOUS_PROJECT" \
  -F "authority=SUPPORTING" \
  -F "allowedAgents=Requirement Analyst Agent" \
  -F "workflowStates=REQUIREMENT_ANALYSIS" \
  -F "tags=qris,payment,reversal,reconciliation" \
  -F "file=@./qris-lessons.md"
```

Append a new immutable version:

```bash
curl -X POST http://localhost:8080/api/knowledge/documents/{documentId}/versions \
  -F "file=@./qris-lessons-v2.md"
```

Inspect knowledge records:

```bash
curl http://localhost:8080/api/knowledge/documents
curl http://localhost:8080/api/knowledge/documents/{documentId}
curl http://localhost:8080/api/knowledge/documents/{documentId}/versions
```

Phase 3 stores file bytes in MinIO or the in-memory storage adapter and stores knowledge metadata, chunks, and embeddings in the relational database. PostgreSQL deployments add a pgvector column and HNSW cosine index for `knowledge_chunks`; tests use H2 with text-serialized embeddings. The AegisFlow knowledge version remains the canonical version for retrieval and citation; MinIO version ids are captured as backend storage evidence when available.

The default embedding provider is local and deterministic:

```yaml
aegisflow:
  knowledge:
    embedding:
      provider: local
```

This keeps local development and tests offline. A later adapter can replace this with OpenAI embeddings or another enterprise-approved embedding service without changing workflow or agent contracts.

## Current API Contract

Create a project with the first BRD version:

```bash
curl -X POST http://localhost:8080/api/projects \
  -F "name=Dynamic QRIS Payment" \
  -F "businessOwner=Payments Business Team" \
  -F "domain=Merchant Payments" \
  -F "brdFile=@./BRD_Dynamic_QRIS_v1.pdf"
```

Append a revised BRD version:

```bash
curl -X POST http://localhost:8080/api/projects/{projectId}/brd-versions \
  -F "brdFile=@./BRD_Dynamic_QRIS_v2.pdf"
```
