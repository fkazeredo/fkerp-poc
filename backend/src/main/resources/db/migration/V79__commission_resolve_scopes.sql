-- Commission Management / Sprint 6 Slice 7: the commission:reject + commission:cancel scopes (voiding an invalid
-- commission). Held by the commercial Manager (001) and the back-office Financeiro (005) — the same approver/manager
-- profiles. Sellers (002), representatives (003), the Board/Director (004, consultation) and operações (006) cannot
-- reject or cancel -> 403.
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'commission:reject'),
    ('00000000-0000-0000-0000-000000000005', 'commission:reject'),
    ('00000000-0000-0000-0000-000000000001', 'commission:cancel'),
    ('00000000-0000-0000-0000-000000000005', 'commission:cancel');
