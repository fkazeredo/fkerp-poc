-- Commission Management / Sprint 6 Slice 2: the Expected Commission, generated as a forecast from a commercially
-- closed Commercial Order using an active Commission Rule. It preserves the commercial origin (Order / Proposal /
-- Opportunity / Lead), the beneficiary (the Order's commercial responsible) and the applied rule + its percentage
-- snapshot; the amount is a percentage of a base (the commercial total, or the received amount when available). It is
-- a forecast (EXPECTED, not payable yet) and is NOT a Commission Payment, Accounts Payable, payroll, tax or
-- accounting entry; Commission Management reads the Order/Receivable but never owns them. At most ONE active
-- (non-rejected/non-cancelled) Commission per Order.
CREATE TABLE commissions (
    id                   UUID PRIMARY KEY,
    version              BIGINT         NOT NULL DEFAULT 0,
    commercial_order_id  UUID           NOT NULL REFERENCES commercial_orders (id),
    proposal_id          UUID           NOT NULL,
    opportunity_id       UUID           NOT NULL,
    lead_id              UUID           NOT NULL,
    beneficiary_user_id  UUID           NOT NULL REFERENCES users (id),
    rule_id              UUID           NOT NULL REFERENCES commission_rules (id),
    rule_percentage      NUMERIC(5, 2)  NOT NULL CHECK (rule_percentage > 0 AND rule_percentage <= 100),
    basis_type           VARCHAR(30)    NOT NULL
        CHECK (basis_type IN ('COMMERCIAL_AMOUNT', 'RECEIVED_AMOUNT')),
    base_amount          NUMERIC(15, 2) NOT NULL CHECK (base_amount >= 0),
    amount               NUMERIC(15, 2) NOT NULL CHECK (amount >= 0),
    status               VARCHAR(20)    NOT NULL
        CHECK (status IN ('EXPECTED', 'ELIGIBLE', 'APPROVED', 'REJECTED', 'PAID', 'CANCELLED')),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           UUID           NOT NULL,
    updated_by           UUID           NOT NULL
);

-- At most one active (non-rejected/non-cancelled) Commission per Order — the last-resort guard behind the
-- service's friendly 409. A new commission is allowed once any previous one is rejected or cancelled.
CREATE UNIQUE INDEX ux_commissions_active_order ON commissions (commercial_order_id)
    WHERE status NOT IN ('REJECTED', 'CANCELLED');

CREATE INDEX idx_commissions_beneficiary ON commissions (beneficiary_user_id);
CREATE INDEX idx_commissions_status ON commissions (status);
