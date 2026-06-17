-- Sprint 2 / Slice 9: the Opportunity gets its own commercial loss-reason vocabulary (a fixed Sprint-2
-- enum), distinct from the Lead's contact-oriented loss reasons. Replaces the V16 FK to the shared
-- loss_reasons cadastro with an enum column + CHECK, mirroring the activity type/result enums (V18). The
-- Lead keeps using the loss_reasons cadastro (unchanged).
ALTER TABLE opportunities ADD COLUMN loss_reason VARCHAR(40)
    CHECK (loss_reason IS NULL OR loss_reason IN ('NO_BUDGET', 'NO_DECISION', 'NO_RESPONSE',
        'COMPETITOR_CHOSEN', 'PRODUCT_MISMATCH', 'PRICE_TOO_HIGH', 'TRAVEL_CANCELLED',
        'DUPLICATED_OPPORTUNITY', 'OUT_OF_PROFILE', 'OTHER'));

-- Any Opportunity already lost (the cadastro codes do not map 1:1) becomes OTHER, so the invariant holds.
UPDATE opportunities SET loss_reason = 'OTHER' WHERE loss_reason_id IS NOT NULL;

-- Swap the "lost requires a reason" invariant from the old FK column to the new enum column.
ALTER TABLE opportunities DROP CONSTRAINT chk_opportunities_lost_has_reason;
ALTER TABLE opportunities DROP COLUMN loss_reason_id;
ALTER TABLE opportunities ADD CONSTRAINT chk_opportunities_lost_has_reason
    CHECK (stage <> 'LOST' OR loss_reason IS NOT NULL);
