package com.aegisflow.api.ports;

import com.aegisflow.api.agent.BrdIntakeAnalysis;
import com.aegisflow.api.domain.WorkflowState;

import java.util.List;
import java.util.UUID;

public interface KnowledgeSearchPort {
    List<KnowledgeCitation> retrieveAllowedEvidence(UUID projectId, WorkflowState workflowState, String agentName);

    List<KnowledgeCitation> retrieveRequirementAnalysisEvidence(
            UUID projectId,
            String brdText,
            BrdIntakeAnalysis brdIntakeAnalysis
    );
}
