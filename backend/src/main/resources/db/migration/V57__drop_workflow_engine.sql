-- Revert the data-driven workflow engine (owner decision): the six lifecycles go back to enum-backed status
-- columns with pre-defined transitions enforced in the domain entities. The denormalized status/stage code
-- columns and their CHECK constraints stay (the entities now map them as @Enumerated(STRING)); only the
-- workflow_state FK columns, their sync triggers/functions, and the workflow engine tables are removed. The
-- workflow:manage scope is dropped (the visual "Fluxos de trabalho" editor module is gone). The reference
-- cadastros (Cadastros module) are untouched.

-- 1. Drop the FK-sync triggers and their functions.
DROP TRIGGER IF EXISTS trg_leads_fill_current_state ON leads;
DROP TRIGGER IF EXISTS trg_opportunities_fill_current_state ON opportunities;
DROP TRIGGER IF EXISTS trg_proposals_fill_current_state ON proposals;
DROP TRIGGER IF EXISTS trg_commercial_orders_fill_current_state ON commercial_orders;
DROP TRIGGER IF EXISTS trg_booking_requests_fill_current_state ON booking_requests;
DROP TRIGGER IF EXISTS trg_booking_items_fill_current_state ON booking_items;

DROP FUNCTION IF EXISTS leads_fill_current_state();
DROP FUNCTION IF EXISTS opportunities_fill_current_state();
DROP FUNCTION IF EXISTS proposals_fill_current_state();
DROP FUNCTION IF EXISTS commercial_orders_fill_current_state();
DROP FUNCTION IF EXISTS booking_requests_fill_current_state();
DROP FUNCTION IF EXISTS booking_items_fill_current_state();

-- 2. Drop the workflow-state FK columns (their indexes go with them); the status/stage code columns remain
--    the source of truth.
ALTER TABLE leads DROP COLUMN current_state_id;
ALTER TABLE opportunities DROP COLUMN current_state_id;
ALTER TABLE proposals DROP COLUMN current_state_id;
ALTER TABLE commercial_orders DROP COLUMN current_state_id;
ALTER TABLE booking_requests DROP COLUMN current_state_id;
ALTER TABLE booking_items DROP COLUMN current_state_id;

-- 3. Drop the workflow engine tables (child tables first to satisfy the FKs).
DROP TABLE IF EXISTS workflow_attention_rules;
DROP TABLE IF EXISTS workflow_rules;
DROP TABLE IF EXISTS workflow_transitions;
DROP TABLE IF EXISTS workflow_states;
DROP TABLE IF EXISTS workflow_definitions;

-- 4. Remove the now-defunct workflow:manage scope.
DELETE FROM user_scopes WHERE scope = 'workflow:manage';
