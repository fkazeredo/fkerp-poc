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
- Tests created/updated. A bug fix MUST add a regression test that fails before and passes
  after; if impossible, explain why.
- Flyway migration when the schema changes. OpenAPI/docs updated when contracts change.
- i18n messages added for any user-facing text; global error handling respected.
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
| **Domain** (pure core) | `com.fksoft.domain.<module>` | Services, entities, repositories, domain events, enums, value/view records, business exceptions, public module facades (ports). Plus kernel `domain.error` (`DomainException`, `ErrorDetails`, `RateLimited`). |
| **Delivery** (driving adapters) | `com.fksoft.application` | Entry mechanisms only: `api` (controllers) + `api.dto`; `realtime` (WebSocket publishers) + `realtime.dto`; `queue` (consumers) if any. |
| **Infra** (driven adapters + config) | `com.fksoft.infra.<concern>` | `security` (JWT, `UserContext`/`UserContextProvider` + adapter), `email`, `integration`, `web` (`ApiErrorResponse`, `GlobalExceptionHandler`, `HttpErrorMapping`, `PageResponse`), `i18n`, `time`, `observability`, `socket`. |

```txt
com.fksoft
  domain                         <- pure core; one package per module; MUST NOT import application/infra
    order/   Order  OrderStatus  OrderService  OrderRepository  OrderCancelled(event)
             OrderNotFoundException  OrderCatalog(facade/port)  OrderResponse(maps entity -> stays here)
    <other business modules>/
    error/   DomainException  ErrorDetails  RateLimited        <- kernel
  application                    <- delivery; entity-free
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

**Entity-free delivery.** Services return view/Response records, never `@Entity`. A `Response`
DTO that maps an entity (via `from(entity)`) stays inside its domain module; all other
request/response DTOs are entity-free and live in `application.api.dto`.

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
explicit factory methods close to the object (`OrderResponse.from(order)`); dedicated mapper
classes are not the default (a mapping library MAY be used for repetitive many-field mappings).

### 5.5 Validation

Validate at every relevant boundary: delivery (controllers, consumers, schedulers), application
(preconditions, existence), domain (invariants, transitions), persistence (constraints, FKs,
indexes), integration (incoming/outgoing data). The domain MUST NOT depend on controller
validation to remain valid.

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
status. Every API MUST have a global handler and a predictable error body:

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

The `domain` package is organized by business area (`domain.<area>`) but is **internally open**:
any class under `domain` MAY use any other class under `domain` directly, including across areas
(e.g. `domain.crm` may call `domain.identity` repositories/types). There is **no Facade pattern**
for intra-domain collaboration and **no enforced inter-area boundary inside `domain`**. Spring
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
  controllers live flat in `application.api`, request/response DTOs in `application.api.dto`,
  realtime/socket in `application.realtime` (+ `application.realtime.dto`), queue consumers in
  `application.queue`. Do NOT create `application.api.<area>` sub-packages.
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
  extract builders, mappers, validators.
- **HTTP & errors:** `core/http` owns base URL, auth headers, correlation ID, global error
  handling, interceptors. Each feature exposes domain-oriented API services - no raw `HttpClient`
  in components. Combine global normalization with feature-specific error presentation.
- **Realtime:** only when justified; `core/realtime` manages the connection (STOMP), feature
  services translate technical events into UI behavior. Components never handle raw protocol
  messages.
- **UI & styling:** the component library owns component internals; Tailwind owns layout and
  custom widgets. All user-facing text goes through the i18n layer.

## 13. Testing

Tests protect behavior, prevent regressions and make refactoring safe. Coverage is a signal, not
the goal - high coverage with weak assertions is not quality.

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
- **Frontend:** protect real behavior - feature/API/state services, guards, interceptors,
  validators, form mappers, error handling, loading/empty/error states, critical journeys. E2E
  for critical flows only.
- Right level of realism: mocks/fakes for logic; Testcontainers for infrastructure behavior. Do
  not mock everything blindly or boot the whole stack for simple logic. Tests create their own
  data via builders/factories.

## 14. Delivery

- **Build & versions:** Maven via the wrapper only (`./mvnw`), no system Maven; never migrate
  build tools without explicit instruction. Versions prioritize stability/LTS; no RC/snapshot in
  production. Add libraries conservatively; isolate risky dependencies.
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
