CREATE TABLE identity_provider_configs (
    provider_id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    provider_type VARCHAR(40) NOT NULL,
    client_registration_id VARCHAR(64) NOT NULL,
    authorization_path VARCHAR(200) NOT NULL,
    issuer_uri VARCHAR(500),
    enabled BOOLEAN NOT NULL,
    allowed_email_domains TEXT,
    allowed_organizations TEXT,
    default_authority VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE external_identities (
    identity_id UUID PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL,
    username VARCHAR(100) NOT NULL,
    external_subject VARCHAR(255) NOT NULL,
    email VARCHAR(320),
    display_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_external_identity_provider
        FOREIGN KEY (provider_id)
        REFERENCES identity_provider_configs (provider_id),
    CONSTRAINT fk_external_identity_user
        FOREIGN KEY (username)
        REFERENCES users (username)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX ix_external_identities_provider_subject
    ON external_identities (provider_id, external_subject);

CREATE UNIQUE INDEX ix_external_identities_provider_email
    ON external_identities (provider_id, email);

CREATE TABLE role_mapping_rules (
    rule_id UUID PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL,
    claim_name VARCHAR(120) NOT NULL,
    claim_value VARCHAR(255) NOT NULL,
    authority VARCHAR(100) NOT NULL,
    priority INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_role_mapping_provider
        FOREIGN KEY (provider_id)
        REFERENCES identity_provider_configs (provider_id)
);

CREATE INDEX ix_role_mapping_rules_provider
    ON role_mapping_rules (provider_id, enabled, priority);

CREATE TABLE auth_audit_events (
    event_id UUID PRIMARY KEY,
    username VARCHAR(100),
    provider_id VARCHAR(64),
    event_type VARCHAR(80) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    occurred_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_auth_audit_events_occurred_at
    ON auth_audit_events (occurred_at);

INSERT INTO identity_provider_configs (
    provider_id,
    display_name,
    provider_type,
    client_registration_id,
    authorization_path,
    issuer_uri,
    enabled,
    allowed_email_domains,
    allowed_organizations,
    default_authority,
    created_at,
    updated_at
) VALUES
    (
        'google',
        'Google Workspace',
        'OIDC',
        'google',
        '/oauth2/authorization/google',
        'https://accounts.google.com',
        FALSE,
        '',
        '',
        'ROLE_BUSINESS_USER',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'github',
        'GitHub Enterprise',
        'OAUTH2',
        'github',
        '/oauth2/authorization/github',
        NULL,
        FALSE,
        '',
        '',
        'ROLE_BUSINESS_USER',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'enterprise',
        'Enterprise SSO',
        'OIDC',
        'enterprise',
        '/oauth2/authorization/enterprise',
        NULL,
        FALSE,
        '',
        '',
        'ROLE_BUSINESS_USER',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );
