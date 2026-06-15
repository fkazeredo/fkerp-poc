-- Slice 2: operational Lead listing. Adds the (read-only) next-contact date, a creation-date index
-- for the period filter/default sort, and the read scopes for the dev user (regular read + the
-- manager read-all that bypasses ownership visibility).
ALTER TABLE leads ADD COLUMN next_contact_at TIMESTAMPTZ;

CREATE INDEX idx_leads_created_at ON leads (created_at);

INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'crm:lead:read'),
    ('00000000-0000-0000-0000-000000000001', 'crm:lead:read:all');
