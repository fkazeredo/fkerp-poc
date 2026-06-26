-- Customer Management / Sprint 7 Slice 1 (CARE7-001): the customer:create scope (create/consolidate a Customer
-- Profile from a Commercial Order). Held by Operações (006, the post-sale back-office owner) and the commercial
-- Manager (001, oversight). Sellers (002), Representatives (003), the Board/Director (004) and Financeiro (005)
-- cannot create -> 403. The customer:read tier (consultation — all commercial + back-office) is seeded together
-- with the read endpoints (CARE7-002), so no scope is left gating nothing.
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000006', 'customer:create'),
    ('00000000-0000-0000-0000-000000000001', 'customer:create');
