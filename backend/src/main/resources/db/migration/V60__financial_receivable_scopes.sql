-- Financial Operations / Sprint 5 Slice 1: Receivable scopes (financial:receivable:*). Mirrors the read-tier +
-- operation model of the other contexts.
--   financeiro (005): create + read:all (the financial user owns Receivables) + sales:order:read:all so it can
--                      see the source Commercial Order to originate the Receivable.
--   comercial/manager (001) and diretor (004): read:all (consultation only — no create).
-- Sellers/Representatives and HR/IT have no financial read tier (they do not see Receivables).
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000005', 'financial:receivable:create'),
    ('00000000-0000-0000-0000-000000000005', 'financial:receivable:read:all'),
    ('00000000-0000-0000-0000-000000000005', 'sales:order:read:all'),
    ('00000000-0000-0000-0000-000000000001', 'financial:receivable:read:all'),
    ('00000000-0000-0000-0000-000000000004', 'financial:receivable:read:all');
