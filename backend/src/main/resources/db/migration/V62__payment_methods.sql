-- Financial Operations / Sprint 5 Slice 5: Payment method cadastro (reference data, NOT an enum). How a
-- Receivable payment was received. An admin can add/rename/deactivate values without code, and no logic branches
-- on the specific value (it is a label on a Payment). Inactive values cannot be used by new payments but remain
-- for historical integrity. Seeded with the standard methods.
CREATE TABLE payment_methods (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, label VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO payment_methods (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'CASH', 'Dinheiro', 1),
    (gen_random_uuid(), 'BANK_TRANSFER', 'Transferência bancária', 2),
    (gen_random_uuid(), 'PIX', 'Pix', 3),
    (gen_random_uuid(), 'CREDIT_CARD', 'Cartão de crédito', 4),
    (gen_random_uuid(), 'DEBIT_CARD', 'Cartão de débito', 5),
    (gen_random_uuid(), 'INVOICE_PAYMENT', 'Pagamento de fatura', 6),
    (gen_random_uuid(), 'OTHER', 'Outro', 7);
