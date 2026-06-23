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
