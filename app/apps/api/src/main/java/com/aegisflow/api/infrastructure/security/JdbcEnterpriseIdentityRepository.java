package com.aegisflow.api.infrastructure.security;

import com.aegisflow.api.application.identity.AuthAuditEvent;
import com.aegisflow.api.application.identity.EnterpriseIdentityRepository;
import com.aegisflow.api.application.identity.IdentityProviderConfig;
import com.aegisflow.api.application.identity.RoleMappingRule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcEnterpriseIdentityRepository implements EnterpriseIdentityRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<IdentityProviderConfig> providerRowMapper = (resultSet, rowNum) -> new IdentityProviderConfig(
            resultSet.getString("provider_id"),
            resultSet.getString("display_name"),
            resultSet.getString("provider_type"),
            resultSet.getString("client_registration_id"),
            resultSet.getString("authorization_path"),
            resultSet.getString("issuer_uri"),
            resultSet.getBoolean("enabled"),
            splitCsv(resultSet.getString("allowed_email_domains")),
            splitCsv(resultSet.getString("allowed_organizations")),
            resultSet.getString("default_authority")
    );

    private final RowMapper<RoleMappingRule> roleMappingRowMapper = (resultSet, rowNum) -> new RoleMappingRule(
            resultSet.getObject("rule_id", UUID.class),
            resultSet.getString("provider_id"),
            resultSet.getString("claim_name"),
            resultSet.getString("claim_value"),
            resultSet.getString("authority"),
            resultSet.getInt("priority"),
            resultSet.getBoolean("enabled")
    );

    public JdbcEnterpriseIdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<IdentityProviderConfig> listProviders() {
        return jdbcTemplate.query(
                """
                        SELECT provider_id, display_name, provider_type, client_registration_id, authorization_path,
                               issuer_uri, enabled, allowed_email_domains, allowed_organizations, default_authority
                        FROM identity_provider_configs
                        ORDER BY provider_id
                        """,
                providerRowMapper
        );
    }

    @Override
    public Optional<IdentityProviderConfig> findProvider(String providerId) {
        return jdbcTemplate.query(
                """
                        SELECT provider_id, display_name, provider_type, client_registration_id, authorization_path,
                               issuer_uri, enabled, allowed_email_domains, allowed_organizations, default_authority
                        FROM identity_provider_configs
                        WHERE provider_id = ?
                        """,
                providerRowMapper,
                providerId
        ).stream().findFirst();
    }

    @Override
    public List<RoleMappingRule> listRoleMappings() {
        return jdbcTemplate.query(
                """
                        SELECT rule_id, provider_id, claim_name, claim_value, authority, priority, enabled
                        FROM role_mapping_rules
                        ORDER BY provider_id, priority, authority
                        """,
                roleMappingRowMapper
        );
    }

    @Override
    public List<RoleMappingRule> listEnabledRoleMappings(String providerId) {
        return jdbcTemplate.query(
                """
                        SELECT rule_id, provider_id, claim_name, claim_value, authority, priority, enabled
                        FROM role_mapping_rules
                        WHERE provider_id = ? AND enabled = TRUE
                        ORDER BY priority, authority
                        """,
                roleMappingRowMapper,
                providerId
        );
    }

    @Override
    public void saveExternalIdentity(
            String providerId,
            String username,
            String externalSubject,
            String email,
            String displayName,
            Instant occurredAt
    ) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM external_identities WHERE provider_id = ? AND external_subject = ?",
                Integer.class,
                providerId,
                externalSubject
        );
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    """
                            UPDATE external_identities
                            SET username = ?, email = ?, display_name = ?, updated_at = ?, last_login_at = ?
                            WHERE provider_id = ? AND external_subject = ?
                            """,
                    username,
                    email,
                    displayName,
                    Timestamp.from(occurredAt),
                    Timestamp.from(occurredAt),
                    providerId,
                    externalSubject
            );
            return;
        }

        jdbcTemplate.update(
                """
                        INSERT INTO external_identities (
                            identity_id, provider_id, username, external_subject, email, display_name,
                            created_at, updated_at, last_login_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                providerId,
                username,
                externalSubject,
                email,
                displayName,
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt)
        );
    }

    @Override
    public void saveAudit(String username, String providerId, String eventType, String message, Instant occurredAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO auth_audit_events (
                            event_id, username, provider_id, event_type, message, occurred_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                username,
                providerId,
                eventType,
                message,
                Timestamp.from(occurredAt)
        );
    }

    @Override
    public List<AuthAuditEvent> listAuditEvents(int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query(
                """
                        SELECT event_id, username, provider_id, event_type, message, occurred_at
                        FROM auth_audit_events
                        ORDER BY occurred_at DESC
                        LIMIT ?
                        """,
                (resultSet, rowNum) -> new AuthAuditEvent(
                        resultSet.getObject("event_id", UUID.class),
                        resultSet.getString("username"),
                        resultSet.getString("provider_id"),
                        resultSet.getString("event_type"),
                        resultSet.getString("message"),
                        resultSet.getTimestamp("occurred_at").toInstant()
                ),
                cappedLimit
        );
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
