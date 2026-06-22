-- Seed the Commercial Order lifecycle as a configurable workflow (system rows). Mirrors the former
-- CommercialOrderStatus enum: a new Order starts at PENDING_BOOKING (when it has a bookable item) or
-- BOOKING_NOT_REQUIRED otherwise — both are entry states — and CANCELLED is the terminal-negative ("not
-- active") state. There are no user transitions yet (the cancel action is a later slice); the workflow only
-- gives the Order a data-driven set of states (the initial-state selection stays the Order's own logic).

INSERT INTO workflow_definitions (id, code, label)
VALUES (gen_random_uuid(), 'order', 'Pedido Comercial');

INSERT INTO workflow_states (id, definition_id, code, label, category, sort_order, active, system)
SELECT gen_random_uuid(), d.id, s.code, s.label, s.category, s.sort_order, TRUE, TRUE
FROM workflow_definitions d
CROSS JOIN (
    VALUES ('PENDING_BOOKING', 'Aguardando reserva', 'INITIAL', 1),
           ('BOOKING_NOT_REQUIRED', 'Sem reserva necessária', 'INITIAL', 2),
           ('CANCELLED', 'Cancelado', 'TERMINAL_NEGATIVE', 3)
) AS s(code, label, category, sort_order)
WHERE d.code = 'order';
