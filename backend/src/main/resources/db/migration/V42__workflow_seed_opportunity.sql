-- Seed the Opportunity pipeline as a configurable workflow (system rows: editable label/order, immutable
-- code, undeletable). Mirrors the former OpportunityStage enum + its flow exactly:
--   NEW_OPPORTUNITY --advance--> DISCOVERY --advance--> PRODUCT_FIT --advance--> READY_FOR_PROPOSAL
--   {any non-terminal} --lose--> LOST            (the lose action; LOST is terminal-negative)
--   {any non-terminal} --win--> WON              (system, fired when a Commercial Order is created from an
--                                                  accepted Proposal; WON is terminal-positive)
-- The strict-forward funnel is encoded by the single "advance" edge out of each active stage (skipping a
-- stage / going back simply has no edge); the category replaces the old isTerminal()/terminalStages().

INSERT INTO workflow_definitions (id, code, label)
VALUES (gen_random_uuid(), 'opportunity', 'Oportunidade');

INSERT INTO workflow_states (id, definition_id, code, label, category, sort_order, active, system)
SELECT gen_random_uuid(), d.id, s.code, s.label, s.category, s.sort_order, TRUE, TRUE
FROM workflow_definitions d
CROSS JOIN (
    VALUES ('NEW_OPPORTUNITY', 'Nova', 'INITIAL', 1),
           ('DISCOVERY', 'Descoberta', 'ACTIVE', 2),
           ('PRODUCT_FIT', 'Aderência', 'ACTIVE', 3),
           ('READY_FOR_PROPOSAL', 'Pronta para proposta', 'ACTIVE', 4),
           ('WON', 'Ganha', 'TERMINAL_POSITIVE', 5),
           ('LOST', 'Perdida', 'TERMINAL_NEGATIVE', 6)
) AS s(code, label, category, sort_order)
WHERE d.code = 'opportunity';

INSERT INTO workflow_transitions (
    id, definition_id, code, from_state_id, to_state_id, label, trigger_type, sort_order, system)
SELECT gen_random_uuid(), d.id, t.code, fs.id, ts.id, t.label, t.trigger_type, t.sort_order, TRUE
FROM workflow_definitions d
CROSS JOIN (
    VALUES ('advance', 'NEW_OPPORTUNITY', 'DISCOVERY', 'Avançar', 'USER', 1),
           ('advance', 'DISCOVERY', 'PRODUCT_FIT', 'Avançar', 'USER', 1),
           ('advance', 'PRODUCT_FIT', 'READY_FOR_PROPOSAL', 'Avançar', 'USER', 1),
           ('lose', 'NEW_OPPORTUNITY', 'LOST', 'Marcar perdida', 'USER', 2),
           ('lose', 'DISCOVERY', 'LOST', 'Marcar perdida', 'USER', 2),
           ('lose', 'PRODUCT_FIT', 'LOST', 'Marcar perdida', 'USER', 2),
           ('lose', 'READY_FOR_PROPOSAL', 'LOST', 'Marcar perdida', 'USER', 2),
           ('win', 'NEW_OPPORTUNITY', 'WON', 'Marcar ganha', 'SYSTEM', 3),
           ('win', 'DISCOVERY', 'WON', 'Marcar ganha', 'SYSTEM', 3),
           ('win', 'PRODUCT_FIT', 'WON', 'Marcar ganha', 'SYSTEM', 3),
           ('win', 'READY_FOR_PROPOSAL', 'WON', 'Marcar ganha', 'SYSTEM', 3)
) AS t(code, from_code, to_code, label, trigger_type, sort_order)
JOIN workflow_states fs ON fs.definition_id = d.id AND fs.code = t.from_code
JOIN workflow_states ts ON ts.definition_id = d.id AND ts.code = t.to_code
WHERE d.code = 'opportunity';
