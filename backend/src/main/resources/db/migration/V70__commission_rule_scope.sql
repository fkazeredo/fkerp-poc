-- Commission Management / Sprint 6 Slice 1: commission-rule management scope. Managing commission rules is for the
-- commercial Manager (001) and the back-office Financeiro (005); other profiles (sellers, representatives,
-- Board/Director, operations) have no commission scope yet -> 403 on the rule endpoints.
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'commission:rule:manage'),
    ('00000000-0000-0000-0000-000000000005', 'commission:rule:manage');
