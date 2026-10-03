# Knowledge Document Extraction and Indexing

## Purpose

This document refines how AegisFlow should process knowledge documents before they are searchable by agents.

The immediate concern is the current flow around:

```java
KnowledgeService.createVersion(...)
KnowledgeService.indexVersion(...)
KnowledgeTextExtractor
KnowledgeChunker
CodeKnowledgeParserRegistry
TreeSitterGoCodeKnowledgeParser
```

The current implementation works for plain text and source code that can first be represented as text. It is not yet sufficient for PDFs, Word documents, Excel files, scanned documents, or other structured enterprise knowledge formats.

## Current Implementation

The current implementation now has the first version of the refined extraction and indexing boundary.

Implemented components:

```text
KnowledgeContentExtractor
KnowledgeContentExtractorRegistry
ExtractedKnowledgeContent
ExtractedKnowledgeSection
PlainTextKnowledgeContentExtractor
TikaKnowledgeContentExtractor
KnowledgeIndexingPipeline
```

The current flow is:

```text
Uploaded or synced file
  |
  v
createVersion(...)
  |
  +-- store raw file in MinIO
  |
  +-- KnowledgeTextExtractor delegates to KnowledgeContentExtractorRegistry
  |
  +-- PlainTextKnowledgeContentExtractor handles text, Markdown, JSON, CSV, YAML, SQL, Go
  |
  +-- TikaKnowledgeContentExtractor handles PDF, DOC/DOCX, XLS/XLSX
  |
  +-- persist KnowledgeDocumentVersion.extractedText
  |
  v
indexVersion(...)
  |
  +-- delegates to KnowledgeIndexingPipeline
        |
        +-- if a CodeKnowledgeParser supports the file:
        |     +-- parse extractedText
        |     +-- convert CodeKnowledgeUnit to LLM-friendly text chunks
        |
        +-- otherwise:
        |     +-- KnowledgeChunker.chunk(extractedText)
        |
        +-- embed chunks
        |
        +-- save knowledge_chunks
```

Important observation:

```text
indexVersion(...) is not the first limitation.
KnowledgeTextExtractor.extract(...) is the first limitation.
```

`indexVersion(...)` can index anything that has already become `extractedText`. The system previously failed earlier for unsupported formats because `KnowledgeTextExtractor` only supported:

```text
text/*
.md
.txt
.json
application/json
```

That has now been expanded through `KnowledgeContentExtractorRegistry`.

## Problem

Enterprise knowledge is not only plain text.

AegisFlow must eventually process:

- Markdown
- plain text
- JSON
- source code
- PDF
- DOC/DOCX
- XLS/XLSX
- CSV
- PowerPoint
- diagrams
- scanned documents
- exported Jira tickets
- API specifications
- SQL files
- YAML configuration

Treating all of these as simple text creates several problems:

1. Important structure is lost.
2. Citations become vague.
3. Tables become hard to understand.
4. Code loses symbols and relationships.
5. Spreadsheet rows lose sheet/range context.
6. PDFs lose page references.
7. Word documents lose headings and section hierarchy.
8. Scanned documents need OCR, not text extraction.

## Refined Architecture

AegisFlow should separate four concerns:

```text
Storage
Extraction
Chunking / Parsing
Indexing
```

Recommended architecture:

```text
Raw Knowledge File
  |
  v
DocumentStoragePort
  |
  v
KnowledgeContentExtractorRegistry
  |
  +-- PlainTextContentExtractor
  +-- MarkdownContentExtractor
  +-- PdfContentExtractor
  +-- DocxContentExtractor
  +-- SpreadsheetContentExtractor
  +-- SourceCodeContentExtractor
  +-- OcrContentExtractor
  |
  v
ExtractedKnowledgeContent
  |
  v
KnowledgeIndexingPipeline
  |
  +-- CodeKnowledgeParserRegistry
  +-- KnowledgeChunker
  +-- TableAwareChunker
  +-- PageAwareChunker
  |
  v
KnowledgeChunk + Embedding
```

## Proposed Components

### KnowledgeContentExtractor

Introduce a generic extraction interface:

```java
public interface KnowledgeContentExtractor {
    boolean supports(String fileName, String mediaType);

    ExtractedKnowledgeContent extract(String fileName, String mediaType, byte[] content);
}
```

This replaces the single `KnowledgeTextExtractor` as the only extraction path.

### KnowledgeContentExtractorRegistry

The registry selects the correct extractor:

```text
KnowledgeContentExtractorRegistry
  -> findExtractor(fileName, mediaType)
```

Selection should be based on:

- media type
- file extension
- magic bytes when needed
- source metadata from adapter

### ExtractedKnowledgeContent

The extraction result should be richer than a single string.

Recommended model:

```java
public record ExtractedKnowledgeContent(
    String extractionType,
    String text,
    List<ExtractedKnowledgeSection> sections,
    Map<String, String> metadata,
    List<String> warnings
) {
}
```

Example extraction types:

```text
TEXT
MARKDOWN
PDF
DOCX
XLSX
CSV
SOURCE_CODE
OCR
UNSUPPORTED
```

### ExtractedKnowledgeSection

Sections preserve source structure:

```java
public record ExtractedKnowledgeSection(
    String sectionType,
    String title,
    String text,
    Integer pageNumber,
    String sheetName,
    String cellRange,
    Integer startLine,
    Integer endLine,
    Map<String, String> metadata
) {
}
```

Examples:

```text
PDF page 12
DOCX heading "Settlement Flow"
XLSX sheet "SLA Matrix", range A2:F18
Go file qris/service.go, lines 42-91
Markdown heading "Failure Handling"
```

## Format-Specific Strategy

### Plain Text

Use the existing text flow.

```text
extract UTF-8 text
chunk by paragraph
embed
```

Current components:

```text
KnowledgeTextExtractor
KnowledgeChunker
```

Future name:

```text
PlainTextContentExtractor
```

### Markdown

Markdown should preserve headings.

Recommended behavior:

```text
split by heading hierarchy
preserve heading path
chunk large sections
cite heading path
```

Example citation:

```text
docs/architecture.md > Integration Pattern > Retry Handling
```

### Source Code

Source code should not be treated as generic prose.

Current implementation:

```text
CodeKnowledgeParser
CodeKnowledgeParserRegistry
TreeSitterGoCodeKnowledgeParser
CodeKnowledgeUnit
CodeRelationshipExtractor
```

Current behavior:

```text
.go file
  -> Tree-sitter parse
  -> function/type/method code units
  -> LLM-friendly code chunks
```

Recommended future behavior:

```text
source file
  -> Tree-sitter parse
  -> code units
  -> code relationships
  -> persist knowledge_code_units
  -> persist knowledge_code_relationships
  -> embed symbol-level chunks
```

Example citation:

```text
qris/service.go:42-91 CreateDynamicQr
```

### PDF

PDF support should be page-aware.

Recommended behavior:

```text
extract text per page
preserve page number
detect headings where possible
chunk page/section text
embed chunks with page metadata
```

Example citation:

```text
BRD_Dynamic_QRIS.pdf page 14
```

Additional considerations:

- Some PDFs contain embedded text.
- Some PDFs are scanned images and require OCR.
- Tables in PDFs may need specialized extraction.
- Page numbers must be preserved for human review.

Recommended Java libraries to evaluate later:

```text
Apache PDFBox
Tika
OCR engine for scanned PDFs
```

### DOC/DOCX

Word documents should preserve document hierarchy.

Recommended behavior:

```text
extract title
extract headings
extract paragraphs under heading path
extract tables
extract comments if required
chunk by section
```

Example citation:

```text
QRIS_BRD.docx > Functional Requirement > Reversal Flow
```

Recommended Java libraries to evaluate later:

```text
Apache POI
Tika
```

### XLS/XLSX

Spreadsheets should not be flattened blindly.

Recommended behavior:

```text
extract workbook metadata
extract sheets
detect table regions
preserve headers
chunk by sheet/table/range
```

Example citation:

```text
SLA_Matrix.xlsx > Sheet "Availability" > A2:F18
```

Spreadsheet-specific chunk text should include context:

```text
Workbook: SLA_Matrix.xlsx
Sheet: Availability
Range: A2:F18
Headers: Channel, SLA, RTO, RPO, Owner
Rows:
...
```

Recommended Java libraries to evaluate later:

```text
Apache POI
Tika
```

### CSV

CSV should be table-aware.

Recommended behavior:

```text
detect headers
chunk by row groups
preserve row numbers
preserve column names
```

Example citation:

```text
service_catalog.csv rows 21-40
```

### Images and Scanned Documents

Images should not be indexed unless OCR is enabled.

Recommended behavior:

```text
store raw file
mark extractionType = OCR_REQUIRED
do not embed until OCR succeeds
audit warning
```

OCR should be a separate capability because it has different cost, latency, and accuracy characteristics.

## How `indexVersion(...)` Should Evolve

Current method:

```java
private void indexVersion(KnowledgeDocument document, KnowledgeDocumentVersion version)
```

Current responsibility:

```text
turn extractedText into chunks
embed chunks
save chunks
```

Recommended refined responsibility:

```text
turn ExtractedKnowledgeContent into retrieval chunks
embed chunks
save chunks
save extraction/indexing metadata
```

Future shape:

```java
private void indexVersion(
    KnowledgeDocument document,
    KnowledgeDocumentVersion version,
    ExtractedKnowledgeContent content
)
```

Or better:

```text
KnowledgeIndexingPipeline.index(document, version, extractedContent)
```

This keeps `KnowledgeService` from becoming responsible for every file format.

## Why Extraction Comes Before Indexing

The current instinct might be to make `indexVersion(...)` handle PDF, DOCX, and XLSX.

That would be the wrong boundary.

Better boundary:

```text
Extraction answers:
What text/structure can we safely derive from this file?

Indexing answers:
How should that extracted content become searchable chunks?
```

Examples:

```text
PDF extractor
  -> page-aware sections

DOCX extractor
  -> heading-aware sections

XLSX extractor
  -> table-aware sections

Tree-sitter parser
  -> symbol-aware sections
```

Then indexing can be generalized.

## Database Implications

Current tables:

```text
knowledge_documents
knowledge_document_versions
knowledge_chunks
```

Useful near-term additions:

```text
knowledge_extraction_runs
knowledge_extracted_sections
```

### knowledge_extraction_runs

Suggested fields:

```text
extraction_run_id
document_id
document_version
extractor_name
extractor_version
status
started_at
completed_at
warnings
error_message
```

### knowledge_extracted_sections

Suggested fields:

```text
section_id
document_id
document_version
section_type
title
text
page_number
sheet_name
cell_range
start_line
end_line
metadata_json
created_at
```

Then `knowledge_chunks` can reference `section_id` later.

## Citation Requirements

The goal is not only retrieval. The goal is trustworthy retrieval.

Every indexed chunk should be able to explain where it came from.

Citation examples:

| Source Type | Citation |
|---|---|
| PDF | `BRD.pdf page 8` |
| DOCX | `BRD.docx > Scope > Out of Scope` |
| XLSX | `SLA.xlsx > Sheet "RTO" > B4:F12` |
| Go source | `qris/service.go:42-91 CreateDynamicQr` |
| Markdown | `architecture.md > Retry Strategy` |
| CSV | `catalog.csv rows 10-30` |

Without structured extraction, citations will be too vague for enterprise review.

## Error and Failure Handling

Extraction and indexing should have separate failures.

### Extraction Failure

Examples:

```text
unsupported format
encrypted PDF
corrupt DOCX
scanned PDF without OCR
XLSX too large
binary file
```

Behavior:

```text
store raw file
record extraction failure
do not index
mark source NEEDS_REVIEW or PARTIAL
write audit event
```

### Indexing Failure

Examples:

```text
embedding provider unavailable
chunk too large
parser failure
database failure
```

Behavior:

```text
keep extracted content
retry indexing if safe
record indexing failure
do not lose raw file
```

## Security Considerations

Supporting more file types increases risk.

Required controls:

- file size limits;
- media type validation;
- extension validation;
- malware scanning later;
- secret scanning before embedding;
- PII detection before embedding;
- encrypted document handling;
- OCR cost controls;
- tenant isolation;
- audit events for extraction and indexing;
- source-specific access control.

Important rule:

```text
Never embed content that should not be retrievable by the requesting agent.
```

## Recommended Implementation Order

### Phase 1: Refactor Extraction Boundary

Create:

```text
KnowledgeContentExtractor
KnowledgeContentExtractorRegistry
ExtractedKnowledgeContent
ExtractedKnowledgeSection
PlainTextContentExtractor
```

Status:

```text
Implemented
```

Notes:

```text
KnowledgeTextExtractor remains as a compatibility facade, but delegates to KnowledgeContentExtractorRegistry.
```

### Phase 2: Move Code Parsing Behind Indexing Pipeline

Create:

```text
KnowledgeIndexingPipeline
```

Responsibilities:

```text
choose code parser or generic chunker
produce KnowledgeChunk records
embed chunks
save chunks
```

Status:

```text
Implemented
```

Notes:

```text
KnowledgeService.indexVersion(...) now delegates to KnowledgeIndexingPipeline.
```

### Phase 3: Add PDF Support

Add:

```text
PdfContentExtractor
```

Minimum requirement:

```text
page-aware text extraction
page citations
```

Status:

```text
Partially implemented
```

Notes:

```text
TikaKnowledgeContentExtractor can extract text from PDFs.
Page-aware section persistence and page-level citations are still future work.
```

### Phase 4: Add DOCX Support

Add:

```text
DocxContentExtractor
```

Minimum requirement:

```text
heading-aware extraction
table extraction
section citations
```

Status:

```text
Partially implemented
```

Notes:

```text
TikaKnowledgeContentExtractor can extract text from DOC/DOCX.
Heading-aware sections and table-aware citations are still future work.
```

### Phase 5: Add XLSX/CSV Support

Add:

```text
SpreadsheetContentExtractor
CsvContentExtractor
TableAwareChunker
```

Minimum requirement:

```text
sheet/range-aware chunks
row/column citations
```

Status:

```text
Partially implemented
```

Notes:

```text
TikaKnowledgeContentExtractor can extract text from XLS/XLSX.
PlainTextKnowledgeContentExtractor supports CSV as text.
Sheet/range-aware chunks remain future work.
```

### Phase 6: Persist Extraction Metadata

Add:

```text
knowledge_extraction_runs
knowledge_extracted_sections
```

This improves auditability and debugging.

### Phase 7: OCR and Advanced Formats

Add only after governance decisions:

```text
OCR
PowerPoint extraction
diagram extraction
image extraction
```

OCR should be explicitly controlled because it can be costly and inaccurate.

## Impact on Agents

Better extraction improves agent quality.

### Requirement Analyst Agent

Can retrieve:

- BRD sections;
- requirement tables;
- assumptions;
- NFR matrices;
- glossary terms.

### Architecture Agent

Can retrieve:

- source code symbols;
- API definitions;
- ADR sections;
- integration diagrams;
- service catalog tables.

### System Analyst Agent

Can retrieve:

- API contracts;
- sequence flow sections;
- spreadsheet mappings;
- field-level tables.

### Estimation Agent

Can retrieve:

- previous project scope tables;
- impacted code units;
- related Jira patterns;
- test areas.

## Conclusion

To support PDF, DOC, XLS, and other enterprise formats, AegisFlow should not make `indexVersion(...)` directly understand every file type.

Instead:

```text
Make extraction pluggable.
Make extracted content structured.
Make indexing content-aware.
Preserve citations.
Audit extraction and indexing separately.
```

The key refinement is now partially applied:

```text
KnowledgeTextExtractor delegates to a registry of content extractors.
indexVersion delegates to a format-aware indexing pipeline.
Tree-sitter remains one parser inside that pipeline, specifically for source code.
```
