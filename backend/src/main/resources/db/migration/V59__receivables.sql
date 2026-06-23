-- Financial Operations / Sprint 5 Slice 1: Receivables. A Receivable is the amount the company has to receive
-- from a client for a closed deal whose Booking is CONFIRMED. It is created from a Commercial Order, snapshots
-- the commercial total and preserves the full commercial origin (Order / Proposal / Opportunity / Lead /
-- Customer / commercial responsible). It is NOT a Payment, Commission or Invoice; creating it registers none of
-- those. It starts OPEN. At most ONE active (non-cancelled) Receivable per Commercial Order.
CREATE TABLE receivables (
    id                              UUID PRIMARY KEY,
    version                         BIGINT         NOT NULL DEFAULT 0,
    commercial_order_id             UUID           NOT NULL REFERENCES commercial_orders (id),
    proposal_id                     UUID           NOT NULL REFERENCES proposals (id),
    opportunity_id                  UUID           NOT NULL REFERENCES opportunities (id),
    lead_id                         UUID           NOT NULL REFERENCES leads (id),
    customer_id                     UUID           NOT NULL REFERENCES customers (id),
    commercial_responsible_person_id UUID,
    financial_responsible_person_id  UUID,
    total_amount                    NUMERIC(14, 2) NOT NULL CHECK (total_amount >= 0),
    due_date                        DATE           NOT NULL,
    payment_notes                   VARCHAR(2000),
    status                          VARCHAR(20)    NOT NULL
        CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED')),
    created_at                      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by                      UUID           NOT NULL,
    updated_by                      UUID           NOT NULL
);

-- At most one active Receivable per Order (a new one is allowed once the previous is CANCELLED).
CREATE UNIQUE INDEX ux_receivables_active_per_order
    ON receivables (commercial_order_id)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_receivables_customer ON receivables (customer_id);
CREATE INDEX idx_receivables_financial_responsible ON receivables (financial_responsible_person_id);
CREATE INDEX idx_receivables_status ON receivables (status);
CREATE INDEX idx_receivables_due_date ON receivables (due_date);
