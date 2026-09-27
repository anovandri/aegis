package com.aegisflow.api.agent;

import java.util.List;

public record BrdIntakeAnalysis(
        String status,
        double confidence,
        double completenessScore,
        String summary,
        List<String> businessObjectives,
        List<String> actors,
        List<String> functionalRequirements,
        List<String> nonFunctionalRequirements,
        List<String> businessRules,
        List<String> assumptions,
        List<String> dependencies,
        List<String> missingInformation
) {
}
