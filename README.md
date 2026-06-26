# FKERP — a proof of concept of pair programming with Claude Code

FKERP is a working **ERP built end‑to‑end as an experiment**: *what does it look like to develop real
software with [Claude Code](https://claude.com/claude-code) as a pair programmer, with a human owner in
command, while deliberately emulating professional engineering discipline and Extreme Programming (XP)?*

This repository **is the answer**. It is not a tutorial or a toy scaffold — it is a coherent, layered,
tested commercial ERP (CRM → Sales → Booking → Financial → Commission) that was grown one vertical slice
at a time, where **I drove and decided, and the AI did the typing** against an executable engineering
contract. The result is meant to be read, run, and judged like any other codebase.

> **Status:** application version **0.72.2** — six functional sprints delivered (see *What is actually
> built* below). The product is genuinely functional, but it remains a **proof of concept**: it was never
> hardened for production, it carries no real customer data, and some operational concerns are intentionally
> out of scope.

---

## The experiment

The goal was not "let the AI write an app." The goal was to find out whether an AI agent, kept on a short
leash by a written engineering contract and a disciplined process, can produce software that an experienced
engineer would actually accept — and to do it as a **pair**, not a vending machine.

The collaboration mirrored **XP pair programming** with the roles made explicit:

- **I was the navigator / owner (the human in command).** I set direction, sliced the work, made every
  product and business decision, chose the architecture trade‑offs, answered the "this rule is missing — ask
  before inventing" questions, and reviewed and accepted (or rejected) each increment. Nothing about the
  *what* and the *why* was delegated.
- **Claude Code was the driver.** It wrote the code, the tests, the migrations and the docs, ran the build
  and the test suites, and proposed designs — but always **within the contract**, never expanding scope or
  inventing business rules on its own.

The single most important artifact of the experiment is **[CLAUDE.md](CLAUDE.md)**: a normative, executable
*operating contract* that encodes the engineering rules the agent must obey (architecture, layering,
validation, testing, persistence, security, delivery). It is written for the agent, not for a human reader,
and **the tooling enforces large parts of it** (ArchUnit, Spring Modulith, Spotless, Checkstyle, CI gates).
When code and the contract disagreed, the contract won; when the contract was wrong, we changed the contract
deliberately and recorded why.

### How XP showed up in practice

| XP practice | How it was applied here |
|---|---|
| **Pair programming** | Human navigator + AI driver, every change (see above). |
| **Test‑Driven Development** | Tests first, then the code that makes them pass — *the tests are treated as more important than the code*. Happy path **and** every plausible sad path (401/403/400/404/409/422, visibility, idempotency, edges), across unit, integration, architecture, frontend component/guard/interceptor, and E2E. |
| **Small releases / Continuous Integration** | Work shipped in thin **vertical slices**, each merged to `develop` and `main` and the dev stack rebuilt, so `localhost:4200` always reflects reality. The app carries a real **SemVer** version, bumped on every change. |
| **Simple design (YAGNI)** | "Rule Zero — avoid overengineering": the simplest solution that satisfies the requirement and the tests. No speculative patterns, layers, or abstractions. A simple CRUD stays simple. |
| **Refactoring** | Safe because behavior is protected by tests and architecture gates; the contract itself was refactored when reality demanded it (e.g. a configurable‑workflow engine was built, then deliberately reverted as overhead). |
| **Coding standards** | Not a style guide — **executable gates** bound to the build (formatting, imports, Javadoc on public APIs, method/param limits, no field injection, no `*Impl`, no leaking entities). |
| **Collective ownership / metaphor** | One contract (`CLAUDE.md`), one ubiquitous domain language, one navigation model — the source of truth that keeps every part consistent. |
| **Sustainable pace** | One slice at a time, each *fully* done (code + tests + migration + i18n + docs + delivery) before the next. "Done" means done, not "a report says done." |

### The per‑slice delivery ritual

Every slice followed the same loop, end to end, with nothing skipped:

1. Read the contract and the current state; plan the thinnest slice.
2. **Write the failing tests first**, then the implementation, across all relevant layers.
3. Add the Flyway migration (if the schema changed) and the i18n messages (for any user‑facing text).
4. Run the **full backend `verify`** (incl. ArchUnit + Spring Modulith), the **frontend tests + build**,
   and the **full E2E cycle** on an **isolated, throwaway stack**.
5. Update the bilingual **user manual** and the agent contract; bump the **version**.
6. Commit on a `feature/*` branch, merge to `develop` + `main`, push, and rebuild the dev stack.
7. Produce a written **development report**; a customer **release note** at each sprint close.

---

## What is actually built

FKERP models the commercial pipeline of a travel‑style agency, as a series of **bounded contexts** that hand
off to one another while staying separated. Each sprint is a complete, validated business capability:

- **Sprint 1–2 — CRM (`domain.crm`)**: Leads (capture, assignment, qualification, loss, interactions,
  pending‑work worklists, indicators) and Opportunities (a strict forward funnel, commercial details,
  activities, loss, pending lists, indicators). Escalating read tiers + operation scopes per profile.
- **Sprint 3 — Sales & Proposals (`domain.sales`)**: Commercial Proposals from a qualified Opportunity —
  items, discounts, totals, submit/approve/send/accept lifecycle — graduating into **Commercial Orders**.
- **Sprint 4 — Booking Operations (`domain.booking`)**: a back‑office, manual reservation process — booking
  requests from a confirmed order, per‑item manual attempts and confirmations (travel package / car rental),
  failures, status consolidation reflected (read‑only) onto the order, pending worklist and indicators.
- **Sprint 5 — Financial Operations (`domain.financial`)**: Receivables from a confirmed‑booking order —
  installments, full/partial payments, payment reversal, overdue identification (a daily scheduled job),
  status reflected onto the order, operational view and indicators.
- **Sprint 6 — Commission Management (`domain.commission`)**: commission rules, expected‑commission
  generation, eligibility once the receivable is paid, approval, reject/cancel, manual payment + reversal,
  statement by beneficiary, status reflected onto the order, operational view and indicators.

A **Customer** is materialized automatically from its source Lead when a deal closes. Throughout, the order
stays owned by Sales: Booking, Financial and Commission only **reflect** their status onto it (read‑only) and
never write it — the aggregates remain distinct, and every step preserves a full, traceable handoff record
for the next.

Authorization is **scope‑based** (OAuth2 JWT scopes, e.g. `crm:lead:read:all`, `commission:approve`) bundled
into profiles; the backend is always the only authority and the frontend merely mirrors it.

---

## Tech stack

- **Backend** — Java 21 (LTS), Spring Boot 4, Spring Modulith, Spring Web MVC, Spring Data JPA, Bean
  Validation, Spring Security + OAuth2 Resource Server (JWT), Flyway + PostgreSQL, springdoc‑openapi,
  Micrometer + Prometheus + Actuator, structured JSON logging, Lombok. Build with the Maven wrapper only.
- **Frontend** — Angular (standalone components, **zoneless** + signals, no NgModules, no NgRx), PrimeNG +
  Tailwind, runtime i18n. Workflow‑oriented navigation (Comercial · Reservas · Acompanhamento · Cadastros),
  a command palette, keyboard shortcuts, and unsaved‑changes protection on every form.
- **Testing** — JUnit + Mockito + AssertJ, **Testcontainers** (a real Postgres, shared across the suite),
  **ArchUnit** + `spring-modulith-starter-test` for architecture, JaCoCo coverage; **Vitest** for the
  frontend (DOM‑state assertions, jsdom); **Playwright** for E2E against an isolated stack.
- **Observability** — Prometheus, Loki, Grafana Alloy and Grafana, provisioned via Docker Compose.

---

## Architecture in one breath

Pragmatic **modular hexagonal + DDD**, organized by business area under the base package `com.fksoft`:

- `com.fksoft.domain.<area>` — the pure core (model, repository, service, `service.data` read models,
  exceptions). It **must not** depend on delivery or infrastructure (ArchUnit‑enforced).
- `com.fksoft.application` — delivery adapters (REST controllers, request DTOs, realtime), organized by
  mechanism, not by area.
- `com.fksoft.infra.<concern>` — driven adapters + config (security, web/error handling, i18n, time,
  observability), implementing ports defined in the domain.

Deliberately **no CQS**: one application service per area serves both commands and reads, returning read
models from `service.data` straight through the controller (entities never leak). **Defense in depth**:
every write flow validates at every layer it crosses (form → controller → service → entity → database
`CHECK`/`UNIQUE`/FK). The full rationale lives in **[CLAUDE.md](CLAUDE.md)**.

---

## Repository layout

```
backend/         # Spring Boot 4 / Java 21 / Maven (com.fksoft.erp); build via ./mvnw
frontend/        # Angular (zoneless + signals, PrimeNG, Tailwind, Playwright)
infra/           # observability config (Prometheus, Loki, Alloy, Grafana)
artifacts/       # human-facing documentation (see below)
compose.yaml     # the development stack (frontend 4200)
compose.e2e.yaml # the isolated, throwaway E2E stack (frontend 4201, ephemeral Postgres)
CLAUDE.md        # the executable engineering contract — the heart of the experiment
```

### `artifacts/` — the paper trail

- `artifacts/development-reports/` — a **technical report per slice** (the engineering record).
- `artifacts/release-notes/` — a **customer‑facing release note per sprint** (business language, pt‑BR).
- `artifacts/user-manual/` — the end‑user manual, maintained in **both en‑US and pt‑BR**.

---

## Running it

Prerequisites: **Docker + Docker Compose**. (For local dev outside Docker you also want Java 21 and Node 22;
the backend always uses the Maven wrapper `./mvnw`.)

```bash
cp .env.example .env        # first time only
docker compose up --build   # builds and starts the whole stack
```

| Service          | URL                                        |
|------------------|--------------------------------------------|
| Frontend         | http://localhost:4200                      |
| Backend          | http://localhost:8080                      |
| Backend health   | http://localhost:8080/actuator/health      |
| Backend metrics  | http://localhost:8080/actuator/prometheus  |
| Version endpoint | http://localhost:8080/api/version          |
| Grafana          | http://localhost:3000 (admin / admin)      |
| Prometheus       | http://localhost:9090                      |
| Loki             | http://localhost:3100                      |
| PostgreSQL       | localhost:5432                             |

In Grafana, the datasources (Prometheus, Loki) and a backend overview dashboard are pre‑provisioned; logs are
under **Explore → Loki**.

### Demo logins

The database is seeded (via Flyway) with one user per profile. Passwords are the username followed by `123`:

| Username        | Password           | Profile (representative scopes)                                  |
|-----------------|--------------------|-----------------------------------------------------------------|
| `comercial`     | `comercial123`     | Commercial **manager** — broad read/write across the pipeline   |
| `vendedor`      | `vendedor123`      | **Seller** — own + unassigned leads/opportunities               |
| `representante` | `representante123` | **Representative** — own records only                           |
| `diretor`       | `diretor123`       | **Board/Director** — consultation (read‑all, no operations)     |
| `financeiro`    | `financeiro123`    | **Finance** back‑office — receivables, payments, commissions    |
| `operacoes`     | `operacoes123`     | **Operations** back‑office — booking operator                   |

These are **development seeds for the POC** — not credentials for any real system.

---

## Local development

Backend (in `backend/`, always via the wrapper):

```bash
./mvnw spring-boot:run                  # needs a reachable PostgreSQL
./mvnw verify                           # build + gates (Spotless, Checkstyle, ArchUnit, Modulith) + tests (needs Docker)
./mvnw test -Dtest=ErpApplicationTests  # a single test
./mvnw spotless:apply                   # format the code
```

Frontend (in `frontend/`):

```bash
npm install
npm start        # http://localhost:4200
npm test         # unit/component tests (ng test / Vitest)
npm run build    # production build
```

### End‑to‑end tests (Playwright)

E2E runs against an **isolated, throwaway stack** (`compose.e2e.yaml`) whose Postgres is **ephemeral**
(`tmpfs`) and on its own ports (frontend **4201**), so it **never touches the development database**. From
`frontend/`:

```bash
npm run e2e:up     # build + start the isolated stack at http://localhost:4201
npm run e2e        # run the tests (baseURL 4201; override with E2E_BASE_URL)
npm run e2e:down   # tear it down (the ephemeral Postgres is discarded)
```

The dev stack (`compose.yaml`, port 4200) and the E2E stack (4201) can run side by side.

---

## Quality gates

These are bound to the build and CI, and were **never weakened to make code pass** — a failing gate was a
signal to fix the code (or, deliberately, the contract):

- **ArchUnit** — the domain may not depend on delivery/infra; controllers may not touch repositories; no
  `@Data`/`@Setter` on entities; no field injection; no `*Impl`; exceptions live with their domain.
- **Spring Modulith** — verifies the modular structure; a build‑time test fails if any domain exception lacks
  an HTTP status mapping.
- **Spotless (palantir‑java‑format) + Checkstyle** — formatting, imports, Javadoc on public APIs, method and
  parameter limits, and more, failing the build on violation.
- **Coverage** — JaCoCo (backend) and Vitest v8 (frontend), reporting‑only.

---

## A note on authorship and honesty

The code in this repository was **written by Claude Code (Claude Opus)** under my direction, as the
experiment describes. I take ownership of the product and architecture decisions; the agent takes the
keyboard. Outcomes here are reported faithfully — including the handful of known, documented test flakes
noted in the development reports. If you're evaluating "can you build something real this way?", read the
[CLAUDE.md](CLAUDE.md) contract first, then pick any slice's development report and follow it into the code
and its tests.
