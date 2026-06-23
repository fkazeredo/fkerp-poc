-- Seed transitions for the workflows whose status is consolidated automatically — Commercial Order, Booking
-- Request and Booking Item — so the visual editor draws their lifecycle as a flow instead of disconnected
-- boxes. These transitions are SYSTEM-triggered (the status is computed in the domain and reflected via
-- triggers/events, not driven through the workflow engine's apply()) and are documentation-only: no behaviour
-- changes, the editor just renders the arrows. system = TRUE so they are locked like the other seeded rows.

INSERT INTO workflow_transitions (
    id, definition_id, code, from_state_id, to_state_id, label, trigger_type, sort_order, system)
SELECT gen_random_uuid(), d.id, t.code, fs.id, ts.id, t.label, 'SYSTEM', t.sort_order, TRUE
FROM workflow_definitions d
CROSS JOIN (
    VALUES
        -- Commercial Order (Pedido Comercial)
        ('order',           'ord_cancel_pending', 'PENDING_BOOKING',      'CANCELLED',           'Cancelar',           1),
        ('order',           'ord_cancel_nr',      'BOOKING_NOT_REQUIRED', 'CANCELLED',           'Cancelar',           2),
        -- Booking Request (Reserva)
        ('booking_request', 'br_start',           'PENDING',              'IN_PROGRESS',         'Iniciar',            1),
        ('booking_request', 'br_partial',         'IN_PROGRESS',          'PARTIALLY_CONFIRMED', 'Confirmar parcial',  2),
        ('booking_request', 'br_confirm',         'IN_PROGRESS',          'CONFIRMED',           'Confirmar',          3),
        ('booking_request', 'br_confirm_rest',    'PARTIALLY_CONFIRMED',  'CONFIRMED',           'Confirmar restante', 4),
        ('booking_request', 'br_fail',            'IN_PROGRESS',          'FAILED',              'Falhar',             5),
        ('booking_request', 'br_retry',           'FAILED',               'CONFIRMED',           'Retentar',           6),
        ('booking_request', 'br_cancel',          'PENDING',              'CANCELLED',           'Cancelar',           7),
        -- Booking Item (Item de reserva)
        ('booking_item',    'bi_start',           'PENDING',              'IN_PROGRESS',         'Tentar',             1),
        ('booking_item',    'bi_confirm',         'IN_PROGRESS',          'CONFIRMED',           'Confirmar',          2),
        ('booking_item',    'bi_fail',            'IN_PROGRESS',          'FAILED',              'Falhar',             3),
        ('booking_item',    'bi_retry',           'FAILED',               'CONFIRMED',           'Retentar',           4),
        ('booking_item',    'bi_cancel',          'PENDING',              'CANCELLED',           'Cancelar',           5)
) AS t(def_code, code, from_code, to_code, label, sort_order)
JOIN workflow_states fs ON fs.definition_id = d.id AND fs.code = t.from_code
JOIN workflow_states ts ON ts.definition_id = d.id AND ts.code = t.to_code
WHERE d.code = t.def_code;
