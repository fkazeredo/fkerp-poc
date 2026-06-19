# Relatório — Sprint 3 / Slice 5: Consulta do detalhe da Proposta (histórico de status + Lead de origem)

- **Data:** 2026-06-19
- **Branch:** `feature/sales-proposal-detail` (a partir de `develop`)
- **Versão:** 0.28.0 → **0.29.0** (MINOR — nova funcionalidade)
- **Escopo entregue:** o detalhe da Proposta vira uma **tela de consulta completa antes de agir**. A tela já
  existia (Slices 1–3); esta slice acrescenta o **Histórico de status** (linha do tempo das transições) e o
  card do **Lead de origem com contatos**, fechando a leitura do detalhe.

## 1. O que foi implementado

**Backend — `domain.sales` (espelha o histórico de estágio da Oportunidade)**
- **`ProposalStatusChange`** (entidade nova): `id, fromStatus, toStatus, changedBy, changedAt` + factory
  `of(...)`. Cópia direta de `OpportunityStageChange`.
- **`Proposal`**: coleção `@OneToMany(cascade ALL, orphanRemoval) List<ProposalStatusChange> statusChanges`
  + `recordStatusChange(from, to, byUser)` chamado em `submitForReview` (registra `DRAFT → READY_FOR_REVIEW`).
  O DRAFT inicial **não** é registrado (histórico vazio até a 1ª transição, como na Oportunidade).
- **Flyway `V23__proposal_status_changes.sql`**: tabela `proposal_status_changes` (FK `proposal_id`, CHECK
  dos 8 status em from/to, `changed_by`, `changed_at`) + índice. Sem novo escopo.
- **`ProposalDetail`** ganhou **`statusHistory`** (record `StatusChange(from, to, at, by)`, mais novo
  primeiro) e **`sourceLead`** (record `SourceLead(id, name, phone, whatsapp, email, status)` — espelha o da
  Oportunidade). Factory passa a `from(p, opportunity, lead, names)`.
- **`ProposalService.toDetail`**: carrega o **Lead** (`leads.findById(proposal.leadId())`), coleta os atores
  (`responsiblePersonId` + `statusChanges.changedBy`) e resolve nomes via `users.findAllById` (espelha o
  `OpportunityService.toDetail`). **`LeadRepository`** injetado (domain.sales → domain.crm, §6).

**Frontend — `features/proposals/proposal-detail`**
- `proposal.service.ts`: interfaces `ProposalStatusChange` e `ProposalSourceLead`; `ProposalDetail` estendido.
- `proposal-detail.html`: novo card **"Lead de origem"** (nome + telefone + WhatsApp + e-mail + link
  `/leads/:id`) e novo card **"Histórico de status"** (timeline `data · status(from) → status(to) · ator`,
  com empty state "Sem alterações de status registradas ainda."). O card de Oportunidade ficou só com o link
  da Oportunidade. Os itens/totais/diálogos/atalhos seguem intactos.
- `proposal-detail.css`: reutiliza `.section/.history/.when/.who` do `opportunity-detail.css`.

## 2. Regras funcionais cobertas
- A **Oportunidade de origem permanece rastreável** (card + link); o **Lead de origem** também (card com
  contatos + link).
- **Itens e totais visíveis** (mantidos da Slice 2/3).
- **Aprovação / aceite-rejeição visíveis quando aplicável**: surgem na **linha do tempo de status** quando o
  ciclo futuro gravar →Aprovada/→Enviada/→Aceita/→Rejeitada (cada transição registra quem e quando) — decisão
  do dono (sem cards vazios especulativos agora; Rule Zero).
- **Visibilidade**: o usuário só abre Propostas que pode ver (`canSee` no serviço → 404 inexistente / 403 sem
  permissão), já coberto e mantido.
- O detalhe **não** mostra Booking/Payment/Receivable/Commission (asserido no contrato — `containsExactlyInAnyOrder`).

## 3. Critérios de aceite cobertos
Abrir o detalhe (autorizado) ✓ · mostra a Oportunidade de origem ✓ · itens/descontos/total ✓ · validade +
termos ✓ · status + histórico de status relevante ✓ · suporta aprovação quando existir (via timeline) ✓ ·
suporta decisão do cliente quando existir (via timeline) ✓ · não-autorizados não acessam (403/404) ✓ ·
comportamento existente da Proposta segue funcionando ✓.

## 4. Arquivos alterados (principais)
- **Backend (novos):** `domain/sales/model/ProposalStatusChange.java`,
  `db/migration/V23__proposal_status_changes.sql`.
- **Backend (editados):** `Proposal.java` (coleção + recordStatusChange em submit), `ProposalDetail.java`
  (+statusHistory/+sourceLead), `ProposalService.java` (toDetail carrega Lead + resolve atores;
  `LeadRepository`), `application.yml` (0.29.0).
- **Backend (testes):** `ProposalTest` (+2), `ProposalServiceTest` (+mock LeadRepository),
  `ProposalLifecycleApiIntegrationTest` (+2: history após submit, fresh-draft sourceLead/empty),
  `ProposalCreationApiIntegrationTest` (contrato +`statusHistory`/`sourceLead`).
- **Frontend:** `core/api/proposal.service.ts`; `proposal-detail.{html,css,spec.ts}`;
  `e2e/proposal-creation.spec.ts` (asserções da Slice 5).
- **Docs:** manual en-US + pt-BR (9.2 — detalhe com histórico + card do Lead), este relatório.

## 5. Testes / validações
- **Backend `./mvnw verify`: 380 verdes** (Spotless/Checkstyle/ArchUnit/Modulith + Testcontainers; V23
  aplicada). Cobre: submit **registra** a transição com o ator; detalhe após submit expõe `statusHistory`
  (from/to/at/by) e `sourceLead`; fresh draft tem histórico vazio + sourceLead presente; contrato exato (sem
  reserva/pagamento/recebível/comissão); 401/403 já cobertos.
- **Frontend `ng test`: 261 verdes** (proposal-detail: card do Lead com contatos; histórico empty + populado;
  estados loading/erro/permissão). **`ng build`** verde.
- **E2E `npm run e2e`: 39 verdes** — `proposal-creation` agora também confere, após o envio, o **Histórico de
  status** (`Rascunho → Pronta para revisão`) e o card **Lead de origem** no detalhe.
- Pilha dev recriada (`docker compose up -d --build frontend backend`); Flyway aplica a **V23 forward** (não
  destrutivo); `/api/version` = 0.29.0.

## 6. Gaps conhecidos
- **Aprovação/envio/decisão do cliente** ainda não têm dado próprio — surgem na **linha do tempo de status**
  quando as transições futuras (Aprovar→Enviar→Aceitar/Rejeitar) forem implementadas (Slice 6+). Hoje o
  histórico tem **≤ 1 entrada** (o submit).
- O detalhe continua **somente comercial**; fora de escopo: booking/financeiro/comissão/atendimento/geração de
  documento/assinatura.

## 7. Próximo prompt recomendado
> **SLICE 6: Proposal lifecycle — aprovação e envio.** Implemente as transições `READY_FOR_REVIEW → APPROVED
> → SENT` (cada uma registrando o `ProposalStatusChange` já existente, que aparece automaticamente no
> Histórico de status do detalhe), com gating por escopo (`sales:proposal:update` ou um novo
> `sales:proposal:approve`, decidir com o dono), validações (ex.: validade obrigatória no envio), e os
> endpoints/telas correspondentes. Sem criar Sale/Booking/Financial. Cobrir feliz + tristes (sem permissão,
> transição inválida, validade ausente no envio) e manter lista/detalhe/itens/totais funcionando.
