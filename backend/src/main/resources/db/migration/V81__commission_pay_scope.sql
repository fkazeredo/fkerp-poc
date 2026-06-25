-- Commission Management / Sprint 6 Slice 8: the commission:pay scope (registering a commission payment). Held by the
-- commercial Manager (001) and the back-office Financeiro (005) — the same financial/commission managers who approve.
-- Sellers (002), representatives (003), the Board/Director (004, consultation) and operações (006) cannot pay -> 403.
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'commission:pay'),
    ('00000000-0000-0000-0000-000000000005', 'commission:pay');
