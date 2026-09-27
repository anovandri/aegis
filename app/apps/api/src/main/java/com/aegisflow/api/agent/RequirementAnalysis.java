package com.aegisflow.api.agent;

import java.util.List;

public record RequirementAnalysis(
        String status,
        double confidence,
        double readinessScore,
        String summary,
        List<String> clarificationQuestions,
        List<String> acceptanceCriteria,
        List<String> ambiguities,
        List<String> contradictions,
        List<String> missingRequirements,
        List<String> edgeCases,
        List<String> nonFunctionalConcerns,
        List<String> securityConcerns,
        List<String> auditConcerns,
        List<String> sourceRefs
) {
}
