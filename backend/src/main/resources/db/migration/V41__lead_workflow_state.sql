-- Rewire the Lead onto the configurable workflow: add the FK to its current workflow state, backfilled
-- from the denormalized status code (seeded in V40). The `status` column is kept as the denormalized state
-- code (== current_state.code()) for cheap filtering/grouping and the read contract; the FK is the
-- data-driven source of truth (referential integrity to workflow_states), kept in sync on every transition.
ALTER TABLE leads ADD COLUMN current_state_id UUID REFERENCES workflow_states (id);

UPDATE leads l SET current_state_id = (
    SELECT s.id
    FROM workflow_states s
    JOIN workflow_definitions d ON d.id = s.definition_id AND d.code = 'lead'
    WHERE s.code = l.status);

ALTER TABLE leads ALTER COLUMN current_state_id SET NOT NULL;
CREATE INDEX idx_leads_current_state ON leads (current_state_id);

-- Safety net keeping the FK consistent with the denormalized status code: when a row is inserted with a
-- status but no current_state_id (e.g. a direct SQL insert), derive the FK from the Lead workflow. The
-- application always sets current_state_id explicitly (via the entity), so the trigger only aids direct
-- inserts and guarantees the two never drift.
CREATE OR REPLACE FUNCTION leads_fill_current_state() RETURNS trigger AS $$
BEGIN
    IF NEW.current_state_id IS NULL THEN
        NEW.current_state_id := (
            SELECT s.id
            FROM workflow_states s
            JOIN workflow_definitions d ON d.id = s.definition_id
            WHERE d.code = 'lead' AND s.code = NEW.status);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_leads_fill_current_state
    BEFORE INSERT ON leads
    FOR EACH ROW EXECUTE FUNCTION leads_fill_current_state();
