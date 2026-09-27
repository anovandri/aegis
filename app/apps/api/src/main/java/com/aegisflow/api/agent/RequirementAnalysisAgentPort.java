package com.aegisflow.api.agent;

import com.aegisflow.api.ports.KnowledgeCitation;

import java.util.List;
import java.util.UUID;

public interface RequirementAnalysisAgentPort {
    RequirementAnalysis analyze(
            UUID projectId,
            String brdText,
            BrdIntakeAnalysis brdIntakeAnalysis,
            RequirementChecklist checklist,
            List<KnowledgeCitation> knowledgeCitations
    );

    default String modelName() {
        return "unknown";
    }
}
