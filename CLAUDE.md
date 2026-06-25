# CLAUDE.md - Operating Rules & Architecture

Operating contract for this repository. Audience: the coding agent, not a human reader.
Rules are normative: `MUST` mandatory, `MUST NOT` forbidden, `SHOULD` recommended default
(deviation needs a reason), `MAY` allowed when justified. This file is the contract; when it
conflicts with existing code, the file wins (see Authority order). Stack assumes Java/Spring on
the backend and Angular on the frontend; the base package is `com.fksoft`.

## 1. Invariants (govern every task)

1. **Rule Zero - avoid overengineering.** Architecture exists to reduce the cost of change.
   Patterns, layers, abstractions, queues, caches and interfaces exist only when they solve a
   real, present problem. A simple CRUD stays simple. When in doubt, pick the simplest solution
   that satisfies the requirement and tests. Current need over speculative future need.
2. **Authority order:** `current owner/user request > this file > existing code`. Existing code
   is evidence, not authority. Peer preference, market fashion, cargo-cult patterns and
   undocumented convention are NOT sources of truth.
3. **Never invent business rules.** If missing information affects behavior, contracts, data,
   security or architecture: ASK before implementing. Never close a gap by guessing or by
   silently inventing behavior - surface the divergence.
4. **Never ship fake behavior; defer cleanly.** Mocks/stubs are for tests and explicitly
   deferred seams, never misleading results on production paths. When a requirement is out of
   scope or undecided, do not expand scope and do not invent the rule - mock the seam and defer
   the real implementation to its owner or a follow-up. A good mock is explicit, traceable,
   narrow and replaceable; record the deferral where work is tracked.
5. **Tooling is authoritative.** ArchUnit, Spring Modulith `verify()`, Spotless, Checkstyle and
   CI gates encode these rules executably. Never weaken, skip or delete a gate to make code
   pass. If a rule seems wrong, raise it with the owner and update this file - do not bypass.
6. **No loose ends.** No TODO/FIXME without a tracked reference; no commented-out code; no
   incomplete implementations; no `@Data`/`@Setter` on JPA entities (Lombok
   `@Getter`/`@RequiredArgsConstructor`/`@Slf4j` welcome for boilerplate); no `*Impl` naming;
   constructor injection only.
7. **Tests come first - quality, not a report (owner mandate, highest priority).** Treat tests as
   **more important than the code itself**: the code exists to make the tests pass, not the other way
   round. Work **TDD** - write the failing test first, then the implementation. Every behavior is covered
   on its **happy path AND all plausible sad paths**, in **every** category (domain/unit, integration with
   real infra, architecture, frontend **component DOM-state**, service/guard/interceptor, E2E). Tests
   assert **real behavior and visible state**, never just a status or a render-happened. A "test is not a
   report, it is quality" - coverage is improved by writing **real** tests (including rendering each
   screen's loading / empty / error / permission-denied / success states), never by gaming the number or
   by explaining it away. No fake/assertion-free/skipped tests. This invariant outranks delivery speed:
   when in doubt, write the test. (Enforced through §3 and §13.)
8. **Reference data is data-driven; lifecycles are enums — owner mandate (revised, supersedes the prior
   "configurable workflow" reform — owner reverted it).** A value set that is genuinely *reference data* MUST
   be a **cadastro** (a DB-backed, admin-editable list), not a hardcoded `enum`. A *lifecycle* (states +
   transitions), by contrast, is a **fixed `enum` state machine with pre-defined transition methods on the
   entity** — there is **no configurable-workflow engine** (the `domain.workflow` engine, the visual editor
   and the `workflow:manage` scope were removed; the transitions are structural, not admin-editable, so the
   abstraction was pure overhead). The *attention rules* that flag why a record needs action (the
   pending-items worklists) are **hardcoded** in the owning domain (e.g. `PendingLeadReasons`,
   `BookingRequestPendingReasons` + their mirroring `*PendingSpecifications`), **not** configurable rows.
   Apply **the cadastro-vs-enum test**:
   - **CADASTRO** when the value is a rótulo/motivo/tipo/canal an admin can **add/rename/deactivate without
     code** and **no logic branches** on the specific value (loss/rejection reasons, activity/attempt
     types & results, channels, failure reasons, **item type**). New such lists → a cadastro in the owning
     domain over the `domain.reference` kernel, id-based contract `{code,label}`, `active`→422.
   - **ENUM** for everything **structural/behavioral**, which now explicitly **includes every lifecycle
     status/stage** (`LeadStatus`, `OpportunityStage`, `ProposalStatus`, `CommercialOrderStatus`,
     `BookingRequestStatus`, `BookingItemStatus`): the code `switch`es on it, calculates with it
     (`DiscountType.amountOf`), maps it structurally (`BookingNeed.toStatus`), anchors a coded flow
     (`confirmTravelPackage` on `TRAVEL_PACKAGE`), enforces the legal transitions on the entity (qualify
     needs CONTACTED, the funnel advances one step via `OpportunityStage.next()`), or is a **computed set**
     the code produces. Adding such a value requires programming → it is not reference data. The status is
     persisted as the enum name (`@Enumerated(STRING)`, mirrored by a DB `CHECK`), so the JSON contract is
     the same string.
   The admin manages **only the cadastros**, via `reference:manage`. New features follow this in §2 (Decision
   protocol) and §3 (Definition of Done).

## 2. Decision protocol

Before adding any complexity, ask: does this solve a real problem? Is it proportional to the
risk? Can it be simpler? Is there an existing pattern in this repo to reuse? Do tests protect
the behavior? Does it preserve owner direction? Default mindset: domain first, clarity first,
production awareness always, overengineering never.

**Reuse before creating.** Search for an existing equivalent (error response, user-context
provider, file storage, email sender, pagination envelope) before introducing a parallel
mechanism. MUST NOT introduce a second way to do something the repo already does.

**When existing code contradicts a rule or request,** reconcile deliberately - do not blindly
rewrite. Decide explicitly: is the code outdated, the rule outdated, an exception warranted, or
is the request an intentional change? State which.

**Exceptions** are allowed only for real reasons: explicit owner decision, current request,
client/regulatory/contractual constraint, legacy or framework limitation, measured performance
need, operational/security constraint, or migration strategy. An exception MUST NOT exist
because a peer prefers another style or a tool generated it. An exception that affects
architecture, persistence, integration, messaging, security, deployment or module boundaries
MUST be recorded in this file and MUST NOT become a new default unless the owner updates it.

## 3. Definition of Done (every meaningful change)

- Code matches the agreed requirement; this file updated if a rule changed.
- Tests created/updated **test-first (TDD, §13)**, covering the happy path AND all plausible sad paths in
  every relevant category; no fake/assertion-free tests. A bug fix MUST add a regression test that fails
  before and passes after; if impossible, explain why.
- Flyway migration when the schema changes. OpenAPI/docs updated when contracts change.
- i18n messages added for any user-facing text; global error handling respected.
- **Frontend feature parity (§12):** any new user-facing feature ships its **keyboard access**
  (command-palette entry + a shortcut for primary destinations/actions, listed in the `?` overlay)
  and, for any form/edit surface, its **unsaved-changes protection** (route guard + dialog/`beforeunload`).
- Build and tests executed when possible. Never hide a failed command or a skipped check.
- Final response reports: files changed, behavior implemented, tests, migrations, contract
  impacts, commands executed, verification result, risks, pending items.

## 4. Stack

Choose stable/LTS; no experimental/milestone/RC/snapshot dependencies in production. Do not add
a library for a trivial problem; significant dependencies are raised with the owner first.

- **Backend:** Java (LTS), Spring Boot, Spring Modulith (`detection-strategy=explicitly-annotated`),
  Spring Web MVC, Spring Data JPA, Bean Validation, Spring Security + OAuth2 Resource Server
  (JWT), Spring WebSocket (STOMP) when realtime is needed, Spring Mail, Flyway + PostgreSQL,
  springdoc-openapi, Micrometer + Prometheus, Actuator, structured JSON logging, Lombok
  (boilerplate: `@Slf4j`, `@RequiredArgsConstructor`, entity `@Getter`; `lombok.config` sets
  fluent accessors).
- **Test:** JUnit, Testcontainers (Postgres), ArchUnit (core API), `spring-modulith-starter-test`.
  Build with the Maven wrapper only (`./mvnw`); Spotless + Checkstyle bound to `verify`.
- **Frontend:** Angular (standalone components + signals, no NgModules, no NgRx by default),
  a component library + Tailwind for layout, runtime i18n. Test/lint: the framework defaults.
- **Runtime:** single deployable per environment unless scale-out is justified. Monorepo:
  `backend/ frontend/ infra/ artifacts/ compose.yaml README.md` (`artifacts/` holds human-oriented
  documentation, as opposed to this agent-facing contract).

## 5. Backend architecture

### 5.1 Layers & package layout

Pragmatic modular **hexagonal + DDD**, organized by business domain. Hexagonal is a principle,
not folder theater; Spring/JPA/Bean-Validation annotations are acceptable. Three top-level
layers under `com.fksoft`:

| Layer | Package | Contents |
|---|---|---|
| **Domain** (pure core) | `com.fksoft.domain.<area>` | Organized by business area; each area split into role sub-packages: `model` (entities, enums, value objects, domain events + logic), `repository` (Spring Data repositories, projections, query objects), `service` (Application Services — **command AND read** — + policies + specifications), `service.data` (the service's data records: **command/criteria inputs AND read-model outputs** like `LeadDetail`/`LeadListItem`), `exception` (business exceptions). **No `dto` package in the domain.** Plus kernel `domain.error` (`DomainException`, `ErrorDetails`, `RateLimited`). |
| **Delivery** (driving adapters) | `com.fksoft.application` | `api` (controllers) + `api.dto` (request DTOs + trivial create responses); `realtime` (+ `realtime.dto`); `queue` (consumers) if any. Reads are served by the domain service's read models (no read layer here). |
| **Infra** (driven adapters + config) | `com.fksoft.infra.<concern>` | `security` (JWT, `UserContext`/`UserContextProvider` + adapter), `email`, `integration`, `web` (`ApiErrorResponse`, `GlobalExceptionHandler`, `HttpErrorMapping`, `PageResponse`), `i18n`, `time`, `observability`, `socket`. |

```txt
com.fksoft
  domain                         <- pure core; by business area, each area in role sub-packages; MUST NOT import application/infra
    order/
      model/         Order  OrderStatus  OrderCancelled(event)  (entities/enums/events/VOs/logic)
      repository/    OrderRepository  (+ projections, query objects)
      service/       OrderService(commands + reads)  OrderAccessPolicy  OrderSpecifications
      service/data/  CreateOrderCommand  OrderSearchCriteria (inputs)  OrderListItem  OrderDetail (read models)
      exception/     OrderNotFoundException  ...
    <other business areas>/
    error/   DomainException  ErrorDetails  RateLimited        <- kernel
  application                    <- delivery; entities never leak (the read model is the contract)
    api/  api/dto/  realtime/  realtime/dto/
  infra                          <- centralized technical layer, by concern
    security/ email/ integration/ time/ i18n/ socket/ observability/ web/
```

**Dependency rule (ArchUnit-enforced).** `domain` is the only protected layer: it MUST NOT
depend on `application` or `infra`. Both `application` and `infra` MAY depend on `domain`.
`application` MAY depend on `infra`. `infra` MUST NOT depend on `application`. Technical
adapters live in `infra.<concern>` and implement a port defined in the domain module, so the
domain depends on the port, never on infra. There is no `shared` package: error kernel goes to
`domain.error`; identity and `PageResponse` go to `infra`.

**One Application Service per area does commands AND reads — no CQS by default (Rule Zero).** Do
**NOT** split a separate read/query layer (no `application.read`, no `*ReadService`) unless real
complexity justifies it. The domain service registers/mutates **and** serves the list/detail/
indicators, assembling its **read models** (queries, visibility, cross-aggregate enrichment such as
resolving a responsible's name) and returning a record from `<area>.service.data`. A write transition
returns the refreshed detail read model (assembled inside its own transaction). The controller calls
the service and **passes the read model through** — it never touches a repository (the
`controllersMustNotAccessRepositories` gate holds because reads go through the service).

**Entities never leak; the read model is the contract.** The `service.data` records (built from
entities via explicit factories, `LeadDetail.from(lead, names)`) ARE the JSON contract and are
returned straight by the controller. Do **NOT** duplicate them with a parallel `*Response` in
`application.api.dto` (that double-DTO is the overengineering this rule removes); `application.api.dto`
keeps only request DTOs and the trivial create responses the controller builds itself. Services never
return an `@Entity` past the controller boundary.

MUST NOT create `domain/application/ports/adapters/in/out` folder trees unless complexity truly
justifies it. Single Maven project, strong package modularity; multi-module only for shared
libraries, separate deployables, very large codebases or independent ownership.

### 5.2 Services

A module service is an **Application Service**: it coordinates flow, transactions, repositories,
domain behavior and results. It MUST NOT become a dumping ground for business rules - domain
rules live in entities, value objects, enums-with-behavior, policies or domain services. MUST
NOT create explicit `UseCase` or `*Impl` classes by default. Constructor injection only -
prefer Lombok `@RequiredArgsConstructor` (over `final` fields, no field `@Autowired`) and
`@Slf4j` for loggers; keep a hand-written constructor only when params carry `@Value`/`@Qualifier`
or the constructor has logic.

### 5.3 Entities & JPA

JPA entities MAY be domain entities - no artificial domain/persistence split by default. Anemic
models are not acceptable: entities MUST protect invariants and expose meaningful methods.
Entities MAY use Lombok `@Getter` and `@NoArgsConstructor(access = PROTECTED)` for boilerplate
(set `lombok.accessors.fluent = true` so getters stay `title()`), but **NEVER** `@Data` or
`@Setter` - they mutate only through meaningful business methods (both ArchUnit-enforced).
Separate persistence models only for concrete reasons (complex legacy mapping, read/write models
that diverge, critical isolation).

### 5.4 DTOs & mapping

Request/response DTOs MAY be passed to Application Services when it stays simple. MUST NOT create
a `Command` per request by default - use commands only when multiple delivery mechanisms trigger
the same use case or the API shape differs from the use-case input. Prefer records. Map with
explicit factory methods close to the object (`OrderResponse.from(order, …)`); dedicated mapper
classes are not the default (a mapping library MAY be used for repetitive many-field mappings).
**Read models live in the domain, not duplicated in delivery (§5.1):** the read records live in
`<area>.service.data` with a `from(entity, …)` factory; the Application Service supplies the
enrichment (resolved names, history, counts) and the controller returns the read model **directly**
(via `PageResponse` when paged). Do **NOT** add a parallel `*Response` in `application.api.dto` for
reads — that double-DTO is the overengineering this rule removes (Rule Zero).

### 5.5 Validation (defense in depth - protect every boundary)

Protecting boundaries is mandatory. Every action flow MUST validate at **every layer it crosses**;
no layer trusts the previous one. For a write/command flow the full chain below is **required** -
omitting a layer needs a recorded reason (§2 exceptions), never silence:

1. **Frontend (Angular form):** Reactive Forms validators give immediate feedback (required,
   formats, ranges) and disable submit while invalid. UX only - NEVER the security boundary.
2. **Delivery (Controller):** Bean Validation on the request DTO (`@Valid` + `@NotBlank`/`@Email`/
   `@Pattern`/`@Size`/`@NotNull`/custom constraints). Rejects malformed input before the use case.
3. **Application (Service):** preconditions, existence and cross-entity/business checks the DTO
   cannot express (referenced value exists and is active, "at least one contact", uniqueness).
4. **Domain (Entity):** Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Email`,
   …) on business fields **plus** business methods that protect invariants and legal state
   transitions. The entity MUST be valid on its own, independent of controller/service validation.
   (Do not Bean-Validate framework-managed audit fields like `@CreationTimestamp` - rely on the
   DB column constraint there to avoid pre-persist ordering traps.)
5. **Persistence (Database):** the schema is the last guard and MUST mirror the domain /
   Bean-Validation invariants - `NOT NULL`, `UNIQUE`, **`CHECK`** (enum value sets, non-negative
   numbers, "at least one X", digits-only / format), FKs, indexes (Flyway). Add the matching
   `CHECK` whenever a domain rule can be expressed in SQL; DB violations are translated by
   `infra.web` into the standard error body (§5.6), never raw exceptions/500s.

The backend is the authority: frontend validation MUST NOT be the only check, and the domain MUST
NOT depend on delivery validation to stay valid. Validation messages are i18n keys (§5.6). Read
boundaries and integrations (incoming/outgoing data) validate too (constraints, types, ranges).

### 5.6 Errors & i18n

Business errors are explicit, specific exceptions (`OrderCannotBeCancelledException`) extending
the pure `DomainException` (kernel `domain.error`). A `DomainException` carries only domain data:
a stable `code` (== i18n key) + optional message args, and - when needed - extra domain data via
the kernel interfaces `ErrorDetails` (key/value pairs) or `RateLimited` (a `Duration`). Domain
exceptions carry NO transport concern: no HTTP status, no headers, no response DTO.

The presentation layer (`infra.web`) owns HTTP translation: a `@RestControllerAdvice`
`GlobalExceptionHandler` + an `HttpErrorMapping` registry (`Map<Class<? extends DomainException>,
HttpStatus>`). The handler resolves the i18n message and maps `ErrorDetails -> fields`,
`RateLimited -> Retry-After`. A build-time test fails if any `DomainException` subclass lacks a
status. The handler MUST also translate framework validation/persistence failures into the **same**
body so they never leak as raw 500s (standard, not optional): `MethodArgumentNotValidException` and
`jakarta.validation.ConstraintViolationException` (entity/last-resort Bean Validation) -> **400**
with per-field details; `DataIntegrityViolationException` (DB `CHECK`/`UNIQUE`/FK) -> **409** with a
safe generic message, logging the cause server-side but **never** exposing SQL, column or constraint
names. Every API MUST have a global handler and a predictable error body:

```json
{ "code": "order.cannot-be-cancelled", "message": "...", "fields": [] }
```

User-facing messages MUST be internationalized from the start (`messages.properties` +
`messages_<locale>.properties`); the `MessageSource` config lives in `infra.i18n`. Never expose
raw enum names as user-facing labels.

### 5.7 Repositories

Command repositories are aggregate-oriented and SHOULD expose explicit locking methods
(`getRequiredForUpdate(id)` with `@Lock(PESSIMISTIC_WRITE)`) when concurrency risk exists. Read
operations are flexible - projections, views, SQL; do not force aggregate purity on read-only
queries. Spring Data interfaces are natural; do NOT create `interface+Impl` pairs for internal
services. Interfaces are for real ports (external providers, messaging, storage, gateways, cache).

### 5.8 Dates & timezones

UTC for technical instants. `Instant`/`OffsetDateTime` for real instants; avoid `LocalDateTime`
when timezone matters; `LocalDate` for calendar dates; `Duration` vs `Period` by meaning. APIs
use ISO-8601. Never rely on server default timezone. Timezone-sensitive rules MUST be tested.

### 5.9 Naming & documentation

Readable, explicit, domain-oriented names; business language first; technical suffixes when they
clarify (`OrderController`, `OrderCreatedEvent`, `S3FileStorage`, `OrderAccessPolicy`). Avoid
vague names (`Manager`, `Helper`, `Util`, `Data`) and `ServiceImpl`. Events are named as business
facts that happened. Value Objects only when they protect invariants or carry meaning. Enums for
simple statuses; explicit state machines only when workflow complexity justifies; invalid
transitions throw specific business exceptions.

Javadoc REQUIRED for: public module APIs/facades and Application Service public methods; domain
entities' business methods, domain services, policies; business exceptions and integration
ports; any non-obvious logic (validation, concurrency, date/time, security). NOT required for
trivial records/DTOs, simple accessors, delegating controllers, test code. Comments explain
intent, contract, constraints, side effects - never restate the name.

## 6. Domain access & boundaries

The `domain` package is organized by business area (`domain.<area>`), each area split into role
sub-packages (`model`/`repository`/`service`/`service.data`/`exception`, §5.1), but is **internally
open**:
any class under `domain` MAY use any other class under `domain` directly, including across areas and
sub-packages (e.g. `domain.crm.service` may call `domain.identity` repositories/types). The role
sub-packages are organization, not enforced walls — there is **no Facade pattern** for intra-domain
collaboration and **no enforced inter-area/inter-sub-package boundary inside `domain`**. Spring
Modulith is therefore not used to police intra-domain boundaries.

The architectural invariants that MUST hold (all the OTHER rules in this file still apply):

- **`domain` MUST NOT depend on `application` or `infra`** (ArchUnit-enforced). The domain stays
  pure of delivery and technical concerns - this is the boundary that matters.
- **`application` and `infra` MAY depend on `domain`** - preferably through a **port** (interface)
  defined in the domain and implemented by an **adapter** in `infra.<concern>` (ports & adapters /
  hexagonal). Preferred but flexible; do NOT introduce Facades for this.
- Technical concerns (security, email, storage, …) are domain **ports** implemented by infra
  **adapters** (`infra -> domain` allowed; `domain -> infra` forbidden).
- Asynchronous reactions use domain events.
- The `application` (delivery) layer is organized by **mechanism, NOT by business area**:
  controllers live flat in `application.api`, request DTOs + trivial create responses in
  `application.api.dto`, realtime/socket in `application.realtime` (+ `application.realtime.dto`),
  queue consumers in `application.queue`. Reads are served by the domain service's read models (§5.1) —
  there is **no `application.read` / read-service layer** (no CQS by default). Do NOT create
  `application.api.<area>` sub-packages.
- In a monolith a shared database is acceptable - do not pretend to be distributed. Extract a
  microservice only with a clear bounded context plus a concrete reason (independent
  deploy/scale, separate ownership, fault/security isolation). Microservices do not fix bad
  modularity - they distribute it.

## 7. APIs

APIs are external contracts, not accidental exposure of entities or framework structures.

- MUST NOT expose JPA entities - use stable DTOs. Pragmatic REST: domain-action endpoints are
  fine (`POST /orders/{id}/cancel`).
- JSON is part of the contract: never casually change field names/types, enum values, date
  formats, nullability, structure, pagination, error format or status codes.
- Enums exposed in APIs SHOULD have explicit external values; invalid values produce clear
  validation errors. Date/time uses ISO-8601.
- Versioning: prefer backward-compatible changes; a breaking change means a new version + a
  documented deprecation period. OpenAPI MUST document relevant APIs and be updated when
  contracts change.
- GraphQL only for real data-composition needs; gRPC for service-to-service with strong
  contracts; webhooks are serious external contracts (signature, retries, idempotency, logs,
  versioning). A BFF MUST NOT be introduced by default and MUST NOT own business rules; an API
  Gateway is infrastructure, never a hidden business layer.

## 8. Persistence

Database design is architecture. Flyway is the migration tool: every schema change is a
versioned SQL migration (`V<n>__short_description.sql`). Migrations are immutable once
applied/released: never edit an applied migration - add a new one. Never `ddl-auto=update` in
production; never `flyway:clean` outside a throwaway local DB. The database enforces integrity:
PKs, FKs, unique/not-null/check constraints, indexes, isolation, locks, views.

- **Migrations/backfills:** a plain migration suffices for simple changes; for production data,
  large tables or compatibility risk, define the strategy (backfill, expand/contract, window).
- **Seeds:** essential system data MAY go through Flyway; local fake data MUST NOT mix with
  production migrations; tests create their own data via builders/factories.
- **Transactions:** `@Transactional` at the Application Service method that represents the use
  case. Never pretend a message publish or external call is atomic with a DB commit. Eventual
  consistency only with safeguards (idempotency keys, retries, DLQ, outbox/inbox, locking,
  versioning, reconciliation, explicit failure states).
- **Concurrency:** optimistic `@Version` is the default for mutable entities at risk; pessimistic
  locking when risk justifies (financial ops, stock, critical transitions, job claiming).
  Conflicts translate to clear API/domain errors, never raw DB exceptions. For a hot concurrent
  transition, defend in depth: DB unique constraint + a pessimistic row lock scoped to that
  transition + optimistic `@Version` on the aggregate's other transitions.
- **Deletion:** hard delete for disposable data; soft delete or lifecycle status when deletion
  has business meaning; important deletions SHOULD be audited.
- **Heavy reads & search:** use query services, projections, SQL, views, read models - do not
  force every read through aggregates. Large reports run async. Relational DB first; a search
  engine only when requirements justify the operational cost.
- **Caching:** only when it solves a real problem; define key, expiration, invalidation,
  expected consistency, scope, metrics and failure behavior. Stale data is a deliberate trade-off.

## 9. Messaging & integrations

- **Domain events** represent business facts in business language. Publishing events that leave
  the process inside a transaction is risky - use the Outbox Pattern for important events. Once
  consumed by multiple modules/external services, treat the event as a stable contract (eventId,
  type, version, occurredAt, correlationId, payload).
- **Background jobs:** simple ones use `@Scheduled`. Important jobs MUST consider idempotency,
  concurrency control, retry policy, timeout, failure state, history, metrics, logs, safe restart.
- **Idempotency:** only where duplicate execution causes real damage. Prefer DB constraints and
  state checks before building idempotency infrastructure.
- **External integrations:** external systems are unreliable. The domain MUST NOT be shaped by
  external APIs or vendor DTOs - use an Anti-Corruption Layer; vendor DTOs MUST NOT leak into
  domain/application. Every external call MUST have a timeout. Retries are intentional; never
  blindly retry non-idempotent operations. A fallback MUST NOT silently produce misleading
  business results.
- **Files/uploads:** abstract storage (`FileStorage`); business logic MUST NOT depend on storage
  SDKs. Uploads validate size, type, content and authorization - never trust the extension.
- **Notifications:** anything leaving the boundary (email, SMS, push, webhook) is an external
  integration behind an abstraction (`EmailSender`). Important notifications SHOULD be async; a
  completed business transaction SHOULD NOT fail because a provider is down.
- **AI:** isolate providers behind ports; output is probabilistic and MUST be validated before
  affecting business state (schema, types, ranges, business rules, confidence, fallback) and MUST
  be observable (model+version, latency, failure rate, fallback usage, cost/tokens).

## 10. Security

- Spring Security is the default for authentication, authorization, endpoint protection, security
  context and CORS. Do not reinvent auth mechanisms.
- General access control lives in the security config; business authorization that depends on
  domain state/ownership/workflow uses policy classes (`OrderAccessPolicy`) or the Application
  Service. The backend is the final authority - never trust frontend checks.
- Application Services SHOULD NOT touch `SecurityContextHolder` directly - use the centralized
  `UserContextProvider` (userId, roles, tenantId when applicable).
- Privacy hygiene always: never log passwords/tokens/secrets; never expose sensitive internal
  data in API errors; never send secrets to the frontend; mask sensitive values in logs.
- Multi-tenancy only when the product requires it; if real, tenant context propagates through
  HTTP, security, queries, cache keys, logs, jobs and realtime. Tenant data leakage is a critical
  bug; the isolation strategy MUST be explicit.

**Lead authorization model (normative).** Profiles are **scope bundles**, never a role enum (no
second mechanism). Two orthogonal axes: a **read tier** (which Leads you may see) and **operation
permissions** (which actions you may perform). Read tiers are escalating scopes — `crm:lead:read`
(own only) → also `crm:lead:read:unassigned` (the unassigned pool) → `crm:lead:read:all` (all); any
read tier passes the GET security gate, and `LeadAccessPolicy` narrows which Leads are returned (it
MUST be applied as a query Specification + a `canSee` check so filters/detail can never bypass it).
Operations are gated by `crm:lead:create` / `crm:lead:update` (qualify/lose/reassign/interactions) /
`crm:lead:assign`. **Consultation-only** = a read tier with no operation scope; **no access** = no
`crm:lead:*` scope (→ 403). Profile → scopes: Admin/Manager = read:all + create/update/assign;
Board/Marketing = read:all; Sellers/Call-Center = read + read:unassigned + create/update;
Representatives = read + create/update (own only); Finance/HR/IT = none. The frontend mirrors this
(hide actions/routes) but the backend is the only authority.

**Opportunity authorization model (normative).** Mirrors the Lead model: same two orthogonal axes,
the same escalating read tiers — `crm:opportunity:read` (own only) → also
`crm:opportunity:read:unassigned` (the unassigned pool) → `crm:opportunity:read:all` (all); any read
tier passes the GET security gate, and `OpportunityAccessPolicy` (a query Specification on
`responsiblePersonId`) narrows which Opportunities are returned so filters can never bypass it; the
single-record detail (`GET /api/opportunities/{id}`) applies the same `canSee` check (404 if absent, 403
if not visible). Operation `crm:opportunity:create` gates creating an Opportunity from a Qualified Lead
(the creator must also be allowed to see the source Lead, via the Lead read tiers). Operation
`crm:opportunity:update` gates the Opportunity operations — **editing its commercial details**
(`PUT /api/opportunities/{id}` — estimated value, expected closing date, product type and commercial
notes; the main interest stays from the Lead qualification; creates no Financial/Booking/Proposal/
Commission data), **advancing it through the pipeline** (`POST /api/opportunities/{id}/stage`; a strict
forward funnel `New → Discovery → Product Fit → Ready for Proposal`, one step at a time — skipping a stage
and going back are rejected; `LOST` is terminal and reached only via the lose action; every move is
recorded in the stage-movement history), **marking it as lost** (`POST /api/opportunities/{id}/lose`, with
a loss reason — its own fixed commercial-reason enum `OpportunityLossReason`, distinct from the Lead's
`loss_reasons` cadastro — and an optional note, from any active stage; never touches the source Lead) and
**registering commercial activities**
(`POST /api/opportunities/{id}/activities` — append-only history with type, result, description, date,
optional next action; never moves the stage nor creates a Proposal/Sale/Booking/Financial record); in all
the caller must also be allowed to see it. Profile → scopes: Admin/Manager = read:all + create + update; Sellers = read +
read:unassigned + create +
update; Representatives = read + create + update (own only); Board/Director = read:all (consultation);
Finance/HR/IT = none. The list and detail expose commercial pipeline data only — never Proposal, Sale,
Sales Order, Booking, Financial or Commission data. The **operational pending-items worklist**
(`GET /api/opportunities/pending`) is a read view gated by the same read tiers (any tier passes; the
policy narrows visibility at the query level, like the list) — it surfaces the visible Opportunities that
need action (no recent activity / overdue next action / stuck in an early stage / ready for a proposal /
expected close past, computed from a fixed 14-day staleness window), each tagged with its reasons. It is
**not** an executive dashboard, has **no** notification/SLA engine, and creates no Proposal/Sale/Booking/
Financial data; LOST Opportunities are terminal and excluded. The minimum **commercial-pipeline
indicators** (`GET /api/opportunities/indicators`) are a read view gated by the same read tiers (the
policy narrows visibility at the query level — a representative sees only their own numbers); they carry
two scopes like a mainstream CRM — **volume in the period** (total, lost, by stage/origin/responsible,
by creation date) plus a **current pipeline snapshot** (active, ready for proposal, overdue close, active
pipeline value, value by responsible). They expose commercial pipeline data only — never Proposal, Sale,
Sales Order, Booking, Financial or Commission data — and are not an executive dashboard (no
revenue/forecast/ROI).

**Proposal authorization model (normative — Sales & Proposals).** The **Sales & Proposals** bounded
context lives in `domain.sales` (same layout as `domain.crm`: `model`/`repository`/`service`/
`service.data`/`exception`) and owns Commercial **Proposals** (and, later, Proposal items, approval,
acceptance and Commercial Orders). Its scopes use the **`sales:`** prefix (the first non-`crm:` context):
the same two orthogonal axes as Opportunity — escalating read tiers `sales:proposal:read` (own only) →
also `sales:proposal:read:unassigned` (the unassigned pool) → `sales:proposal:read:all` (all); any read
tier passes the GET gate and `ProposalAccessPolicy` (a query Specification on `responsiblePersonId`)
narrows the list, the single-record detail applying the same `canSee` check. Operation
`sales:proposal:create` gates creating a Proposal from an Opportunity that is **`READY_FOR_PROPOSAL`**
(the creator must also be allowed to **see the source Opportunity**, via the Opportunity read tiers);
`sales:proposal:update` gates managing the Proposal's **items** (add/edit/remove, see below) and the
future lifecycle transitions. A Proposal **preserves** its source
Opportunity (`opportunityId`, never modified) and the source **Lead reference** (`leadId`), defaults the
responsible from the Opportunity, and starts as **`DRAFT`**. An Opportunity has **at most one open
Proposal at a time** (the service returns a friendly 409; a partial unique index is the last-resort guard;
a new Proposal is allowed once the previous is `REJECTED`/`EXPIRED`/`CANCELLED`). Creating a Proposal
creates **no** Sale, Sales Order, Booking, Customer, Financial, Payment or Commission data, and never
modifies the Opportunity or Lead. Profile → scopes: Admin/Manager = read:all + create + update; Sellers =
read + read:unassigned + create + update; Representatives = read + create + update (own only);
Board/Director = read:all (consultation); Finance/HR/IT = none. In the frontend, Proposals are a destination
of the workflow-oriented **Comercial** funnel module (Leads · Oportunidades · Propostas · Pedidos, §12), gated
by its read scopes; the backend stays the only authority.

**Proposal items (normative — Sales & Proposals).** A **`DRAFT`** Proposal carries **items** — the
commercial-offer lines (what the company intends to sell), modeled as a child collection of the Proposal
aggregate (`@OneToMany`, cascade + `orphanRemoval`, mirroring `OpportunityActivity`). Each item has a
**type** (`TRAVEL_PACKAGE`, `CAR_RENTAL`, `SERVICE_FEE`, `OTHER`), a **description**, a **quantity**
(integer ≥ 1), a **unit value** (≥ 0) and an **optional discount** expressed as either an **amount**
(`AMOUNT`, in R$, 0–subtotal) or a **percentage** (`PERCENT`, 0–100) — both null = no discount; an invalid
discount throws `proposal.item-invalid` (**422**). Each item's **line total** = `unitValue × quantity −
discount`; the items **subtotal** = the sum of the line totals (see the totals block below) —
**persisted/denormalized** on the aggregate and **recomputed inside the aggregate on every item change** (no
N+1 for the list), all in `BigDecimal` scale 2, `HALF_UP`. Items are added/edited/removed **only while the
Proposal is `DRAFT`** (the aggregate's `requireDraft()` guard → `proposal.not-editable`, **422**); an unknown
item id → `proposal.item-not-found` (**404**). Managing items **never** creates a Booking, checks external
availability, or creates Financial/Commission/supplier-cost/margin/tax/invoice data, and never touches the
source Opportunity or Lead. Endpoints `POST/PUT/DELETE /api/proposals/{id}/items[/{itemId}]` are each gated
by `sales:proposal:update` and return the refreshed `ProposalDetail` (its `items` + totals); the list and
detail expose the **total** alongside the existing commercial-offer fields — still never Sale / Sales Order
/ Booking / Financial / Commission data.

**Proposal totals, discounts, validity & submit-for-review (normative — Sales & Proposals).** A Proposal
exposes a **`subtotal`** (the sum of its items' line totals) and an optional **Proposal-level discount**
(`discountType` ∈ {`AMOUNT`,`PERCENT`} + `discountValue`, both null = none) applied to the subtotal, giving
the **`total`** (`total = subtotal − discount`, **never negative** — the effective discount is capped at the
subtotal so removing items can't drive it below zero). The discount range rules live on the **`DiscountType`**
enum (`amountOf`/`isValid`) and are **reused** by both the item-level and the Proposal-level discount; an
invalid Proposal discount throws `proposal.discount-invalid` (**422**). The Proposal also carries descriptive
**`paymentNotes`** (free text only — **never** a Financial/Payment/Receivable record). Editing these
commercial details (validity, commercial terms, payment notes, discount) is **`DRAFT`-only**
(`requireDraft()` → `proposal.not-editable`, **422**) via `PUT /api/proposals/{id}` (gated by
`sales:proposal:update`), returning the refreshed `ProposalDetail` (subtotal/discount/total recomputed in the
aggregate). **Submit for review** (`POST /api/proposals/{id}/submit`, gated by `sales:proposal:update`) moves
`DRAFT → READY_FOR_REVIEW` and **requires at least one item and a positive total** (else
`proposal.no-items` / `proposal.total-required`, **422**); the **validity date** is editable now and becomes
mandatory only at the future *send* step (not enforced this slice). None of this creates Receivable, Payment,
Booking, Commission, tax or margin data; the rest of the lifecycle (Approve/Send/Accept…) is a later slice.

**Booking authorization model (normative — Booking Operations).** The **Booking Operations** bounded context
lives in `domain.booking` (same layout as `domain.sales`) and owns the **Booking Request** (and, later, its
items' attempts/confirmation and the operational booking history). It is a back-office, **still-manual**
process — a Booking Request is **NOT** an external integration, a Receivable, a Payment, a Commission or
Customer Care. Its scopes use the **`booking:`** prefix. Operation `booking:request:create` gates creating a
Booking Request **from a Commercial Order that is `PENDING_BOOKING`** (`POST /api/bookings`); the creator must
also be allowed to **see the source Order**, reusing the Order read tiers (`sales:order:read` / `:read:unassigned`
/ `:read:all`) — a caller who cannot see it gets `order.access-denied` (**403**). The **operational Booking
Request list** (`GET /api/bookings`) is gated by the same escalating read tiers — `booking:request:read` (own
only) → also `booking:request:read:unassigned` (the no-operator pool) → `booking:request:read:all` (all); any
tier passes the GET gate and **`BookingRequestAccessPolicy`** (a query Specification: own = booking operator
OR commercial responsible; the unassigned tier adds operator-is-null) narrows which requests are returned so
filters can never bypass it. The list carries **operational reservation data only — never Financial, Payment
or Commission data**: the source Order number (`PC-000n`, the human identifier, 1:1 with the Order — the
reservation has no number of its own), the source Proposal title (commercial reference), the booking status,
the operator and commercial responsible, the counts of items requiring booking / confirmed, and the
creation/update instants (the latest-attempt field is reserved, null until the attempt slice). The default
view excludes the terminal `CONFIRMED` + `CANCELLED` requests but **keeps `FAILED` visible**; filters: status,
operator (incl. unassigned), commercial responsible, creation period, source order, item type, has-failed-items.
Only the `read:all` tier is seeded today; the own/unassigned tiers are defined in the policy for future operator
profiles. The **single-record detail** (`GET /api/bookings/{id}`) is gated by the same read tiers and applies the
same `BookingRequestAccessPolicy.canSee` check — **404** (`booking.not-found`) if the request is absent, **403**
(`booking.access-denied`) if it is not visible. It is a read-only consultation showing the reservation summary,
the source **Commercial Order / Proposal / Opportunity / Lead kept traceable**, the operational notes, and the
**booking items with their per-item status** (the available confirmation/failure signal — a `CONFIRMED` item is
shown confirmed, a `FAILED` item shown failed) plus the requiring/confirmed/failed counts; each item stays
traceable to its source Order item (`orderItemId`). It carries **operational reservation data only — never
Financial, Payment or Commission data**. **Manual booking attempts** (`POST /api/bookings/{id}/attempts`, gated
by the operation scope **`booking:request:update`** — seeded for operações(006) and the Manager(001); the
Board/Director(004) stays read-only) are an **append-only** operational history mirroring the Opportunity's
activities: each attempt has an **author, date (`occurredAt`, not future), type, result and description**, may
link to **one booking item or the whole request** (an item outside the request → `booking.item-not-found`,
**404**), and may define a **next action date**. Registering an attempt **may move the request `PENDING →
IN_PROGRESS`** but **never** confirms the booking, **never** changes a booking item's status (even an attempt
`result` of `FAILED` is history only) and **never** creates Financial/Commission data; the history is never
deleted. The **list** now exposes the **latest attempt** (`lastBookingAttemptAt`, denormalized). **Manually
confirming a Travel Package item** (`POST /api/bookings/{id}/items/{itemId}/confirm`, gated by the same
`booking:request:update` + visibility) records the **external reservation result** on the item — external
system/supplier and locator (**both required**, else 400), the confirmation date (operator-supplied,
`@PastOrPresent`) and author, plus optional travel metadata (package description, travel dates, traveler notes)
and operational notes (an `@Embeddable` value object on the item; **no monetary data**). It moves the item to
`CONFIRMED` and **consolidates** the request status (all items requiring booking confirmed → `CONFIRMED`,
otherwise `PARTIALLY_CONFIRMED`). **Only** a `TRAVEL_PACKAGE` item that requires booking and is not already
resolved can be confirmed (else `booking.item-not-confirmable` **422**); a confirmed/cancelled item rejects a
re-confirm (`booking.item-already-resolved` **422**). It calls **no** external system and creates **no**
Financial/Payment/Commission/Customer Care data and **no** voucher. **Manually confirming a Car Rental item**
(`POST /api/bookings/{id}/items/{itemId}/confirm-car-rental`, same scope/visibility/guards/roll-up/422 rules)
records the **car-specific** metadata instead — rental company, pickup/dropoff location and date-time (the
pickup/dropoff instants may be in the future), car category — plus the required system/locator/date; only a
`CAR_RENTAL` item that requires booking and is not resolved can be confirmed through it. Both flows share a
single `@Embeddable BookingItemConfirmation` (the type-irrelevant fields stay null) and a single `Confirmation`
read shape. A request with a travel package + a car rental reaches `CONFIRMED` only when **both** are confirmed.
**Marking a booking item as failed** (`POST /api/bookings/{id}/items/{itemId}/fail`, gated by
`booking:request:update` + the same visibility) records a **failure reason** (required, its own fixed enum
`BookingFailureReason` — `NO_AVAILABILITY`, `SUPPLIER_UNAVAILABLE`, `INVALID_COMMERCIAL_DATA`,
`MISSING_TRAVELER_DATA`, `EXTERNAL_SYSTEM_UNAVAILABLE`, `PRICE_CHANGED`, `MANUAL_OPERATION_ERROR`,
`OUT_OF_POLICY`, `OTHER`), an optional note, and who/when (`failedBy` = the authenticated user, `failedAt`
operator-supplied `@PastOrPresent`) in an `@Embeddable BookingItemFailure` on the item (**no monetary data**). It
moves the item to `FAILED` and **consolidates** the request via a single unified roll-up over the items requiring
booking: all `CONFIRMED` → `CONFIRMED`; ≥1 `CONFIRMED` (not all) → `PARTIALLY_CONFIRMED`; else ≥1 `FAILED` →
`FAILED`. **Only** an item that requires booking and is not already resolved can be failed (a `SERVICE_FEE`/
non-requiring item → `booking.item-not-failable` **422**; a `CONFIRMED`/`CANCELLED` item →
`booking.item-already-resolved` **422**); an unknown item id → `booking.item-not-found` **404**. A failed item
**stays visible as an operational problem**, **may receive new manual attempts** (the attempt log is unchanged),
and **may be retried** — a later confirm reconsolidates the request (`FAILED → PARTIALLY_CONFIRMED`/`CONFIRMED`).
The fail flow **never** cancels the Commercial Order and creates **no** Financial/Payment/Commission/Customer Care
data. Confirming a `SERVICE_FEE`/`OTHER` item and the cancellation flow remain later slices. A Booking Request
**preserves** its source `commercialOrderId` (never
modified), the source `proposalId` / `opportunityId` / `leadId` and the commercial `responsiblePersonId`, takes
an optional `bookingOperatorId` (assignment is a later slice; validated when present, else
`booking.operator-not-found`, **422**) and optional notes, snapshots the Order's items as **booking items**
(type/description/quantity + a `requiresBooking` classification + an item status — **no monetary data**;
`TRAVEL_PACKAGE`/`CAR_RENTAL` always require booking, `SERVICE_FEE` never does, and an `OTHER` item requires it
**only when explicitly marked** at creation via the request's `bookingRequiredItemIds` — marking a non-OTHER item
or an id outside the Order → `booking.item-not-markable`, **422**; required items start `PENDING`, the rest
`NOT_REQUIRED`), and starts **`PENDING`**. A Commercial Order
has **at most one active Booking Request** (the service returns a friendly **409** `booking.already-exists`; a
partial unique index is the last-resort guard; a new request is allowed once the previous is `CANCELLED`).
Creating a request creates **no** external reservation and **no** Receivable/Payment/Commission/Customer Care
data, and never modifies the Order. **Persona → scopes (Sprint 4):** a new back-office **`operacoes`** user
(seed 006) is the booking operator (`booking:request:create` + `sales:order:read:all` to see the source Order +
`booking:request:read:all` to work the list); the commercial **Manager** (001) also holds
`booking:request:create` + `booking:request:read:all` (oversight); the **Board/Director** (004) holds
`booking:request:read:all` (consultation); Sellers/Representatives and Finance/HR/IT have **no** booking read
tier (they do not see reservations).

**Booking Request status consolidation (normative).** The Booking Request status is **state-derived** from its
items requiring booking + its attempt history (a single `consolidateStatus()` on the aggregate, run after every
attempt/confirm/fail; it never overrides an explicit `CANCELLED`): every requiring item confirmed → `CONFIRMED`;
≥1 (but not all) confirmed → `PARTIALLY_CONFIRMED`; none confirmed but ≥1 failed → `FAILED` (the operation can't
proceed until retried); nothing confirmed/failed yet but ≥1 attempt → `IN_PROGRESS`; otherwise (all pending, no
attempt) → `PENDING`. `CANCELLED` is reserved for an explicit cancellation (a later slice). Confirming a
previously failed item reconsolidates the request.

**Reflecting the booking status onto the Commercial Order (normative).** The Commercial Order **shows** the
consolidated booking status while **staying owned by Sales & Proposals** — Booking Operations **never** takes
ownership of (or writes) the Order. Booking publishes a domain event (`BookingStatusConsolidated{bookingRequestId,
commercialOrderId, status}`) on every consolidation (and on create, reflecting `PENDING`); a **Sales-owned**
`@EventListener` (`CommercialOrderBookingStatusListener`, synchronous → atomic with the booking change) writes the
Order's own nullable `booking_status` column via `CommercialOrder.reflectBookingStatus(...)`. This is a **read-only
reflection**: it **never** changes the Order's own lifecycle (`status`) and **never** cancels the Order. The Order
detail/list expose `bookingStatus` (null = no Booking Request yet): a **`CONFIRMED`** booking makes the Order
**identifiable as ready for Financial Operations** (Sprint 5) and a **`FAILED`** booking marks it as **having a
booking problem**. A confirmed booking **must not** create a Receivable, Payment or Commission now, and a failed
booking **must not** cancel the Order automatically; Finance is not implemented now. The lifecycle transitions
beyond the consolidated status, the explicit **cancellation** flow, and Financial Operations remain later slices.

**Booking pending-items worklist (normative).** The operational worklist (`GET /api/bookings/pending`) is a read
view gated by the same Booking read tiers (any tier passes; `BookingRequestAccessPolicy` narrows visibility at the
query level, like the list — so it never exposes a request the caller may not see; Sellers/Representatives and
Finance/HR/IT have **no** booking read tier → 403). It surfaces the visible Booking Requests that **need action**,
each tagged with its **reasons** (`BookingPendingReason`): unassigned operator, `PENDING` without an attempt, `IN
PROGRESS` without a recent attempt (a fixed **7-day** staleness window), a failed item, a requiring-booking item
still pending, `PARTIALLY_CONFIRMED`, or an overdue next action. The reason computation
(`BookingRequestPendingReasons`) and the query (`BookingRequestPendingSpecifications`) mirror each other; the
terminal `CONFIRMED`/`CANCELLED` requests are excluded. "Overdue next action" reads a **denormalized
`next_action_date`** on the request (mirrors `last_attempt_at`: maintained from the latest attempt). It is
**operational, not** an executive dashboard, has **no** notification/SLA engine and **no** automatic external
retry, and creates no Financial/Payment/Commission/Customer Care data. In the frontend it is the **Reservas** tab
of the **Acompanhamento → Pendências** hub (gated by `canSeeBookings()`).

**Booking Operations indicators (normative).** The minimum indicators (`GET /api/bookings/indicators`) are a read
view gated by the same Booking read tiers (the policy narrows visibility at the query level — a manager sees the
global numbers; Sellers/Representatives and Finance/HR/IT, with no booking read tier, get 403). They carry **two
scopes** like the other indicator views — **volume in the period** (by creation date): `total`, `byStatus` (the
per-status counts: pending / in progress / partially confirmed / confirmed / failed / cancelled), `itemsByType`
(booking items per type), `failedItems`, and the **average creation→confirmation time** (`avgConfirmationSeconds`,
null when none confirmed in the period); plus a **current snapshot**: `readyForFinance` (the requests currently
`CONFIRMED` — ready for Financial Operations). The average reads a **denormalized `confirmed_at`** on the request
(mirrors `last_attempt_at`/`next_action_date`: stamped the first time the request reaches `CONFIRMED` in
`consolidateStatus`). They expose **operational reservation figures only** — never Financial, Payment, Commission,
Customer Care or external-integration data (integrations do not exist yet) — and are **not** an executive
dashboard. In the frontend they are the **Reservas** tab of the **Acompanhamento → Indicadores** hub (gated by
`canSeeBookings()`).

**Handoff to Financial Operations (Sprint 5) (normative — Sprint 4 is closed).** A **Commercial Order whose Booking
Request is `CONFIRMED`** (the Order carries `booking_status = CONFIRMED`, §reflection above) is the trigger that
**may** originate **Financial Operations in Sprint 5** — but Sprint 4 implements **no** Finance: it creates **no**
Receivable, Payment, Commission or Customer Care data and calls **no** external integration. The confirmed booking
**preserves the full handoff record so Sprint 5 starts without recapturing data**, split across the two (separate)
contexts: the **Booking Request** keeps the source **Order / Proposal / Opportunity / Lead** references, the
commercial **responsible** and the booking **operator**, the **booking items + types**, and — per confirmed item —
the **external locator**, the **external system / supplier name** and the **confirmation date**, plus the manual
**attempt history** and any per-item **failure**, and the **consolidated status**; the **Commercial Order** (owned
by Sales) keeps `booking_status = CONFIRMED` (the readiness signal), the **commercial total**, the items and the
customer (Lead). Sprint 5 Finance will **read**: the **Order** for the money + customer (the **total** is read from
the Order via the Booking Request's preserved `commercialOrderId` — the **Booking Request stays free of any
monetary data**, so the two contexts **remain separated**), and the **Booking Request** for the reservation
evidence (locators / systems / dates). No write crosses the boundary in the handoff; Finance only reads, and the
Order stays owned by Sales.

**Receivable authorization model (normative — Financial Operations, Sprint 5 Slice 1).** The **Financial
Operations** bounded context lives in `domain.financial` (same layout as the other contexts:
`model`/`repository`/`service`/`service.data`/`exception`) and owns the **Receivable** — the amount the company
has to receive from a client for a closed deal whose Booking is `CONFIRMED`. Its scopes use the **`financial:`**
prefix (the first non-`crm:`/`sales:`/`booking:` context). It mirrors the read-tier + operation model: two
escalating read tiers — `financial:receivable:read` (own only = the financial responsible) → `financial:receivable:read:all`
(all); any read tier passes the GET gate and **`ReceivableAccessPolicy`** (a query Specification on
`financialResponsiblePersonId`) narrows the list, the single-record detail applying the same `canSee` check (**404**
`financial.receivable.not-found` if absent, **403** `financial.receivable.access-denied` if not visible). Operation
`financial:receivable:create` gates creating a Receivable **from a Commercial Order whose `booking_status` is
`CONFIRMED`** (`POST /api/receivables`); the creator must also be allowed to **see the source Order**, reusing the
Order read tiers (`sales:order:read` / `:read:unassigned` / `:read:all`) — a caller who cannot see it gets
`financial.receivable.order-access-denied` (**403**), an unknown Order is `financial.receivable.order-not-found`
(**404**), and a non-`CONFIRMED` booking is `financial.receivable.order-not-confirmed` (**422**). A Commercial Order
has **at most one active Receivable** (the service returns a friendly **409** `financial.receivable.already-exists`
with the existing id; a partial unique index `WHERE status <> 'CANCELLED'` is the last-resort guard; a new one is
allowed once the previous is `CANCELLED`). A Receivable **preserves** its source `commercialOrderId` (never
modified), the source `proposalId` / `opportunityId` / `leadId`, the **payer** (`customerId`, see Customer below),
the commercial `responsiblePersonId` (snapshot) and the **commercial total** (snapshot of `order.total()`), takes a
single **required `dueDate`**, an optional `financialResponsiblePersonId` and optional descriptive `paymentNotes`
(free text — **never** a Payment/Receipt record), and starts **`OPEN`**. The `ReceivableStatus` lifecycle
(`OPEN`/`PARTIALLY_PAID`/`PAID`/`OVERDUE`/`CANCELLED`) is a **flow** → it stays an enum (the *payment method*, a
future payment slice, is **reference data** → a cadastro, not an enum). Creating a Receivable registers **no**
Payment and creates **no** Commission, Invoice, Booking or Customer Care data, and never modifies the Order, Lead or
Customer (it only reads them). The list/detail and `GET /api/receivables/eligible-orders` (the confirmed Orders
without an active Receivable, visible to the caller — feeds the create selector) expose **receivable +
commercial-origin data only — never Payment, Commission or Invoice data**. **Persona → scopes (Sprint 5):** the
back-office **`financeiro`** user (seed 005) creates + reads all (`financial:receivable:create` +
`financial:receivable:read:all`) and additionally holds **`sales:order:read:all`** so it can see the source Order
(this intentionally lets Finance read the commercial Order list/detail/indicators, but **never** create or modify an
Order — it lacks `sales:order:create`/`update`); the commercial **Manager** (001) and the **Board/Director** (004)
hold `financial:receivable:read:all` (consultation only); Sellers/Representatives and HR/IT have **no** financial
read tier. The payments lifecycle (registering/reversing a payment, the `OVERDUE`/`PAID` transitions, the financial
status reflected onto the Order, indicators) and Commission are **later slices**.

**Operational Receivable list (normative — Financial Operations, Sprint 5 Slice 3).** `GET /api/receivables` is the
**operational worklist** of the receivables that require financial follow-up (to prioritize collection). It is gated
by the same read tiers and narrowed by `ReceivableAccessPolicy` (so no filter can surface a receivable the caller may
not see). The **default** list shows the **operational** statuses (`OPEN`/`PARTIALLY_PAID`/`OVERDUE`) and **excludes
the settled `PAID` and `CANCELLED`** (pass them in the `status` filter to see them); **overdue receivables stay
visible** as operational problems. A receivable is **`overdue`** — the read-model flag **and** the `overdueOnly`
filter — exactly when its stored **`status == OVERDUE`** (the single source of truth; the daily overdue check flags
past-due receivables with a balance, per-installment-precise — §Slice 8). The list item carries the
operational fields — id, source Order (`PC-000n`), payer (Customer), `totalAmount`, `amountPaid`,
`outstandingAmount` (`total − paid`), status, next `dueDate`, `overdue`, commercial + financial responsible names,
`createdAt`, `lastPaymentDate` — **receivable + commercial-origin data only, never Commission or bank-reconciliation
data**. `amountPaid`/`outstandingAmount`/`lastPaymentDate` reflect the current **no-payment** state (zero / full
total / none) and become real with the payment slice. Filters: `status`, `payer` (customer-name substring), `order`
/ `orderNumber` (source Commercial Order), due-date period, creation period, `commercialResponsible`,
`financialResponsible`, amount range and `overdueOnly`. The list creates nothing and never shows Commission, bank
reconciliation, tax-invoice or cash-flow data (those are out of scope).

**Receivable detail consultation (normative — Financial Operations, Sprint 5 Slice 4).** `GET /api/receivables/{id}`
is the read-only **detail** a financial user opens to understand a receivable's origin, installments, payment
standing and outstanding balance. It is gated by the read tiers and `ReceivableAccessPolicy.canSee` (**404**
`financial.receivable.not-found` if absent, **403** `financial.receivable.access-denied` if not visible — only
receivables the caller may see). It exposes: the **summary** + **financial notes** (`paymentNotes`); the **traceable
commercial origin** — source **Commercial Order** (`PC-000n`, kept traceable), the **Proposal** and **Opportunity**
**commercial references** (the resolved `proposalReference` title and `opportunityReference` name, plus the ids/Lead
for linking); the **payer** (Customer); **`totalAmount`** / **`amountPaid`** / **`outstandingAmount`** (`total −
paid`) and the **`overdue`** flag (the stored `status == OVERDUE`); the **installment list** — each installment
carrying its own read-time **`overdue`** flag (`OPEN`/`PARTIALLY_PAID` and past its due date; paid/cancelled
installments are never overdue); and the **commercial + financial responsible**. The **payment history and reversal history** become available with the
payment slice — until then the detail's payment section is an honest empty state (no payments yet); a **reversed
payment will stay historically visible** there. `amountPaid` is zero / `outstanding` is the full total until
payments exist. The detail carries **receivable + commercial-origin data only — never Commission, bank-reconciliation
or tax-invoice data**, and creates nothing.

**Receivable installments (normative — Financial Operations, Sprint 5 Slice 2).** A Receivable is split into one or
more **installments** (`ReceivableInstallment`, a child collection of the Receivable aggregate —
`@OneToMany(cascade=ALL, orphanRemoval=true)`, mirroring `ProposalItem`). Each installment has an **`number`**
(1-based position), an **`amount`** (`@PositiveOrZero`, scale 2), a **`dueDate`** (required), a **`status`** and
optional **`paymentNotes`**. The installments **always sum to the Receivable's `total`** — every Receivable has
**at least one installment** (uniform model): the schedule is **defined at creation** (the create request carries an
optional `installments` list) and **empty/absent ⇒ one full-amount installment** at the receivable's reference
`dueDate`. When a schedule is supplied it must sum to the total (else `proposal`-style **422**
`financial.receivable.installment-schedule-invalid`; a negative amount or a missing installment due date is a **400**
via Bean Validation, re-guarded in the aggregate); installments are numbered 1..n in the given order. The
`InstallmentStatus` lifecycle (`OPEN`/`PARTIALLY_PAID`/`PAID`/`OVERDUE`/`CANCELLED`) is a **flow** → an enum
(mirrors `ReceivableStatus`); installments start **`OPEN`** and only `OPEN` is reachable in this slice (the
transitions are driven by payment behavior, a later slice). Scheduling installments creates **no** Payment,
Commission, Invoice or tax data; the detail exposes the installment schedule, still **never** Payment/Commission/
Invoice data. Out of scope: interest, late fee, boleto/Pix generation, recurring billing, tax-invoice schedule,
commission, and editing the schedule after creation.

**Payment registration (normative — Financial Operations, Sprint 5 Slices 5–6).** An authorized financial user
**registers a payment** (full or partial) against one **installment** of a Receivable: `POST
/api/receivables/{id}/installments/{installmentId}/payments`, gated by the operation scope
**`financial:payment:register`** and the same Receivable read tiers for visibility (the caller must be able to see
the Receivable, else **403**). A **payment** (`ReceivablePayment`, an append-only child of the Receivable aggregate
— `@OneToMany(cascade=ALL, orphanRemoval=true)`) carries an **amount** (`@Positive`), a **payment date**
(`@PastOrPresent` — never future), a **payment method**, an optional free-text **note** and the **registered-by**
user + instant. The **payment method is a cadastro** (reference data, **not** an enum — §1 invariant 8):
`PaymentMethod extends ReferenceData` in `domain.financial`, managed via `reference:manage` at `GET/POST/PUT/DELETE
/api/financial/payment-methods`, seeded with Cash / Bank transfer / Pix / Credit card / Debit card / Invoice
payment / Other; an unknown/inactive method is **422** `financial.payment.method-not-available`. The payment is
**amount-driven**: `0 < amount ≤ the installment's outstanding` (`amount − amountPaid`) — it may settle the
installment **fully** (amount == outstanding) **or partially**; **overpayment is out of scope**, so an amount
exceeding the outstanding is **422** `financial.payment.exceeds-outstanding`. A **payable** installment is one not
already resolved — `OPEN` **or `PARTIALLY_PAID`** (an already `PAID`/`CANCELLED` installment is **422**
`financial.payment.installment-not-payable`); an installment outside the Receivable is **404**
`financial.payment.installment-not-found`. The installment **denormalizes its own `amount_paid`** and moves to
**`PARTIALLY_PAID`** (0 < paid < amount) or **`PAID`** (paid == amount); **multiple partial payments** may be
registered until it is settled. The aggregate then **consolidates** the Receivable status
(`Receivable.registerPayment` → `consolidateStatus`, amount-driven): no payment → `OPEN`; outstanding == 0 →
**`PAID`**; else (≥1 payment, balance remains) → **`PARTIALLY_PAID`**, never overriding `CANCELLED`. The Receivable
**denormalizes** `amount_paid` (sum of payments) and `last_payment_date` (latest payment date), so the list/detail
serve `amountPaid` / `outstandingAmount` / `lastPaymentDate` without an N+1; the **detail** exposes the **payment
history** and the **per-installment** `amountPaid` / `outstanding` alongside the schedule. Registering a payment
creates **no** Commission, Invoice, receipt/voucher or bank-reconciliation data, performs **no** Pix/card/gateway
capture, and never touches the Order, Lead or Customer. **Persona → scopes:** only the back-office **`financeiro`**
(005) holds `financial:payment:register`; the **Manager** (001) and **Board/Director** (004) keep read-only
consultation (no payment register). Out of scope (later slices): **overpayment**, cross-installment /
Receivable-level auto-allocation, payment reversal, financial indicators, interest/late fee, Commission, and
receipt-PDF generation. In the frontend, the Receivable detail offers **Registrar pagamento** per **payable**
installment (shortcut <kbd>p</kbd>) behind `canRegisterPayment()` with an **editable amount** defaulting to the
outstanding, and the **Formas de pagamento** cadastro lives in the **Cadastros** module.

**Payment reversal (normative — Financial Operations, Sprint 5 Slice 9).** An authorized financial user **reverses a
registered payment** to **correct a payment-entry mistake** while **preserving the audit history**: `POST
/api/receivables/{id}/payments/{paymentId}/reversals`, gated by the operation scope **`financial:payment:reverse`**
(a sensitive correction, granular scope) plus the Receivable read tiers for visibility (the caller must be able to see
the Receivable, else **403**). A reversal **requires a reason** (`@NotBlank` → **400** when blank) and **records
who/when** (`reversedBy` = the authenticated user, `reversedAt` = now). Only a **registered (non-reversed)** payment is
reversible — a second reversal is **422** `financial.payment.already-reversed`; a payment outside the Receivable is
**404** `financial.payment.not-found`. The reversal **never deletes** the payment: it **mutates it into a reversed
state** (`reversal_reason`, `reversed_by`, `reversed_at`) and the payment **stays visible in the history** marked
reversed. The denormalized `amount_paid` (installment **and** Receivable) is the sum of the **non-reversed** payments,
so the reversal **decrements** it (`Receivable.reversePayment` → `installment.reverseAmount` → `consolidateStatus`),
**re-derives the installment status** (paid 0 → `OPEN`, < amount → `PARTIALLY_PAID`, == amount → `PAID`),
**recomputes** `last_payment_date` from the remaining payments, and **re-consolidates** the Receivable — a reversal
**from `PAID` may return it to `PARTIALLY_PAID`/`OPEN`** per the remaining paid amount (never overriding `CANCELLED`).
The `ReceivableStatusChanged` reflection onto the Order's `financial_status` is **republished**, so e.g. a `PAID`
order that is no longer fully paid stops being identifiable as ready for Commission. Reversing a payment creates **no**
refund, bank chargeback, gateway reversal, customer notification, Commission clawback or accounting-ledger entry, and
never touches the Order, Lead or Customer. **Persona → scopes:** only the back-office **`financeiro`** (005) holds
`financial:payment:reverse`; the **Manager** (001) and **Board/Director** (004) keep read-only consultation (no
reversal). In the frontend, the Receivable detail's **Pagamentos** table gains a **Situação** column (Registrado /
**Estornado** with reason · who · when) and an **Estornar** action (reversal dialog, required *motivo*) per
non-reversed payment behind `canReversePayment()`; reversed rows stay visible but de-emphasised. Out of scope (later
slices): partial reversal of a single payment (a reversal is all-or-nothing for the chosen payment), editing a reversed
payment, refund/chargeback processing, and Commission clawback.

**Reflecting the financial status onto the Commercial Order (normative — Financial Operations, Sprint 5 Slice 7).**
The Commercial Order **shows** the Receivable's financial status while **staying owned by Sales & Proposals** —
Financial Operations **never** takes ownership of (or writes) the Order. This mirrors the Sprint-4 booking-status
reflection exactly: Financial publishes a domain event (`ReceivableStatusChanged{receivableId, commercialOrderId,
status}`) whenever the status is established or changes — on **creation** (`OPEN`), after **each payment** (the
consolidated status) and when the **daily overdue check** flags it — and a **Sales-owned** `@EventListener`
(`CommercialOrderFinancialStatusListener`, synchronous → atomic with the Financial change) writes the Order's own
nullable `financial_status` column via `CommercialOrder.reflectFinancialStatus(...)`. This is a **read-only
reflection**: it **never** changes the Order's own lifecycle (`status`) and **never** cancels the Order. The Order
detail/list expose `financialStatus` (null = no Receivable yet): a **`PAID`** financial status makes the Order
**identifiable as ready for Commission Management (Sprint 6)** and an **`OVERDUE`** marks it as a **financial
problem**; a **`PARTIALLY_PAID`** Order is **not** treated as paid. **OVERDUE is a real stored Receivable status**
set by a daily **`@Scheduled`** job (`ReceivableOverdueJob`, the project's first scheduled job —
`@EnableScheduling`): it flips operational (`OPEN`/`PARTIALLY_PAID`) Receivables to **`OVERDUE`** once a due date
has passed with a balance (`Receivable.markOverdueIfPastDue`, idempotent) and republishes the reflection. A
payment **never "un-overdues"** a still-outstanding Receivable (`consolidateStatus` preserves `OVERDUE`); settling
it moves it to `PAID`. Reflecting the status **must not** create Commission now, performs no notification, and the
Order stays owned by Sales.

**Identifying overdue Receivables (normative — Financial Operations, Sprint 5 Slice 8).** Overdue identification
keys off the **stored `OVERDUE` status** (the single source of truth, set by the Slice-7 daily check
per-installment-precisely): the receivable-level **`overdue`** flag (list + detail) and the **`overdueOnly`** list
filter are exactly `status == OVERDUE` — so a multi-installment receivable with a paid first installment and a
not-yet-due second is **not** falsely flagged, and overdue receivables stay visible by default (`operational()`
includes `OVERDUE`). The **detail** additionally exposes a **per-installment `overdue`** flag (read-time:
`ReceivableInstallment.isPastDue(today)` — `OPEN`/`PARTIALLY_PAID` and past its due date; **paid and cancelled
installments are never overdue**), so the specific overdue installments are identifiable. Overdue detection
applies **no interest or late fee**, sends **no notification**, and creates **no** Customer Care ticket.

**Financial Operations indicators (normative — Financial Operations, Sprint 5 Slices 10–11).** The minimum
functional indicators (`GET /api/receivables/indicators?from=&to=`) are a **manager's minimal view of receivables and
received payments**, gated by the **same Financial read tiers** as the Receivable list/detail — **no new scope, no
migration** (the existing `GET /api/receivables/**` gate covers it; `ReceivableAccessPolicy` narrows visibility at the
query level, on `financialResponsiblePersonId`). They carry **two scopes**: the **volume in the selected period** —
receivables **created** in the period (`totalReceivablesInPeriod`, `totalToReceive` = Σ their `total`); the
**non-reversed** payments **received** in the period by payment date (`receivedAmount`, `paymentsRegistered`,
`paymentsByMethod` → `{method, methodLabel, count, amount}`; a reversed payment is a correction, excluded); and the
receivables **settled** in the period (`paidReceivablesInPeriod`, and `avgDaysToPayment` = the average creation→
settlement days over those settled, `null` when none) — plus the **current snapshot** (independent of the period):
`byStatus` (count per status), `outstandingAmount` (Σ `total − amountPaid` over non-`CANCELLED`), `overdueAmount` (Σ
`total − amountPaid` over `OVERDUE`) and `readyForCommission` (the count of `PAID` receivables — identifiable as
**ready for Commission Management** in Sprint 6; a **readiness count, not a Commission calculation**). The single
period window `[from, to]` is applied per figure by its natural date (created → `createdAt` anchored at UTC midnight;
received → `payment_date`; settled → `last_payment_date`). The aggregation reuses the visibility `Specification` as
its WHERE predicate (`ReceivableIndicatorQueries`, Criteria API), so the numbers never include receivables the caller
cannot see. They expose **receivable + received-payment figures only — never** Commission **calculation/forecast**,
Accounts Payable, bank reconciliation, accounting ledger, fiscal, P&L, cash-flow or executive-dashboard data, and
they are **operational, not executive reporting**. **Persona → tiers:** the back-office **`financeiro`** (005) and the
consultation profiles **Manager** (001) and **Board/Director** (004) hold a financial read tier → **200**;
**Sellers/Representatives** and HR/IT have **no** financial tier → **403** (no global financial indicators). In the
frontend the same `ReceivableIndicatorsPage` is surfaced **both** as the **Recebimentos** destination in the
**Financeiro** module (`/financeiro/recebimentos`, gated by `canSeeReceivables()`) **and** as the **Financeiro** tab
of the **Acompanhamento → Indicadores** hub; the backend stays the only authority.

**Customer (normative — the commercial graduation of a Lead, in `domain.crm`).** The **Customer** is the company's
client, materialized from its source **Lead** when a Commercial Order is created (deal closed): a **synchronous,
idempotent** `@EventListener` on `CommercialOrderCreated` (`CustomerMaterializationListener`, same transaction as
the Order creation — purely additive, the Order creation is unchanged) snapshots the Lead's name and contacts into a
`Customer` (relation **1:1**, `lead_id` unique). It lives in **`domain.crm`** (next to the Lead, its origin), **not**
in `domain.financial` — it is the **commercial** graduation of the Lead, and Financial Operations only **reads** it
(cross-context read) to resolve the **payer** of a Receivable; it is **not** financial data. The document (CPF/CNPJ)
and billing address are optional placeholders filled by a later slice. There is **no Customer CRUD UI** in this slice
(it is materialized automatically). Customer Care remains out of scope.

**Handoff to Commission Management (Sprint 6) (normative — Sprint 5 is closed).** A **Paid Receivable** is the
trigger that **may** make a **Commercial Order eligible for Commission Management in Sprint 6** — the readiness signal
is the Order's reflected **`financial_status = PAID`** (mirrored from the Receivable, §reflection above; surfaced on
the Order list/detail and counted by the indicators' `readyForCommission`). But Sprint 5 implements **no Commission**:
it creates **no** Commission record/calculation/approval/payment, **no** Accounts Payable, **no** refund, **no**
Customer Care, **no** tax invoice and **no** bank reconciliation. The Paid financial data **preserves the full handoff
so Sprint 6 starts without recapturing basic financial data**, split across the two (separate) contexts that own each
piece — the **Commercial Order** (owned by Sales) keeps `financial_status = PAID` (the readiness signal), the
**commercial total**, the items and the **customer** (payer), plus the source Proposal/Opportunity/Lead and the
**commercial responsible**; the **Receivable** (owned by Financial Operations) keeps the source **Commercial Order /
Proposal / Opportunity / Lead** references, the **payer** (`customerId`), the **commercial responsible** (snapshot),
the **total** (snapshot of `order.total()`), the **installment schedule**, the **payments** — each with its **amount,
payment date, payment method and registered-by user** — including any **reversed** payments (kept in history with the
reason + who/when), the denormalized **amount paid** / **outstanding amount**, and the **final Receivable status**
(`PAID`). Sprint 6 Commission Management will **read**: the **Order** for the readiness signal + money + customer, and
the **Receivable** for the payment evidence (the amounts received, dates, methods and the final status). **No write
crosses the boundary in the handoff** — Commission only reads, and the **Commercial Order, Booking Request, Receivable
and Payment remain separated** (distinct aggregates/contexts; the Receivable references the Order read-only). Adding
Commission (the `Expected → Eligible → Approved → Paid` commission lifecycle, its calculation, approval and payment)
is **Sprint 6** and MUST NOT be started here.

**Commission Management (normative — Sprint 6).** The **Commission Management** bounded context lives in
`domain.commission` (same layout as the other contexts: `model`/`repository`/`service`/`service.data`/`exception`)
and owns **Commission Rules**, **Commissions**, **Commission Approval** and **Commission Payment registration**. Its
scopes use the **`commission:`** prefix. It **calculates, makes eligible, approves and registers commission as paid**
from **paid Commercial Orders** — it **does NOT take ownership of** the Commercial Order (Sales) or the Receivable/
Payment (Financial), which it only **reads**. A **Commission is not salary, not Accounts Payable, not bank
integration, not tax and not accounting**; Commission Management MUST NOT implement payroll, HR, tax, full
accounting, generic Accounts Payable, expenses, refunds, Customer Care, bank integration, automatic transfer,
invoice issuing, advanced split, or monthly-target / margin-based / supplier-based commission. The Commission
lifecycle is a fixed enum state machine: `Expected → Eligible → Approved → Paid`, plus `Rejected` and `Cancelled`
(commission becomes **Eligible** only after the Receivable is **paid**); the Commission Payment is `Registered`
(with a simple `Reversed`).

**Commission Rule (normative — Commission Management, Sprint 6 Slice 1).** The first slice ships **only the
configuration**: an authorized **commercial or financial manager** defines a **Commission Rule** — for Sprint 6, a
**percentage of the received amount** (no fixed-amount type yet). A rule is a managed entity (`CommissionRule`, not a
`domain.reference` cadastro — it carries percentage/dates/target and business logic), with a **`name`** (required), a
**`percentage`** (> 0 and ≤ 100, scale 2, mirrored by a DB CHECK), a **`targetType`** enum
(`SELLER`/`SALES_REPRESENTATIVE`/`COMMERCIAL_RESPONSIBLE` — the commercial actor it targets), an optional
**`targetUserId`** (a user-specific rule; validated to exist+active when set, else **422**
`commission.rule.target-user-not-found`), an **`active`** flag, a **`startDate`** (required) + optional **`endDate`**
(`≥ startDate`, else **422** `commission.rule.dates-invalid`), and optional **`notes`**. The percentage **must not
exceed a configured safe business limit** (`app.commission.safe-max-percentage`, default 50, a typed
`CommissionProperties`) **unless the request explicitly sets `allowAboveLimit`** (else **422**
`commission.rule.percentage-above-limit`, carrying the limit); ≤ 0 or > 100 is **400** (Bean Validation). **Only
active rules** are usable for new commission calculation (a later slice). Endpoints `POST/GET/PUT
/api/commission/rules[/{id}]` + `POST /{id}/activate|deactivate` are each gated by **`commission:rule:manage`**
(seeded for the commercial **Manager** (001) + **Financeiro** (005); sellers/representatives/Board → **403**); the
shared `GET /api/crm/responsibles` lookup also admits `commission:rule:manage` so a manager can target a specific
user. **Creating/editing a rule creates NO Commission record, Payment, payroll, payable, tax or accounting data.**
In the frontend the rules screen lives under **Cadastros** (`/cadastros/regras-comissao`, a bespoke screen gated by
`canManageCommissionRules()`); the backend stays the only authority. Commission **generation, eligibility, approval,
payment, statement, order commission-status reflection and indicators** are the **later Sprint-6 slices**.

**Expected Commission generation (normative — Commission Management, Sprint 6 Slice 2).** An authorized **commercial/
financial manager generates an Expected Commission from a Commercial Order** (`POST /api/commissions`,
`{commercialOrderId}`), so the future commission payment is **tracked from the start**. The commission is a
**forecast** — it starts **`EXPECTED`** and is **not payable** yet. Generation requires the Order to be
**commercially closed** (it exists and is **active**, i.e. not `CANCELLED` — else **422** `commission.order-not-closed`),
to have a **commercial responsible** (the **beneficiary**; else **422** `commission.order-no-responsible`) and a
**positive commercial total** (else **422** `commission.order-no-amount`); the caller must also be allowed to **see
the source Order**, reusing the Order read tiers (`sales:order:read` / `:read:unassigned` / `:read:all`) — a caller who
cannot see it gets **403** `commission.order-access-denied`, an unknown Order is **404** `commission.order-not-found`.
The applied rule is the **active, in-window** `CommissionRule` selected **specific-first**: a rule whose
`targetUserId == the beneficiary` wins, else a generic rule with `targetType = COMMERCIAL_RESPONSIBLE`; among
candidates of the same kind the newest wins. Generic `SELLER`/`SALES_REPRESENTATIVE` rules are **not** auto-matched
(there is no user-role model yet); when none applies → **422** `commission.no-applicable-rule`. The **amount** is
`baseAmount × rulePercentage ÷ 100` (`BigDecimal` scale 2, `HALF_UP`); the **basis** is the **received amount** when
the Order's Receivable already has payments (`amountPaid > 0`) — `RECEIVED_AMOUNT`, base = `amountPaid` — otherwise the
**commercial total** (`COMMERCIAL_AMOUNT`, a forecast). The Commission **preserves** the commercial origin
(`commercialOrderId`/`proposalId`/`opportunityId`/`leadId`, never modified), the **beneficiary**, and the applied
**`ruleId` + `rulePercentage` snapshot**. An Order has **at most one active Commission** (the service returns a
friendly **409** `commission.already-exists` with the existing id; a partial unique index `WHERE status NOT IN
('REJECTED','CANCELLED')` is the last-resort guard; a new one is allowed once the previous is rejected/cancelled).
Generating a Commission **reads** the Order and the Receivable but **never owns or modifies** them, and creates **NO**
Commission Payment, Accounts Payable, payroll, tax or accounting data. `GET /api/commissions/{id}` returns the detail
(commission + commercial-origin data only). **Persona → scopes:** `commission:create` (commercial **Manager** 001 +
**Financeiro** 005; both already hold `sales:order:read:all` to see the Order) + a commission **read tier** (since
Sprint 6 Slice 4: 001 + **Board/Director** 004 + 005 hold `commission:read:all`; sellers/representatives hold the
own-only `commission:read`; operations/HR-IT have **no** commission read tier → **403**). In the
frontend the Order detail offers a minimal **"Gerar comissão"** action (shortcut <kbd>c</kbd>) behind
`canCreateCommission()` that shows the generated commission inline; the backend stays the only authority. **Out of
scope (later slices):** eligibility (`EXPECTED → ELIGIBLE` once the Receivable is paid), approval/rejection, payment
registration + reversal, statement, order commission-status reflection, indicators, and multi-level/margin/supplier/
target-based commission.

**Commission eligibility (normative — Commission Management, Sprint 6 Slice 3).** An Expected Commission advances one
fixed step — **`EXPECTED → ELIGIBLE`** (pending approval) — **only when its related Receivable is fully `PAID`**, so the
company never pays commission before receiving the money. Commission Management **consumes** the financial status; the
Receivable stays **owned by Financial Operations** and the Order by Sales. The transition is driven by a
**Commission-owned synchronous `@EventListener`** (`CommissionEligibilityListener`) on the **`ReceivableStatusChanged`**
event Financial already publishes — it reacts **only** to `PAID` (a `PARTIALLY_PAID`/`OPEN`/`OVERDUE` receivable does
**not** make it eligible; there is **no partial eligibility**), finds the Order's `EXPECTED` commission and calls
`Commission.markEligible(...)`. The same transition also runs at **generation time** when the source Receivable is
**already `PAID`** (the `PAID` event fired before the commission existed, so the listener would miss it). `markEligible`
is **idempotent** (only from `EXPECTED`; a repeated `PAID` event or an already-advanced commission is a no-op), records
the **financial evidence for review** (`eligibleAt` + the paid `receivableId`), and creates **NO** Commission Payment,
Accounts Payable, payroll, tax or accounting data. Becoming eligible is **not** an automatic approval and **not** an
automatic payment — it only makes the commission **visible as pending approval**. The Order detail surfaces the order's
commission via **`GET /api/commissions?commercialOrderId={id}`** (gated `commission:read`, 0-or-1 active commission),
showing `ELIGIBLE` as **"Pendente de aprovação"** with the `eligibleAt` date; the "Gerar comissão" action hides once a
commission exists. **Known gap (intentional):** a payment **reversal** (Sprint 5) republishes a non-`PAID` status, which
the listener **ignores** — an already-`ELIGIBLE` commission is **not regressed** back to `EXPECTED` (no clawback /
reversal-reaction automation — out of scope). **Out of scope (later slices):** partial eligibility, clawback,
reversal-reaction automation, approval/rejection, payment registration + reversal, order commission-status reflection,
indicators, and payroll / accounts payable / tax / accounting ledger.

**Operational Commission list (normative — Commission Management, Sprint 6 Slice 4).** `GET /api/commissions` is the
**operational, paginated, filtered** list a commercial/financial manager uses to track Expected / Eligible / Approved /
Paid commissions. It introduces the **two-tier commission read model** (mirroring the Receivable model): **`commission:read`**
(own only = the **beneficiary**) → **`commission:read:all`** (all); any tier passes the GET gate and **`CommissionAccessPolicy`**
(a query Specification on `beneficiaryUserId`) narrows the list, the single-record **detail** (`GET /api/commissions/{id}`)
applying the same `canSee` check (**404** `commission.not-found` if absent, **403** `commission.access-denied` if not visible).
The **default** list shows the **operational** statuses (`EXPECTED`/`ELIGIBLE`/`APPROVED` — `CommissionStatus.operational()`);
the settled **`PAID`** and the terminal **`REJECTED`/`CANCELLED`** appear **only when passed in the `status` filter**
(Eligible stays visible as **pending approval**). The list item carries **commission + commercial-origin data only — never
payroll, tax, accounting or generic accounts-payable data**: id, beneficiary (id + name), source Order (`PC-000n`), source
**Proposal/Opportunity reference**, commission `amount`, `rulePercentage` + rule name, `status`, the commercial-amount
`basisType`, the source order's **active Receivable status** (consumed read-only from Financial, or null), `createdAt`,
`eligibleAt`, and the forward-looking `approvedAt`/`paidAt` (null until the approval/payment slices set them). Filters
(all optional): `status`, `beneficiary` (user id), source order (`order` id / `orderNumber`), `rule` id, creation period,
eligibility period, **payment period** and amount range (ISO dates anchored at UTC midnight). The **rule-filter dropdown**
is served by `GET /api/commission/rules`, whose **read** gate is broadened to admit any commission read tier (the rule
**writes** stay `commission:rule:manage`). **Persona → scopes (revised this slice):** the commercial **Manager** (001),
**Board/Director** (004) and **Financeiro** (005) hold **`commission:read:all`** (they move off the flat `commission:read`
seeded in Slice 2); **Sellers** (vendedor 002) and **Representatives** (representante 003) hold the own-only
**`commission:read`** (they see **only** commissions where they are the beneficiary); **operações** (006) and HR/IT have
**no** commission read tier → **403**. In the frontend the list is the **Comissões** destination of the **Comercial**
funnel module (`/comissoes`, gated by `canSeeCommissions()`, shortcut <kbd>g m</kbd>); the backend stays the only
authority. **Out of scope (later slices):** approval/rejection (populates `approved_at`), payment registration + reversal
(populates `paid_at`), statement, order commission-status reflection, indicators, and the payroll dashboard / accounting
report / tax report / bank payment file / accounts-payable list.

**Commission detail consultation (normative — Commission Management, Sprint 6 Slice 5).** `GET /api/commissions/{id}`
returns the **full read-only detail** a commercial/financial manager opens to understand a commission's origin,
calculation, eligibility, approval and payment history. It is gated by the Commission read tiers and
**`CommissionAccessPolicy.canSee`** (**404** `commission.not-found` if absent, **403** `commission.access-denied` if not
visible — sellers/representatives may open **only their own**). The enriched `CommissionDetail` keeps the **commercial
origin traceable** (source Order `PC-000n`, source **Proposal** `proposalReference` + id, source **Opportunity**
`opportunityReference` + id, Lead id) and the **related Receivable** traceable (`receivableId` + `receivableStatus` —
the source order's active Receivable, resolved read-only from Financial; the receivable is identified by the Order, so
no monetary receivable data is duplicated), exposes the **calculation basis** (`basisType` + `baseAmount`) and the
**rule used** via the **immutable `rulePercentage` snapshot** (so it stays visible even if the rule is later renamed or
re-priced; `ruleName` is a live convenience), the **amount**, the **status**, `createdByName`, and the lifecycle stamps
**`eligibleAt` / `approvedAt` / `paidAt`** — shown **when available** (approval/payment are later slices, null now). The
frontend renders a read-only detail page at **`/comissoes/{id}`** (Comercial module, reached from the list's
beneficiary link or the Order detail's commission panel; <kbd>Esc</kbd> returns to the list) with a **history/timeline**
(Gerada → Elegível → Aprovada → Paga) that fills in as each stamp is populated. It carries **commission +
commercial-origin data only — never payroll, tax or accounting data**. No new endpoint/scope/migration (the detail
endpoint + visibility existed since Slices 2–4; this slice only **enriches** the read model). **Out of scope (later
slices):** the **approval/rejection** info, **payment** info, **cancellation** info and free-text notes (they fill in as
those slices land), statement, order commission-status reflection, indicators, and payroll / accounting / tax /
bank-transfer / accounts-payable detail.

## 11. Observability & performance

Observability is architecture. Logs are structured (JSON), contextual and safe; a log MUST
answer: what happened, when, which user/request/job, which entity, success or failure, duration,
failure class. Never log secrets. Every relevant request/message/job SHOULD carry a correlation
ID. Expose Prometheus metrics (Micrometer): request count/latency, error rate, JVM, DB pool,
queue size, retries, timeouts, external-API latency; business and AI metrics when relevant.
Health checks distinguish liveness/readiness.

Performance: avoid obvious bad choices from the start (N+1, missing indexes, unbounded queries,
missing pagination, external calls in loops, missing timeouts). Heavy optimization is
evidence-driven only (metrics, traces, profilers, slow-query logs). Code stays readable unless a
measured hotspot justifies complexity.

## 12. Frontend (Angular)

Organize by feature/domain with a controlled `core` and `shared`:

```txt
src/app
  core/      auth config http interceptors guards layout observability realtime
  shared/    components directives pipes validators utils
  features/  <feature>/ ...   (pages components services models)
```

`core` = app-wide infrastructure and singletons; `shared` = genuinely reusable UI/utils;
feature-specific code MUST stay inside the feature.

- **Product & UX first.** UI represents the user workflow, not the database model. Screens MUST
  handle real states: loading, empty, error, validation errors, permission denied, partial data,
  in-progress, success/failure feedback, confirmation before destructive actions, disabled submit
  during processing. Technical preference MUST NOT override product/UX requirements.
- **State: signals-first.** No global store by default - local state for simple UI, feature
  services with signals for shared feature state. No NgRx unless real complexity justifies it.
- **Components & forms:** standalone components; reusable components are presentation-oriented
  with inputs/outputs and minimal business coupling. Reactive Forms by default; large forms
  extract builders, mappers, validators. Form validation MUST mirror the backend constraints
  (§5.5) for fast feedback, but is never the only guard - the backend always re-validates.
- **HTTP & errors:** `core/http` owns base URL, auth headers, correlation ID, global error
  handling, interceptors. Each feature exposes domain-oriented API services - no raw `HttpClient`
  in components. Combine global normalization with feature-specific error presentation.
- **Realtime:** only when justified; `core/realtime` manages the connection (STOMP), feature
  services translate technical events into UI behavior. Components never handle raw protocol
  messages.
- **UI & styling:** the component library owns component internals; Tailwind owns layout and
  custom widgets. All user-facing text goes through the i18n layer.
- **Module-oriented navigation (normative) — organized by WORKFLOW, not by backend bounded context:** a
  **single navigation config** (`core/navigation`, `NavigationService`) is the source of truth for the sidebar,
  the system home and the per-module homes — the three never drift. The modules follow the **user's workflow**,
  deliberately **not** mirroring the backend contexts: **Comercial** (`/comercial`) is the whole commercial
  **funnel in order** — Leads · Oportunidades · Propostas · Pedidos (each destination gated by its read scope);
  **Reservas** (`/reservas`) is the operations worklist (the booking list; a standalone module that grows with
  Booking Operations); **Acompanhamento** (`/acompanhamento`) groups the cross-funnel monitoring as **two single
  hubs** — `/pendencias` (the pending-items hub) and `/indicadores` (the indicators hub), each a **tabbed page
  reusing the existing area screens** (Leads/Oportunidades/Propostas/Pedidos) and showing only the tabs the
  profile may see; **Cadastros** (`/cadastros`) is the reference data. The **system home** (`/`) shows a card per
  accessible module leading to its **module home** (tiles); the **sidebar** renders each module as a
  **collapsible accordion section** (header links to the module home; a chevron toggles; only the active
  module's section is open). Visibility mirrors the backend authority; the backend stays the only guard. A new
  feature is added in the nav config (+ a hub tab when it is a pending/indicator view), which updates the sidebar
  + homes + command palette at once.
- **Keyboard access (normative, every feature):** every feature MUST be reachable by keyboard. The
  **command palette** (`Ctrl/Cmd+K`, in the shell) is the universal index and MUST list every route +
  primary action; primary destinations get a `g`-leader shortcut (`g i/l/o/p/d/r/c` → início/leads/
  oportunidades/propostas/pedidos/reservas/cadastros) and detail pages get context keys (e.g. proposal:
  `i` add item, `e` edit details, `s` submit, `Esc` back). The `?` help overlay is the single source of
  truth listing the current set. A new feature ships its palette entry + shortcut as part of the
  Definition of Done (§3).
- **Unsaved-changes protection (normative, every form/action):** leaving a form/edit with in-progress
  changes MUST warn before the data is lost — **including navigation via keyboard shortcuts**. Use the
  central `unsavedChangesGuard` (`CanDeactivate`, driven by the component's `hasUnsavedChanges()`) on
  every form route, the shell's `<p-confirmDialog>` + `UnsavedChangesService.confirmDiscard()` for the
  prompt, and a `beforeunload` warning (fed by `UnsavedChangesService.dirty`) for tab close/reload. New
  forms/dialogs wire this as part of the Definition of Done (§3).

## 13. Testing

Tests protect behavior, prevent regressions and make refactoring safe. Coverage is a signal, not
the goal - high coverage with weak assertions is not quality.

- **TDD by default (normative, owner mandate).** Write the **failing test first**, then the implementation.
  Every behavior MUST cover the **happy path AND all plausible sad paths** (auth `401`, missing scope `403`,
  validation `400`, not-found `404`, conflict `409`, business rule `422`, boundary/edge values, visibility and
  idempotency edges) across **all categories** (unit, integration, architecture, frontend component/service/
  guard/interceptor, E2E). **No fake tests** - no assertion-free tests, no `status`-only checks, no filler.
  This is a Definition-of-Done item (§3). **Coverage tooling** is wired and reporting-only (never a gate that is
  weakened): backend **JaCoCo** (`./mvnw verify` → `target/site/jacoco`), frontend **vitest v8**
  (`ng test --coverage` → `coverage/`); the latest audit lives in `artifacts/test-reports/`.
- **Unit** tests cover domain/application logic without infrastructure.
- **Integration** tests cover persistence, transactions, APIs, messaging - use Testcontainers
  when infrastructure behavior matters. Share ONE container across the suite: a
  `@TestConfiguration` exposing `@Bean @ServiceConnection <Container>`, imported by a single
  `AbstractIntegrationTest` base (`@SpringBootTest(RANDOM_PORT)`, cached context) - never one
  container per test class.
- **Architecture** tests (ArchUnit + Spring Modulith) run in the normal suite and MUST NOT be
  weakened to make code pass.
- **Regression:** every bug fix MUST add a test that fails before the fix and passes after,
  whenever technically possible; if impossible, explain why.
- **Assert the contract, not just the status.** For APIs, assert the full error body
  (`{code, message, fields}`, the i18n `code`, the offending field) and the success shape - not
  only the HTTP status. Every validation-layer rejection (§5.5) MUST have a test proving it
  rejects with the right status and body (auth → 401, missing scope → 403, validation → 400,
  business rule → 4xx, duplicate → 409). Coverage with weak assertions is not quality.
- **Frontend:** protect real behavior - feature/API/state services, guards, interceptors,
  validators, form mappers, error handling, loading/empty/error states, critical journeys. The
  security infrastructure (auth interceptor's 401→refresh→replay, route guards) MUST be tested.
  Component tests render the DOM and assert the visible state (jsdom; polyfill `ResizeObserver`
  for the component library). E2E for critical flows only.
- **E2E (Playwright):** critical journeys run in a real browser against an **isolated, throwaway
  stack** (`compose.e2e.yaml`) whose Postgres is **ephemeral** (`tmpfs`) and on its own ports
  (frontend 4201), so E2E **MUST NOT** touch the development database. From `frontend/`:
  `npm run e2e:up` (build + start the isolated stack), `npm run e2e` (baseURL defaults to 4201;
  override with `E2E_BASE_URL`), `npm run e2e:down`. Never point the default E2E run at the dev stack
  (4200) — that pollutes real data. E2E is the layer that catches cross-cutting issues
  unit/integration miss (CORS origin, the same-origin proxy, port wiring).
- Right level of realism: mocks/fakes for logic; Testcontainers for infrastructure behavior. Do
  not mock everything blindly or boot the whole stack for simple logic. Tests create their own
  data via builders/factories.
- **Real infra, no excuses.** Integration tests run against a real Postgres via Testcontainers and
  MUST actually execute (never silently skipped behind a "no Docker" assumption - if Docker tooling
  fails, fix the infra). On recent Docker Engines the bundled docker-java negotiates an API version
  the daemon rejects (HTTP 400 "Could not find a valid Docker environment"); the surefire
  `api.version` system property in `backend/pom.xml` pins a supported version - do not remove it.

## 14. Delivery

- **Build & versions:** Maven via the wrapper only (`./mvnw`), no system Maven; never migrate
  build tools without explicit instruction. Versions prioritize stability/LTS; no RC/snapshot in
  production. Add libraries conservatively; isolate risky dependencies.
- **Versioning & releases (SemVer):** the application carries a **Semantic Version**
  `MAJOR.MINOR.PATCH` - **MAJOR** = backward-incompatible change; **MINOR** = new backward-compatible
  feature; **PATCH** = backward-compatible bug fix. **The backend is the single source of truth for the
  version (owner decision):** it lives as a **hardcoded literal** `app.version` in
  `backend/.../application.yml` (e.g. `version: 0.26.0`), is served by the public `GET /api/version`
  and **displayed in the UI** (login screen + sidebar footer); the frontend reads it from that endpoint.
  `compose.yaml` **does NOT inject `APP_VERSION`**, and `.env`/`.env.example` no longer carry it (owner
  removed it — legacy/unused for versioning). **Bump it on
  EVERY delivered change** (part of the Definition of Done) by editing the `application.yml` literal — each
  slice/change merged to `develop`/`main` increments the version per SemVer (**MINOR** for a new feature,
  **PATCH** for a fix/small change **or a purely internal refactor**); after the end-of-slice rebuild the
  footer/`/api/version` reflects it **automatically**, no `.env` edit needed. The version bump is
  **independent of the release note** (below).
- **Continuous local delivery — rebuild the dev stack each slice (owner decision):** at the **end of every
  slice**, after the merge, the agent rebuilds and recreates the local dev stack so `localhost:4200` reflects
  the merged code (Flyway applies the new migrations forward): `docker compose up -d --build frontend backend`
  (non-destructive — the Postgres volume/data persists; do NOT use `-v`/`down -v`). Verify backend health and
  that migrations applied before reporting done. (Note: `localhost:4201` is the throwaway E2E stack, separate
  from the dev stack on `4200`.)
- **Release notes (delivery document, customer-facing) — only at the end of a COMPLETE delivery:** a
  release note is written **once per finished delivery (a sprint / milestone), NOT per slice** — it
  summarizes everything shipped in that delivery. Do **not** create a release note per intermediate
  slice, and **never** for an internal refactor (nothing customer-facing). Each note lives in
  **`artifacts/release-notes/`** (one file per delivered version, e.g. `v0.12.0.md`), written **for key
  users and managers, NOT engineers**. It is a **serious delivery document**: a **header** (version, release
  date, status, audience, prepared-by), a short **executive summary** for managers, then a
  **thorough, detailed**
  description organized **by capability/theme**, in **business language**. Tone is **professional and
  respectful** - never childish, never condescending, no emoji-driven fluff. **No technical jargon**
  (no endpoints, tests, architecture, scopes-as-code, migrations or SemVer internals). The
  **development reports** (`artifacts/development-reports/`) remain the technical record; the release
  note is its human-facing, detailed companion.
- **User manual (bilingual):** the end-user manual is maintained in **en-US and pt-BR**
  (`artifacts/user-manual/fkerp-user-manual.en-US.md` and `...pt-BR.md`); keep both in sync whenever a
  user-facing behavior changes.
- **Git:** pragmatic Git Flow (`main`, `develop`, `feature/*`, `bugfix/*`, `release/*`,
  `hotfix/*`) and Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `refactor:`). PRs are
  focused and reviewable - tests, migrations, screenshots for UI, API impacts. Commit/push only
  when the owner asks; if on the default branch, branch first; never force-push. When delivering an
  implementation report, commit the work on the working branch first - the commit is mandatory
  before presenting the report. **Standing authorization (this project, until revoked):** the owner
  works solo and has authorized, without asking each time, pushing the working branch, merging it
  into `develop` and `main`, and pushing both. Gitflow branches/merges are kept for record and for
  when the team grows.
- **Generated files:** never hand-edited - modify the generation source (OpenAPI contract,
  schema, generator config). A hook blocks edits under generated paths.
- **CI/CD:** the pipeline runs build, unit + integration tests, frontend tests/build,
  lint/static analysis, dependency scan, migration validation, image build, deploy, smoke tests.
  Failed tests, broken builds, invalid migrations or broken contracts block merge/deploy.
- **Local dev:** reproducible env via Docker Compose when external services are needed; minimal
  but complete; `.env.example` provided; config is typed `@ConfigurationProperties` (records,
  `@ConfigurationPropertiesScan`, `@Validated`) that fails fast at startup on missing/invalid
  required properties (guard it with a startup test); secrets never committed.
- **Deployment/IaC:** business logic MUST NOT depend on cloud SDKs or deployment specifics -
  abstract storage/messaging/notifications. Do not create Terraform/Helm/K8s files unless the
  project requires it.
- **Feature flags:** reduce delivery risk but MUST NOT become permanent hidden complexity - each
  has a removal condition; most are temporary.
- **Audit:** relevant entities track `createdAt, updatedAt, createdBy, updatedBy`; important
  business actions are audited in business language.
- **New project bootstrap:** generate a minimal runnable backend+frontend for the first feature -
  never a large empty architecture with unused modules or placeholder classes.

## 15. Enforcement & tooling (authoritative gates)

These encode the rules executably. Never weaken, skip or delete them to make code pass - change
the rule with the owner and update this file instead.

**ArchUnit** (`backend/src/test/java/com/fksoft/architecture/ArchitectureTest.java`, plain
JUnit over the ArchUnit core API):

- `domainMustNotDependOnDeliveryOrInfra` - `domain..` MUST NOT depend on `application..`/`infra..`.
- `infraMustNotDependOnDelivery` - `infra..` MUST NOT depend on `application..`.
- `controllersMustNotAccessRepositories` - `..api..` MUST NOT depend on `*Repository`.
- `noLombokDataOnEntities` / `noLombokSetterOnEntities` - no `@Data` / `@Setter` on `@Entity`
  (entities mutate only through business methods; `@Getter`/`@NoArgsConstructor` are fine).
- `noImplSuffix` - no class name ends in `Impl`.
- `noFieldInjection` - no field `@Autowired` (constructor injection only).
- `exceptionsLiveWithTheirDomain` - `*Exception` resides in `domain..`, not `..api..`/`..infra..`.
- No per-area persistence isolation inside `domain`: the domain package is internally open - any
  domain class MAY use another domain area's `@Entity`/`*Repository` directly (see §6). The only
  enforced boundary is `domain` ↛ `application`/`infra`.

**Spring Modulith:** `ModularityTests.verifiesModularStructure()` verifies inter-module
boundaries. A presentation-completeness test fails if any `DomainException` subclass has no HTTP
status in `HttpErrorMapping`.

**Format/style** (bound to `verify`, so CI blocks): Spotless (palantirJavaFormat,
removeUnusedImports, importOrder) - `spotless:apply` to fix; Checkstyle (fail on violation):
`AvoidStarImport`, `UnusedImports`, `IllegalImport`, `MissingJavadocMethod` (public methods over
a min length), `MethodLength`, `ParameterNumber`, `EmptyCatchBlock`, `EqualsHashCode`,
`FallThrough`, `MissingSwitchDefault`, `StringLiteralEquality`, `OneTopLevelClass`.

**Hooks** (`.claude/settings.json` -> `.claude/hooks/`):

- PreToolUse `protect-generated` (Edit|Write) - blocks edits under `target/`, `generated/`,
  `generated-sources/`, `node_modules/`, `dist/`, `.angular/`.
- PostToolUse `format-java` (Edit|Write) - runs `spotless:apply` on edited `.java` files.

**Permission tiers** (`.claude/settings.json`):

- **deny:** `git push --force*`, `git reset --hard*`, `git clean -fd*`, `rm -rf *`,
  `docker volume rm*`, `docker compose down -v*`, `*flyway:clean*`, `dropdb*`, reading `./.env*`
  and `./secrets/**`.
- **ask:** `git push*`, `git rebase*`, `docker compose down*`, `rm -r *`, `flyway:*`, `psql*`.
- **allow:** `./mvnw` with `test/verify/compile/spotless:apply/spotless:check/checkstyle:check`;
  `npm run lint`, `npm test`, `npm run build`.
- A denied command is a signal - never work around it; explain the risk and ask the owner to run it.

**Project commands** (inspect `README.md`/`pom.xml`/`package.json` before inventing any):

```bash
cd backend && ./mvnw verify           # build + tests (ArchUnit + Modulith; needs Docker up)
cd backend && ./mvnw spotless:apply   # format
npm run lint && npm test              # frontend (from frontend/)
```
