package com.aegisflow.api.agent;

import java.util.UUID;

public interface BrdIntakeAgentPort {
    BrdIntakeAnalysis analyze(UUID projectId, int brdVersion, String brdText);

    default String modelName() {
        return "unknown";
    }
}
