package com.aegisflow.api.infrastructure.knowledge;

import java.util.List;

public record KnowledgeSourceEligibility(
        boolean eligible,
        String status,
        String title,
        String detail,
        List<String> reasons
) {
    public KnowledgeSourceEligibility {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
