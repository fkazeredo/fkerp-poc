# Data-driven reform — cadastros, configurable workflows & the visual editor

- **Date:** 2026-06-23 04:30
- **Branch:** `feature/data-driven-workflows-cadastros` → `develop` → `main`
- **Version:** 0.47.2 → **0.48.0** (MINOR — configurability: managed reference data + configurable workflows/
  attention rules + the visual editor; no existing business behaviour changed)
- **Type:** feature (`feat:`). Customer release note: `artifacts/release-notes/v0.48.0.md`.

## 1. Goal status

**Achieved.** The reform makes the system data-driven where it should be: the operational value-lists become
**admin-managed cadastros**, the six lifecycles become **configurable workflows**, the pending-items reasons
become **configurable attention rules**, and a **visual editor** (`@swimlane/ngx-graph`) exposes the workflows
and rules. Crucially this is a discernment exercise, not a blanket conversion — **structural enums stayed enums**.
No existing operational behaviour or JSON contract changed; labels were preserved.

## 2. The criterion (CLAUDE.md §1 invariant 8)

The owner caught an early **category error** (the plan had proposed turning `DiscountType` into a cadastro) and
set the lookup×structural×workflow test, now recorded as an invariant:

- **CADASTRO** — a rótulo/motivo/tipo/canal an admin can add/rename/deactivate without code, where no logic
  branches on the value. → loss/rejection reasons, activity/attempt types & results, channels, **item type**.
- **ENUM stays** — structural/behavioural: code switches/calculates/maps on it. **`DiscountType`** (`amountOf`/
  `isValid`) and **`BookingNeed`** (`toStatus`) explicitly stayed enums; `ProposalItemType`'s `TRAVEL_PACKAGE`/
  `CAR_RENTAL` stay reserved codes that anchor the type-specific confirmation flows.
- **WORKFLOW** — a state machine (the six lifecycles) and a configurable **attention rule** per worklist reason.

## 3. Completed capabilities

1. **`domain.reference` + `domain.workflow` kernels** (engine, definitions/states/transitions/rules, attention
   rules, SPI guard/post-function + attention-condition catalog). Scopes `reference:manage` / `workflow:manage`.
2. **Six lifecycles → configurable workflows** (V40–V49): `status`/`stage` stay a denormalized code (JSON
   unchanged) + an FK to the workflow state; user-driven transitions write the FK through the engine, the
   computed Booking statuses keep a trigger-maintained FK.
3. **Lookup cadastros** (V50 CRM/opportunity, V51 Sales, V52 Booking) and **`ProposalItemType` → cadastro** with a
   `requires_booking` attribute (V53); the three item tables rewired to FKs (expand/contract, trigger keeps raw-SQL
   fixtures working). `BookingNeed` stayed an enum (separate from item-type).
4. **Attention-rules engine** (V54): the three pending-reason enums (`PendingReason`/`OpportunityPendingReason`/
   `BookingPendingReason`) deleted; the worklists now load active `WorkflowAttentionRule`s and build both the JPA
   query and the in-memory tags from the catalog conditions. Seeded `system` rules reproduce today's worklists
   byte-identically; the fixed windows (14d/7d) became editable params.
5. **Workflow admin API** (V55): `WorkflowAdminController` (`/api/workflows`) — list/catalog/detail + attention-rule
   CRUD + state update — with the `system` lock; `workflow:manage` granted to the `reference:manage` admin.
6. **Frontend cadastro reform:** `ReferenceService` generalized for the `crm`/`sales`/`booking` base paths; the 7
   operational forms (opportunity activity/loss, proposal reject/decline/send + items, booking attempt/fail) now
   load the cadastros and send ids; 10+1 cadastro CRUD screens (reusing `ReferenceList`).
7. **Visual workflow editor (Phase 5c):** `WorkflowService` + a workflows list + the `ngx-graph` editor (states =
   nodes, transitions = edges, LR dagre), a state-edit panel, a read-only transition view and the attention-rules
   side panel (create/edit/delete, system-lock). Guard + nav + command palette + `g w` + `?` overlay + unsaved guard.

## 4. Key technical decisions & gotchas

- **`@swimlane/ngx-graph` on Angular 22 zoneless.** The owner chose to keep ngx-graph despite its peer range (19–21)
  and the app being zoneless. v12 turned out to be a **modern signal-based standalone** build with **no Angular-
  animations dependency**; it compiles and renders under Angular 22 zoneless with only `LayoutService` provided and
  `dagre`/`webcola` allow-listed as CommonJS. Pan/zoom are disabled so node clicks register, and the clickable node
  group uses a **unique class** (`wf-node`) so it never collides with ngx-graph's own internal `g.node` wrappers
  (the original collision swallowed the click). Real browser rendering + interaction verified by E2E.
- **Label fidelity.** The new seed migrations had drifted from the historical pt-BR labels in **13 places** (e.g.
  `NOT_INTERESTED` 'Sem interesse' → 'Não interessado'). Restored the exact originals (safe — the dev DB was at
  V37, only the ephemeral E2E DB had them) and aligned two integration assertions that had been written against the
  drifted labels.
- **Item-add E2E regression (zoneless + PrimeNG).** The proposal item dialog's number field stopped committing under
  Playwright's synthetic `fill()` once the async cadastro loads removed the incidental change-detection that had
  masked it. Root cause: zoneless + PrimeNG `inputNumber` ignores `fill()`. Fixed in the test tooling
  (`pressSequentially` = real keystrokes; the product works for real users), and made the item-type (and the
  workflow rule condition) an **explicit selection** — consistent with every other cadastro select and free of the
  async-default re-render.

## 5. Verification

- **Backend:** `./mvnw verify` → **633 tests, 0 failures**; Spotless + Checkstyle + ArchUnit + Modulith green.
- **Frontend:** `ng test` → **428** tests green (the lone intermittent is the pre-existing booking-detail jsdom-load
  flake, 30/30 in isolation); `ng build` clean.
- **E2E (ephemeral 4201):** full suite green including the new workflow-editor journey (renders the graph, edits a
  state, creates/deletes an attention rule) and a guard-denied case; residual toast/overlay timing absorbed by the
  configured single retry.

## 6. Artifacts

- Migrations **V38–V55** (kernels, six lifecycles, cadastros, item-type cadastro, attention rules, workflow scope).
- CLAUDE.md **§1 invariant 8** (+ §2/§3 references). Memory `data-driven-by-default`.
- Manual updated (pt-BR + en-US): §11 cadastros expanded + **§11.5 workflow editor**; `g w` shortcut.
- `app.version` → **0.48.0**.
