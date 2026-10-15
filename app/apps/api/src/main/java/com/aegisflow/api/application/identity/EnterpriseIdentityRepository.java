package com.aegisflow.api.application.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EnterpriseIdentityRepository {
    List<IdentityProviderConfig> listProviders();

    Optional<IdentityProviderConfig> findProvider(String providerId);

    List<RoleMappingRule> listRoleMappings();

    List<RoleMappingRule> listEnabledRoleMappings(String providerId);

    void saveExternalIdentity(
            String providerId,
            String username,
            String externalSubject,
            String email,
            String displayName,
            Instant occurredAt
    );

    void saveAudit(String username, String providerId, String eventType, String message, Instant occurredAt);

    List<AuthAuditEvent> listAuditEvents(int limit);
}
