-- Sprint 4 / Slice 3: operational Booking Request listing. The Booking read tiers
-- (booking:request:read / :read:unassigned / :read:all) gate the GET list/detail; BookingRequestAccessPolicy
-- then narrows WHICH requests are visible. This slice seeds only the read:all tier, for the operations user
-- (006), the commercial Manager (001) and the Board/Director (004, consultation). The own/unassigned tiers
-- are defined in code and arrive with future operator profiles. Sellers/Representatives get no booking read
-- tier (they do not see reservations). No schema change.

INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'booking:request:read:all'),
    ('00000000-0000-0000-0000-000000000006', 'booking:request:read:all'),
    ('00000000-0000-0000-0000-000000000004', 'booking:request:read:all');
