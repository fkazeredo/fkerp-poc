# FKERP — User Manual

> **Audience:** end users of the FKERP system (commercial / sales team).
> **Language:** English (en-US).
> **Scope:** this is the **first edition** and covers the features released so far
> (Sprint 1 / Slice 1 — Commercial / CRM: lead intake and reference data). It will
> grow as new capabilities ship.

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
  Customer — it prepares the lead for Sprint 2.
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

## 8. Managing reference data (*cadastros*)

Reference data are the lists that feed the lead form and future workflows. There are
four, all managed the same way:

| Cadastro | Used for |
|----------|----------|
| **Origens** (Origins) | Where leads come from. |
| **Motivos de perda** (Loss reasons) | Why a lead is eventually lost. |
| **Tipos de interação** (Interaction types) | Kinds of contact (call, WhatsApp, note…). |
| **Resultados de interação** (Interaction results) | Outcome of a contact. |

Open them from **Cadastros** in the top menu or via the command palette.

### 8.1 The list

Each row shows the **code**, the **label** (what users see), the **order**, and whether
it is **Active** or **Inactive**. By default only active records are shown; use
**Mostrar inativos / Ocultar inativos** (Show / Hide inactive) to toggle.

### 8.2 Creating a record

1. Click **Novo** (New).
2. Fill in **Código** (a stable internal code), **Rótulo** (the display label), and
   **Ordem** (sort order, a number ≥ 0).
3. Click **Salvar** (Save). Codes must be unique — reusing one is rejected.

### 8.3 Editing a record

1. Click the **pencil** icon on the row.
2. You can change the **label**, the **order**, and the **Active** switch. The **code**
   cannot be changed (it is the stable identifier).
3. Click **Salvar**.

### 8.4 Activating / deactivating

- Click the **ban** icon to **deactivate** a record (soft delete). Inactive values stay
  for historical accuracy but **cannot be used on new leads**.
- For an inactive record, click the **check** icon to **reactivate** it.

---

## 9. Messages and validation

FKERP validates your input and shows clear, Portuguese-language messages:

- **Field-level messages** appear beside the field that needs attention.
- **Required fields** are marked with an asterisk (`*`).
- The system never shows raw technical errors; if something unexpected happens you get a
  short, safe message and can try again.

---

## 10. Signing out

Click **Sair** (Sign out) in the top-right of the menu bar. You are returned to the login
screen and your session is closed.

---

## 11. What's next

This edition covers lead **creation** and reference-data management. Upcoming releases
will add lead **follow-up** (status changes such as Contacted / Qualified / Lost),
interaction history, and more. This manual will be updated as those ship.

---

*Document status: first edition (Sprint 1 / Slice 1). Maintained alongside the product.*
