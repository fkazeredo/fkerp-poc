# FKERP — User Manual

> **Audience:** end users of the FKERP system (commercial / sales team).
> **Language:** English (en-US). A Portuguese edition is maintained alongside
> (`fkerp-user-manual.pt-BR.md`).
> **Scope:** covers everything released through **v0.12.0** — the full Commercial / CRM
> lead lifecycle (intake, list/search/filters, detail, assignment, interactions, the Contacted rule,
> qualification, the Lost flow, visibility by profile, pending items and indicators) plus lead
> **deduplication**. It grows as new capabilities ship.

---

## 1. What FKERP is

FKERP is the company's ERP. The first capability available is the **Commercial / CRM**
area, where the sales team registers and organizes **leads** — people or companies that
showed initial interest. A lead is only an *interested party*; it is not yet a customer,
an opportunity, or a sale.

This release lets you:

- Sign in securely.
- Register a new lead (with an optional first note and an optional responsible person).
- See, search and filter the leads you are allowed to work with.
- Open a lead's detail and qualify it, mark it lost (with a reason), or reassign it.
- Manage the supporting reference lists (*cadastros*): **Origins**, **Loss reasons**,
  **Interaction types**, and **Interaction results**.

---

## 2. Getting started

### 2.1 Accessing the system

Open your browser at the address provided by your administrator. For a local
environment this is:

| What | Address |
|------|---------|
| Application | http://localhost:4200 |

### 2.2 Signing in

1. On the login screen, type your **username** and **password**.
2. Press **Enter** or click **Entrar** (Sign in).
3. On success you land on the home screen. If the credentials are wrong, a message
   reads *"Usuário ou senha inválidos."* (Invalid username or password.)

> Development/demo accounts (password = username + `123`): **`comercial`** (manager — sees & operates
> all), **`vendedor`** (seller — own + the unassigned pool), **`representante`** (sales rep — only
> their own leads), **`diretor`** (board — sees everything, read-only) and **`financeiro`** (no Lead
> access). Your real account is created by your administrator.

Your session is kept alive automatically and securely while you use the app. If you
reload the page you stay signed in. Use **Sair** (Sign out) in the top bar to end the
session.

The application **version** (e.g., `v0.12.0`) is shown on the login screen and in the bottom of the
sidebar, so you always know which release you are using.

### 2.3 Profiles & access — what you can see and do

What you can see and do depends on your **profile**. The system enforces this on the server, so the
rules always hold even outside the screen:

| Profile | Sees | Can operate? |
|---|---|---|
| **Admin / Commercial Manager** | all leads | yes — create, assign, qualify, mark lost, interactions |
| **Board / Directors, Marketing** | all leads | **no** — consultation only (read-only) |
| **Sellers, Call Center** | their own leads **+ the unassigned pool** | yes |
| **Sales Representatives** | **only their own** leads | yes (only on their own) |
| **Finance / HR / IT** | — | no access to the Lead module |

- A **representative never sees** leads assigned to other representatives, and cannot open or act on
  them. Searching and filtering never reveal leads outside your visibility.
- **Consultation-only** users (directors, marketing) browse lists and details but see **no action
  buttons** and no *Novo lead*.
- Users **without Lead access** don't see the **Leads** menu and are sent back to the home screen,
  which shows a short "no access" notice.

---

## 3. Keyboard-first navigation

FKERP is designed to be driven **from the keyboard**, so you rarely need the mouse.

| Action | Shortcut |
|--------|----------|
| Open the **command palette** (search any action) | `Ctrl` + `K` (or `Cmd` + `K` on macOS) |
| Show **all shortcuts** (help) | `?` |
| New lead | `n` |
| Go to the **lead list** | `g` then `l` |
| Go to **Origins** | `g` then `o` |
| Go to **Home** | `g` then `i` |
| Move between fields | `Tab` / `Shift` + `Tab` |
| Confirm / submit a form | `Enter` |
| Close a dialog / cancel | `Esc` |

**On a lead's detail page** (when no dialog is open):

| Action | Shortcut |
|--------|----------|
| **Register interaction** | `i` |
| **Qualify** | `q` |
| **Mark as Lost** | `p` |
| **Reassign / Take it** | `r` |
| Back to the list | `Esc` |

Notes:

- The **command palette** (`Ctrl/Cmd + K`) lets you type the name of an action and run
  it — the fastest way to move around. Press `?` anytime to see the full list.
- When you open a form, the **first field is focused automatically**, so you can start
  typing right away.
- Single-letter shortcuts (like `n` or `i`) are ignored while you are typing inside a field
  or while a dialog is open, so they never interfere with data entry.

### Light / dark theme

Use the **sun/moon button** in the top bar (or the *"Alternar tema claro/escuro"* command in the
palette) to switch between light and dark mode. Your choice is remembered on the device.

---

## 4. Home

The home screen summarizes the Commercial / CRM area and offers quick links to:

- **Novo lead** (New lead) — open the lead registration form.
- **Cadastros** (Reference data) — manage the supporting lists.

A reminder of the main keyboard shortcuts is shown at the bottom.

---

## 5. Registering a lead

Open **Novo Lead** (top menu, the `n` shortcut, or the command palette).

### 5.1 Fields

| Field | Required | Notes |
|-------|----------|-------|
| **Nome** (Name) | Yes | The person's or company's name. |
| **Origem** (Origin) | Yes | Where the lead came from (e.g., Website, Instagram). Pick from the list. |
| **Telefone** (Phone) | — | Digits only. |
| **WhatsApp** | — | Digits only. |
| **E-mail** | — | Must be a valid e-mail address. |
| **Responsável** (Responsible) | — | The user who will work this lead. Leave empty to create it unassigned. |
| **Anotação inicial** (Initial note) | — | A first note; it becomes the first entry in the lead's history. |

**Contact rule:** a lead must have **at least one** way to be reached — phone, WhatsApp,
or e-mail. If none is provided, the system rejects the lead with the message
*"Informe ao menos um contato (telefone, WhatsApp ou e-mail)."*

**No duplicates:** a lead is refused when an **open** lead (one not yet Lost) already has the **same
phone/WhatsApp number or e-mail** — the message *"Já existe um lead ativo com este telefone ou
e-mail"* appears. Open the existing lead instead of creating a copy. Once a lead has been marked
**Lost**, the same contact can be registered again.

### 5.2 Saving

- Press **Enter** or click **Salvar lead** (Save lead).
- On success, a green confirmation appears (*"Lead criado"*) and you return to the home
  screen.
- The new lead starts in status **NEW** (Novo).
- If something is invalid, the message appears **next to the field** in question, so you
  can fix it and resubmit. Use **Cancelar** (Cancel) to discard the entry and return to the
  home screen.

---

## 6. Finding leads — the list, search & filters

Open **Leads** from the top menu (press `g` then `l`, or use the command palette). The list shows
the leads you are allowed to work with — the ones where you are the responsible, plus the unassigned
ones. Managers see every lead.

### What the list shows

Each row shows the lead's **name**, **main contact**, **origin**, **status**, **responsible** (or
*Sem responsável* when unassigned), **creation date**, the **latest interaction** (date and type,
when there is one) and the **next contact date** (when set).

### Searching and filtering

Use the filter bar above the table:

- **Buscar** (Search) — type part of a name or contact; results update as you type.
- **Status** — pick one or more statuses. By default **lost leads are hidden**; they appear only
  when you add **Perdido** (Lost) to the status filter.
- **Origem** (Origin) — restrict to a single origin.
- **Responsável** (Responsible) — restrict to one person, or pick **Sem responsável** to see only
  unassigned leads.
- **Criado de / até** (Created from / to) — restrict to a creation-date range.
- **Limpar** (Clear) — reset every filter.

The list is paginated — use the pager at the bottom to move between pages. You can only ever see the
leads you are allowed to work with; searching and filtering never reveal anyone else's leads.

### Pending items (Pendências)

Open **Pendências** from the sidebar (or the home tile) for an operational worklist of the leads that
**need action**, so follow-ups aren't lost. Each lead is tagged with one or more reasons:

- **Sem responsável** — the lead has no responsible person (needs an owner).
- **Sem interação** — a New lead that has never been contacted.
- **Contato atrasado** — its scheduled next contact is past due.
- **Sem desfecho** — a Contacted lead with no follow-up planned and not yet qualified or lost.

The same **visibility rules** apply: a representative or seller sees only their own pending items, a
manager sees all. Qualified and lost leads never appear here. Click a name to open its detail and act.

### Indicators (Indicadores)

Open **Indicadores** from the sidebar (or the home tile) for a read-only view of the **top of the
funnel** over a period. It shows:

- **KPI cards:** **Total** in the period, then by status — **Novos** (New), **Em contato**
  (Contacted), **Qualificados** (Qualified) and **Perdidos** (Lost) — plus **Aguardando 1º contato**
  (New leads not yet contacted). Unlike the operational list, **Lost leads are counted here**.
- **Por origem** (by origin) and **Por responsável** (by responsible) — each label with its count and
  a proportion bar; unassigned leads appear as **Sem responsável**.

Use the **Criado de / até** date pickers to choose the period (by creation date); it **defaults to the
current month** (1st → today). Click **Todo o período** to clear the dates and see all-time numbers.

The same **visibility rules** apply: a representative or seller sees only their own numbers, a manager
sees everyone's. It is an operational read-out, not an executive dashboard — there are no charts,
forecasts or financial figures yet.

## 7. Opening a lead — detail & actions

Click a lead's **name** in the list to open its detail page (you can only open leads you are
allowed to see; others return a permission message).

### What the detail shows

- **Main data & contacts:** name, status, phone, WhatsApp, e-mail, origin, responsible, the
  creation and last-update dates, and the next-contact date when set.
- **Interaction history:** the lead's notes/contacts over time, when there are any.
- **Assignment history:** who assigned the lead to whom, and when.
- **Qualification:** shown once the lead has been qualified (when, by whom, note).
- **Loss:** shown once the lead has been marked lost (reason, when, by whom, note).

### Acting on a lead

Buttons appear at the top when the action applies to the current status:

- **Qualificar** (Qualify) — marks a lead as **Qualified**. It appears only once the lead is **Em
  contato** (Contacted) and has a **responsible person**; you must enter the **main interest**
  (*interesse principal*) and may add a commercial note. Qualifying does not create an Opportunity or
  Customer by itself — it makes the lead **eligible** to originate one.
- **Criar oportunidade** (Create opportunity) — appears only on a **Qualified** lead. It opens a
  commercial **Opportunity** from the lead (see *Opportunities* below). Keyboard shortcut: **`o`**.
- **Marcar como perdido** (Mark as Lost) — sets the lead to **Lost**; you must choose a **loss
  reason** and may add a note. Lost is final.
- **Reatribuir** (Reassign) — changes the responsible person (or clears it to unassigned). Every
  change is recorded in the assignment history.
- **Assumir** (Take it) — assigns the lead **to yourself**. It appears only on an unassigned lead
  you can see and is the way a sales rep picks up new leads.

After an action, the detail refreshes and a confirmation appears. The history and the
qualification/loss sections are always **preserved** — a lost or qualified lead keeps showing that
information.

### Who can assign a lead

Which of the two buttons you see depends on your **assignment authority**:

- **Commercial managers and administrators** can assign or reassign a lead to **anyone** (and clear
  it to unassigned). They see the **Reatribuir** button.
- **Sales representatives** can only **take an unassigned lead for themselves**. They see the
  **Assumir** button instead — they cannot hand a lead to another person or unassign it. The system
  enforces this on the server, so the rule holds even outside the screen.

### Registering an interaction

Every contact, contact attempt or internal note is recorded in the lead's history. Click **Registrar
interação** (Register interaction) at the top of the detail and fill in:

| Field | Required | Notes |
|-------|----------|-------|
| **Tipo** (Type) | Yes | Phone call, WhatsApp, Email, In-person, Internal note, or Other. |
| **Resultado** (Result) | Yes | The outcome of the interaction (see below). |
| **Data** (Date) | Yes | When it happened. Defaults to now; you can backdate it, but it cannot be in the future. |
| **Descrição** (Description) | Yes | What happened — this becomes the history entry. |
| **Próximo contato** (Next contact) | — | Optionally schedule the next contact; it then shows on the lead and on the list. |

The interaction is added to the **history** (which is never deleted); the author and the moment are
recorded automatically.

**New → Contacted.** Registering an **effective contact** moves a lead in status **Novo** (New) to
**Em contato** (Contacted). A contact is effective for every result **except** *Não atendeu* (No
answer) and *Contato inválido* (Invalid contact) — those are failed attempts, so the lead stays
**Novo** while still keeping the attempt in its history. Leads already past New keep their status.

## 8. Opportunities (*Oportunidades*)

An **Opportunity** is a real commercial negotiation opened from a **qualified** lead. The lead and
the Opportunity stay **separate records**: the Opportunity carries over the lead's data (origin,
responsible, main interest) so nothing is re-typed, while the lead remains the system of record for
the contacts and history. An Opportunity is **not** a proposal, sale, order or financial record.

### 8.1 Creating an Opportunity

On a **Qualified** lead's detail, click **Criar oportunidade** (or press **`o`**). You may optionally
inform the **product type / area of interest**, an **estimated value**, an **expected closing date**
and an **initial commercial note**. The Opportunity is created at the first pipeline stage, **Nova**
(New). Each lead originates **at most one** Opportunity; a second attempt is blocked and points to the
existing one. Creating an Opportunity does not change the lead.

### 8.2 The operational list

Open **Oportunidades** in the side menu (or via the command palette, `Ctrl K`) to see the
Opportunities you may work. The list is paginated and shows, for each Opportunity:

| Column | Notes |
|--------|-------|
| **Título** (Title) | The Opportunity title; links to its **source lead**. |
| **Responsável** (Responsible) | The owner, or *Sem responsável* (unassigned). |
| **Estágio** (Stage) | Nova, Descoberta, Aderência, Pronta p/ proposta, Perdida. |
| **Valor estimado** (Estimated value) | When informed. |
| **Fechamento previsto** (Expected close) | When informed. |
| **Criado em** (Created) | The creation date. |
| **Última atividade / Próxima ação** (Last activity / Next action) | The most recent activity's date and the planned next action, when present. |

Above the list there is a **search and filter** bar. **Search** looks across the Opportunity's title,
product type and interest **and also the source lead's name and contacts (phone, WhatsApp, email)** —
so you can find a negotiation by typing the customer's name or phone number. The available filters are:

| Filter | What it does |
|--------|--------------|
| **Estágio** (Stage) | One or more pipeline stages. |
| **Responsável** (Responsible) | A specific owner, or *Sem responsável* (unassigned). |
| **Origem** (Origin) | The origin of the lead that started the Opportunity. |
| **Criado de / até** (Created from / to) | The Opportunity's creation period. |
| **Fechamento de / até** (Close from / to) | The expected-close period. |
| **Valor mín. / máx.** (Value min / max) | The estimated-value range. |

Filters combine (the list shows only Opportunities matching all of them) and the **Limpar** (Clear)
button resets them at once. Applying any filter returns the list to the first page.

- **Lost Opportunities are hidden by default.** They show only when you select the **Perdida** (Lost)
  stage in the filter.
- **You see only what is yours to work.** A representative sees **only their own** Opportunities; a
  seller sees their own **and the unassigned** ones; commercial managers and the board see **all**.
  Search and filters never reveal Opportunities outside your reach.
- The list shows **commercial pipeline data only** — never proposal, sale, order, booking, financial
  or commission information.

### 8.3 Opportunity detail

Click an Opportunity's **title** in the list to open its **detail** — a consultation view to understand
the negotiation before the next commercial action. The detail shows:

- **Commercial summary:** responsible, origin, main interest, product type, estimated value and expected
  close date (when informed), notes, and the creation and update dates.
- **Source lead:** the name and contacts (phone, WhatsApp, email) of the lead that started the
  Opportunity, with a **View source lead** shortcut — the lead stays traceable and remains the source of
  record for contacts and history.
- **Loss:** when the Opportunity is **Perdida** (Lost), the detail shows the reason, the date, who
  recorded it and the note.
- **Stage movement:** the history of the Opportunity's stage changes (from which stage to which, when and
  by whom), newest first.
- **Commercial activity history:** every registered activity (type, result, description, date, next
  action and author), newest first.

**Edit commercial details.** If you have operation permission, the detail offers the **Editar dados
comerciais** (Edit commercial details) action (keyboard shortcut **`e`**): adjust the **estimated value**,
the **expected closing date**, the **product type / interest area** and the **commercial notes**. Leaving
a field blank **clears** it. The **main interest** is not edited here — it stays as set at the Lead
qualification. The value and expected closing date then also appear in the **list**. Editing does **not**
create financial, receivable, sales forecast, booking, proposal or commission data — it is commercial
information only.

**Register activity.** If you have operation permission, the detail offers the **Registrar atividade**
(Register activity) action (keyboard shortcut **`a`**): provide the **type** (phone call, WhatsApp, email,
meeting, internal note, document request, price discussion, travel requirement clarification or other),
the **result** (client engaged, needs follow-up, waiting for client, waiting for internal information,
product fit identified, ready for proposal, not interested or other), the **date** (not in the future), a
**description** and, optionally, a **next action**. The activity is added to the history (which **cannot**
be deleted), and the last-activity date and next action then also appear in the **list**. Registering an
activity does **not** move the stage (use **Advance stage**) and does **not** create a proposal, sale,
booking or financial record.

**Advance stage.** Each Opportunity has a **current stage** in the commercial pipeline — **Nova** (New),
**Descoberta** (Discovery), **Aderência** (Product Fit), **Pronta p/ proposta** (Ready for Proposal) or
**Perdida** (Lost). If you have operation permission, the detail offers the **Avançar estágio** (Advance
stage) action (keyboard shortcut **`s`**): the Opportunity advances **one step at a time** along the
pipeline — **New → Discovery → Product Fit → Ready for Proposal** — and you **cannot skip** stages or
**go back** to an earlier one. Every advance is recorded in the **stage movement** history. At **Ready for
Proposal** there is no next step (this sprint's pipeline ends there) and *Ready for Proposal* is just a
stage — it does **not** create a proposal in this release. To end a negotiation, use **Mark as lost**
(below), from any active stage.

**Mark as lost.** If you have operation permission over Opportunities, the detail offers the **Marcar
como perdida** (Mark as lost) action (keyboard shortcut **`p`**): pick a **loss reason** (required) — no
budget, no decision, no response, competitor chosen, product mismatch, price too high, travel cancelled,
duplicated Opportunity, out of profile or other — and, optionally, a note. (These reasons are specific to
the Opportunity, different from the lead's loss reasons.) The Opportunity moves to the **Perdida** (Lost)
stage (which is **final** — it does not reopen) and the loss is recorded on the detail itself. This action
**does not change** the source lead. Consultation-only users do not see the actions, and no one can
operate an Opportunity they are not allowed to see.

The detail shows **commercial data only** — never proposal, sale, order, booking, financial, commission
or customer care information.

### 8.4 Pending Opportunities

Open **Pending Opportunities** in the sidebar (or from the command palette, `Ctrl K`) for an
operational worklist of the Opportunities that **need action**, so a negotiation does not stall
silently. The table lists, for each Opportunity, the **title** (links to the detail), the **stage**,
the **responsible**, the **estimated value**, the **expected closing date**, the **next action**, the
**last activity** and the pending **reasons**. Each Opportunity is tagged with one or more reasons:

- **No recent activity** — no commercial activity has been recorded in the last **14 days**.
- **Next action overdue** — the planned next action is past due.
- **Stuck in New** — it has stayed in the **New** stage for more than 14 days without moving.
- **Stuck in Discovery** — it has stayed in the **Discovery** stage for more than 14 days.
- **Ready for proposal** — it is ready for a proposal (a step not yet handled in this version).
- **Closing date overdue** — its expected closing date has passed.

The same **visibility rules** as the list apply: a representative sees **only their own** pending; a
manager sees all. **Lost** Opportunities never appear here (they are terminal). This is an operational
view, not an executive dashboard — there are no notifications, e-mail alerts or deadline targets (SLAs),
and no proposal, sale, booking or financial data.

### 8.5 Opportunity indicators

Open **Opportunity indicators** in the sidebar (or from the command palette, `Ctrl K`) for a minimum view
of the commercial pipeline. The screen has **two blocks**, like a mainstream CRM:

- **Volume in the period** (filtered by creation date; default = current month): **Total** and **Lost**,
  plus the breakdowns **By stage**, **By origin** and **By responsible** (counts). Adjust the period with
  the **Created from / to** fields, or click **All time** to see the whole history.
- **Current pipeline** (a snapshot of today, **independent of the period**): **Active** (open, not lost),
  **Ready for proposal**, **Closing date overdue** (open with the expected closing date already past), the
  **active pipeline value** and the **value by responsible**.

The same **visibility rules** apply: a representative sees **only their own** numbers; a manager sees all.
**Lost** Opportunities are counted in the volume (and in "Lost"), but never in the active pipeline or its
value. This is an operational view, **not** an executive dashboard — no revenue, cash flow, sales
forecast, commission or ROI, and the "Ready for proposal" indicator does **not** create a proposal.

## 9. Proposals (Sales module)

FKERP separates the modules in the menu: besides **Comercial / CRM** (Leads and Opportunities), there is
now a **Vendas** (Sales) module with the **Propostas** (Proposals) screen. A **commercial proposal** is the
formalized offer to the client, created from an Opportunity that is **Ready for Proposal**.

### 9.1 Creating a proposal
On the detail of an Opportunity that is **Ready for Proposal**, click **Criar proposta** (Create proposal).
Enter the **client-facing title / summary** (pre-filled with the Opportunity name; you may adjust it) and,
optionally, the **validity date**, the **commercial terms**, **notes** and the **responsible** (which
defaults to the Opportunity's). The proposal reuses the source Opportunity's data — and, through it, the
Lead's — **without retyping**, and starts as a **Draft**. The Opportunity is **not** changed.

Key rules:

- **Only a "Ready for Proposal" Opportunity** may originate a proposal — earlier stages and **lost**
  Opportunities cannot.
- **One active proposal per Opportunity**: while an open proposal exists, creating another for the same
  Opportunity is blocked (a new one is allowed only once the previous is rejected, expired or cancelled).
- Creating a proposal does **not** create a sale, order, booking or financial data — that is a future step.

### 9.2 The list and the detail
Open **Vendas → Propostas** in the menu for the list of the proposals you may see (title, status,
responsible, the source Opportunity, the **total**, validity and creation date). Click the title to open the
**detail**, which shows the proposal summary, the status, the **source Opportunity** (with shortcuts to the
Opportunity and the Lead) and the **items** (see below). The same **visibility rules** as Opportunities
apply: a representative sees only their own proposals; a manager sees all.

### 9.3 Proposal items
The **Itens** (Items) card on the proposal detail lists what will be offered to the client. While the
proposal is a **Draft** and you have permission to operate it, you can **add**, **edit** and **remove**
items; each item contributes to the proposal **total**, shown at the bottom of the card and in the list.

Each item has:

- a **type** — *Pacote de viagem* (Travel package), *Locação de veículo* (Car rental), *Taxa de serviço*
  (Service fee) or *Outro* (Other);
- a **description**, a **quantity** (a whole number, at least 1) and a **unit value**;
- an **optional discount**, applied per line as either a **value (R$)** or a **percentage (%)** — choose
  *Sem desconto* (No discount), *Valor (R$)* or *Percentual (%)*.

The **line total** is the unit value times the quantity, minus the discount; the **proposal total** is the
sum of all line totals, recalculated automatically whenever you add, change or remove an item. A percentage
discount must be between 0 and 100, and a value discount cannot exceed the line's amount.

Items can be changed **only while the proposal is a Draft**. Adding or editing items **does not** reserve
anything, check availability with suppliers, or create any sale, order, booking, financial or commission
data — the proposal remains a commercial offer.

> The next steps of the Sales module (internal approval, customer acceptance and the generation of a
> **commercial order**) will come in later releases.

## 10. Managing reference data (*cadastros*)

Reference data are the lists that feed the lead form and future workflows. There are
four, all managed the same way:

| Cadastro | Used for |
|----------|----------|
| **Origens** (Origins) | Where leads come from. |
| **Motivos de perda** (Loss reasons) | Why a lead is eventually lost. |
| **Tipos de interação** (Interaction types) | Kinds of contact (call, WhatsApp, note…). |
| **Resultados de interação** (Interaction results) | Outcome of a contact. |

Open them from **Cadastros** in the top menu or via the command palette.

### 10.1 The list

Each row shows the **code**, the **label** (what users see), the **order**, and whether
it is **Active** or **Inactive**. By default only active records are shown; use
**Mostrar inativos / Ocultar inativos** (Show / Hide inactive) to toggle.

### 10.2 Creating a record

1. Click **Novo** (New).
2. Fill in **Código** (a stable internal code), **Rótulo** (the display label), and
   **Ordem** (sort order, a number ≥ 0).
3. Click **Salvar** (Save). Codes must be unique — reusing one is rejected.

### 10.3 Editing a record

1. Click the **pencil** icon on the row.
2. You can change the **label**, the **order**, and the **Active** switch. The **code**
   cannot be changed (it is the stable identifier).
3. Click **Salvar**.

### 10.4 Activating / deactivating

- Click the **ban** icon to **deactivate** a record (soft delete). Inactive values stay
  for historical accuracy but **cannot be used on new leads**.
- For an inactive record, click the **check** icon to **reactivate** it.

---

## 11. Messages and validation

FKERP validates your input and shows clear, Portuguese-language messages:

- **Field-level messages** appear beside the field that needs attention.
- **Required fields** are marked with an asterisk (`*`).
- The system never shows raw technical errors; if something unexpected happens you get a
  short, safe message and can try again.

---

## 12. Signing out

Click **Sair** (Sign out) in the top-right of the menu bar. You are returned to the login
screen and your session is closed.

---

## 13. What's next

This edition covers the full **Sprint 1** lead lifecycle (registering and finding leads, the lead
detail, assignment, interaction history with the **Contacted** rule, **qualification**, the **Lost**
flow, visibility by profile, the **Pendências** worklist and the **Indicadores** view) and the now
complete **Sprint 2 — Opportunities**: creating an Opportunity from a qualified lead, the **operational
list** with filters, the **Opportunity detail**, moving it **through the stages** (New → Discovery →
Product Fit → Ready for Proposal), **registering commercial activities**, **editing the commercial
details** (estimated value and expected closing date), the **Lost** flow with a reason, **Pending
Opportunities** and **Opportunity indicators** — all with per-profile visibility.

**Sprint 3 — Sales & Proposals** has begun: you can now **create a commercial proposal** from a ready
Opportunity (the **Vendas → Propostas** module). The next steps will continue the proposal lifecycle
(items, values, discounts, internal approval, customer acceptance) and the generation of a **commercial
order**. This manual will be updated as each ships.

---

*Document status: Sprint 1 and Sprint 2 closed; Sprint 3 (Sales & Proposals) in progress — creating a
proposal from a ready Opportunity. Maintained alongside the product.*
