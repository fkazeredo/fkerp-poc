-- CRM reference data (cadastros): origins, loss reasons, interaction types/results.
CREATE TABLE origins (
    id         UUID PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    label      VARCHAR(120) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE loss_reasons (
    id         UUID PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    label      VARCHAR(120) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE interaction_types (
    id         UUID PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    label      VARCHAR(120) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE interaction_results (
    id         UUID PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    label      VARCHAR(120) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO origins (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'WEBSITE', 'Website', 1),
    (gen_random_uuid(), 'INSTAGRAM', 'Instagram', 2),
    (gen_random_uuid(), 'GOOGLE_ADS', 'Google Ads', 3),
    (gen_random_uuid(), 'REFERRAL', 'Indicação', 4),
    (gen_random_uuid(), 'SALES_REPRESENTATIVE', 'Representante comercial', 5),
    (gen_random_uuid(), 'ACTIVE_CALL_CENTER', 'Call center ativo', 6),
    (gen_random_uuid(), 'RETURNING_CUSTOMER', 'Cliente recorrente', 7),
    (gen_random_uuid(), 'EVENT', 'Evento', 8),
    (gen_random_uuid(), 'OTHER', 'Outro', 9);

INSERT INTO loss_reasons (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'NO_RESPONSE', 'Sem resposta', 1),
    (gen_random_uuid(), 'INVALID_CONTACT', 'Contato inválido', 2),
    (gen_random_uuid(), 'NO_INTEREST', 'Sem interesse', 3),
    (gen_random_uuid(), 'PRICE_TOO_HIGH', 'Preço muito alto', 4),
    (gen_random_uuid(), 'BOUGHT_ELSEWHERE', 'Comprou em outro lugar', 5),
    (gen_random_uuid(), 'OUT_OF_PROFILE', 'Fora do perfil', 6),
    (gen_random_uuid(), 'DUPLICATED', 'Duplicado', 7),
    (gen_random_uuid(), 'OTHER', 'Outro', 8);

INSERT INTO interaction_types (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'PHONE_CALL', 'Ligação', 1),
    (gen_random_uuid(), 'WHATSAPP', 'WhatsApp', 2),
    (gen_random_uuid(), 'EMAIL', 'E-mail', 3),
    (gen_random_uuid(), 'IN_PERSON', 'Presencial', 4),
    (gen_random_uuid(), 'INTERNAL_NOTE', 'Nota interna', 5),
    (gen_random_uuid(), 'OTHER', 'Outro', 6);

INSERT INTO interaction_results (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'CONTACT_MADE', 'Contato realizado', 1),
    (gen_random_uuid(), 'NO_ANSWER', 'Não atendeu', 2),
    (gen_random_uuid(), 'INVALID_CONTACT', 'Contato inválido', 3),
    (gen_random_uuid(), 'ASKED_FOR_RETURN', 'Pediu retorno', 4),
    (gen_random_uuid(), 'INTERESTED', 'Interessado', 5),
    (gen_random_uuid(), 'NOT_INTERESTED', 'Não interessado', 6),
    (gen_random_uuid(), 'NEEDS_FOLLOW_UP', 'Precisa follow-up', 7),
    (gen_random_uuid(), 'OTHER', 'Outro', 8);
