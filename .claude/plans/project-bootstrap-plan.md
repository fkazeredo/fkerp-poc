# Plano — Boot do projeto fkerp (estrutura base + observability)

## Contexto

O repositório `fkerp-poc` está vazio (só `README.md` com o título e `CLAUDE.md`). O objetivo
desta iteração **não** é modelar o domínio do ERP, e sim deixar a fundação "no ar":

1. **Backend** rodando (pacote base `com.fksoft.erp`).
2. **Frontend** rodando com uma **página de welcome**.
3. **Observabilidade completa no ar** (métricas, logs e traces).

Tudo orquestrado via **Docker Compose** (decisão do usuário: backend, frontend e infra
containerizados). Greenfield — todas as escolhas abaixo foram confirmadas por entrevista.

## Decisões confirmadas (entrevista)

| Área | Escolha |
|------|---------|
| Backend | **Java 21 + Spring Boot 4** (Spring Framework 7), **Maven**, **módulo único (monólito)** |
| Pacote base / artefato | `com.fksoft.erp` / artifactId `erp` (groupId `com.fksoft`) |
| Frontend | **Angular (TypeScript)** — última versão estável |
| Banco | **PostgreSQL** + Spring Data JPA + **Flyway** |
| Observability | **Prometheus** (métricas) + **Loki** (logs) + **Tempo** (traces) + **Grafana** (dashboards/visualização) |
| Orquestração | **Tudo em Docker Compose** (backend e frontend com Dockerfile) |
| Estrutura | Monorepo: `/backend` `/frontend` `/infra` |

## Estrutura de pastas alvo

```
fkerp-poc/
├─ .claude/                    # config do Claude versionada no repo (ver seção abaixo)
│  ├─ settings.json            # settings de projeto (commitado)
│  ├─ plans/<este-plano>.md    # cópia canônica deste plano
│  ├─ memory/                  # espelho versionado da memória do projeto (MEMORY.md + *.md)
│  ├─ commands/                # placeholder p/ slash commands futuros
│  └─ agents/                  # placeholder p/ subagents futuros
├─ docker-compose.yml          # na raiz (orquestra tudo; referencia ./infra para configs)
├─ .env.example                # template de variáveis (commitado)
├─ .env                        # valores reais (GITIGNORADO)
├─ .gitignore                  # ignora .env e .claude/settings.local.json + build artifacts
├─ README.md                   # atualizar com instruções de subida
├─ backend/
│  ├─ pom.xml                  # Spring Boot 4, Java 21
│  ├─ Dockerfile               # multistage: maven build -> JRE 21
│  ├─ .dockerignore
│  └─ src/
│     ├─ main/java/com/fksoft/erp/
│     │  └─ ErpApplication.java             # prova de vida via Actuator (/actuator/health)
│     ├─ main/resources/
│     │  ├─ application.yml                  # datasource, actuator, tracing, logs JSON
│     │  └─ db/migration/V1__init.sql        # baseline Flyway (tabela mínima de health)
│     └─ test/java/com/fksoft/erp/ErpApplicationTests.java
├─ frontend/                   # workspace Angular (gerado via Angular CLI)
│  ├─ Dockerfile               # multistage: node build -> nginx
│  ├─ nginx.conf               # serve estático + proxy /api -> backend:8080
│  ├─ .dockerignore
│  └─ src/app/...              # página de welcome
└─ infra/
   ├─ prometheus/prometheus.yml
   ├─ loki/loki-config.yml
   ├─ tempo/tempo.yml
   ├─ alloy/config.alloy        # coleta logs dos containers -> Loki
   └─ grafana/
      ├─ provisioning/datasources/datasources.yml   # Prometheus + Loki + Tempo
      ├─ provisioning/dashboards/dashboards.yml
      └─ dashboards/spring-boot.json
```

> Nota: `docker-compose.yml` fica na **raiz** (para `docker compose up` funcionar direto),
> com os arquivos de configuração de infra dentro de `/infra`. Se preferir o compose dentro
> de `/infra`, é um ajuste simples — me avise.

## Detalhes de implementação

### Backend (Spring Boot 4 / Java 21)
- `spring-boot-starter-parent` **4.0.x**, Java 21 (Boot 4 exige Java 17+; 21 LTS ok).
- Dependências: `web`, `data-jpa`, `actuator`, `validation`, `postgresql` (runtime),
  `flyway-core` + `flyway-database-postgresql`, `micrometer-registry-prometheus` (runtime),
  `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`, `spring-boot-starter-test`.
- **Sem endpoint custom**: a prova de vida do backend é o **Actuator** (`/actuator/health` = `UP`).
  Nenhum `HelloController`/controller de exemplo é criado.
- `application.yml`:
  - Datasource → `jdbc:postgresql://postgres:5432/erp`; `jpa.hibernate.ddl-auto=validate` (Flyway é dono do schema).
  - Actuator: expõe `health,info,prometheus,metrics`; `health.show-details=always`.
  - Tracing: `management.tracing.sampling.probability=1.0`; OTLP → `http://tempo:4318/v1/traces`.
  - Logs: **structured logging JSON no stdout** (`logging.structured.format.console`) — recurso nativo do Boot.
- `V1__init.sql`: migration baseline mínima (ex.: tabela `app_info`) só para validar Flyway + JPA + Postgres.

### Frontend (Angular)
- Workspace gerado via Angular CLI (última versão estável, standalone components).
- A **própria página inicial é a welcome page**, exibindo apenas o nome do sistema **FKERP**
  (sem chamadas ao backend nesta etapa).
- `Dockerfile` multistage: `node:lts-alpine` (`npm ci` + `ng build`) → `nginx:alpine` servindo `dist/`.
- `nginx.conf`: serve o SPA (com `try_files` para roteamento). Proxy de `/api/*` → `backend:8080`
  fica para quando existirem endpoints de negócio (não necessário agora).

### Observability (stack Grafana, tudo em containers)
- **Prometheus**: faz scrape de `backend:8080/actuator/prometheus` e de si mesmo.
- **Tempo**: recebe traces via OTLP (gRPC 4317 / HTTP 4318) vindos do backend.
- **Loki**: armazena logs. Coleta feita pelo **Grafana Alloy** lendo os logs dos containers Docker
  (monta o socket do Docker) e empurrando para o Loki — desacopla o envio de logs da versão do
  Spring Boot (mais robusto que um appender Logback dedicado, dado que o Boot 4 é muito recente).
- **Grafana**: datasources (Prometheus, Loki, Tempo) e 1 dashboard de JVM/Spring **provisionados**
  automaticamente, além de Explore para logs (Loki) e traces (Tempo). Sem login manual de setup.

### Docker Compose (serviços)
`postgres`, `backend`, `frontend`, `prometheus`, `loki`, `tempo`, `grafana`, `alloy`.
Portas no host (padrão): frontend `4200`→80, backend `8080`, Grafana `3000`, Prometheus `9090`,
Loki `3100`, Tempo `3200`/`4317`/`4318`, Postgres `5432`. `depends_on` + healthchecks onde fizer sentido
(backend espera Postgres saudável). Rede e volumes nomeados (Postgres e Grafana).

## Pasta `.claude` no projeto (versionada)

A pasta `.claude` ficará **dentro do repositório** e **commitada no git** (decisão do usuário):
- `settings.json` — settings de projeto compartilhados (commitado).
- `plans/` — cópia canônica deste plano (e dos próximos); mantida em sincronia daqui pra frente.
- `memory/` — **espelho versionado** da memória do projeto (`MEMORY.md` + arquivos `*.md`).
  Ressalva: o *recall* automático do harness lê da home (`~/.claude/projects/.../memory/`); este
  diretório no projeto é um espelho para versionar — manterei os dois em sincronia ao gravar memória.
- `commands/` e `agents/` — placeholders (com `.gitkeep`) para slash commands e subagents futuros.
- `settings.local.json` (se existir) fica **fora** do git via `.gitignore` (config local/sensível).

## Configuração de ambiente — `.env` e `.gitignore`

- **`.env`** (raiz): consumido automaticamente pelo Docker Compose. Contém variáveis como
  `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, portas dos serviços, `GF_SECURITY_ADMIN_USER`,
  `GF_SECURITY_ADMIN_PASSWORD`. **Gitignorado**.
- **`.env.example`** (raiz): mesmo conjunto de chaves com placeholders/defaults de POC. **Commitado**,
  serve de template (`cp .env.example .env`).
- O backend recebe as credenciais via variáveis de ambiente do Compose (ex.: `SPRING_DATASOURCE_URL`,
  `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`), sem hardcode no `application.yml`.
- **`.gitignore`** (raiz) cobre: `target/` (Maven), `node_modules/`, `dist/`, `.angular/` (Angular),
  `.env`, `.claude/settings.local.json`, arquivos de IDE (`.idea/`, `.vscode/`), `*.log` e arquivos de SO.
  (O Angular CLI também gera um `frontend/.gitignore` próprio, que será mantido.)

## CLAUDE.md — conteúdo completo e organizado (pedido do usuário)

**Decisão final do usuário**: *tudo* o que foi definido nesta sessão vai **dentro do `CLAUDE.md`**, cada
item na sua seção certa e organizado. **Nada fica de fora** e nada é duplicado em memória — como o
`CLAUDE.md` é versionado e carregado em toda sessão, ele é a fonte única de verdade.

O trecho atual ("ainda não foi scaffoldado") será substituído por esta estrutura de seções:

1. **Visão geral** — FKERP é um ERP (em fase de POC); monorepo já scaffoldado com backend, frontend e
   observability no ar. (Domínio do ERP ainda não modelado.)
2. **Commands** — pré-requisito `cp .env.example .env`; subir tudo com `docker compose up --build`;
   backend `mvn spring-boot:run`, `mvn test`, teste único `mvn test -Dtest=Classe#metodo`; frontend
   `npm start` / `ng serve`, `ng build`, `ng test`; tabela de URLs/portas (frontend 4200, backend 8080,
   Grafana 3000, Prometheus 9090, Loki 3100, Tempo 3200, Postgres 5432).
3. **Architecture** — monorepo `/backend` `/frontend` `/infra` (papel de cada pasta); backend
   Spring Boot 4 / Java 21 / Maven (módulo único, pacote base `com.fksoft.erp`, prova de vida via
   Actuator, sem controller custom); frontend Angular (página inicial = welcome com o nome **FKERP**);
   persistência PostgreSQL + Spring Data JPA + Flyway; observability stack Grafana (Prometheus = métricas
   via Micrometer/Actuator, Loki = logs via Grafana Alloy, Tempo = traces via OTLP, Grafana =
   dashboards/datasources provisionados); orquestração via Docker Compose.
4. **Repository conventions** — `.claude/` versionado no git (settings/plans/memory/commands/agents),
   com `settings.local.json` no `.gitignore`; `.env` gitignorado + `.env.example` commitado, credenciais
   injetadas via variáveis de ambiente no Compose.
5. **Working with the user** — sempre entrevistar antes de agir; nunca criar nada sozinho; perguntar
   sempre que houver dúvida; **ao concluir cada implementação, entregar um relatório completo do que foi
   implementado** (arquivos criados/alterados, decisões, como rodar, como validar). Vale para todas as iterações.
6. **Git workflow (gitflow)** — branches `main` (produção), `develop` (integração) e
   `feature/*` `release/*` `hotfix/*`; **nunca commitar direto em `main`**; cada iteração na sua própria
   branch, **já valendo nesta primeira** (o scaffolding sai numa `feature/*` a partir de `develop`).
7. **Maintenance** — toda mudança de arquitetura ou design deve atualizar o `CLAUDE.md` na mesma alteração.

## Ordem de execução

1. **Gitflow**: criar `develop` a partir de `main` e a branch `feature/project-bootstrap` a partir de
   `develop`; **toda a implementação acontece nessa feature branch** (nada direto em `main`).
2. Criar `.gitignore` (raiz), `.env.example` + `.env`, e o esqueleto de pastas.
3. Criar a pasta `.claude/` no projeto: `settings.json`, `plans/` (mover este plano), `memory/`
   (estrutura p/ memória futura, com `MEMORY.md`), `commands/` e `agents/` (com `.gitkeep`).
4. Backend: `pom.xml`, `ErpApplication`, `application.yml` (lendo creds via env), migration Flyway,
   teste de contexto, `Dockerfile` (sem controller; prova de vida via Actuator).
5. Frontend: gerar workspace Angular, página inicial com o nome **FKERP**, `Dockerfile` + `nginx.conf`.
6. Infra: configs de Prometheus, Loki, Tempo, Alloy e provisioning do Grafana (datasources + dashboard).
7. `docker-compose.yml` na raiz amarrando tudo (consumindo o `.env`).
8. Atualizar `README.md` com instruções (`docker compose up --build`) e URLs de cada serviço.
9. **Reescrever o `CLAUDE.md`** com as 7 seções organizadas — Visão geral, Commands, Architecture,
   Repository conventions, Working with the user, Git workflow, Maintenance (ver seção "CLAUDE.md — conteúdo completo").
10. `docker compose up --build` e validar a cadeia inteira (ver Verificação).
11. **Entregar relatório completo** da implementação (arquivos, decisões, como rodar/validar) e
    confirmar com o usuário antes de qualquer commit/merge para `develop` ou push.

## Verificação (end-to-end)

Após `docker compose up --build`:
- **Frontend**: `http://localhost:4200` mostra a página inicial com o nome **FKERP**.
- **Backend**: `http://localhost:8080/actuator/health` = `UP` (com detalhe do Postgres);
  `http://localhost:8080/actuator/prometheus` lista métricas.
- **Prometheus**: `http://localhost:9090` → Targets, alvo `backend` = `UP`.
- **Grafana**: `http://localhost:3000` → datasources Prometheus/Loki/Tempo verdes; dashboard JVM com dados;
  Explore→Loki mostra logs do backend; Explore→Tempo mostra um trace.
- **Gerar tráfego**: acessar `http://localhost:8080/actuator/health` (e a página do front) algumas
  vezes e confirmar métricas (request count), logs (Loki) e ao menos 1 trace (Tempo) no Grafana.
- **Build do backend**: `cd backend && mvn -q test` (teste de contexto sobe).

## Pontos de atenção / riscos

- **Spring Boot 4 é muito recente (GA nov/2025)**: por isso logs vão via Alloy (infra) e não por
  appender de terceiros — minimiza dependência de libs que ainda possam não suportar o Boot 4.
- Primeiro `up` baixa imagens e builda Maven/Angular — pode demorar alguns minutos.
- Endpoint/protocolo OTLP do Tempo (HTTP 4318 vs gRPC 4317) será conferido contra a config do Tempo na implementação.

## Confirmado pelo usuário durante o plano
- "Vou usar Docker também, pode criar o compose. Banco de dados postgresql" → já refletido (tudo em Compose + Postgres).
