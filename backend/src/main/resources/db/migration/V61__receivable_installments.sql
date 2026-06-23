-- Financial Operations / Sprint 5 Slice 2: Receivable installments. A Receivable is split into one or more
-- installments (slices of the amount due), each with its own due date and status; the installments always sum
-- to the Receivable's total. The schedule is defined at creation. An installment is NOT a Payment, Commission
-- or Invoice; scheduling registers none of those. Installments start OPEN.
CREATE TABLE receivable_installments (
    id             UUID PRIMARY KEY,
    receivable_id  UUID           NOT NULL REFERENCES receivables (id) ON DELETE CASCADE,
    number         INTEGER        NOT NULL CHECK (number >= 1),
    amount         NUMERIC(14, 2) NOT NULL CHECK (amount >= 0),
    due_date       DATE           NOT NULL,
    status         VARCHAR(20)    NOT NULL
        CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED')),
    payment_notes  VARCHAR(2000),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ux_receivable_installment_number UNIQUE (receivable_id, number)
);

CREATE INDEX idx_receivable_installments_receivable ON receivable_installments (receivable_id);

-- Backfill: every existing Receivable graduates to the uniform model with a single full-amount installment
-- (number 1, the receivable's total and due date, its current status), so all receivables have a schedule.
INSERT INTO receivable_installments (id, receivable_id, number, amount, due_date, status, created_at)
SELECT gen_random_uuid(), r.id, 1, r.total_amount, r.due_date, r.status, now()
FROM receivables r;
