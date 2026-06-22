-- Rewire the Opportunity onto the configurable workflow (mirrors V41 for the Lead): add the FK to its
-- current workflow state, backfilled from the denormalized stage code, plus the BEFORE INSERT trigger that
-- keeps the FK consistent with the stage code for direct SQL inserts. The application sets current_state_id
-- explicitly via the entity; the trigger only aids direct inserts and guarantees the two never drift.
ALTER TABLE opportunities ADD COLUMN current_state_id UUID REFERENCES workflow_states (id);

UPDATE opportunities o SET current_state_id = (
    SELECT s.id
    FROM workflow_states s
    JOIN workflow_definitions d ON d.id = s.definition_id AND d.code = 'opportunity'
    WHERE s.code = o.stage);

ALTER TABLE opportunities ALTER COLUMN current_state_id SET NOT NULL;
CREATE INDEX idx_opportunities_current_state ON opportunities (current_state_id);

CREATE OR REPLACE FUNCTION opportunities_fill_current_state() RETURNS trigger AS $$
BEGIN
    IF NEW.current_state_id IS NULL THEN
        NEW.current_state_id := (
            SELECT s.id
            FROM workflow_states s
            JOIN workflow_definitions d ON d.id = s.definition_id
            WHERE d.code = 'opportunity' AND s.code = NEW.stage);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_opportunities_fill_current_state
    BEFORE INSERT ON opportunities
    FOR EACH ROW EXECUTE FUNCTION opportunities_fill_current_state();
