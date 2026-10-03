package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class KnowledgeIndexingPipeline {
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeChunker knowledgeChunker;
    private final KnowledgeEmbeddingPort knowledgeEmbeddingPort;
    private final CodeKnowledgeParserRegistry codeKnowledgeParserRegistry;

    public KnowledgeIndexingPipeline(
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeChunker knowledgeChunker,
            KnowledgeEmbeddingPort knowledgeEmbeddingPort,
            CodeKnowledgeParserRegistry codeKnowledgeParserRegistry
    ) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeChunker = knowledgeChunker;
        this.knowledgeEmbeddingPort = knowledgeEmbeddingPort;
        this.codeKnowledgeParserRegistry = codeKnowledgeParserRegistry;
    }

    public void index(KnowledgeDocument document, KnowledgeDocumentVersion version) {
        if (knowledgeDocumentRepository.hasChunks(document.documentId(), version.version())) {
            return;
        }

        List<String> chunkTexts = codeKnowledgeParserRegistry
                .findParser(version.fileName(), version.mediaType())
                .map(parser -> parser.parse(version.fileName(), version.mediaType(), version.extractedText()).stream()
                        .map(CodeKnowledgeUnit::toKnowledgeText)
                        .toList())
                .filter(chunks -> !chunks.isEmpty())
                .orElseGet(() -> knowledgeChunker.chunk(version.extractedText()));

        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int index = 0; index < chunkTexts.size(); index++) {
            String chunkText = chunkTexts.get(index);
            chunks.add(new KnowledgeChunk(
                    UUID.randomUUID(),
                    document.documentId(),
                    document.sourceId(),
                    document.sourceTitle(),
                    document.sourceType(),
                    document.authority(),
                    document.allowedAgents(),
                    document.workflowStates(),
                    document.tags(),
                    version.version(),
                    index,
                    chunkText,
                    knowledgeEmbeddingPort.modelName(),
                    knowledgeEmbeddingPort.embed(chunkText)
            ));
        }
        knowledgeDocumentRepository.saveChunks(chunks);
    }
}
