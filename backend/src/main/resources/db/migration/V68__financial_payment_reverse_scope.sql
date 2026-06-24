-- Financial Operations / Sprint 5 Slice 9: payment-reverse scope. Only the financial user (005) reverses payments
-- (a sensitive correction); the Manager (001) and Board/Director (004) keep read-only consultation (no
-- payment:reverse).
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000005', 'financial:payment:reverse');
