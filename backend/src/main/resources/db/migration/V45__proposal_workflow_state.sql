-- Rewire the Proposal onto the configurable workflow (mirrors V41/V43): add the FK to its current workflow
-- state, backfilled from the denormalized status code, plus the BEFORE INSERT trigger keeping the FK
-- consistent with the status code for direct SQL inserts.
ALTER TABLE proposals ADD COLUMN current_state_id UUID REFERENCES workflow_states (id);

UPDATE proposals p SET current_state_id = (
    SELECT s.id
    FROM workflow_states s
    JOIN workflow_definitions d ON d.id = s.definition_id AND d.code = 'proposal'
    WHERE s.code = p.status);

ALTER TABLE proposals ALTER COLUMN current_state_id SET NOT NULL;
CREATE INDEX idx_proposals_current_state ON proposals (current_state_id);

CREATE OR REPLACE FUNCTION proposals_fill_current_state() RETURNS trigger AS $$
BEGIN
    IF NEW.current_state_id IS NULL THEN
        NEW.current_state_id := (
            SELECT s.id
            FROM workflow_states s
            JOIN workflow_definitions d ON d.id = s.definition_id
            WHERE d.code = 'proposal' AND s.code = NEW.status);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_proposals_fill_current_state
    BEFORE INSERT ON proposals
    FOR EACH ROW EXECUTE FUNCTION proposals_fill_current_state();
