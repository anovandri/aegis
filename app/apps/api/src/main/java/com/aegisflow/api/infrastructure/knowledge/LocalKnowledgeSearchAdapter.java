package com.aegisflow.api.infrastructure.knowledge;

import com.aegisflow.api.agent.BrdIntakeAnalysis;
import com.aegisflow.api.domain.WorkflowState;
import com.aegisflow.api.ports.KnowledgeCitation;
import com.aegisflow.api.ports.KnowledgeSearchPort;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class LocalKnowledgeSearchAdapter implements KnowledgeSearchPort {
    private static final String REQUIREMENT_ANALYST_AGENT = "Requirement Analyst Agent";
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeEmbeddingPort knowledgeEmbeddingPort;

    public LocalKnowledgeSearchAdapter(
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeEmbeddingPort knowledgeEmbeddingPort
    ) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeEmbeddingPort = knowledgeEmbeddingPort;
    }

    @Override
    public List<KnowledgeCitation> retrieveAllowedEvidence(UUID projectId, WorkflowState workflowState, String agentName) {
        return knowledgeDocumentRepository.findLatestChunksForAgentAndState(agentName, workflowState.name()).stream()
                .limit(5)
                .map(chunk -> toCitation(chunk, "Allowed by workflow state and agent policy"))
                .toList();
    }

    @Override
    public List<KnowledgeCitation> retrieveRequirementAnalysisEvidence(
            UUID projectId,
            String brdText,
            BrdIntakeAnalysis brdIntakeAnalysis
    ) {
        String query = buildQuery(brdText, brdIntakeAnalysis);
        List<Double> queryEmbedding = knowledgeEmbeddingPort.embed(query);
        return knowledgeDocumentRepository
                .findLatestChunksForAgentAndState(REQUIREMENT_ANALYST_AGENT, WorkflowState.REQUIREMENT_ANALYSIS.name())
                .stream()
                .map(chunk -> new ScoredKnowledgeChunk(chunk, score(chunk, query, queryEmbedding)))
                .filter(scored -> scored.score() > 0.05d)
                .sorted(Comparator.comparingDouble(ScoredKnowledgeChunk::score).reversed())
                .limit(5)
                .map(scored -> toCitation(
                        scored.chunk(),
                        "Matched requirement-analysis embedding query with score %.3f".formatted(scored.score())
                ))
                .toList();
    }

    private String buildQuery(String brdText, BrdIntakeAnalysis analysis) {
        return Stream.concat(
                        Stream.of(brdText),
                        Stream.concat(
                                analysis.functionalRequirements().stream(),
                                Stream.concat(analysis.nonFunctionalRequirements().stream(), analysis.missingInformation().stream())
                        )
                )
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(Locale.ROOT);
    }

    private double score(KnowledgeChunk chunk, String query, List<Double> queryEmbedding) {
        double score = cosine(queryEmbedding, chunk.embedding());
        for (String tag : chunk.tags()) {
            if (query.contains(tag.toLowerCase(Locale.ROOT))) {
                score += 0.08d;
            }
        }
        for (String token : chunk.sourceTitle().toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!token.isBlank() && query.contains(token)) {
                score += 0.03d;
            }
        }
        for (String token : query.split("[^a-z0-9]+")) {
            if (token.length() > 4 && chunk.text().toLowerCase(Locale.ROOT).contains(token)) {
                score += 0.01d;
            }
        }
        return score;
    }

    private KnowledgeCitation toCitation(KnowledgeChunk chunk, String reason) {
        return new KnowledgeCitation(
                chunk.sourceId(),
                chunk.sourceTitle(),
                chunk.sourceType(),
                chunk.authority(),
                chunk.text(),
                "%s; chunk=%s@v%d:%d; embeddingModel=%s".formatted(
                        reason,
                        chunk.documentId(),
                        chunk.documentVersion(),
                        chunk.chunkIndex(),
                        chunk.embeddingModel()
                )
        );
    }

    private double cosine(List<Double> left, List<Double> right) {
        if (left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return 0.0d;
        }
        double dot = 0.0d;
        double leftMagnitude = 0.0d;
        double rightMagnitude = 0.0d;
        for (int index = 0; index < left.size(); index++) {
            dot += left.get(index) * right.get(index);
            leftMagnitude += left.get(index) * left.get(index);
            rightMagnitude += right.get(index) * right.get(index);
        }
        if (leftMagnitude == 0.0d || rightMagnitude == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private record ScoredKnowledgeChunk(KnowledgeChunk chunk, double score) {
    }
}
