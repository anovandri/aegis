CREATE TABLE users (
    username VARCHAR(100) PRIMARY KEY,
    password VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL
);

CREATE TABLE authorities (
    username VARCHAR(100) NOT NULL,
    authority VARCHAR(100) NOT NULL,
    CONSTRAINT fk_authorities_users
        FOREIGN KEY (username)
        REFERENCES users (username)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX ix_auth_username
    ON authorities (username, authority);

INSERT INTO users (username, password, enabled)
VALUES (
    'admin',
    '{bcrypt}$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO/BTk76klW',
    TRUE
);

INSERT INTO authorities (username, authority)
VALUES
    ('admin', 'ROLE_ADMIN'),
    ('admin', 'ROLE_TECHNICAL_REVIEWER'),
    ('admin', 'ROLE_SYSTEM_ANALYST'),
    ('admin', 'ROLE_PM_APPROVER'),
    ('admin', 'ROLE_BUSINESS_USER');
