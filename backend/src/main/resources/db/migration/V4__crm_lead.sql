-- Commercial / CRM: leads + their interactions (initial history). Origin/interaction type/result
-- reference the cadastro tables (soft-deletable; inactive values are blocked for new leads in code).
CREATE TABLE leads (
    id                    UUID PRIMARY KEY,
    name                  VARCHAR(200) NOT NULL,
    phone                 VARCHAR(30),
    whatsapp              VARCHAR(30),
    email                 VARCHAR(255),
    origin_id             UUID         NOT NULL REFERENCES origins (id),
    status                VARCHAR(20)  NOT NULL,
    responsible_person_id UUID,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            UUID         NOT NULL,
    updated_by            UUID         NOT NULL
);

CREATE INDEX idx_leads_status ON leads (status);
CREATE INDEX idx_leads_origin ON leads (origin_id);
CREATE INDEX idx_leads_responsible ON leads (responsible_person_id);

CREATE TABLE lead_interactions (
    id            UUID PRIMARY KEY,
    lead_id       UUID        NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    type_id       UUID        NOT NULL REFERENCES interaction_types (id),
    result_id     UUID        REFERENCES interaction_results (id),
    content       TEXT,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    registered_by UUID        NOT NULL
);

CREATE INDEX idx_lead_interactions_lead ON lead_interactions (lead_id);
