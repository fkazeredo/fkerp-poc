-- Sprint 3 / Slice 1: commercial Proposal created from a READY_FOR_PROPOSAL Opportunity. The Proposal is
-- the aggregate root of the new Sales & Proposals bounded context. It is NOT a Sale, Sales Order, Booking,
-- Customer, Financial, Payment or Commission record. The source Opportunity stays the system of record for
-- the negotiation (referenced by opportunity_id, never modified); lead_id keeps the source Lead reference
-- for traceability. A new Proposal starts as DRAFT. The full 8-state lifecycle is defined in the CHECK
-- (only DRAFT is reached in this slice; the transitions are later slices).
CREATE TABLE proposals (
    id                    UUID PRIMARY KEY,
    version               BIGINT       NOT NULL DEFAULT 0,
    opportunity_id        UUID         NOT NULL REFERENCES opportunities (id),
    lead_id               UUID         NOT NULL REFERENCES leads (id),
    responsible_person_id UUID,
    title                 VARCHAR(200) NOT NULL,
    notes                 TEXT,
    valid_until           DATE,
    commercial_terms      TEXT,
    status                VARCHAR(30)  NOT NULL
        CHECK (status IN ('DRAFT', 'READY_FOR_REVIEW', 'APPROVED', 'SENT', 'ACCEPTED',
                          'REJECTED', 'EXPIRED', 'CANCELLED')),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            UUID         NOT NULL,
    updated_by            UUID         NOT NULL
);

CREATE INDEX idx_proposals_opportunity ON proposals (opportunity_id);
CREATE INDEX idx_proposals_responsible ON proposals (responsible_person_id);
CREATE INDEX idx_proposals_status ON proposals (status);

-- At most one OPEN Proposal per Opportunity (the service returns a friendly 409 first; this partial unique
-- index is the last-resort guard). A new Proposal is allowed once the previous one is terminal-negative
-- (REJECTED / EXPIRED / CANCELLED) — so revisions are not blocked.
CREATE UNIQUE INDEX ux_proposals_active_per_opportunity
    ON proposals (opportunity_id)
    WHERE status NOT IN ('REJECTED', 'EXPIRED', 'CANCELLED');

-- Sales & Proposals scopes (sales:proposal:*), mirroring the Opportunity profile model:
--   manager (001): read:all + create + update
--   vendedor (002): read + read:unassigned + create + update
--   representante (003): read + create + update
--   diretor (004): read:all (consultation only)
--   financeiro (005): none
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'sales:proposal:read:all'),
    ('00000000-0000-0000-0000-000000000001', 'sales:proposal:create'),
    ('00000000-0000-0000-0000-000000000001', 'sales:proposal:update'),
    ('00000000-0000-0000-0000-000000000002', 'sales:proposal:read'),
    ('00000000-0000-0000-0000-000000000002', 'sales:proposal:read:unassigned'),
    ('00000000-0000-0000-0000-000000000002', 'sales:proposal:create'),
    ('00000000-0000-0000-0000-000000000002', 'sales:proposal:update'),
    ('00000000-0000-0000-0000-000000000003', 'sales:proposal:read'),
    ('00000000-0000-0000-0000-000000000003', 'sales:proposal:create'),
    ('00000000-0000-0000-0000-000000000003', 'sales:proposal:update'),
    ('00000000-0000-0000-0000-000000000004', 'sales:proposal:read:all');
