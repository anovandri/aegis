package com.aegisflow.api.application.identity;

import java.util.List;

public record IdentityProviderConfig(
        String providerId,
        String displayName,
        String providerType,
        String clientRegistrationId,
        String authorizationPath,
        String issuerUri,
        boolean enabled,
        List<String> allowedEmailDomains,
        List<String> allowedOrganizations,
        String defaultAuthority
) {
    public IdentityProviderConfig {
        allowedEmailDomains = allowedEmailDomains == null ? List.of() : List.copyOf(allowedEmailDomains);
        allowedOrganizations = allowedOrganizations == null ? List.of() : List.copyOf(allowedOrganizations);
    }
}
