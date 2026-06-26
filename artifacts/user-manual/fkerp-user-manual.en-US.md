# FKERP — User Manual

> **Audience:** end users of the FKERP system (commercial / sales team).
> **Language:** English (en-US). A Portuguese edition is maintained alongside
> (`fkerp-user-manual.pt-BR.md`).
> **Scope:** covers everything released through **v0.47.2** — the **Commercial / CRM** (the full lead and
> opportunity lifecycle), **Sales & Proposals** (proposals, items, amounts and discounts, the
> approval/send/acceptance flow and the commercial orders), and **Booking operations** (the Reservas module:
> the worklist, the detail, the attempt history, confirming Travel package and Car rental items, registering
> failures with retry, the **consolidated booking status reflected onto the Order**, the **Pending bookings**
> view, and the **Booking indicators**). It grows as new capabilities ship.

---

## 1. What FKERP is

FKERP is the company's ERP, organised into **modules** that follow the commercial and operational workflow. The
system covers the **commercial funnel end to end** and the **booking operation**, with **monitoring**,
**editable support lists**. Each user only sees the modules and actions their
profile allows.

What the system offers today, by module:

- **Comercial** (Commercial) — the funnel in order: **Leads** (interested parties to work) → **Opportunities**
  (deals in progress) → **Proposals** (the formal offer, with items, totals, discount, validity and approval) →
  **Orders** (closed deals). It includes qualifying/losing leads, logging interactions and activities, advancing
  the pipeline, and recording the customer's acceptance/decline. (Sections 5–9.)
- **Reservas** (Bookings) — the back-office operation that **works the reservations** for the items of a closed
  order: the work queue, the detail, manual attempts, confirming travel packages and car rentals, recording
  failures and retrying, and the consolidated status reflected onto the order. (Section 10.)
- **Acompanhamento** (Monitoring) — funnel-wide monitoring in two hubs: **Pending items** (what needs action)
  and **Indicators** (the numbers), with per-area tabs according to your profile.
- **Cadastros** (Reference data) — the **editable support lists** that feed the forms (origins, reasons, types,
  results, channels, item types…), managed by administration without a new version of the system. (Section 11.)

All of it with **secure sign-in**, **keyboard navigation**, and **clear validation** of the information.

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
- The same profile logic applies to the other modules (Opportunities, Proposals, Orders, Bookings, Finance):
  you only see and operate what your profile allows (detailed in the respective sections).
- In **Finance** (section 11), the *financeiro* profile creates and sees all **receivables** and **registers
  payments** (and gains read access to the commercial orders to locate the origin), while the **Manager** and the
  **Board** only consult.
- **Administration** (the reference-data profile) manages **Cadastros** (section 12); that module only appears
  in the menu for users with that profile.

---

## 3. Keyboard-first navigation

FKERP is designed to be driven **from the keyboard**, so you rarely need the mouse.

| Action | Shortcut |
|--------|----------|
| Open the **command palette** (search any action) | `Ctrl` + `K` (or `Cmd` + `K` on macOS) |
| Show **all shortcuts** (help) | `?` |
| New lead | `n` |
| Go to the **lead list** | `g` then `l` |
| Go to **Opportunities** | `g` then `o` |
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

## 4. Home and module navigation

When you sign in you land on the **system home**, which shows a **card for each module** you can access. The
modules follow the **workflow**:

- **Comercial** (Commercial) — the sales funnel in order: **Leads → Oportunidades → Propostas → Pedidos** (plus
  the **New lead** action).
- **Reservas** (Bookings) — the operational worklist of booking requests born from closed orders (section 10).
- **Financeiro** (Finance) — the financial operations, starting with the **receivables** generated from orders
  whose booking is confirmed (section 11).
- **Acompanhamento** (Monitoring) — the cross-funnel monitoring gathered into two hubs: **Pendências** (what
  needs action — Leads, Opportunities and **Bookings**) and **Indicadores** (the funnel's numbers — Leads,
  Opportunities, Proposals, Orders and **Bookings**). Each hub is a **tabbed** page by area — you see only the
  tabs your profile may see.
- **Cadastros** (Reference data) — the support lists that feed the workflows (section 12).

Clicking a card opens that **module's home**, with the shortcuts to its screens.

The **sidebar** mirrors this: each module is a **collapsible section** (accordion). Click the **module title**
to open its home, or the **chevron** to collapse/expand the section — the app remembers which sections you left
collapsed, so the menu stays tidy as the system grows. **Início** (Home) at the top returns to the system home.

A reminder of the main keyboard shortcuts is shown at the bottom (press `?` for the full list).

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
| **Estágio** (Stage) | Nova, Descoberta, Aderência, Pronta p/ proposta, Ganha, Perdida. |
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

**Won Opportunity.** The Opportunity moves to the **Ganha** (Won) stage — also **final** — automatically when a
**commercial order** is created from an accepted proposal (see section 9.8); there is no manual "mark as won"
action. Like *Perdida*, a *Ganha* Opportunity is **hidden from the default list** (pick the stage in the filter
to see it) and leaves the active pipeline and the pending worklist. Marking won triggers no finance or booking.

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

## 9. Proposals and orders (Comercial module)

**Proposals** and **Orders** are the final stages of the **Comercial** funnel — after Leads and Opportunities,
in the same module. A **commercial proposal** is the formalized offer to the client, created from an
Opportunity that is **Ready for Proposal**.

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
Open **Comercial → Propostas** in the menu for the operational list of the proposals you may see. Each row
shows the **title**, the **status**, the **responsible**, the **source Opportunity** (by name, with a
shortcut to the Opportunity), the **total**, the **validity**, the **creation date** and the **last update
date**.

By default the list shows only the **active** proposals — **rejected, expired and cancelled** ones do not
appear unless you select them in the status filter. Use the **filters** at the top to narrow it down:

- **Buscar** (Search) — by part of the proposal title or the source Opportunity name;
- **Status** — one or more states (include Rejected/Expired/Cancelled to see the inactive ones);
- **Responsável** (Responsible) — a person or "Sem responsável" (unassigned);
- **Criada de / até** (Created from/to) and **Validade de / até** (Validity from/to) — periods;
- **Valor mín. / máx.** (Amount min/max) — total range;
- **Limpar** (Clear) — resets every filter.

Click the title to open the **detail**, to review everything before acting. It brings together: the
proposal **summary** (responsible, validity, commercial terms, payment notes, internal notes, dates), the
current **status**, the **source Opportunity** card (with a shortcut to the Opportunity), the **source
Lead** card with the **client's contacts** (name, phone, WhatsApp, e-mail) and a shortcut to the Lead, the
**items** with **subtotal / discount / total** (see below) and the **status history** — a timeline of the
status changes (from → to, when and by whom). As the proposal lifecycle advances, this history also starts
recording the **approval**, the **sending** and the **customer decision** (who and when). The same
**visibility rules** as Opportunities apply: a representative sees only their own proposals; a manager sees
all. The detail exposes commercial data only — never booking, payment, receivable or commission data.

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

The **line total** is the unit value times the quantity, minus the line discount. A percentage discount must
be between 0 and 100, and a value discount cannot exceed the line's amount.

Items can be changed **only while the proposal is a Draft**. Adding or editing items **does not** reserve
anything, check availability with suppliers, or create any sale, order, booking, financial or commission
data — the proposal remains a commercial offer.

### 9.4 Totals, discount and validity
Below the items, the proposal shows a clear **totals** summary:

- **Subtotal** — the sum of all the items' line totals;
- **Proposal discount** — an optional discount on the whole proposal (a **value (R$)** or a **percentage
  (%)**), shown only when present;
- **Total** — the subtotal minus the proposal discount. The total is **never negative**: a value discount is
  capped at the subtotal.

Use **Editar dados comerciais** (Edit commercial details) — available while the proposal is a Draft and you
may operate it — to set the **validity date**, the **commercial terms**, the **payment notes** (descriptive
text only — it does **not** create any financial or receivable record) and the **proposal discount**. The
total recalculates automatically.

When the offer is ready, use **Enviar para revisão** (Submit for review). To submit, the proposal must have
**at least one item**, a **total greater than zero**, a **validity date** and a **responsible person** — if
something is missing the button stays disabled and a hint lists what's missing (you set the validity in
**Editar dados comerciais**). On submit it leaves *Rascunho* (Draft) and becomes *Pronta para revisão* (Ready
for review), after which its items and details are no longer editable. Submitting for review does **not** send
the proposal to the client and creates no order, booking, finance or commission data — it only moves the
status to the internal approval step.

### 9.5 Approve or reject (internal review)
A proposal in *Pronta para revisão* (Ready for review) goes through **internal approval**: the **manager**
(the profile with the approval permission) sees the **Aprovar** (Approve) and **Rejeitar** (Reject) buttons at
the top of the detail.

- **Approve** moves the proposal to *Aprovada* (Approved) and records **who approved and when** in the status
  history.
- **Reject** opens a dialog where you pick a **reason** (a fixed list) and, optionally, a **note**; the proposal
  becomes *Rejeitada* (Rejected), the reason shows on the summary, and the transition (who/when) is recorded in
  the history. A rejected proposal is **not** sent to the client; to revise the offer, create a **new proposal**
  from the same Opportunity (the rejected one frees the Opportunity).

Non-approvers (sellers, representatives) do **not** see these buttons and cannot approve — not even their own
proposals. Approving or rejecting creates no commercial order, booking, finance or commission data.

### 9.6 Mark as sent to the client
Once **approved**, a proposal can be **registered as sent/presented to the client**, so the company can track
the client's decision. On an *Aprovada* (Approved) proposal's detail, whoever **operates** the proposal
(sellers, representatives and the manager) sees the **Marcar como enviada** (Mark as sent) button (shortcut
**`m`**).

- On confirming, you may **optionally** record the **sending channel** — *E-mail*, *WhatsApp*, *Phone
  presentation*, *In-person presentation* or *Other* — or leave it blank.
- The proposal moves to *Enviada* (Sent); the channel (when given) appears on the summary as **Canal de envio**
  (Sending channel), and the **who/when** transition is recorded in the status history. A *Sent* proposal
  **stays available** for the client's decision.

Marking as sent is only a **record**: the system does **not** send a real e-mail, WhatsApp or phone call, does
not generate a PDF or signature, and creates **no** customer acceptance, commercial order, booking, finance or
commission data. Only *Approved* proposals can be marked as sent (a draft, in-review or rejected proposal
cannot).

### 9.7 Register the client's acceptance or rejection
When the client responds to a *Enviada* (Sent) proposal, you register their decision to **close the
negotiation**. On a *Sent* proposal's detail, whoever **operates** the proposal (sellers, representatives and
the manager) sees the **Registrar aceite** (Register acceptance) button (shortcut **`c`**) and the **Registrar
recusa** (Register rejection) button (shortcut **`x`**).

- **Register acceptance** opens a dialog with an **optional confirmation note**; the proposal moves to *Aceita*
  (Accepted). An accepted proposal **stays available** (it is the winning offer) and **prepares** the generation
  of the **commercial order**, which will come in a later release.
- **Register rejection** opens a dialog where you pick a **reason** (a fixed list: *Price too high*, *Chose
  competitor*, *Travel postponed*, *Travel cancelled*, *Changed destination*, *No response after the proposal*,
  *Product mismatch*, *Other*) and, optionally, a **note**; the proposal moves to *Rejeitada* (Rejected). This is
  the **client's** rejection reason, distinct from an internal-review rejection (section 9.5).

In both cases the **who/when** transition is recorded in the status history, and the reason/note shows on the
summary. Registering the decision creates **no** booking, finance, commission or **commercial order** data. Only
*Sent* proposals can be accepted or rejected by the client (a draft, in-review, approved or already-rejected
proposal cannot).

### 9.8 Create the commercial order
When the client **accepts** the proposal, a **Commercial Order** is created — the **formal internal record** of
the closed deal, before the booking and finance steps. On an *Aceita* (Accepted) proposal's detail, whoever has
the orders permission sees the **Criar pedido comercial** (Create commercial order) button (shortcut **`o`**).

- On confirming, the system creates the order (you land straight on the **order detail**) and marks the **source
  Opportunity as won** (*Ganha*).
- The order **preserves** the source proposal, the opportunity, the responsible, the **items** and the **total**
  (a faithful copy of what was sold).
- The order starts **Pendente de reserva** (Pending booking) when it contains items that require booking (a
  **travel package** or a **car rental**); otherwise it starts **Reserva não necessária** (Booking not required).
- Each proposal yields **one** active order. Once created, the proposal shows **Ver pedido comercial** (View
  commercial order) instead of creating another.

Creating the order creates **no** booking, finance, commission or payment — it is only the record of the closed
deal. Bookings and finance will come in later releases. Only an *Accepted* proposal can create an order.

### 9.9 The orders list
The commercial orders live under **Comercial → Pedidos** (keyboard shortcut **`g d`**). The list shows, for each
order: the **Identificador** (Identifier — a friendly number such as **PC-0001**, linking to the detail), the
**Resumo** (Summary — the source proposal's title), the **Oportunidade** (Opportunity), the **Responsável**
(Responsible), the **Total**, the **Status**, the **Reserva** (Booking) indicator (*Exige reserva* / Requires
booking when it has a travel package or a car rental; otherwise *Não exige* / Not required), the **Status da
reserva** (Booking status — the progress of the booking operations: *Pending*, *In progress*, *Partially
confirmed*, *Confirmed* or *Failed*; *Not started* until the booking begins) and the **creation date**.

- **Filters:** by **status**, **booking need**, **responsible**, **creation period** and **amount range**, plus
  a **search** over the summary (proposal title). Use **Limpar** (Clear) to reset.
- **Per-profile visibility:** everyone sees only the orders they may see — **representatives** see **only their
  own**; **managers** see **all**. No filter can surface an order you are not allowed to see.
- **Cancelled** orders are hidden by default; pick the *Cancelado* status in the filter to see them.

The order **detail** shows the number, the status, the **items** and the **total** (a copy of what was sold),
the source references (proposal/opportunity/lead) and a **next-step** note (when *Pending booking*, it signals
that the next step can start the booking operations). So it arrives **ready for the booking step** without
re-typing anything, the detail also gathers the **commercial context from the source proposal** — the
**commercial terms**, the **validity**, the **notes** and the **payment notes** (when filled in) — plus an
explicit **booking-need** indicator (*Sim/Não* — Yes/No).

The detail also shows the **Status da reserva** (Booking status) — the reflection of how the booking operations
are going (section 10) — with clear guidance: when the booking is **Confirmed**, the order is **ready to move on
to Finance**; when it **Failed**, the order shows a **booking problem** that needs attention. This status is a
**read-only reflection**: it does **not** change the order's own situation (still owned by the Commercial area),
does **not** cancel the order and creates **nothing** financial.

The detail (and the list) also show the **Status financeiro** (Financial status) — the reflection of the
**receivable** linked to the order (section 11): **Open** / **Partially paid** / **Paid** / **Overdue** (or empty
when there is no receivable yet). When **Paid**, the order is **ready for Commission Management** (a later step);
when **Overdue**, it shows as a **financial problem** to handle; **Partially paid** is not treated as paid. Like
the booking reflection, the financial status is **read-only**: the order **stays owned by the Commercial area**
(Finance never takes over the order), and reflecting it creates **no** commission. The **Overdue** status is set by
a **daily check** that flags past-due receivables with a balance.

The detail (and the list) also show the **Status da comissão** (Commission status) — a read-only summary of the
order's commission: **Prevista** (Expected) / **Pendente de aprovação** (Eligible, awaiting approval) / **Aprovada**
(Approved) / **Paga** (Paid) / **Problema na comissão** (a rejected or cancelled commission that needs attention),
or empty when there is no commission yet. When **Paga**, the order's commission **cycle is closed**. Like the booking
and financial reflections, this is **read-only**: the order **stays owned by the Commercial area** (Commission
Management never takes over the order), and it creates **nothing** related to payroll, tax or accounting. The list and
the detail show **order data + the booking, financial and commission reflections** — never payment or payroll detail.

> From a **Pending booking** order, the **booking operations** are already available in the **Reservas** module
> (section 10); as the booking is confirmed or fails there, the order's **Booking status** updates automatically.

### 9.10 Proposal indicators

Open **Acompanhamento → Indicadores** (Monitoring → Indicators) and pick the **Propostas** tab (or use the
command palette, `Ctrl K`) for a minimal view of the proposal flow. The screen has **two blocks**:

- **Volume no período** (Volume in period — filtered by creation date; default = current month): the **Total**
  number of proposals, the **Valor proposto** (Proposed amount — the sum of their totals), the **Valor aceito**
  (Accepted amount — the sum of those now *Accepted*) and **Recusadas** (Rejected — those now *Rejected*), plus
  the **Por status** (By status) and **Por responsável** (By responsible) breakdowns. Adjust the period with the
  **Criado de / até** (Created from / to) fields, or click **Todo o período** (All time) for the full history.
- **Em andamento** (In progress — a snapshot of today that is **independent of the period**): **Aguardando
  revisão** (Awaiting review — proposals ready for the internal review) and **Aguardando cliente** (Awaiting the
  client — proposals already sent, awaiting the client's decision).

The same **visibility rules** apply: a representative sees **only their own** figures; a manager or the board
see everyone's. It is an operational read, **not** an executive dashboard — no revenue, cash flow, forecast,
commission or ROI — and it shows **proposal commercial data only**, never sale, order, booking, finance,
payment or commission.

### 9.11 Order indicators

Open **Acompanhamento → Indicadores** (Monitoring → Indicators) and pick the **Pedidos** tab to follow the
closed orders. The screen also has **two blocks**:

- **Volume no período** (Volume in period — by creation date; default = current month): the **Total** number of
  orders and the **Valor total** (Total amount — the sum), plus the **Por responsável** (By responsible)
  breakdown. Adjust or clear the period as on the proposals screen.
- **Em andamento** (In progress — a snapshot of today, **independent of the period**): **Pendentes de reserva**
  (Pending booking) — the orders still awaiting the start of the booking operations.

The same **visibility rules** apply (a representative sees only their own; managers and the board see all). The
screen shows **order data only** — never booking, finance, payment or commission — and is **not** an executive
dashboard.

## 10. Booking operations (the *Reservas* module)

When a commercial order is closed with items that require a booking (**Travel package** or **Car rental**), a
**booking request** is created — the operational work of actually reserving, with suppliers and systems, what
was sold. The **Reservas** (Bookings) module is the back-office area where the operations team tracks and works
these requests.

> The process is **manual and operational**. The system does **not** integrate automatically with suppliers,
> does **not** check availability on its own and creates **nothing** financial, payment, commission or customer
> care. It organizes and records the team's work.

### 10.1 Profiles and access

| Profile | Sees | May operate? |
|---|---|---|
| **Operations** (`operacoes`) | all booking requests | yes — register attempts, confirm items, register failures |
| **Commercial Manager** (`comercial`) | all booking requests | yes (operational oversight) |
| **Board** (`diretor`) | all booking requests | **no** — read-only |
| **Sellers, Representatives, Finance / HR / IT** | — | no access to the Reservas module |

As everywhere in the system, the rule is enforced on the server: someone without access does not see the
**Reservas** menu nor can open a booking by a direct link.

### 10.2 The booking list

Open **Reservas** in the sidebar (or via the command palette, `Ctrl K`, shortcut **`g r`**). The list is the
operations **worklist**. For each booking it shows: the source **Order** (the friendly identifier, e.g.
**PC-0001**, which opens the detail), the source **Proposal**, the **Status**, the **Operator** in charge of the
booking, the commercial **Responsible**, the counts of **Items to book** and of **Confirmed**, the **Last
attempt**, and the **created** and **updated** dates.

- **Filters:** by **status**, **operator** (including *No operator*), **commercial responsible**, **item type**,
  **creation period**, and a **Has failures** toggle (shows only bookings that have a failed item). Use **Clear**
  to reset.
- **What shows by default:** the **active** bookings. **Confirmed** and **Cancelled** ones are hidden (pick those
  statuses in the filter to see them); **failed** ones stay visible, since they are problems to resolve.
- **Visibility by profile:** everyone sees only the bookings they may see; no filter reveals a booking outside
  your permission.

The list shows **operational booking data only** — never finance, payment or commission.

### 10.3 The booking detail

Click the order to open the **detail**. It gathers:

- **Booking summary:** the order, the status, the operator and commercial responsible, the counts of items
  **to book / confirmed / failed**, the notes and the audit data (created at / by).
- **Sources** (always traceable): the **order**, the **proposal**, the **opportunity** and the **lead** that
  originated the booking, each with a shortcut to its screen.
- **Booking items:** each item sold, with its **type**, **description**, **quantity**, whether it **requires
  booking** and its **status** (Pending, In progress, Confirmed, Failed, Not required, Cancelled).
- The **Booking confirmations**, **Operational problems** and **Attempt history** cards (below).

### 10.4 Registering an attempt

Each step of the work — accessing an external system, calling or emailing the supplier, checking internally,
verifying availability — can be **registered as an attempt**, building a **history** of what was done.

1. Click **Registrar tentativa** (Register attempt; shortcut **`a`**).
2. Provide the **type** and the **result** (e.g. *Waiting for supplier*, *Availability found*, *Needs retry*…),
   the **date**, a **description** and, optionally, which **item** it concerns (or the whole booking) and a
   **next action**.
3. Save. The attempt enters the history.

Registering the first attempt may move the booking from **Pending** to **In progress**. An attempt is **history
only**: it does **not** confirm or fail the booking on its own.

### 10.5 Confirming a booking item

When an item (Travel package or Car rental) is actually reserved with the supplier, you **confirm** that item.

1. In the items table, click **Confirmar** (Confirm) on the item.
2. Provide the **system or supplier** and the **locator / booking code** (both required) and the **confirmation
   date**. Depending on the type, record optional data: for the **Package**, the package/destination, travel
   dates and traveler notes; for the **Car rental**, the rental company, car category, the pickup and dropoff
   locations and dates. Both have an **operational notes** field.
3. Save. The item becomes **Confirmed** and the confirmation appears in the **Booking confirmations** card.

As items are confirmed, the **booking status** adjusts itself: **Confirmed** when every item requiring booking is
confirmed; **Partially confirmed** when only some are. Confirming calls **no** external system and creates **no**
voucher, finance, payment or commission.

### 10.6 Registering a failure and retrying

When an item's booking **does not work out** — no availability, supplier unavailable, invalid data, price
changed, etc. — you register the item's **failure**.

1. In the items table, click **Falhar** (Fail).
2. Choose the **failure reason** (required) among the options — *No availability*, *Supplier unavailable*,
   *Invalid commercial data*, *Missing traveler data*, *External system unavailable*, *Price changed*, *Manual
   operation error*, *Out of policy* or *Other* —, the **date** and, optionally, a **note**.
3. Save. The item becomes **Failed** and appears in the **Operational problems** card, with the reason, the note
   and who registered it, when.

A failed item **stays visible as an operational problem** to resolve — it does **not** disappear from the
booking. The booking then becomes **Partially confirmed** (if some other item was already confirmed) or
**Failed** (if none was).

**Retrying is simple:** a failed item may receive **new attempts** (section 10.4) and may be **confirmed later**
(section 10.5) — confirming it **reconsolidates** the booking automatically to *Partially confirmed* or
*Confirmed*. Registering a failure does **not** cancel the commercial order and creates **nothing** financial,
payment, commission or customer care.

### 10.7 How the booking shows on the Order

The **consolidated** booking status (*Pending* · *In progress* · *Partially confirmed* · *Confirmed* · *Failed*)
is **reflected automatically** onto the source **Commercial Order** (section 9), so the whole team can see where
the booking stands without leaving the order screen. A **Confirmed** booking makes the order **ready to move on
to Finance**; a **Failed** booking marks the order with a **booking problem** to handle. This reflection is
**read-only**: the order stays **owned by the Commercial area**, its own situation does not change because of the
booking, and it is **never** cancelled automatically — nor is any financial record created.

> **Preparing Finance (next cycle).** A **Confirmed** booking makes the order ready for the future **Finance** step
> with no re-typing: the booking keeps the whole operational record — each confirmation's **locators**,
> **system/supplier** and **dates**, the attempt history and the sources (order/proposal/opportunity/lead); the
> deal **amount** stays on the **Order**. When Finance arrives it only **reads** this information; the booking
> remains **free of any financial value**.

### 10.8 Pending bookings (what needs action)

So no booking stalls silently, there is a **Pending bookings** view: the list of booking requests that **need
action**, each tagged with the **reasons** why. It lives as the **Reservas** (Bookings) tab of the
**Acompanhamento → Pendências** hub (`Ctrl K`), alongside Leads and Opportunities.

A booking appears as pending when it: has **no operator** assigned; is **Pending with no attempt** yet; is **In
progress with no recent attempt** (no activity for more than **7 days**); has a **failed item**; has a
**requiring-booking item still pending**; is **Partially confirmed**; or has an **overdue next action**. Already
**Confirmed** or **Cancelled** bookings do not appear (they need no action).

Each row shows the order (PC-000n, linking to the booking detail), the proposal, the status, the operator, the
responsible, the items to book, the next action and the last attempt, plus the **reason tags**. It is an
**operational** view (not an executive dashboard): **read-only**, with no email alerts, no notification/SLA engine
and no automatic retries. The same **visibility rules** as the bookings apply — anyone without booking access
(sellers, representatives, finance) does not see this list.

### 10.9 Booking indicators (workload and problems)

So the operations manager can track the **workload and problems** of the bookings, there are the **Booking
indicators**, on the **Reservas** (Bookings) tab of the **Acompanhamento → Indicadores** hub. As in the other
indicator views, there are **two blocks**:

- **Volume in the period** (by creation date; default = current month): the **Total** number of bookings, the
  **By status** breakdown (Pending, In progress, Partially confirmed, Confirmed, Failed, Cancelled), the **Items
  by type** (Travel package, Car rental, Service fee, Other), the **Failed items**, and the **Average time to
  confirmation** (the average time between creating a booking and confirming it, over the period). Adjust the
  period with **Criado de / até** (Created from / to) or click **Todo o período** (All time).
- **In progress** (a snapshot of today, **independent of the period**): **Ready for Finance** — the bookings
  currently **Confirmed**, which can move on to Finance.

The same **visibility rules** apply: operations and commercial managers see the global numbers; anyone without
booking access does not see these indicators. It is an **operational** view, not an executive dashboard — with
**no** finance, payment, commission or external-integration data (integrations do not exist yet).

## 11. Financial operations — receivables (the *Financeiro* module)

The **Financeiro** (Finance) module starts the **financial operations** from deals that are already closed. It
delivers **receivables**: the amount the company has to receive from a client for an order whose **booking is
confirmed**. From this release you can also **register the full payment** of an installment, settling the
receivable. **Commissions, invoices, partial payments and reversals** arrive in later releases.

### 11.1 Profiles and access

- **Finance** (the *financeiro* profile) **creates and sees all** receivables and **registers payments**. To locate
  the source order, this profile also gains **read access to the commercial orders** (read-only — it neither creates
  nor changes orders).
- The **Commercial Manager** and the **Board/Director** **consult** receivables (read-only, **without registering
  payments**), for monitoring.
- **Sellers, representatives** and **HR/IT** do **not** see the Finance module.

As everywhere, **the server is the authority**: the screen only hides what your profile may not do.

### 11.2 The customer (payer)

When a **commercial order is created** (the deal closes), the system **graduates the Lead into a Customer**
automatically — copying the Lead's name and contacts. That **Customer is the payer** shown on the receivable.
There is no manual customer registry in this release: it is born on its own at the close. The document (national
ID) and billing address are left for a later step.

### 11.3 Generating a receivable

There are two paths:

- In **Financeiro → Contas a receber** (Receivables), click **Nova conta a receber** (New receivable).
- On the **detail of an order** with a **confirmed booking**, click **Gerar conta a receber** (Generate
  receivable) — the order comes pre-selected.

In the form, provide:

- **Order** (required) — the list offers only the **eligible orders**: those with a **confirmed booking** that do
  **not yet** have an active receivable. The order amount is shown for reference.
- **Due date** (required when there are **no** installments) — the receivable's due date.
- **Installments** (optional) — you may **split the receivable into installments** (see below).
- **Financial responsible** (optional) — who in Finance owns this receivable.
- **Payment notes** (optional) — free text (not a payment record).

**Installments.** Use **Adicionar parcela** (Add installment) to split the receivable. Each installment has an
**amount**, a **due date** and optional notes. The **installments must sum to the order amount** — the screen
shows the **Remaining** live and only enables **Gerar conta a receber** (Generate receivable) when it matches.
With **no** installments, the receivable is born with a **single installment** for the full amount at the given
due date. Installments start **Open**.

The receivable **preserves the commercial origin** (order, proposal, opportunity and lead), the **customer** and
the order's **total amount**, and is born in the **Open** state. Each order has **at most one active receivable**
— if one already exists, the system warns you. Only orders with a **confirmed booking** can originate a
receivable; an order without that condition is rejected with a clear message. Generating the receivable (and its
installments) creates **no** payment, commission or invoice and never changes the order.

### 11.4 The list and the detail

The **Receivables** screen is the **operational list** — the receivables that need financial follow-up, so you can
**prioritize collection**. For each receivable it shows: the source **order** (code PC-000n), the **customer
(payer)**, the **total amount**, the **amount paid**, the **outstanding amount**, the **status**, the **next due
date** (with an **Overdue** highlight when the receivable is past due), the **commercial** and **financial
responsible**, the **creation** date and the **last payment** date. The **paid**, **outstanding** and **last
payment** figures reflect the payments already registered on the receivable.

**By default**, the list shows the receivables **under follow-up** (Open, Partially paid, Overdue) and **hides the
Paid and Cancelled** ones — select those statuses in the filter to see them. **Overdue receivables stay visible** as
operational problems. You can **filter** by **status**, **customer (payer)**, **order number**, **due-date period**,
**creation period**, **commercial responsible**, **financial responsible**, **amount range** and **overdue only**.
Each profile sees only the receivables it is allowed to see — **representatives do not see** the financial detail.
The list shows **no commission** and **no bank-reconciliation** data.

Click a receivable to open its **detail** — the full consultation to understand its **origin, installments,
payments and outstanding balance**. The detail gathers:

- the **summary**: **total amount**, **amount paid**, **outstanding amount**, **next due date** (with an
  **Overdue** mark and "overdue by N day(s)" when past due), the **status**, the **financial responsible** and the
  **financial notes**;
- the **customer (payer)**;
- the traceable **commercial origin** — the **order** (PC-000n) and the **references** of the source **proposal**
  and **opportunity** (with links to open them, plus the lead) and the **commercial responsible**;
- the **installments table** (number, amount, **paid**, **outstanding**, due date, status and notes), with each
  **overdue installment** (unpaid and past its due date) **highlighted** and, for authorized users, a **Register
  payment** button on each installment that still has a balance;
- the **Payments** section — the **history of registered payments** (installment, amount, date, payment method, who
  registered it and notes); empty while there are no payments.

The screen shows **receivable data and its payments only** — never **commission**, **bank reconciliation** or **tax
invoice** data. You can only open the detail of receivables you are allowed to see.

### 11.5 Registering a payment (full or partial)

Users with the **Finance** profile can **register a payment** for an installment that still has a balance (**Open**
or **Partially paid**) — the amount received may be **full** or **partial**. On the receivable detail, click
**Register payment** on the installment (or use the **`p`** shortcut, which opens the dialog for the first
installment with a balance). Provide:

- **Amount** (required) — pre-filled with the installment's **outstanding** balance; lower it to register a
  **partial payment**. It must be **greater than zero** and **may not exceed the installment's outstanding** balance
  (overpayment is not handled in this release).
- **Payment method** (required) — choose from the registered methods (Cash, Bank transfer, Pix, Credit card, Debit
  card, Invoice payment, Other). The administrator manages this list under **Reference data → Payment methods**.
- **Payment date** (required) — when the amount was received; it **cannot be in the future**.
- **Notes** (optional) — a reference or free remark.

On confirmation: if the amount **settles the installment's balance**, it becomes **Paid**; if it covers **part** of
the balance, it becomes **Partially paid** and you may register **further payments** until it is settled. The
receivable becomes **Paid** when **nothing is outstanding**, or **Partially paid** while a balance remains. The
payment appears in the **Payments** section and the **paid** / **outstanding** figures (of the receivable and each
installment) update. Registering a payment creates **no** commission, invoice or receipt and performs **no** bank
reconciliation, and it never changes the order, lead or customer.

### 11.6 Reversing a payment

A payment entered by mistake can be **reversed** — without erasing the audit trail. Users with the **Finance**
profile see an **Reverse** action next to each **registered** payment in the **Payments** section of the receivable
detail. Reversing requires a **reason** (mandatory); the system records **who** reversed it and **when**.

What happens on reversal:

- The payment **stays in the history**, now marked **Reversed** (with the reason, the user and the date). It is
  **never deleted**, so the record of the correction is preserved.
- The **paid** and **outstanding** amounts (of the receivable and the affected installment) are **recalculated** as
  if that payment had not been counted. The installment returns to **Open** (nothing else paid) or **Partially paid**
  (another payment remains), and the receivable is re-evaluated the same way — a **Paid** receivable can go back to
  **Partially paid** or **Open**.

Only a **registered** payment can be reversed: a payment that was **already reversed** cannot be reversed again. The
**Manager** and **Board/Director** profiles keep read-only consultation and **cannot** reverse a payment. A reversal
issues **no** refund, bank chargeback or customer notification, and creates **no** commission adjustment — it is
purely a correction of the financial record.

### 11.7 Receivable and installment states · Overdue

Both the receivable and each **installment** can be **Open**, **Partially paid**, **Paid**, **Overdue** or
**Cancelled**. Every receivable is born **Open**; as payments are registered, the installment becomes **Partially
paid** (a partial payment) or **Paid** (settled), and the receivable **Partially paid** or **Paid**.

A receivable becomes **Overdue** when its due date has passed and an **outstanding balance** remains — a **daily
check** flags such receivables as **Overdue** automatically (**paid** and **cancelled** installments are never
overdue). **Overdue receivables stay visible by default** in the list (as a problem to prioritize) and you can
filter **overdue only**; in the detail, each **overdue installment** is flagged. **Identifying overdue items
applies no interest or late fee, sends no notification and opens no customer-care ticket** in this release.

### 11.8 Operational indicators: receivables and received payments

The **Recebimentos** screen is a **minimal operational view** for financial managers — what was billed and collected
in a period and the current standing. It is reachable in two places: as **Recebimentos** in the **Finance** module,
and as the **Finance** tab of **Monitoring → Indicators**. It is **operational, not an executive report** — and it is
**not** bank reconciliation, accounts payable, cash flow or accounting.

You choose a **period** (default: the current month) and it shows two parts:

- **In the period**: how many receivables were **created** and their total **amount to receive**; the **amount
  received** and the number of **payments registered**; how many receivables were **settled**; the **average days to
  payment**; and a **received by payment method** breakdown (Cash, Pix, Bank transfer, …). A **reversed** payment does
  **not** count as received.
- **Current standing** (regardless of the period): the **receivables by status** (Open / Partially paid / Paid /
  Overdue / Cancelled), the total **outstanding amount** and **overdue amount** still to receive, and how many
  receivables are **ready for Commission Management** (the fully paid ones — a readiness count, **not** a commission
  calculation).

The numbers respect your **visibility**: a financial user and the consultation profiles (manager, board) see the
indicators; **sellers and representatives do not have access** to this global financial view. The screen shows
**receivable and received-payment figures only** — never commission calculation, accounts payable or
bank-reconciliation data.

---

## 12. Managing reference data (*cadastros*)

Reference data are the **lists that feed the forms** across the whole system — the options you pick in the
type, reason, result and channel fields. As of this release, **nearly all of those lists are editable** by
administration: you can **add, rename, reorder and deactivate** options **without waiting for a new version
of the system**. What used to be fixed in the program is now data you control.

They are all managed the same way, organised by area:

| Area | Reference lists (*cadastros*) |
|------|-------------------------------|
| **Leads** | Origins · Loss reasons · Interaction types · Interaction results |
| **Opportunities** | Activity types · Activity results · Loss reasons (opportunity) |
| **Proposals** | Rejection reasons · Customer-rejection reasons · Sending channels · Item types |
| **Bookings** | Attempt types · Attempt results · Failure reasons |
| **Finance** | Payment methods |
| **Commission** | Commission rules |

When you rename an option, the new label appears on the operational screens immediately; when you
**deactivate** an option, it is no longer offered on new records but stays visible on the older records that
already used it (historical accuracy). Open them from **Cadastros** in the menu, or via the command palette.

### 11.1 The list

Each row shows the **code**, the **label** (what users see), the **order**, and whether
it is **Active** or **Inactive**. By default only active records are shown; use
**Mostrar inativos / Ocultar inativos** (Show / Hide inactive) to toggle.

### 11.2 Creating a record

1. Click **Novo** (New).
2. Fill in **Código** (a stable internal code), **Rótulo** (the display label), and
   **Ordem** (sort order, a number ≥ 0).
3. Click **Salvar** (Save). Codes must be unique — reusing one is rejected.

### 11.3 Editing a record

1. Click the **pencil** icon on the row.
2. You can change the **label**, the **order**, and the **Active** switch. The **code**
   cannot be changed (it is the stable identifier).
3. Click **Salvar**.

### 11.4 Activating / deactivating

- Click the **ban** icon to **deactivate** a record (soft delete). Inactive values stay
  for historical accuracy but **cannot be used on new leads**.
- For an inactive record, click the **check** icon to **reactivate** it.

### 11.5 Commission rules (Commission Management)

The **Commission rule** is a **special** reference screen (richer than the others), under **Cadastros → Regras de
comissão**, available to the **commercial manager** or **Finance**. It defines **how commission is calculated** — in
this release, a **percentage of the received amount** — so the system can compute commissions consistently in the
next steps. **Creating a rule is configuration only**: it creates no commission, payment, payroll, payable, tax or
accounting data.

When creating or editing a rule, provide: a **name** (required); a **percentage** (required — greater than zero, at
most 100; above a configured **safe limit**, default 50%, the system **blocks** saving unless you check **"allow
above the safe limit"**, a conscious confirmation against typos); the **target** (Seller, Sales representative or
Commercial responsible); an optional **specific responsible** (when the rule applies to one person; blank = all of
that type); a **start** (required) and optional **end** date (the end cannot be before the start); and optional
**notes**. Each rule can be **activated/deactivated** — only **active** rules are used for new commission
calculation. Commission calculation, approval and payment arrive in the next Sprint-6 steps.

### 11.6 Generating the expected commission (Commission Management)

From a **closed commercial order**, the **commercial manager** or **Finance** can **generate the expected
commission** — so the future commission is **tracked from the start**. On the **order detail**, the **Gerar comissão**
button (shortcut <kbd>c</kbd>) creates the forecast and shows its summary right there: the **beneficiary** (the order's
commercial responsible), the **applied rule** and its **percentage**, the **calculation basis** and the **commission
amount**. The commission starts as **Expected** and is **not payable** yet.

- The system uses an **active commission rule** that applies to the order's responsible (preferring a rule specific to
  that person; otherwise a **Commercial responsible** rule). If no active rule applies, generation is refused with a
  clear message.
- The **amount** is the **rule's percentage** of a base: the **amount already received** when the order already has
  receipts (its receivable has a registered payment), or the **order's commercial amount** when nothing was received
  yet — in which case the commission is a **forecast**.
- The order must be **closed** (not cancelled), have a **commercial responsible** and a positive **amount**; each order
  has **at most one active commission** (a message warns if one already exists).

Generating the commission **only reads** the order and the receivable — it **does not change** them — and creates **no**
commission payment, accounts payable, payroll, tax or accounting data. Commission **approval**, **payment** and
**indicators** arrive in the next Sprint-6 steps.

### 11.7 Eligible commission — pending approval (Commission Management)

An expected commission becomes **eligible** (pending approval) **only when its related receivable is fully paid** — so
the company **never pays commission before receiving the money**. On the **order detail** the commission moves from
**Prevista** (forecast) to **Pendente de aprovação** (pending approval) and shows **since when** it became eligible.
Key points:

- A **partial payment** of the receivable does **not** make the commission eligible — it stays a forecast until the
  receivable is fully settled (there is no partial eligibility).
- Becoming eligible does **not** approve or pay the commission automatically — it only marks it **pending approval**
  for the next step.
- The receivable stays owned by **Finance**; Commission Management only **consumes** that status and keeps the evidence
  (when it became eligible and which receivable was paid) for the future review.
- Making a commission eligible creates **no** commission payment, accounts payable, payroll, tax or accounting data.

**Note:** if a payment is later **reversed** (a Sprint 5 capability), a commission that already became eligible is
**not** moved back to forecast in this release (there is no automatic commission clawback/reversal — a later step).

### 11.8 Commission list (Commission Management)

Under **Comercial → Comissões** (shortcut <kbd>g</kbd> then <kbd>m</kbd>) the commercial or financial manager tracks
commissions — expected, pending approval, approved and paid — in a **paginated, filterable** list. **Who sees what:**
the **commercial manager**, the **Board** and **Finance** see **all** commissions; **sellers** and **representatives**
see **only their own** (the ones where they are the beneficiary). The backend is the only authority — no filter can
surface a commission you may not see.

Each row shows: the **beneficiary**, the source **order** (PC-000n), the proposal/opportunity **reference**, the
**amount**, the **percentage and rule**, the **status**, the **calculation basis**, the related **receivable status**,
and the **creation**, **eligibility**, **approval** and **payment** dates (the last two appear when available —
approval and payment arrive in later steps). Available filters: **status**, **beneficiary**, **order**, **rule**,
**creation period**, **eligibility period**, **payment period** and **amount range**.

By default the list shows the **in-progress** commissions (Expected / Pending approval / Approved); **Paid**,
**Rejected** and **Cancelled** appear **only when you select them** in the status filter. The screen shows **only
commission and commercial-origin data** — never payroll, tax, accounting or accounts-payable data.

Above the table, an **Operational summary** (Resumo operacional) groups the **same commissions you are seeing** (it
honours the active filters and your visibility): the **count and total amount by status** (one card per status present)
and a **per-beneficiary** breakdown (who has how many and how much), plus the overall count and total. Combined with the
status + payment-period filters, it answers "how much is pending approval", "how much is pending payment" and "how much
was paid in the period" at a glance. It is an **operational view, not executive reporting**, and shows commission figures
only — never payroll, tax, accounting or bank data.

### 11.9 Commission detail (Commission Management)

Clicking the **beneficiary** in the commission list (or **Ver detalhe da comissão** on the order detail) opens the
**commission detail** — the screen to understand its origin, calculation, eligibility, approval and payment. You only
open commissions you may see (sellers and representatives open **only their own**); opening one without permission is
refused with a clear message. The detail shows:

- **Summary** — beneficiary, commission amount, percentage and status.
- **Commercial origin** — the source **order** (PC-000n), **proposal** and **opportunity**, all traceable by links,
  and the lead.
- **Calculation** — the **calculation basis** (commercial or received amount), the **applied rule** (the percentage is
  a **snapshot** kept on the commission, so it stays visible even if the rule changes later) and the **amount**.
- **Receivable** — the related receivable status, traceable via the source order.
- **History** — a timeline (Generated → Eligible → Approved → Paid) that fills in as each step happens. Once the
  commission is **approved**, the summary shows **who approved it and when** plus the approval **notes**; payment and
  cancellation arrive in later releases.

The screen is **read-only** (except the **approve** action described next) and shows **only commission and
commercial-origin data** — never payroll, tax, accounting, bank-transfer or accounts-payable data.

### 11.10 Approving a commission (Commission Management)

An **eligible** commission (pending approval) must be **approved** by an authorized approver before it can be paid — so
payment happens in a **controlled** way. On the **commission detail**, an authorized user sees the **Aprovar comissão**
(Approve commission) button (shortcut <kbd>a</kbd>), which opens a dialog with an **optional notes** field; on confirm,
the commission becomes **Approved** and **ready for payment**, recording **who approved it, when** and the notes. Key
points:

- **Only eligible commissions can be approved.** An **expected** commission (whose receivable is not yet fully paid) or
  one already **approved/paid/rejected/cancelled** cannot be approved — the system refuses with a clear message.
- **You cannot approve your own commission** (segregation of duties): if you are the **beneficiary**, the button is
  hidden and the system refuses the attempt. In practice **finance** approves the commercial manager's commissions and
  the **commercial manager** approves the sellers'/representatives'.
- **Who approves:** the **commercial manager** and **finance**. The **board/director** (consultation), **sellers**,
  **representatives** and **operations** **cannot** approve.
- **Approving pays nothing.** Approval **registers no payment** and creates **no** commission payment, accounts
  payable, payroll, tax, accounting or bank-transfer data — it only marks the commission ready for payment. Commission
  payment is a later step.

### 11.11 Rejecting or cancelling a commission (Commission Management)

When a commission is **not valid**, an authorized user can **void it** so it is not paid — by two paths, both requiring
a **reason** (standard list) and, optionally, a **note**, and recording **who and when**. On the **commission detail**:

- **Rejeitar** (Reject; shortcut <kbd>r</kbd>) is available **only for an eligible commission** (pending approval): it
  becomes **Rejected**.
- **Cancelar comissão** (Cancel; shortcut <kbd>c</kbd>) is available for an **expected** or **approved-but-unpaid**
  commission: it becomes **Cancelled**. A **paid** commission **cannot** be cancelled through this flow.

Key points:

- **The reason is required** (initial reasons: incorrect responsible, incorrect rule, order correction needed,
  receivable/payment issue, duplicate commission, business exception, other). The list is a **cadastro** under
  **Cadastros → Motivos de rejeição/cancelamento de comissão**, editable by the administrator.
- **Who rejects/cancels:** the **commercial manager** and **finance**. The **board/director** (consultation),
  **sellers**, **representatives** and **operations** **cannot** reject or cancel.
- **Voiding touches nothing but the commission:** it does **not** change the source **order** or the **receivable**, and
  creates **no** refund, payroll, tax or accounting data.
- **They stay historically visible:** **Rejected** and **Cancelled** commissions are hidden in the list by default but
  appear when you pick the status in the filter; the detail shows the reason, note and who/when it was voided.

### 11.12 Registering a commission payment (Commission Management)

When an **approved** commission is actually paid, an authorized user **registers the payment** to **close** the
commission cycle. On the **commission detail**, an authorized user sees the **Registrar pagamento** (Register payment)
button (shortcut <kbd>p</kbd>), which opens a dialog with the **payment method**, the **amount**, the **payment date**
and an optional **note**; on confirm, the commission becomes **Paid**, recording the amount, method, date and **who and
when** paid. Key points:

- **Only approved commissions can be paid.** An expected, eligible, already-paid, rejected or cancelled commission
  cannot be paid.
- **The amount must equal the commission amount** (full payment): the field is pre-filled with the commission amount;
  partial payment is not allowed in this release.
- **The payment method** comes from the **payment-methods** cadastro (the same one Finance uses): Cash, Bank transfer,
  Pix, Card, etc. The **date** cannot be in the future.
- **Who registers:** the **commercial manager** and **finance**. The **board/director** (consultation), **sellers**,
  **representatives** and **operations** **cannot** register a payment.
- **Registering the payment triggers no bank or accounting:** it does **not** call any bank integration and creates
  **no** accounts payable, payroll, tax or accounting data; it does **not** change the order or the receivable.
- **It stays visible in history:** **Paid** commissions are hidden in the list by default but appear when you pick the
  **Paid** status in the filter; the detail shows the amount, method, date and who registered the payment.

### 11.13 Commission statement by beneficiary (Commission Management)

The **Extrato de comissões** (Commission statement, under **Comercial → Extrato de comissões**) groups a
**beneficiary's** commissions with per-status **totals**, so expectations, eligibility, approvals and payments are
clear. It is an **informational** screen: it **approves and pays nothing**.

- **Who sees what:** **sellers** and **representatives** see **only their own** statement (the beneficiary selector is
  locked to them); the **commercial manager**, **board/director** and **finance** can pick **any** beneficiary. The
  system is the only authority — you cannot see someone else's statement without permission.
- **What it shows:** the **beneficiary**, the **commission entries** (source order, status, amount, created/eligibility/
  approval/payment dates) and the **totals**: total **expected**, total **eligible**, total **approved** and total
  **paid**.
- **Filters:** a **period** (by creation date) and an **include rejected/cancelled** toggle. By default **rejected**
  and **cancelled** commissions are **excluded** (and never counted in the totals); turn the toggle on to see them.
- **Commercial data only:** the statement shows commission data only — never payroll, tax, accounting, bank export or
  invoice data.

### 11.14 Commission indicators (Commission Management)

Open **Acompanhamento → Indicadores** (Monitoring → Indicators) and pick the **Comissões** tab for a manager's minimal
view of commission obligations and payments. The numbers are **scoped to the commissions you can see** (managers,
board/director and finance see everyone; sellers and representatives see only their own). The screen has:

- **Volume in the period** (by payment date; default = current month): how many commissions were **paid** in the period
  and the **total amount paid**.
- **Currently open** (a snapshot, independent of the period): the **amount pending approval** (eligible) and the **amount
  pending payment** (approved), plus two **average times** — from becoming **eligible to approved** and from **approved to
  paid** — so you can see how quickly commissions move through approval and payment.
- **By status** and **by beneficiary** breakdowns: the count and total amount of commissions in each status and for each
  beneficiary.

It is an **operational view, not executive reporting**, and shows commission figures only — never payroll, tax,
accounting or bank data.

### 11.15 Consolidating the customer from an order (Post-sale)

When a deal is closed, the company's **real customer** is consolidated from the **commercial order**. On an **order's
detail**, a **Post-sale (Operations)** user or the **commercial manager** sees the **Consolidate customer** action
(shortcut <kbd>k</kbd>). It opens a dialog prefilled from the source lead, where you can adjust the **name, document
(CPF/CNPJ), e-mail, phone, WhatsApp, preferred contact channel and notes**.

On confirmation:

- the customer is **created or consolidated** from the order (the operation is **idempotent** — a customer that already
  exists for that lead is enriched, never duplicated);
- the customer starts **Active**;
- the **commercial origin is preserved** (the source order, proposal and opportunity stay traceable);
- **nothing else changes**: the action **does not open a care ticket** and **does not touch** booking, financial or
  commission data.

The consolidated customer appears in a panel on the order itself. The **customer list**, the **commercial history** and
the **care tickets** are the next deliveries of the Post-sale module.

> **End of the commercial-financial cycle — start of Post-sale.** With the commission **paid**, the cycle that began
> with a **lead** — opportunity, proposal, order, booking, receivable and commission — is **complete**, and every
> record stays preserved and traceable. From that closed cycle, **Post-sale** begins: the **real customer** can already
> be **consolidated from the order** (§11.15). **Customer Care tickets** are the next step of the module.

---

## 13. Messages and validation

FKERP validates your input and shows clear, Portuguese-language messages:

- **Field-level messages** appear beside the field that needs attention.
- **Required fields** are marked with an asterisk (`*`).
- The system never shows raw technical errors; if something unexpected happens you get a
  short, safe message and can try again.
- **Unsaved changes are protected.** If you start filling a form or an edit dialog and then try to leave the
  page — by a link, by a keyboard shortcut, or by closing the browser tab — the system asks you to confirm
  before discarding your changes (**Descartar** to leave, **Continuar editando** to stay).

---

## 13.1 Keyboard shortcuts

The menu is organized into clear **modules** — **Comercial**, **Reservas**, **Financeiro**, **Acompanhamento**
and **Cadastros** — each a collapsible section in the sidebar with its own **home** (see section 4). Everything
is also reachable by keyboard:

- **`Ctrl`/`Cmd` + `K`** — the **command palette**: search and jump to any screen or action from anywhere.
- **`?`** — show the full shortcut help at any time.
- **Go to (press `g`, then a letter):** `g i` Home · `g l` Leads · `g o` Opportunities · `g p` Proposals ·
  `g d` Orders · `g r` Bookings · `g f` Finance (receivables) · `g c` Reference data.
  **`n`** creates a new lead.
- **On a lead:** `i` log interaction · `q` qualify · `o` create opportunity · `p` mark lost · `r` reassign ·
  `Esc` back.
- **On an opportunity:** `a` log activity · `e` edit details · `s` advance stage · `p` mark lost · `Esc` back.
- **On a proposal:** `i` add item · `e` edit commercial details · `s` submit for review · `Esc` back.
- **On an order:** `c` generate commission · `k` consolidate customer (each when allowed) · `Esc` back.
- **On a booking:** `a` register attempt · `Esc` back.
- **On a receivable:** `p` register payment (first installment with a balance) · `Esc` back / cancel.
- **On a commission:** `a` approve · `r` reject · `c` cancel · `p` register payment (each when allowed) · `Esc` back /
  cancel.

---

## 14. Signing out

Click **Sair** (Sign out) in the top-right of the menu bar. You are returned to the login
screen and your session is closed.

---

## 15. What's next

This edition covers the full **Sprint 1** lead lifecycle (registering and finding leads, the lead
detail, assignment, interaction history with the **Contacted** rule, **qualification**, the **Lost**
flow, visibility by profile, the **Pendências** worklist and the **Indicadores** view) and the now
complete **Sprint 2 — Opportunities**: creating an Opportunity from a qualified lead, the **operational
list** with filters, the **Opportunity detail**, moving it **through the stages** (New → Discovery →
Product Fit → Ready for Proposal), **registering commercial activities**, **editing the commercial
details** (estimated value and expected closing date), the **Lost** flow with a reason, **Pending
Opportunities** and **Opportunity indicators** — all with per-profile visibility.

**Sprint 3 — Sales & Proposals** is complete: from a ready Opportunity you can **create a commercial proposal**
(**Comercial → Propostas**), manage its **items, values and discounts**, **submit it for internal review**,
**approve or reject** it, **mark an approved proposal as sent** to the client, **register the client's
acceptance or rejection**, **create the commercial order** from an accepted proposal (which marks the
Opportunity as **won**), and **consult the orders list** (**Comercial → Pedidos**).

**Sprint 4 — Booking operations** is **complete**: the **Reservas** module (section 10) delivers the **worklist**
of booking requests born from closed orders, the booking **detail** with its traceable sources, the **attempt
history**, **confirming** Travel package and Car rental items, **registering failures with retry**, the
**consolidated booking status reflected onto the Commercial Order** (section 10.7) — making the order identifiable
as *ready for Finance* or *having a booking problem* —, the **Pending bookings** view (section 10.8) and the
**Booking indicators** (section 10.9) in the Acompanhamento hub. The delivery was **validated end to end**.

**Sprint 5 — Financial Operations** is **complete**: the **Financeiro** module (section 11) delivers the full
**receivables and payments** cycle from the **orders with a confirmed booking** — **generating** the receivable
(with the **Customer** payer created automatically at the close), **installments**, the **operational list** and the
**detail** of each receivable, **registering payments** (full and partial, with method, date, amount and the user
who registered them), the **automatic status update** (*Open* / *Partially paid* / *Paid* / *Overdue*) and its
**reflection onto the Commercial Order**, the **identification of overdue receivables** by the daily check, the
**reversal of payments** preserving the history, the **operational Recebimentos view** and the **minimum financial
indicators**. The delivery was **validated end to end**. A **Paid** receivable makes the order **identifiable as
ready for Commission Management** (next cycle): the record is already in place, **without re-typing** — the **Order**
keeps the amount, the customer and the ready signal; the **receivable** keeps the commercial origin, the responsible,
the total, the installments and the payments (including reversals). Order, Booking, Receivable and Payment stay
**separate**.

The next planned step is **Sprint 6 — Commission Management** (commission calculation, approval and payment), which
**starts from the paid receivables** and only **reads** the record already in place. This manual will be updated as
each ships.

---

*Document status: Sprints 1, 2, 3, 4 and 5 closed — the Finance module delivers the full receivables-and-payments
cycle (generation, installments, list, detail, full and partial payments, status and order reflection, overdue,
reversal, the Recebimentos view and minimum indicators), validated end to end, with a **Paid** receivable leaving the
order ready for Commission Management (Sprint 6). Next step: commission. Maintained alongside the product.*
