-- Financial Operations / Sprint 5 Slice 5: Register full payment. A payment settles one installment with its
-- exact amount (full payments only in this slice); the installment becomes PAID and, when every installment is
-- paid, the Receivable becomes PAID (otherwise PARTIALLY_PAID). The paid total and the latest payment date are
-- denormalized on the Receivable. A payment is append-only history — NOT a Commission, Invoice or
-- bank-reconciliation record.

-- Denormalized payment standing on the Receivable (existing rows default to nothing paid).
ALTER TABLE receivables ADD COLUMN amount_paid NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0);
ALTER TABLE receivables ADD COLUMN last_payment_date DATE;

CREATE TABLE receivable_payments (
    id                 UUID PRIMARY KEY,
    receivable_id      UUID           NOT NULL REFERENCES receivables (id) ON DELETE CASCADE,
    installment_id     UUID           NOT NULL REFERENCES receivable_installments (id) ON DELETE CASCADE,
    amount             NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    payment_date       DATE           NOT NULL,
    payment_method_id  UUID           NOT NULL REFERENCES payment_methods (id),
    note               VARCHAR(2000),
    registered_by      UUID           NOT NULL,
    registered_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_receivable_payments_receivable ON receivable_payments (receivable_id);
CREATE INDEX idx_receivable_payments_installment ON receivable_payments (installment_id);
CREATE INDEX idx_receivable_payments_method ON receivable_payments (payment_method_id);
