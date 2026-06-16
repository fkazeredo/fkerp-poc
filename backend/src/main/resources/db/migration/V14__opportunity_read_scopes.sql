-- Sprint 2 / Slice 2: operational Opportunity listing. Read tiers mirror the Lead model (§10),
-- additive scopes:
--   crm:opportunity:read             -> own only (Sales Representatives)
--   crm:opportunity:read:unassigned  -> also the unassigned pool (Sellers, Call Center)
--   crm:opportunity:read:all         -> all (Manager, Admin, Board, Marketing)
-- The OpportunityAccessPolicy narrows WHICH Opportunities are visible; any read tier passes the GET
-- security gate. Operate scope (crm:opportunity:create) was seeded in V13 and is orthogonal.
-- Finance/HR/IT keep no crm scope at all -> 403 on every Opportunity endpoint.

INSERT INTO user_scopes (user_id, scope) VALUES
    -- comercial (manager): every Opportunity.
    ('00000000-0000-0000-0000-000000000001', 'crm:opportunity:read:all'),
    -- vendedor (seller): own + the unassigned pool.
    ('00000000-0000-0000-0000-000000000002', 'crm:opportunity:read'),
    ('00000000-0000-0000-0000-000000000002', 'crm:opportunity:read:unassigned'),
    -- representante: own only.
    ('00000000-0000-0000-0000-000000000003', 'crm:opportunity:read'),
    -- diretor (board): consults every Opportunity (no operate scope).
    ('00000000-0000-0000-0000-000000000004', 'crm:opportunity:read:all');
