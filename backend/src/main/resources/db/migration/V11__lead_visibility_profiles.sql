-- Slice 8: profile-based Lead visibility. Read tiers (additive scopes):
--   crm:lead:read             -> own only (Sales Representatives)
--   crm:lead:read:unassigned  -> also the unassigned pool (Sellers, Call Center)   [NEW]
--   crm:lead:read:all         -> all (Manager, Admin, Board, Marketing)
-- Operate scopes (create/update/assign) stay orthogonal; consult-only = a read tier with none of
-- them; no crm scope at all = no access (Finance/HR/IT).

-- The existing inside-seller seed keeps own + unassigned (preserves the self-claim flow).
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000002', 'crm:lead:read:unassigned');

-- Dev seed users (DEV ONLY) for the new profiles. BCrypt of '<username>123'.
INSERT INTO users (id, username, password_hash, active) VALUES
    ('00000000-0000-0000-0000-000000000003', 'representante',
     '$2a$10$VLhcUB1WGomkAPRUzPMYVuXfzzCTdWljxqYAHLHlp7y5ULGMYi0We', TRUE),
    ('00000000-0000-0000-0000-000000000004', 'diretor',
     '$2a$10$qoW.TB8.s1Yab36zVGYnOuq7enCzR.G8OiIkJQw.aMYFBsFCirm7O', TRUE),
    ('00000000-0000-0000-0000-000000000005', 'financeiro',
     '$2a$10$oC.a9tK5mVD2n3jdXgFyAOuQCci0uk4UCryqVfshLY6DZzMa/D7E6', TRUE);

-- Sales Representative: own only + operate (no read:unassigned, no read:all).
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000003', 'crm:lead:read'),
    ('00000000-0000-0000-0000-000000000003', 'crm:lead:create'),
    ('00000000-0000-0000-0000-000000000003', 'crm:lead:update');

-- Board/Director: sees all, consultation only (no operate scopes).
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000004', 'crm:lead:read:all');

-- Finance/HR/IT: no crm scopes at all -> 403 on every Lead endpoint (no rows for 'financeiro').
