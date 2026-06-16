-- Sprint 2 / Slice 5: minimum commercial pipeline. The Opportunity gains a stage-movement history
-- (part of the aggregate), mirroring the Lead's assignment history (V7). Every transition between active
-- stages — and the move to LOST (via the lose action) — is recorded for the record. No new scope:
-- the transitions reuse crm:opportunity:update (seeded in V16).
CREATE TABLE opportunity_stage_changes (
    id             UUID PRIMARY KEY,
    opportunity_id UUID        NOT NULL REFERENCES opportunities (id),
    from_stage     VARCHAR(30) NOT NULL
        CHECK (from_stage IN ('NEW_OPPORTUNITY', 'DISCOVERY', 'PRODUCT_FIT', 'READY_FOR_PROPOSAL', 'LOST')),
    to_stage       VARCHAR(30) NOT NULL
        CHECK (to_stage IN ('NEW_OPPORTUNITY', 'DISCOVERY', 'PRODUCT_FIT', 'READY_FOR_PROPOSAL', 'LOST')),
    changed_by     UUID        NOT NULL,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_opportunity_stage_changes_opportunity ON opportunity_stage_changes (opportunity_id);
