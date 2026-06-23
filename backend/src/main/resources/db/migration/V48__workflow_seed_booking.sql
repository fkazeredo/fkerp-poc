-- Seed the Booking Operations lifecycles as configurable workflows (system rows). Two definitions mirror the
-- former BookingRequestStatus and BookingItemStatus enums. The booking statuses are STATE-DERIVED (the request
-- by consolidateStatus, each item by its own confirm/fail transitions) — the reduction algorithm stays in code;
-- these rows make the states/labels editable data. FAILED is an ACTIVE problem state (it can be retried), not a
-- terminal one; CONFIRMED is terminal-positive and CANCELLED terminal-negative.

INSERT INTO workflow_definitions (id, code, label)
VALUES (gen_random_uuid(), 'booking_request', 'Reserva'),
       (gen_random_uuid(), 'booking_item', 'Item de reserva');

-- Booking Request states.
INSERT INTO workflow_states (id, definition_id, code, label, category, sort_order, active, system)
SELECT gen_random_uuid(), d.id, s.code, s.label, s.category, s.sort_order, TRUE, TRUE
FROM workflow_definitions d
CROSS JOIN (
    VALUES ('PENDING', 'Pendente', 'INITIAL', 1),
           ('IN_PROGRESS', 'Em andamento', 'ACTIVE', 2),
           ('PARTIALLY_CONFIRMED', 'Parcialmente confirmada', 'ACTIVE', 3),
           ('CONFIRMED', 'Confirmada', 'TERMINAL_POSITIVE', 4),
           ('FAILED', 'Com falha', 'ACTIVE', 5),
           ('CANCELLED', 'Cancelada', 'TERMINAL_NEGATIVE', 6)
) AS s(code, label, category, sort_order)
WHERE d.code = 'booking_request';

-- Booking Item states.
INSERT INTO workflow_states (id, definition_id, code, label, category, sort_order, active, system)
SELECT gen_random_uuid(), d.id, s.code, s.label, s.category, s.sort_order, TRUE, TRUE
FROM workflow_definitions d
CROSS JOIN (
    VALUES ('PENDING', 'Pendente', 'INITIAL', 1),
           ('NOT_REQUIRED', 'Sem reserva necessária', 'INITIAL', 2),
           ('IN_PROGRESS', 'Em andamento', 'ACTIVE', 3),
           ('CONFIRMED', 'Confirmado', 'TERMINAL_POSITIVE', 4),
           ('FAILED', 'Com falha', 'ACTIVE', 5),
           ('CANCELLED', 'Cancelado', 'TERMINAL_NEGATIVE', 6)
) AS s(code, label, category, sort_order)
WHERE d.code = 'booking_item';
