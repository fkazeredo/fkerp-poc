-- Configurable attention rules: the data-driven replacement for the hardcoded pending-reason enums
-- (PendingReason / OpportunityPendingReason / BookingPendingReason). Each rule attaches a worklist reason to
-- a workflow definition via a catalog condition key + typed params, with a stable code (the reason tag, kept
-- identical to the former enum name so the worklist JSON is unchanged) and an editable label. The seeded rules
-- are `system` (code/condition immutable, not deletable; label/threshold/order/active editable). The detection
-- logic stays in the owning domain, gated by the active rules and parameterized by these rows.

CREATE TABLE workflow_attention_rules (
    id            UUID PRIMARY KEY,
    definition_id UUID         NOT NULL REFERENCES workflow_definitions (id),
    condition_key VARCHAR(100) NOT NULL,
    threshold_days INT         CHECK (threshold_days IS NULL OR threshold_days >= 0),
    state_value   VARCHAR(60),
    code          VARCHAR(60)  NOT NULL,
    label         VARCHAR(120) NOT NULL,
    sort_order    INT          NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    system        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_workflow_attention_rule UNIQUE (definition_id, code)
);
CREATE INDEX idx_workflow_attention_rules_definition ON workflow_attention_rules (definition_id, active);

-- Lead worklist reasons.
INSERT INTO workflow_attention_rules
    (id, definition_id, condition_key, threshold_days, state_value, code, label, sort_order, system)
SELECT gen_random_uuid(), d.id, c.condition_key, c.threshold_days, c.state_value, c.code, c.label, c.sort_order, TRUE
FROM workflow_definitions d
JOIN (VALUES
    ('UNASSIGNED',                NULL::int, NULL::varchar, 'UNASSIGNED',                'Sem responsável',           1),
    ('NEW_WITHOUT_INTERACTION',   NULL,      NULL,          'NEW_WITHOUT_INTERACTION',   'Novo sem interação',        2),
    ('OVERDUE_NEXT_CONTACT',      NULL,      NULL,          'OVERDUE_NEXT_CONTACT',      'Próximo contato vencido',   3),
    ('CONTACTED_WITHOUT_OUTCOME', NULL,      NULL,          'CONTACTED_WITHOUT_OUTCOME', 'Contatado sem desfecho',    4)
) AS c(condition_key, threshold_days, state_value, code, label, sort_order) ON TRUE
WHERE d.code = 'lead';

-- Opportunity worklist reasons (staleness window 14 days).
INSERT INTO workflow_attention_rules
    (id, definition_id, condition_key, threshold_days, state_value, code, label, sort_order, system)
SELECT gen_random_uuid(), d.id, c.condition_key, c.threshold_days, c.state_value, c.code, c.label, c.sort_order, TRUE
FROM workflow_definitions d
JOIN (VALUES
    ('NO_RECENT_ACTIVITY',     14::int,   NULL::varchar,       'WITHOUT_RECENT_ACTIVITY', 'Sem atividade recente',        1),
    ('NEXT_ACTION_OVERDUE',    NULL,      NULL,                'OVERDUE_NEXT_ACTION',     'Próxima ação vencida',         2),
    ('IN_STATE_LONGER_THAN',   14,        'NEW_OPPORTUNITY',   'STUCK_IN_NEW',            'Parada em Nova',               3),
    ('IN_STATE_LONGER_THAN',   14,        'DISCOVERY',         'STUCK_IN_DISCOVERY',      'Parada em Descoberta',         4),
    ('IN_STATE',               NULL,      'READY_FOR_PROPOSAL','READY_FOR_PROPOSAL',      'Pronta para proposta',         5),
    ('EXPECTED_CLOSE_OVERDUE', NULL,      NULL,                'EXPECTED_CLOSE_OVERDUE',  'Fechamento previsto vencido',  6)
) AS c(condition_key, threshold_days, state_value, code, label, sort_order) ON TRUE
WHERE d.code = 'opportunity';

-- Booking Request worklist reasons (staleness window 7 days).
INSERT INTO workflow_attention_rules
    (id, definition_id, condition_key, threshold_days, state_value, code, label, sort_order, system)
SELECT gen_random_uuid(), d.id, c.condition_key, c.threshold_days, c.state_value, c.code, c.label, c.sort_order, TRUE
FROM workflow_definitions d
JOIN (VALUES
    ('UNASSIGNED_OPERATOR',        NULL::int, NULL::varchar,          'UNASSIGNED_OPERATOR',                'Sem operador',                       1),
    ('STATUS_IS',                  NULL,      'PENDING',              'PENDING_WITHOUT_ATTEMPT',            'Pendente sem tentativa',             2),
    ('IN_PROGRESS_STALE',          7,         NULL,                   'IN_PROGRESS_WITHOUT_RECENT_ATTEMPT', 'Em andamento sem tentativa recente', 3),
    ('HAS_FAILED_ITEM',            NULL,      NULL,                   'HAS_FAILED_ITEM',                    'Tem item com falha',                 4),
    ('HAS_PENDING_REQUIRED_ITEM',  NULL,      NULL,                   'HAS_PENDING_REQUIRED_ITEM',          'Tem item obrigatório pendente',      5),
    ('STATUS_IS',                  NULL,      'PARTIALLY_CONFIRMED',  'PARTIALLY_CONFIRMED',                'Parcialmente confirmada',            6),
    ('NEXT_ACTION_OVERDUE',        NULL,      NULL,                   'OVERDUE_NEXT_ACTION',                'Próxima ação vencida',               7)
) AS c(condition_key, threshold_days, state_value, code, label, sort_order) ON TRUE
WHERE d.code = 'booking_request';
