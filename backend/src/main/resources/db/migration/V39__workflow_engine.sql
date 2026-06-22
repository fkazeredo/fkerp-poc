-- Configurable workflow engine: the data-driven replacement for the hardcoded status enums. Definitions
-- (one per lifecycle), their states and transitions, and the per-transition rules (conditions/validators/
-- post functions, chosen from the engine catalog) all live here. Seeds of the six existing lifecycles are
-- added per domain in later migrations; this migration only creates the neutral engine schema.

CREATE TABLE workflow_definitions (
    id         UUID PRIMARY KEY,
    code       VARCHAR(60)  NOT NULL UNIQUE,
    label      VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE workflow_states (
    id            UUID PRIMARY KEY,
    definition_id UUID         NOT NULL REFERENCES workflow_definitions (id),
    code          VARCHAR(60)  NOT NULL,
    label         VARCHAR(120) NOT NULL,
    category      VARCHAR(30)  NOT NULL
        CHECK (category IN ('INITIAL', 'ACTIVE', 'TERMINAL_POSITIVE', 'TERMINAL_NEGATIVE')),
    sort_order    INT          NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    system        BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (definition_id, code)
);

CREATE TABLE workflow_transitions (
    id            UUID PRIMARY KEY,
    definition_id UUID         NOT NULL REFERENCES workflow_definitions (id),
    code          VARCHAR(60)  NOT NULL,
    from_state_id UUID         NOT NULL REFERENCES workflow_states (id),
    to_state_id   UUID         NOT NULL REFERENCES workflow_states (id),
    label         VARCHAR(120) NOT NULL,
    trigger_type  VARCHAR(20)  NOT NULL CHECK (trigger_type IN ('USER', 'SYSTEM')),
    sort_order    INT          NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    system        BOOLEAN      NOT NULL DEFAULT FALSE,
    -- A transition code is unique per (definition, source state): the same named action (e.g. "qualify",
    -- "lose") may be available from several source states as distinct rows, so the engine resolves a
    -- transition by (definition, current state, code).
    UNIQUE (definition_id, from_state_id, code)
);

CREATE TABLE workflow_rules (
    id            UUID PRIMARY KEY,
    transition_id UUID         NOT NULL REFERENCES workflow_transitions (id) ON DELETE CASCADE,
    kind          VARCHAR(20)  NOT NULL CHECK (kind IN ('CONDITION', 'VALIDATOR', 'POST_FUNCTION')),
    rule_key      VARCHAR(100) NOT NULL,
    params        VARCHAR(2000),
    sort_order    INT          NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    system        BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_workflow_states_definition ON workflow_states (definition_id);
CREATE INDEX idx_workflow_transitions_definition ON workflow_transitions (definition_id);
CREATE INDEX idx_workflow_transitions_from ON workflow_transitions (from_state_id);
CREATE INDEX idx_workflow_rules_transition ON workflow_rules (transition_id);
