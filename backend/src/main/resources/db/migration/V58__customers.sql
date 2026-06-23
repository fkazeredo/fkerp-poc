-- The Customer: the commercial graduation of a Lead, materialized when a Commercial Order is created (deal
-- closed). One Lead originates at most one Customer (lead_id unique). The document (CPF/CNPJ) and billing
-- address are optional placeholders filled by a later slice. A Customer is not financial data.
CREATE TABLE customers (
    id              UUID PRIMARY KEY,
    version         BIGINT       NOT NULL DEFAULT 0,
    lead_id         UUID         NOT NULL UNIQUE REFERENCES leads (id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    phone           VARCHAR(30),
    whatsapp        VARCHAR(30),
    email           VARCHAR(255),
    document        VARCHAR(30),
    document_type   VARCHAR(20),
    billing_address VARCHAR(500),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID         NOT NULL,
    updated_by      UUID         NOT NULL
);

-- Backfill: every Lead that already originated a Commercial Order graduates to a Customer (snapshot of the
-- Lead's name and contacts), so pre-existing confirmed orders can originate Receivables. One Customer per Lead.
INSERT INTO customers (id, lead_id, name, phone, whatsapp, email, active, created_by, updated_by)
SELECT gen_random_uuid(), l.id, l.name, l.phone, l.whatsapp, l.email, TRUE, o.created_by, o.created_by
FROM leads l
JOIN (
    SELECT lead_id, MIN(created_by::text)::uuid AS created_by
    FROM commercial_orders
    GROUP BY lead_id
) o ON o.lead_id = l.id;
