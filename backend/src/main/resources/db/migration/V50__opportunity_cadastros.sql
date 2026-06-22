-- Turn the Opportunity activity-type / activity-result / loss-reason enums into managed cadastros
-- (reference data) and rewire the referencing columns to FKs (expand/contract). Codes match the former
-- enum constants; the pt-BR labels are now editable.

CREATE TABLE opportunity_activity_types (
    id         UUID PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    label      VARCHAR(120) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE opportunity_activity_results (
    id         UUID PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    label      VARCHAR(120) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE opportunity_loss_reasons (
    id         UUID PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    label      VARCHAR(120) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO opportunity_activity_types (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'PHONE_CALL', 'Ligação', 1),
    (gen_random_uuid(), 'WHATSAPP', 'WhatsApp', 2),
    (gen_random_uuid(), 'EMAIL', 'E-mail', 3),
    (gen_random_uuid(), 'MEETING', 'Reunião', 4),
    (gen_random_uuid(), 'INTERNAL_NOTE', 'Nota interna', 5),
    (gen_random_uuid(), 'DOCUMENT_REQUEST', 'Solicitação de documento', 6),
    (gen_random_uuid(), 'PRICE_DISCUSSION', 'Negociação de preço', 7),
    (gen_random_uuid(), 'TRAVEL_REQUIREMENT_CLARIFICATION', 'Esclarecimento de requisitos de viagem', 8),
    (gen_random_uuid(), 'OTHER', 'Outro', 9);

INSERT INTO opportunity_activity_results (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'CLIENT_ENGAGED', 'Cliente engajado', 1),
    (gen_random_uuid(), 'NEEDS_FOLLOW_UP', 'Precisa follow-up', 2),
    (gen_random_uuid(), 'WAITING_FOR_CLIENT', 'Aguardando cliente', 3),
    (gen_random_uuid(), 'WAITING_FOR_INTERNAL_INFO', 'Aguardando informação interna', 4),
    (gen_random_uuid(), 'PRODUCT_FIT_IDENTIFIED', 'Aderência identificada', 5),
    (gen_random_uuid(), 'READY_FOR_PROPOSAL', 'Pronta para proposta', 6),
    (gen_random_uuid(), 'NOT_INTERESTED', 'Não interessado', 7),
    (gen_random_uuid(), 'OTHER', 'Outro', 8);

INSERT INTO opportunity_loss_reasons (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'NO_BUDGET', 'Sem orçamento', 1),
    (gen_random_uuid(), 'NO_DECISION', 'Sem decisão', 2),
    (gen_random_uuid(), 'NO_RESPONSE', 'Sem resposta', 3),
    (gen_random_uuid(), 'COMPETITOR_CHOSEN', 'Escolheu concorrente', 4),
    (gen_random_uuid(), 'PRODUCT_MISMATCH', 'Produto não aderente', 5),
    (gen_random_uuid(), 'PRICE_TOO_HIGH', 'Preço muito alto', 6),
    (gen_random_uuid(), 'TRAVEL_CANCELLED', 'Viagem cancelada', 7),
    (gen_random_uuid(), 'DUPLICATED_OPPORTUNITY', 'Oportunidade duplicada', 8),
    (gen_random_uuid(), 'OUT_OF_PROFILE', 'Fora do perfil', 9),
    (gen_random_uuid(), 'OTHER', 'Outro', 10);

-- Rewire opportunity_activities.type / .result to FKs.
ALTER TABLE opportunity_activities ADD COLUMN type_id UUID REFERENCES opportunity_activity_types (id);
ALTER TABLE opportunity_activities ADD COLUMN result_id UUID REFERENCES opportunity_activity_results (id);

UPDATE opportunity_activities a
SET type_id = (SELECT t.id FROM opportunity_activity_types t WHERE t.code = a.type);
UPDATE opportunity_activities a
SET result_id = (SELECT r.id FROM opportunity_activity_results r WHERE r.code = a.result);

ALTER TABLE opportunity_activities ALTER COLUMN type_id SET NOT NULL;
ALTER TABLE opportunity_activities ALTER COLUMN result_id SET NOT NULL;
CREATE INDEX idx_opportunity_activities_type ON opportunity_activities (type_id);
CREATE INDEX idx_opportunity_activities_result ON opportunity_activities (result_id);
-- Expand phase: the entity now writes type_id/result_id; the former enum columns become unmapped/dead.
-- Relax their NOT NULL so entity inserts (which no longer write them) succeed; they are dropped in a later
-- contract migration once every test fixture/raw insert has stopped writing them.
ALTER TABLE opportunity_activities ALTER COLUMN type DROP NOT NULL;
ALTER TABLE opportunity_activities ALTER COLUMN result DROP NOT NULL;

-- Rewire opportunities.loss_reason to an FK (nullable — set only when the Opportunity is LOST). The former
-- enum column is kept (already nullable, now unmapped/dead) and dropped in the later contract migration.
ALTER TABLE opportunities ADD COLUMN loss_reason_id UUID REFERENCES opportunity_loss_reasons (id);

UPDATE opportunities o
SET loss_reason_id = (SELECT lr.id FROM opportunity_loss_reasons lr WHERE lr.code = o.loss_reason)
WHERE o.loss_reason IS NOT NULL;

CREATE INDEX idx_opportunities_loss_reason ON opportunities (loss_reason_id);

-- Move the "a LOST Opportunity must carry a loss reason" invariant (§5.5) from the old enum column to the FK,
-- since the entity now writes loss_reason_id.
ALTER TABLE opportunities DROP CONSTRAINT chk_opportunities_lost_has_reason;
ALTER TABLE opportunities ADD CONSTRAINT chk_opportunities_lost_has_reason
    CHECK (stage <> 'LOST' OR loss_reason_id IS NOT NULL);

-- BEFORE INSERT trigger so existing raw-SQL fixtures that still set the old loss_reason code (without the FK)
-- keep satisfying the FK CHECK; the entity (which writes loss_reason_id directly) is unaffected.
CREATE OR REPLACE FUNCTION opportunities_fill_loss_reason() RETURNS trigger AS $$
BEGIN
    IF NEW.loss_reason_id IS NULL AND NEW.loss_reason IS NOT NULL THEN
        NEW.loss_reason_id := (SELECT id FROM opportunity_loss_reasons WHERE code = NEW.loss_reason);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_opportunities_fill_loss_reason
    BEFORE INSERT ON opportunities
    FOR EACH ROW EXECUTE FUNCTION opportunities_fill_loss_reason();
