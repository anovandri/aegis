package com.aegisflow.api.application.identity;

import java.util.List;

public record EnterpriseAuthenticatedIdentity(
        String username,
        String providerId,
        String externalSubject,
        String email,
        List<String> authorities
) {
    public EnterpriseAuthenticatedIdentity {
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
    }
}
