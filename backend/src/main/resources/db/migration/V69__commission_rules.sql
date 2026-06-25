-- Commission Management / Sprint 6 Slice 1: Commission Rule (configuration only). A rule says how much commission a
-- commercial actor earns — for Sprint 6, a percentage of the received amount. It is NOT a Commission record, a
-- Payment, payroll, payable, tax or accounting entry; Commission Management owns the rule but not the Commercial
-- Order or the Receivable. Only active rules may be used for new commission calculation (a later slice).
CREATE TABLE commission_rules (
    id              UUID PRIMARY KEY,
    version         BIGINT        NOT NULL DEFAULT 0,
    name            VARCHAR(160)  NOT NULL,
    percentage      NUMERIC(5, 2) NOT NULL CHECK (percentage > 0 AND percentage <= 100),
    target_type     VARCHAR(30)   NOT NULL
        CHECK (target_type IN ('SELLER', 'SALES_REPRESENTATIVE', 'COMMERCIAL_RESPONSIBLE')),
    target_user_id  UUID          REFERENCES users (id),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    start_date      DATE          NOT NULL,
    end_date        DATE          CHECK (end_date IS NULL OR end_date >= start_date),
    notes           VARCHAR(2000),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      UUID          NOT NULL,
    updated_by      UUID          NOT NULL
);

CREATE INDEX idx_commission_rules_active ON commission_rules (active);
CREATE INDEX idx_commission_rules_target_user ON commission_rules (target_user_id);
