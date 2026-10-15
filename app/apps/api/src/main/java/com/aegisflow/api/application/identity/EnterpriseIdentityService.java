package com.aegisflow.api.application.identity;

import com.aegisflow.api.application.UserManagementService;
import com.aegisflow.api.application.UserSummary;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class EnterpriseIdentityService {
    private static final Pattern UNSAFE_USERNAME_CHARS = Pattern.compile("[^a-zA-Z0-9._@-]");

    private final EnterpriseIdentityRepository repository;
    private final UserManagementService userManagementService;
    private final Clock clock;

    public EnterpriseIdentityService(
            EnterpriseIdentityRepository repository,
            UserManagementService userManagementService,
            Clock clock
    ) {
        this.repository = repository;
        this.userManagementService = userManagementService;
        this.clock = clock;
    }

    public List<IdentityProviderConfig> listProviders() {
        return repository.listProviders();
    }

    public Optional<IdentityProviderConfig> findProvider(String providerId) {
        return repository.findProvider(providerId);
    }

    public List<RoleMappingRule> listRoleMappings() {
        return repository.listRoleMappings();
    }

    public List<AuthAuditEvent> listAuditEvents(int limit) {
        return repository.listAuditEvents(limit);
    }

    public EnterpriseAuthenticatedIdentity resolveExternalIdentity(
            String providerId,
            String externalSubject,
            String email,
            String displayName,
            Map<String, Object> claims
    ) {
        Instant now = Instant.now(clock);
        IdentityProviderConfig provider = repository.findProvider(providerId)
                .orElseThrow(() -> new EnterpriseIdentityException("Unknown identity provider: " + providerId));

        try {
            if (!provider.enabled()) {
                throw new EnterpriseIdentityException("Identity provider is disabled: " + providerId);
            }
            if (externalSubject == null || externalSubject.isBlank()) {
                throw new EnterpriseIdentityException("External subject is required");
            }

            enforceEmailDomain(provider, email);
            enforceOrganizationAllowlist(provider, claims);

            String username = internalUsername(provider.providerId(), externalSubject, email);
            List<String> authorities = resolveAuthorities(provider, claims);
            UserSummary user = userManagementService.ensureExternalUser(username, authorities, true);
            repository.saveExternalIdentity(
                    provider.providerId(),
                    user.username(),
                    externalSubject,
                    normalizeEmail(email),
                    displayName,
                    now
            );
            repository.saveAudit(
                    user.username(),
                    provider.providerId(),
                    "LOGIN_SUCCESS",
                    "External identity resolved through %s".formatted(provider.displayName()),
                    now
            );
            return new EnterpriseAuthenticatedIdentity(
                    user.username(),
                    provider.providerId(),
                    externalSubject,
                    normalizeEmail(email),
                    user.authorities()
            );
        } catch (EnterpriseIdentityException exception) {
            repository.saveAudit(
                    normalizeEmail(email),
                    provider.providerId(),
                    "LOGIN_FAILURE",
                    exception.getMessage(),
                    now
            );
            throw exception;
        }
    }

    private void enforceEmailDomain(IdentityProviderConfig provider, String email) {
        if (provider.allowedEmailDomains().isEmpty()) {
            return;
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || !normalizedEmail.contains("@")) {
            throw new EnterpriseIdentityException("Email is required for provider allowlist enforcement");
        }
        String domain = normalizedEmail.substring(normalizedEmail.indexOf('@') + 1);
        boolean allowed = provider.allowedEmailDomains().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals(domain));
        if (!allowed) {
            throw new EnterpriseIdentityException("Email domain is not allowed for provider: " + provider.providerId());
        }
    }

    private void enforceOrganizationAllowlist(IdentityProviderConfig provider, Map<String, Object> claims) {
        if (provider.allowedOrganizations().isEmpty()) {
            return;
        }
        Set<String> candidateValues = claimValues(claims, "organization", "organizations", "org", "orgs", "team", "teams");
        boolean allowed = provider.allowedOrganizations().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(candidateValues::contains);
        if (!allowed) {
            throw new EnterpriseIdentityException("Organization is not allowed for provider: " + provider.providerId());
        }
    }

    private List<String> resolveAuthorities(IdentityProviderConfig provider, Map<String, Object> claims) {
        LinkedHashSet<String> authorities = new LinkedHashSet<>();
        authorities.add(normalizeAuthority(provider.defaultAuthority()));
        for (RoleMappingRule rule : repository.listEnabledRoleMappings(provider.providerId())) {
            if (claimValues(claims, rule.claimName()).contains(rule.claimValue().toLowerCase(Locale.ROOT))) {
                authorities.add(normalizeAuthority(rule.authority()));
            }
        }
        return new ArrayList<>(authorities);
    }

    private Set<String> claimValues(Map<String, Object> claims, String... names) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (claims == null) {
            return values;
        }
        for (String name : names) {
            Object value = claims.get(name);
            if (value instanceof Collection<?> collection) {
                collection.stream()
                        .map(String::valueOf)
                        .map(valueText -> valueText.toLowerCase(Locale.ROOT))
                        .forEach(values::add);
            } else if (value != null) {
                values.add(String.valueOf(value).toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    private String internalUsername(String providerId, String externalSubject, String email) {
        String normalizedEmail = normalizeEmail(email);
        String candidate = normalizedEmail == null || normalizedEmail.isBlank()
                ? providerId + "-" + externalSubject
                : normalizedEmail;
        String safe = UNSAFE_USERNAME_CHARS.matcher(candidate).replaceAll("-");
        return safe.length() <= 100 ? safe : safe.substring(0, 100);
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeAuthority(String authority) {
        String value = authority == null || authority.isBlank() ? "ROLE_BUSINESS_USER" : authority.strip();
        return value.startsWith("ROLE_") ? value.toUpperCase(Locale.ROOT) : "ROLE_" + value.toUpperCase(Locale.ROOT);
    }
}
