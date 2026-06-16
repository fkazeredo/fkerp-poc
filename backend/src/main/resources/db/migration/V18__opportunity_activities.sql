-- Sprint 2 / Slice 7: commercial activities on the Opportunity (append-only negotiation history),
-- mirroring the Lead's interactions (V9). Types and results are a fixed Sprint-2 set (enums + CHECK,
-- not editable cadastros). The Opportunity keeps a denormalized next_action_date (the latest activity's),
-- like leads.next_contact_at. No new scope: registering reuses crm:opportunity:update (seeded in V16).
ALTER TABLE opportunities ADD COLUMN next_action_date DATE;

CREATE TABLE opportunity_activities (
    id               UUID PRIMARY KEY,
    opportunity_id   UUID         NOT NULL REFERENCES opportunities (id),
    type             VARCHAR(40)  NOT NULL
        CHECK (type IN ('PHONE_CALL', 'WHATSAPP', 'EMAIL', 'MEETING', 'INTERNAL_NOTE', 'DOCUMENT_REQUEST',
                        'PRICE_DISCUSSION', 'TRAVEL_REQUIREMENT_CLARIFICATION', 'OTHER')),
    result           VARCHAR(40)  NOT NULL
        CHECK (result IN ('CLIENT_ENGAGED', 'NEEDS_FOLLOW_UP', 'WAITING_FOR_CLIENT', 'WAITING_FOR_INTERNAL_INFO',
                          'PRODUCT_FIT_IDENTIFIED', 'READY_FOR_PROPOSAL', 'NOT_INTERESTED', 'OTHER')),
    description      VARCHAR(4000) NOT NULL,
    occurred_at      TIMESTAMPTZ  NOT NULL,
    next_action_date DATE,
    registered_by    UUID         NOT NULL
);

CREATE INDEX idx_opportunity_activities_opportunity ON opportunity_activities (opportunity_id, occurred_at DESC);
