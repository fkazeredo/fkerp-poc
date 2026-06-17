-- Sprint 3 / Slice 3: Proposal totals, discounts and validity. The Proposal now exposes a subtotal (sum of
-- the items' line totals) and an optional Proposal-level discount (an absolute amount or a percentage)
-- applied to the subtotal, giving the final total (never negative). It also keeps descriptive payment notes
-- (free text — NOT a Financial/Payment/Receivable record). No new scope: editing these and submitting for
-- review reuse sales:proposal:update (seeded in V20).

ALTER TABLE proposals
    ADD COLUMN subtotal       NUMERIC(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN discount_type  VARCHAR(10),
    ADD COLUMN discount_value NUMERIC(15, 2),
    ADD COLUMN payment_notes  VARCHAR(4000);

-- Existing Proposals have no Proposal-level discount yet, so the subtotal equals the current total.
UPDATE proposals SET subtotal = total;

ALTER TABLE proposals
    ADD CONSTRAINT ck_proposal_discount_type CHECK (discount_type IS NULL OR discount_type IN ('AMOUNT', 'PERCENT')),
    ADD CONSTRAINT ck_proposal_discount_pair CHECK ((discount_type IS NULL) = (discount_value IS NULL)),
    ADD CONSTRAINT ck_proposal_discount_nonneg CHECK (discount_value IS NULL OR discount_value >= 0),
    ADD CONSTRAINT ck_proposal_percent_max CHECK (discount_type <> 'PERCENT' OR discount_value <= 100),
    ADD CONSTRAINT ck_proposal_subtotal_nonneg CHECK (subtotal >= 0),
    ADD CONSTRAINT ck_proposal_total_nonneg CHECK (total >= 0);
