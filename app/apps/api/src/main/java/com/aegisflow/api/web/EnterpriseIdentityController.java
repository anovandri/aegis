package com.aegisflow.api.web;

import com.aegisflow.api.application.identity.AuthAuditEvent;
import com.aegisflow.api.application.identity.EnterpriseIdentityService;
import com.aegisflow.api.application.identity.IdentityProviderConfig;
import com.aegisflow.api.application.identity.RoleMappingRule;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class EnterpriseIdentityController {
    private final EnterpriseIdentityService enterpriseIdentityService;

    public EnterpriseIdentityController(EnterpriseIdentityService enterpriseIdentityService) {
        this.enterpriseIdentityService = enterpriseIdentityService;
    }

    @GetMapping("/identity-providers")
    List<IdentityProviderResponse> identityProviders() {
        return enterpriseIdentityService.listProviders().stream()
                .map(IdentityProviderResponse::from)
                .toList();
    }

    @GetMapping("/role-mapping-rules")
    @PreAuthorize("hasRole('ADMIN')")
    List<RoleMappingRuleResponse> roleMappingRules() {
        return enterpriseIdentityService.listRoleMappings().stream()
                .map(RoleMappingRuleResponse::from)
                .toList();
    }

    @GetMapping("/audit-events")
    @PreAuthorize("hasRole('ADMIN')")
    List<AuthAuditEventResponse> auditEvents(@RequestParam(defaultValue = "50") int limit) {
        return enterpriseIdentityService.listAuditEvents(limit).stream()
                .map(AuthAuditEventResponse::from)
                .toList();
    }

    record IdentityProviderResponse(
            String providerId,
            String displayName,
            String providerType,
            String authorizationPath,
            String issuerUri,
            boolean enabled,
            List<String> allowedEmailDomains,
            List<String> allowedOrganizations,
            String defaultAuthority
    ) {
        static IdentityProviderResponse from(IdentityProviderConfig provider) {
            return new IdentityProviderResponse(
                    provider.providerId(),
                    provider.displayName(),
                    provider.providerType(),
                    provider.authorizationPath(),
                    provider.issuerUri(),
                    provider.enabled(),
                    provider.allowedEmailDomains(),
                    provider.allowedOrganizations(),
                    provider.defaultAuthority()
            );
        }
    }

    record RoleMappingRuleResponse(
            String ruleId,
            String providerId,
            String claimName,
            String claimValue,
            String authority,
            int priority,
            boolean enabled
    ) {
        static RoleMappingRuleResponse from(RoleMappingRule rule) {
            return new RoleMappingRuleResponse(
                    rule.ruleId().toString(),
                    rule.providerId(),
                    rule.claimName(),
                    rule.claimValue(),
                    rule.authority(),
                    rule.priority(),
                    rule.enabled()
            );
        }
    }

    record AuthAuditEventResponse(
            String eventId,
            String username,
            String providerId,
            String eventType,
            String message,
            String occurredAt
    ) {
        static AuthAuditEventResponse from(AuthAuditEvent event) {
            return new AuthAuditEventResponse(
                    event.eventId().toString(),
                    event.username(),
                    event.providerId(),
                    event.eventType(),
                    event.message(),
                    event.occurredAt().toString()
            );
        }
    }
}
