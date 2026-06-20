-- Sprint 3 / Slice 9: register the client's decision on a sent Proposal. A SENT Proposal can be accepted
-- (→ ACCEPTED, with an optional client confirmation note) or rejected by the client (→ REJECTED, with a
-- required customer-rejection reason + an optional note). Both record who/when in the status-change history
-- (V23). ACCEPTED is non-terminal (the Opportunity keeps the winning Proposal; it prepares the future
-- Commercial Order) and REJECTED is terminal (it frees the Opportunity for a new Proposal). Registering the
-- decision creates NO Booking, Financial, Commission or Commercial Order data.
ALTER TABLE proposals
    ADD COLUMN acceptance_note          TEXT,
    ADD COLUMN customer_rejection_reason VARCHAR(40),
    ADD COLUMN customer_rejection_note  TEXT;

ALTER TABLE proposals
    ADD CONSTRAINT ck_proposal_customer_rejection_reason CHECK (
        customer_rejection_reason IS NULL OR customer_rejection_reason IN (
            'PRICE_TOO_HIGH', 'CHOSE_COMPETITOR', 'TRAVEL_POSTPONED', 'TRAVEL_CANCELLED', 'CHANGED_DESTINATION',
            'NO_RESPONSE', 'PRODUCT_MISMATCH', 'OTHER'));

-- No new scope: registering the client's decision reuses sales:proposal:update (the seller/manager who
-- operates the Proposal records what the client answered), already seeded for the relevant profiles.
