-- Sprint 2 / Slice 4: Opportunity detail consultation + minimal "mark as lost" transition.
-- The Opportunity gains its own loss outcome (reason/when/who/note), kept for history, mirroring the
-- Lead's loss fields (V7). The source Lead is never touched. A CHECK mirrors the domain invariant: a LOST
-- Opportunity must carry a loss reason (defense in depth, §5.5), like chk_leads_lost_has_reason.
ALTER TABLE opportunities ADD COLUMN lost_at        TIMESTAMPTZ;
ALTER TABLE opportunities ADD COLUMN lost_by        UUID;
ALTER TABLE opportunities ADD COLUMN loss_reason_id UUID REFERENCES loss_reasons (id);
ALTER TABLE opportunities ADD COLUMN loss_note      VARCHAR(2000);

ALTER TABLE opportunities ADD CONSTRAINT chk_opportunities_lost_has_reason
    CHECK (stage <> 'LOST' OR loss_reason_id IS NOT NULL);

-- Operation scope for the "mark as lost" transition (§10), mirroring crm:lead:update. Granted to the
-- profiles that may already create Opportunities (manager, seller, representative); always combined with
-- the visibility check (canSee). Board (consultation) and Finance/HR/IT do not get it.
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'crm:opportunity:update'),
    ('00000000-0000-0000-0000-000000000002', 'crm:opportunity:update'),
    ('00000000-0000-0000-0000-000000000003', 'crm:opportunity:update');
