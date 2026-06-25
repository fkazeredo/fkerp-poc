-- Commission Management / Sprint 6 Slice 7: reject or cancel a Commission. A shared, admin-editable cadastro of
-- reject/cancel reasons, plus the resolution evidence (reason + optional note + who/when) on the commission. The
-- terminal status (REJECTED/CANCELLED) distinguishes which action voided it; voiding touches no Order/Receivable.
CREATE TABLE commission_resolution_reasons (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, label VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO commission_resolution_reasons (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'INCORRECT_RESPONSIBLE', 'Responsável incorreto', 1),
    (gen_random_uuid(), 'INCORRECT_RULE', 'Regra de comissão incorreta', 2),
    (gen_random_uuid(), 'ORDER_CORRECTION_NEEDED', 'Correção necessária no pedido', 3),
    (gen_random_uuid(), 'RECEIVABLE_PAYMENT_ISSUE', 'Problema na conta a receber / pagamento', 4),
    (gen_random_uuid(), 'DUPLICATE_COMMISSION', 'Comissão duplicada', 5),
    (gen_random_uuid(), 'BUSINESS_EXCEPTION', 'Exceção de negócio', 6),
    (gen_random_uuid(), 'OTHER', 'Outro', 7);

-- The resolution block on the commission (one shared set for reject and cancel; null while still active).
ALTER TABLE commissions
    ADD COLUMN resolution_reason_id UUID REFERENCES commission_resolution_reasons (id),
    ADD COLUMN resolution_note      VARCHAR(2000),
    ADD COLUMN resolved_by          UUID,
    ADD COLUMN resolved_at          TIMESTAMPTZ;
