-- Commission Management / Sprint 6 Slice 4: a two-tier commission read model for the operational list, mirroring the
-- Receivable model. commission:read = own only (the beneficiary); commission:read:all = all commissions. The
-- commercial Manager (001), the Board/Director (004) and Financeiro (005) move from the flat commission:read to
-- commission:read:all (they see everything). Sellers (vendedor 002) and representatives (representante 003) get the
-- own-only commission:read so they see ONLY commissions where they are the beneficiary. Operações (006) and HR/IT
-- have no commission read tier (no access).

-- Managers / Board / Finance: own-tier read -> read-all.
DELETE FROM user_scopes
WHERE scope = 'commission:read'
  AND user_id IN (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000005'
  );

INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'commission:read:all'),
    ('00000000-0000-0000-0000-000000000004', 'commission:read:all'),
    ('00000000-0000-0000-0000-000000000005', 'commission:read:all'),
    -- Sellers and representatives: own-only commission read.
    ('00000000-0000-0000-0000-000000000002', 'commission:read'),
    ('00000000-0000-0000-0000-000000000003', 'commission:read');
