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
- Register a new lead (with an optional first note).
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

> Development/demo credentials: **`comercial` / `comercial123`**. Your real account is
> created by your administrator.

Your session is kept alive automatically and securely while you use the app. If you
reload the page you stay signed in. Use **Sair** (Sign out) in the top bar to end the
session.

---

## 3. Keyboard-first navigation

FKERP is designed to be driven **from the keyboard**, so you rarely need the mouse.

| Action | Shortcut |
|--------|----------|
| Open the **command palette** (search any action) | `Ctrl` + `K` (or `Cmd` + `K` on macOS) |
| New lead | `n` |
| Go to **Origins** | `g` then `o` |
| Go to **Home** | `g` then `i` |
| Move between fields | `Tab` / `Shift` + `Tab` |
| Confirm / submit a form | `Enter` |
| Close a dialog / cancel | `Esc` |

Notes:

- The **command palette** (`Ctrl/Cmd + K`) lets you type the name of an action and run
  it — the fastest way to move around.
- When you open a form, the **first field is focused automatically**, so you can start
  typing right away.
- Single-letter shortcuts (like `n`) are ignored while you are typing inside a field, so
  they never interfere with data entry.

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

## 6. Managing reference data (*cadastros*)

Reference data are the lists that feed the lead form and future workflows. There are
four, all managed the same way:

| Cadastro | Used for |
|----------|----------|
| **Origens** (Origins) | Where leads come from. |
| **Motivos de perda** (Loss reasons) | Why a lead is eventually lost. |
| **Tipos de interação** (Interaction types) | Kinds of contact (call, WhatsApp, note…). |
| **Resultados de interação** (Interaction results) | Outcome of a contact. |

Open them from **Cadastros** in the top menu or via the command palette.

### 6.1 The list

Each row shows the **code**, the **label** (what users see), the **order**, and whether
it is **Active** or **Inactive**. By default only active records are shown; use
**Mostrar inativos / Ocultar inativos** (Show / Hide inactive) to toggle.

### 6.2 Creating a record

1. Click **Novo** (New).
2. Fill in **Código** (a stable internal code), **Rótulo** (the display label), and
   **Ordem** (sort order, a number ≥ 0).
3. Click **Salvar** (Save). Codes must be unique — reusing one is rejected.

### 6.3 Editing a record

1. Click the **pencil** icon on the row.
2. You can change the **label**, the **order**, and the **Active** switch. The **code**
   cannot be changed (it is the stable identifier).
3. Click **Salvar**.

### 6.4 Activating / deactivating

- Click the **ban** icon to **deactivate** a record (soft delete). Inactive values stay
  for historical accuracy but **cannot be used on new leads**.
- For an inactive record, click the **check** icon to **reactivate** it.

---

## 7. Messages and validation

FKERP validates your input and shows clear, Portuguese-language messages:

- **Field-level messages** appear beside the field that needs attention.
- **Required fields** are marked with an asterisk (`*`).
- The system never shows raw technical errors; if something unexpected happens you get a
  short, safe message and can try again.

---

## 8. Signing out

Click **Sair** (Sign out) in the top-right of the menu bar. You are returned to the login
screen and your session is closed.

---

## 9. What's next

This edition covers lead **creation** and reference-data management. Upcoming releases
will add lead **follow-up** (status changes such as Contacted / Qualified / Lost),
interaction history, and more. This manual will be updated as those ship.

---

*Document status: first edition (Sprint 1 / Slice 1). Maintained alongside the product.*
