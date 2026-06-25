-- Commission Management / Sprint 6 Slice 2: scopes for generating and reading an Expected Commission. Generating
-- (commission:create) is for the commercial Manager (001) and the back-office Financeiro (005) — both already hold
-- sales:order:read:all, so they can see the source Order. Reading a commission (commission:read) is granted to those
-- two plus the Board/Director (004) for consultation. Sellers, representatives, operations and HR/IT have no
-- commission scope yet -> 403 on the commission endpoints.
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'commission:create'),
    ('00000000-0000-0000-0000-000000000005', 'commission:create'),
    ('00000000-0000-0000-0000-000000000001', 'commission:read'),
    ('00000000-0000-0000-0000-000000000004', 'commission:read'),
    ('00000000-0000-0000-0000-000000000005', 'commission:read');
