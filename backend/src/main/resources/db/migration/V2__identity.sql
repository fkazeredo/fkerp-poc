-- Identity module: users + their scopes.
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_scopes (
    user_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scope   VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, scope)
);

-- Dev seed user (DEV ONLY): username 'comercial', password 'comercial123'
-- BCrypt hash of 'comercial123'. Replace/disable outside local development.
INSERT INTO users (id, username, password_hash, active)
VALUES ('00000000-0000-0000-0000-000000000001',
        'comercial',
        '$2y$10$.5Y.91OFL2lmTN4y8pZu9uuc14WCxvn8fjltI4ls30JSpPOQzkR9C',
        TRUE);

INSERT INTO user_scopes (user_id, scope)
VALUES ('00000000-0000-0000-0000-000000000001', 'crm:lead:create'),
       ('00000000-0000-0000-0000-000000000001', 'crm:reference:manage');
