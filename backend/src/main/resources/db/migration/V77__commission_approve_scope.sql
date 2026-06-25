-- Commission Management / Sprint 6 Slice 6: the commission:approve scope (approving an eligible commission). The
-- commercial Manager (001) and the back-office Financeiro (005) approve; with self-approval blocked, finance can
-- approve the manager's own commissions and the manager can approve sellers'/representatives'. Sellers (002),
-- representatives (003), the Board/Director (004) and operações (006) cannot approve -> 403.
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'commission:approve'),
    ('00000000-0000-0000-0000-000000000005', 'commission:approve');
