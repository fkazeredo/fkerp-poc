-- Rewire the Commercial Order onto the configurable workflow (mirrors V41/V43/V45): add the FK to its
-- current workflow state, backfilled from the denormalized status code, plus the BEFORE INSERT trigger
-- keeping the FK consistent with the status code for direct SQL inserts.
ALTER TABLE commercial_orders ADD COLUMN current_state_id UUID REFERENCES workflow_states (id);

UPDATE commercial_orders co SET current_state_id = (
    SELECT s.id
    FROM workflow_states s
    JOIN workflow_definitions d ON d.id = s.definition_id AND d.code = 'order'
    WHERE s.code = co.status);

ALTER TABLE commercial_orders ALTER COLUMN current_state_id SET NOT NULL;
CREATE INDEX idx_commercial_orders_current_state ON commercial_orders (current_state_id);

CREATE OR REPLACE FUNCTION commercial_orders_fill_current_state() RETURNS trigger AS $$
BEGIN
    IF NEW.current_state_id IS NULL THEN
        NEW.current_state_id := (
            SELECT s.id
            FROM workflow_states s
            JOIN workflow_definitions d ON d.id = s.definition_id
            WHERE d.code = 'order' AND s.code = NEW.status);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_commercial_orders_fill_current_state
    BEFORE INSERT ON commercial_orders
    FOR EACH ROW EXECUTE FUNCTION commercial_orders_fill_current_state();
