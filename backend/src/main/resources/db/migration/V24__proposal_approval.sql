-- Sprint 3 / Slice 7: internal Proposal approval / rejection. A Proposal under review (READY_FOR_REVIEW) can
-- be approved (→ APPROVED) or rejected (→ REJECTED) by an authorized approver. Approval/rejection record the
-- who/when in the status-change history (V23); a rejection also keeps the structured reason + an optional
-- note here (the "why"). Rejecting does NOT send the Proposal to the client and creates no Sale, Order,
-- Booking, Financial or Commission data. REJECTED is terminal (it frees the Opportunity for a new Proposal).
ALTER TABLE proposals
    ADD COLUMN rejection_reason VARCHAR(40),
    ADD COLUMN rejection_note   TEXT;

ALTER TABLE proposals
    ADD CONSTRAINT ck_proposal_rejection_reason CHECK (
        rejection_reason IS NULL OR rejection_reason IN (
            'PRICE_TOO_HIGH', 'DISCOUNT_OUT_OF_POLICY', 'INCOMPLETE_INFORMATION', 'TERMS_NOT_ACCEPTABLE',
            'VALIDITY_TOO_SHORT', 'DUPLICATE', 'OTHER'));

-- Approval is a new, separate authority (sales:proposal:approve), held by the Manager profile (owner
-- decision) — distinct from create/update so sellers/representatives cannot approve (their own) Proposals.
--   manager (001): + approve
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'sales:proposal:approve');
