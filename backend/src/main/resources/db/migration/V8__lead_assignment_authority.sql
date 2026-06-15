-- Slice 4: assignment authority. Grant the dev manager the full assign scope, and add a
-- sales-representative dev user WITHOUT assign authority so the "a rep may only self-claim" rule is
-- exercisable in the running app and in tests.

INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'crm:lead:assign');

-- Dev representative user (DEV ONLY): username 'vendedor', password 'vendedor123' (BCrypt).
INSERT INTO users (id, username, password_hash, active)
VALUES ('00000000-0000-0000-0000-000000000002',
        'vendedor',
        '$2a$10$V2iYRY8Ou1stavyC7yNUWevbzxQw3RhfwYNyEQSadjhzkgdyCSgUC',
        TRUE);

INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000002', 'crm:lead:read'),
    ('00000000-0000-0000-0000-000000000002', 'crm:lead:create'),
    ('00000000-0000-0000-0000-000000000002', 'crm:lead:update');
