-- Sprint 3 / Slice 5: Proposal detail consultation. The Proposal gains a status-change history (part of the
-- aggregate), mirroring the Opportunity's stage-movement history (V17). Every lifecycle transition is kept
-- for the record; as the lifecycle grows it also carries the approval / sent / customer-decision facts
-- (who and when). Only the submit transition (DRAFT → READY_FOR_REVIEW) exists in this slice. No new scope:
-- the transitions reuse sales:proposal:update (seeded in V20).
CREATE TABLE proposal_status_changes (
    id          UUID PRIMARY KEY,
    proposal_id UUID        NOT NULL REFERENCES proposals (id),
    from_status VARCHAR(30) NOT NULL
        CHECK (from_status IN ('DRAFT', 'READY_FOR_REVIEW', 'APPROVED', 'SENT', 'ACCEPTED',
                               'REJECTED', 'EXPIRED', 'CANCELLED')),
    to_status   VARCHAR(30) NOT NULL
        CHECK (to_status IN ('DRAFT', 'READY_FOR_REVIEW', 'APPROVED', 'SENT', 'ACCEPTED',
                             'REJECTED', 'EXPIRED', 'CANCELLED')),
    changed_by  UUID        NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_proposal_status_changes_proposal ON proposal_status_changes (proposal_id);
