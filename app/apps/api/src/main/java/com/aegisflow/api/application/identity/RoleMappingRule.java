package com.aegisflow.api.application.identity;

import java.util.UUID;

public record RoleMappingRule(
        UUID ruleId,
        String providerId,
        String claimName,
        String claimValue,
        String authority,
        int priority,
        boolean enabled
) {
}
