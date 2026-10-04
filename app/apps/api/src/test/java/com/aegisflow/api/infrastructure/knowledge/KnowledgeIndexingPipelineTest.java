package com.aegisflow.api.infrastructure.knowledge;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeIndexingPipelineTest {
    private final KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
    private final KnowledgeChunker chunker = mock(KnowledgeChunker.class);
    private final KnowledgeEmbeddingPort embeddingPort = mock(KnowledgeEmbeddingPort.class);
    private final CodeKnowledgeParserRegistry parserRegistry = mock(CodeKnowledgeParserRegistry.class);
    private final KnowledgeIndexingPipeline pipeline = new KnowledgeIndexingPipeline(
            repository,
            chunker,
            embeddingPort,
            parserRegistry
    );

    @Test
    void skipsIndexingWhenVersionChunksAlreadyExist() {
        KnowledgeDocument document = document();
        KnowledgeDocumentVersion version = version("standard.md", "text/markdown", "Existing chunks");
        when(repository.hasChunks(document.documentId(), version.version())).thenReturn(true);

        pipeline.index(document, version);

        verify(repository, never()).saveChunks(any());
        verifyNoInteractions(chunker, embeddingPort, parserRegistry);
    }

    @Test
    void indexesCodeUnitsWhenMatchingParserExists() {
        KnowledgeDocument document = document();
        KnowledgeDocumentVersion version = version("payment.go", "text/x-go", "package payment");
        CodeKnowledgeParser parser = mock(CodeKnowledgeParser.class);
        when(repository.hasChunks(document.documentId(), version.version())).thenReturn(false);
        when(parserRegistry.findParser("payment.go", "text/x-go")).thenReturn(Optional.of(parser));
        when(parser.parse("payment.go", "text/x-go", "package payment")).thenReturn(List.of(new CodeKnowledgeUnit(
                "go",
                "payment.go",
                "function",
                "CreateDynamicQr",
                7,
                10,
                120,
                220,
                "func CreateDynamicQr(reference string) error { return nil }",
                Map.of("package", "payment")
        )));
        when(embeddingPort.modelName()).thenReturn("test-embedding");
        when(embeddingPort.embed(anyString())).thenReturn(List.of(0.1, 0.2, 0.3));

        pipeline.index(document, version);

        verify(chunker, never()).chunk(anyString());
        verify(repository).saveChunks(argThat(chunks ->
                chunks.size() == 1
                        && chunks.getFirst().text().contains("Symbol Name: CreateDynamicQr")
                        && chunks.getFirst().embeddingModel().equals("test-embedding")
                        && chunks.getFirst().embedding().equals(List.of(0.1, 0.2, 0.3))
        ));
    }

    @Test
    void fallsBackToGenericChunksWhenNoCodeParserMatches() {
        KnowledgeDocument document = document();
        KnowledgeDocumentVersion version = version("standard.md", "text/markdown", "SLA and reconciliation requirements");
        when(repository.hasChunks(document.documentId(), version.version())).thenReturn(false);
        when(parserRegistry.findParser("standard.md", "text/markdown")).thenReturn(Optional.empty());
        when(chunker.chunk("SLA and reconciliation requirements")).thenReturn(List.of("SLA chunk", "reconciliation chunk"));
        when(embeddingPort.modelName()).thenReturn("test-embedding");
        when(embeddingPort.embed(anyString())).thenReturn(List.of(0.4, 0.5));

        pipeline.index(document, version);

        verify(repository).saveChunks(argThat(chunks ->
                chunks.size() == 2
                        && chunks.get(0).text().equals("SLA chunk")
                        && chunks.get(1).text().equals("reconciliation chunk")
        ));
    }

    private KnowledgeDocument document() {
        UUID documentId = UUID.randomUUID();
        return new KnowledgeDocument(
                documentId,
                "payment-source",
                "Payment Source",
                "SOURCE_CODE",
                "SUPPORTING",
                List.of("Requirement Analyst Agent", "Architecture Agent"),
                List.of("REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS"),
                List.of("payment", "qris"),
                1,
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of()
        );
    }

    private KnowledgeDocumentVersion version(String fileName, String mediaType, String extractedText) {
        return new KnowledgeDocumentVersion(
                1,
                fileName,
                mediaType,
                extractedText.length(),
                "hash",
                "aegisflow-documents",
                "knowledge/test",
                "storage-version",
                extractedText,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
