-- Migration baseline. Tabela mínima só para validar a cadeia Flyway + JPA + PostgreSQL.
-- O domínio do ERP ainda não foi modelado.
CREATE TABLE app_info (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO app_info (name) VALUES ('fkerp');
