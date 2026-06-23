-- Seed the Proposal lifecycle as a configurable workflow (system rows). Mirrors the former ProposalStatus
-- enum + its transitions exactly:
--   DRAFT --submit--> READY_FOR_REVIEW --approve--> APPROVED --send--> SENT --accept--> ACCEPTED
--   READY_FOR_REVIEW --reject--> REJECTED ; SENT --decline--> REJECTED
-- ACCEPTED is terminal-positive (the winning offer that originates the Commercial Order); REJECTED/EXPIRED/
-- CANCELLED are terminal-negative ("not open"). EXPIRED/CANCELLED are seeded as states for the open-filter
-- semantics; their transitions are a later slice. The input validations (items/total/validity/responsible
-- on submit, the rejection reasons) stay as vetted entity logic; the engine owns the state graph.

INSERT INTO workflow_definitions (id, code, label)
VALUES (gen_random_uuid(), 'proposal', 'Proposta');

INSERT INTO workflow_states (id, definition_id, code, label, category, sort_order, active, system)
SELECT gen_random_uuid(), d.id, s.code, s.label, s.category, s.sort_order, TRUE, TRUE
FROM workflow_definitions d
CROSS JOIN (
    VALUES ('DRAFT', 'Rascunho', 'INITIAL', 1),
           ('READY_FOR_REVIEW', 'Em revisão', 'ACTIVE', 2),
           ('APPROVED', 'Aprovada', 'ACTIVE', 3),
           ('SENT', 'Enviada', 'ACTIVE', 4),
           ('ACCEPTED', 'Aceita', 'TERMINAL_POSITIVE', 5),
           ('REJECTED', 'Rejeitada', 'TERMINAL_NEGATIVE', 6),
           ('EXPIRED', 'Expirada', 'TERMINAL_NEGATIVE', 7),
           ('CANCELLED', 'Cancelada', 'TERMINAL_NEGATIVE', 8)
) AS s(code, label, category, sort_order)
WHERE d.code = 'proposal';

INSERT INTO workflow_transitions (
    id, definition_id, code, from_state_id, to_state_id, label, trigger_type, sort_order, system)
SELECT gen_random_uuid(), d.id, t.code, fs.id, ts.id, t.label, t.trigger_type, t.sort_order, TRUE
FROM workflow_definitions d
CROSS JOIN (
    VALUES ('submit', 'DRAFT', 'READY_FOR_REVIEW', 'Enviar para revisão', 'USER', 1),
           ('approve', 'READY_FOR_REVIEW', 'APPROVED', 'Aprovar', 'USER', 1),
           ('reject', 'READY_FOR_REVIEW', 'REJECTED', 'Rejeitar', 'USER', 2),
           ('send', 'APPROVED', 'SENT', 'Marcar enviada', 'USER', 1),
           ('accept', 'SENT', 'ACCEPTED', 'Cliente aceitou', 'USER', 1),
           ('decline', 'SENT', 'REJECTED', 'Cliente recusou', 'USER', 2)
) AS t(code, from_code, to_code, label, trigger_type, sort_order)
JOIN workflow_states fs ON fs.definition_id = d.id AND fs.code = t.from_code
JOIN workflow_states ts ON ts.definition_id = d.id AND ts.code = t.to_code
WHERE d.code = 'proposal';
