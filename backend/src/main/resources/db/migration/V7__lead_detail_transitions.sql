-- Slice 3: Lead detail + the transitions that produce its history. Optimistic-lock version,
-- qualification and loss outcomes on the lead, the assignment-history table, integrity checks,
-- and the write scope for the dev user.

ALTER TABLE leads ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE leads ADD COLUMN qualified_at       TIMESTAMPTZ;
ALTER TABLE leads ADD COLUMN qualified_by       UUID;
ALTER TABLE leads ADD COLUMN qualification_note VARCHAR(2000);

ALTER TABLE leads ADD COLUMN lost_at        TIMESTAMPTZ;
ALTER TABLE leads ADD COLUMN lost_by        UUID;
ALTER TABLE leads ADD COLUMN loss_reason_id UUID REFERENCES loss_reasons (id);
ALTER TABLE leads ADD COLUMN loss_note      VARCHAR(2000);

-- Defense in depth (§5.5): a lost lead must carry a reason; a qualified lead must carry its date.
ALTER TABLE leads ADD CONSTRAINT chk_leads_lost_has_reason
    CHECK (status <> 'LOST' OR loss_reason_id IS NOT NULL);
ALTER TABLE leads ADD CONSTRAINT chk_leads_qualified_has_date
    CHECK (status <> 'QUALIFIED' OR qualified_at IS NOT NULL);

CREATE TABLE lead_assignments (
    id                  UUID PRIMARY KEY,
    lead_id             UUID        NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    from_responsible_id UUID,
    to_responsible_id   UUID,
    assigned_by         UUID        NOT NULL,
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lead_assignments_lead ON lead_assignments (lead_id);

INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'crm:lead:update');
