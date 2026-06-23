-- Turn the Booking attempt-type / attempt-result / failure-reason enums into managed cadastros and rewire the
-- referencing columns to FKs (expand phase: the former enum columns are kept, now unmapped/dead, and dropped in
-- a later contract migration). The inline CHECK constraints (auto-named <table>_<column>_check) are dropped
-- since the editable cadastro is now the source of truth (the service validates the code is active).

CREATE TABLE booking_attempt_types (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, label VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE booking_attempt_results (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, label VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE booking_failure_reasons (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, label VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO booking_attempt_types (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'EXTERNAL_SYSTEM_ACCESS', 'Acesso a sistema externo', 1),
    (gen_random_uuid(), 'SUPPLIER_PHONE_CONTACT', 'Contato telefônico com fornecedor', 2),
    (gen_random_uuid(), 'SUPPLIER_EMAIL_CONTACT', 'Contato por e-mail com fornecedor', 3),
    (gen_random_uuid(), 'INTERNAL_VERIFICATION', 'Verificação interna', 4),
    (gen_random_uuid(), 'MANUAL_AVAILABILITY_CHECK', 'Verificação manual de disponibilidade', 5),
    (gen_random_uuid(), 'OTHER', 'Outro', 6);

INSERT INTO booking_attempt_results (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'STARTED', 'Iniciado', 1),
    (gen_random_uuid(), 'WAITING_FOR_SUPPLIER', 'Aguardando fornecedor', 2),
    (gen_random_uuid(), 'WAITING_FOR_INTERNAL_INFO', 'Aguardando informação interna', 3),
    (gen_random_uuid(), 'AVAILABILITY_FOUND', 'Disponibilidade encontrada', 4),
    (gen_random_uuid(), 'AVAILABILITY_NOT_FOUND', 'Sem disponibilidade', 5),
    (gen_random_uuid(), 'NEEDS_RETRY', 'Precisa nova tentativa', 6),
    (gen_random_uuid(), 'FAILED', 'Falhou', 7),
    (gen_random_uuid(), 'OTHER', 'Outro', 8);

INSERT INTO booking_failure_reasons (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'NO_AVAILABILITY', 'Sem disponibilidade', 1),
    (gen_random_uuid(), 'SUPPLIER_UNAVAILABLE', 'Fornecedor indisponível', 2),
    (gen_random_uuid(), 'INVALID_COMMERCIAL_DATA', 'Dados comerciais inválidos', 3),
    (gen_random_uuid(), 'MISSING_TRAVELER_DATA', 'Dados do viajante ausentes', 4),
    (gen_random_uuid(), 'EXTERNAL_SYSTEM_UNAVAILABLE', 'Sistema externo indisponível', 5),
    (gen_random_uuid(), 'PRICE_CHANGED', 'Preço alterado', 6),
    (gen_random_uuid(), 'MANUAL_OPERATION_ERROR', 'Erro de operação manual', 7),
    (gen_random_uuid(), 'OUT_OF_POLICY', 'Fora da política', 8),
    (gen_random_uuid(), 'OTHER', 'Outro', 9);

-- booking_attempts.type / .result → FKs (required).
ALTER TABLE booking_attempts ADD COLUMN type_id UUID REFERENCES booking_attempt_types (id);
ALTER TABLE booking_attempts ADD COLUMN result_id UUID REFERENCES booking_attempt_results (id);
UPDATE booking_attempts a SET type_id = (SELECT t.id FROM booking_attempt_types t WHERE t.code = a.type);
UPDATE booking_attempts a SET result_id = (SELECT r.id FROM booking_attempt_results r WHERE r.code = a.result);
ALTER TABLE booking_attempts ALTER COLUMN type_id SET NOT NULL;
ALTER TABLE booking_attempts ALTER COLUMN result_id SET NOT NULL;
ALTER TABLE booking_attempts DROP CONSTRAINT IF EXISTS booking_attempts_type_check;
ALTER TABLE booking_attempts DROP CONSTRAINT IF EXISTS booking_attempts_result_check;
ALTER TABLE booking_attempts ALTER COLUMN type DROP NOT NULL;
ALTER TABLE booking_attempts ALTER COLUMN result DROP NOT NULL;
CREATE INDEX idx_booking_attempts_type ON booking_attempts (type_id);
CREATE INDEX idx_booking_attempts_result ON booking_attempts (result_id);

-- booking_items.failure_reason → FK (optional, set only when an item is failed).
ALTER TABLE booking_items ADD COLUMN failure_reason_id UUID REFERENCES booking_failure_reasons (id);
UPDATE booking_items i SET failure_reason_id =
    (SELECT r.id FROM booking_failure_reasons r WHERE r.code = i.failure_reason)
WHERE i.failure_reason IS NOT NULL;
ALTER TABLE booking_items DROP CONSTRAINT IF EXISTS booking_items_failure_reason_check;
CREATE INDEX idx_booking_items_failure_reason ON booking_items (failure_reason_id);
