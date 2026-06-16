-- Sprint 2 / Slice 1: commercial Opportunity created from a QUALIFIED Lead. The Opportunity is a
-- separate negotiation record; it is NOT a Proposal, Sale, Sales Order, Booking, Customer or Financial
-- record. The source Lead stays the system of record for contacts/history (referenced by lead_id) and
-- is never modified. A Lead originates at most one Opportunity (UNIQUE lead_id is the last-resort
-- guard; the service returns a friendly 409 first).
CREATE TABLE opportunities (
    id                    UUID PRIMARY KEY,
    version               BIGINT        NOT NULL DEFAULT 0,
    lead_id               UUID          NOT NULL UNIQUE REFERENCES leads (id),
    name                  VARCHAR(200)  NOT NULL,
    origin_id             UUID          NOT NULL REFERENCES origins (id),
    responsible_person_id UUID,
    main_interest         VARCHAR(500),
    product_type          VARCHAR(200),
    estimated_value       NUMERIC(15, 2) CHECK (estimated_value IS NULL OR estimated_value >= 0),
    expected_close_date   DATE,
    stage                 VARCHAR(30)   NOT NULL
        CHECK (stage IN ('NEW_OPPORTUNITY', 'DISCOVERY', 'PRODUCT_FIT', 'READY_FOR_PROPOSAL', 'LOST')),
    notes                 TEXT,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by            UUID          NOT NULL,
    updated_by            UUID          NOT NULL
);

CREATE INDEX idx_opportunities_stage ON opportunities (stage);
CREATE INDEX idx_opportunities_responsible ON opportunities (responsible_person_id);
CREATE INDEX idx_opportunities_origin ON opportunities (origin_id);

-- Operate scope: the Lead operators (manager, seller, representative) may create Opportunities.
-- Board/Marketing (consult-only) and Finance/HR/IT do not get it.
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'crm:opportunity:create'),
    ('00000000-0000-0000-0000-000000000002', 'crm:opportunity:create'),
    ('00000000-0000-0000-0000-000000000003', 'crm:opportunity:create');
