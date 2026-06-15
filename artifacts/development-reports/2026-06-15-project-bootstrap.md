# Relatório de implementação — Bootstrap do FKERP

- **Data:** 2026-06-15
- **Branch:** `feature/project-bootstrap` (a partir de `develop`, a partir de `main`)
- **Commit base:** `8629f4a` (bootstrap) + commit de follow-up (rename `ops`→`infra`, `artifacts/`)
- **Objetivo:** subir a fundação do ERP — backend no ar, frontend com página de welcome, e observabilidade no ar — tudo em Docker Compose.

## O que foi implementado

### Backend (`backend/`)
- **Spring Boot 4 / Java 21 / Maven** (via wrapper `./mvnw`), módulo único, pacote base `com.fksoft.erp`.
- **Sem controller custom**: prova de vida pelo **Actuator** (`/actuator/health`, com liveness/readiness).
- **Persistência**: PostgreSQL + Spring Data JPA + **Flyway** (`V1__init.sql`, baseline mínima).
- **Config tipada**: `AppProperties` (`@ConfigurationProperties` + `@Validated`) — falha rápido se faltar config.
- **Conformidade com o contrato `CLAUDE.md`**: Spring Modulith (detecção explícita), **Lombok**,
  e os gates **ArchUnit + Spring Modulith + Spotless (palantir) + Checkstyle** ligados ao `verify`.
- **Testes**: `AbstractIntegrationTest` com **Testcontainers** (container PostgreSQL compartilhado),
  `ErpApplicationTests` (smoke), `ArchitectureTest`, `ModularityTests`.

### Frontend (`frontend/`)
- **Angular** (standalone, estrutura `core/ shared/ features/`).
- Página inicial = **welcome** exibindo o nome **FKERP** (`features/welcome`).
- Servido por **Nginx** em container (Dockerfile multistage: build Node → Nginx).

### Observabilidade (`infra/`) — métricas + logs
- **Prometheus**: raspa `backend:8080/actuator/prometheus` (Micrometer/Actuator).
- **Loki**: armazena logs; **Grafana Alloy** coleta os logs dos containers e envia ao Loki.
- **Grafana**: datasources (Prometheus, Loki) e 1 dashboard de visão geral do backend **provisionados**.
- **Sem Tempo/traces** — alinhado ao setup de observabilidade que já funciona (Prometheus + Loki + Alloy + Grafana). Ver "Escopo".

### Repositório / governança
- **`CLAUDE.md`**: mantido o contrato do usuário; complementado (pacote base, regra "commitar antes do relatório", layout `infra/`+`artifacts/`).
- **`.claude/`** versionado: `settings.json` (permission tiers + hooks), `hooks/` (`protect-generated`, `format-java`), `plans/`, `memory/`.
- **`artifacts/`**: documentação orientada a humanos (este relatório vive em `artifacts/development-reports/`).
- `.env.example` (commitado) + `.env` (gitignorado), `.gitignore`, `.gitattributes`.

## Como rodar

```bash
cp .env.example .env
docker compose up --build
```

| Serviço         | URL                                        |
|-----------------|--------------------------------------------|
| Frontend        | http://localhost:4200                      |
| Backend health  | http://localhost:8080/actuator/health      |
| Backend métricas| http://localhost:8080/actuator/prometheus  |
| Grafana         | http://localhost:3000 (admin / admin)      |
| Prometheus      | http://localhost:9090                      |
| Loki            | http://localhost:3100                      |
| PostgreSQL      | localhost:5432                             |

Backend (sempre via wrapper): `./mvnw verify` (build + gates + testes; precisa de Docker).

## Validação (resultados desta sessão)

- **Backend**: `/actuator/health` = **UP** (db PostgreSQL UP via Flyway; liveness/readiness UP).
- **Frontend**: `http://localhost:4200` responde **200** com a welcome FKERP.
- **Prometheus**: alvo `backend` **up=1** (métricas sendo raspadas).
- **Loki**: logs do backend presentes (`service="backend"`), coletados pelo Alloy.
- **Grafana**: datasources **Prometheus** e **Loki** provisionados e ativos.
- **Backend gates (`./mvnw`)**: Spotless, Checkstyle, compile, ArchUnit e Modulith **OK**.

## Escopo / decisões

- **Traces ficaram fora.** O setup de observabilidade do usuário é métricas + logs (Prometheus + Loki + Alloy + Grafana), sem Tempo. Além disso, o **Spring Boot 4 (recém-lançado) não exporta traces OTLP "de fábrica"** — a autoconfig modularizada sobe o SDK sem TracerProvider (cai num `noopTracer`), exigindo wiring manual. Optou-se por não incluir traces nesta POC.
- **Conformidade "total"** foi interpretada como honrar as regras estruturais e os gates do contrato, **sem** fabricar módulos de domínio vazios ou auth/i18n não usados (o próprio contrato proíbe — Rule Zero / §14).

## Pendências / follow-ups

- Modelagem do domínio do ERP (primeiro módulo de negócio) — quando definido.
- (Se um dia quiser traces) habilitar OTLP/Tempo com o wiring manual do Boot 4.
- Push da branch / abertura de PR `feature/project-bootstrap` → `develop` — sob seu comando.
