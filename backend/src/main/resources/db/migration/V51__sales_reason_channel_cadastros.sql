-- Turn the Proposal rejection-reason / customer-rejection-reason / sending-channel enums into managed
-- cadastros and rewire the (optional) referencing columns to FKs (expand phase: the former nullable enum
-- columns are kept, now unmapped/dead, and dropped in a later contract migration). Codes match the former
-- enum constants; the pt-BR labels are now editable.

CREATE TABLE proposal_rejection_reasons (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, label VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE customer_rejection_reasons (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, label VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE sending_channels (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, label VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO proposal_rejection_reasons (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'PRICE_TOO_HIGH', 'Preço muito alto', 1),
    (gen_random_uuid(), 'DISCOUNT_OUT_OF_POLICY', 'Desconto fora da política', 2),
    (gen_random_uuid(), 'INCOMPLETE_INFORMATION', 'Informação incompleta', 3),
    (gen_random_uuid(), 'TERMS_NOT_ACCEPTABLE', 'Termos não aceitáveis', 4),
    (gen_random_uuid(), 'VALIDITY_TOO_SHORT', 'Validade muito curta', 5),
    (gen_random_uuid(), 'DUPLICATE', 'Duplicada', 6),
    (gen_random_uuid(), 'OTHER', 'Outro', 7);

INSERT INTO customer_rejection_reasons (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'PRICE_TOO_HIGH', 'Preço muito alto', 1),
    (gen_random_uuid(), 'CHOSE_COMPETITOR', 'Escolheu concorrente', 2),
    (gen_random_uuid(), 'TRAVEL_POSTPONED', 'Viagem adiada', 3),
    (gen_random_uuid(), 'TRAVEL_CANCELLED', 'Viagem cancelada', 4),
    (gen_random_uuid(), 'CHANGED_DESTINATION', 'Mudou destino', 5),
    (gen_random_uuid(), 'NO_RESPONSE', 'Sem resposta', 6),
    (gen_random_uuid(), 'PRODUCT_MISMATCH', 'Produto não aderente', 7),
    (gen_random_uuid(), 'OTHER', 'Outro', 8);

INSERT INTO sending_channels (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'EMAIL', 'E-mail', 1),
    (gen_random_uuid(), 'WHATSAPP', 'WhatsApp', 2),
    (gen_random_uuid(), 'PHONE_PRESENTATION', 'Apresentação por telefone', 3),
    (gen_random_uuid(), 'IN_PERSON_PRESENTATION', 'Apresentação presencial', 4),
    (gen_random_uuid(), 'OTHER', 'Outro', 5);

-- Rewire the proposals' optional reason/channel columns to FKs (the old enum columns are kept, dead).
ALTER TABLE proposals ADD COLUMN rejection_reason_id UUID REFERENCES proposal_rejection_reasons (id);
ALTER TABLE proposals ADD COLUMN customer_rejection_reason_id UUID REFERENCES customer_rejection_reasons (id);
ALTER TABLE proposals ADD COLUMN sending_channel_id UUID REFERENCES sending_channels (id);

UPDATE proposals p SET rejection_reason_id =
    (SELECT r.id FROM proposal_rejection_reasons r WHERE r.code = p.rejection_reason)
WHERE p.rejection_reason IS NOT NULL;
UPDATE proposals p SET customer_rejection_reason_id =
    (SELECT r.id FROM customer_rejection_reasons r WHERE r.code = p.customer_rejection_reason)
WHERE p.customer_rejection_reason IS NOT NULL;
UPDATE proposals p SET sending_channel_id =
    (SELECT c.id FROM sending_channels c WHERE c.code = p.sending_channel)
WHERE p.sending_channel IS NOT NULL;

CREATE INDEX idx_proposals_rejection_reason ON proposals (rejection_reason_id);
CREATE INDEX idx_proposals_customer_rejection_reason ON proposals (customer_rejection_reason_id);
CREATE INDEX idx_proposals_sending_channel ON proposals (sending_channel_id);
