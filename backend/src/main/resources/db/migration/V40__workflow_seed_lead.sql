-- Seed the Lead lifecycle as a configurable workflow (system rows: editable label/order, immutable code,
-- undeletable). Mirrors the former LeadStatus enum + its transitions exactly:
--   NEW --contact(system)--> CONTACTED --qualify--> QUALIFIED ; {NEW,CONTACTED,QUALIFIED} --lose--> LOST
-- The "qualify" transition carries the require-responsible validator (the old
-- LeadQualificationRequiresResponsibleException). "contact" is fired by the system on an effective contact.

INSERT INTO workflow_definitions (id, code, label)
VALUES (gen_random_uuid(), 'lead', 'Lead');

INSERT INTO workflow_states (id, definition_id, code, label, category, sort_order, active, system)
SELECT gen_random_uuid(), d.id, s.code, s.label, s.category, s.sort_order, TRUE, TRUE
FROM workflow_definitions d
CROSS JOIN (
    VALUES ('NEW', 'Novo', 'INITIAL', 1),
           ('CONTACTED', 'Contatado', 'ACTIVE', 2),
           ('QUALIFIED', 'Qualificado', 'ACTIVE', 3),
           ('LOST', 'Perdido', 'TERMINAL_NEGATIVE', 4)
) AS s(code, label, category, sort_order)
WHERE d.code = 'lead';

INSERT INTO workflow_transitions (
    id, definition_id, code, from_state_id, to_state_id, label, trigger_type, sort_order, system)
SELECT gen_random_uuid(), d.id, t.code, fs.id, ts.id, t.label, t.trigger_type, t.sort_order, TRUE
FROM workflow_definitions d
CROSS JOIN (
    VALUES ('contact', 'NEW', 'CONTACTED', 'Marcar contatado', 'SYSTEM', 1),
           ('qualify', 'CONTACTED', 'QUALIFIED', 'Qualificar', 'USER', 2),
           ('lose', 'NEW', 'LOST', 'Marcar perdido', 'USER', 3),
           ('lose', 'CONTACTED', 'LOST', 'Marcar perdido', 'USER', 3),
           ('lose', 'QUALIFIED', 'LOST', 'Marcar perdido', 'USER', 3)
) AS t(code, from_code, to_code, label, trigger_type, sort_order)
JOIN workflow_states fs ON fs.definition_id = d.id AND fs.code = t.from_code
JOIN workflow_states ts ON ts.definition_id = d.id AND ts.code = t.to_code
WHERE d.code = 'lead';

INSERT INTO workflow_rules (id, transition_id, kind, rule_key, params, sort_order, system)
SELECT gen_random_uuid(), tr.id, 'VALIDATOR', 'lead.require-responsible', NULL, 1, TRUE
FROM workflow_transitions tr
JOIN workflow_definitions d ON d.id = tr.definition_id AND d.code = 'lead'
WHERE tr.code = 'qualify';
